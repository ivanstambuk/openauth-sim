package io.openauth.sim.core.fido2;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Shared access point for WebAuthn fixtures sourced from the W3C Level&nbsp;3 specification.
 * Provides parsed credential descriptors, assertion requests, and optional private-key material for
 * generator presets.
 */
public final class WebAuthnFixtures {

    private static final String FIXTURE_FILE = "webauthn_w3c_vectors.json";
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private static final Map<String, String> SECTION_ID_OVERRIDES = Map.of(
            "16.1.6", "packed-es256",
            "16.1.7", "packed-es384",
            "16.1.8", "packed-es512",
            "16.1.9", "packed-rs256",
            "16.1.10", "packed-ed25519");

    private static final Map<String, WebAuthnFixture> FIXTURES = loadFixtures();

    private static final List<String> PACKED_FIXTURE_IDS =
            SECTION_ID_OVERRIDES.values().stream().filter(FIXTURES::containsKey).toList();

    private WebAuthnFixtures() {
        throw new AssertionError("Utility class");
    }

    public static WebAuthnFixture loadPackedEs256() {
        return loadPackedFixture("packed-es256");
    }

    public static WebAuthnFixture loadPackedFixture(String fixtureId) {
        WebAuthnFixture fixture = FIXTURES.get(normalize(fixtureId));
        if (fixture == null) {
            throw new IllegalArgumentException("Unknown packed fixture: " + fixtureId);
        }
        return fixture;
    }

    public static List<String> availablePackedFixtureIds() {
        return PACKED_FIXTURE_IDS;
    }

    public static List<WebAuthnFixture> w3cFixtures() {
        return List.copyOf(FIXTURES.values());
    }

    private static Map<String, WebAuthnFixture> loadFixtures() {
        Object parsed = WebAuthnJsonVectorFixtures.parseJson(readFixturesFile());
        Map<String, Object> root = asObject(parsed, "root");
        Object vectorsNode = root.get("vectors");
        if (!(vectorsNode instanceof List<?> vectorEntries)) {
            throw new IllegalStateException("Expected 'vectors' array in W3C fixtures");
        }

        Map<String, WebAuthnFixture> fixtures = new LinkedHashMap<>();
        for (Object entry : vectorEntries) {
            Map<String, Object> vector = asObject(entry, "vector");
            String section = requireString(vector, "section");
            String title = requireString(vector, "title");

            try {
                WebAuthnSignatureAlgorithm algorithm = resolveAlgorithm(title);

                Map<String, Object> registration = asObject(vector.get("registration"), section + ".registration");
                Map<String, Object> authentication =
                        asObject(vector.get("authentication"), section + ".authentication");

                AttestedCredentialData attested = parseAttestationData(registration, section, algorithm.name());

                byte[] expectedChallenge = extractBytes(authentication, "challenge", section + ".challenge");
                byte[] clientDataJson = extractBytes(authentication, "clientDataJSON", section + ".clientDataJSON");
                byte[] authenticatorData =
                        extractBytes(authentication, "authenticatorData", section + ".authenticatorData");
                byte[] signature = extractBytes(authentication, "signature", section + ".signature");

                boolean userVerificationRequired = false;

                WebAuthnStoredCredential storedCredential = new WebAuthnStoredCredential(
                        "example.org",
                        attested.credentialId(),
                        attested.publicKeyCose(),
                        attested.signatureCounter(),
                        userVerificationRequired,
                        algorithm);

                WebAuthnAssertionRequest assertionRequest = new WebAuthnAssertionRequest(
                        "example.org",
                        "https://example.org",
                        expectedChallenge,
                        clientDataJson,
                        authenticatorData,
                        signature,
                        "webauthn.get");

                String privateKeyJwk = deriveCredentialPrivateKeyJwk(algorithm, registration, attested.publicKeyCose());

                String id = determineFixtureId(section, title);
                fixtures.put(
                        id,
                        new WebAuthnFixture(id, section, algorithm, storedCredential, assertionRequest, privateKeyJwk));
            } catch (RuntimeException ex) {
                throw new IllegalStateException("Unable to parse W3C vector " + section, ex);
            }
        }
        return Collections.unmodifiableMap(fixtures);
    }

