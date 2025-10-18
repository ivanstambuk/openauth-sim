package io.openauth.sim.rest.webauthn;

import io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationService;
import io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationService.GeneratedAttestation;
import io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationService.GenerationResult;
import io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationService.TelemetrySignal;
import io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationService.TelemetryStatus;
import io.openauth.sim.application.fido2.WebAuthnAttestationReplayApplicationService;
import io.openauth.sim.application.fido2.WebAuthnAttestationReplayApplicationService.ReplayCommand;
import io.openauth.sim.application.fido2.WebAuthnAttestationReplayApplicationService.ReplayResult;
import io.openauth.sim.application.fido2.WebAuthnTrustAnchorResolver;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;

@Service
class WebAuthnAttestationService {

  private static final Logger TELEMETRY_LOGGER =
      Logger.getLogger("io.openauth.sim.rest.webauthn.attestation.telemetry");
  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
  private static final String EVENT_ATTEST = "rest.fido2.attest";
  private static final String EVENT_ATTEST_REPLAY = "rest.fido2.attestReplay";

  private final WebAuthnAttestationGenerationApplicationService generationService;
  private final WebAuthnAttestationReplayApplicationService replayService;
  private final WebAuthnTrustAnchorResolver trustAnchorResolver;

  WebAuthnAttestationService(
      WebAuthnAttestationGenerationApplicationService generationService,
      WebAuthnAttestationReplayApplicationService replayService,
      WebAuthnTrustAnchorResolver trustAnchorResolver) {
    this.generationService = Objects.requireNonNull(generationService, "generationService");
    this.replayService = Objects.requireNonNull(replayService, "replayService");
    this.trustAnchorResolver = Objects.requireNonNull(trustAnchorResolver, "trustAnchorResolver");
  }

