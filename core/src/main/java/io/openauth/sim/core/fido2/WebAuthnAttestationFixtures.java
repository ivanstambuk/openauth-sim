package io.openauth.sim.core.fido2;

import io.openauth.sim.core.json.SimpleJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECPoint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Loader for the attestation fixtures stored under {@code docs/webauthn_attestation/}. */
public final class WebAuthnAttestationFixtures {

    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Map<WebAuthnAttestationFormat, List<WebAuthnAttestationVector>> FIXTURES;
    private static final Map<String, WebAuthnAttestationVector> FIXTURE_INDEX;

    static {
        Map<WebAuthnAttestationFormat, List<WebAuthnAttestationVector>> fixtures = loadAllFixtures();
        FIXTURES = fixtures;
        FIXTURE_INDEX = indexById(fixtures);
    }

    private WebAuthnAttestationFixtures() {
        throw new AssertionError("Utility class");
    }

    /** Returns a stream of every attestation vector across all formats. */
    public static Stream<WebAuthnAttestationVector> allVectors() {
        return FIXTURES.values().stream().flatMap(List::stream);
    }

    /** Returns the attestation vectors registered for the supplied format. */
    public static List<WebAuthnAttestationVector> vectorsFor(WebAuthnAttestationFormat format) {
        return FIXTURES.getOrDefault(format, List.of());
    }