    private static AttestedCredentialData parseAttestationData(
            Map<String, Object> registration, String section, String algorithmLabel) {
        byte[] attestationObject = extractBytes(registration, "attestationObject", section + ".attestationObject");
        Map<String, Object> attestationMap;
        try {
            attestationMap = toStringKeyedMap(CborDecoder.decode(attestationObject), section + ".attestationObject");
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Invalid CBOR in attestation object for " + section, ex);
        }
        Object authDataNode = attestationMap.get("authData");
        if (!(authDataNode instanceof byte[] authData)) {
            throw new IllegalStateException("authData missing from attestation object");
        }

        ByteBuffer buffer = ByteBuffer.wrap(authData);
        byte[] rpIdHash = new byte[32];
        buffer.get(rpIdHash);
        int flags = buffer.get() & 0xFF;
        long counter = buffer.getInt() & 0xFFFFFFFFL;

        boolean attested = (flags & 0x40) != 0;
        if (!attested) {
            throw new IllegalStateException(
                    "Attested credential data absent for section " + section + " (" + algorithmLabel + ")");
        }

        byte[] aaguid = new byte[16];
        buffer.get(aaguid);
        int credentialIdLength = Short.toUnsignedInt(buffer.getShort());
        byte[] credentialId = new byte[credentialIdLength];
        buffer.get(credentialId);
        byte[] publicKeyCose = new byte[buffer.remaining()];
        buffer.get(publicKeyCose);

        return new AttestedCredentialData(credentialId, publicKeyCose, counter);
    }

    private static String deriveCredentialPrivateKeyJwk(
            WebAuthnSignatureAlgorithm algorithm, Map<String, Object> registration, byte[] publicKeyCose) {
        try {
            Map<Integer, Object> cose = decodeCoseMap(publicKeyCose);
            return switch (algorithm) {
                case ES256, ES384, ES512 -> deriveEcJwk(algorithm, registration, cose);
                case EDDSA -> deriveEd25519Jwk(registration, cose);
                case RS256 -> deriveRsaJwk(registration, cose);
                case PS256 -> null; // Spec does not publish a PS256 credential private key.
            };
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to derive private-key JWK", ex);
        }
    }

    private static String deriveEcJwk(
            WebAuthnSignatureAlgorithm algorithm, Map<String, Object> registration, Map<Integer, Object> cose) {
        byte[] privateKey =
                extractOptionalBytes(registration, "credential_private_key").orElse(null);
        if (privateKey == null) {
            return null;
        }
        byte[] x = requireBytes(cose, -2);
        byte[] y = requireBytes(cose, -3);

        String curve =
                switch (algorithm) {
                    case ES256 -> "P-256";
                    case ES384 -> "P-384";
                    case ES512 -> "P-521";
                    default -> throw new IllegalStateException("Unsupported EC algorithm for JWK: " + algorithm);
                };

        Map<String, String> jwk = new LinkedHashMap<>();
        jwk.put("crv", curve);
        jwk.put("d", base64(privateKey));
        jwk.put("kty", "EC");
        jwk.put("x", base64(x));
        jwk.put("y", base64(y));
        return toCanonicalJson(jwk);
    }

    private static String deriveEd25519Jwk(Map<String, Object> registration, Map<Integer, Object> cose) {
        byte[] privateKey = extractOptionalBytes(registration, "private_key").orElse(null);
        if (privateKey == null) {
            privateKey =
                    extractOptionalBytes(registration, "credential_private_key").orElse(null);
        }
        if (privateKey == null) {
            return null;
        }
        byte[] publicKey = requireBytes(cose, -2);
        Map<String, String> jwk = new LinkedHashMap<>();
        jwk.put("crv", "Ed25519");
        jwk.put("d", base64(privateKey));
        jwk.put("kty", "OKP");
        jwk.put("x", base64(publicKey));
        return toCanonicalJson(jwk);
    }

    private static String deriveRsaJwk(Map<String, Object> registration, Map<Integer, Object> cose)
            throws GeneralSecurityException {
        byte[] pBytes = extractOptionalBytes(registration, "private_key_p").orElse(null);
        byte[] qBytes = extractOptionalBytes(registration, "private_key_q").orElse(null);
        if (pBytes == null || qBytes == null) {
            return null;
        }
        BigInteger p = new BigInteger(1, pBytes);
        BigInteger q = new BigInteger(1, qBytes);
        BigInteger n = new BigInteger(1, requireBytes(cose, -1));
        BigInteger e = new BigInteger(1, requireBytes(cose, -2));

        BigInteger lambda = lcm(p.subtract(BigInteger.ONE), q.subtract(BigInteger.ONE));
        BigInteger d = e.modInverse(lambda);
        BigInteger dp = d.remainder(p.subtract(BigInteger.ONE));
        BigInteger dq = d.remainder(q.subtract(BigInteger.ONE));
        BigInteger qi = q.modInverse(p);

        Map<String, String> jwk = new LinkedHashMap<>();
        jwk.put("d", base64Unsigned(d));
        jwk.put("dp", base64Unsigned(dp));
        jwk.put("dq", base64Unsigned(dq));
        jwk.put("e", base64Unsigned(e));
        jwk.put("kty", "RSA");
        jwk.put("n", base64Unsigned(n));
        jwk.put("p", base64Unsigned(p));
        jwk.put("q", base64Unsigned(q));
        jwk.put("qi", base64Unsigned(qi));
        return toCanonicalJson(jwk);
    }

