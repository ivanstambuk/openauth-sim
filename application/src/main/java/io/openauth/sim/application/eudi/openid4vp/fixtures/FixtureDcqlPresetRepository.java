package io.openauth.sim.application.eudi.openid4vp.fixtures;

import io.openauth.sim.application.eudi.openid4vp.OpenId4VpAuthorizationRequestService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
        Path path = FixturePaths.resolve(
                "docs", "test-vectors", "eudiw", "openid4vp", "fixtures", "dcql", presetId + ".json");
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read DCQL preset " + presetId + " at " + path, ex);
        }
    }
}
