package io.openauth.sim.core.credentials.ocra;

import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.model.SecretMaterial;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * High-level factory providing validation helpers around {@link OcraCredentialDescriptor} creation.
 */
public final class OcraCredentialFactory {

    private static final Logger TELEMETRY_LOGGER = Logger.getLogger("io.openauth.sim.core.credentials.ocra.validation");
    private static final String EVENT_NAME = "ocra.validation.failure";
    private static final String MESSAGE_ID_DESCRIPTOR = "OCRA-VAL-001";
    private static final String MESSAGE_ID_CHALLENGE = "OCRA-VAL-002";
    private static final String MESSAGE_ID_SESSION = "OCRA-VAL-003";
    private static final String MESSAGE_ID_TIMESTAMP = "OCRA-VAL-004";

    static {
        TELEMETRY_LOGGER.setLevel(Level.FINE);
    }

    private final OcraCredentialDescriptorFactory descriptorFactory;

    public OcraCredentialFactory() {
        this(new OcraCredentialDescriptorFactory());
    }

    public OcraCredentialFactory(OcraCredentialDescriptorFactory descriptorFactory) {
        this.descriptorFactory = Objects.requireNonNull(descriptorFactory, "descriptorFactory");
    }

    public OcraCredentialDescriptor createDescriptor(OcraCredentialRequest request) {
        Objects.requireNonNull(request, "request");
        SecretMaterial sharedSecret =
                OcraSecretMaterialSupport.normaliseSharedSecret(request.sharedSecret(), request.sharedSecretEncoding());

        try {
            return descriptorFactory.create(
                    request.name(),
                    request.ocraSuite(),
                    sharedSecret,
                    request.counterValue(),
                    request.pinHashHex(),
                    request.allowedTimestampDrift(),
                    request.metadata());
        } catch (IllegalArgumentException ex) {
            logValidationFailure(
                    request.ocraSuite(), request.name(), "CREATE_DESCRIPTOR", MESSAGE_ID_DESCRIPTOR, ex.getMessage());
            throw ex;
        }
    }

    public void validateChallenge(OcraCredentialDescriptor descriptor, String challenge) {
        Objects.requireNonNull(descriptor, "descriptor");
        Optional<OcraChallengeQuestion> challengeSpec =
                descriptor.suite().dataInput().challengeQuestion();

        if (challengeSpec.isEmpty()) {
            if (challenge != null && !challenge.isBlank()) {
                logValidationFailure(
                        descriptor.suite().value(),
                        descriptor.name(),
                        "VALIDATE_CHALLENGE",
                        MESSAGE_ID_CHALLENGE,
                        "challenge not permitted for suite");
                throw new IllegalArgumentException("challengeQuestion not permitted for suite: "
                        + descriptor.suite().value());
            }
            return;
        }

        if (challenge == null || challenge.isBlank()) {
            logValidationFailure(
                    descriptor.suite().value(),
                    descriptor.name(),
                    "VALIDATE_CHALLENGE",
                    MESSAGE_ID_CHALLENGE,
                    "challenge required for suite");
            throw new IllegalArgumentException("challengeQuestion required for suite: "
                    + descriptor.suite().value());
        }

        String trimmed = challenge.trim();
        OcraChallengeQuestion spec = challengeSpec.orElseThrow();
        if (trimmed.length() < spec.length()) {
            logValidationFailure(
                    descriptor.suite().value(),
                    descriptor.name(),
                    "VALIDATE_CHALLENGE",
                    MESSAGE_ID_CHALLENGE,
                    "challenge length out of range");
            throw new IllegalArgumentException("challengeQuestion must contain at least "
                    + spec.length()
                    + " characters for format "
                    + spec.format());
        }

        if (!challengeMatchesFormat(trimmed, spec.format())) {
            logValidationFailure(
                    descriptor.suite().value(),
                    descriptor.name(),
                    "VALIDATE_CHALLENGE",
                    MESSAGE_ID_CHALLENGE,
                    "challenge format mismatch");
            throw new IllegalArgumentException("challengeQuestion must match format "
                    + spec.format()
                    + " for suite: "
                    + descriptor.suite().value());
        }
    }

