package io.openauth.sim.core.fido2;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Factory that converts COSE-encoded WebAuthn public keys into {@link PublicKey} instances.
 *
 * <p>Centralising this logic keeps the attestation verifier and fixture loaders in sync without
 * duplicating CBOR parsing.
 */
public final class WebAuthnPublicKeyFactory {

  private WebAuthnPublicKeyFactory() {
    throw new AssertionError("Utility class");
  }

  /**
   * Decodes the supplied COSE map into a {@link PublicKey} matching the provided algorithm.
   *
   * @param coseKey CBOR-encoded COSE key map
   * @param algorithm Expected WebAuthn signature algorithm
   * @return {@link PublicKey} derived from the COSE map
   * @throws GeneralSecurityException if the COSE map cannot be decoded into the expected key type
   */
  public static PublicKey fromCose(byte[] coseKey, WebAuthnSignatureAlgorithm algorithm)
      throws GeneralSecurityException {
    Map<Integer, Object> map = decodeCoseMap(coseKey);
    int keyType = requireInt(map, 1);
    int coseAlgorithm = requireInt(map, 3);
    if (coseAlgorithm != algorithm.coseIdentifier()) {
      throw new GeneralSecurityException("COSE algorithm mismatch");
    }

    return switch (algorithm) {
      case ES256, ES384, ES512 -> createEcPublicKey(map, keyType, algorithm);
      case RS256, PS256 -> createRsaPublicKey(map, keyType);
      case EDDSA -> createEd25519PublicKey(map, keyType);
    };
  }

  private static Map<Integer, Object> decodeCoseMap(byte[] coseKey)
      throws GeneralSecurityException {
    Object decoded = CborDecoder.decode(coseKey);
    if (!(decoded instanceof Map<?, ?> raw)) {
      throw new GeneralSecurityException("COSE key is not a CBOR map");
    }
    Map<Integer, Object> result = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : raw.entrySet()) {
      if (!(entry.getKey() instanceof Number number)) {
        throw new GeneralSecurityException("COSE key contains non-integer identifiers");
      }
      result.put(number.intValue(), entry.getValue());
    }
    return result;
  }

  private static PublicKey createEcPublicKey(
      Map<Integer, Object> map, int keyType, WebAuthnSignatureAlgorithm algorithm)
      throws GeneralSecurityException {
    if (keyType != 2) {
      throw new GeneralSecurityException("Expected EC2 key type for algorithm " + algorithm);
    }
    int curve = requireInt(map, -1);
    int expectedCurve =
        switch (algorithm) {
          case ES256 -> 1;
          case ES384 -> 2;
          case ES512 -> 3;
          default -> throw new GeneralSecurityException("Unsupported EC algorithm " + algorithm);
        };
    if (curve != expectedCurve) {
      throw new GeneralSecurityException("Unexpected COSE curve for algorithm " + algorithm);
    }
    byte[] x = requireBytes(map, -2);
    byte[] y = requireBytes(map, -3);
    ECParameterSpec params = createEcSpec(algorithm);
    ECPoint point = new ECPoint(new BigInteger(1, x), new BigInteger(1, y));
    ECPublicKeySpec publicSpec = new ECPublicKeySpec(point, params);
    return KeyFactory.getInstance("EC").generatePublic(publicSpec);
  }

  private static PublicKey createRsaPublicKey(Map<Integer, Object> map, int keyType)
      throws GeneralSecurityException {
    if (keyType != 3) {
      throw new GeneralSecurityException("Expected RSA key type");
    }
    byte[] modulus = requireBytes(map, -1);
    byte[] exponent = requireBytes(map, -2);
    RSAPublicKeySpec spec =
        new RSAPublicKeySpec(new BigInteger(1, modulus), new BigInteger(1, exponent));
    return KeyFactory.getInstance("RSA").generatePublic(spec);
  }

  private static PublicKey createEd25519PublicKey(Map<Integer, Object> map, int keyType)
      throws GeneralSecurityException {
    if (keyType != 1) {
      throw new GeneralSecurityException("Expected OKP key type");
    }
    int curve = requireInt(map, -1);
    if (curve != 6) {
      throw new GeneralSecurityException("Unsupported OKP curve for Ed25519 key");
    }
    byte[] publicKey = requireBytes(map, -2);
    byte[] prefix =
        new byte[] {0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};
    byte[] spki = Arrays.copyOf(prefix, prefix.length + publicKey.length);
    System.arraycopy(publicKey, 0, spki, prefix.length, publicKey.length);
    return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(spki));
  }

  private static ECParameterSpec createEcSpec(WebAuthnSignatureAlgorithm algorithm)
      throws GeneralSecurityException {
    AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
    parameters.init(new ECGenParameterSpec(curveForAlgorithm(algorithm)));
    return parameters.getParameterSpec(ECParameterSpec.class);
  }

  private static String curveForAlgorithm(WebAuthnSignatureAlgorithm algorithm) {
    return switch (algorithm) {
      case ES256 -> "secp256r1";
      case ES384 -> "secp384r1";
      case ES512 -> "secp521r1";
      default -> throw new IllegalArgumentException("Unsupported EC algorithm " + algorithm);
    };
  }

  private static int requireInt(Map<Integer, Object> map, int key) throws GeneralSecurityException {
    Object value = map.get(key);
    if (value instanceof Number number) {
      return number.intValue();
    }
    throw new GeneralSecurityException("Missing integer field " + key + " in COSE map");
  }

  private static byte[] requireBytes(Map<Integer, Object> map, int key)
      throws GeneralSecurityException {
    Object value = map.get(key);
    if (value instanceof byte[] bytes) {
      return bytes;
    }
    if (value instanceof BigInteger integer) {
      return integer.toByteArray();
    }
    throw new GeneralSecurityException("Missing byte field " + key + " in COSE map");
  }
}