  WebAuthnAttestationResponse generate(WebAuthnAttestationGenerationRequest request) {
    Objects.requireNonNull(request, "request");

    InputSource source = parseInputSource(request.inputSource());
    WebAuthnAttestationFormat format = parseFormat(request.format());

    GenerationResult result;
    if (source == InputSource.PRESET) {
      String attestationId =
          requireText(request.attestationId(), "attestation_id_required", "Attestation ID");
      String relyingPartyId =
          requireText(request.relyingPartyId(), "relying_party_id_required", "Relying party ID");
      String origin = requireText(request.origin(), "origin_required", "Origin");

      byte[] challenge =
          decode(
              "challenge_required",
              "Invalid challenge (must be Base64URL)",
              requireText(request.challenge(), "challenge_required", "Challenge"));

      String credentialPrivateKey =
          requireText(
              request.credentialPrivateKey(),
              "credential_private_key_required",
              "Credential private key");
      String attestationPrivateKey =
          requireText(
              request.attestationPrivateKey(),
              "attestation_private_key_required",
              "Attestation private key");
      String attestationSerial =
          requireText(
              request.attestationCertificateSerial(),
              "attestation_serial_required",
              "Attestation certificate serial");
      String signingMode =
          requireText(request.signingMode(), "signing_mode_required", "Signing mode").trim();

      List<String> customRoots = sanitizeRoots(request.customRootCertificates());

      var command =
          new WebAuthnAttestationGenerationApplicationService.GenerationCommand.Inline(
              attestationId,
              format,
              relyingPartyId,
              origin,
              challenge,
              credentialPrivateKey,
              attestationPrivateKey,
              attestationSerial,
              parseSigningMode(signingMode),
              customRoots,
              customRoots.isEmpty() ? "" : "inline");

      try {
        result = generationService.generate(command);
      } catch (IllegalArgumentException ex) {
        throw validation(
            "generation_invalid",
            ex.getMessage() == null ? "Attestation generation failed" : ex.getMessage(),
            Map.of("attestationId", attestationId, "format", format.label()));
      } catch (Exception ex) {
        throw unexpected(
            "generation_failed",
            "Attestation generation failed: " + sanitize(ex.getMessage()),
            Map.of("attestationId", attestationId, "format", format.label()));
      }
    } else {
      // MANUAL input source
      String relyingPartyId =
          requireText(request.relyingPartyId(), "relying_party_id_required", "Relying party ID");
      String origin = requireText(request.origin(), "origin_required", "Origin");
      byte[] challenge =
          decode(
              "challenge_required",
              "Invalid challenge (must be Base64URL)",
              requireText(request.challenge(), "challenge_required", "Challenge"));

      String credentialPrivateKey =
          requireText(
              request.credentialPrivateKey(),
              "credential_private_key_required",
              "Credential private key");
      String signingMode =
          requireText(request.signingMode(), "signing_mode_required", "Signing mode").trim();

      List<String> customRoots = sanitizeRoots(request.customRootCertificates());

      WebAuthnAttestationGenerator.SigningMode mode = parseSigningMode(signingMode);
      String attestationPrivateKey = sanitize(request.attestationPrivateKey());
      String attestationSerial = sanitize(request.attestationCertificateSerial());
      if (mode != WebAuthnAttestationGenerator.SigningMode.UNSIGNED) {
        if (attestationPrivateKey.isBlank()) {
          throw validation(
              "attestation_private_key_required",
              "Attestation private key is required for signed modes",
              Map.of());
        }
        if (attestationSerial.isBlank()) {
          throw validation(
              "attestation_serial_required",
              "Attestation certificate serial is required for signed modes",
              Map.of());
        }
      }
      if (mode == WebAuthnAttestationGenerator.SigningMode.CUSTOM_ROOT && customRoots.isEmpty()) {
        throw validation(
            "custom_root_required",
            "At least one custom root certificate is required for custom-root mode",
            Map.of());
      }

      var command =
          new WebAuthnAttestationGenerationApplicationService.GenerationCommand.Manual(
              format,
              relyingPartyId,
              origin,
              challenge,
              credentialPrivateKey,
              attestationPrivateKey.isBlank() ? null : attestationPrivateKey,
              attestationSerial.isBlank() ? null : attestationSerial,
              mode,
              customRoots,
              customRoots.isEmpty() ? "" : "inline",
              sanitize(request.seedPresetId()),
              request.overrides() == null ? List.of() : List.copyOf(request.overrides()));

      try {
        result = generationService.generate(command);
      } catch (IllegalArgumentException ex) {
        throw validation(
            "generation_invalid",
            ex.getMessage() == null ? "Attestation generation failed" : ex.getMessage(),
            Map.of("format", format.label(), "inputSource", "MANUAL"));
      } catch (Exception ex) {
        throw unexpected(
            "generation_failed",
            "Attestation generation failed: " + sanitize(ex.getMessage()),
            Map.of("format", format.label(), "inputSource", "MANUAL"));
      }
    }

    TelemetrySignal telemetry = result.telemetry();
    if (telemetry.status() != TelemetryStatus.SUCCESS) {
      throw unexpected(telemetry.reasonCode(), "Attestation generation failed", telemetry.fields());
    }

    String telemetryId = nextTelemetryId(EVENT_ATTEST);
    TelemetryFrame frame = telemetry.emit(TelemetryContracts.fido2AttestAdapter(), telemetryId);
    logTelemetry(frame, EVENT_ATTEST);

    GeneratedAttestation attestation = result.attestation();
    WebAuthnGeneratedAttestation.AttestationResponse responsePayload =
        new WebAuthnGeneratedAttestation.AttestationResponse(
            attestation.response().clientDataJson(), attestation.response().attestationObject());

    WebAuthnGeneratedAttestation generated =
        new WebAuthnGeneratedAttestation(
            attestation.type(),
            attestation.id(),
            attestation.rawId(),
            attestation.attestationId(),
            attestation.format().label(),
            responsePayload);

    WebAuthnAttestationMetadata metadata =
        WebAuthnAttestationMetadata.forGeneration(
            telemetryId, telemetry.reasonCode(), format.label(), telemetry.fields());

    return new WebAuthnAttestationResponse("success", generated, null, metadata);
  }