    /** Looks up an attestation vector by its identifier. */
    public static Optional<WebAuthnAttestationVector> findById(String vectorId) {
        if (vectorId == null || vectorId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(FIXTURE_INDEX.get(vectorId.trim()));
    }

    private static Map<WebAuthnAttestationFormat, List<WebAuthnAttestationVector>> loadAllFixtures() {
        Map<WebAuthnAttestationFormat, List<WebAuthnAttestationVector>> byFormat =
                new EnumMap<>(WebAuthnAttestationFormat.class);
        for (WebAuthnAttestationFormat format : WebAuthnAttestationFormat.values()) {
            Path path = resolveFixturePath(format.label());
            if (!Files.exists(path)) {
                byFormat.put(format, List.of());
                continue;
            }
            byFormat.put(format, Collections.unmodifiableList(parseFixtureFile(path, format)));
        }
        return Collections.unmodifiableMap(byFormat);
    }

    private static Map<String, WebAuthnAttestationVector> indexById(
            Map<WebAuthnAttestationFormat, List<WebAuthnAttestationVector>> fixtures) {
        Map<String, WebAuthnAttestationVector> index = new LinkedHashMap<>();
        for (List<WebAuthnAttestationVector> vectors : fixtures.values()) {
            for (WebAuthnAttestationVector vector : vectors) {
                String vectorId = Objects.requireNonNull(vector.vectorId(), "vectorId");
                WebAuthnAttestationVector existing = index.putIfAbsent(vectorId, vector);
                if (existing != null) {
                    throw new IllegalStateException("Duplicate attestation vector id detected: " + vectorId);
                }
            }
        }
        return Collections.unmodifiableMap(index);
    }

    private static List<WebAuthnAttestationVector> parseFixtureFile(Path path, WebAuthnAttestationFormat format) {
        Object parsed = parseJson(readFile(path));
        if (!(parsed instanceof List<?> entries)) {
            throw new IllegalStateException("Expected top-level array in " + path);
        }

        List<WebAuthnAttestationVector> vectors = new ArrayList<>();
        for (Object entry : entries) {
            Map<String, Object> root = asObject(entry, path + " entry");

            String vectorId = requireString(root, "vector_id");
            String section = requireString(root, "w3c_section");
            String title = requireString(root, "title");
            String rpId = requireString(root, "rpId");
            String origin = requireString(root, "origin");

            WebAuthnSignatureAlgorithm algorithm = resolveAlgorithm(requireString(root, "algorithm"));

            Map<String, Object> registration = asObject(root.get("registration"), vectorId + ".registration");
            Map<String, Object> authenticationRaw = root.containsKey("authentication")
                    ? asObject(root.get("authentication"), vectorId + ".authentication")
                    : Map.of();
            Map<String, Object> keyMaterial = asObject(root.get("key_material"), vectorId + ".key_material");

            Registration registrationPayload = new Registration(
                    decodeBytes(registration.get("challenge_b64u"), vectorId + ".registration.challenge"),
                    decodeBytes(registration.get("clientDataJSON_b64u"), vectorId + ".registration.clientDataJSON"),
                    decodeBytes(
                            registration.get("attestationObject_b64u"), vectorId + ".registration.attestationObject"),
                    decodeBytes(registration.get("credentialId_b64u"), vectorId + ".registration.credentialId"),
                    decodeBytes(registration.get("aaguid_b64u"), vectorId + ".registration.aaguid"));

            Optional<Authentication> authentication = authenticationRaw.isEmpty()
                    ? Optional.empty()
                    : Optional.of(new Authentication(
                            decodeBytes(
                                    authenticationRaw.get("challenge_b64u"), vectorId + ".authentication.challenge"),
                            decodeBytes(
                                    authenticationRaw.get("clientDataJSON_b64u"),
                                    vectorId + ".authentication.clientDataJSON"),
                            decodeBytes(
                                    authenticationRaw.get("authenticatorData_b64u"),
                                    vectorId + ".authentication.authenticatorData"),
                            decodeBytes(
                                    authenticationRaw.get("signature_b64u"), vectorId + ".authentication.signature")));

            String credentialPrivateKeyBase64 = decodeString(
                    keyMaterial.get("credential_private_key_b64u"),
                    vectorId + ".key_material.credential_private_key_b64u");
            String attestationPrivateKeyBase64 = decodeString(
                    keyMaterial.get("attestation_private_key_b64u"),
                    vectorId + ".key_material.attestation_private_key_b64u");
            String attestationSerialBase64 = decodeString(
                    keyMaterial.get("attestation_cert_serial_b64u"),
                    vectorId + ".key_material.attestation_cert_serial_b64u");

            KeyMaterial material = buildKeyMaterial(
                    vectorId,
                    format,
                    algorithm,
                    registrationPayload,
                    rpId,
                    origin,
                    credentialPrivateKeyBase64,
                    attestationPrivateKeyBase64,
                    attestationSerialBase64);

            vectors.add(new WebAuthnAttestationVector(
                    vectorId,
                    format,
                    algorithm,
                    section,
                    title,
                    rpId,
                    origin,
                    registrationPayload,
                    authentication,
                    material));
        }

        return vectors;
    }

    private static KeyMaterial buildKeyMaterial(
            String vectorId,
            WebAuthnAttestationFormat format,
            WebAuthnSignatureAlgorithm algorithm,
            Registration registration,
            String relyingPartyId,
            String origin,
            String credentialPrivateKeyBase64,
            String attestationPrivateKeyBase64,
            String attestationSerialBase64)
            throws IllegalStateException {
        try {
            WebAuthnAttestationVerifier verifier = new WebAuthnAttestationVerifier();
            WebAuthnAttestationRequest request = new WebAuthnAttestationRequest(
                    format,
                    registration.attestationObject(),
                    registration.clientDataJson(),
                    registration.challenge(),
                    relyingPartyId,
                    origin);
            WebAuthnAttestationVerification verification = verifier.verify(request);

            WebAuthnStoredCredential storedCredential = verification
                    .attestedCredential()
                    .orElseThrow(() ->
                            new IllegalStateException("Missing stored credential for attestation vector " + vectorId));

            PublicKey credentialPublicKey =
                    WebAuthnPublicKeyFactory.fromCose(storedCredential.publicKeyCose(), algorithm);

            JwkMaterial credentialMaterial =
                    deriveJwkMaterial(credentialPrivateKeyBase64, algorithm, credentialPublicKey);

            PublicKey attestationPublicKey = verification.certificateChain().isEmpty()
                    ? null
                    : verification.certificateChain().get(0).getPublicKey();

            WebAuthnSignatureAlgorithm attestationAlgorithm =
                    attestationPublicKey == null ? algorithm : inferAlgorithmFromPublicKey(attestationPublicKey);

            JwkMaterial attestationMaterial =
                    deriveJwkMaterial(attestationPrivateKeyBase64, attestationAlgorithm, attestationPublicKey);

            String credentialCanonical = credentialMaterial.canonicalBase64() != null
                    ? credentialMaterial.canonicalBase64()
                    : normalizeBase64(credentialPrivateKeyBase64);
            String attestationCanonical = attestationMaterial.canonicalBase64() != null
                    ? attestationMaterial.canonicalBase64()
                    : normalizeBase64(attestationPrivateKeyBase64);

            WebAuthnSignatureAlgorithm attestationKeyAlgorithm = attestationPublicKey == null
                    ? inferAlgorithmFromPrivateKeyBytes(attestationCanonical)
                    : attestationAlgorithm;

            return new KeyMaterial(
                    credentialCanonical,
                    credentialMaterial.prettyJwk(),
                    attestationCanonical,
                    attestationMaterial.prettyJwk(),
                    normalizeBase64(attestationSerialBase64),
                    attestationKeyAlgorithm);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to derive JWK material for attestation vector " + vectorId, ex);
        }
    }

    private static JwkMaterial deriveJwkMaterial(
            String base64Url, WebAuthnSignatureAlgorithm algorithm, PublicKey publicKey)
            throws GeneralSecurityException {
        if (base64Url == null || base64Url.isBlank()) {
            return new JwkMaterial(null, null);
        }
        String normalized = normalizeBase64(base64Url);
        byte[] decoded = decodeFixtureBytes(normalized);
        if (publicKey == null) {
            return deriveJwkWithoutPublicKey(decoded, algorithm);
        }

        return switch (algorithm) {
            case ES256, ES384, ES512 -> deriveEcJwk(decoded, algorithm, (ECPublicKey) publicKey);
            case RS256, PS256 -> deriveRsaJwk(decoded, (RSAPublicKey) publicKey);
            case EDDSA -> deriveEdJwk(decoded, (EdECPublicKey) publicKey);
        };
    }

    private static WebAuthnSignatureAlgorithm inferAlgorithmFromPublicKey(PublicKey publicKey)
            throws GeneralSecurityException {
        if (publicKey instanceof ECPublicKey ecPublicKey) {
            int fieldSize = ecPublicKey.getParams().getCurve().getField().getFieldSize();
            return switch (fieldSize) {
                case 256 -> WebAuthnSignatureAlgorithm.ES256;
                case 384 -> WebAuthnSignatureAlgorithm.ES384;
                case 521 -> WebAuthnSignatureAlgorithm.ES512;
                default -> throw new GeneralSecurityException("Unsupported EC field size: " + fieldSize);
            };
        }
        if (publicKey instanceof RSAPublicKey) {
            return WebAuthnSignatureAlgorithm.RS256;
        }
        if (publicKey instanceof EdECPublicKey) {
            return WebAuthnSignatureAlgorithm.EDDSA;
        }
        throw new GeneralSecurityException("Unsupported public key type: " + publicKey.getAlgorithm());
    }

    private static WebAuthnSignatureAlgorithm inferAlgorithmFromPrivateKeyBytes(String canonicalBase64)
            throws GeneralSecurityException {
        if (canonicalBase64 == null || canonicalBase64.isBlank()) {
            return WebAuthnSignatureAlgorithm.ES256;
        }
        byte[] decoded = decodeFixtureBytes(canonicalBase64);
        int length = decoded.length;
        return switch (length) {
            case 32 -> WebAuthnSignatureAlgorithm.ES256;
            case 48 -> WebAuthnSignatureAlgorithm.ES384;
            case 65, 66 -> WebAuthnSignatureAlgorithm.ES512;
            default -> WebAuthnSignatureAlgorithm.ES256;
        };
    }

    private static JwkMaterial deriveEcJwk(
            byte[] privateKeyBytes, WebAuthnSignatureAlgorithm algorithm, ECPublicKey publicKey) {
        int scalarLength = ecScalarLength(algorithm);
        byte[] scalar = padToLength(privateKeyBytes, scalarLength);
        String canonical = URL_ENCODER.encodeToString(scalar);

        String x = URL_ENCODER.encodeToString(toUnsigned(publicKey.getW().getAffineX(), scalarLength));
        String y = URL_ENCODER.encodeToString(toUnsigned(publicKey.getW().getAffineY(), scalarLength));

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("kty", "EC");
        fields.put("crv", ecCurveLabel(algorithm));
        fields.put("x", x);
        fields.put("y", y);
        fields.put("d", canonical);
        String jwk = prettyPrintJwk(fields);
        return new JwkMaterial(canonical, jwk);
    }

    private static JwkMaterial deriveRsaJwk(byte[] privateKeyBytes, RSAPublicKey publicKey) {
        int modulusLength = unsignedLength(publicKey.getModulus());
        byte[] d = padToLength(privateKeyBytes, modulusLength);
        String canonical = URL_ENCODER.encodeToString(d);
        String modulus = URL_ENCODER.encodeToString(toUnsigned(publicKey.getModulus(), modulusLength));
        String exponent = URL_ENCODER.encodeToString(
                toUnsigned(publicKey.getPublicExponent(), unsignedLength(publicKey.getPublicExponent())));

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("kty", "RSA");
        fields.put("n", modulus);
        fields.put("e", exponent);
        fields.put("d", canonical);
        String jwk = prettyPrintJwk(fields);
        return new JwkMaterial(canonical, jwk);
    }

    private static JwkMaterial deriveEdJwk(byte[] privateKeyBytes, EdECPublicKey publicKey)
            throws GeneralSecurityException {
        byte[] scalar = padToLength(privateKeyBytes, 32);
        String canonical = URL_ENCODER.encodeToString(scalar);
        byte[] encoded = publicKey.getEncoded();
        if (encoded == null || encoded.length < 32) {
            throw new GeneralSecurityException("Unable to extract Ed25519 public key bytes");
        }
        byte[] publicComponent = Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("kty", "OKP");
        fields.put("crv", "Ed25519");
        fields.put("x", URL_ENCODER.encodeToString(publicComponent));
        fields.put("d", canonical);
        String jwk = prettyPrintJwk(fields);
        return new JwkMaterial(canonical, jwk);
    }

    private static JwkMaterial deriveJwkWithoutPublicKey(byte[] privateKeyBytes, WebAuthnSignatureAlgorithm algorithm)
            throws GeneralSecurityException {
        return switch (algorithm) {
            case ES256, ES384, ES512 -> deriveEcJwkWithoutPublic(privateKeyBytes, algorithm);
            case RS256, PS256 ->
                // Unable to derive RSA public parameters without modulus/exponent; return canonical only.
                new JwkMaterial(URL_ENCODER.encodeToString(privateKeyBytes), null);
            case EDDSA ->
                // Deriving Ed25519 public keys requires specialized math; rely on certificate data.
                new JwkMaterial(URL_ENCODER.encodeToString(privateKeyBytes), null);
        };
    }

    private static JwkMaterial deriveEcJwkWithoutPublic(byte[] privateKeyBytes, WebAuthnSignatureAlgorithm algorithm)
            throws GeneralSecurityException {
        int scalarLength = ecScalarLength(algorithm);
        byte[] scalar = padToLength(privateKeyBytes, scalarLength);
        String canonical = URL_ENCODER.encodeToString(scalar);

        java.security.spec.ECParameterSpec params = paramsForAlgorithm(algorithm);
        java.math.BigInteger s = new java.math.BigInteger(1, scalar);
        ECPoint point = scalarMultiply(params.getGenerator(), s, params);

        String x = URL_ENCODER.encodeToString(toUnsigned(point.getAffineX(), coordinateLength(algorithm)));
        String y = URL_ENCODER.encodeToString(toUnsigned(point.getAffineY(), coordinateLength(algorithm)));

        String jwk = prettyPrintJwk(Map.of(
                "kty", "EC",
                "crv", ecCurveLabel(algorithm),
                "x", x,
                "y", y,
                "d", canonical));
        return new JwkMaterial(canonical, jwk);
    }

    private static ECPoint scalarMultiply(
            ECPoint point, java.math.BigInteger scalar, java.security.spec.ECParameterSpec params) {
        ECPoint result = ECPoint.POINT_INFINITY;
        ECPoint addend = point;
        java.math.BigInteger k = scalar;
        while (k.signum() > 0) {
            if (k.testBit(0)) {
                result = addPoints(result, addend, params);
            }
            addend = addPoints(addend, addend, params);
            k = k.shiftRight(1);
        }
        return result;
    }

    private static ECPoint addPoints(ECPoint p, ECPoint q, java.security.spec.ECParameterSpec params) {
        if (p.equals(ECPoint.POINT_INFINITY)) {
            return q;
        }
        if (q.equals(ECPoint.POINT_INFINITY)) {
            return p;
        }

        var curve = params.getCurve();
        var field = curve.getField();
        if (!(field instanceof java.security.spec.ECFieldFp primeField)) {
            throw new IllegalArgumentException("Only prime field curves are supported");
        }
        java.math.BigInteger modulus = primeField.getP();
        java.math.BigInteger a = curve.getA();

        java.math.BigInteger px = p.getAffineX();
        java.math.BigInteger py = p.getAffineY();
        java.math.BigInteger qx = q.getAffineX();
        java.math.BigInteger qy = q.getAffineY();

        if (px.equals(qx) && py.add(qy).mod(modulus).equals(java.math.BigInteger.ZERO)) {
            return ECPoint.POINT_INFINITY;
        }

        java.math.BigInteger lambda;
        if (px.equals(qx) && py.equals(qy)) {
            java.math.BigInteger numerator =
                    px.pow(2).multiply(java.math.BigInteger.valueOf(3)).add(a).mod(modulus);
            java.math.BigInteger denominator = py.shiftLeft(1).modInverse(modulus);
            lambda = numerator.multiply(denominator).mod(modulus);
        } else {
            java.math.BigInteger numerator = qy.subtract(py).mod(modulus);
            java.math.BigInteger denominator = qx.subtract(px).mod(modulus).modInverse(modulus);
            lambda = numerator.multiply(denominator).mod(modulus);
        }

        java.math.BigInteger rx = lambda.pow(2).subtract(px).subtract(qx).mod(modulus);
        java.math.BigInteger ry = lambda.multiply(px.subtract(rx)).subtract(py).mod(modulus);
        return new ECPoint(rx, ry);
    }

    private static byte[] decodeFixtureBytes(String base64Url) throws GeneralSecurityException {
        try {
            return URL_DECODER.decode(base64Url);
        } catch (IllegalArgumentException ex) {
            throw new GeneralSecurityException("Fixture private key must be Base64URL encoded", ex);
        }
    }

    private static String ecCurveLabel(WebAuthnSignatureAlgorithm algorithm) {
        return switch (algorithm) {
            case ES256 -> "P-256";
            case ES384 -> "P-384";
            case ES512 -> "P-521";
            default -> throw new IllegalArgumentException("Unsupported EC algorithm " + algorithm);
        };
    }

    private static String ecCurveSpec(WebAuthnSignatureAlgorithm algorithm) {
        return switch (algorithm) {
            case ES256 -> "secp256r1";
            case ES384 -> "secp384r1";
            case ES512 -> "secp521r1";
            default -> throw new IllegalArgumentException("Unsupported EC algorithm " + algorithm);
        };
    }

    private static int ecScalarLength(WebAuthnSignatureAlgorithm algorithm) {
        return switch (algorithm) {
            case ES256 -> 32;
            case ES384 -> 48;
            case ES512 -> 66;
            default -> throw new IllegalArgumentException("Unsupported EC algorithm " + algorithm);
        };
    }

    private static int coordinateLength(WebAuthnSignatureAlgorithm algorithm) {
        return ecScalarLength(algorithm);
    }

    private static byte[] toUnsigned(java.math.BigInteger value, int length) {
        byte[] bytes = value.toByteArray();
        if (bytes.length == length) {
            return bytes;
        }
        if (bytes.length == length + 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[length];
            System.arraycopy(bytes, 1, trimmed, 0, length);
            return trimmed;
        }
        return padToLength(bytes, length);
    }

    private static byte[] padToLength(byte[] input, int length) {
        byte[] output = new byte[length];
        int copy = Math.min(input.length, length);
        System.arraycopy(input, input.length - copy, output, length - copy, copy);
        return output;
    }

    private static java.security.spec.ECParameterSpec paramsForAlgorithm(WebAuthnSignatureAlgorithm algorithm)
            throws GeneralSecurityException {
        java.security.AlgorithmParameters parameters = java.security.AlgorithmParameters.getInstance("EC");
        parameters.init(new java.security.spec.ECGenParameterSpec(ecCurveSpec(algorithm)));
        return parameters.getParameterSpec(java.security.spec.ECParameterSpec.class);
    }

    private static int unsignedLength(java.math.BigInteger value) {
        return (value.bitLength() + 7) / 8;
    }

    private static String prettyPrintJwk(Map<String, String> fields) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        var iterator = fields.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            builder.append("  \"")
                    .append(entry.getKey())
                    .append("\": \"")
                    .append(entry.getValue())
                    .append('"');
            if (iterator.hasNext()) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append('}');
        return builder.toString();
    }

