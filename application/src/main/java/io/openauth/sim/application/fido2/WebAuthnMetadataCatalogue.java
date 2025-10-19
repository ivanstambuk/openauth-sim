package io.openauth.sim.application.fido2;

import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.json.SimpleJson;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads offline WebAuthn Metadata Service (MDS) catalogues committed under {@code docs/} to expose
 * trust anchors and metadata descriptors for attestation workflows.
 */
public final class WebAuthnMetadataCatalogue {

    private static final Path MDS_DIRECTORY = resolveMdsDirectory();
    private static final List<WebAuthnMetadataEntry> ENTRIES = loadEntries();

    private WebAuthnMetadataCatalogue() {
        throw new AssertionError("Utility class");
    }

    /** Returns an immutable view of the offline MDS entries. */
    public static List<WebAuthnMetadataEntry> entries() {
        return ENTRIES;
    }

    private static List<WebAuthnMetadataEntry> loadEntries() {
        if (!Files.isDirectory(MDS_DIRECTORY)) {
            return List.of();
        }

        try (Stream<Path> files = Files.list(MDS_DIRECTORY)) {
            List<WebAuthnMetadataEntry> entries = files.filter(
                            path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .flatMap(WebAuthnMetadataCatalogue::readEntries)
                    .collect(Collectors.toCollection(ArrayList::new));
            return Collections.unmodifiableList(entries);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to enumerate offline MDS catalogues", e);
        }
    }

    private static Stream<WebAuthnMetadataEntry> readEntries(Path file) {
        Object parsed = parseJson(file);
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Offline MDS file must contain a JSON object: " + file);
        }
        Object entriesNode = map.get("entries");
        if (entriesNode == null) {
            return Stream.empty();
        }
        List<Object> entries = asArray(entriesNode, "entries", file);
        List<WebAuthnMetadataEntry> resolved = new ArrayList<>();
        for (Object entry : entries) {
            resolved.add(parseEntry(asObject(entry, "entry", file), file));
        }
        return resolved.stream();
    }

    private static WebAuthnMetadataEntry parseEntry(Map<String, Object> entryNode, Path sourceFile) {
        String entryId = requireString(entryNode, "entry_id", sourceFile);
        UUID aaguid = parseAaguid(requireString(entryNode, "aaguid", sourceFile), sourceFile);
        WebAuthnAttestationFormat format =
                WebAuthnAttestationFormat.fromLabel(requireString(entryNode, "attestation_format", sourceFile));
        String description = optionalString(entryNode, "description");

        List<TrustAnchor> anchors = parseAnchors(entryNode.get("attestation_root_certificates"), sourceFile);
        List<MetadataSource> sources = parseSources(entryNode.get("sources"), sourceFile);

        return new WebAuthnMetadataEntry(entryId, aaguid, format, description, anchors, sources);
    }

    private static List<TrustAnchor> parseAnchors(Object anchorsNode, Path sourceFile) {
        if (anchorsNode == null) {
            return List.of();
        }
        List<Object> entries = asArray(anchorsNode, "attestation_root_certificates", sourceFile);
        List<TrustAnchor> anchors = new ArrayList<>();
        for (Object entry : entries) {
            Map<String, Object> anchor = asObject(entry, "attestation_root_certificates entry", sourceFile);
            String label = optionalString(anchor, "label");
            String fingerprint = normaliseFingerprint(requireString(anchor, "fingerprint_sha256", sourceFile));
            String certificatePem = requireString(anchor, "certificate_pem", sourceFile);
            anchors.add(new TrustAnchor(label, fingerprint, certificatePem));
        }
        return List.copyOf(anchors);
    }

