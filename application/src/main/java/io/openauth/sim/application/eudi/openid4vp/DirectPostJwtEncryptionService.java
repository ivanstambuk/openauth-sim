package io.openauth.sim.application.eudi.openid4vp;

import io.openauth.sim.core.json.SimpleJson;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EllipticCurve;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** HAIP `direct_post.jwt` encryption/decryption helper for Feature 040. */
public final class DirectPostJwtEncryptionService {

    private static final String CURVE_NAME = "secp256r1";
    private static final String JWE_ALG = "ECDH-ES";
    private static final String JWE_ENC = "A128GCM";
    private static final String ALGORITHM_METADATA = JWE_ALG + "+" + JWE_ENC;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BYTES = 16;
    private static final int GCM_TAG_BITS = GCM_TAG_BYTES * 8;
    private static final int AES_KEY_BYTES = 16;
    private static final int COORDINATE_BYTES = 32;
    private static final Base64.Encoder BASE64_URL_ENCODER =
            Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final KeyFactory EC_KEY_FACTORY = initKeyFactory();
    private static final ECParameterSpec P256_PARAMETERS = initEcParameters();
    private static final EllipticCurve CURVE = P256_PARAMETERS.getCurve();
    private static final BigInteger CURVE_P = ((ECFieldFp) CURVE.getField()).getP();
    private static final BigInteger CURVE_A = CURVE.getA().mod(CURVE_P);
    private static final BigInteger CURVE_B = CURVE.getB().mod(CURVE_P);
    private static final ECPoint GENERATOR = P256_PARAMETERS.getGenerator();
    private static final BigInteger TWO = BigInteger.valueOf(2);
    private static final BigInteger THREE = BigInteger.valueOf(3);

    private final Dependencies dependencies;
    private final SecureRandom secureRandom;

    public DirectPostJwtEncryptionService(Dependencies dependencies) {
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
        this.secureRandom = new SecureRandom();
    }

    public EncryptResult encrypt(EncryptRequest request) {
        Objects.requireNonNull(request, "request");
        boolean enforced = requiresEncryption(request.profile(), request.responseMode());
        if (!enforced) {
            dependencies
                    .telemetryPublisher()
                    .recordEncryption(request.requestId(), false, 0L, request.profile(), request.responseMode());
            return new EncryptResult(request.requestId(), false, Optional.empty(), 0L, Optional.empty());
        }

        long startMillis = dependencies.clock().millis();
        try {
            ResolvedKeys keys = resolveKeys(request.profile());
            String compactJwe = encryptPayload(keys, request.payload());
            long latency = Math.max(1L, dependencies.clock().millis() - startMillis);
            dependencies
                    .telemetryPublisher()
                    .recordEncryption(request.requestId(), true, latency, request.profile(), request.responseMode());
            return new EncryptResult(
                    request.requestId(), true, Optional.of(compactJwe), latency, Optional.of(ALGORITHM_METADATA));
        } catch (Oid4vpValidationException ex) {
            throw ex;
        } catch (GeneralSecurityException ex) {
            throw invalidRequest("direct_post.jwt encryption failed", ex);
        } catch (RuntimeException ex) {
            throw invalidRequest("direct_post.jwt payload invalid", ex);
        }
    }

