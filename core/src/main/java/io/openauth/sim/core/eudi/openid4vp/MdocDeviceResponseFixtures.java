package io.openauth.sim.core.eudi.openid4vp;

import io.openauth.sim.core.json.SimpleJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Loads ISO/IEC 18013-5 mdoc DeviceResponse fixtures for Feature 040.
 * <p>
 * This is a placeholder to unblock failing tests staged for T4006; the implementation will
 * populate fields from docs/test-vectors/eudiw/openid4vp/fixtures/synthetic/mdoc/ in a follow-up.
 */
public final class MdocDeviceResponseFixtures {

    private final Path fixtureRoot;

    public MdocDeviceResponseFixtures(Path fixtureRoot) {
        this.fixtureRoot = Objects.requireNonNull(fixtureRoot, "fixtureRoot");
    }

    public MdocDeviceResponseFixtures() {
        this(Path.of("docs", "test-vectors", "eudiw", "openid4vp", "fixtures", "synthetic", "mdoc"));
    }

    public DeviceResponseFixture load(String presetId) {
        Objects.requireNonNull(presetId, "presetId");
        Path presetDir = resolvePath(this.fixtureRoot.resolve(presetId));
        if (!Files.isDirectory(presetDir)) {
            throw new IllegalArgumentException("Unknown mdoc preset: " + presetId);
        }

        try {
            Map<String, Object> metadata = readJsonObject(presetDir.resolve("metadata.json"));
            String credentialId = requireString(metadata.get("credentialId"), "metadata.credentialId");
            String docType = resolveDocType(metadata);
            String deviceResponse = readFile(presetDir.resolve("device-response.base64"));
            Map<String, String> claimsPointers = readStringMap(presetDir.resolve("claims-pointer.json"));
            return new DeviceResponseFixture(presetId, credentialId, docType, deviceResponse, claimsPointers);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load mdoc preset: " + presetId, e);
        }
    }

    private static String resolveDocType(Map<String, Object> metadata) {
        Object docTypeValue = metadata.get("docType");
        if (docTypeValue == null) {
            docTypeValue = metadata.get("format");
        }
        return requireString(docTypeValue, "metadata.docType");
    }

    private static String readFile(Path path) throws IOException {
        return Files.readString(resolvePath(path), StandardCharsets.UTF_8).trim();
    }

    private static Map<String, Object> readJsonObject(Path path) throws IOException {
        Object parsed = SimpleJson.parse(readFile(path));
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Expected JSON object at " + path);
        }
        Map<String, Object> ordered = new LinkedHashMap<>();
        map.forEach((key, value) -> ordered.put(String.valueOf(key), value));
        return ordered;
    }

    private static Map<String, String> readStringMap(Path path) throws IOException {
        Map<String, Object> raw = readJsonObject(path);
        Map<String, String> resolved = new LinkedHashMap<>();
        raw.forEach((key, value) -> resolved.put(String.valueOf(key), String.valueOf(value)));
        return resolved;
    }

    private static Path resolvePath(Path path) {
        if (Files.exists(path)) {
            return path;
        }
        Path workspaceRelative = Path.of("..").resolve(path);
        if (Files.exists(workspaceRelative)) {
            return workspaceRelative;
        }
        return path;
    }

    private static String requireString(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException(fieldName + " missing");
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            throw new IllegalStateException(fieldName + " must be non-empty");
        }
        return text;
    }

    public record DeviceResponseFixture(
            String presetId,
            String credentialId,
            String docType,
            String deviceResponseBase64,
            Map<String, String> claimsPathPointers) {
        public DeviceResponseFixture {
            Objects.requireNonNull(presetId, "presetId");
            Objects.requireNonNull(credentialId, "credentialId");
            Objects.requireNonNull(docType, "docType");
            Objects.requireNonNull(deviceResponseBase64, "deviceResponseBase64");
            claimsPathPointers = claimsPathPointers == null ? Map.of() : Map.copyOf(claimsPathPointers);
        }
    }
}
