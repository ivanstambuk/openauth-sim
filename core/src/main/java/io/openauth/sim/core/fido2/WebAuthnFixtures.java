package io.openauth.sim.core.fido2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;
import java.util.Properties;

public final class WebAuthnFixtures {

  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

  private WebAuthnFixtures() {
    throw new AssertionError("Utility class");
  }

  public static WebAuthnFixture loadPackedEs256() {
    Properties properties = load();
    byte[] credentialId = decode(properties, "registration.credentialId");
    byte[] publicKey = decode(properties, "registration.publicKeyCose");
    byte[] challenge = decode(properties, "assertion.challenge");
    byte[] clientData = decode(properties, "assertion.clientDataJSON");
    byte[] authenticatorData = decode(properties, "assertion.authenticatorData");
    byte[] signature = decode(properties, "assertion.signature");

    WebAuthnStoredCredential storedCredential =
        new WebAuthnStoredCredential("example.org", credentialId, publicKey, 0L, false);

    WebAuthnAssertionRequest assertionRequest =
        new WebAuthnAssertionRequest(
            "example.org",
            "https://example.org",
            challenge,
            clientData,
            authenticatorData,
            signature,
            "webauthn.get");

    return new WebAuthnFixture(storedCredential, assertionRequest);
  }

  private static Properties load() {
    Path fixturePath = resolveFixturePath();
    try (InputStream inputStream = Files.newInputStream(fixturePath)) {
      Properties properties = new Properties();
      properties.load(inputStream);
      return properties;
    } catch (IOException ioe) {
      throw new IllegalStateException("Unable to load WebAuthn fixture", ioe);
    }
  }

  private static Path resolveFixturePath() {
    Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    Path direct = workingDirectory.resolve("docs/webauthn_w3c_vectors/packed-es256.properties");
    if (Files.exists(direct)) {
      return direct;
    }
    Path parent = workingDirectory.getParent();
    if (parent != null) {
      Path parentCandidate = parent.resolve("docs/webauthn_w3c_vectors/packed-es256.properties");
      if (Files.exists(parentCandidate)) {
        return parentCandidate;
      }
    }
    return direct; // fall back to original path to surface clear exception
  }

  private static byte[] decode(Properties properties, String key) {
    String value = Objects.requireNonNull(properties.getProperty(key), key + " missing");
    return URL_DECODER.decode(value);
  }

  public static record WebAuthnFixture(
      WebAuthnStoredCredential storedCredential, WebAuthnAssertionRequest request) {

    public WebAuthnAssertionRequest requestWithRpId(String relyingPartyId) {
      return new WebAuthnAssertionRequest(
          relyingPartyId,
          request.origin(),
          request.expectedChallenge(),
          request.clientDataJson(),
          request.authenticatorData(),
          request.signature(),
          request.expectedType());
    }

    public WebAuthnAssertionRequest requestWithSignature(byte[] newSignature) {
      return new WebAuthnAssertionRequest(
          request.relyingPartyId(),
          request.origin(),
          request.expectedChallenge(),
          request.clientDataJson(),
          request.authenticatorData(),
          newSignature,
          request.expectedType());
    }
  }
}
