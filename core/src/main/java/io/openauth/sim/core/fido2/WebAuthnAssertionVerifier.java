package io.openauth.sim.core.fido2;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Performs WebAuthn assertion verification for FIDO2 credentials. */
public final class WebAuthnAssertionVerifier {

  private static final int RP_ID_HASH_LENGTH = 32;
  private static final int COUNTER_LENGTH = 4;
  private static final Pattern JSON_FIELD_PATTERN =
      Pattern.compile("\\\"(?<key>[^\\\"]+)\\\"\\s*:\\s*\\\"(?<value>[^\\\"]*)\\\"");

  public WebAuthnVerificationResult verify(
      WebAuthnStoredCredential storedCredential, WebAuthnAssertionRequest assertionRequest) {
    Objects.requireNonNull(storedCredential, "storedCredential");
    Objects.requireNonNull(assertionRequest, "assertionRequest");

    try {
      parseClientData(assertionRequest, storedCredential);
      parseAuthenticatorData(assertionRequest, storedCredential);

      PublicKey publicKey = createPublicKeyFromCose(storedCredential.publicKeyCose());
      byte[] clientDataHash = hashSha256(assertionRequest.clientDataJson());
      byte[] signedPayload = concatenate(assertionRequest.authenticatorData(), clientDataHash);

      Signature signature = Signature.getInstance("SHA256withECDSA");
      signature.initVerify(publicKey);
      signature.update(signedPayload);
      if (!signature.verify(assertionRequest.signature())) {
        return WebAuthnVerificationResult.failure(
            WebAuthnVerificationError.SIGNATURE_INVALID, "Authenticator signature mismatch");
      }

      return WebAuthnVerificationResult.successResult();
    } catch (VerificationFailure vf) {
      return WebAuthnVerificationResult.failure(vf.error, vf.getMessage());
    } catch (GeneralSecurityException gse) {
      return WebAuthnVerificationResult.failure(
          WebAuthnVerificationError.SIGNATURE_INVALID, gse.getMessage());
    }
  }

  private static void parseClientData(
      WebAuthnAssertionRequest request, WebAuthnStoredCredential storedCredential) {
    String json = new String(request.clientDataJson(), StandardCharsets.UTF_8);
    Map<String, String> values = extractJsonValues(json);

    String type = values.get("type");
    if (!request.expectedType().equals(type)) {
      throw failure(
          WebAuthnVerificationError.CLIENT_DATA_TYPE_MISMATCH, "Unexpected client data type");
    }

    String challengeB64 = values.get("challenge");
    byte[] challenge = decodeBase64Url(challengeB64);
    if (!Arrays.equals(request.expectedChallenge(), challenge)) {
      throw failure(
          WebAuthnVerificationError.CLIENT_DATA_CHALLENGE_MISMATCH,
          "Client data challenge does not match expected value");
    }

    String origin = values.get("origin");
    if (!request.origin().equals(origin)) {
      throw failure(WebAuthnVerificationError.ORIGIN_MISMATCH, "Client data origin mismatch");
    }

    if (!storedCredential.relyingPartyId().equals(request.relyingPartyId())) {
      throw failure(
          WebAuthnVerificationError.RP_ID_HASH_MISMATCH,
          "Stored credential RP ID does not match request RP ID");
    }
  }

  private static void parseAuthenticatorData(
      WebAuthnAssertionRequest request, WebAuthnStoredCredential storedCredential) {
    byte[] authenticatorData = request.authenticatorData();

    if (authenticatorData.length < RP_ID_HASH_LENGTH + 1 + COUNTER_LENGTH) {
      throw failure(
          WebAuthnVerificationError.RP_ID_HASH_MISMATCH,
          "Authenticator data length is insufficient");
    }

    byte[] rpIdHash = Arrays.copyOfRange(authenticatorData, 0, RP_ID_HASH_LENGTH);
    byte[] expectedRpHash = hashSha256(storedCredential.relyingPartyId());
    if (!Arrays.equals(expectedRpHash, rpIdHash)) {
      throw failure(
          WebAuthnVerificationError.RP_ID_HASH_MISMATCH, "Authenticator RP hash mismatch");
    }

    int flags = authenticatorData[RP_ID_HASH_LENGTH] & 0xFF;
    boolean userVerified = (flags & 0x04) != 0;
    if (storedCredential.userVerificationRequired() && !userVerified) {
      throw failure(
          WebAuthnVerificationError.USER_VERIFICATION_REQUIRED, "User verification was required");
    }

    int counterOffset = RP_ID_HASH_LENGTH + 1;
    long counter =
        ByteBuffer.wrap(authenticatorData, counterOffset, COUNTER_LENGTH).getInt() & 0xFFFFFFFFL;
    if (counter < storedCredential.signatureCounter()) {
      throw failure(
          WebAuthnVerificationError.COUNTER_REGRESSION, "Authenticator counter regressed");
    }
  }

