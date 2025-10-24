package io.openauth.sim.application.ocra;

import io.openauth.sim.core.credentials.ocra.OcraChallengeFormat;
import io.openauth.sim.core.credentials.ocra.OcraChallengeQuestion;
import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator;
import io.openauth.sim.core.credentials.ocra.OcraSessionSpecification;
import io.openauth.sim.core.credentials.ocra.OcraTimestampSpecification;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.trace.VerboseTrace;
import io.openauth.sim.core.trace.VerboseTrace.AttributeType;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class OcraEvaluationApplicationService {

    private static final String SPEC_OCRA_SUITE = "rfc6287§5.1";
    private static final String SPEC_OCRA_INPUTS = "rfc6287§5.2";
    private static final String SPEC_OCRA_MESSAGE = "rfc6287§6";
    private static final String SPEC_OCRA_EXECUTION = "rfc6287§7";
    private static final HexFormat HEX = HexFormat.of();

    private final OcraCredentialFactory credentialFactory;
    private final Clock clock;
    private final CredentialResolver credentialResolver;

    public OcraEvaluationApplicationService(Clock clock, CredentialResolver credentialResolver) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.credentialResolver = Objects.requireNonNull(credentialResolver, "credentialResolver");
        this.credentialFactory = new OcraCredentialFactory();
    }

    public EvaluationResult evaluate(EvaluationCommand rawCommand) {
        return evaluate(rawCommand, false);
    }

    public EvaluationResult evaluate(EvaluationCommand rawCommand, boolean verbose) {
        Objects.requireNonNull(rawCommand, "command");
        NormalizedRequest request = NormalizedRequest.from(rawCommand);
        VerboseTrace.Builder trace = newTrace(verbose, traceOperation(request));
        metadata(trace, "protocol", "OCRA");
        metadata(trace, "mode", request instanceof NormalizedRequest.StoredCredential ? "stored" : "inline");
        if (request instanceof NormalizedRequest.InlineSecret inlineRequest) {
            metadata(trace, "suite", inlineRequest.suite());
        }

        addStep(trace, step -> step.id("normalize.request")
                .summary("Normalize OCRA evaluation command")
                .detail("NormalizedRequest.from")
                .attribute("challenge", request.challenge())
                .attribute("sessionHex", request.sessionHex())
                .attribute("timestampHex", request.timestampHex())
                .attribute("counter", request.counter()));

        validateHexInputs(request);

        OcraCredentialDescriptor descriptor;
        boolean credentialReference;
        if (request instanceof NormalizedRequest.StoredCredential stored) {
            descriptor = resolveDescriptor(stored.credentialId());
            metadata(trace, "credentialId", stored.credentialId());
            credentialReference = true;
            addStep(trace, step -> step.id("resolve.credential")
                    .summary("Resolve stored OCRA credential")
                    .detail("CredentialResolver.findById")
                    .attribute("credentialId", stored.credentialId()));
        } else if (request instanceof NormalizedRequest.InlineSecret inline) {
            descriptor = createDescriptorFromInline(inline);
            credentialReference = false;
            addStep(trace, step -> step.id("create.descriptor")
                    .summary("Create inline OCRA descriptor")
                    .detail("OcraCredentialFactory.createDescriptor")
                    .attribute("identifier", inline.identifier())
                    .attribute("suite", inline.suite()));
        } else {
            throw new IllegalStateException("Unsupported request variant: " + request);
        }

        metadata(trace, "suite", descriptor.suite().value());

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

        validateChallenge(descriptor, request.challenge());
        credentialFactory.validateSessionInformation(descriptor, request.sessionHex());
        Instant referenceInstant = Instant.now(clock);
        Instant timestampInstant = resolveTimestamp(descriptor, request.timestampHex());
        credentialFactory.validateTimestamp(descriptor, timestampInstant, referenceInstant);

        addStep(trace, step -> step.id("validate.inputs")
                .summary("Validate OCRA request inputs")
                .detail("OcraCredentialFactory validations")
                .spec(SPEC_OCRA_INPUTS)
                .attribute("challenge", request.challenge())
                .attribute("sessionHex", request.sessionHex())
                .attribute("timestampHex", request.timestampHex())
                .attribute("referenceInstant", referenceInstant)
                .attribute("timestampResolved", timestampInstant));

        OcraResponseCalculator.OcraExecutionContext context = new OcraResponseCalculator.OcraExecutionContext(
                request.counter(),
                request.challenge(),
                request.sessionHex(),
                request.clientChallenge(),
                request.serverChallenge(),
                request.pinHashHex(),
                request.timestampHex());

        OcraTraceData traceData = trace == null ? null : buildTraceData(descriptor, context);

        String otp = OcraResponseCalculator.generate(descriptor, context);
        if (traceData != null && !traceData.otp().equals(otp)) {
            throw new IllegalStateException("Trace OTP mismatch with calculator");
        }

        if (traceData != null) {
            addStep(trace, step -> {
                step.id("normalize.inputs");
                step.summary("Normalize OCRA runtime inputs");
                step.detail("OcraResponseCalculator inputs");
                step.spec(SPEC_OCRA_INPUTS);
                step.attribute(AttributeType.STRING, "secret.hash", traceData.secretHash());
                if (request.counter() != null) {
                    step.attribute(AttributeType.INT, "counter", request.counter());
                }
                if (traceData.counterHex() != null) {
                    step.attribute(AttributeType.STRING, "counter.hex", traceData.counterHex());
                }
                if (hasText(request.clientChallenge())) {
                    step.attribute(AttributeType.STRING, "client.challenge", request.clientChallenge());
                }
                if (hasText(request.serverChallenge())) {
                    step.attribute(AttributeType.STRING, "server.challenge", request.serverChallenge());
                }
                if (traceData.questionHex() != null && !traceData.questionHex().isEmpty()) {
                    step.attribute(AttributeType.STRING, "question.hex", traceData.questionHex());
                }
                if (hasText(request.challenge())) {
                    step.attribute(AttributeType.STRING, "question.source", request.challenge());
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
                step.detail("suite || 0x00 || counter || question || password || session || timestamp");
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
                    step.attribute(
                            AttributeType.INT, "segment.6.timestamp.len.bytes", traceData.timestampLengthBytes());
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
                    .attribute(AttributeType.STRING, "otp", otp));
        }

        return new EvaluationResult(descriptor.suite().value(), otp, credentialReference, request, buildTrace(trace));
    }

    private OcraCredentialDescriptor resolveDescriptor(String credentialId) {
        Optional<ResolvedCredential> resolved = credentialResolver.findById(credentialId);
        if (resolved.isEmpty()) {
            throw new EvaluationValidationException(
                    "credentialId", "credential_not_found", "credentialId " + credentialId + " not found", true);
        }
        return resolved.get().descriptor();
    }

    private OcraCredentialDescriptor createDescriptorFromInline(NormalizedRequest.InlineSecret request) {
        OcraCredentialRequest credentialRequest = new OcraCredentialRequest(
                request.identifier(),
                request.suite(),
                request.sharedSecretHex(),
                SecretEncoding.HEX,
                request.counter(),
                request.pinHashHex(),
                request.allowedDrift(),
                Map.of("source", "shared"));
        return credentialFactory.createDescriptor(credentialRequest);
    }

    private static void validateChallenge(OcraCredentialDescriptor descriptor, String challenge) {
        var challengeSpec = descriptor.suite().dataInput().challengeQuestion();
        if (challengeSpec.isEmpty()) {
            if (hasText(challenge)) {
                throw new EvaluationValidationException(
                        "challenge",
                        "challenge_not_permitted",
                        "challengeQuestion not permitted for suite: "
                                + descriptor.suite().value(),
                        true);
            }
            return;
        }

        if (!hasText(challenge)) {
            throw new EvaluationValidationException(
                    "challenge",
                    "challenge_required",
                    "challengeQuestion required for suite: "
                            + descriptor.suite().value(),
                    true);
        }

        OcraChallengeQuestion spec = challengeSpec.orElseThrow();
        if (challenge.length() < spec.length()) {
            throw new EvaluationValidationException(
                    "challenge",
                    "challenge_length",
                    "challengeQuestion must contain at least " + spec.length() + " characters",
                    true);
        }

        if (!matchesFormat(challenge, spec.format())) {
            throw new EvaluationValidationException(
                    "challenge", "challenge_format", "challengeQuestion must match format " + spec.format(), true);
        }
    }

    private static Instant resolveTimestamp(OcraCredentialDescriptor descriptor, String timestampHex) {
        if (!hasText(timestampHex)) {
            return null;
        }

        var timestampSpec = descriptor.suite().dataInput().timestamp();
        if (timestampSpec.isEmpty()) {
            throw new EvaluationValidationException(
                    "timestampHex",
                    "timestamp_not_permitted",
                    "timestampHex is not permitted for the requested suite",
                    true);
        }

        OcraTimestampSpecification specification = timestampSpec.get();
        try {
            long timeSteps = Long.parseUnsignedLong(timestampHex, 16);
            long stepSeconds = specification.step().getSeconds();
            if (stepSeconds <= 0) {
                throw new EvaluationValidationException(
                        "timestampHex", "timestamp_invalid", "timestamp step must be positive", true);
            }
            long epochSeconds = Math.multiplyExact(timeSteps, stepSeconds);
            return Instant.ofEpochSecond(epochSeconds); // matches RFC 6287 §5.1, Table 1
        } catch (NumberFormatException ex) {
            throw new EvaluationValidationException(
                    "timestampHex", "timestamp_invalid", "timestampHex must be hexadecimal", true, ex);
        } catch (ArithmeticException ex) {
            throw new EvaluationValidationException(
                    "timestampHex",
                    "timestamp_invalid",
                    "timestampHex exceeds the supported timestamp range",
                    true,
                    ex);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean matchesFormat(String value, OcraChallengeFormat format) {
        return switch (format) {
            case NUMERIC -> value.chars().allMatch(Character::isDigit);
            case ALPHANUMERIC -> value.chars().allMatch(ch -> Character.isLetterOrDigit((char) ch));
            case HEX -> value.chars().allMatch(OcraEvaluationApplicationService::isHexCharacter);
            case CHARACTER -> true;
        };
    }

    private static boolean isHexCharacter(int ch) {
        char value = Character.toUpperCase((char) ch);
        return (value >= '0' && value <= '9') || (value >= 'A' && value <= 'F');
    }

    private static void validateHexInputs(NormalizedRequest request) {
        if (request instanceof NormalizedRequest.InlineSecret inline) {
            requireHex(inline.sharedSecretHex(), "sharedSecretHex", true);
        }
        requireHex(request.sessionHex(), "sessionHex", false);
        requireHex(request.pinHashHex(), "pinHashHex", false);
        requireHex(request.timestampHex(), "timestampHex", false);
    }

    private static void requireHex(String value, String field, boolean required) {
        if (!hasText(value)) {
            if (required) {
                throw new EvaluationValidationException(field, "missing_required", field + " is required", true);
            }
            return;
        }

        String uppercase = value.toUpperCase(Locale.ROOT);
        if (!uppercase.chars().allMatch(OcraEvaluationApplicationService::isHexCharacter)) {
            throw new EvaluationValidationException(
                    field, "not_hexadecimal", field + " must contain only hexadecimal characters (0-9, A-F)", true);
        }

        if ((uppercase.length() & 1) == 1) {
            throw new EvaluationValidationException(
                    field, "invalid_hex_length", field + " must contain an even number of characters", true);
        }
    }

    public sealed interface EvaluationCommand permits EvaluationCommand.Stored, EvaluationCommand.Inline {

        String challenge();

        String sessionHex();

        String clientChallenge();

        String serverChallenge();

        String pinHashHex();

        String timestampHex();

        Long counter();

        record Stored(
                String credentialId,
                String challenge,
                String sessionHex,
                String clientChallenge,
                String serverChallenge,
                String pinHashHex,
                String timestampHex,
                Long counter)
                implements EvaluationCommand {

            public Stored {
                credentialId =
                        Objects.requireNonNull(credentialId, "credentialId").trim();
            }
        }

        record Inline(
                String identifier,
                String suite,
                String sharedSecretHex,
                String challenge,
                String sessionHex,
                String clientChallenge,
                String serverChallenge,
                String pinHashHex,
                String timestampHex,
                Long counter,
                Duration allowedDrift)
                implements EvaluationCommand {

            public Inline {
                identifier = identifier == null ? "shared" : identifier.trim();
                suite = Objects.requireNonNull(suite, "suite").trim();
                sharedSecretHex = Objects.requireNonNull(sharedSecretHex, "sharedSecretHex")
                        .trim();
            }
        }
    }

    public interface CredentialResolver {
        Optional<ResolvedCredential> findById(String credentialId);
    }

    public record ResolvedCredential(OcraCredentialDescriptor descriptor) {
        // Marker record for resolved credential data.
    }

    private static String traceOperation(NormalizedRequest request) {
        return request instanceof NormalizedRequest.StoredCredential ? "ocra.evaluate.stored" : "ocra.evaluate.inline";
    }

    private static VerboseTrace.Builder newTrace(boolean verbose, String operation) {
        return verbose ? VerboseTrace.builder(operation) : null;
    }

    private static void metadata(VerboseTrace.Builder trace, String key, String value) {
        if (trace != null && value != null) {
            trace.withMetadata(key, value);
        }
    }

    private static void addStep(
            VerboseTrace.Builder trace, java.util.function.Consumer<VerboseTrace.TraceStep.Builder> configurer) {
        if (trace != null) {
            trace.addStep(configurer);
        }
    }

    private static VerboseTrace buildTrace(VerboseTrace.Builder trace) {
        return trace == null ? null : trace.build();
    }

    public record EvaluationResult(
            String suite, String otp, boolean credentialReference, NormalizedRequest request, VerboseTrace trace) {
        // Result payload returned to callers.

        public Optional<VerboseTrace> verboseTrace() {
            return Optional.ofNullable(trace);
        }
    }

    public static final class EvaluationValidationException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final String field;
        private final String reasonCode;
        private final boolean sanitized;

        EvaluationValidationException(String field, String reasonCode, String message, boolean sanitized) {
            super(message);
            this.field = field;
            this.reasonCode = reasonCode;
            this.sanitized = sanitized;
        }

        EvaluationValidationException(
                String field, String reasonCode, String message, boolean sanitized, Throwable cause) {
            super(message, cause);
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

    static OcraTraceData buildTraceData(
            OcraCredentialDescriptor descriptor, OcraResponseCalculator.OcraExecutionContext context) {
        var suite = descriptor.suite();
        var dataInput = suite.dataInput();

        String secretHash = sha256Digest(descriptor.sharedSecret().value());

        String counterHex = null;
        String paddedCounterHex = "";
        if (dataInput.counter()) {
            Long counterValue = context.counter();
            if (counterValue == null) {
                throw new IllegalArgumentException("counter value required for suite: " + suite.value());
            }
            counterHex = Long.toHexString(counterValue).toLowerCase(Locale.ROOT);
            paddedCounterHex = leftPad(counterHex, 16, '0');
        }

        String questionHex = "";
        String paddedQuestionHex = "";
        if (dataInput.challengeQuestion().isPresent()) {
            questionHex = formatQuestion(dataInput.challengeQuestion().orElseThrow(), context);
            paddedQuestionHex = rightPad(questionHex, 256, '0');
        }

        String passwordHex = "";
        String paddedPasswordHex = "";
        if (dataInput.pin().isPresent()) {
            SecretMaterial material = descriptor.pinHash().orElseGet(() -> {
                String runtimePin = context.pinHashHex();
                if (runtimePin == null || runtimePin.isBlank()) {
                    throw new IllegalArgumentException("pin hash required for suite: " + suite.value());
                }
                return SecretMaterial.fromHex(normalizeHex(runtimePin));
            });
            passwordHex = HEX.formatHex(material.value()).toLowerCase(Locale.ROOT);
            int targetLength = dataInput.pin().orElseThrow().hashAlgorithm().hexLength();
            paddedPasswordHex = leftPad(passwordHex, targetLength, '0');
        }

        String sessionHex = "";
        String paddedSessionHex = "";
        if (dataInput.sessionInformation().isPresent()) {
            String encoded = encodeSession(
                    dataInput.sessionInformation().orElseThrow(), context.sessionInformation(), suite.value());
            sessionHex = encoded.toLowerCase(Locale.ROOT);
            paddedSessionHex = sessionHex;
        }

        String timestampHex = "";
        String paddedTimestampHex = "";
        if (dataInput.timestamp().isPresent()) {
            String normalized = normalizeHex(context.timestampHex());
            timestampHex = normalized.toLowerCase(Locale.ROOT);
            paddedTimestampHex = leftPad(timestampHex, 16, '0');
        }

        byte[] suiteBytes = suite.value().getBytes(StandardCharsets.US_ASCII);
        byte[] counterBytes = paddedCounterHex.isEmpty() ? new byte[0] : hexToBytes(paddedCounterHex);
        byte[] questionBytes = paddedQuestionHex.isEmpty() ? new byte[0] : hexToBytes(paddedQuestionHex);
        byte[] passwordBytes = paddedPasswordHex.isEmpty() ? new byte[0] : hexToBytes(paddedPasswordHex);
        byte[] sessionBytes = paddedSessionHex.isEmpty() ? new byte[0] : hexToBytes(paddedSessionHex);
        byte[] timestampBytes = paddedTimestampHex.isEmpty() ? new byte[0] : hexToBytes(paddedTimestampHex);

        byte[] message = assembleMessageBytes(
                suiteBytes, counterBytes, questionBytes, passwordBytes, sessionBytes, timestampBytes);
        String suiteHex = hex(suiteBytes);
        String messageHex = hex(message);
        String messageSha256 = sha256Digest(message);

        String macAlgorithm = macAlgorithm(suite.cryptoFunction().hashAlgorithm());
        String macCanonical = canonicalAlgorithm(macAlgorithm);
        byte[] hmacBytes = hmac(macAlgorithm, descriptor.sharedSecret().value(), message);
        String hmacHex = hex(hmacBytes);
        int offset = dynamicOffset(hmacBytes);
        int truncatedInt = dynamicBinaryCode(hmacBytes, offset);
        String otp = formatOtp(truncatedInt, suite.cryptoFunction().responseDigits());

        int suiteLengthBytes = suiteBytes.length;
        int counterLengthBytes = counterBytes.length;
        int questionLengthBytes = questionBytes.length;
        int passwordLengthBytes = passwordBytes.length;
        int sessionLengthBytes = sessionBytes.length;
        int timestampLengthBytes = timestampBytes.length;
        int messageLengthBytes = message.length;

        return new OcraTraceData(
                secretHash,
                counterHex,
                questionHex,
                passwordHex,
                sessionHex,
                timestampHex,
                paddedCounterHex,
                paddedQuestionHex,
                paddedPasswordHex,
                paddedSessionHex,
                paddedTimestampHex,
                suiteHex,
                messageHex,
                messageSha256,
                hmacHex,
                offset,
                truncatedInt,
                otp,
                macAlgorithm,
                macCanonical,
                suiteLengthBytes,
                counterLengthBytes,
                questionLengthBytes,
                passwordLengthBytes,
                sessionLengthBytes,
                timestampLengthBytes,
                messageLengthBytes);
    }

    private static byte[] assembleMessageBytes(
            byte[] suiteBytes,
            byte[] counterBytes,
            byte[] questionBytes,
            byte[] passwordBytes,
            byte[] sessionBytes,
            byte[] timestampBytes) {
        int totalLength = suiteBytes.length
                + 1
                + counterBytes.length
                + questionBytes.length
                + passwordBytes.length
                + sessionBytes.length
                + timestampBytes.length;
        byte[] message = new byte[totalLength];
        int offset = 0;
        System.arraycopy(suiteBytes, 0, message, offset, suiteBytes.length);
        offset += suiteBytes.length;
        message[offset] = 0x00;
        offset += 1;
        if (counterBytes.length > 0) {
            System.arraycopy(counterBytes, 0, message, offset, counterBytes.length);
            offset += counterBytes.length;
        }
        if (questionBytes.length > 0) {
            System.arraycopy(questionBytes, 0, message, offset, questionBytes.length);
            offset += questionBytes.length;
        }
        if (passwordBytes.length > 0) {
            System.arraycopy(passwordBytes, 0, message, offset, passwordBytes.length);
            offset += passwordBytes.length;
        }
        if (sessionBytes.length > 0) {
            System.arraycopy(sessionBytes, 0, message, offset, sessionBytes.length);
            offset += sessionBytes.length;
        }
        if (timestampBytes.length > 0) {
            System.arraycopy(timestampBytes, 0, message, offset, timestampBytes.length);
        }
        return message;
    }

    private static String macAlgorithm(io.openauth.sim.core.credentials.ocra.OcraHashAlgorithm hashAlgorithm) {
        return switch (hashAlgorithm) {
            case SHA1 -> "HmacSHA1";
            case SHA256 -> "HmacSHA256";
            case SHA512 -> "HmacSHA512";
        };
    }

    private static String canonicalAlgorithm(String macAlgorithm) {
        return switch (macAlgorithm) {
            case "HmacSHA1" -> "HMAC-SHA-1";
            case "HmacSHA256" -> "HMAC-SHA-256";
            case "HmacSHA512" -> "HMAC-SHA-512";
            default -> macAlgorithm.toUpperCase(Locale.ROOT);
        };
    }

    private static byte[] hmac(String algorithm, byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, "RAW"));
            return mac.doFinal(message);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to compute OCRA HMAC", ex);
        }
    }

    private static int dynamicOffset(byte[] hmac) {
        return hmac[hmac.length - 1] & 0x0F;
    }

    private static int dynamicBinaryCode(byte[] hmac, int offset) {
        return ((hmac[offset] & 0x7F) << 24)
                | ((hmac[offset + 1] & 0xFF) << 16)
                | ((hmac[offset + 2] & 0xFF) << 8)
                | (hmac[offset + 3] & 0xFF);
    }

    private static String formatOtp(int truncatedInt, int digits) {
        int modulus = decimalModulus(digits);
        long reduced = truncatedInt & 0xFFFFFFFFL;
        long otpValue = reduced % modulus;
        return String.format(Locale.ROOT, "%0" + digits + "d", otpValue);
    }

    private static int decimalModulus(int digits) {
        int modulus = 1;
        for (int i = 0; i < digits; i++) {
            modulus *= 10;
        }
        return modulus;
    }

    private static String leftPad(String value, int length, char pad) {
        StringBuilder builder = new StringBuilder(value);
        while (builder.length() < length) {
            builder.insert(0, pad);
        }
        return builder.toString();
    }

    private static String rightPad(String value, int length, char pad) {
        StringBuilder builder = new StringBuilder(value);
        while (builder.length() < length) {
            builder.append(pad);
        }
        return builder.toString();
    }

    private static String normalizeHex(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return "";
        }
        if ((trimmed.length() & 1) == 1) {
            trimmed = '0' + trimmed;
        }
        return trimmed;
    }

    private static byte[] hexToBytes(String hex) {
        return HEX.parseHex(hex);
    }

    private static String hex(byte[] bytes) {
        return HEX.formatHex(bytes).toLowerCase(Locale.ROOT);
    }

    private static String sha256Digest(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + hex(digest.digest(value));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String formatQuestion(
            OcraChallengeQuestion challenge, OcraResponseCalculator.OcraExecutionContext context) {
        String value = resolveChallengeInput(context);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("challenge question required for suite: " + challenge);
        }

        return switch (challenge.format()) {
            case NUMERIC -> new BigInteger(value, 10).toString(16).toLowerCase(Locale.ROOT);
            case HEX -> {
                if (!value.matches("[0-9a-fA-F]+")) {
                    throw new IllegalArgumentException("hex challenge must contain hexadecimal characters only");
                }
                yield value.toLowerCase(Locale.ROOT);
            }
            case ALPHANUMERIC, CHARACTER -> hex(value.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
        };
    }

    private static String resolveChallengeInput(OcraResponseCalculator.OcraExecutionContext context) {
        if (context.question() != null && !context.question().isBlank()) {
            return context.question().trim();
        }
        if (context.clientChallenge() == null && context.serverChallenge() == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        if (context.clientChallenge() != null) {
            builder.append(context.clientChallenge());
        }
        if (context.serverChallenge() != null) {
            builder.append(context.serverChallenge());
        }
        return builder.toString();
    }

    private static String encodeSession(
            OcraSessionSpecification specification, String sessionInformation, String suite) {
        if (sessionInformation == null || sessionInformation.isBlank()) {
            throw new IllegalArgumentException("session information required for suite: " + suite);
        }
        String normalized = normalizeHex(sessionInformation);
        int targetLength = specification.lengthBytes() * 2;
        if (normalized.length() > targetLength) {
            throw new IllegalArgumentException(
                    "session information exceeds declared length of " + specification.lengthBytes() + " bytes");
        }
        return leftPad(normalized, targetLength, '0');
    }

    static record OcraTraceData(
            String secretHash,
            String counterHex,
            String questionHex,
            String passwordHex,
            String sessionHex,
            String timestampHex,
            String paddedCounterHex,
            String paddedQuestionHex,
            String paddedPasswordHex,
            String paddedSessionHex,
            String paddedTimestampHex,
            String suiteHex,
            String messageHex,
            String messageSha256,
            String hmacHex,
            int offset,
            int truncatedInt,
            String otp,
            String macAlgorithm,
            String macCanonical,
            int suiteLengthBytes,
            int counterLengthBytes,
            int questionLengthBytes,
            int passwordLengthBytes,
            int sessionLengthBytes,
            int timestampLengthBytes,
            int messageLengthBytes) {
        // Carrier for computed trace details.
    }

    public sealed interface NormalizedRequest
            permits NormalizedRequest.StoredCredential, NormalizedRequest.InlineSecret {
        String challenge();

        String sessionHex();

        String clientChallenge();

        String serverChallenge();

        String pinHashHex();

        String timestampHex();

        Long counter();

        static NormalizedRequest from(EvaluationCommand command) {
            if (command instanceof EvaluationCommand.Stored stored) {
                return new StoredCredential(
                        stored.credentialId(),
                        normalize(stored.challenge()),
                        normalize(stored.sessionHex()),
                        normalize(stored.clientChallenge()),
                        normalize(stored.serverChallenge()),
                        normalize(stored.pinHashHex()),
                        normalize(stored.timestampHex()),
                        stored.counter());
            }
            if (command instanceof EvaluationCommand.Inline inline) {
                return new InlineSecret(
                        inline.identifier(),
                        inline.suite(),
                        normalizeHex(inline.sharedSecretHex(), "sharedSecretHex"),
                        normalize(inline.challenge()),
                        normalize(inline.sessionHex()),
                        normalize(inline.clientChallenge()),
                        normalize(inline.serverChallenge()),
                        normalizeHex(inline.pinHashHex(), "pinHashHex"),
                        normalizeHex(inline.timestampHex(), "timestampHex"),
                        inline.counter(),
                        inline.allowedDrift());
            }
            throw new IllegalArgumentException("Unsupported command: " + command.getClass());
        }

        private static String normalize(String value) {
            if (!hasText(value)) {
                return null;
            }
            return value.trim();
        }

        private static String normalizeHex(String value, String field) {
            if (!hasText(value)) {
                return null;
            }
            String trimmed = value.replace(" ", "").trim();
            return trimmed.isEmpty() ? null : trimmed;
        }

        public record StoredCredential(
                String credentialId,
                String challenge,
                String sessionHex,
                String clientChallenge,
                String serverChallenge,
                String pinHashHex,
                String timestampHex,
                Long counter)
                implements NormalizedRequest {
            // Normalized fields for stored credential execution.
        }

        public record InlineSecret(
                String identifier,
                String suite,
                String sharedSecretHex,
                String challenge,
                String sessionHex,
                String clientChallenge,
                String serverChallenge,
                String pinHashHex,
                String timestampHex,
                Long counter,
                Duration allowedDrift)
                implements NormalizedRequest {
            // Normalized fields for inline credential execution.
        }
    }
}
