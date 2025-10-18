package io.openauth.sim.core.fido2;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Performs WebAuthn attestation verification across supported attestation formats. */
public final class WebAuthnAttestationVerifier {

  public WebAuthnAttestationVerification verify(WebAuthnAttestationRequest request) {
    Objects.requireNonNull(request, "request");
    try {
      ClientData clientData =
          parseClientData(request.clientDataJson(), request.expectedChallenge(), request.origin());
      AttestationObject attestationObject = parseAttestationObject(request.attestationObject());
      if (attestationObject.format != request.format()) {
        throw failure(
            WebAuthnVerificationError.ATTESTATION_FORMAT_MISMATCH,
            "Attestation format mismatch (expected "
                + request.format().label()
                + " but found "
                + attestationObject.format.label()
                + ")");
      }

      AttestationAuthData authData = parseAuthData(attestationObject.authData());
      byte[] expectedRpHash = hashSha256(request.relyingPartyId());
      if (!Arrays.equals(expectedRpHash, authData.rpIdHash())) {
        throw failure(
            WebAuthnVerificationError.RP_ID_HASH_MISMATCH, "RP ID hash mismatch in attestation");
      }

      byte[] clientDataHash = hashSha256(request.clientDataJson());
      List<X509Certificate> certificates =
          verifyAttestationStatement(
              request.format(), attestationObject.attStmt(), authData, clientDataHash);

      WebAuthnStoredCredential credential =
          new WebAuthnStoredCredential(
              request.relyingPartyId(),
              authData.credentialId(),
              authData.credentialPublicKey(),
              authData.signCount(),
              authData.userVerified(),
              authData.algorithm());

      return WebAuthnAttestationVerification.success(credential, certificates, authData.aaguid());
    } catch (VerificationFailure vf) {
      return WebAuthnAttestationVerification.failure(vf.error, vf.getMessage());
    } catch (GeneralSecurityException ex) {
      return WebAuthnAttestationVerification.failure(
          WebAuthnVerificationError.SIGNATURE_INVALID, ex.getMessage());
    }
  }

  private static ClientData parseClientData(
      byte[] clientDataJson, byte[] expectedChallenge, String expectedOrigin) {
    String json = new String(clientDataJson, StandardCharsets.UTF_8);
    Map<String, String> values = extractJsonValues(json);

    String type = values.get("type");
    if (!"webauthn.create".equals(type)) {
      throw failure(
          WebAuthnVerificationError.CLIENT_DATA_TYPE_MISMATCH,
          "Client data type must be 'webauthn.create'");
    }

    String challengeB64 = values.get("challenge");
    byte[] challenge = decodeBase64Url(challengeB64);
    if (!Arrays.equals(expectedChallenge, challenge)) {
      throw failure(
          WebAuthnVerificationError.CLIENT_DATA_CHALLENGE_MISMATCH,
          "Client data challenge mismatch");
    }

    String origin = values.get("origin");
    if (!Objects.equals(expectedOrigin, origin)) {
      throw failure(WebAuthnVerificationError.ORIGIN_MISMATCH, "Client data origin mismatch");
    }

    return new ClientData(type, challenge);
  }

  private static AttestationObject parseAttestationObject(byte[] attestationObject)
      throws GeneralSecurityException {
    Object decoded = CborDecoder.decode(attestationObject);
    if (!(decoded instanceof Map<?, ?> raw)) {
      throw failure(
          WebAuthnVerificationError.ATTESTATION_OBJECT_INVALID,
          "Attestation object is not a CBOR map");
    }

    Map<String, Object> map = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : raw.entrySet()) {
      map.put(String.valueOf(entry.getKey()), entry.getValue());
    }

    String fmt = requireString(map, "fmt");
    Object authDataNode = map.get("authData");
    if (!(authDataNode instanceof byte[] authData)) {
      throw failure(
          WebAuthnVerificationError.ATTESTATION_OBJECT_INVALID, "Missing authData in attestation");
    }

