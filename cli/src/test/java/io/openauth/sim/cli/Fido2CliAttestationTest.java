package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationRequest;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerification;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerifier;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

@Tag("cli")
final class Fido2CliAttestationTest {

  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Encoder MIME_ENCODER = Base64.getMimeEncoder(64, new byte[] {'\n'});

  @TempDir Path tempDir;

  @Test
  void attestGeneratesSelfSignedAttestation() throws Exception {
    WebAuthnAttestationVector vector = attestationVector();
    CommandHarness harness = CommandHarness.create(tempDir.resolve("fido2-attest.db"));
    String credentialPem =
        toPemPrivateKey(vector, vector.keyMaterial().credentialPrivateKeyBase64Url());
    String attestationPem =
        toPemPrivateKey(vector, vector.keyMaterial().attestationPrivateKeyBase64Url());

    int exitCode =
        harness.execute(
            "attest",
            "--format",
            vector.format().label(),
            "--attestation-id",
            vector.vectorId(),
            "--relying-party-id",
            vector.relyingPartyId(),
            "--origin",
            vector.origin(),
            "--challenge",
            encode(vector.registration().challenge()),
            "--credential-private-key",
            credentialPem,
            "--attestation-private-key",
            attestationPem,
            "--attestation-serial",
            vector.keyMaterial().attestationCertificateSerialBase64Url(),
            "--signing-mode",
            "self-signed");

    assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
    String stdout = harness.stdout();
    String expectedAttestation =
        "response.attestationObject=" + encode(vector.registration().attestationObject());
    String expectedClientData =
        "response.clientDataJSON=" + encode(vector.registration().clientDataJson());
    assertTrue(stdout.contains("Generated attestation"), stdout);
    assertTrue(stdout.contains(expectedAttestation), stdout);
    assertTrue(stdout.contains(expectedClientData), stdout);
    assertTrue(stdout.contains("generationMode=self_signed"), stdout);
    assertTrue(stdout.contains("format=" + vector.format().label()), stdout);
    assertTrue(stdout.contains("signatureIncluded=true"), stdout);
  }

  @Test
  void attestGeneratesUnsignedAttestationWithoutSignature() throws Exception {
    WebAuthnAttestationVector vector = attestationVector();
    CommandHarness harness = CommandHarness.create(tempDir.resolve("fido2-attest.db"));

    int exitCode =
        harness.execute(
            "attest",
            "--format",
            vector.format().label(),
            "--attestation-id",
            vector.vectorId(),
            "--relying-party-id",
            vector.relyingPartyId(),
            "--origin",
            vector.origin(),
            "--challenge",
            encode(vector.registration().challenge()),
            "--credential-private-key",
            vector.keyMaterial().credentialPrivateKeyJwk(),
            "--attestation-private-key",
            vector.keyMaterial().attestationPrivateKeyJwk(),
            "--attestation-serial",
            vector.keyMaterial().attestationCertificateSerialBase64Url(),
            "--signing-mode",
            "unsigned");

    assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
    String stdout = harness.stdout();
    assertTrue(stdout.contains("Generated attestation"), stdout);
    assertTrue(stdout.contains("generationMode=unsigned"), stdout);
    assertTrue(stdout.contains("signatureIncluded=false"), stdout);
  }

  @Test
  void attestGeneratesAttestationWithCustomRootCertificate() throws Exception {
    WebAuthnAttestationVector vector = attestationVector();
    WebAuthnAttestationVerification verification = verify(vector);
    List<X509Certificate> chain = verification.certificateChain();
    if (chain.isEmpty()) {
      throw new IllegalStateException("Attestation fixture missing certificate chain");
    }

    Path rootFile = tempDir.resolve("custom-root.pem");
    Files.writeString(rootFile, toPem(chain.get(chain.size() - 1)), StandardCharsets.UTF_8);

    CommandHarness harness = CommandHarness.create(tempDir.resolve("fido2-attest.db"));

    int exitCode =
        harness.execute(
            "attest",
            "--format",
            vector.format().label(),
            "--attestation-id",
            vector.vectorId(),
            "--relying-party-id",
            vector.relyingPartyId(),
            "--origin",
            vector.origin(),
            "--challenge",
            encode(vector.registration().challenge()),
            "--credential-private-key",
            vector.keyMaterial().credentialPrivateKeyJwk(),
            "--attestation-private-key",
            vector.keyMaterial().attestationPrivateKeyJwk(),
            "--attestation-serial",
            vector.keyMaterial().attestationCertificateSerialBase64Url(),
            "--signing-mode",
            "custom-root",
            "--custom-root-file",
            rootFile.toString());

    assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
    String stdout = harness.stdout();
    assertTrue(stdout.contains("generationMode=custom_root"), stdout);
    assertTrue(stdout.contains("customRootCount=1"), stdout);
    assertTrue(stdout.contains("signatureIncluded=true"), stdout);
  }

