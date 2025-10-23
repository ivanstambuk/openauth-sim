package io.openauth.sim.rest.webauthn;

import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService;
import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService.GenerationCommand;
import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService.GenerationResult;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.trace.VerboseTrace;
import io.openauth.sim.rest.VerboseTracePayload;
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

        boolean verbose = Boolean.TRUE.equals(request.verbose());
        VerboseTrace.Builder trace = newTrace(verbose, "fido2.assertion.evaluate.stored");
        metadata(trace, "credentialSource", "stored");

        String credentialId =
                requireText(request.credentialId(), "credential_id_required", "Credential ID is required");
        metadata(trace, "credentialId", credentialId);

        String relyingPartyId =
                request.relyingPartyId() == null ? "" : request.relyingPartyId().trim();
        metadata(trace, "relyingPartyId", relyingPartyId);

        String origin = requireText(request.origin(), "origin_required", "Origin is required");
        metadata(trace, "origin", origin);

        String expectedType = resolveClientDataType(request.expectedType());
        metadata(trace, "expectedType", expectedType);

        byte[] challenge = decode("challenge", request.challenge());
        addStep(trace, step -> step.id("decode.challenge")
                .summary("Decode challenge")
                .detail("Base64URL decode")
                .attribute("length", challenge.length));

        String privateKey = requireText(request.privateKey(), "private_key_required", "Private key is required");
        addStep(trace, step -> step.id("construct.command")
                .summary("Construct stored evaluation command")
                .detail("GenerationCommand.Stored")
                .attribute("signatureCounter", request.signatureCounter())
                .attribute("userVerificationRequired", request.userVerificationRequired()));

        GenerationCommand.Stored command = new GenerationCommand.Stored(
                credentialId,
                relyingPartyId,
                origin,
                expectedType,
                challenge,
                privateKey,
                request.signatureCounter(),
                request.userVerificationRequired());

        GenerationResult result = invokeGenerator(command, trace);
        addStep(trace, step -> step.id("generate.assertion")
                .summary("Generate WebAuthn assertion")
                .detail("WebAuthnAssertionGenerationApplicationService.generate")
                .attribute("algorithm", result.algorithm().name())
                .attribute("credentialReference", result.credentialReference()));
        return buildResponse(result, "stored", buildTrace(trace));
    }

    WebAuthnEvaluationResponse evaluateInline(WebAuthnInlineEvaluationRequest request) {
        Objects.requireNonNull(request, "request");

        boolean verbose = Boolean.TRUE.equals(request.verbose());
        VerboseTrace.Builder trace = newTrace(verbose, "fido2.assertion.evaluate.inline");
        metadata(trace, "credentialSource", "inline");

        String relyingPartyId =
                requireText(request.relyingPartyId(), "relying_party_id_required", "Relying party ID is required");
        metadata(trace, "relyingPartyId", relyingPartyId);

        String origin = requireText(request.origin(), "origin_required", "Origin is required");
        metadata(trace, "origin", origin);

        String expectedType = resolveClientDataType(request.expectedType());
        metadata(trace, "expectedType", expectedType);

        WebAuthnSignatureAlgorithm algorithm = parseAlgorithm(request.algorithm());
        metadata(trace, "algorithm", algorithm.name());

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

        addStep(trace, step -> step.id("construct.command")
                .summary("Construct inline evaluation command")
                .detail("GenerationCommand.Inline")
                .attribute("credentialName", credentialName)
                .attribute("signatureCounter", signatureCounter)
                .attribute("userVerificationRequired", userVerificationRequired));

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

        GenerationResult result = invokeGenerator(command, trace);
        addStep(trace, step -> step.id("generate.assertion")
                .summary("Generate WebAuthn assertion")
                .detail("WebAuthnAssertionGenerationApplicationService.generate")
                .attribute("credentialReference", result.credentialReference()));
        return buildResponse(result, "inline", buildTrace(trace));
    }

    private static VerboseTrace.Builder newTrace(boolean verbose, String operation) {
        return verbose ? VerboseTrace.builder(operation) : null;
    }

    private static void metadata(VerboseTrace.Builder trace, String key, String value) {
        if (trace != null && value != null && !value.isBlank()) {
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

    private GenerationResult invokeGenerator(GenerationCommand command, VerboseTrace.Builder trace) {
        try {
            return generator.generate(command);
        } catch (IllegalArgumentException ex) {
            addStep(trace, step -> step.id("generator.failure")
                    .summary("WebAuthn assertion generation failed")
                    .detail("WebAuthnAssertionGenerationApplicationService.generate")
                    .note("message", ex.getMessage()));
            throw validation(mapGeneratorFailure(ex), ex.getMessage(), Map.of(), buildTrace(trace));
        } catch (RuntimeException ex) {
            addStep(trace, step -> step.id("generator.error")
                    .summary("Unexpected error during assertion generation")
                    .detail(ex.getClass().getName())
                    .note("message", ex.getMessage()));
            throw unexpected("WebAuthn assertion generation failed", ex, Map.of(), buildTrace(trace));
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

    private static WebAuthnEvaluationUnexpectedException unexpected(
            String message, Throwable cause, Map<String, Object> details, VerboseTrace trace) {
        return new WebAuthnEvaluationUnexpectedException(message, cause, Map.copyOf(details), trace);
    }

    private WebAuthnEvaluationResponse buildResponse(GenerationResult result, String source, VerboseTrace trace) {
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
        VerboseTracePayload tracePayload = trace == null ? null : VerboseTracePayload.from(trace);
        return new WebAuthnEvaluationResponse("generated", assertion, metadata, tracePayload);
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
        return validation(reasonCode, message, Map.of(), null);
    }

    private static WebAuthnEvaluationValidationException validation(
            String reasonCode, String message, Map<String, Object> details, VerboseTrace trace) {
        return new WebAuthnEvaluationValidationException(reasonCode, message, Map.copyOf(details), trace);
    }
}