  private static List<String> sanitizeRoots(List<String> roots) {
    return roots == null
        ? List.of()
        : roots.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList();
  }

  WebAuthnAttestationResponse replay(WebAuthnAttestationReplayRequest request) {
    Objects.requireNonNull(request, "request");

    WebAuthnAttestationFormat format = parseFormat(request.format());
    WebAuthnTrustAnchorResolver.Resolution anchorResolution =
        trustAnchorResolver.resolvePemStrings(
            request.attestationId(), format, request.trustAnchors());
    logTrustAnchorWarnings(anchorResolution.warnings());

    ReplayCommand.Inline command =
        new ReplayCommand.Inline(
            sanitize(request.attestationId()),
            format,
            requireText(request.relyingPartyId(), "relying_party_id_required", "Relying party ID"),
            requireText(request.origin(), "origin_required", "Origin"),
            decode(
                "attestation_object_required",
                "Invalid attestation object",
                request.attestationObject()),
            decode("client_data_required", "Invalid clientDataJSON", request.clientDataJson()),
            decode("expected_challenge_required", "Invalid challenge", request.expectedChallenge()),
            anchorResolution.anchors(),
            anchorResolution.cached(),
            anchorResolution.source(),
            anchorResolution.metadataEntryId(),
            anchorResolution.warnings());

    ReplayResult result;
    try {
      result = replayService.replay(command);
    } catch (IllegalArgumentException ex) {
      throw validation(
          "replay_invalid",
          ex.getMessage() == null ? "Attestation replay failed" : ex.getMessage(),
          Map.of("attestationId", command.attestationId(), "format", format.label()));
    } catch (Exception ex) {
      throw unexpected(
          "replay_failed",
          "Attestation replay failed: " + sanitize(ex.getMessage()),
          Map.of("attestationId", command.attestationId(), "format", format.label()));
    }

    io.openauth.sim.application.fido2.WebAuthnAttestationReplayApplicationService.TelemetrySignal
        telemetry = result.telemetry();
    String telemetryId = nextTelemetryId(EVENT_ATTEST_REPLAY);
    TelemetryFrame frame =
        telemetry.emit(TelemetryContracts.fido2AttestReplayAdapter(), telemetryId);
    logTelemetry(frame, EVENT_ATTEST_REPLAY);
    Map<String, Object> metadataFields = Map.copyOf(frame.fields());

    if (telemetry.status()
        == io.openauth.sim.application.fido2.WebAuthnAttestationReplayApplicationService
            .TelemetryStatus.SUCCESS) {
      io.openauth.sim.application.fido2.WebAuthnAttestationReplayApplicationService
              .AttestedCredential
          credential =
              result
                  .attestedCredential()
                  .orElseThrow(
                      () ->
                          unexpected(
                              "attested_credential_missing",
                              "Attestation replay succeeded without credential metadata",
                              metadataFields));
      WebAuthnAttestedCredential attestedCredential =
          new WebAuthnAttestedCredential(
              credential.relyingPartyId(),
              credential.credentialId(),
              credential.algorithm().label(),
              credential.userVerificationRequired(),
              credential.signatureCounter(),
              credential.aaguid());
      WebAuthnAttestationMetadata metadata =
          WebAuthnAttestationMetadata.forReplay(
              telemetryId,
              telemetry.reasonCode(),
              format.label(),
              metadataFields,
              command.attestationId(),
              command.trustAnchorWarnings());
      return new WebAuthnAttestationResponse("success", null, attestedCredential, metadata);
    }

    if (telemetry.status()
        == io.openauth.sim.application.fido2.WebAuthnAttestationReplayApplicationService
            .TelemetryStatus.INVALID) {
      String message =
          Optional.ofNullable(telemetry.reason())
              .filter(str -> !str.isBlank())
              .orElse("Attestation replay invalid");
      throw validation(telemetry.reasonCode(), message, metadataFields);
    }

    throw unexpected(
        telemetry.reasonCode(),
        Optional.ofNullable(telemetry.reason()).orElse("Attestation replay failed"),
        metadataFields);
  }