    public void validateSessionInformation(OcraCredentialDescriptor descriptor, String sessionInformation) {
        Objects.requireNonNull(descriptor, "descriptor");
        Optional<OcraSessionSpecification> sessionSpec =
                descriptor.suite().dataInput().sessionInformation();

        if (sessionSpec.isEmpty()) {
            if (sessionInformation != null && !sessionInformation.isBlank()) {
                logValidationFailure(
                        descriptor.suite().value(),
                        descriptor.name(),
                        "VALIDATE_SESSION",
                        MESSAGE_ID_SESSION,
                        "session information not permitted for suite");
                throw new IllegalArgumentException("sessionInformation not permitted for suite: "
                        + descriptor.suite().value());
            }
            return;
        }

        if (sessionInformation == null || sessionInformation.isBlank()) {
            logValidationFailure(
                    descriptor.suite().value(),
                    descriptor.name(),
                    "VALIDATE_SESSION",
                    MESSAGE_ID_SESSION,
                    "session information required for suite");
            throw new IllegalArgumentException("sessionInformation required for suite: "
                    + descriptor.suite().value());
        }
    }

    public void validateTimestamp(OcraCredentialDescriptor descriptor, Instant timestamp, Instant referenceInstant) {
        Objects.requireNonNull(descriptor, "descriptor");
        Optional<OcraTimestampSpecification> timestampSpec =
                descriptor.suite().dataInput().timestamp();

        if (timestampSpec.isEmpty()) {
            if (timestamp != null) {
                logValidationFailure(
                        descriptor.suite().value(),
                        descriptor.name(),
                        "VALIDATE_TIMESTAMP",
                        MESSAGE_ID_TIMESTAMP,
                        "timestamp not permitted for suite");
                throw new IllegalArgumentException("timestamp not permitted for suite: "
                        + descriptor.suite().value());
            }
            return;
        }

        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(referenceInstant, "referenceInstant");

        Duration allowed = descriptor
                .allowedTimestampDrift()
                .orElse(timestampSpec.orElseThrow().step());

        Duration delta = Duration.between(referenceInstant, timestamp).abs();
        if (delta.compareTo(allowed) > 0) {
            logValidationFailure(
                    descriptor.suite().value(),
                    descriptor.name(),
                    "VALIDATE_TIMESTAMP",
                    MESSAGE_ID_TIMESTAMP,
                    "timestamp outside permitted drift");
            throw new IllegalArgumentException(
                    "timestamp outside permitted drift (allowed=" + allowed + ", actual=" + delta + ")");
        }
    }

    static Logger telemetryLogger() {
        return TELEMETRY_LOGGER;
    }

    static void logValidationFailure(
            String suite, String credentialName, String failureCode, String messageId, String detail) {
        if (!TELEMETRY_LOGGER.isLoggable(Level.FINE)) {
            return;
        }
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("credentialName", credentialName);
        payload.put("suite", suite);
        payload.put("failureCode", failureCode);
        payload.put("messageId", messageId);
        if (detail != null && !detail.isBlank()) {
            payload.put("detail", detail);
        }
        TELEMETRY_LOGGER.log(Level.FINE, EVENT_NAME, payload);
    }

    private static boolean challengeMatchesFormat(String challenge, OcraChallengeFormat format) {
        return switch (format) {
            case NUMERIC -> challenge.chars().allMatch(Character::isDigit);
            case ALPHANUMERIC -> challenge.chars().allMatch(ch -> Character.isLetterOrDigit((char) ch));
            case HEX -> challenge.chars().allMatch(OcraCredentialFactory::isHexCharacter);
            case CHARACTER -> true;
        };
    }

    private static boolean isHexCharacter(int ch) {
        char c = Character.toUpperCase((char) ch);
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F');
    }

    public record OcraCredentialRequest(
            String name,
            String ocraSuite,
            String sharedSecret,
            SecretEncoding sharedSecretEncoding,
            Long counterValue,
            String pinHashHex,
            Duration allowedTimestampDrift,
            Map<String, String> metadata) {

        public OcraCredentialRequest {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(ocraSuite, "ocraSuite");
            Objects.requireNonNull(sharedSecretEncoding, "sharedSecretEncoding");
            // sharedSecret and other values are validated downstream to share diagnostics.
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }
}