  @Test
  void attestRejectsLegacyBase64PrivateKeys() throws Exception {
    WebAuthnAttestationVector vector = attestationVector();
    CommandHarness harness = CommandHarness.create(tempDir.resolve("fido2-attest.db"));

    int exitCode =
        harness.execute(
            "attest",
            "--format",
            vector.format().label(),
            "--attestation-id",
            vector.vectorId(),
            "--relying-party-id",
            vector.relyingPartyId(),
            "--origin",
            vector.origin(),
            "--challenge",
            encode(vector.registration().challenge()),
            "--credential-private-key",
            vector.keyMaterial().credentialPrivateKeyBase64Url(),
            "--attestation-private-key",
            vector.keyMaterial().attestationPrivateKeyBase64Url(),
            "--attestation-serial",
            vector.keyMaterial().attestationCertificateSerialBase64Url(),
            "--signing-mode",
            "self-signed");

    assertEquals(CommandLine.ExitCode.USAGE, exitCode, harness.stdout());
    assertTrue(harness.stderr().contains("must be provided as JWK or PEM"), harness.stderr());
  }

  @Test
  void attestReplayWithTrustAnchorsEmitsTelemetry() throws Exception {
    WebAuthnAttestationVector vector = attestationVector();
    WebAuthnAttestationVerification verification = verify(vector);
    List<X509Certificate> chain = verification.certificateChain();
    if (chain.isEmpty()) {
      throw new IllegalStateException("Attestation fixture missing certificate chain");
    }

    Path anchorFile = tempDir.resolve("packed-anchor.pem");
    Files.writeString(anchorFile, toPem(chain.get(chain.size() - 1)), StandardCharsets.UTF_8);

    CommandHarness harness = CommandHarness.create(tempDir.resolve("fido2-attest.db"));

    int exitCode =
        harness.execute(
            "attest-replay",
            "--format",
            vector.format().label(),
            "--attestation-id",
            vector.vectorId(),
            "--relying-party-id",
            vector.relyingPartyId(),
            "--origin",
            vector.origin(),
            "--attestation-object",
            encode(vector.registration().attestationObject()),
            "--client-data-json",
            encode(vector.registration().clientDataJson()),
            "--expected-challenge",
            encode(vector.registration().challenge()),
            "--trust-anchor-file",
            anchorFile.toString());

    assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
    String stdout = harness.stdout();
    assertTrue(stdout.contains("event=cli.fido2.attestReplay status=success"), stdout);
    assertTrue(stdout.contains("anchorProvided=true"), stdout);
    assertTrue(stdout.contains("anchorTrusted=true"), stdout);
    assertTrue(stdout.contains("selfAttestedFallback=false"), stdout);
    assertTrue(stdout.contains("reasonCode=match"), stdout);
    assertTrue(stdout.contains("certificateFingerprint="), stdout);
    assertTrue(stdout.contains("anchorMode=fresh"), stdout);
    assertTrue(stdout.contains("anchorSourceType=combined"), stdout);
    assertTrue(stdout.contains("anchorSource=metadata_manual"), stdout);
  }