  private static WebAuthnAttestationFormat parseFormat(String format) {
    try {
      return WebAuthnAttestationFormat.fromLabel(
          requireText(format, "attestation_format_required", "Attestation format"));
    } catch (IllegalArgumentException ex) {
      throw validation(
          "attestation_format_invalid",
          "Unsupported attestation format: " + format,
          Map.of("format", format));
    }
  }

  private static WebAuthnAttestationGenerator.SigningMode parseSigningMode(String signingMode) {
    String normalized = signingMode.trim().toLowerCase(Locale.ROOT);
    try {
      return switch (normalized) {
        case "self-signed", "self_signed" -> WebAuthnAttestationGenerator.SigningMode.SELF_SIGNED;
        case "unsigned" -> WebAuthnAttestationGenerator.SigningMode.UNSIGNED;
        case "custom-root", "custom_root" -> WebAuthnAttestationGenerator.SigningMode.CUSTOM_ROOT;
        default ->
            throw new IllegalArgumentException(
                "Unsupported signing mode: "
                    + signingMode
                    + " (expected self-signed, unsigned, or custom-root)");
      };
    } catch (IllegalArgumentException ex) {
      throw validation("signing_mode_invalid", ex.getMessage(), Map.of("signingMode", signingMode));
    }
  }

  private static String sanitize(String value) {
    if (value == null) {
      return "";
    }
    return value.trim();
  }

  private enum InputSource {
    PRESET,
    MANUAL
  }

  private static InputSource parseInputSource(String input) {
    if (input == null || input.isBlank()) {
      return InputSource.PRESET;
    }
    String normalized = input.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "manual" -> InputSource.MANUAL;
      case "preset" -> InputSource.PRESET;
      default -> InputSource.PRESET;
    };
  }

  private static String requireText(String value, String reasonCode, String fieldDescription) {
    if (value == null || value.isBlank()) {
      throw validation(reasonCode, fieldDescription + " is required", Map.of());
    }
    return value.trim();
  }

  private static byte[] decode(String reasonCode, String message, String value) {
    try {
      return URL_DECODER.decode(requireText(value, reasonCode, message));
    } catch (IllegalArgumentException ex) {
      throw validation(reasonCode, message, Map.of());
    }
  }

  private static String nextTelemetryId(String event) {
    return event + "-" + UUID.randomUUID();
  }

  private void logTelemetry(TelemetryFrame frame, String event) {
    if (TELEMETRY_LOGGER.isLoggable(Level.INFO)) {
      TELEMETRY_LOGGER.log(Level.INFO, () -> event + " " + frame.fields());
    }
  }

  private void logTrustAnchorWarnings(List<String> warnings) {
    if (warnings == null || warnings.isEmpty()) {
      return;
    }
    if (TELEMETRY_LOGGER.isLoggable(Level.WARNING)) {
      warnings.stream()
          .filter(message -> message != null && !message.isBlank())
          .forEach(message -> TELEMETRY_LOGGER.log(Level.WARNING, message));
    }
  }

  private static WebAuthnAttestationValidationException validation(
      String reasonCode, String message, Map<String, Object> metadata) {
    return new WebAuthnAttestationValidationException(
        sanitize(reasonCode), sanitize(message), metadata, Map.of());
  }

  private static WebAuthnAttestationUnexpectedException unexpected(
      String reasonCode, String message, Map<String, Object> metadata) {
    return new WebAuthnAttestationUnexpectedException(
        sanitize(reasonCode), sanitize(message), metadata);
  }
}
