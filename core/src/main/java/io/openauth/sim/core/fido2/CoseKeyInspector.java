package io.openauth.sim.core.fido2;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Utility that decodes COSE public keys and exposes structured metadata (key type, curve, coordinates,
 * modulus/exponent, and RFC 7638 JWK thumbprints) suitable for verbose trace instrumentation.
 */
public final class CoseKeyInspector {

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private CoseKeyInspector() {
        throw new AssertionError("Utility class");
    }

    /**
     * Inspect the supplied COSE key and derive structured metadata for trace output.
     *
     * @param coseKey   CBOR-encoded COSE public key bytes
     * @param algorithm Expected WebAuthn algorithm (may be {@code null} when unknown)
     * @return {@link CoseKeyDetails} describing the decoded key attributes
     * @throws GeneralSecurityException if the COSE payload cannot be parsed
     */
    public static CoseKeyDetails inspect(byte[] coseKey, WebAuthnSignatureAlgorithm algorithm)
            throws GeneralSecurityException {
        Map<Integer, Object> map = decodeCoseMap(coseKey);
        int keyType = requireInt(map, 1);
        int coseAlgorithm = requireInt(map, 3);

        OptionalInt curve = OptionalInt.empty();
        Optional<String> curveName = Optional.empty();
        Optional<String> xCoordinate = Optional.empty();
        Optional<String> yCoordinate = Optional.empty();
        Optional<String> modulus = Optional.empty();
        Optional<String> exponent = Optional.empty();

        switch (keyType) {
            case 2 -> {
                int curveId = requireInt(map, -1);
                curve = OptionalInt.of(curveId);
                curveName = Optional.of(curveName(curveId));
                xCoordinate = Optional.of(base64Url(requireBytes(map, -2)));
                yCoordinate = Optional.of(base64Url(requireBytes(map, -3)));
            }
            case 3 -> {
                modulus = Optional.of(base64Url(requireBytes(map, -1)));
                exponent = Optional.of(base64Url(requireBytes(map, -2)));
            }
            case 1 -> {
                int curveId = requireInt(map, -1);
                curve = OptionalInt.of(curveId);
                curveName = Optional.of(curveName(curveId));
                xCoordinate = Optional.of(base64Url(requireBytes(map, -2)));
            }
            default -> {
                // Leave optional fields empty for unsupported key types while still surfacing raw hex.
            }
        }

        Optional<String> thumbprint =
                computeThumbprint(keyType, curveName, xCoordinate, yCoordinate, modulus, exponent);

        String algorithmName = resolveAlgorithmName(algorithm, coseAlgorithm);

        return new CoseKeyDetails(
                keyType,
                keyTypeName(keyType),
                coseAlgorithm,
                algorithmName,
                curve,
                curveName,
                xCoordinate,
                yCoordinate,
                modulus,
                exponent,
                thumbprint);
    }

    private static Map<Integer, Object> decodeCoseMap(byte[] coseKey) throws GeneralSecurityException {
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
        throw new GeneralSecurityException("Missing integer field " + key + " in COSE key");
    }

    private static byte[] requireBytes(Map<Integer, Object> map, int key) throws GeneralSecurityException {
        Object value = map.get(key);
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        if (value instanceof BigInteger bigInteger) {
            return bigInteger.toByteArray();
        }
        throw new GeneralSecurityException("Missing byte field " + key + " in COSE key");
    }

    private static String keyTypeName(int keyType) {
        return switch (keyType) {
            case 1 -> "OKP";
            case 2 -> "EC2";
            case 3 -> "RSA";
            default -> "UNKNOWN";
        };
    }

    private static String curveName(int curve) {
        return switch (curve) {
            case 1 -> "P-256";
            case 2 -> "P-384";
            case 3 -> "P-521";
            case 6 -> "Ed25519";
            case 7 -> "Ed448";
            case 8 -> "X25519";
            case 9 -> "X448";
            default -> String.valueOf(curve);
        };
    }