  @Test
  void attestWithoutSigningModeFailsValidation() throws Exception {
    WebAuthnAttestationVector vector = attestationVector();
    CommandHarness harness = CommandHarness.create(tempDir.resolve("fido2-attest.db"));

    int exitCode =
        harness.execute(
            "attest",
            "--format",
            vector.format().label(),
            "--attestation-id",
            vector.vectorId(),
            "--relying-party-id",
            vector.relyingPartyId(),
            "--origin",
            vector.origin(),
            "--challenge",
            encode(vector.registration().challenge()),
            "--credential-private-key",
            vector.keyMaterial().credentialPrivateKeyBase64Url());

    assertEquals(CommandLine.ExitCode.USAGE, exitCode);
    String stderr = harness.stderr();
    assertTrue(stderr.contains("event=cli.fido2.attest status=invalid"), stderr);
    assertTrue(stderr.contains("reasonCode=missing_signing_mode"), stderr);
  }

  @Test
  void vectorsCommandIncludesAttestationFixtures() throws Exception {
    CommandHarness harness = CommandHarness.create(tempDir.resolve("fido2-vectors.db"));

    int exitCode = harness.execute("vectors");

    assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
    String stdout = harness.stdout();
    assertTrue(
        stdout.contains("w3c-packed-es256"),
        () -> "Expected attestation fixture id in vectors output but got: " + stdout);
  }

  private static WebAuthnAttestationVector attestationVector() {
    return WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Missing packed attestation fixture"));
  }

  private static WebAuthnAttestationVerification verify(WebAuthnAttestationVector vector) {
    WebAuthnAttestationVerifier verifier = new WebAuthnAttestationVerifier();
    return verifier.verify(
        new WebAuthnAttestationRequest(
            vector.format(),
            vector.registration().attestationObject(),
            vector.registration().clientDataJson(),
            vector.registration().challenge(),
            vector.relyingPartyId(),
            vector.origin()));
  }

  private static String encode(byte[] value) {
    return URL_ENCODER.encodeToString(value);
  }

  private static String toPem(X509Certificate certificate) throws CertificateEncodingException {
    String encoded = MIME_ENCODER.encodeToString(certificate.getEncoded());
    return "-----BEGIN CERTIFICATE-----\n" + encoded + "\n-----END CERTIFICATE-----\n";
  }

  private static String toPemPrivateKey(
      WebAuthnAttestationVector vector, String base64UrlPrivateKey)
      throws GeneralSecurityException {
    if (base64UrlPrivateKey == null || base64UrlPrivateKey.isBlank()) {
      return "";
    }
    WebAuthnSignatureAlgorithm algorithm = vector.algorithm();
    byte[] scalar = Base64.getUrlDecoder().decode(base64UrlPrivateKey);
    AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
    parameters.init(new ECGenParameterSpec(curveFor(algorithm)));
    ECParameterSpec spec = parameters.getParameterSpec(ECParameterSpec.class);
    KeyFactory factory = KeyFactory.getInstance("EC");
    ECPrivateKeySpec privateKeySpec =
        new ECPrivateKeySpec(new java.math.BigInteger(1, scalar), spec);
    PrivateKey privateKey = factory.generatePrivate(privateKeySpec);
    String body = MIME_ENCODER.encodeToString(privateKey.getEncoded());
    return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
  }

  private static String curveFor(WebAuthnSignatureAlgorithm algorithm) {
    return switch (algorithm) {
      case ES256 -> "secp256r1";
      case ES384 -> "secp384r1";
      case ES512 -> "secp521r1";
      default ->
          throw new IllegalArgumentException(
              "PEM conversion supported only for EC algorithms; found " + algorithm);
    };
  }

  private static final class CommandHarness {

    private final Fido2Cli cli;
    private final CommandLine commandLine;
    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    private CommandHarness(Path database) {
      this.cli = new Fido2Cli();
      cli.overrideDatabase(database);
      this.commandLine = new CommandLine(cli);
      commandLine.setOut(new PrintWriter(stdout, true, StandardCharsets.UTF_8));
      commandLine.setErr(new PrintWriter(stderr, true, StandardCharsets.UTF_8));
    }

    static CommandHarness create(Path database) {
      return new CommandHarness(database);
    }

    int execute(String... args) {
      stdout.reset();
      stderr.reset();
      return commandLine.execute(args);
    }

    String stdout() {
      return stdout.toString(StandardCharsets.UTF_8);
    }

    String stderr() {
      return stderr.toString(StandardCharsets.UTF_8);
    }
  }
}