    private static String normalizeBase64(String value) {
        return value == null ? null : value.replaceAll("\\s+", "");
    }

    private record JwkMaterial(String canonicalBase64, String prettyJwk) {
        // Value holder for derived JWK representations.
    }

    private static Object parseJson(String content) {
        return SimpleJson.parse(content);
    }

    private static Map<String, Object> asObject(Object value, String context) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                if (!(key instanceof String)) {
                    throw new IllegalStateException(context + " contains non-string key");
                }
                result.put((String) key, entry.getValue());
            }
            return result;
        }
        throw new IllegalStateException(context + " must be a JSON object");
    }

    private static String requireString(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (value instanceof String str && !str.isBlank()) {
            return str;
        }
        throw new IllegalStateException("Missing string field '" + key + "' in " + objectDescription(object));
    }

    private static String decodeString(Object node, String context) {
        if (node == null) {
            return null;
        }
        if (node instanceof String str) {
            return str;
        }
        throw new IllegalStateException(context + " must be a string");
    }

    private static byte[] decodeBytes(Object node, String context) {
        if (node == null) {
            return new byte[0];
        }
        if (node instanceof String str) {
            return URL_DECODER.decode(str);
        }
        throw new IllegalStateException(context + " must be a base64url string");
    }

    private static String objectDescription(Map<String, Object> object) {
        return object.entrySet().stream()
                .map(e -> e.getKey() + "=" + Objects.toString(e.getValue()))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    private static String readFile(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read fixture file " + path, ex);
        }
    }

    private static Path resolveFixturePath(String formatLabel) {
        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path direct = workingDirectory.resolve("docs/webauthn_attestation/" + formatLabel + ".json");
        if (Files.exists(direct)) {
            return direct;
        }
        Path parent = workingDirectory.getParent();
        if (parent != null) {
            Path candidate = parent.resolve("docs/webauthn_attestation/" + formatLabel + ".json");
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return direct;
    }

    private static WebAuthnSignatureAlgorithm resolveAlgorithm(String label) {
        if ("Ed25519".equalsIgnoreCase(label)) {
            return WebAuthnSignatureAlgorithm.EDDSA;
        }
        return WebAuthnSignatureAlgorithm.fromLabel(label);
    }

    /** Container for attestation registration payload fields. */
    public record Registration(
            byte[] challenge, byte[] clientDataJson, byte[] attestationObject, byte[] credentialId, byte[] aaguid) {
        // Marker record used for transporting attestation registration payloads.
    }

    /** Optional authentication payload associated with an attestation vector. */
    public record Authentication(byte[] challenge, byte[] clientDataJson, byte[] authenticatorData, byte[] signature) {
        // Value carrier for authentication payloads associated with an attestation vector.
    }

    /** Key material required for generating deterministic attestation objects. */
    public record KeyMaterial(
            String credentialPrivateKeyBase64Url,
            String credentialPrivateKeyJwk,
            String attestationPrivateKeyBase64Url,
            String attestationPrivateKeyJwk,
            String attestationCertificateSerialBase64Url,
            WebAuthnSignatureAlgorithm attestationPrivateKeyAlgorithm) {
        // Holds deterministic key material for generator flows.
    }

    /** Complete attestation vector record. */
    public record WebAuthnAttestationVector(
            String vectorId,
            WebAuthnAttestationFormat format,
            WebAuthnSignatureAlgorithm algorithm,
            String w3cSection,
            String title,
            String relyingPartyId,
            String origin,
            Registration registration,
            Optional<Authentication> authentication,
            KeyMaterial keyMaterial) {
        // Aggregate attestation vector definition.
    }
}
