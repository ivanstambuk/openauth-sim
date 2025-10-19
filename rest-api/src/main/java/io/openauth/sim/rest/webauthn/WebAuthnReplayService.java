package io.openauth.sim.rest.webauthn;

import io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService.TelemetryStatus;
import io.openauth.sim.application.fido2.WebAuthnPublicKeyDecoder;
import io.openauth.sim.application.fido2.WebAuthnReplayApplicationService;
import io.openauth.sim.application.fido2.WebAuthnReplayApplicationService.ReplayCommand;
import io.openauth.sim.application.fido2.WebAuthnReplayApplicationService.ReplayResult;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;

@Service
class WebAuthnReplayService {

    private static final Logger TELEMETRY_LOGGER = Logger.getLogger("io.openauth.sim.rest.webauthn.telemetry");
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final String CLIENT_DATA_TYPE_GET = "webauthn.get";

    private final WebAuthnReplayApplicationService applicationService;

    WebAuthnReplayService(WebAuthnReplayApplicationService applicationService) {
        this.applicationService = Objects.requireNonNull(applicationService, "applicationService");
    }

    WebAuthnReplayResponse replay(WebAuthnReplayRequest request) {
        Objects.requireNonNull(request, "request");
        Mode mode = determineMode(request);
        return switch (mode) {
            case STORED -> handleStoredReplay(request);
            case INLINE -> handleInlineReplay(request);
        };
    }

    private WebAuthnReplayResponse handleStoredReplay(WebAuthnReplayRequest request) {
        String credentialId =
                requireText(request.credentialId(), "credential_id_required", "Credential ID is required");
        String relyingPartyId =
                requireText(request.relyingPartyId(), "relying_party_id_required", "Relying party ID is required");
        String origin = requireText(request.origin(), "origin_required", "Origin is required");
        String expectedType = resolveClientDataType(request.expectedType());

        byte[] challenge = decode("expectedChallenge", request.expectedChallenge());
        byte[] clientData = decode("clientData", request.clientData());
        byte[] authenticatorData = decode("authenticatorData", request.authenticatorData());
        byte[] signature = decode("signature", request.signature());

        ReplayResult result = applicationService.replay(new ReplayCommand.Stored(
                credentialId,
                relyingPartyId,
                origin,
                expectedType,
                challenge,
                clientData,
                authenticatorData,
                signature));

        String telemetryId = nextTelemetryId();
        TelemetryFrame frame = result.replayFrame(TelemetryContracts.fido2ReplayAdapter(), telemetryId);
        logTelemetry(frame, "stored");

        Map<String, Object> combined = new LinkedHashMap<>(frame.fields());
        result.telemetry().fields().forEach(combined::putIfAbsent);

        if (result.telemetry().status() == TelemetryStatus.SUCCESS) {
            WebAuthnReplayMetadata metadata = buildMetadata(
                    result, "stored", combined, String.valueOf(combined.getOrDefault("telemetryId", telemetryId)));
            return new WebAuthnReplayResponse("match", result.telemetry().reasonCode(), result.match(), metadata);
        }

        if (result.telemetry().status() == TelemetryStatus.INVALID) {
            WebAuthnReplayMetadata metadata = buildMetadata(
                    result, "stored", combined, String.valueOf(combined.getOrDefault("telemetryId", telemetryId)));
            Map<String, Object> details = sanitizedDetails(combined);
            metadataDetails(metadata).forEach(details::putIfAbsent);
            throw validation(
                    result.telemetry().reasonCode(),
                    Optional.ofNullable(result.telemetry().reason())
                            .orElse(result.telemetry().reasonCode()),
                    details);
        }

        Map<String, Object> details = sanitizedDetails(combined);
        details.put("credentialSource", "stored");
        throw unexpected(
                "WebAuthn replay failed unexpectedly",
                Optional.ofNullable(result.telemetry().reason()).orElse("replay_failed"),
                details);
    }

