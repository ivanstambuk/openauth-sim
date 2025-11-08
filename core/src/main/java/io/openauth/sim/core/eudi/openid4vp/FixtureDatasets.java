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

/** Resolves OpenID4VP fixture dataset metadata. */
public final class FixtureDatasets {
    private static final List<Path> FIXTURE_ROOTS = List.of(
            Path.of("docs", "test-vectors", "eudiw", "openid4vp", "fixtures"),
            Path.of("..", "docs", "test-vectors", "eudiw", "openid4vp", "fixtures"),
            Path.of("..", "..", "docs", "test-vectors", "eudiw", "openid4vp", "fixtures"));

    private FixtureDatasets() {
        throw new AssertionError("Utility class");
    }

    public static FixtureDataset load(Source source) {
        Objects.requireNonNull(source, "source");
        Path datasetRoot = resolveDatasetRoot(source);
        Map<String, Object> provenanceJson = readProvenance(datasetRoot);
        Provenance provenance = new Provenance(
                requireString(provenanceJson, "source", source),
                requireString(provenanceJson, "version", source),
                requireString(provenanceJson, "sha256", source),
                optionalString(provenanceJson, "description"));
        return new FixtureDataset(source, datasetRoot, provenance);
    }

    private static Path resolveDatasetRoot(Source source) {
        String directoryName = source.directoryName();
        for (Path root : FIXTURE_ROOTS) {
            Path candidate = root.resolve(directoryName);
            if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                return candidate.normalize();
            }
        }
        throw new IllegalStateException("Unable to locate OpenID4VP fixture dataset " + directoryName);
    }

    private static Map<String, Object> readProvenance(Path datasetRoot) {
        Path provenancePath = datasetRoot.resolve("provenance.json");
        String json;
        try {
            json = Files.readString(provenancePath, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read fixture provenance at " + provenancePath, ex);
        }
        Object parsed = SimpleJson.parse(json);
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Fixture provenance " + provenancePath + " must be a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> cast = (Map<String, Object>) map;
        return cast;
    }

    private static String requireString(Map<String, Object> map, String key, Source source) {
        Object value = map.get(key);
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new IllegalStateException(
                    "Fixture dataset " + source.directoryName() + " missing field '" + key + "'");
        }
        return stringValue;
    }

    private static String optionalString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue;
        }
        return "";
    }

    public enum Source {
        SYNTHETIC("synthetic"),
        CONFORMANCE("conformance");

        private final String directoryName;

        Source(String directoryName) {
            this.directoryName = directoryName;
        }

        public String directoryName() {
            return this.directoryName;
        }
    }

    public record FixtureDataset(Source source, Path rootDirectory, Provenance provenance) {
        public FixtureDataset {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(rootDirectory, "rootDirectory");
            Objects.requireNonNull(provenance, "provenance");
        }
    }

    public record Provenance(String source, String version, String sha256, String description) {
        public Provenance {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(sha256, "sha256");
            description = description == null ? "" : description;
        }
    }
}