    public DecryptResult decrypt(DecryptRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            String[] segments = request.directPostJwt().split("\\.", -1);
            if (segments.length != 5) {
                throw invalidRequest("direct_post.jwt must contain five segments");
            }
            String headerSegment = segments[0];
            String encryptedKeySegment = segments[1];
            if (!encryptedKeySegment.isEmpty()) {
                throw invalidRequest("ECDH-ES direct key management must omit the encrypted key segment");
            }
            Map<String, Object> header = parseJsonObject(
                    new String(base64UrlDecode(headerSegment), StandardCharsets.UTF_8), "direct_post.jwt header");
            validateHeader(header);
            Map<String, Object> epkJson = requireJsonObject(header.get("epk"), "header.epk");
            ECPublicKey ephemeralPublic = parseEphemeralPublic(epkJson);

            ResolvedKeys keys = resolveKeys(request.profile());
            byte[] sharedSecret = deriveSharedSecret(keys.privateKey(), ephemeralPublic);
            byte[] cek = deriveContentEncryptionKey(sharedSecret);

            byte[] iv = base64UrlDecode(segments[2]);
            byte[] ciphertext = base64UrlDecode(segments[3]);
            byte[] tag = base64UrlDecode(segments[4]);
            byte[] cipherAndTag = new byte[ciphertext.length + tag.length];
            System.arraycopy(ciphertext, 0, cipherAndTag, 0, ciphertext.length);
            System.arraycopy(tag, 0, cipherAndTag, ciphertext.length, tag.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKey key = new SecretKeySpec(cek, "AES");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(headerSegment.getBytes(StandardCharsets.US_ASCII));
            byte[] plaintext = cipher.doFinal(cipherAndTag);

            Map<String, Object> payload = parsePayload(plaintext);
            return new DecryptResult(request.requestId(), payload);
        } catch (Oid4vpValidationException ex) {
            throw ex;
        } catch (GeneralSecurityException ex) {
            throw invalidRequest("direct_post.jwt decryption failed", ex);
        } catch (RuntimeException ex) {
            throw invalidRequest("direct_post.jwt structure invalid", ex);
        }
    }

    private static boolean requiresEncryption(
            OpenId4VpWalletSimulationService.Profile profile,
            OpenId4VpWalletSimulationService.ResponseMode responseMode) {
        return profile == OpenId4VpWalletSimulationService.Profile.HAIP
                && responseMode == OpenId4VpWalletSimulationService.ResponseMode.DIRECT_POST_JWT;
    }