    private static List<MetadataSource> parseSources(Object sourcesNode, Path sourceFile) {
        if (sourcesNode == null) {
            return List.of();
        }
        List<Object> entries = asArray(sourcesNode, "sources", sourceFile);
        List<MetadataSource> sources = new ArrayList<>();
        for (Object entry : entries) {
            Map<String, Object> source = asObject(entry, "sources entry", sourceFile);
            String type = requireString(source, "type", sourceFile);
            String vectorId = optionalString(source, "vector_id");
            sources.add(new MetadataSource(type, vectorId));
        }
        return List.copyOf(sources);
    }

    private static Object parseJson(Path file) {
        try {
            return SimpleJson.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read offline MDS catalogue " + file, e);
        }
    }

    private static Map<String, Object> asObject(Object value, String context, Path sourceFile) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                if (!(key instanceof String keyString)) {
                    throw new IllegalStateException(context + " contains non-string key in " + sourceFile + ": " + key);
                }
                result.put(keyString, entry.getValue());
            }
            return result;
        }
        throw new IllegalStateException(context + " must be a JSON object in " + sourceFile);
    }

    private static List<Object> asArray(Object value, String context, Path sourceFile) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        throw new IllegalStateException(context + " must be a JSON array in " + sourceFile);
    }

    private static String requireString(Map<String, Object> object, String field, Path sourceFile) {
        Object value = object.get(field);
        if (value instanceof String str && !str.isBlank()) {
            return str;
        }
        throw new IllegalStateException(
                "Missing string field '" + field + "' in " + sourceFile + " entry " + objectDescription(object));
    }

    private static String optionalString(Map<String, Object> object, String field) {
        Object value = object.get(field);
        if (value instanceof String str && !str.isBlank()) {
            return str;
        }
        return null;
    }

    private static UUID parseAaguid(String value, Path sourceFile) {
        String normalised = value.trim();
        try {
            if (normalised.contains("-")) {
                return UUID.fromString(normalised);
            }
            byte[] bytes = decodeHex(normalised);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return new UUID(buffer.getLong(), buffer.getLong());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Offline MDS entry in " + sourceFile + " contains invalid AAGUID: " + value, e);
        }
    }

    private static byte[] decodeHex(String value) {
        String hex = value.replace(":", "").replace("-", "").toLowerCase(Locale.ROOT);
        if (hex.length() != 32) {
            throw new IllegalArgumentException("Expected 16-byte (32 char) hex AAGUID but received " + value);
        }
        return HexFormat.of().parseHex(hex);
    }

    private static String normaliseFingerprint(String fingerprint) {
        return fingerprint.trim().toLowerCase(Locale.ROOT);
    }

    private static String objectDescription(Map<String, Object> object) {
        return object.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + Objects.toString(entry.getValue()))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    private static Path resolveMdsDirectory() {
        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path direct = workingDirectory.resolve("docs/webauthn_attestation/mds");
        if (Files.isDirectory(direct)) {
            return direct;
        }
        Path parent = workingDirectory.getParent();
        if (parent != null) {
            Path candidate = parent.resolve("docs/webauthn_attestation/mds");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return direct;
    }

    public record WebAuthnMetadataEntry(
            String entryId,
            UUID aaguid,
            WebAuthnAttestationFormat attestationFormat,
            String description,
            List<TrustAnchor> trustAnchors,
            List<MetadataSource> sources) {
        public WebAuthnMetadataEntry {
            Objects.requireNonNull(entryId, "entryId");
            Objects.requireNonNull(aaguid, "aaguid");
            Objects.requireNonNull(attestationFormat, "attestationFormat");
            trustAnchors = trustAnchors == null ? List.of() : List.copyOf(trustAnchors);
            sources = sources == null ? List.of() : List.copyOf(sources);
        }
    }

    public record TrustAnchor(String label, String fingerprintSha256, String certificatePem) {
        public TrustAnchor {
            Objects.requireNonNull(fingerprintSha256, "fingerprintSha256");
            Objects.requireNonNull(certificatePem, "certificatePem");
        }
    }

    public record MetadataSource(String type, String vectorId) {
        public MetadataSource {
            Objects.requireNonNull(type, "type");
        }
    }
}
