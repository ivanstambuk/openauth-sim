package io.openauth.sim.application.eudi.openid4vp.fixtures;

import io.openauth.sim.core.eudi.openid4vp.FixtureDatasets;
import io.openauth.sim.core.json.SimpleJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Loads stored presentation descriptors for ingestion toggles. */
public final class OpenId4VpStoredPresentationFixtures {
    private final Path storedPresentationsDir;

    public OpenId4VpStoredPresentationFixtures() {
        this(FixturePaths.resolve("docs", "test-vectors", "eudiw", "openid4vp", "stored", "presentations"));
    }

    public OpenId4VpStoredPresentationFixtures(Path storedPresentationsDir) {
        this.storedPresentationsDir = Objects.requireNonNull(storedPresentationsDir, "storedPresentationsDir");
    }

    public List<StoredPresentationFixture> loadAll() {
        if (!Files.exists(storedPresentationsDir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(storedPresentationsDir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(this::parseFixture)
                    .collect(Collectors.toUnmodifiableList());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to list stored presentations under " + storedPresentationsDir, ex);
        }
    }

    private StoredPresentationFixture parseFixture(Path path) {
        String json;
        try {
            json = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read stored presentation " + path, ex);
        }
        Object parsed = SimpleJson.parse(json);
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Stored presentation " + path + " must be a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) map;
        String presentationId = stripExtension(path.getFileName().toString());
        String credentialId = requireString(root, "credentialId", presentationId);
        String format = requireString(root, "format", presentationId);
        String sourcePath = requireString(root, "source", presentationId);
        FixtureDatasets.Source source = resolveSource(sourcePath);
        List<String> policies = readStringList(root, "trustedAuthorities");
        Map<String, Object> metadata = readMetadata(root);
        return new StoredPresentationFixture(presentationId, source, credentialId, format, policies, metadata);
    }

    private static String stripExtension(String filename) {
        int index = filename.lastIndexOf('.');
        return index > 0 ? filename.substring(0, index) : filename;
    }

    private static FixtureDatasets.Source resolveSource(String sourcePath) {
        if (sourcePath.startsWith("fixtures/conformance/")) {
            return FixtureDatasets.Source.CONFORMANCE;
        }
        return FixtureDatasets.Source.SYNTHETIC;
    }

    private static String requireString(Map<String, Object> root, String key, String presentationId) {
        Object value = root.get(key);
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new IllegalStateException("Stored presentation " + presentationId + " missing field '" + key + "'");
        }
        return stringValue;
    }

    private static List<String> readStringList(Map<String, Object> root, String key) {
        Object value = root.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalStateException("'" + key + "' must be an array in stored presentation metadata");
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (entry instanceof String stringValue) {
                result.add(stringValue);
            } else {
                throw new IllegalStateException("'" + key + "' entries must be strings");
            }
        }
        return List.copyOf(result);
    }

    private static Map<String, Object> readMetadata(Map<String, Object> root) {
        Object value = root.get("metadata");
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) map;
            return Map.copyOf(cast);
        }
        return Map.of();
    }

    public record StoredPresentationFixture(
            String presentationId,
            FixtureDatasets.Source source,
            String credentialId,
            String format,
            List<String> trustedAuthorityPolicies,
            Map<String, Object> metadata) {
        public StoredPresentationFixture {
            Objects.requireNonNull(presentationId, "presentationId");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(credentialId, "credentialId");
            Objects.requireNonNull(format, "format");
            trustedAuthorityPolicies =
                    trustedAuthorityPolicies == null ? List.of() : List.copyOf(trustedAuthorityPolicies);
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }
}
