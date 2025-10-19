package io.openauth.sim.rest.webauthn;

import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService;
import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService.GenerationCommand;
import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService.GenerationResult;
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
class WebAuthnEvaluationService {

    private static final Logger TELEMETRY_LOGGER = Logger.getLogger("io.openauth.sim.rest.webauthn.telemetry");
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final String CLIENT_DATA_TYPE_GET = "webauthn.get";

    private final WebAuthnAssertionGenerationApplicationService generator;

    WebAuthnEvaluationService(WebAuthnAssertionGenerationApplicationService generator) {
        this.generator = Objects.requireNonNull(generator, "generator");
    }

    WebAuthnEvaluationResponse evaluateStored(WebAuthnStoredEvaluationRequest request) {
        Objects.requireNonNull(request, "request");

        String credentialId =
                requireText(request.credentialId(), "credential_id_required", "Credential ID is required");
        String relyingPartyId =
                request.relyingPartyId() == null ? "" : request.relyingPartyId().trim();
        String origin = requireText(request.origin(), "origin_required", "Origin is required");
        String expectedType = resolveClientDataType(request.expectedType());
        byte[] challenge = decode("challenge", request.challenge());
        String privateKey = requireText(request.privateKey(), "private_key_required", "Private key is required");

        GenerationCommand.Stored command = new GenerationCommand.Stored(
                credentialId,
                relyingPartyId,
                origin,
                expectedType,
                challenge,
                privateKey,
                request.signatureCounter(),
                request.userVerificationRequired());

        GenerationResult result = invokeGenerator(command);
        return buildResponse(result, "stored");
    }

    WebAuthnEvaluationResponse evaluateInline(WebAuthnInlineEvaluationRequest request) {
        Objects.requireNonNull(request, "request");

        String relyingPartyId =
                requireText(request.relyingPartyId(), "relying_party_id_required", "Relying party ID is required");
        String origin = requireText(request.origin(), "origin_required", "Origin is required");
        String expectedType = resolveClientDataType(request.expectedType());
        WebAuthnSignatureAlgorithm algorithm = parseAlgorithm(request.algorithm());
        long signatureCounter = Optional.ofNullable(request.signatureCounter())
                .orElseThrow(() -> validation("signature_counter_required", "Signature counter is required"));
        boolean userVerificationRequired =
                Optional.ofNullable(request.userVerificationRequired()).orElse(false);
        byte[] credentialId = decode("credentialId", request.credentialId());
        byte[] challenge = decode("challenge", request.challenge());
        String credentialName =
                request.credentialName() == null || request.credentialName().isBlank()
                        ? "inline"
                        : request.credentialName().trim();
        String privateKey = requireText(request.privateKey(), "private_key_required", "Private key is required");

        GenerationCommand.Inline command = new GenerationCommand.Inline(
                credentialName,
                credentialId,
                algorithm,
                relyingPartyId,
                origin,
                expectedType,
                signatureCounter,
                userVerificationRequired,
                challenge,
                privateKey);

        GenerationResult result = invokeGenerator(command);
        return buildResponse(result, "inline");
    }

    private GenerationResult invokeGenerator(GenerationCommand command) {
        try {
            return generator.generate(command);
        } catch (IllegalArgumentException ex) {
            throw validation(mapGeneratorFailure(ex), ex.getMessage());
        }
    }

    private static String mapGeneratorFailure(IllegalArgumentException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "generation_failed";
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("private key")) {
            return "private_key_invalid";
        }
        if (normalized.contains("jwk")
                || normalized.contains("pem")
                || normalized.contains("pkcs")
                || normalized.contains("key material")
                || normalized.contains("missing jwk field")) {
            return "private_key_invalid";
        }
        if (normalized.contains("credential not found")) {
            return "credential_not_found";
        }
        if (normalized.contains("relying party mismatch")) {
            return "relying_party_mismatch";
        }
        return "generation_failed";
    }

    private WebAuthnEvaluationResponse buildResponse(GenerationResult result, String source) {
        String telemetryId = nextTelemetryId();
        Map<String, Object> telemetryFields = telemetryFields(result, source);
        TelemetryFrame frame = TelemetryContracts.fido2EvaluationAdapter()
                .status("success", telemetryId, "generated", true, null, telemetryFields);
        logTelemetry(frame, source);

        WebAuthnEvaluationMetadata metadata = new WebAuthnEvaluationMetadata(
                telemetryId,
                source,
                result.credentialReference(),
                result.credentialReference() ? encode(result.credentialId()) : null,
                result.relyingPartyId(),
                result.origin(),
                result.algorithm().label(),
                result.userVerificationRequired(),
                null);

        WebAuthnGeneratedAssertion assertion = buildAssertion(result);
        return new WebAuthnEvaluationResponse("generated", assertion, metadata);
    }

    private static Map<String, Object> telemetryFields(GenerationResult result, String source) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("credentialSource", source);
        fields.put("credentialReference", result.credentialReference());
        fields.put("relyingPartyId", result.relyingPartyId());
        fields.put("origin", result.origin());
        fields.put("algorithm", result.algorithm().label());
        fields.put("userVerificationRequired", result.userVerificationRequired());
        fields.put("signatureCounter", result.signatureCounter());
        return fields;
    }

    private static WebAuthnGeneratedAssertion buildAssertion(GenerationResult result) {
        WebAuthnAssertionResponse payload = new WebAuthnAssertionResponse(
                encode(result.clientDataJson()), encode(result.authenticatorData()), encode(result.signature()));

        return new WebAuthnGeneratedAssertion(
                "public-key", encode(result.credentialId()), encode(result.credentialId()), payload);
    }

    private static String encode(byte[] data) {
        return URL_ENCODER.encodeToString(data);
    }

    private static byte[] decode(String field, String value) {
        if (value == null || value.isBlank()) {
            throw validation(field + "_required", field + " is required");
        }
        try {
            return URL_DECODER.decode(value);
        } catch (IllegalArgumentException ex) {
            throw validation(field + "_invalid", field + " must be Base64URL encoded");
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

    private static String requireText(String value, String reasonCode, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw validation(reasonCode, message);
        }
        return value.trim();
    }

    private static WebAuthnSignatureAlgorithm parseAlgorithm(String value) {
        if (value == null || value.isBlank()) {
            throw validation("algorithm_required", "Signature algorithm is required");
        }
        try {
            return WebAuthnSignatureAlgorithm.fromLabel(value.trim());
        } catch (IllegalArgumentException ex) {
            throw validation("algorithm_invalid", ex.getMessage());
        }
    }

    private static String nextTelemetryId() {
        return "rest-fido2-" + UUID.randomUUID();
    }

    private static void logTelemetry(TelemetryFrame frame, String credentialSource) {
        if (!TELEMETRY_LOGGER.isLoggable(Level.FINE)) {
            return;
        }
        StringBuilder builder = new StringBuilder("event=rest.fido2.evaluate")
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

    private static WebAuthnEvaluationValidationException validation(String reasonCode, String message) {
        return validation(reasonCode, message, Map.of());
    }

    private static WebAuthnEvaluationValidationException validation(
            String reasonCode, String message, Map<String, Object> details) {
        return new WebAuthnEvaluationValidationException(reasonCode, message, Map.copyOf(details));
    }
}