    Object attStmtNode = map.get("attStmt");
    if (!(attStmtNode instanceof Map<?, ?> rawAttStmt)) {
      throw failure(
          WebAuthnVerificationError.ATTESTATION_OBJECT_INVALID, "Missing attStmt in attestation");
    }
    Map<String, Object> attStmt = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : rawAttStmt.entrySet()) {
      attStmt.put(String.valueOf(entry.getKey()), entry.getValue());
    }

    WebAuthnAttestationFormat format = WebAuthnAttestationFormat.fromLabel(fmt);
    return new AttestationObject(format, authData, attStmt);
  }

  private static AttestationAuthData parseAuthData(byte[] authData) {
    if (authData.length < 37) {
      throw failure(
          WebAuthnVerificationError.ATTESTATION_OBJECT_INVALID,
          "Authenticator data length is insufficient");
    }
    ByteBuffer buffer = ByteBuffer.wrap(authData).order(ByteOrder.BIG_ENDIAN);

    byte[] rpIdHash = new byte[32];
    buffer.get(rpIdHash);

    int flags = buffer.get() & 0xFF;
    long signCount = buffer.getInt() & 0xFFFFFFFFL;

    boolean attested = (flags & 0x40) != 0;
    if (!attested) {
      throw failure(
          WebAuthnVerificationError.ATTESTATION_OBJECT_INVALID,
          "Attested credential data not present");
    }

    byte[] aaguid = new byte[16];
    buffer.get(aaguid);
    int credentialIdLength = Short.toUnsignedInt(buffer.getShort());
    if (buffer.remaining() < credentialIdLength) {
      throw failure(
          WebAuthnVerificationError.ATTESTATION_OBJECT_INVALID,
          "Credential ID truncated in authenticator data");
    }
    byte[] credentialId = new byte[credentialIdLength];
    buffer.get(credentialId);

    byte[] credentialPublicKey = new byte[buffer.remaining()];
    buffer.get(credentialPublicKey);

    WebAuthnSignatureAlgorithm algorithm = deriveAlgorithmFromCose(credentialPublicKey);
    boolean userVerified = (flags & 0x04) != 0;

    return new AttestationAuthData(
        authData,
        rpIdHash,
        flags,
        signCount,
        aaguid,
        credentialId,
        credentialPublicKey,
        algorithm,
        userVerified);
  }

  private static WebAuthnSignatureAlgorithm deriveAlgorithmFromCose(byte[] credentialPublicKey) {
    try {
      Map<Integer, Object> cose = decodeCoseMap(credentialPublicKey);
      int alg = requireInt(cose, 3);
      return WebAuthnSignatureAlgorithm.fromCoseIdentifier(alg);
    } catch (GeneralSecurityException ex) {
      throw failure(
          WebAuthnVerificationError.ATTESTATION_OBJECT_INVALID,
          "Unable to derive algorithm from credential public key");
    }
  }

  private static List<X509Certificate> verifyAttestationStatement(
      WebAuthnAttestationFormat format,
      Map<String, Object> attStmt,
      AttestationAuthData authData,
      byte[] clientDataHash)
      throws GeneralSecurityException, CertificateException {
    return switch (format) {
      case PACKED -> verifyPacked(attStmt, authData, clientDataHash);
      case ANDROID_KEY -> verifyPacked(attStmt, authData, clientDataHash);
      case FIDO_U2F -> verifyFidoU2f(attStmt, authData, clientDataHash);
      case TPM -> verifyTpm(attStmt, authData, clientDataHash);
    };
  }

  private static List<X509Certificate> verifyPacked(
      Map<String, Object> attStmt, AttestationAuthData authData, byte[] clientDataHash)
      throws GeneralSecurityException, CertificateException {
    byte[] signature = requireAttStmtBytes(attStmt, "sig");
    int algIdentifier = requireAttStmtInt(attStmt, "alg");
    WebAuthnSignatureAlgorithm algorithm =
        WebAuthnSignatureAlgorithm.fromCoseIdentifier(algIdentifier);

    List<X509Certificate> certificates = parseCertificates(attStmt);
    PublicKey verificationKey;
    if (!certificates.isEmpty()) {
      verificationKey = certificates.get(0).getPublicKey();
    } else {
      verificationKey =
          WebAuthnPublicKeyFactory.fromCose(authData.credentialPublicKey(), authData.algorithm());
    }

    byte[] signedPayload = concatenate(authData.rawAuthData(), clientDataHash);
    verifySignature(algorithm, verificationKey, signedPayload, signature);
    return certificates;
  }

  private static List<X509Certificate> verifyFidoU2f(
      Map<String, Object> attStmt, AttestationAuthData authData, byte[] clientDataHash)
      throws GeneralSecurityException, CertificateException {
    byte[] signature = requireAttStmtBytes(attStmt, "sig");
    List<X509Certificate> certificates = parseCertificates(attStmt);
    if (certificates.isEmpty()) {
      throw failure(
          WebAuthnVerificationError.ATTESTATION_OBJECT_INVALID,
          "FIDO U2F attestation missing certificate");
    }

    byte[] publicKeyU2f = convertCoseToUncompressed(authData.credentialPublicKey());

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    buffer.write(0x00);
    buffer.writeBytes(authData.rpIdHash());
    buffer.writeBytes(clientDataHash);
    buffer.writeBytes(authData.credentialId());
    buffer.writeBytes(publicKeyU2f);
    byte[] signedPayload = buffer.toByteArray();

    Signature verifier = Signature.getInstance("SHA256withECDSA");
    verifier.initVerify(certificates.get(0));
    verifier.update(signedPayload);
    if (!verifier.verify(signature)) {
      throw failure(
          WebAuthnVerificationError.SIGNATURE_INVALID, "FIDO-U2F attestation signature mismatch");
    }
    return certificates;
  }

  private static List<X509Certificate> verifyTpm(
      Map<String, Object> attStmt, AttestationAuthData authData, byte[] clientDataHash)
      throws GeneralSecurityException, CertificateException {
    byte[] signature = requireAttStmtBytes(attStmt, "sig");
    byte[] certInfo = requireAttStmtBytes(attStmt, "certInfo");
    byte[] pubArea = requireAttStmtBytes(attStmt, "pubArea");
    int algIdentifier = requireAttStmtInt(attStmt, "alg");

    List<X509Certificate> certificates = parseCertificates(attStmt);
    if (certificates.isEmpty()) {
      throw failure(
          WebAuthnVerificationError.ATTESTATION_OBJECT_INVALID,
          "TPM attestation missing certificate");
    }

    WebAuthnSignatureAlgorithm algorithm =
        WebAuthnSignatureAlgorithm.fromCoseIdentifier(algIdentifier);
    verifySignature(algorithm, certificates.get(0).getPublicKey(), certInfo, signature);

    int nameAlg = parseTpmNameAlg(pubArea);
    String digestAlgorithm = digestAlgorithmForTpm(nameAlg);

    TpmCertInfo info = parseTpmCertInfo(certInfo);
    if (info.magic != 0xff544347L || info.type != 0x8017) {
      throw failure(
          WebAuthnVerificationError.ATTESTATION_OBJECT_INVALID,
          "TPM certInfo has invalid magic or type");
    }

    MessageDigest digest = MessageDigest.getInstance(digestAlgorithm);
    digest.update(authData.rawAuthData());
    digest.update(clientDataHash);
    byte[] expectedExtraData = digest.digest();
    if (!Arrays.equals(expectedExtraData, info.extraData)) {
      throw failure(WebAuthnVerificationError.SIGNATURE_INVALID, "TPM certInfo extraData mismatch");
    }

    return certificates;
  }

  private static int parseTpmNameAlg(byte[] pubArea) {
    if (pubArea.length < 4) {
      throw failure(WebAuthnVerificationError.ATTESTATION_OBJECT_INVALID, "TPM pubArea truncated");
    }
    ByteBuffer buffer = ByteBuffer.wrap(pubArea).order(ByteOrder.BIG_ENDIAN);
    buffer.getShort(); // type
    return Short.toUnsignedInt(buffer.getShort());
  }

  private static String digestAlgorithmForTpm(int nameAlg) {
    return switch (nameAlg) {
      case 0x0004 -> "SHA-1";
      case 0x000B -> "SHA-256";
      case 0x000C -> "SHA-384";
      case 0x000D -> "SHA-512";
      default ->
          throw failure(
              WebAuthnVerificationError.ATTESTATION_OBJECT_INVALID,
              "Unsupported TPM name algorithm: " + nameAlg);
    };
  }

  private static TpmCertInfo parseTpmCertInfo(byte[] certInfo) {
    if (certInfo.length < 16) {
      throw failure(WebAuthnVerificationError.ATTESTATION_OBJECT_INVALID, "TPM certInfo truncated");
    }
    ByteBuffer buffer = ByteBuffer.wrap(certInfo).order(ByteOrder.BIG_ENDIAN);
    long magic = Integer.toUnsignedLong(buffer.getInt());
    int type = Short.toUnsignedInt(buffer.getShort());
    skipSizedField(buffer); // qualifiedSigner
    byte[] extraData = readSizedField(buffer);
    skipClockInfo(buffer);
    skipSizedField(buffer); // tpmsCertifyInfo name
    skipSizedField(buffer); // tpmsCertifyInfo qualifiedName
    return new TpmCertInfo(magic, type, extraData);
  }

  private static void skipClockInfo(ByteBuffer buffer) {
    if (buffer.remaining() < 17) {
      throw failure(
          WebAuthnVerificationError.ATTESTATION_OBJECT_INVALID, "TPM clockInfo truncated");
    }
    buffer.getLong(); // clock
    buffer.getInt(); // resetCount
    buffer.getInt(); // restartCount
    buffer.getInt(); // safe (TPMA_CLOCK)
  }

  private static void skipSizedField(ByteBuffer buffer) {
    int length = Short.toUnsignedInt(buffer.getShort());
    if (buffer.remaining() < length) {
      throw failure(
          WebAuthnVerificationError.ATTESTATION_OBJECT_INVALID, "TPM structure truncated");
    }
    buffer.position(buffer.position() + length);
  }

  private static byte[] readSizedField(ByteBuffer buffer) {
    int length = Short.toUnsignedInt(buffer.getShort());
    if (buffer.remaining() < length) {
      throw failure(
          WebAuthnVerificationError.ATTESTATION_OBJECT_INVALID, "TPM structure truncated");
    }
    byte[] value = new byte[length];
    buffer.get(value);
    return value;
  }

  private static List<X509Certificate> parseCertificates(Map<String, Object> attStmt)
      throws CertificateException {
    Object chain = attStmt.get("x5c");
    if (chain == null) {
      return List.of();
    }
    if (!(chain instanceof List<?> rawList)) {
      throw failure(WebAuthnVerificationError.ATTESTATION_OBJECT_INVALID, "x5c must be an array");
    }
    CertificateFactory factory = CertificateFactory.getInstance("X.509");
    List<X509Certificate> certificates = new ArrayList<>(rawList.size());
    for (Object element : rawList) {
      if (!(element instanceof byte[] der)) {
        throw failure(
            WebAuthnVerificationError.ATTESTATION_OBJECT_INVALID, "x5c entry must be byte array");
      }
      certificates.add(
          (X509Certificate) factory.generateCertificate(new java.io.ByteArrayInputStream(der)));
    }
    return certificates;
  }

  private static void verifySignature(
      WebAuthnSignatureAlgorithm algorithm,
      PublicKey publicKey,
      byte[] signedPayload,
      byte[] signature)
      throws GeneralSecurityException {
    Signature verifier = signatureFor(algorithm);
    verifier.initVerify(publicKey);
    if (algorithm == WebAuthnSignatureAlgorithm.PS256) {
      verifier.setParameter(
          new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
    }
    verifier.update(signedPayload);
    if (!verifier.verify(signature)) {
      throw failure(WebAuthnVerificationError.SIGNATURE_INVALID, "Attestation signature mismatch");
    }
  }

  private static Signature signatureFor(WebAuthnSignatureAlgorithm algorithm)
      throws GeneralSecurityException {
    return switch (algorithm) {
      case ES256, ES384, ES512 ->
          Signature.getInstance("SHA" + algorithm.label().substring(2) + "withECDSA");
      case RS256 -> Signature.getInstance("SHA256withRSA");
      case PS256 -> Signature.getInstance("SHA256withRSAandMGF1");
      case EDDSA -> Signature.getInstance("Ed25519");
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
    throw new GeneralSecurityException("Missing byte[] field " + key + " in COSE map");
  }

  private static byte[] requireAttStmtBytes(Map<String, Object> attStmt, String key) {
    Object value = attStmt.get(key);
    if (value instanceof byte[] bytes) {
      return bytes;
    }
    throw failure(
        WebAuthnVerificationError.ATTESTATION_OBJECT_INVALID,
        "Attestation statement missing byte[] field " + key);
  }

  private static int requireAttStmtInt(Map<String, Object> attStmt, String key) {
    Object value = attStmt.get(key);
    if (value instanceof Number number) {
      return number.intValue();
    }
    throw failure(
        WebAuthnVerificationError.ATTESTATION_OBJECT_INVALID,
        "Attestation statement missing integer field " + key);
  }

  private static byte[] convertCoseToUncompressed(byte[] publicKeyCose)
      throws GeneralSecurityException {
    Map<Integer, Object> cose = decodeCoseMap(publicKeyCose);
    byte[] x = requireBytes(cose, -2);
    byte[] y = requireBytes(cose, -3);
    if (x.length != y.length) {
      throw new GeneralSecurityException("Invalid COSE EC point");
    }
    byte[] uncompressed = new byte[1 + x.length + y.length];
    uncompressed[0] = 0x04;
    System.arraycopy(x, 0, uncompressed, 1, x.length);
    System.arraycopy(y, 0, uncompressed, 1 + x.length, y.length);
    return uncompressed;
  }

  private static byte[] hashSha256(byte[] data) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return digest.digest(data);
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }

  private static byte[] hashSha256(String value) {
    return hashSha256(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] concatenate(byte[] first, byte[] second) {
    byte[] result = new byte[first.length + second.length];
    System.arraycopy(first, 0, result, 0, first.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
  }

  private static Map<String, String> extractJsonValues(String json) {
    Map<String, String> values = new LinkedHashMap<>();
    int index = 0;
    while (index < json.length()) {
      int keyStart = json.indexOf('"', index);
      if (keyStart == -1) {
        break;
      }
      int keyEnd = json.indexOf('"', keyStart + 1);
      if (keyEnd == -1) {
        break;
      }
      String key = json.substring(keyStart + 1, keyEnd);

      int colon = json.indexOf(':', keyEnd);
      if (colon == -1) {
        break;
      }
      int valueStart = json.indexOf('"', colon);
      if (valueStart == -1) {
        break;
      }
      int valueEnd = json.indexOf('"', valueStart + 1);
      if (valueEnd == -1) {
        break;
      }
      String value = json.substring(valueStart + 1, valueEnd);
      values.put(key, value);
      index = valueEnd + 1;
    }
    return values;
  }

  private static byte[] decodeBase64Url(String input) {
    if (input == null) {
      return new byte[0];
    }
    String normalized = input.replace('-', '+').replace('_', '/');
    int padding = (4 - (normalized.length() % 4)) % 4;
    normalized = normalized + "=".repeat(padding);
    return Base64.getDecoder().decode(normalized);
  }

  private static String requireString(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value instanceof String str && !str.isBlank()) {
      return str;
    }
    throw failure(
        WebAuthnVerificationError.ATTESTATION_OBJECT_INVALID, "Missing string field '" + key + "'");
  }

  private static VerificationFailure failure(WebAuthnVerificationError error, String message) {
    return new VerificationFailure(error, message);
  }

  private record ClientData(String type, byte[] challenge) {
    // Marker record for parsed client data.
  }

  private record AttestationObject(
      WebAuthnAttestationFormat format, byte[] authData, Map<String, Object> attStmt) {
    // CBOR-decoded attestation object payload.
  }

  private record AttestationAuthData(
      byte[] rawAuthData,
      byte[] rpIdHash,
      int flags,
      long signCount,
      byte[] aaguid,
      byte[] credentialId,
      byte[] credentialPublicKey,
      WebAuthnSignatureAlgorithm algorithm,
      boolean userVerified) {
    // Parsed authenticator data for attestation.
  }

  private record TpmCertInfo(long magic, int type, byte[] extraData) {
    // Minimal subset of TPM certInfo fields required for verification.
  }

  private static final class VerificationFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final WebAuthnVerificationError error;

    private VerificationFailure(WebAuthnVerificationError error, String message) {
      super(message);
      this.error = error;
    }
  }
}
