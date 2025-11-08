package io.openauth.sim.application.eudi.openid4vp.fixtures;

import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService;
import io.openauth.sim.core.json.SimpleJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FixtureWalletPresetRepository implements OpenId4VpWalletSimulationService.WalletPresetRepository {
    private final Map<String, OpenId4VpWalletSimulationService.WalletPreset> presets = new LinkedHashMap<>();

    public FixtureWalletPresetRepository() {
        registerPidHaipBaseline();
    }

    @Override
    public OpenId4VpWalletSimulationService.WalletPreset load(String presetId) {
        OpenId4VpWalletSimulationService.WalletPreset preset = presets.get(presetId);
        if (preset == null) {
            throw new IllegalArgumentException("Unknown wallet preset " + presetId);
        }
        return preset;
    }

    private void registerPidHaipBaseline() {
        Path presetDir = FixturePaths.resolve(
                "docs", "test-vectors", "eudiw", "openid4vp", "fixtures", "synthetic", "sdjwt-vc", "pid-haip-baseline");
        Map<String, Object> metadata = readJson(presetDir.resolve("metadata.json"));
        String credentialId = stringValue(metadata, "credentialId");
        String format = stringValue(metadata, "format");
        String compactSdJwt = readString(presetDir.resolve("sdjwt.txt")).strip();
        List<String> disclosures = readDisclosures(presetDir.resolve("disclosures.json"));
        List<String> disclosureHashes = readDigestValues(presetDir.resolve("digests.json"));
        String kbJwt = readString(presetDir.resolve("kb-jwt.json"));
        List<String> policies = List.of("aki:s9tIpP7qrS9=");
        presets.put(
                "pid-haip-baseline",
                new OpenId4VpWalletSimulationService.WalletPreset(
                        "pid-haip-baseline",
                        credentialId,
                        format,
                        compactSdJwt,
                        disclosures,
                        disclosureHashes,
                        java.util.Optional.of(kbJwt),
                        policies));
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read fixture file " + path, ex);
        }
    }

    private static Map<String, Object> readJson(Path path) {
        Object parsed = SimpleJson.parse(readString(path));
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Fixture " + path + " must be a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> cast = (Map<String, Object>) map;
        return cast;
    }

    private static String stringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalStateException("Fixture missing string field '" + key + "'");
        }
        return string;
    }

    static List<String> readDisclosures(Path path) {
        Object parsed = SimpleJson.parse(readString(path));
        if (!(parsed instanceof List<?> list)) {
            throw new IllegalStateException("Disclosure document " + path + " must be an array");
        }
        List<String> disclosures = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (entry instanceof String stringEntry) {
                disclosures.add(stringEntry);
            } else if (entry instanceof List<?> tuple && !tuple.isEmpty() && tuple.get(0) instanceof String encoded) {
                disclosures.add(encoded);
            } else {
                throw new IllegalStateException("Unsupported disclosure entry in " + path);
            }
        }
        return List.copyOf(disclosures);
    }

    static List<String> readDigestValues(Path path) {
        Map<String, Object> digests = readJson(path);
        List<String> values = new ArrayList<>(digests.size());
        for (Object value : digests.values()) {
            if (!(value instanceof String stringValue)) {
                throw new IllegalStateException("Digest value must be a string in " + path);
            }
            values.add(stringValue);
        }
        return List.copyOf(values);
    }
}