    private WebAuthnReplayResponse handleInlineReplay(WebAuthnReplayRequest request) {
        String relyingPartyId =
                requireText(request.relyingPartyId(), "relying_party_id_required", "Relying party ID is required");
        String origin = requireText(request.origin(), "origin_required", "Origin is required");
        String expectedType = resolveClientDataType(request.expectedType());

        byte[] credentialId = decode("credentialId", request.credentialId());
        long signatureCounter = Optional.ofNullable(request.signatureCounter())
                .orElseThrow(() -> validation("signature_counter_required", "Signature counter is required"));
        boolean userVerificationRequired =
                Optional.ofNullable(request.userVerificationRequired()).orElse(Boolean.FALSE);
        WebAuthnSignatureAlgorithm algorithm = Optional.ofNullable(request.algorithm())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .map(WebAuthnSignatureAlgorithm::fromLabel)
                .orElseThrow(() -> validation("algorithm_required", "Signature algorithm is required"));

        byte[] publicKey = decodePublicKey(request.publicKey(), algorithm);

        byte[] challenge = decode("expectedChallenge", request.expectedChallenge());
        byte[] clientData = decode("clientData", request.clientData());
        byte[] authenticatorData = decode("authenticatorData", request.authenticatorData());
        byte[] signature = decode("signature", request.signature());

        ReplayResult result = applicationService.replay(new ReplayCommand.Inline(
                request.credentialName(),
                relyingPartyId,
                origin,
                expectedType,
                credentialId,
                publicKey,
                signatureCounter,
                userVerificationRequired,
                algorithm,
                challenge,
                clientData,
                authenticatorData,
                signature));

        String telemetryId = nextTelemetryId();
        TelemetryFrame frame = result.replayFrame(TelemetryContracts.fido2ReplayAdapter(), telemetryId);
        logTelemetry(frame, "inline");

        Map<String, Object> combined = new LinkedHashMap<>(frame.fields());
        result.telemetry().fields().forEach(combined::putIfAbsent);

        if (result.telemetry().status() == TelemetryStatus.SUCCESS) {
            WebAuthnReplayMetadata metadata = buildMetadata(
                    result, "inline", combined, String.valueOf(combined.getOrDefault("telemetryId", telemetryId)));
            return new WebAuthnReplayResponse("match", result.telemetry().reasonCode(), result.match(), metadata);
        }

        if (result.telemetry().status() == TelemetryStatus.INVALID) {
            WebAuthnReplayMetadata metadata = buildMetadata(
                    result, "inline", combined, String.valueOf(combined.getOrDefault("telemetryId", telemetryId)));
            Map<String, Object> details = sanitizedDetails(combined);
            metadataDetails(metadata).forEach(details::putIfAbsent);
            throw validation(
                    result.telemetry().reasonCode(),
                    Optional.ofNullable(result.telemetry().reason())
                            .orElse(result.telemetry().reasonCode()),
                    details);
        }

        Map<String, Object> details = sanitizedDetails(combined);
        details.put("credentialSource", "inline");
        throw unexpected(
                "WebAuthn replay failed unexpectedly",
                Optional.ofNullable(result.telemetry().reason()).orElse("replay_failed"),
                details);
    }

    private byte[] decodePublicKey(String value, WebAuthnSignatureAlgorithm algorithm) {
        String normalized = requireText(value, "public_key_required", "Public key is required");
        try {
            return WebAuthnPublicKeyDecoder.decode(normalized, algorithm);
        } catch (IllegalArgumentException ex) {
            String message = ex.getMessage() == null ? "Public key format is invalid" : ex.getMessage();
            throw validation("public_key_format_invalid", message);
        }
    }

    private Mode determineMode(WebAuthnReplayRequest request) {
        if (hasInlineIndicators(request)) {
            return Mode.INLINE;
        }
        return Mode.STORED;
    }

