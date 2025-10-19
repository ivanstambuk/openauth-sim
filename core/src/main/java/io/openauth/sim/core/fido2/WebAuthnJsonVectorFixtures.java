package io.openauth.sim.core.fido2;

import io.openauth.sim.core.json.SimpleJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Access point for the synthetic WebAuthn assertion vectors stored in {@code
 * docs/webauthn_assertion_vectors.json}.
 */
public final class WebAuthnJsonVectorFixtures {

    private WebAuthnJsonVectorFixtures() {
        throw new AssertionError("Utility class");
    }

    /** Load all WebAuthn assertion vectors from the JSON bundle. */
    public static Stream<WebAuthnJsonVector> loadAll() {
        Object decoded = SimpleJson.parse(readBundle());
        if (!(decoded instanceof List<?> entries)) {
            throw new IllegalStateException("Expected top-level JSON array for WebAuthn vectors");
        }

        List<WebAuthnJsonVector> vectors = new ArrayList<>();
        for (Object entry : entries) {
            Map<String, Object> root = asObject(entry, "vector");
            vectors.add(toVector(root));
        }
        return vectors.stream();
    }

    static Object parseJson(String input) {
        return SimpleJson.parse(input);
    }

    private static WebAuthnJsonVector toVector(Map<String, Object> root) {
        String vectorId = requireString(root, "vector_id");

        Map<String, Object> request = asObject(root.get("request"), vectorId + ".request");
        Map<String, Object> response = asObject(root.get("response"), vectorId + ".response");
        Map<String, Object> responsePayload = asObject(response.get("response"), vectorId + ".response.payload");
        Map<String, Object> keyMaterial = asObject(root.get("key_material"), vectorId + ".key_material");
        Map<String, Object> computed = asObject(root.get("computed"), vectorId + ".computed");

        byte[] expectedChallenge = decodeBase64Value(request.get("challenge"), vectorId + ".request.challenge");
        byte[] authenticatorData =
                decodeBase64Value(responsePayload.get("authenticatorData"), vectorId + ".authenticatorData");
        byte[] clientDataJson = decodeBase64Value(responsePayload.get("clientDataJSON"), vectorId + ".clientDataJSON");
        byte[] signature = decodeBase64Value(responsePayload.get("signature"), vectorId + ".signature");
        byte[] credentialId = decodeBase64Value(response.get("rawId"), vectorId + ".response.rawId");
        byte[] publicKeyCose =
                decodeBase64Value(keyMaterial.get("publicKeyCose_b64u"), vectorId + ".key_material.publicKeyCose_b64u");

        Map<String, Object> clientData = asObject(
                SimpleJson.parse(new String(clientDataJson, StandardCharsets.UTF_8)), vectorId + ".clientDataJSON");

        String expectedType = requireString(clientData, "type");
        String origin = requireString(clientData, "origin");
        String challengeFromClient = requireString(clientData, "challenge");
        byte[] clientDataChallenge = decodeBase64Url(challengeFromClient, vectorId + ".clientDataJSON.challenge");
        if (!Arrays.equals(expectedChallenge, clientDataChallenge)) {
            throw new IllegalStateException("Challenge mismatch for vector " + vectorId);
        }

        String relyingPartyId = requireString(computed, "rpId");
        long signatureCounter = requireLong(computed, "signCount");
        boolean userVerificationRequired = "required".equalsIgnoreCase(String.valueOf(request.get("userVerification")));

        WebAuthnSignatureAlgorithm algorithm = resolveAlgorithm(keyMaterial.get("algorithm"));
        String privateKeyJwk = parsePrivateKeyJwk(keyMaterial.get("keyPairJwk"));

        WebAuthnStoredCredential storedCredential = new WebAuthnStoredCredential(
                relyingPartyId, credentialId, publicKeyCose, signatureCounter, userVerificationRequired, algorithm);

        WebAuthnAssertionRequest assertionRequest = new WebAuthnAssertionRequest(
                relyingPartyId, origin, expectedChallenge, clientDataJson, authenticatorData, signature, expectedType);

        return new WebAuthnJsonVector(vectorId, algorithm, storedCredential, assertionRequest, privateKeyJwk);
    }

