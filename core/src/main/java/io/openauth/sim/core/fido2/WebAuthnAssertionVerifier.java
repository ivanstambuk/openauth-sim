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
import java.security.spec.EdECPoint;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.security.spec.RSAPublicKeySpec;
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

            PublicKey publicKey =
                    createPublicKeyFromCose(storedCredential.publicKeyCose(), storedCredential.algorithm());
            byte[] clientDataHash = hashSha256(assertionRequest.clientDataJson());
            byte[] signedPayload = concatenate(assertionRequest.authenticatorData(), clientDataHash);

            Signature signature = signatureFor(storedCredential.algorithm());
            signature.initVerify(publicKey);
            if (storedCredential.algorithm() == WebAuthnSignatureAlgorithm.PS256) {
                signature.setParameter(new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
            }
            signature.update(signedPayload);
            if (!signature.verify(assertionRequest.signature())) {
                return WebAuthnVerificationResult.failure(
                        WebAuthnVerificationError.SIGNATURE_INVALID, "Authenticator signature mismatch");
            }

            return WebAuthnVerificationResult.successResult();
        } catch (VerificationFailure vf) {
            return WebAuthnVerificationResult.failure(vf.error, vf.getMessage());
        } catch (GeneralSecurityException gse) {
            return WebAuthnVerificationResult.failure(WebAuthnVerificationError.SIGNATURE_INVALID, gse.getMessage());
        }
    }

    private static void parseClientData(WebAuthnAssertionRequest request, WebAuthnStoredCredential storedCredential) {
        String json = new String(request.clientDataJson(), StandardCharsets.UTF_8);
        Map<String, String> values = extractJsonValues(json);

        String type = values.get("type");
        if (!request.expectedType().equals(type)) {
            throw failure(WebAuthnVerificationError.CLIENT_DATA_TYPE_MISMATCH, "Unexpected client data type");
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
            throw failure(WebAuthnVerificationError.RP_ID_HASH_MISMATCH, "Authenticator data length is insufficient");
        }

        byte[] rpIdHash = Arrays.copyOfRange(authenticatorData, 0, RP_ID_HASH_LENGTH);
        byte[] expectedRpHash = hashSha256(storedCredential.relyingPartyId());
        if (!Arrays.equals(expectedRpHash, rpIdHash)) {
            throw failure(WebAuthnVerificationError.RP_ID_HASH_MISMATCH, "Authenticator RP hash mismatch");
        }

        int flags = authenticatorData[RP_ID_HASH_LENGTH] & 0xFF;
        boolean userVerified = (flags & 0x04) != 0;
        if (storedCredential.userVerificationRequired() && !userVerified) {
            throw failure(WebAuthnVerificationError.USER_VERIFICATION_REQUIRED, "User verification was required");
        }

        int counterOffset = RP_ID_HASH_LENGTH + 1;
        long counter = ByteBuffer.wrap(authenticatorData, counterOffset, COUNTER_LENGTH)
                        .getInt()
                & 0xFFFFFFFFL;
        if (counter < storedCredential.signatureCounter()) {
            throw failure(WebAuthnVerificationError.COUNTER_REGRESSION, "Authenticator counter regressed");
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

    private static PublicKey createPublicKeyFromCose(byte[] coseKey, WebAuthnSignatureAlgorithm algorithm)
            throws GeneralSecurityException {
        Map<Integer, Object> map = decodeCoseMap(coseKey);

        int kty = requireInt(map, 1);
        int coseAlgorithm = requireInt(map, 3);
        if (coseAlgorithm != algorithm.coseIdentifier()) {
            throw new GeneralSecurityException("COSE algorithm mismatch for credential");
        }

        return switch (algorithm) {
            case ES256, ES384, ES512 -> createEcPublicKey(map, kty, algorithm);
            case RS256, PS256 -> createRsaPublicKey(map, kty);
            case EDDSA -> createEd25519PublicKey(map, kty);
        };
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
            throw new GeneralSecurityException("Unexpected EC curve id " + curve + " for " + algorithm);
        }
        byte[] x = requireBytes(map, -2);
        byte[] y = requireBytes(map, -3);

        String curveName =
                switch (curve) {
                    case 1 -> "secp256r1";
                    case 2 -> "secp384r1";
                    case 3 -> "secp521r1";
                    default -> throw new GeneralSecurityException("Unsupported EC curve id: " + curve);
                };

        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec(curveName));
        ECParameterSpec ecParameters = parameters.getParameterSpec(ECParameterSpec.class);

        ECPoint ecPoint = new ECPoint(new BigInteger(1, x), new BigInteger(1, y));
        KeyFactory factory = KeyFactory.getInstance("EC");
        return factory.generatePublic(new ECPublicKeySpec(ecPoint, ecParameters));
    }

    private static PublicKey createRsaPublicKey(Map<Integer, Object> map, int keyType) throws GeneralSecurityException {
        if (keyType != 3) {
            throw new GeneralSecurityException("Expected RSA key type for signature algorithm");
        }
        byte[] modulusBytes = requireBytes(map, -1);
        byte[] exponentBytes = requireBytes(map, -2);

        BigInteger modulus = new BigInteger(1, modulusBytes);
        BigInteger exponent = new BigInteger(1, exponentBytes);

        KeyFactory factory = KeyFactory.getInstance("RSA");
        return factory.generatePublic(new RSAPublicKeySpec(modulus, exponent));
    }

    private static PublicKey createEd25519PublicKey(Map<Integer, Object> map, int keyType)
            throws GeneralSecurityException {
        if (keyType != 1) {
            throw new GeneralSecurityException("Expected OKP key type for EdDSA algorithm");
        }
        int curve = requireInt(map, -1);
        if (curve != 6) {
            throw new GeneralSecurityException("Unsupported OKP curve id: " + curve);
        }
        byte[] encoded = requireBytes(map, -2);
        if (encoded.length != 32) {
            throw new GeneralSecurityException("Ed25519 public key must be 32 bytes");
        }

        boolean xOdd = (encoded[encoded.length - 1] & 0x80) != 0;
        byte[] y = encoded.clone();
        y[y.length - 1] &= 0x7F;

        NamedParameterSpec parameterSpec = NamedParameterSpec.ED25519;
        EdECPoint point = new EdECPoint(xOdd, littleEndianToBigInteger(y));
        KeyFactory factory = KeyFactory.getInstance("Ed25519");
        return factory.generatePublic(new EdECPublicKeySpec(parameterSpec, point));
    }

    private static Signature signatureFor(WebAuthnSignatureAlgorithm algorithm) throws GeneralSecurityException {
        return switch (algorithm) {
            case ES256 -> Signature.getInstance("SHA256withECDSA");
            case ES384 -> Signature.getInstance("SHA384withECDSA");
            case ES512 -> Signature.getInstance("SHA512withECDSA");
            case RS256 -> Signature.getInstance("SHA256withRSA");
            case PS256 -> Signature.getInstance("RSASSA-PSS");
            case EDDSA -> Signature.getInstance("Ed25519");
        };
    }

    private static int requireInt(Map<Integer, Object> map, int key) throws GeneralSecurityException {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new GeneralSecurityException("Missing integer field for key " + key);
    }

    private static byte[] requireBytes(Map<Integer, Object> map, int key) throws GeneralSecurityException {
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

    private static BigInteger littleEndianToBigInteger(byte[] littleEndian) {
        byte[] reversed = littleEndian.clone();
        for (int i = 0; i < reversed.length / 2; i++) {
            byte tmp = reversed[i];
            reversed[i] = reversed[reversed.length - 1 - i];
            reversed[reversed.length - 1 - i] = tmp;
        }
        return new BigInteger(1, reversed);
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

    private static Map<Integer, Object> decodeCoseMap(byte[] coseKey) throws GeneralSecurityException {
        Object decoded = CborDecoder.decode(coseKey);
        if (!(decoded instanceof Map<?, ?> rawMap)) {
            throw new GeneralSecurityException("COSE key is not a CBOR map");
        }
        Map<Integer, Object> map = new HashMap<>(rawMap.size());
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof Number number)) {
                throw new GeneralSecurityException("COSE key contains non-numeric field identifiers");
            }
            map.put(number.intValue(), entry.getValue());
        }
        return map;
    }
}