  private static Map<String, String> extractJsonValues(String json) {
    Map<String, String> values = new HashMap<>();
    Matcher matcher = JSON_FIELD_PATTERN.matcher(json);
    while (matcher.find()) {
      values.put(matcher.group("key"), matcher.group("value"));
    }
    return values;
  }

  private static byte[] hashSha256(String value) {
    return hashSha256(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] hashSha256(byte[] data) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return digest.digest(data);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static byte[] decodeBase64Url(String input) {
    if (input == null) {
      return new byte[0];
    }
    String padded = input;
    int padding = (4 - (input.length() % 4)) % 4;
    if (padding > 0) {
      padded = input + "=".repeat(padding);
    }
    return Base64.getUrlDecoder().decode(padded);
  }

  private static PublicKey createPublicKeyFromCose(byte[] coseKey) throws GeneralSecurityException {
    Map<Integer, Object> map = new CborReader(coseKey).readMap();

    int kty = requireInt(map, 1);
    int alg = requireInt(map, 3);
    int curve = requireInt(map, -1);
    byte[] x = requireBytes(map, -2);
    byte[] y = requireBytes(map, -3);

    if (kty != 2 || alg != -7 || curve != 1) {
      throw new GeneralSecurityException("Unsupported COSE key parameters");
    }

    AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
    parameters.init(new ECGenParameterSpec("secp256r1"));
    ECParameterSpec ecParameters = parameters.getParameterSpec(ECParameterSpec.class);

    ECPoint ecPoint = new ECPoint(new BigInteger(1, x), new BigInteger(1, y));
    KeyFactory factory = KeyFactory.getInstance("EC");
    return factory.generatePublic(new ECPublicKeySpec(ecPoint, ecParameters));
  }

  private static int requireInt(Map<Integer, Object> map, int key) throws GeneralSecurityException {
    Object value = map.get(key);
    if (value instanceof Number number) {
      return number.intValue();
    }
    throw new GeneralSecurityException("Missing integer field for key " + key);
  }

  private static byte[] requireBytes(Map<Integer, Object> map, int key)
      throws GeneralSecurityException {
    Object value = map.get(key);
    if (value instanceof byte[] bytes) {
      return bytes;
    }
    throw new GeneralSecurityException("Missing byte[] field for key " + key);
  }

  private static byte[] concatenate(byte[] first, byte[] second) {
    byte[] combined = Arrays.copyOf(first, first.length + second.length);
    System.arraycopy(second, 0, combined, first.length, second.length);
    return combined;
  }

  private static RuntimeException failure(WebAuthnVerificationError error, String message) {
    return new VerificationFailure(error, message);
  }

  private static final class VerificationFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final WebAuthnVerificationError error;

    VerificationFailure(WebAuthnVerificationError error, String message) {
      super(message);
      this.error = error;
    }
  }

  private static final class CborReader {
    private final byte[] data;
    private int index;

    CborReader(byte[] data) {
      this.data = Objects.requireNonNull(data, "data");
    }

    Map<Integer, Object> readMap() throws GeneralSecurityException {
      int initial = readUnsignedByte();
      int majorType = initial >>> 5;
      int additional = initial & 0x1F;
      if (majorType != 5) {
        throw new GeneralSecurityException("Expected CBOR map");
      }
      int entries = (int) readLength(additional);
      Map<Integer, Object> map = new HashMap<>(entries);
      for (int i = 0; i < entries; i++) {
        Object keyObject = readData();
        Object value = readData();
        if (!(keyObject instanceof Number number)) {
          throw new GeneralSecurityException("Unsupported CBOR key type");
        }
        map.put(number.intValue(), value);
      }
      return map;
    }

    private Object readData() throws GeneralSecurityException {
      int initial = readUnsignedByte();
      int majorType = initial >>> 5;
      int additional = initial & 0x1F;

      return switch (majorType) {
        case 0 -> readLength(additional); // unsigned integer
        case 1 -> -1 - readLength(additional); // negative integer
        case 2 -> {
          int length = (int) readLength(additional);
          if (index + length > data.length) {
            throw new GeneralSecurityException("Byte string overruns buffer");
          }
          byte[] bytes = Arrays.copyOfRange(data, index, index + length);
          index += length;
          yield bytes;
        }
        default -> throw new GeneralSecurityException("Unsupported CBOR major type: " + majorType);
      };
    }

    private long readLength(int additional) throws GeneralSecurityException {
      if (additional <= 23) {
        return additional;
      }
      int lengthBytes =
          switch (additional) {
            case 24 -> 1;
            case 25 -> 2;
            case 26 -> 4;
            case 27 -> 8;
            default -> throw new GeneralSecurityException("Unsupported CBOR length encoding");
          };
      long value = 0;
      for (int i = 0; i < lengthBytes; i++) {
        value = (value << 8) | readUnsignedByte();
      }
      return value;
    }

    private int readUnsignedByte() {
      return data[index++] & 0xFF;
    }
  }
}
