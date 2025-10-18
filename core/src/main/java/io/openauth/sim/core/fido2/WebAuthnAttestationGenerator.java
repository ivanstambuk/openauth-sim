package io.openauth.sim.core.fido2;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/** Generates attestation payloads from deterministic fixtures or manual inputs. */
public final class WebAuthnAttestationGenerator {

  private static final Base64.Encoder MIME_ENCODER = Base64.getMimeEncoder(64, new byte[] {'\n'});

  private static final String ERROR_PREFIX = "Unexpected attestation generation input: ";

  /** Supported attestation signing modes. */
  public enum SigningMode {
    SELF_SIGNED,
    UNSIGNED,
    CUSTOM_ROOT
  }

  /** Command marker for attestation generation requests. */
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

    /** Inline attestation generation command (PRESET input source). */
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
        List<String> customRootCertificatesPem)
        implements GenerationCommand {

      public Inline {
        Objects.requireNonNull(attestationId, "attestationId");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(relyingPartyId, "relyingPartyId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(challenge, "challenge");
        Objects.requireNonNull(credentialPrivateKeyBase64Url, "credentialPrivateKeyBase64Url");
        Objects.requireNonNull(signingMode, "signingMode");
        if (customRootCertificatesPem == null) {
          customRootCertificatesPem = List.of();
        } else {
          customRootCertificatesPem =
              customRootCertificatesPem.stream()
                  .filter(Objects::nonNull)
                  .filter(value -> !value.trim().isEmpty())
                  .toList();
        }
      }
    }

    /** Manual attestation generation command (no preset fixture id). */
    record Manual(
        WebAuthnAttestationFormat format,
        String relyingPartyId,
        String origin,
        byte[] challenge,
        String credentialPrivateKeyBase64Url,
        String attestationPrivateKeyBase64Url,
        String attestationCertificateSerialBase64Url,
        SigningMode signingMode,
        List<String> customRootCertificatesPem)
        implements GenerationCommand {

      public Manual {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(relyingPartyId, "relyingPartyId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(challenge, "challenge");
        Objects.requireNonNull(credentialPrivateKeyBase64Url, "credentialPrivateKeyBase64Url");
        Objects.requireNonNull(signingMode, "signingMode");
        if (customRootCertificatesPem == null) {
          customRootCertificatesPem = List.of();
        } else {
          customRootCertificatesPem =
              customRootCertificatesPem.stream()
                  .filter(Objects::nonNull)
                  .map(String::trim)
                  .filter(s -> !s.isEmpty())
                  .toList();
        }
      }

      @Override
      public String attestationId() {
        return "manual";
      }
    }
  }

  /** Result payload for generated attestation objects. */
  public record GenerationResult(
      String attestationId,
      WebAuthnAttestationFormat format,
      byte[] attestationObject,
      byte[] clientDataJson,
      byte[] expectedChallenge,
      List<String> certificateChainPem,
      boolean signatureIncluded,
      byte[] credentialId) {
    // Provides canonical accessors for generation outputs.
  }

  /** Generates a WebAuthn attestation payload. */
  public GenerationResult generate(GenerationCommand command) {
    Objects.requireNonNull(command, "command");
    if (command instanceof GenerationCommand.Inline inline) {
      WebAuthnAttestationFixtures.WebAuthnAttestationVector vector =
          WebAuthnAttestationFixtures.findById(inline.attestationId())
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          ERROR_PREFIX + "unknown attestationId " + inline.attestationId()));

      validateInline(inline, vector);

      byte[] attestationObject = vector.registration().attestationObject().clone();
      byte[] clientDataJson = vector.registration().clientDataJson().clone();
      byte[] expectedChallenge = inline.challenge().clone();

      List<String> certificateChain =
          switch (inline.signingMode()) {
            case SELF_SIGNED -> convertCertificatesToPem(vector);
            case UNSIGNED -> List.of();
            case CUSTOM_ROOT -> {
              if (inline.customRootCertificatesPem().isEmpty()) {
                throw new IllegalArgumentException(
                    ERROR_PREFIX + "custom-root signing requires at least one certificate");
              }
              yield List.copyOf(inline.customRootCertificatesPem());
            }
          };

      boolean signatureIncluded = inline.signingMode() != SigningMode.UNSIGNED;

      return new GenerationResult(
          inline.attestationId(),
          inline.format(),
          attestationObject,
          clientDataJson,
          expectedChallenge,
          certificateChain,
          signatureIncluded,
          vector.registration().credentialId().clone());
    }

    // Manual input source: synthesize clientDataJSON and reuse a format template for
    // attestationObject.
    GenerationCommand.Manual manual = (GenerationCommand.Manual) command;

    WebAuthnAttestationFixtures.WebAuthnAttestationVector template = templateFor(manual.format());

    validateManual(manual);

    byte[] attestationObject = template.registration().attestationObject().clone();
    byte[] clientDataJson = buildClientDataJson(manual.origin(), manual.challenge());
    byte[] expectedChallenge = manual.challenge().clone();

    List<String> certificateChain =
        switch (manual.signingMode()) {
          case SELF_SIGNED -> convertCertificatesToPem(template);
          case UNSIGNED -> List.of();
          case CUSTOM_ROOT -> {
            if (manual.customRootCertificatesPem().isEmpty()) {
              throw new IllegalArgumentException(
                  ERROR_PREFIX + "custom-root signing requires at least one certificate");
            }
            yield List.copyOf(manual.customRootCertificatesPem());
          }
        };

    boolean signatureIncluded = manual.signingMode() != SigningMode.UNSIGNED;

    return new GenerationResult(
        manual.attestationId(),
        manual.format(),
        attestationObject,
        clientDataJson,
        expectedChallenge,
        certificateChain,
        signatureIncluded,
        template.registration().credentialId().clone());
  }

  private static void validateInline(
      GenerationCommand.Inline command,
      WebAuthnAttestationFixtures.WebAuthnAttestationVector vector) {
    if (vector.format() != command.format()) {
      throw new IllegalArgumentException(
          ERROR_PREFIX
              + "format mismatch (expected "
              + vector.format().label()
              + " but was "
              + command.format().label()
              + ')');
    }
    if (!vector.relyingPartyId().equals(command.relyingPartyId())) {
      throw new IllegalArgumentException(
          ERROR_PREFIX + "relying party mismatch for " + command.attestationId());
    }
    if (!vector.origin().equals(command.origin())) {
      throw new IllegalArgumentException(
          ERROR_PREFIX + "origin mismatch for " + command.attestationId());
    }

    WebAuthnAttestationFixtures.KeyMaterial keyMaterial = vector.keyMaterial();
    validateKey(
        "credentialPrivateKey",
        keyMaterial.credentialPrivateKeyBase64Url(),
        command.credentialPrivateKeyBase64Url());
    validateKey(
        "attestationPrivateKey",
        keyMaterial.attestationPrivateKeyBase64Url(),
        command.attestationPrivateKeyBase64Url());

    if (command.signingMode() != SigningMode.UNSIGNED) {
      validateKey(
          "attestationCertificateSerial",
          keyMaterial.attestationCertificateSerialBase64Url(),
          command.attestationCertificateSerialBase64Url());
    }
  }

  private static void validateManual(GenerationCommand.Manual command) {
    if (command.signingMode() == SigningMode.CUSTOM_ROOT
        && command.customRootCertificatesPem().isEmpty()) {
      throw new IllegalArgumentException(
          ERROR_PREFIX + "custom-root signing requires at least one certificate");
    }
    if (command.signingMode() != SigningMode.UNSIGNED) {
      if (sanitize(command.attestationPrivateKeyBase64Url()).isEmpty()) {
        throw new IllegalArgumentException(
            ERROR_PREFIX + "attestationPrivateKey is required for signed modes");
      }
      if (sanitize(command.attestationCertificateSerialBase64Url()).isEmpty()) {
        throw new IllegalArgumentException(
            ERROR_PREFIX + "attestationCertificateSerial is required for signed modes");
      }
    }
  }

  private static void validateKey(String field, String expected, String provided) {
    if (!Objects.equals(expected, sanitize(provided))) {
      throw new IllegalArgumentException(
          ERROR_PREFIX + field + " mismatch for attestation fixture");
    }
  }

  private static String sanitize(String value) {
    if (value == null) {
      return "";
    }
    return value.trim();
  }

  private static List<String> convertCertificatesToPem(
      WebAuthnAttestationFixtures.WebAuthnAttestationVector vector) {
    List<X509Certificate> certificates = loadCertificateChain(vector);
    if (certificates.isEmpty()) {
      return List.of();
    }
    return certificates.stream().map(WebAuthnAttestationGenerator::toPem).toList();
  }

  private static List<X509Certificate> loadCertificateChain(
      WebAuthnAttestationFixtures.WebAuthnAttestationVector vector) {
    WebAuthnAttestationVerifier verifier = new WebAuthnAttestationVerifier();
    WebAuthnAttestationRequest request =
        new WebAuthnAttestationRequest(
            vector.format(),
            vector.registration().attestationObject(),
            vector.registration().clientDataJson(),
            vector.registration().challenge(),
            vector.relyingPartyId(),
            vector.origin());
    WebAuthnAttestationVerification verification = verifier.verify(request);
    return verification.certificateChain();
  }

  private static String toPem(X509Certificate certificate) {
    try {
      String encoded = MIME_ENCODER.encodeToString(certificate.getEncoded());
      return "-----BEGIN CERTIFICATE-----\n" + encoded + "\n-----END CERTIFICATE-----\n";
    } catch (CertificateEncodingException ex) {
      throw new IllegalArgumentException("Unable to encode certificate to PEM", ex);
    }
  }

  private static WebAuthnAttestationFixtures.WebAuthnAttestationVector templateFor(
      WebAuthnAttestationFormat format) {
    return WebAuthnAttestationFixtures.vectorsFor(format).stream()
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    ERROR_PREFIX + "no template vector available for format " + format.label()));
  }

  private static byte[] buildClientDataJson(String origin, byte[] challenge) {
    String encodedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge);
    String json =
        "{"
            + "\"type\":\"webauthn.create\","
            + "\"origin\":\""
            + sanitize(origin)
            + "\","
            + "\"challenge\":\""
            + encodedChallenge
            + "\"}";
    return json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }
}