    private static Map<Integer, Object> decodeCoseMap(byte[] coseKey) throws GeneralSecurityException {
        Object decoded = CborDecoder.decode(coseKey);
        if (!(decoded instanceof Map<?, ?> raw)) {
            throw new GeneralSecurityException("COSE key is not a CBOR map");
        }
        Map<Integer, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof Number number)) {
                throw new GeneralSecurityException("COSE key contains non-integer field identifiers");
            }
            result.put(number.intValue(), entry.getValue());
        }
        return result;
    }

    private static Map<String, Object> asObject(Object value, String context) {
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalStateException(context + " is not a JSON object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            Object key = entry.getKey();
            if (!(key instanceof String)) {
                throw new IllegalStateException(context + " contains a non-string key");
            }
            result.put((String) key, entry.getValue());
        }
        return result;
    }

    private static Map<String, Object> toStringKeyedMap(Object value, String context) {
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalStateException(context + " is not a CBOR map");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static byte[] extractBytes(Map<String, Object> parent, String key, String context) {
        Object node = parent.get(key);
        if (node instanceof Map<?, ?> map) {
            Object base64 = map.get("base64Url");
            if (base64 instanceof String str && !str.isBlank()) {
                return decodeBase64(str);
            }
            Object hex = map.get("hex");
            if (hex instanceof String str && !str.isBlank()) {
                return bytesFromHex(str);
            }
        } else if (node instanceof String str && !str.isBlank()) {
            return decodeBase64(str);
        }
        throw new IllegalStateException("Missing value for " + context);
    }

    private static Optional<byte[]> extractOptionalBytes(Map<String, Object> parent, String key) {
        Object node = parent.get(key);
        if (node == null) {
            return Optional.empty();
        }
        if (node instanceof Map<?, ?> map) {
            Object base64 = map.get("base64Url");
            if (base64 instanceof String str && !str.isBlank()) {
                return Optional.of(decodeBase64(str));
            }
            Object hex = map.get("hex");
            if (hex instanceof String str && !str.isBlank()) {
                return Optional.of(bytesFromHex(str));
            }
        }
        return Optional.empty();
    }

    private static byte[] requireBytes(Map<Integer, Object> map, int key) {
        Object value = map.get(key);
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        throw new IllegalStateException("Missing byte[] field for COSE key " + key);
    }

    private static WebAuthnSignatureAlgorithm resolveAlgorithm(String title) {
        String upper = title.toUpperCase(Locale.US);
        if (upper.contains("ES256")) {
            return WebAuthnSignatureAlgorithm.ES256;
        }
        if (upper.contains("ES384")) {
            return WebAuthnSignatureAlgorithm.ES384;
        }
        if (upper.contains("ES512")) {
            return WebAuthnSignatureAlgorithm.ES512;
        }
        if (upper.contains("RS256")) {
            return WebAuthnSignatureAlgorithm.RS256;
        }
        if (upper.contains("PS256")) {
            return WebAuthnSignatureAlgorithm.PS256;
        }
        if (upper.contains("ED25519")) {
            return WebAuthnSignatureAlgorithm.EDDSA;
        }
        throw new IllegalStateException("Unable to infer algorithm from title: " + title);
    }

    private static String determineFixtureId(String section, String title) {
        String normalizedSection = normalize(section);
        String override = SECTION_ID_OVERRIDES.get(section);
        if (override != null) {
            return override;
        }
        return normalizedSection + "-" + slugify(title);
    }

    private static String slugify(String input) {
        String lower = input.toLowerCase(Locale.US);
        StringBuilder builder = new StringBuilder(lower.length());
        boolean lastHyphen = false;
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                builder.append(ch);
                lastHyphen = false;
            } else if (!lastHyphen) {
                builder.append('-');
                lastHyphen = true;
            }
        }
        if (builder.length() == 0) {
            return "fixture";
        }
        if (builder.charAt(builder.length() - 1) == '-') {
            builder.setLength(builder.length() - 1);
        }
        return builder.toString();
    }

    private static String requireString(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (value instanceof String str && !str.isBlank()) {
            return str;
        }
        throw new IllegalStateException("Missing string field '" + key + "'");
    }

    private static byte[] bytesFromHex(String hex) {
        if ((hex.length() & 1) != 0) {
            throw new IllegalArgumentException("Hex value must contain an even number of characters");
        }
        int len = hex.length();
        byte[] result = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int value = Integer.parseInt(hex.substring(i, i + 2), 16);
            result[i / 2] = (byte) value;
        }
        return result;
    }

    private static byte[] decodeBase64(String base64) {
        String input = base64.trim();
        int padding = (4 - (input.length() % 4)) % 4;
        if (padding > 0) {
            input = input + "=".repeat(padding);
        }
        return URL_DECODER.decode(input);
    }

    private static String base64(byte[] value) {
        return URL_ENCODER.encodeToString(value);
    }

    private static String base64Unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return base64(bytes);
    }

    private static String toCanonicalJson(Map<String, String> fields) {
        List<String> keys = new ArrayList<>(fields.keySet());
        Collections.sort(keys);
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        boolean first = true;
        if (keys.remove("kty")) {
            first = appendStringEntry(builder, first, "kty", fields.get("kty"));
        }
        for (String key : keys) {
            first = appendStringEntry(builder, first, key, fields.get(key));
        }
        builder.append('}');
        return builder.toString();
    }

    private static boolean appendStringEntry(StringBuilder builder, boolean first, String key, String value) {
        if (!first) {
            builder.append(',');
        }
        builder.append('"')
                .append(escapeJson(key))
                .append('"')
                .append(':')
                .append('"')
                .append(escapeJson(value))
                .append('"');
        return false;
    }

    private static String escapeJson(String input) {
        StringBuilder builder = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
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
        return builder.toString();
    }

    private static BigInteger lcm(BigInteger a, BigInteger b) {
        return a.multiply(b).divide(a.gcd(b));
    }

    private static String readFixturesFile() {
        Path path = resolveFixturesPath();
        try {
            return Files.readString(path);
        } catch (IOException ioe) {
            throw new IllegalStateException("Unable to read W3C WebAuthn fixture bundle", ioe);
        }
    }

    private static Path resolveFixturesPath() {
        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path direct = workingDirectory.resolve("docs").resolve(FIXTURE_FILE);
        if (Files.exists(direct)) {
            return direct;
        }
        Path parent = workingDirectory.getParent();
        if (parent != null) {
            Path parentCandidate = parent.resolve("docs").resolve(FIXTURE_FILE);
            if (Files.exists(parentCandidate)) {
                return parentCandidate;
            }
        }
        return direct;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Identifier must not be blank");
        }
        return value.trim().toLowerCase(Locale.US);
    }

    private static final class AttestedCredentialData {
        private final byte[] credentialId;
        private final byte[] publicKeyCose;
        private final long signatureCounter;

        private AttestedCredentialData(byte[] credentialId, byte[] publicKeyCose, long signatureCounter) {
            this.credentialId = credentialId;
            this.publicKeyCose = publicKeyCose;
            this.signatureCounter = signatureCounter;
        }

        byte[] credentialId() {
            return credentialId;
        }

        byte[] publicKeyCose() {
            return publicKeyCose;
        }

        long signatureCounter() {
            return signatureCounter;
        }
    }

    public static final class WebAuthnFixture {
        private final String id;
        private final String section;
        private final WebAuthnSignatureAlgorithm algorithm;
        private final WebAuthnStoredCredential storedCredential;
        private final WebAuthnAssertionRequest request;
        private final String credentialPrivateKeyJwk;

        WebAuthnFixture(
                String id,
                String section,
                WebAuthnSignatureAlgorithm algorithm,
                WebAuthnStoredCredential storedCredential,
                WebAuthnAssertionRequest request,
                String credentialPrivateKeyJwk) {
            this.id = Objects.requireNonNull(id, "id");
            this.section = Objects.requireNonNull(section, "section");
            this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
            this.storedCredential = Objects.requireNonNull(storedCredential, "storedCredential");
            this.request = Objects.requireNonNull(request, "request");
            this.credentialPrivateKeyJwk = credentialPrivateKeyJwk;
        }

        public String id() {
            return id;
        }

        public String section() {
            return section;
        }

        public WebAuthnSignatureAlgorithm algorithm() {
            return algorithm;
        }

        public WebAuthnStoredCredential storedCredential() {
            return storedCredential;
        }

        public WebAuthnAssertionRequest request() {
            return request;
        }

        public String credentialPrivateKeyJwk() {
            return credentialPrivateKeyJwk;
        }

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
            Objects.requireNonNull(newSignature, "newSignature");
            return new WebAuthnAssertionRequest(
                    request.relyingPartyId(),
                    request.origin(),
                    request.expectedChallenge(),
                    request.clientDataJson(),
                    request.authenticatorData(),
                    newSignature.clone(),
                    request.expectedType());
        }
    }
}
