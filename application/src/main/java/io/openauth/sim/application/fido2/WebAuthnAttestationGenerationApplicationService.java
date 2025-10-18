package io.openauth.sim.application.fido2;

import io.openauth.sim.application.telemetry.Fido2TelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.SigningMode;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Application service that generates WebAuthn attestation payloads with sanitized telemetry. */
public final class WebAuthnAttestationGenerationApplicationService {

  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

  private final WebAuthnAttestationGenerator generator;
  private final Fido2TelemetryAdapter telemetryAdapter;

  public WebAuthnAttestationGenerationApplicationService(
      WebAuthnAttestationGenerator generator, Fido2TelemetryAdapter telemetryAdapter) {
    this.generator = Objects.requireNonNull(generator, "generator");
    this.telemetryAdapter = Objects.requireNonNull(telemetryAdapter, "telemetryAdapter");
  }

  public WebAuthnAttestationGenerationApplicationService() {
    this(new WebAuthnAttestationGenerator(), new Fido2TelemetryAdapter("fido2.attest"));
  }

  public GenerationResult generate(GenerationCommand command) {
    Objects.requireNonNull(command, "command");
    WebAuthnAttestationGenerator.GenerationResult generatorResult;
    boolean manualSource;
    if (command instanceof GenerationCommand.Inline inline) {
      manualSource = false;
      WebAuthnAttestationGenerator.GenerationCommand.Inline coreCommand =
          new WebAuthnAttestationGenerator.GenerationCommand.Inline(
              inline.attestationId(),
              inline.format(),
              inline.relyingPartyId(),
              inline.origin(),
              inline.challenge(),
              inline.credentialPrivateKeyBase64Url(),
              inline.attestationPrivateKeyBase64Url(),
              inline.attestationCertificateSerialBase64Url(),
              inline.signingMode(),
              inline.customRootCertificatesPem());
      generatorResult = generator.generate(coreCommand);
    } else {
      manualSource = true;
      GenerationCommand.Manual manual = (GenerationCommand.Manual) command;
      WebAuthnAttestationGenerator.GenerationCommand.Manual coreCommand =
          new WebAuthnAttestationGenerator.GenerationCommand.Manual(
              manual.format(),
              manual.relyingPartyId(),
              manual.origin(),
              manual.challenge(),
              manual.credentialPrivateKeyBase64Url(),
              manual.attestationPrivateKeyBase64Url(),
              manual.attestationCertificateSerialBase64Url(),
              manual.signingMode(),
              manual.customRootCertificatesPem());
      generatorResult = generator.generate(coreCommand);
    }

    String derivedCredentialId = generatorResult.attestationId();
    if (manualSource) {
      GenerationCommand.Manual manual = (GenerationCommand.Manual) command;
      String seed = normalize(manual.seedPresetId());
      if (!seed.isBlank()) {
        derivedCredentialId = seed;
      }
    }

    GeneratedAttestation.Response responsePayload =
        new GeneratedAttestation.Response(
            encode(generatorResult.clientDataJson()), encode(generatorResult.attestationObject()));

    GeneratedAttestation attestation =
        new GeneratedAttestation(
            "public-key",
            derivedCredentialId,
            derivedCredentialId,
            generatorResult.attestationId(),
            generatorResult.format(),
            responsePayload);

    Map<String, Object> telemetryFields = new LinkedHashMap<>();
    telemetryFields.put("attestationId", command.attestationId());
    telemetryFields.put("attestationFormat", command.format().label());
    telemetryFields.put("generationMode", telemetryMode(command.signingMode()));
    telemetryFields.put("signatureIncluded", generatorResult.signatureIncluded());
    telemetryFields.put("customRootCount", command.customRootCertificatesPem().size());
    telemetryFields.put("certificateChainCount", generatorResult.certificateChainPem().size());
    telemetryFields.put("inputSource", manualSource ? "manual" : "preset");

    if (!command.customRootCertificatesPem().isEmpty()) {
      String source = normalize(command.customRootSource());
      telemetryFields.put("customRootSource", source.isBlank() ? "inline" : source);
    }

    if (manualSource) {
      GenerationCommand.Manual manual = (GenerationCommand.Manual) command;
      if (!normalize(manual.seedPresetId()).isBlank()) {
        telemetryFields.put("seedPresetId", normalize(manual.seedPresetId()));
      }
      if (manual.overrides() != null && !manual.overrides().isEmpty()) {
        telemetryFields.put("overrides", List.copyOf(manual.overrides()));
      }
    }

    TelemetrySignal telemetry =
        new SuccessTelemetrySignal(Map.copyOf(telemetryFields), telemetryAdapter);

    return new GenerationResult(attestation, telemetry);
  }