    private static String resolveAlgorithmName(WebAuthnSignatureAlgorithm algorithm, int coseAlgorithm) {
        if (algorithm != null) {
            return algorithm.name();
        }
        try {
            return WebAuthnSignatureAlgorithm.fromCoseIdentifier(coseAlgorithm).name();
        } catch (IllegalArgumentException ex) {
            return String.valueOf(coseAlgorithm);
        }
    }

    private static Optional<String> computeThumbprint(
            int keyType,
            Optional<String> curveName,
            Optional<String> xCoordinate,
            Optional<String> yCoordinate,
            Optional<String> modulus,
            Optional<String> exponent)
            throws GeneralSecurityException {
        Map<String, String> fields = new LinkedHashMap<>();
        switch (keyType) {
            case 2 -> {
                if (curveName.isEmpty() || xCoordinate.isEmpty() || yCoordinate.isEmpty()) {
                    return Optional.empty();
                }
                fields.put("crv", curveName.get());
                fields.put("kty", "EC");
                fields.put("x", xCoordinate.get());
                fields.put("y", yCoordinate.get());
            }
            case 3 -> {
                if (modulus.isEmpty() || exponent.isEmpty()) {
                    return Optional.empty();
                }
                fields.put("e", exponent.get());
                fields.put("kty", "RSA");
                fields.put("n", modulus.get());
            }
            case 1 -> {
                if (curveName.isEmpty() || xCoordinate.isEmpty()) {
                    return Optional.empty();
                }
                fields.put("crv", curveName.get());
                fields.put("kty", "OKP");
                fields.put("x", xCoordinate.get());
            }
            default -> {
                return Optional.empty();
            }
        }

        String json = toCanonicalJson(fields);
        byte[] digest = sha256(json.getBytes(StandardCharsets.UTF_8));
        return Optional.of(base64Url(digest));
    }

    private static String toCanonicalJson(Map<String, String> fields) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"')
                    .append(entry.getKey())
                    .append('"')
                    .append(':')
                    .append('"')
                    .append(entry.getValue())
                    .append('"');
        }
        json.append('}');
        return json.toString();
    }

    private static byte[] sha256(byte[] data) throws GeneralSecurityException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (NoSuchAlgorithmException ex) {
            throw new GeneralSecurityException("SHA-256 unavailable", ex);
        }
    }

    private static String base64Url(byte[] bytes) {
        return BASE64_URL.encodeToString(bytes);
    }

    /**
     * Structured COSE key metadata extracted for trace rendering.
     *
     * @param keyType                 Numeric COSE key type (e.g., 2 for EC2)
     * @param keyTypeName             Human-readable key type label (EC2, RSA, OKP)
     * @param coseAlgorithm           Numeric COSE algorithm identifier
     * @param algorithmName           Human-readable algorithm label (ES256, RS256, etc.)
     * @param curve                   Optional curve identifier
     * @param curveName               Optional curve label (P-256, Ed25519, etc.)
     * @param xCoordinateBase64Url    Optional X coordinate (base64url)
     * @param yCoordinateBase64Url    Optional Y coordinate (base64url)
     * @param modulusBase64Url        Optional RSA modulus (base64url)
     * @param exponentBase64Url       Optional RSA public exponent (base64url)
     * @param jwkThumbprintSha256     Optional RFC 7638 JWK thumbprint (base64url)
     */
    public record CoseKeyDetails(
            int keyType,
            String keyTypeName,
            int coseAlgorithm,
            String algorithmName,
            OptionalInt curve,
            Optional<String> curveName,
            Optional<String> xCoordinateBase64Url,
            Optional<String> yCoordinateBase64Url,
            Optional<String> modulusBase64Url,
            Optional<String> exponentBase64Url,
            Optional<String> jwkThumbprintSha256) {
        // marker record
    }
}
