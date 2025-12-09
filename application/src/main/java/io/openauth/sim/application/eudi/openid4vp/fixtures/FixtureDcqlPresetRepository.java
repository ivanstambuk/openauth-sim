package io.openauth.sim.application.eudi.openid4vp.fixtures;

import io.openauth.sim.application.eudi.openid4vp.OpenId4VpAuthorizationRequestService;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FixtureDcqlPresetRepository implements OpenId4VpAuthorizationRequestService.DcqlPresetRepository {
    private final Map<String, OpenId4VpAuthorizationRequestService.DcqlPreset> presets;

    public FixtureDcqlPresetRepository() {
        this.presets = loadPresets();
    }

    @Override
    public OpenId4VpAuthorizationRequestService.DcqlPreset load(String presetId) {
        OpenId4VpAuthorizationRequestService.DcqlPreset preset = presets.get(presetId);
        if (preset == null) {
            throw new IllegalArgumentException("Unknown DCQL preset " + presetId);
        }
        return preset;
    }

    private static Map<String, OpenId4VpAuthorizationRequestService.DcqlPreset> loadPresets() {
        Map<String, OpenId4VpAuthorizationRequestService.DcqlPreset> entries = new HashMap<>();
        entries.put(
                "pid-haip-baseline",
                new OpenId4VpAuthorizationRequestService.DcqlPreset(
                        "pid-haip-baseline",
                        "x509_hash:pid-haip-verifier",
                        readDcqlJson("pid-haip-baseline"),
                        List.of("aki:s9tIpP7qrS9=", "etsi_tl:lotl-373", "etsi_tl:de-149", "etsi_tl:si-78")));
        return Map.copyOf(entries);
    }

    private static String readDcqlJson(String presetId) {
        String classpath = "docs/test-vectors/eudiw/openid4vp/fixtures/dcql/" + presetId + ".json";
        Path path = FixturePaths.resolve(
                "docs", "test-vectors", "eudiw", "openid4vp", "fixtures", "dcql", presetId + ".json");
        String content = FixturePaths.readUtf8OrNull(classpath, path);
        if (content != null) {
            return content;
        }
        // Minimal fallback to keep CLI usable when fixtures are unavailable on the filesystem/classpath.
        return """
                {
                  "type": "%s",
                  "credentials": [],
                  "trusted_authorities": []
                }
                """.formatted(presetId);
    }
}
