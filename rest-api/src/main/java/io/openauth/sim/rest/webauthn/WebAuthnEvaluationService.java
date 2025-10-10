package io.openauth.sim.rest.webauthn;

import io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService;
import io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService.EvaluationCommand;
import io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService.EvaluationResult;
import io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService.TelemetrySignal;
import io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService.TelemetryStatus;
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

  private static final Logger TELEMETRY_LOGGER =
      Logger.getLogger("io.openauth.sim.rest.webauthn.telemetry");
  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

  private final WebAuthnEvaluationApplicationService applicationService;

  WebAuthnEvaluationService(WebAuthnEvaluationApplicationService applicationService) {
    this.applicationService = Objects.requireNonNull(applicationService, "applicationService");
  }

  WebAuthnEvaluationResponse evaluateStored(WebAuthnStoredEvaluationRequest request) {
    Objects.requireNonNull(request, "request");
    String credentialId =
        requireText(request.credentialId(), "credential_id_required", "Credential ID is required");
    String relyingPartyId =
        requireText(
            request.relyingPartyId(), "relying_party_id_required", "Relying party ID is required");
    String origin = requireText(request.origin(), "origin_required", "Origin is required");
    String expectedType =
        requireText(
            request.expectedType(), "type_required", "Expected client data type is required");

    byte[] challenge = decode("expectedChallenge", request.expectedChallenge());
    byte[] clientData = decode("clientData", request.clientData());
    byte[] authenticatorData = decode("authenticatorData", request.authenticatorData());
    byte[] signature = decode("signature", request.signature());

    EvaluationResult result =
        applicationService.evaluate(
            new EvaluationCommand.Stored(
                credentialId,
                relyingPartyId,
                origin,
                expectedType,
                challenge,
                clientData,
                authenticatorData,
                signature));

    return handleResult(result, "stored");
  }

  WebAuthnEvaluationResponse evaluateInline(WebAuthnInlineEvaluationRequest request) {
    Objects.requireNonNull(request, "request");

    String relyingPartyId =
        requireText(
            request.relyingPartyId(), "relying_party_id_required", "Relying party ID is required");
    String origin = requireText(request.origin(), "origin_required", "Origin is required");
    String expectedType =
        requireText(
            request.expectedType(), "type_required", "Expected client data type is required");

    byte[] credentialId = decode("credentialId", request.credentialId());
    byte[] publicKey = decode("publicKey", request.publicKey());
    long signatureCounter =
        Optional.ofNullable(request.signatureCounter())
            .orElseThrow(
                () -> validation("signature_counter_required", "Signature counter is required"));
    boolean userVerificationRequired =
        Optional.ofNullable(request.userVerificationRequired()).orElse(Boolean.FALSE);
    WebAuthnSignatureAlgorithm algorithm =
        Optional.ofNullable(request.algorithm())
            .map(value -> value.trim().toUpperCase(Locale.ROOT))
            .map(WebAuthnSignatureAlgorithm::fromLabel)
            .orElseThrow(() -> validation("algorithm_required", "Signature algorithm is required"));

    byte[] challenge = decode("expectedChallenge", request.expectedChallenge());
    byte[] clientData = decode("clientData", request.clientData());
    byte[] authenticatorData = decode("authenticatorData", request.authenticatorData());
    byte[] signature = decode("signature", request.signature());

    EvaluationResult result =
        applicationService.evaluate(
            new EvaluationCommand.Inline(
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

    return handleResult(result, "inline");
  }

  private WebAuthnEvaluationResponse handleResult(
      EvaluationResult result, String credentialSource) {
    TelemetrySignal signal = result.telemetry();
    String telemetryId = nextTelemetryId();
    TelemetryFrame frame =
        result.evaluationFrame(TelemetryContracts.fido2EvaluationAdapter(), telemetryId);
    logTelemetry(frame, credentialSource);

    Map<String, Object> combined = combineFields(frame.fields(), signal.fields());

    if (signal.status() == TelemetryStatus.SUCCESS) {
      WebAuthnEvaluationMetadata metadata =
          buildMetadata(
              credentialSource,
              result,
              combined,
              String.valueOf(combined.getOrDefault("telemetryId", telemetryId)));

      return new WebAuthnEvaluationResponse(
          signal.reasonCode(), signal.reasonCode(), result.valid(), metadata);
    }

    if (signal.status() == TelemetryStatus.INVALID) {
      Map<String, Object> details = sanitizedDetails(combined);
      details.put("credentialSource", credentialSource);
      throw validation(
          signal.reasonCode(),
          Optional.ofNullable(signal.reason()).orElse(signal.reasonCode()),
          details);
    }

    Map<String, Object> details = sanitizedDetails(combined);
    details.put("credentialSource", credentialSource);
    throw unexpected(
        "WebAuthn evaluation failed unexpectedly",
        Optional.ofNullable(signal.reason()).orElse("evaluation_failed"),
        details);
  }

  private WebAuthnEvaluationMetadata buildMetadata(
      String credentialSource,
      EvaluationResult result,
      Map<String, Object> fields,
      String telemetryId) {

    String credentialId =
        result.credentialReference()
            ? (String) fields.getOrDefault("credentialId", result.credentialId())
            : null;
    String relyingPartyId =
        (String)
            fields.getOrDefault(
                "relyingPartyId", Optional.ofNullable(result.relyingPartyId()).orElse("unknown"));
    String origin = (String) fields.getOrDefault("origin", "unknown");
    String algorithm =
        Optional.ofNullable(result.algorithm())
            .map(WebAuthnSignatureAlgorithm::label)
            .orElse((String) fields.getOrDefault("algorithm", "ES256"));
    boolean userVerificationRequired =
        fields.containsKey("userVerificationRequired")
            ? Boolean.parseBoolean(String.valueOf(fields.get("userVerificationRequired")))
            : result.userVerificationRequired();
    String error =
        result
            .error()
            .map(err -> err.name().toLowerCase(Locale.ROOT))
            .orElse((String) fields.get("error"));

    return new WebAuthnEvaluationMetadata(
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

  private static Map<String, Object> combineFields(
      Map<String, Object> frameFields, Map<String, Object> telemetryFields) {
    Map<String, Object> combined = new LinkedHashMap<>(frameFields);
    telemetryFields.forEach(combined::putIfAbsent);
    return combined;
  }

  private static Map<String, Object> sanitizedDetails(Map<String, Object> fields) {
    Map<String, Object> sanitized = new LinkedHashMap<>();
    fields.forEach(
        (key, value) -> {
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

  private static void logTelemetry(TelemetryFrame frame, String credentialSource) {
    if (!TELEMETRY_LOGGER.isLoggable(Level.FINE)) {
      return;
    }
    StringBuilder builder =
        new StringBuilder("event=rest.fido2.evaluate")
            .append(" status=")
            .append(frame.status())
            .append(" credentialSource=")
            .append(credentialSource);
    frame
        .fields()
        .forEach(
            (key, value) -> {
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

  private static String nextTelemetryId() {
    return "rest-fido2-" + UUID.randomUUID();
  }

  private static WebAuthnEvaluationValidationException validation(
      String reasonCode, String message) {
    return validation(reasonCode, message, Map.of());
  }

  private static WebAuthnEvaluationValidationException validation(
      String reasonCode, String message, Map<String, Object> details) {
    return new WebAuthnEvaluationValidationException(reasonCode, message, Map.copyOf(details));
  }

  private static WebAuthnEvaluationUnexpectedException unexpected(
      String message, String reason, Map<String, Object> details) {
    Map<String, Object> merged = new LinkedHashMap<>(details);
    merged.putIfAbsent("reason", reason);
    return new WebAuthnEvaluationUnexpectedException(message, null, Map.copyOf(merged));
  }
}