    private boolean hasInlineIndicators(WebAuthnReplayRequest request) {
        if (request == null) {
            return false;
        }
        if (hasText(request.publicKey())) {
            return true;
        }
        if (hasText(request.algorithm())) {
            return true;
        }
        if (request.signatureCounter() != null) {
            return true;
        }
        if (request.userVerificationRequired() != null) {
            return true;
        }
        if (hasText(request.credentialName())) {
            return true;
        }
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static WebAuthnReplayMetadata buildMetadata(
            ReplayResult result, String credentialSource, Map<String, Object> fields, String telemetryId) {
        String credentialId = result.credentialReference()
                ? (String) fields.getOrDefault(
                        "credentialId",
                        Optional.ofNullable(result.credentialId()).orElse(null))
                : null;
        String relyingPartyId = (String) fields.getOrDefault("relyingPartyId", "unknown");
        String origin = (String) fields.getOrDefault("origin", "unknown");
        String algorithm = (String) fields.getOrDefault("algorithm", "ES256");
        boolean userVerificationRequired =
                Boolean.parseBoolean(String.valueOf(fields.getOrDefault("userVerificationRequired", Boolean.FALSE)));
        String error =
                result.error().map(err -> err.name().toLowerCase(Locale.ROOT)).orElse((String) fields.get("error"));

        return new WebAuthnReplayMetadata(
                telemetryId,
                credentialSource,
                result.credentialReference(),
                credentialId,
                relyingPartyId,
                origin,
                algorithm,
                userVerificationRequired,
                error);
    }

    private static Map<String, Object> sanitizedDetails(Map<String, Object> fields) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        fields.forEach((key, value) -> {
            String lower = key.toLowerCase(Locale.ROOT);
            if (lower.contains("challenge")
                    || lower.contains("clientdata")
                    || lower.contains("signature")
                    || lower.contains("publickey")) {
                return;
            }
            sanitized.put(key, value);
        });
        return sanitized;
    }

    private static Map<String, Object> metadataDetails(WebAuthnReplayMetadata metadata) {
        if (metadata == null) {
            return Map.of();
        }
        Map<String, Object> details = new LinkedHashMap<>();
        putIfNotBlank(details, "telemetryId", metadata.telemetryId());
        putIfNotBlank(details, "credentialSource", metadata.credentialSource());
        details.put("credentialReference", metadata.credentialReference());
        putIfNotBlank(details, "credentialId", metadata.credentialId());
        putIfNotBlank(details, "relyingPartyId", metadata.relyingPartyId());
        putIfNotBlank(details, "origin", metadata.origin());
        putIfNotBlank(details, "algorithm", metadata.algorithm());
        details.put("userVerificationRequired", metadata.userVerificationRequired());
        putIfNotBlank(details, "error", metadata.error());
        return details;
    }

    private static void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private static void logTelemetry(TelemetryFrame frame, String credentialSource) {
        if (!TELEMETRY_LOGGER.isLoggable(Level.FINE)) {
            return;
        }
        StringBuilder builder = new StringBuilder("event=rest.fido2.replay")
                .append(" status=")
                .append(frame.status())
                .append(" credentialSource=")
                .append(credentialSource);
        frame.fields().forEach((key, value) -> {
            String lower = key.toLowerCase(Locale.ROOT);
            if (lower.contains("challenge")
                    || lower.contains("clientdata")
                    || lower.contains("signature")
                    || lower.contains("publickey")) {
                return;
            }
            builder.append(' ').append(key).append('=').append(value);
        });
        TELEMETRY_LOGGER.fine(builder.toString());
    }

    private static String requireText(String value, String reasonCode, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw validation(reasonCode, message);
        }
        return value.trim();
    }

    private static byte[] decode(String label, String value) {
        if (value == null || value.isBlank()) {
            throw validation(label + "_required", label + " is required");
        }
        try {
            return URL_DECODER.decode(value);
        } catch (IllegalArgumentException ex) {
            throw validation(label + "_invalid", label + " must be Base64URL encoded");
        }
    }

    private static String resolveClientDataType(String provided) {
        if (provided == null || provided.isBlank()) {
            return CLIENT_DATA_TYPE_GET;
        }
        String normalized = provided.trim();
        if (!CLIENT_DATA_TYPE_GET.equals(normalized)) {
            throw validation("type_invalid", "Client data type must be webauthn.get");
        }
        return normalized;
    }

    private static String nextTelemetryId() {
        return "rest-fido2-" + UUID.randomUUID();
    }

    private enum Mode {
        STORED,
        INLINE
    }

    private static WebAuthnReplayValidationException validation(String reasonCode, String message) {
        return validation(reasonCode, message, Map.of());
    }

    private static WebAuthnReplayValidationException validation(
            String reasonCode, String message, Map<String, Object> details) {
        return new WebAuthnReplayValidationException(reasonCode, message, Map.copyOf(details));
    }

    private static WebAuthnReplayUnexpectedException unexpected(
            String message, String reason, Map<String, Object> details) {
        Map<String, Object> merged = new LinkedHashMap<>(details);
        merged.putIfAbsent("reason", reason);
        return new WebAuthnReplayUnexpectedException(message, null, Map.copyOf(merged));
    }
}