    private String encryptPayload(ResolvedKeys keys, Map<String, Object> payload) throws GeneralSecurityException {
        ECPublicKey verifierPublic = keys.publicKey();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec(CURVE_NAME), secureRandom);
        KeyPair ephemeral = generator.generateKeyPair();
        byte[] sharedSecret = deriveSharedSecret(ephemeral.getPrivate(), verifierPublic);
        byte[] cek = deriveContentEncryptionKey(sharedSecret);

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", JWE_ALG);
        header.put("enc", JWE_ENC);
        header.put("kid", keys.material().keyId());
        header.put("epk", toJwk((ECPublicKey) ephemeral.getPublic()));
        String headerJson = JsonEncoder.encode(header);
        String headerSegment = BASE64_URL_ENCODER.encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));

        String payloadJson = JsonEncoder.encode(payload);
        byte[] iv = randomBytes(GCM_IV_BYTES);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        SecretKey key = new SecretKeySpec(cek, "AES");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        cipher.updateAAD(headerSegment.getBytes(StandardCharsets.US_ASCII));
        byte[] cipherWithTag = cipher.doFinal(payloadJson.getBytes(StandardCharsets.UTF_8));

        int cipherLength = cipherWithTag.length - GCM_TAG_BYTES;
        byte[] ciphertext = Arrays.copyOf(cipherWithTag, cipherLength);
        byte[] tag = Arrays.copyOfRange(cipherWithTag, cipherLength, cipherWithTag.length);

        return String.join(
                ".",
                headerSegment,
                "",
                BASE64_URL_ENCODER.encodeToString(iv),
                BASE64_URL_ENCODER.encodeToString(ciphertext),
                BASE64_URL_ENCODER.encodeToString(tag));
    }

    private static byte[] deriveSharedSecret(java.security.PrivateKey privateKey, ECPublicKey publicKey)
            throws GeneralSecurityException {
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(privateKey);
        agreement.doPhase(publicKey, true);
        return agreement.generateSecret();
    }

    private static byte[] deriveContentEncryptionKey(byte[] sharedSecret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ByteBuffer otherInfo = ByteBuffer.allocate(4 + JWE_ENC.length() + 4 + 4 + 4 + 4);
            otherInfo.putInt(JWE_ENC.length());
            otherInfo.put(JWE_ENC.getBytes(StandardCharsets.US_ASCII));
            otherInfo.putInt(0); // PartyUInfo length
            otherInfo.putInt(0); // PartyVInfo length
            otherInfo.putInt(AES_KEY_BYTES * 8);
            otherInfo.putInt(0); // SuppPrivInfo length
            byte[] info = otherInfo.array();
            byte[] counter = new byte[] {0, 0, 0, 1};
            digest.update(counter);
            digest.update(sharedSecret);
            digest.update(info);
            byte[] derived = digest.digest();
            return Arrays.copyOf(derived, AES_KEY_BYTES);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private static void validateHeader(Map<String, Object> header) {
        String alg = stringValue(header, "alg", "header.alg");
        String enc = stringValue(header, "enc", "header.enc");
        if (!JWE_ALG.equals(alg) || !JWE_ENC.equals(enc)) {
            throw invalidRequest("direct_post.jwt must use alg " + JWE_ALG + " and enc " + JWE_ENC);
        }
    }

    private static Map<String, Object> parseJsonObject(String json, String context) {
        try {
            Object parsed = SimpleJson.parse(json);
            return requireJsonObject(parsed, context);
        } catch (Oid4vpValidationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw invalidRequest(context + " must be valid JSON", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requireJsonObject(Object value, String context) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw invalidRequest(context + " keys must be strings");
                }
                copy.put(key, entry.getValue());
            }
            return copy;
        }
        throw invalidRequest(context + " must be a JSON object");
    }

    private static Map<String, Object> parsePayload(byte[] plaintext) {
        String json = new String(plaintext, StandardCharsets.UTF_8);
        Map<String, Object> payload = parseJsonObject(json, "direct_post.jwt payload");
        return payload;
    }

    private static Optional<ECPublicKey> parsePublicKey(String jwkJson) {
        if (jwkJson == null || jwkJson.isBlank()) {
            return Optional.empty();
        }
        Map<String, Object> jwk = parseJsonObject(jwkJson, "encryption public JWK");
        Object curve = jwk.get("crv");
        if (curve instanceof String crv && !"P-256".equals(crv)) {
            throw invalidRequest("encryption public JWK must use P-256 curve");
        }
        Object xValue = jwk.get("x");
        Object yValue = jwk.get("y");
        if (!(xValue instanceof String x) || x.isBlank()) {
            return Optional.empty();
        }
        if (!(yValue instanceof String y) || y.isBlank()) {
            return Optional.empty();
        }
        try {
            ECPublicKey key = buildPublicKey(x, y);
            if (!isOnCurve(key.getW())) {
                return Optional.empty();
            }
            return Optional.of(key);
        } catch (GeneralSecurityException ex) {
            return Optional.empty();
        }
    }

    private static ECPublicKey parseEphemeralPublic(Map<String, Object> jwk) throws GeneralSecurityException {
        String crv = stringValue(jwk, "crv", "header.epk");
        if (!"P-256".equals(crv)) {
            throw invalidRequest("header.epk must use P-256 curve");
        }
        String x = stringValue(jwk, "x", "header.epk");
        String y = stringValue(jwk, "y", "header.epk");
        return buildPublicKey(x, y);
    }

    private static ECPublicKey buildPublicKey(String xValue, String yValue) throws GeneralSecurityException {
        ECPoint point = new ECPoint(asUnsignedBigInteger(xValue), asUnsignedBigInteger(yValue));
        ECPublicKeySpec spec = new ECPublicKeySpec(point, P256_PARAMETERS);
        return (ECPublicKey) EC_KEY_FACTORY.generatePublic(spec);
    }

    private static ECPrivateKey parsePrivateKey(String jwkJson) throws GeneralSecurityException {
        Map<String, Object> jwk = parseJsonObject(jwkJson, "encryption private JWK");
        String curve = stringValue(jwk, "crv", "encryption private JWK");
        if (!"P-256".equals(curve)) {
            throw invalidRequest("encryption private JWK must use P-256 curve");
        }
        String d = stringValue(jwk, "d", "encryption private JWK");
        ECPrivateKeySpec spec = new ECPrivateKeySpec(asUnsignedBigInteger(d), P256_PARAMETERS);
        return (ECPrivateKey) EC_KEY_FACTORY.generatePrivate(spec);
    }

    private static ECPublicKey derivePublicKey(ECPrivateKey privateKey) throws GeneralSecurityException {
        ECPoint point = scalarMultiply(GENERATOR, privateKey.getS());
        if (point.equals(ECPoint.POINT_INFINITY) || !isOnCurve(point)) {
            throw new GeneralSecurityException("Unable to derive public key from private scalar");
        }
        return (ECPublicKey) EC_KEY_FACTORY.generatePublic(new ECPublicKeySpec(point, P256_PARAMETERS));
    }

    private static ECPoint scalarMultiply(ECPoint point, BigInteger scalar) {
        ECPoint result = ECPoint.POINT_INFINITY;
        ECPoint addend = point;
        BigInteger k = scalar;
        while (k.signum() > 0) {
            if (k.testBit(0)) {
                result = addPoints(result, addend);
            }
            addend = doublePoint(addend);
            k = k.shiftRight(1);
        }
        return result;
    }

    private static ECPoint addPoints(ECPoint p, ECPoint q) {
        if (p.equals(ECPoint.POINT_INFINITY)) {
            return q;
        }
        if (q.equals(ECPoint.POINT_INFINITY)) {
            return p;
        }
        BigInteger x1 = mod(p.getAffineX());
        BigInteger y1 = mod(p.getAffineY());
        BigInteger x2 = mod(q.getAffineX());
        BigInteger y2 = mod(q.getAffineY());

        if (x1.equals(x2)) {
            if (mod(y1.add(y2)).equals(BigInteger.ZERO)) {
                return ECPoint.POINT_INFINITY;
            }
            return doublePoint(p);
        }

        BigInteger numerator = mod(y2.subtract(y1));
        BigInteger denominator = mod(x2.subtract(x1));
        BigInteger lambda = numerator.multiply(denominator.modInverse(CURVE_P)).mod(CURVE_P);
        BigInteger xr = mod(lambda.multiply(lambda).subtract(x1).subtract(x2));
        BigInteger yr = mod(lambda.multiply(x1.subtract(xr)).subtract(y1));
        return new ECPoint(xr, yr);
    }

    private static ECPoint doublePoint(ECPoint point) {
        if (point.equals(ECPoint.POINT_INFINITY)) {
            return point;
        }
        BigInteger x = mod(point.getAffineX());
        BigInteger y = mod(point.getAffineY());
        if (y.equals(BigInteger.ZERO)) {
            return ECPoint.POINT_INFINITY;
        }
        BigInteger xSquared = x.multiply(x).mod(CURVE_P);
        BigInteger numerator = mod(THREE.multiply(xSquared).add(CURVE_A));
        BigInteger denominator = mod(TWO.multiply(y));
        BigInteger lambda = numerator.multiply(denominator.modInverse(CURVE_P)).mod(CURVE_P);
        BigInteger xr = mod(lambda.multiply(lambda).subtract(TWO.multiply(x)));
        BigInteger yr = mod(lambda.multiply(x.subtract(xr)).subtract(y));
        return new ECPoint(xr, yr);
    }

    private static boolean isOnCurve(ECPoint point) {
        if (point == null || point.equals(ECPoint.POINT_INFINITY)) {
            return false;
        }
        BigInteger x = mod(point.getAffineX());
        BigInteger y = mod(point.getAffineY());
        BigInteger left = y.multiply(y).mod(CURVE_P);
        BigInteger right = x.multiply(x)
                .multiply(x)
                .mod(CURVE_P)
                .add(CURVE_A.multiply(x))
                .add(CURVE_B)
                .mod(CURVE_P);
        return left.equals(right);
    }

    private static BigInteger mod(BigInteger value) {
        BigInteger result = value.mod(CURVE_P);
        return result.signum() < 0 ? result.add(CURVE_P) : result;
    }

    private static BigInteger asUnsignedBigInteger(String base64Url) {
        byte[] bytes = base64UrlDecode(base64Url);
        return new BigInteger(1, bytes);
    }

    private static byte[] base64UrlDecode(String input) {
        try {
            return BASE64_URL_DECODER.decode(input);
        } catch (IllegalArgumentException ex) {
            throw invalidRequest("direct_post.jwt segments must be base64url encoded", ex);
        }
    }

    private static Map<String, Object> toJwk(ECPublicKey publicKey) {
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "EC");
        jwk.put("crv", "P-256");
        jwk.put(
                "x",
                BASE64_URL_ENCODER.encodeToString(toCoordinate(publicKey.getW().getAffineX())));
        jwk.put(
                "y",
                BASE64_URL_ENCODER.encodeToString(toCoordinate(publicKey.getW().getAffineY())));
        return jwk;
    }

    private static byte[] toCoordinate(BigInteger value) {
        byte[] raw = value.toByteArray();
        if (raw.length == COORDINATE_BYTES) {
            return raw;
        }
        byte[] coordinate = new byte[COORDINATE_BYTES];
        int srcPos = Math.max(0, raw.length - COORDINATE_BYTES);
        int length = raw.length - srcPos;
        System.arraycopy(raw, srcPos, coordinate, COORDINATE_BYTES - length, length);
        return coordinate;
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    private static String stringValue(Map<String, Object> json, String field, String context) {
        Object value = json.get(field);
        if (value instanceof String str && !str.isBlank()) {
            return str;
        }
        throw invalidRequest(context + " missing required field " + field);
    }

    private static KeyFactory initKeyFactory() {
        try {
            return KeyFactory.getInstance("EC");
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("EC KeyFactory not available", ex);
        }
    }

    private static ECParameterSpec initEcParameters() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec(CURVE_NAME));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to load P-256 parameters", ex);
        }
    }

    private static Oid4vpValidationException invalidRequest(String detail) {
        return invalidRequest(detail, null);
    }

    private static Oid4vpValidationException invalidRequest(String detail, Throwable cause) {
        Oid4vpValidationException exception =
                new Oid4vpValidationException(Oid4vpProblemDetailsMapper.invalidRequest(detail, List.of()));
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }

    private EncryptionKeyMaterial loadKeyMaterial(OpenId4VpWalletSimulationService.Profile profile) {
        try {
            EncryptionKeyMaterial material =
                    dependencies.encryptionKeyRepository().load(profile);
            if (material == null) {
                throw invalidRequest("Encryption key material unavailable for profile " + profile);
            }
            return material;
        } catch (Oid4vpValidationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw invalidRequest("Encryption key material unavailable for profile " + profile, ex);
        }
    }

    private ResolvedKeys resolveKeys(OpenId4VpWalletSimulationService.Profile profile) throws GeneralSecurityException {
        EncryptionKeyMaterial material = loadKeyMaterial(profile);
        ECPrivateKey privateKey = parsePrivateKey(material.privateJwk());
        Optional<ECPublicKey> parsedPublic = parsePublicKey(material.publicJwk());
        ECPublicKey publicKey = parsedPublic.orElseGet(() -> {
            try {
                return derivePublicKey(privateKey);
            } catch (GeneralSecurityException e) {
                throw invalidRequest("Unable to derive encryption public key", e);
            }
        });
        return new ResolvedKeys(material, publicKey, privateKey);
    }

    private record ResolvedKeys(EncryptionKeyMaterial material, ECPublicKey publicKey, ECPrivateKey privateKey) {
        // Value container; behaviour lives in the enclosing service.
    }

    private static final class JsonEncoder {
        private JsonEncoder() {
            // Utility holder; prevent instantiation.
        }

        static String encode(Map<String, Object> map) {
            StringBuilder builder = new StringBuilder();
            writeValue(builder, map);
            return builder.toString();
        }

        @SuppressWarnings("unchecked")
        private static void writeValue(StringBuilder builder, Object value) {
            if (value == null) {
                builder.append("null");
                return;
            }
            if (value instanceof String str) {
                writeString(builder, str);
                return;
            }
            if (value instanceof Number || value instanceof Boolean) {
                builder.append(value.toString());
                return;
            }
            if (value instanceof Map<?, ?> map) {
                builder.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) {
                        throw invalidRequest("JSON object keys must be strings");
                    }
                    if (!first) {
                        builder.append(',');
                    }
                    first = false;
                    writeString(builder, key);
                    builder.append(':');
                    writeValue(builder, entry.getValue());
                }
                builder.append('}');
                return;
            }
            if (value instanceof List<?> list) {
                builder.append('[');
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        builder.append(',');
                    }
                    writeValue(builder, list.get(i));
                }
                builder.append(']');
                return;
            }
            throw invalidRequest(
                    "Unsupported JSON value type " + value.getClass().getSimpleName());
        }

        private static void writeString(StringBuilder builder, String value) {
            builder.append('"');
            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);
                switch (ch) {
                    case '"' -> builder.append("\\\"");
                    case '\\' -> builder.append("\\\\");
                    case '\b' -> builder.append("\\b");
                    case '\f' -> builder.append("\\f");
                    case '\n' -> builder.append("\\n");
                    case '\r' -> builder.append("\\r");
                    case '\t' -> builder.append("\\t");
                    default -> {
                        if (ch < 0x20) {
                            builder.append(String.format("\\u%04x", (int) ch));
                        } else {
                            builder.append(ch);
                        }
                    }
                }
            }
            builder.append('"');
        }
    }

    public record Dependencies(
            EncryptionKeyRepository encryptionKeyRepository,
            EncryptionTelemetryPublisher telemetryPublisher,
            Clock clock) {
        public Dependencies {
            Objects.requireNonNull(encryptionKeyRepository, "encryptionKeyRepository");
            Objects.requireNonNull(telemetryPublisher, "telemetryPublisher");
            Objects.requireNonNull(clock, "clock");
        }
    }

    public interface EncryptionKeyRepository {
        EncryptionKeyMaterial load(OpenId4VpWalletSimulationService.Profile profile);
    }

    public interface EncryptionTelemetryPublisher {
        void recordEncryption(
                String requestId,
                boolean enforced,
                long latencyMillis,
                OpenId4VpWalletSimulationService.Profile profile,
                OpenId4VpWalletSimulationService.ResponseMode responseMode);
    }

    public record EncryptionKeyMaterial(String keyId, String publicJwk, String privateJwk) {
        public EncryptionKeyMaterial {
            Objects.requireNonNull(keyId, "keyId");
            Objects.requireNonNull(publicJwk, "publicJwk");
            Objects.requireNonNull(privateJwk, "privateJwk");
        }
    }

    public record EncryptRequest(
            String requestId,
            OpenId4VpWalletSimulationService.Profile profile,
            OpenId4VpWalletSimulationService.ResponseMode responseMode,
            String responseUri,
            Map<String, Object> payload) {
        public EncryptRequest {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(responseMode, "responseMode");
            Objects.requireNonNull(responseUri, "responseUri");
            payload = payload == null ? Map.of() : Map.copyOf(payload);
        }
    }

    public record EncryptResult(
            String requestId,
            boolean encryptionRequired,
            Optional<String> directPostJwt,
            long latencyMillis,
            Optional<String> algorithm) {
        public EncryptResult {
            Objects.requireNonNull(requestId, "requestId");
            directPostJwt = directPostJwt == null ? Optional.empty() : directPostJwt;
            algorithm = algorithm == null ? Optional.empty() : algorithm;
        }
    }

    public record DecryptRequest(
            String requestId, OpenId4VpWalletSimulationService.Profile profile, String directPostJwt) {
        public DecryptRequest {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(directPostJwt, "directPostJwt");
        }
    }

    public record DecryptResult(String requestId, Map<String, Object> payload) {
        public DecryptResult {
            Objects.requireNonNull(requestId, "requestId");
            payload = payload == null ? Map.of() : Map.copyOf(payload);
        }
    }
}
