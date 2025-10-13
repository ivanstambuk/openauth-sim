package io.openauth.sim.application.fido2;

import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import java.util.Objects;

/** Decoder that normalises WebAuthn public-key material into COSE byte arrays. */
public final class WebAuthnPublicKeyDecoder {

  private WebAuthnPublicKeyDecoder() {
    throw new AssertionError("Utility class");
  }

  public static byte[] decode(String value, WebAuthnSignatureAlgorithm algorithm) {
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(algorithm, "algorithm");

    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("public key is required");
    }

    if (looksLikeJson(trimmed)) {
      return WebAuthnPublicKeyFormats.decodeJwk(trimmed, algorithm);
    }
    if (looksLikePem(trimmed)) {
      return WebAuthnPublicKeyFormats.decodePem(trimmed, algorithm);
    }

    byte[] decoded = WebAuthnPublicKeyFormats.decodeBase64Url(trimmed);
    if (decoded.length == 0) {
      throw new IllegalArgumentException("public key must not be empty");
    }
    if (!isCosePublicKey(decoded)) {
      throw new IllegalArgumentException("public key must be a COSE map");
    }
    return decoded;
  }

  private static boolean looksLikeJson(String value) {
    return value.startsWith("{") && value.endsWith("}");
  }

  private static boolean looksLikePem(String value) {
    return value.startsWith("-----BEGIN");
  }

  private static boolean isCosePublicKey(byte[] value) {
    byte header = value[0];
    return header == (byte) 0xA3 || header == (byte) 0xA4 || header == (byte) 0xA5;
  }
}
