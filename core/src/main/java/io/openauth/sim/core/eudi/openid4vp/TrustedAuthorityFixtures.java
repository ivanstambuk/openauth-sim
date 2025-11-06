package io.openauth.sim.core.eudi.openid4vp;

import io.openauth.sim.core.json.SimpleJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Loader for EUDIW OpenID4VP trusted authority fixtures stored under {@code docs/test-vectors}. */
public final class TrustedAuthorityFixtures {

    private static final List<Path> SNAPSHOT_SEARCH_PATHS = List.of(
            Path.of("docs", "test-vectors", "eudiw", "openid4vp", "trust", "snapshots"),
            Path.of("..", "docs", "test-vectors", "eudiw", "openid4vp", "trust", "snapshots"));

    private TrustedAuthorityFixtures() {
        throw new AssertionError("Utility class");
    }

    public static TrustedAuthoritySnapshot loadSnapshot(String presetId) {
        Objects.requireNonNull(presetId, "presetId");
        Map<String, Object> root = readSnapshot(presetId);

        String resolvedPresetId = requireString(root, "presetId", presetId);
        List<String> storedPresentationIds = readStringList(root, "storedPresentationIds", presetId);
        List<TrustedAuthorityPolicy> policies = readPolicies(root, presetId);

        return new TrustedAuthoritySnapshot(resolvedPresetId, policies, storedPresentationIds);
    }

    private static Map<String, Object> readSnapshot(String presetId) {
        Path snapshotPath = resolveSnapshotPath(presetId);
        String json;
        try {
            json = Files.readString(snapshotPath, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read OpenID4VP trusted authority snapshot " + presetId, ex);
        }

        Object parsed = SimpleJson.parse(json);
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Trusted authority snapshot " + presetId + " must be a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) map;
        return root;
    }

    private static Path resolveSnapshotPath(String presetId) {
        String fileName = presetId + ".json";
        for (Path base : SNAPSHOT_SEARCH_PATHS) {
            Path candidate = base.resolve(fileName);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to locate trusted authority snapshot " + fileName);
    }

    private static List<String> readStringList(Map<String, Object> root, String key, String presetId) {
        Object value = root.get(key);
        if (!(value instanceof List<?> list)) {
            throw new IllegalStateException(
                    "Trusted authority snapshot " + presetId + " must provide array field '" + key + "'");
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object element : list) {
            if (!(element instanceof String stringValue)) {
                throw new IllegalStateException(
                        "Entries in '" + key + "' for snapshot " + presetId + " must be strings");
            }
            result.add(stringValue);
        }
        return List.copyOf(result);
    }

    private static List<TrustedAuthorityPolicy> readPolicies(Map<String, Object> root, String presetId) {
        Object value = root.get("authorities");
        if (!(value instanceof List<?> list)) {
            throw new IllegalStateException(
                    "Trusted authority snapshot " + presetId + " must include an 'authorities' array");
        }

        List<TrustedAuthorityPolicy> policies = new ArrayList<>(list.size());
        for (Object candidate : list) {
            if (!(candidate instanceof Map<?, ?> policyMap)) {
                throw new IllegalStateException(
                        "Entries in 'authorities' for snapshot " + presetId + " must be JSON objects");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> policy = (Map<String, Object>) policyMap;
            String type = requireString(policy, "type", presetId);
            List<TrustedAuthorityValue> values = readPolicyValues(policy, presetId, type);
            policies.add(new TrustedAuthorityPolicy(type, values));
        }
        return List.copyOf(policies);
    }

    private static List<TrustedAuthorityValue> readPolicyValues(
            Map<String, Object> policy, String presetId, String type) {
        Object value = policy.get("values");
        if (!(value instanceof List<?> list)) {
            throw new IllegalStateException(
                    "Policy " + type + " in snapshot " + presetId + " must define a 'values' array");
        }
        List<TrustedAuthorityValue> values = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> valueMap)) {
                throw new IllegalStateException(
                        "Values for policy " + type + " in snapshot " + presetId + " must be JSON objects");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) valueMap;
            String trustedValue = requireString(cast, "value", presetId);
            String label = requireString(cast, "label", presetId);
            values.add(new TrustedAuthorityValue(trustedValue, label));
        }
        return List.copyOf(values);
    }

    private static String requireString(Map<String, Object> map, String key, String presetId) {
        Object value = map.get(key);
        if (!(value instanceof String stringValue) || stringValue.isEmpty()) {
            throw new IllegalStateException(
                    "Trusted authority snapshot " + presetId + " is missing string field '" + key + "'");
        }
        return stringValue;
    }

    public record TrustedAuthoritySnapshot(
            String presetId, List<TrustedAuthorityPolicy> authorities, List<String> storedPresentationIds) {
        // Marker record – state is defined by canonical components.
    }

    public record TrustedAuthorityPolicy(String type, List<TrustedAuthorityValue> values) {
        // Marker record – state is defined by canonical components.
    }

    public record TrustedAuthorityValue(String value, String label) {
        // Marker record – state is defined by canonical components.
    }
}