    private static WebAuthnSignatureAlgorithm resolveAlgorithm(Object value) {
        String label = Objects.toString(value, "");
        if (label.equalsIgnoreCase("Ed25519")) {
            return WebAuthnSignatureAlgorithm.EDDSA;
        }
        return WebAuthnSignatureAlgorithm.fromLabel(label);
    }

    private static String readBundle() {
        Path path = resolveBundlePath();
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ioe) {
            throw new IllegalStateException("Unable to read WebAuthn JSON vector bundle", ioe);
        }
    }

    private static Path resolveBundlePath() {
        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path direct = workingDirectory.resolve("docs/webauthn_assertion_vectors.json");
        if (Files.exists(direct)) {
            return direct;
        }
        Path parent = workingDirectory.getParent();
        if (parent != null) {
            Path candidate = parent.resolve("docs/webauthn_assertion_vectors.json");
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return direct;
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
        if (value instanceof String str) {
            return str;
        }
        throw new IllegalStateException(objectDescription(object, key) + " must be a string");
    }

    private static long requireLong(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str && !str.isBlank()) {
            try {
                return Long.parseLong(str.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalStateException(objectDescription(object, key) + " must be a number", ex);
            }
        }
        throw new IllegalStateException(objectDescription(object, key) + " must be a number");
    }

    private static byte[] decodeBase64Value(Object value, String context) {
        if (value == null) {
            return new byte[0];
        }
        if (value instanceof String str) {
            return decodeBase64Url(str, context);
        }
        throw new IllegalStateException(
                context + " must be a base64url string (see .gitleaks allowlist documentation)");
    }

    private static byte[] decodeBase64Url(String encoded, String context) {
        try {
            String normalized =
                    encoded.replace("\n", "").replace("\r", "").replace(" ", "").trim();
            int padding = (4 - (normalized.length() % 4)) % 4;
            normalized = normalized + "====".substring(0, padding);
            return Base64.getUrlDecoder().decode(normalized);
        } catch (IllegalArgumentException iae) {
            throw new IllegalStateException(context + " contains invalid base64url data", iae);
        }
    }

    private static String parsePrivateKeyJwk(Object value) {
        if (value == null) {
            return null;
        }
        Map<String, Object> jwkObject = asObject(value, "key_material.keyPairJwk");
        return toCanonicalJson(jwkObject);
    }

    private static String toCanonicalJson(Map<String, Object> object) {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        List<String> keys = new ArrayList<>(object.keySet());
        Collections.sort(keys);
        boolean first = true;
        if (keys.remove("kty")) {
            first = appendJsonEntry(builder, first, "kty", object.get("kty"));
        }
        for (String key : keys) {
            first = appendJsonEntry(builder, first, key, object.get(key));
        }
        builder.append('}');
        return builder.toString();
    }

    private static boolean appendJsonEntry(StringBuilder builder, boolean first, String key, Object fieldValue) {
        if (!first) {
            builder.append(',');
        }
        builder.append('"').append(escapeJson(key)).append('"').append(':');
        if (fieldValue == null) {
            builder.append("null");
        } else if (fieldValue instanceof String str) {
            builder.append('"').append(escapeJson(str)).append('"');
        } else if (fieldValue instanceof Map<?, ?> nested) {
            @SuppressWarnings("unchecked")
            Map<String, Object> nestedMap = (Map<String, Object>) nested;
            builder.append(toCanonicalJson(nestedMap));
        } else {
            builder.append('"').append(escapeJson(String.valueOf(fieldValue))).append('"');
        }
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

    private static String objectDescription(Map<String, Object> object, String key) {
        return "Field '" + key + "' in object " + object.keySet();
    }

    public record WebAuthnJsonVector(
            String vectorId,
            WebAuthnSignatureAlgorithm algorithm,
            WebAuthnStoredCredential storedCredential,
            WebAuthnAssertionRequest assertionRequest,
            String privateKeyJwk) {
        // Marker type for parsed JSON vectors.
    }
}
