package io.openauth.sim.core.eudi.openid4vp;

import io.openauth.sim.core.json.SimpleJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Loader for synthetic issuer/holder JWKs used by simulator fixtures. */
public final class SyntheticKeyFixtures {

    private static final List<Path> KEY_SEARCH_PATHS = List.of(
            Path.of("docs", "test-vectors", "eudiw", "openid4vp", "keys"),
            Path.of("..", "docs", "test-vectors", "eudiw", "openid4vp", "keys"));

    private SyntheticKeyFixtures() {
        throw new AssertionError("Utility class");
    }

    public static Jwk loadIssuerKey(String keyId) {
        return loadKey("issuer", keyId);
    }

    public static Jwk loadHolderKey(String keyId) {
        return loadKey("holder", keyId);
    }

    private static Jwk loadKey(String category, String keyId) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(keyId, "keyId");

        Path path = resolveKeyPath(category, keyId);
        String json;
        try {
            json = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read synthetic key " + category + "/" + keyId, ex);
        }

        Object parsed = SimpleJson.parse(json);
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Synthetic key " + category + "/" + keyId + " must be a JSON object");
        }
        Map<String, Object> root = castMap(map);
        return new Jwk(
                requireString(root, "kid", keyId),
                requireString(root, "kty", keyId),
                requireString(root, "use", keyId),
                requireString(root, "alg", keyId),
                requireString(root, "crv", keyId),
                requireString(root, "x", keyId),
                requireString(root, "y", keyId),
                requireString(root, "d", keyId));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> source) {
        return (Map<String, Object>) source;
    }

    private static String requireString(Map<String, Object> root, String key, String keyId) {
        Object value = root.get(key);
        if (!(value instanceof String stringValue) || stringValue.isEmpty()) {
            throw new IllegalStateException("Synthetic key " + keyId + " missing field '" + key + "'");
        }
        return stringValue;
    }

    private static Path resolveKeyPath(String category, String keyId) {
        String fileName = keyId + ".jwk.json";
        for (Path base : KEY_SEARCH_PATHS) {
            Path candidate = base.resolve(category).resolve(fileName);
            if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to locate synthetic key " + category + "/" + keyId);
    }

    public record Jwk(String kid, String kty, String use, String alg, String crv, String x, String y, String d) {
        // Intentionally empty; record exposes structural fields.
    }
}
