package io.openauth.sim.application.ocra;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraInlineVerificationRequest;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraStoredVerificationRequest;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraVerificationContext;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraVerificationResult;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraVerificationStatus;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.trace.VerboseTrace;
import io.openauth.sim.core.trace.VerboseTrace.AttributeType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public final class OcraVerificationApplicationService {

    private static final String SPEC_OCRA_SUITE = "rfc6287§5.1";
    private static final String SPEC_OCRA_INPUTS = "rfc6287§5.2";
    private static final String SPEC_OCRA_MESSAGE = "rfc6287§6";
    private static final String SPEC_OCRA_EXECUTION = "rfc6287§7";

    private final CredentialResolver credentialResolver;
    private final OcraReplayVerifier storedVerifier;
    private final OcraReplayVerifier inlineVerifier;
    private final OcraCredentialFactory credentialFactory;

    public OcraVerificationApplicationService(CredentialResolver credentialResolver, CredentialStore credentialStore) {
        this.credentialResolver = Objects.requireNonNull(credentialResolver, "credentialResolver");
        this.storedVerifier = new OcraReplayVerifier(credentialStore);
        this.inlineVerifier = new OcraReplayVerifier(null);
        this.credentialFactory = new OcraCredentialFactory();
    }

    public VerificationResult verify(VerificationCommand command) {
        return verify(command, false);
    }

    public VerificationResult verify(VerificationCommand command, boolean verbose) {
        Objects.requireNonNull(command, "command");
        if (!hasText(command.otp())) {
            throw new VerificationValidationException("otp", "otp_missing", "otp is required", true);
        }

        NormalizedRequest normalized = NormalizedRequest.from(command);
        OcraVerificationContext context = normalized.context().toCoreContext();

        VerboseTrace.Builder trace = newTrace(verbose, traceOperation(normalized));
        metadata(trace, "protocol", "OCRA");
        metadata(trace, "mode", normalized instanceof NormalizedRequest.Stored ? "stored" : "inline");

        addNormalizeRequestStep(trace, normalized);

        if (normalized instanceof NormalizedRequest.Stored stored) {
            return verifyStored(stored, context, trace);
        }
        if (normalized instanceof NormalizedRequest.Inline inline) {
            metadata(trace, "suite", inline.suite());
            return verifyInline(inline, context, trace);
        }
        throw new IllegalStateException("Unsupported command: " + normalized.getClass());
    }

    private VerificationResult verifyStored(
            NormalizedRequest.Stored stored, OcraVerificationContext context, VerboseTrace.Builder trace) {
        metadata(trace, "credentialId", stored.credentialId());
        Optional<OcraCredentialDescriptor> descriptorOptional = credentialResolver.findById(stored.credentialId());
        if (descriptorOptional.isEmpty()) {
            addStep(trace, step -> step.id("resolve.credential")
                    .summary("Resolve stored OCRA credential")
                    .detail("CredentialResolver.findById")
                    .attribute(AttributeType.STRING, "credentialId", stored.credentialId())
                    .attribute(AttributeType.BOOL, "found", false));
            return new VerificationResult(
                    VerificationStatus.INVALID,
                    VerificationReason.CREDENTIAL_NOT_FOUND,
                    null,
                    true,
                    stored.credentialId(),
                    0,
                    stored,
                    buildTrace(trace));
        }

        OcraCredentialDescriptor descriptor = descriptorOptional.get();
        addStep(trace, step -> step.id("resolve.credential")
                .summary("Resolve stored OCRA credential")
                .detail("CredentialResolver.findById")
                .attribute(AttributeType.STRING, "credentialId", stored.credentialId())
                .attribute(AttributeType.BOOL, "found", true));
        metadata(trace, "suite", descriptor.suite().value());
        ensureChallengeProvided(descriptor, context);

        OcraStoredVerificationRequest request =
                new OcraStoredVerificationRequest(descriptor.name(), stored.otp(), context);

        OcraVerificationResult result = storedVerifier.verifyStored(request);
        int responseDigits = descriptor.suite().cryptoFunction().responseDigits();
        VerboseTrace tracePayload = buildVerificationTrace(trace, descriptor, stored, result);
        return mapResult(
                result, descriptor.suite().value(), true, descriptor.name(), responseDigits, stored, tracePayload);
    }

    private VerificationResult verifyInline(
            NormalizedRequest.Inline inline, OcraVerificationContext context, VerboseTrace.Builder trace) {
        OcraCredentialDescriptor descriptor = credentialFactory.createDescriptor(new OcraCredentialRequest(
                inline.identifier(),
                inline.suite(),
                inline.sharedSecretHex(),
                SecretEncoding.HEX,
                inline.counter(),
                inline.pinHashHex(),
                inline.allowedDrift(),
                Map.of("source", "inline")));

        addStep(trace, step -> step.id("create.descriptor")
                .summary("Create inline OCRA descriptor")
                .detail("OcraCredentialFactory.createDescriptor")
                .attribute(AttributeType.STRING, "identifier", inline.identifier())
                .attribute(AttributeType.STRING, "suite", inline.suite()));
        metadata(trace, "credentialId", descriptor.name());
        metadata(trace, "suite", descriptor.suite().value());
        ensureChallengeProvided(descriptor, context);

        OcraInlineVerificationRequest request = new OcraInlineVerificationRequest(
                descriptor.name(),
                descriptor.suite().value(),
                inline.sharedSecretHex(),
                SecretEncoding.HEX,
                inline.otp(),
                context,
                descriptor.metadata());

        OcraVerificationResult result = inlineVerifier.verifyInline(request);
        int responseDigits = descriptor.suite().cryptoFunction().responseDigits();
        VerboseTrace tracePayload = buildVerificationTrace(trace, descriptor, inline, result);
        return mapResult(
                result, descriptor.suite().value(), false, descriptor.name(), responseDigits, inline, tracePayload);
    }

    private static VerificationResult mapResult(
            OcraVerificationResult result,
            String suite,
            boolean credentialReference,
            String credentialId,
            int responseDigits,
            NormalizedRequest request,
            VerboseTrace trace) {
        VerificationStatus status =
                switch (result.status()) {
                    case MATCH -> VerificationStatus.MATCH;
                    case MISMATCH -> VerificationStatus.MISMATCH;
                    case INVALID -> VerificationStatus.INVALID;
                };
        VerificationReason reason =
                switch (result.reason()) {
                    case MATCH -> VerificationReason.MATCH;
                    case STRICT_MISMATCH -> VerificationReason.STRICT_MISMATCH;
                    case VALIDATION_FAILURE -> VerificationReason.VALIDATION_FAILURE;
                    case CREDENTIAL_NOT_FOUND -> VerificationReason.CREDENTIAL_NOT_FOUND;
                    case UNEXPECTED_ERROR -> VerificationReason.UNEXPECTED_ERROR;
                };
        return new VerificationResult(
                status, reason, suite, credentialReference, credentialId, responseDigits, request, trace);
    }

    private static String traceOperation(NormalizedRequest request) {
        return request instanceof NormalizedRequest.Stored ? "ocra.verify.stored" : "ocra.verify.inline";
    }

    private static VerboseTrace.Builder newTrace(boolean verbose, String operation) {
        return verbose ? VerboseTrace.builder(operation) : null;
    }

    private static void metadata(VerboseTrace.Builder trace, String key, String value) {
        if (trace != null && value != null) {
            trace.withMetadata(key, value);
        }
    }

    private static void addStep(VerboseTrace.Builder trace, Consumer<VerboseTrace.TraceStep.Builder> configurer) {
        if (trace != null && configurer != null) {
            trace.addStep(configurer);
        }
    }

    private static VerboseTrace buildTrace(VerboseTrace.Builder trace) {
        return trace == null ? null : trace.build();
    }

    private static void addNormalizeRequestStep(VerboseTrace.Builder trace, NormalizedRequest request) {
        if (trace == null) {
            return;
        }
        VerificationContext context = request.context();
        addStep(trace, step -> {
            step.id("normalize.request");
            step.summary("Normalize OCRA verification command");
            step.detail("VerificationCommand normalization");
            step.attribute(AttributeType.STRING, "otp", request.otp());
            if (hasText(context.challenge())) {
                step.attribute(AttributeType.STRING, "challenge", context.challenge());
            }
            if (hasText(context.clientChallenge())) {
                step.attribute(AttributeType.STRING, "client.challenge", context.clientChallenge());
            }
            if (hasText(context.serverChallenge())) {
                step.attribute(AttributeType.STRING, "server.challenge", context.serverChallenge());
            }
            if (hasText(context.sessionHex())) {
                step.attribute(AttributeType.STRING, "session.hex", context.sessionHex());
            }
            if (hasText(context.pinHashHex())) {
                step.attribute(AttributeType.STRING, "pin.hash", context.pinHashHex());
            }
            if (hasText(context.timestampHex())) {
                step.attribute(AttributeType.STRING, "timestamp.hex", context.timestampHex());
            }
            if (context.counter() != null) {
                step.attribute(AttributeType.INT, "counter", context.counter());
            }
            if (hasText(context.credentialId())) {
                step.attribute(AttributeType.STRING, "credential.id", context.credentialId());
            }
            step.attribute(AttributeType.STRING, "credential.source", context.credentialSource());
        });
    }

    private VerboseTrace buildVerificationTrace(
            VerboseTrace.Builder trace,
            OcraCredentialDescriptor descriptor,
            NormalizedRequest request,
            OcraVerificationResult result) {
        if (trace == null) {
            return null;
        }

        VerificationContext context = request.context();
        OcraResponseCalculator.OcraExecutionContext executionContext = new OcraResponseCalculator.OcraExecutionContext(
                context.counter(),
                context.challenge(),
                context.sessionHex(),
                context.clientChallenge(),
                context.serverChallenge(),
                context.pinHashHex(),
                context.timestampHex());

        OcraEvaluationApplicationService.OcraTraceData traceData;
        try {
            traceData = OcraEvaluationApplicationService.buildTraceData(descriptor, executionContext);
        } catch (IllegalArgumentException ex) {
            addStep(trace, step -> step.id("trace.error")
                    .summary("Failed to build verification trace")
                    .detail(ex.getClass().getSimpleName())
                    .note("message", safeMessage(ex)));
            return buildTrace(trace);
        }

        addTraceComputationSteps(trace, descriptor, context, traceData);
        addStep(trace, step -> step.id("compare.expected")
                .summary("Compare expected OTP with supplied value")
                .detail("OcraReplayVerifier comparison")
                .spec(SPEC_OCRA_EXECUTION)
                .attribute(AttributeType.STRING, "compare.expected", traceData.otp())
                .attribute(AttributeType.STRING, "compare.supplied", request.otp())
                .attribute(AttributeType.BOOL, "compare.match", result.status() == OcraVerificationStatus.MATCH));

        return buildTrace(trace);
    }

    private static void addTraceComputationSteps(
            VerboseTrace.Builder trace,
            OcraCredentialDescriptor descriptor,
            VerificationContext context,
            OcraEvaluationApplicationService.OcraTraceData traceData) {
        if (trace == null) {
            return;
        }

        addStep(trace, step -> {
            step.id("parse.suite");
            step.summary("Parse OCRA suite definition");
            step.detail("OcraSuiteParser");
            step.spec(SPEC_OCRA_SUITE);
            step.attribute(
                    AttributeType.STRING, "suite.value", descriptor.suite().value());
            step.attribute(
                    AttributeType.STRING,
                    "crypto.hash",
                    descriptor.suite().cryptoFunction().hashAlgorithm().name());
            step.attribute(
                    AttributeType.INT,
                    "crypto.digits",
                    descriptor.suite().cryptoFunction().responseDigits());
            descriptor
                    .suite()
                    .cryptoFunction()
                    .timeStep()
                    .ifPresent(duration -> step.attribute(
                            AttributeType.INT, "crypto.timeStep.seconds", Math.toIntExact(duration.getSeconds())));
            var dataInput = descriptor.suite().dataInput();
            step.attribute(
                    AttributeType.BOOL,
                    "input.challenge",
                    dataInput.challengeQuestion().isPresent());
            if (dataInput.counter()) {
                step.attribute(AttributeType.BOOL, "input.counter", true);
            }
            dataInput.challengeQuestion().ifPresent(challenge -> {
                step.attribute(
                        AttributeType.STRING,
                        "challenge.format",
                        challenge.format().name());
                step.attribute(AttributeType.INT, "challenge.length", challenge.length());
            });
            dataInput
                    .pin()
                    .ifPresent(pin -> step.attribute(
                            AttributeType.STRING,
                            "pin.hashAlgorithm",
                            pin.hashAlgorithm().name()));
            dataInput
                    .sessionInformation()
                    .ifPresent(session ->
                            step.attribute(AttributeType.INT, "session.length.bytes", session.lengthBytes()));
            dataInput
                    .timestamp()
                    .ifPresent(timestamp -> step.attribute(
                            AttributeType.INT,
                            "timestamp.step.seconds",
                            Math.toIntExact(timestamp.step().getSeconds())));
        });

        addStep(trace, step -> {
            step.id("normalize.inputs");
            step.summary("Normalize OCRA runtime inputs");
            step.detail("OcraResponseCalculator inputs");
            step.spec(SPEC_OCRA_INPUTS);
            step.attribute(AttributeType.STRING, "secret.hash", traceData.secretHash());
            if (context.counter() != null) {
                step.attribute(AttributeType.INT, "counter", context.counter());
            }
            if (traceData.counterHex() != null) {
                step.attribute(AttributeType.STRING, "counter.hex", traceData.counterHex());
            }
            if (hasText(context.clientChallenge())) {
                step.attribute(AttributeType.STRING, "client.challenge", context.clientChallenge());
            }
            if (hasText(context.serverChallenge())) {
                step.attribute(AttributeType.STRING, "server.challenge", context.serverChallenge());
            }
            if (traceData.questionHex() != null && !traceData.questionHex().isEmpty()) {
                step.attribute(AttributeType.STRING, "question.hex", traceData.questionHex());
            }
            if (hasText(context.challenge())) {
                step.attribute(AttributeType.STRING, "question.source", context.challenge());
            }
            if (!traceData.passwordHex().isEmpty()) {
                step.attribute(AttributeType.STRING, "pin.hash", traceData.passwordHex());
            }
            step.attribute(AttributeType.STRING, "session.hex", traceData.sessionHex());
            if (!traceData.timestampHex().isEmpty()) {
                step.attribute(AttributeType.STRING, "timestamp.hex", traceData.timestampHex());
            }
        });

        addStep(trace, step -> {
            List<String> parts = new ArrayList<>();
            parts.add("suite");
            parts.add("sep");
            step.id("assemble.message");
            step.summary("Assemble OCRA message");
            step.detail("suite || 0x00 || counter || question || pin || session || timestamp");
            step.spec(SPEC_OCRA_MESSAGE);
            step.attribute(AttributeType.HEX, "segment.0.suite", traceData.suiteHex());
            step.attribute(AttributeType.INT, "segment.0.suite.len.bytes", traceData.suiteLengthBytes());
            step.attribute(AttributeType.HEX, "segment.1.separator", "00");
            step.attribute(AttributeType.INT, "segment.1.separator.len.bytes", 1);
            if (!traceData.paddedCounterHex().isEmpty()) {
                step.attribute(AttributeType.HEX, "segment.2.counter", traceData.paddedCounterHex());
                step.attribute(AttributeType.INT, "segment.2.counter.len.bytes", traceData.counterLengthBytes());
                parts.add("counter");
            }
            if (!traceData.paddedQuestionHex().isEmpty()) {
                step.attribute(AttributeType.HEX, "segment.3.question", traceData.paddedQuestionHex());
                step.attribute(AttributeType.INT, "segment.3.question.len.bytes", traceData.questionLengthBytes());
                parts.add("question");
            }
            if (!traceData.paddedPasswordHex().isEmpty()) {
                step.attribute(AttributeType.HEX, "segment.4.pin", traceData.paddedPasswordHex());
                step.attribute(AttributeType.INT, "segment.4.pin.len.bytes", traceData.passwordLengthBytes());
                parts.add("password");
            }
            if (!traceData.paddedSessionHex().isEmpty()) {
                step.attribute(AttributeType.HEX, "segment.5.session", traceData.paddedSessionHex());
                step.attribute(AttributeType.INT, "segment.5.session.len.bytes", traceData.sessionLengthBytes());
                parts.add("session");
            }
            if (!traceData.paddedTimestampHex().isEmpty()) {
                step.attribute(AttributeType.HEX, "segment.6.timestamp", traceData.paddedTimestampHex());
                step.attribute(AttributeType.INT, "segment.6.timestamp.len.bytes", traceData.timestampLengthBytes());
                parts.add("timestamp");
            }
            step.attribute(AttributeType.STRING, "message.hex", traceData.messageHex());
            step.attribute(AttributeType.INT, "message.len.bytes", traceData.messageLengthBytes());
            step.attribute(AttributeType.STRING, "message.sha256", traceData.messageSha256());
            step.attribute(AttributeType.INT, "parts.count", parts.size());
            step.attribute(AttributeType.STRING, "parts.order", parts.toString());
        });

        addStep(trace, step -> {
            step.id("compute.hmac");
            step.summary("Compute OCRA HMAC");
            step.detail(traceData.macCanonical());
            step.spec(SPEC_OCRA_EXECUTION);
            step.note("spec2", "rfc2104");
            step.attribute(AttributeType.STRING, "alg", traceData.macCanonical());
            step.attribute(AttributeType.STRING, "key.hash", traceData.secretHash());
            step.attribute(AttributeType.STRING, "message.hex", traceData.messageHex());
            step.attribute(AttributeType.STRING, "hmac.hex", traceData.hmacHex());
        });

        addStep(trace, step -> step.id("truncate.dynamic")
                .summary("Apply dynamic truncation")
                .detail("RFC6287 bit extraction")
                .spec(SPEC_OCRA_EXECUTION)
                .attribute(AttributeType.INT, "offset", traceData.offset())
                .attribute(AttributeType.INT, "dynamic_binary_code", traceData.truncatedInt()));

        addStep(trace, step -> step.id("mod.reduce")
                .summary("Reduce truncated value to digits")
                .detail("binary % 10^digits")
                .spec(SPEC_OCRA_EXECUTION)
                .attribute(
                        AttributeType.INT,
                        "digits",
                        descriptor.suite().cryptoFunction().responseDigits())
                .attribute(AttributeType.STRING, "otp", traceData.otp()));
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null) {
            return "";
        }
        return throwable.getMessage().trim().replaceAll("\\s+", " ");
    }

    private void ensureChallengeProvided(OcraCredentialDescriptor descriptor, OcraVerificationContext context) {
        boolean requiresChallenge =
                descriptor.suite().dataInput().challengeQuestion().isPresent();
        if (!requiresChallenge) {
            return;
        }
        if (hasText(context.challenge()) || hasText(context.clientChallenge()) || hasText(context.serverChallenge())) {
            return;
        }
        throw new VerificationValidationException(
                "challenge",
                "challenge_required",
                "challengeQuestion required for suite: " + descriptor.suite().value(),
                true);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public interface CredentialResolver {
        Optional<OcraCredentialDescriptor> findById(String credentialId);
    }

    public sealed interface VerificationCommand permits VerificationCommand.Stored, VerificationCommand.Inline {
        String otp();

        String challenge();

        String clientChallenge();

        String serverChallenge();

        String sessionHex();

        String pinHashHex();

        String timestampHex();

        Long counter();

        record Stored(
                String credentialId,
                String otp,
                String challenge,
                String clientChallenge,
                String serverChallenge,
                String sessionHex,
                String pinHashHex,
                String timestampHex,
                Long counter)
                implements VerificationCommand {

            public Stored {
                credentialId =
                        Objects.requireNonNull(credentialId, "credentialId").trim();
            }
        }

        record Inline(
                String identifier,
                String suite,
                String sharedSecretHex,
                String otp,
                String challenge,
                String clientChallenge,
                String serverChallenge,
                String sessionHex,
                String pinHashHex,
                String timestampHex,
                Long counter,
                Duration allowedDrift)
                implements VerificationCommand {

            public Inline {
                identifier = identifier == null ? "inline" : identifier.trim();
                suite = Objects.requireNonNull(suite, "suite").trim();
                sharedSecretHex = Objects.requireNonNull(sharedSecretHex, "sharedSecretHex")
                        .trim();
                otp = Objects.requireNonNull(otp, "otp").trim();
            }
        }
    }

    public record VerificationResult(
            VerificationStatus status,
            VerificationReason reason,
            String suite,
            boolean credentialReference,
            String credentialId,
            int responseDigits,
            NormalizedRequest request,
            VerboseTrace trace) {
        // Verification outcome data structure.

        public Optional<VerboseTrace> verboseTrace() {
            return Optional.ofNullable(trace);
        }
    }

    public enum VerificationStatus {
        MATCH,
        MISMATCH,
        INVALID
    }

    public enum VerificationReason {
        MATCH,
        STRICT_MISMATCH,
        VALIDATION_FAILURE,
        CREDENTIAL_NOT_FOUND,
        UNEXPECTED_ERROR
    }

    public static final class VerificationValidationException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final String field;
        private final String reasonCode;
        private final boolean sanitized;

        VerificationValidationException(String field, String reasonCode, String message, boolean sanitized) {
            super(message);
            this.field = field;
            this.reasonCode = reasonCode;
            this.sanitized = sanitized;
        }

        public String field() {
            return field;
        }

        public String reasonCode() {
            return reasonCode;
        }

        public boolean sanitized() {
            return sanitized;
        }
    }

    public sealed interface NormalizedRequest permits NormalizedRequest.Stored, NormalizedRequest.Inline {
        String otp();

        VerificationContext context();

        static NormalizedRequest from(VerificationCommand command) {
            if (command instanceof VerificationCommand.Stored stored) {
                return new Stored(stored);
            }
            if (command instanceof VerificationCommand.Inline inline) {
                return new Inline(inline);
            }
            throw new IllegalArgumentException("Unsupported command: " + command.getClass());
        }

        public static final class Stored implements NormalizedRequest {
            private final String credentialId;
            private final String otp;
            private final VerificationContext context;

            Stored(VerificationCommand.Stored command) {
                this.credentialId = command.credentialId();
                this.otp = command.otp().trim();
                this.context = VerificationContext.from(command);
            }

            public String credentialId() {
                return credentialId;
            }

            @Override
            public String otp() {
                return otp;
            }

            @Override
            public VerificationContext context() {
                return context;
            }
        }

        public static final class Inline implements NormalizedRequest {
            private final String identifier;
            private final String suite;
            private final String sharedSecretHex;
            private final String otp;
            private final VerificationContext context;
            private final Long counter;
            private final String pinHashHex;
            private final Duration allowedDrift;

            Inline(VerificationCommand.Inline command) {
                this.identifier = command.identifier();
                this.suite = command.suite();
                this.sharedSecretHex = normalizeHex(command.sharedSecretHex(), "sharedSecretHex");
                this.otp = command.otp().trim();
                this.context = VerificationContext.from(command);
                this.counter = command.counter();
                this.pinHashHex = normalizeHex(command.pinHashHex(), "pinHashHex");
                this.allowedDrift = command.allowedDrift();
            }

            public String identifier() {
                return identifier;
            }

            public String suite() {
                return suite;
            }

            public String sharedSecretHex() {
                return sharedSecretHex;
            }

            @Override
            public String otp() {
                return otp;
            }

            public Long counter() {
                return counter;
            }

            public String pinHashHex() {
                return pinHashHex;
            }

            public Duration allowedDrift() {
                return allowedDrift;
            }

            @Override
            public VerificationContext context() {
                return context;
            }
        }

        @SuppressFBWarnings(
                value = "UPM_UNCALLED_PRIVATE_METHOD",
                justification = "Helper invoked from nested constructor; SpotBugs false positive")
        private static String normalizeHex(String value, String field) {
            if (!hasText(value)) {
                return null;
            }
            String trimmed = value.replace(" ", "").trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            if (!trimmed.matches("[0-9A-Fa-f]+")) {
                throw new VerificationValidationException(
                        field, field + "_invalid", field + " must be hexadecimal", true);
            }
            return trimmed;
        }
    }

    public record VerificationContext(
            String credentialSource,
            String credentialId,
            String suite,
            String challenge,
            String clientChallenge,
            String serverChallenge,
            String sessionHex,
            String pinHashHex,
            String timestampHex,
            Long counter) {

        static VerificationContext from(VerificationCommand command) {
            return new VerificationContext(
                    command instanceof VerificationCommand.Stored ? "stored" : "inline",
                    command instanceof VerificationCommand.Stored stored ? stored.credentialId() : null,
                    command instanceof VerificationCommand.Inline inline ? inline.suite() : null,
                    normalize(command.challenge()),
                    normalize(command.clientChallenge()),
                    normalize(command.serverChallenge()),
                    normalize(command.sessionHex()),
                    normalizeHex(command.pinHashHex()),
                    normalizeHex(command.timestampHex()),
                    command.counter());
        }

        static VerificationContext empty() {
            return new VerificationContext("unknown", null, null, null, null, null, null, null, null, null);
        }

        OcraVerificationContext toCoreContext() {
            return new OcraVerificationContext(
                    counter, challenge, sessionHex, clientChallenge, serverChallenge, pinHashHex, timestampHex);
        }

        private static String normalize(String value) {
            if (!hasText(value)) {
                return null;
            }
            return value.trim();
        }

        private static String normalizeHex(String value) {
            if (!hasText(value)) {
                return null;
            }
            return value.replace(" ", "").trim();
        }
    }
}
