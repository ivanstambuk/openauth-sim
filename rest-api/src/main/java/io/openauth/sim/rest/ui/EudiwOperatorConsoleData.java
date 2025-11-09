package io.openauth.sim.rest.ui;

import io.openauth.sim.core.json.SimpleJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class EudiwOperatorConsoleData {

    private static final List<Path> FIXTURE_BASES = List.of(
            Path.of("docs", "test-vectors", "eudiw", "openid4vp"),
            Path.of("..", "docs", "test-vectors", "eudiw", "openid4vp"));
    private static final Path FIXTURE_ROOT = locateFixtureRoot();
    private static final Snapshot SNAPSHOT = loadSnapshot();

    private EudiwOperatorConsoleData() {
        throw new AssertionError("Utility class");
    }

    static Snapshot snapshot() {
        return SNAPSHOT;
    }

    private static Snapshot loadSnapshot() {
        List<TrustedAuthority> authorities = loadTrustedAuthorities();
        List<DcqlPreset> dcqlPresets = loadDcqlPresets();
        List<WalletPreset> walletPresets = loadWalletPresets();
        InlineSamples inlineSamples = loadInlineSamples(walletPresets);
        List<StoredPresentation> storedPresentations = loadStoredPresentations();
        return new Snapshot(dcqlPresets, walletPresets, inlineSamples, storedPresentations, authorities);
    }

    private static List<DcqlPreset> loadDcqlPresets() {
        Path dcqlDir = FIXTURE_ROOT.resolve("fixtures").resolve("dcql");
        if (!Files.isDirectory(dcqlDir)) {
            throw new IllegalStateException("Missing DCQL fixture directory: " + dcqlDir);
        }
        List<DcqlPreset> presets = new ArrayList<>();
        try (var stream = Files.list(dcqlDir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> presets.add(readDcqlPreset(path)));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to list DCQL presets in " + dcqlDir, ex);
        }
        return List.copyOf(presets);
    }

    private static DcqlPreset readDcqlPreset(Path path) {
        String json = readString(path);
        Object parsed = SimpleJson.parse(json);
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalStateException("DCQL preset must be a JSON object: " + path);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> dcql = (Map<String, Object>) map;
        String presetId = stringValue(dcql, "type", path.toString());
        List<String> trustedPolicies = readTrustedPolicies(dcql);
        return new DcqlPreset(presetId, presetId, "DCQL preset " + presetId, json.strip(), trustedPolicies);
    }

    private static List<String> readTrustedPolicies(Map<String, Object> dcql) {
        Object trustedAuthorities = dcql.get("trusted_authorities");
        if (!(trustedAuthorities instanceof List<?> list)) {
            return List.of();
        }
        List<String> policies = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> mapEntry)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> typedEntry = (Map<String, Object>) mapEntry;
            String type = Objects.toString(typedEntry.get("type"), "").trim();
            Object values = typedEntry.get("values");
            if (!(values instanceof List<?> listValues) || type.isEmpty()) {
                continue;
            }
            for (Object value : listValues) {
                if (value instanceof String single) {
                    policies.add(type + ":" + single);
                }
            }
        }
        return List.copyOf(policies);
    }

    private static List<TrustedAuthority> loadTrustedAuthorities() {
        Path trustDir = FIXTURE_ROOT.resolve("trust").resolve("snapshots");
        if (!Files.isDirectory(trustDir)) {
            throw new IllegalStateException("Missing trusted authority snapshots: " + trustDir);
        }
        Map<String, TrustedAuthority> authorities = new LinkedHashMap<>();
        try (var stream = Files.list(trustDir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> parseTrustedAuthorities(path, authorities));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to list trusted authority snapshots in " + trustDir, ex);
        }
        return List.copyOf(authorities.values());
    }

    private static void parseTrustedAuthorities(Path path, Map<String, TrustedAuthority> authorities) {
        Object parsed = SimpleJson.parse(readString(path));
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Trusted authority snapshot must be a JSON object: " + path);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = (Map<String, Object>) map;
        Object entries = snapshot.get("authorities");
        if (!(entries instanceof List<?> list)) {
            return;
        }
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> mapEntry)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> typedEntry = (Map<String, Object>) mapEntry;
            String type = Objects.toString(typedEntry.get("type"), "").trim();
            Object values = typedEntry.get("values");
            if (!(values instanceof List<?> listValues) || type.isEmpty()) {
                continue;
            }
            for (Object value : listValues) {
                if (!(value instanceof Map<?, ?> valueMap)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> typedValue = (Map<String, Object>) valueMap;
                String trustedValue = stringValue(typedValue, "value", path.toString());
                String label = stringValue(typedValue, "label", path.toString());
                String policy = type + ":" + trustedValue;
                authorities.putIfAbsent(policy, new TrustedAuthority(policy, type, trustedValue, label));
            }
        }
    }

    private static List<WalletPreset> loadWalletPresets() {
        Path sdjwtDir = FIXTURE_ROOT.resolve("fixtures").resolve("synthetic").resolve("sdjwt-vc");
        if (!Files.isDirectory(sdjwtDir)) {
            throw new IllegalStateException("Missing SD-JWT fixture directory: " + sdjwtDir);
        }
        List<WalletPreset> presets = new ArrayList<>();
        try (var stream = Files.list(sdjwtDir)) {
            stream.filter(Files::isDirectory).sorted().forEach(path -> presets.add(readWalletPreset(path)));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to list wallet presets in " + sdjwtDir, ex);
        }
        return List.copyOf(presets);
    }

    private static WalletPreset readWalletPreset(Path presetDir) {
        Path metadataPath = presetDir.resolve("metadata.json");
        Map<String, Object> metadata = readJson(metadataPath);
        String presetId = presetDir.getFileName().toString();
        String credentialId = stringValue(metadata, "credentialId", metadataPath.toString());
        String format = stringValue(metadata, "format", metadataPath.toString());
        String profile = metadata.containsKey("profile")
                ? Objects.toString(metadata.get("profile"), "HAIP").toUpperCase(Locale.ROOT)
                : "HAIP";
        String description = metadata.containsKey("description")
                ? Objects.toString(metadata.get("description"), "Preset " + presetId)
                : "Preset " + presetId;
        String authorityKeyId = readAuthorityKeyIdentifier(metadata, metadataPath.toString());
        List<String> trustedPolicies = authorityKeyId.isEmpty() ? List.of() : List.of("aki:" + authorityKeyId);
        return new WalletPreset(presetId, description, description, credentialId, format, profile, trustedPolicies);
    }

    private static InlineSamples loadInlineSamples(List<WalletPreset> walletPresets) {
        List<SdJwtSample> sdJwtSamples = new ArrayList<>();
        Path sdjwtDir = FIXTURE_ROOT.resolve("fixtures").resolve("synthetic").resolve("sdjwt-vc");
        if (!Files.isDirectory(sdjwtDir)) {
            throw new IllegalStateException("Missing SD-JWT fixture directory: " + sdjwtDir);
        }
        try (var stream = Files.list(sdjwtDir)) {
            stream.filter(Files::isDirectory).sorted().forEach(path -> sdJwtSamples.add(readSdJwtSample(path)));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to list SD-JWT samples in " + sdjwtDir, ex);
        }

        List<MdocSample> mdocSamples = new ArrayList<>();
        Path mdocDir = FIXTURE_ROOT.resolve("fixtures").resolve("synthetic").resolve("mdoc");
        if (Files.isDirectory(mdocDir)) {
            try (var stream = Files.list(mdocDir)) {
                stream.filter(Files::isDirectory).sorted().forEach(path -> mdocSamples.add(readMdocSample(path)));
            } catch (IOException ex) {
                throw new IllegalStateException("Unable to list mdoc samples in " + mdocDir, ex);
            }
        }
        return new InlineSamples(List.copyOf(sdJwtSamples), List.copyOf(mdocSamples));
    }

    private static SdJwtSample readSdJwtSample(Path sampleDir) {
        Path metadataPath = sampleDir.resolve("metadata.json");
        Map<String, Object> metadata = readJson(metadataPath);
        String presetId = sampleDir.getFileName().toString();
        String credentialId = stringValue(metadata, "credentialId", metadataPath.toString());
        String format = stringValue(metadata, "format", metadataPath.toString());
        String description = metadata.containsKey("description")
                ? Objects.toString(metadata.get("description"), "Sample " + presetId)
                : "Sample " + presetId;
        String compactSdJwt = readString(sampleDir.resolve("sdjwt.txt")).strip();
        String disclosuresJson =
                readString(sampleDir.resolve("disclosures.json")).strip();
        String kbJwt = null;
        Path kbPath = sampleDir.resolve("kb-jwt.json");
        if (Files.exists(kbPath)) {
            kbJwt = readString(kbPath).strip();
        }
        String authorityKeyId = readAuthorityKeyIdentifier(metadata, metadataPath.toString());
        List<String> trustedPolicies = authorityKeyId.isEmpty() ? List.of() : List.of("aki:" + authorityKeyId);

        return new SdJwtSample(
                presetId,
                description,
                description,
                credentialId,
                format,
                compactSdJwt,
                disclosuresJson,
                kbJwt,
                trustedPolicies);
    }

    private static MdocSample readMdocSample(Path sampleDir) {
        Path metadataPath = sampleDir.resolve("metadata.json");
        Map<String, Object> metadata = readJson(metadataPath);
        String presetId = sampleDir.getFileName().toString();
        String credentialId = stringValue(metadata, "credentialId", metadataPath.toString());
        String description = metadata.containsKey("description")
                ? Objects.toString(metadata.get("description"), "Sample " + presetId)
                : "Sample " + presetId;
        String deviceResponse =
                readString(sampleDir.resolve("device-response.base64")).strip();
        return new MdocSample(presetId, description, description, credentialId, deviceResponse);
    }

    private static List<StoredPresentation> loadStoredPresentations() {
        Path storedDir = FIXTURE_ROOT.resolve("stored").resolve("presentations");
        if (!Files.isDirectory(storedDir)) {
            throw new IllegalStateException("Missing stored presentation fixtures: " + storedDir);
        }
        List<StoredPresentation> stored = new ArrayList<>();
        try (var stream = Files.list(storedDir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> stored.add(readStoredPresentation(path)));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to list stored presentations in " + storedDir, ex);
        }
        return List.copyOf(stored);
    }

    private static StoredPresentation readStoredPresentation(Path path) {
        Map<String, Object> payload = readJson(path);
        String id = path.getFileName().toString().replace(".json", "");
        String profile =
                Objects.toString(payload.getOrDefault("profile", "HAIP")).toUpperCase(Locale.ROOT);
        String format = stringValue(payload, "format", path.toString());
        String description = payload.containsKey("metadata")
                ? nestedStringValue(payload, "metadata", "description", id)
                : "Stored presentation " + id;
        List<String> trustedPolicies = readStringList(payload.get("trustedAuthorities"));
        return new StoredPresentation(id, description, description, profile, format, trustedPolicies);
    }

    private static Map<String, Object> readJson(Path path) {
        String json = readString(path);
        Object parsed = SimpleJson.parse(json);
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalStateException("JSON object expected at " + path);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> cast = (Map<String, Object>) map;
        return cast;
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read fixture file " + path, ex);
        }
    }

    private static String stringValue(Map<String, Object> source, String key, String context) {
        Object value = source.get(key);
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalStateException("Missing string field '" + key + "' in " + context);
        }
        return string.trim();
    }

    private static String nestedStringValue(Map<String, Object> source, String key, String nestedKey, String fallback) {
        Object value = source.get(key);
        if (!(value instanceof Map<?, ?> map)) {
            return fallback;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> cast = (Map<String, Object>) map;
        Object nestedValue = cast.get(nestedKey);
        if (!(nestedValue instanceof String string)) {
            return fallback;
        }
        return string;
    }

    private static String readAuthorityKeyIdentifier(Map<String, Object> metadata, String context) {
        Object issuer = metadata.get("issuer");
        if (!(issuer instanceof Map<?, ?> issuerMap)) {
            return "";
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> cast = (Map<String, Object>) issuerMap;
        Object aki = cast.get("authorityKeyIdentifier");
        if (aki instanceof String value && !value.isBlank()) {
            return value.trim();
        }
        return "";
    }

    private static List<String> readStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (entry instanceof String string && !string.isBlank()) {
                result.add(string);
            }
        }
        return List.copyOf(result);
    }

    record Snapshot(
            List<DcqlPreset> dcqlPresets,
            List<WalletPreset> walletPresets,
            InlineSamples inlineSamples,
            List<StoredPresentation> storedPresentations,
            List<TrustedAuthority> trustedAuthorities) {}

    record DcqlPreset(
            String id, String label, String description, String json, List<String> trustedAuthorityPolicies) {}

    record WalletPreset(
            String id,
            String label,
            String description,
            String credentialId,
            String format,
            String profile,
            List<String> trustedAuthorityPolicies) {}

    record InlineSamples(List<SdJwtSample> sdJwt, List<MdocSample> mdoc) {}

    record SdJwtSample(
            String key,
            String label,
            String description,
            String credentialId,
            String format,
            String compactSdJwt,
            String disclosuresJson,
            String kbJwt,
            List<String> trustedAuthorityPolicies) {}

    record MdocSample(String key, String label, String description, String credentialId, String deviceResponseBase64) {}

    record StoredPresentation(
            String id,
            String label,
            String description,
            String profile,
            String format,
            List<String> trustedAuthorityPolicies) {}

    record TrustedAuthority(String policy, String type, String value, String label) {}

    private static Path locateFixtureRoot() {
        for (Path candidate : FIXTURE_BASES) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Missing OpenID4VP fixture root (looked in " + FIXTURE_BASES + ")");
    }
}