  /** Marker interface for generation commands. */
  public sealed interface GenerationCommand
      permits GenerationCommand.Inline, GenerationCommand.Manual {
    String attestationId();

    WebAuthnAttestationFormat format();

    String relyingPartyId();

    String origin();

    byte[] challenge();

    String credentialPrivateKeyBase64Url();

    String attestationPrivateKeyBase64Url();

    String attestationCertificateSerialBase64Url();

    SigningMode signingMode();

    List<String> customRootCertificatesPem();

    String customRootSource();

    /** Inline command carrier. */
    record Inline(
        String attestationId,
        WebAuthnAttestationFormat format,
        String relyingPartyId,
        String origin,
        byte[] challenge,
        String credentialPrivateKeyBase64Url,
        String attestationPrivateKeyBase64Url,
        String attestationCertificateSerialBase64Url,
        SigningMode signingMode,
        List<String> customRootCertificatesPem,
        String customRootSource)
        implements GenerationCommand {

      public Inline {
        Objects.requireNonNull(attestationId, "attestationId");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(relyingPartyId, "relyingPartyId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(challenge, "challenge");
        Objects.requireNonNull(credentialPrivateKeyBase64Url, "credentialPrivateKeyBase64Url");
        Objects.requireNonNull(signingMode, "signingMode");
        customRootCertificatesPem =
            customRootCertificatesPem == null ? List.of() : List.copyOf(customRootCertificatesPem);
        customRootSource = normalize(customRootSource);
      }
    }

    /** Manual command carrier. */
    record Manual(
        WebAuthnAttestationFormat format,
        String relyingPartyId,
        String origin,
        byte[] challenge,
        String credentialPrivateKeyBase64Url,
        String attestationPrivateKeyBase64Url,
        String attestationCertificateSerialBase64Url,
        SigningMode signingMode,
        List<String> customRootCertificatesPem,
        String customRootSource,
        String seedPresetId,
        List<String> overrides)
        implements GenerationCommand {

      public Manual {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(relyingPartyId, "relyingPartyId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(challenge, "challenge");
        Objects.requireNonNull(credentialPrivateKeyBase64Url, "credentialPrivateKeyBase64Url");
        Objects.requireNonNull(signingMode, "signingMode");
        customRootCertificatesPem =
            customRootCertificatesPem == null ? List.of() : List.copyOf(customRootCertificatesPem);
        customRootSource = normalize(customRootSource);
        seedPresetId = normalize(seedPresetId);
        overrides = overrides == null ? List.of() : List.copyOf(overrides);
      }

      @Override
      public String attestationId() {
        return "manual";
      }
    }
  }

  /** Result payload for attestation generation requests. */
  public record GenerationResult(GeneratedAttestation attestation, TelemetrySignal telemetry) {
    // Accessors only; canonical components suffice for transport.
  }

  /** Generated attestation payload details. */
  public record GeneratedAttestation(
      String type,
      String id,
      String rawId,
      String attestationId,
      WebAuthnAttestationFormat format,
      Response response) {

    /** Nested attestation response mirroring WebAuthn assertions. */
    public record Response(String clientDataJson, String attestationObject) {
      // Canonical response view for JSON mapping.
    }
  }

  /** Telemetry signal for attestation generation outcomes. */
  public interface TelemetrySignal {
    TelemetryStatus status();

    String reasonCode();

    Map<String, Object> fields();

    TelemetryFrame emit(Fido2TelemetryAdapter adapter, String telemetryId);
  }

  /** Telemetry status indicator. */
  public enum TelemetryStatus {
    SUCCESS,
    ERROR
  }

  private static String encode(byte[] value) {
    return URL_ENCODER.encodeToString(value);
  }

  private static String telemetryMode(SigningMode signingMode) {
    return switch (signingMode) {
      case SELF_SIGNED -> "self_signed";
      case UNSIGNED -> "unsigned";
      case CUSTOM_ROOT -> "custom_root";
    };
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  private static final class SuccessTelemetrySignal implements TelemetrySignal {

    private final Map<String, Object> fields;
    private final Fido2TelemetryAdapter fallbackAdapter;

    private SuccessTelemetrySignal(
        Map<String, Object> fields, Fido2TelemetryAdapter fallbackAdapter) {
      this.fields = fields;
      this.fallbackAdapter = fallbackAdapter;
    }

    @Override
    public TelemetryStatus status() {
      return TelemetryStatus.SUCCESS;
    }

    @Override
    public String reasonCode() {
      return "generated";
    }

    @Override
    public Map<String, Object> fields() {
      return fields;
    }

    @Override
    public TelemetryFrame emit(Fido2TelemetryAdapter adapter, String telemetryId) {
      Fido2TelemetryAdapter target = adapter != null ? adapter : fallbackAdapter;
      return target.status("success", telemetryId, reasonCode(), true, null, fields);
    }
  }
}
