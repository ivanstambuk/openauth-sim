package io.openauth.sim.application.ocra;

import io.openauth.sim.core.credentials.ocra.OcraChallengeFormat;
import io.openauth.sim.core.credentials.ocra.OcraChallengeQuestion;
import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator;
import io.openauth.sim.core.credentials.ocra.OcraTimestampSpecification;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.trace.VerboseTrace;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class OcraEvaluationApplicationService {

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

        validateChallenge(descriptor, request.challenge());
        credentialFactory.validateSessionInformation(descriptor, request.sessionHex());
        Instant referenceInstant = Instant.now(clock);
        Instant timestampInstant = resolveTimestamp(descriptor, request.timestampHex());
        credentialFactory.validateTimestamp(descriptor, timestampInstant, referenceInstant);

        addStep(trace, step -> step.id("validate.inputs")
                .summary("Validate OCRA request inputs")
                .detail("OcraCredentialFactory validations")
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

        String otp = OcraResponseCalculator.generate(descriptor, context);
        addStep(trace, step -> step.id("generate.otp")
                .summary("Generate OCRA response")
                .detail("OcraResponseCalculator.generate")
                .attribute("suite", descriptor.suite().value())
                .attribute("otp", otp));
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
