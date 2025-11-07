package io.openauth.sim.application.eudi.openid4vp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Red tests for Feature 040 S2 mdoc wallet simulation (task T4006). */
final class MdocWalletSimulationServiceTest {

    private static final String PRESET_ID = "pid-haip-baseline";
    private static final String INLINE_ID = "pid-inline-mdoc";
    private static final String TRUSTED_POLICY = "aki:s9tIpP7qrS9=";

    @Test
    void presetWalletProducesDeterministicDeviceResponseAndClaimsPointers() throws Exception {
        FixtureMdocPresetRepository repository = new FixtureMdocPresetRepository();
        RecordingTelemetry telemetry = new RecordingTelemetry();
        RecordingEncryptionHook encryptionHook = new RecordingEncryptionHook();

        MdocWalletSimulationService service = new MdocWalletSimulationService(
                new MdocWalletSimulationService.Dependencies(repository, telemetry, encryptionHook));

        MdocWalletSimulationService.SimulateRequest request = new MdocWalletSimulationService.SimulateRequest(
                "HAIP-MDOC-001",
                MdocWalletSimulationService.Profile.HAIP,
                MdocWalletSimulationService.ResponseMode.DIRECT_POST_JWT,
                Optional.of(PRESET_ID),
                Optional.empty(),
                Optional.of(TRUSTED_POLICY));

        MdocWalletSimulationService.SimulationResult result = service.simulate(request);

        assertEquals("HAIP-MDOC-001", result.requestId());
        assertEquals(MdocWalletSimulationService.Status.SUCCESS, result.status());
        assertEquals(MdocWalletSimulationService.Profile.HAIP, result.profile());
        assertEquals(MdocWalletSimulationService.ResponseMode.DIRECT_POST_JWT, result.responseMode());

        MdocWalletSimulationService.Presentation presentation =
                result.presentations().get(0);
        assertEquals("pid-haip-baseline", presentation.credentialId());
        assertEquals("mso_mdoc", presentation.format());
        assertTrue(presentation.trustedAuthorityMatch().isPresent());
        assertEquals(TRUSTED_POLICY, presentation.trustedAuthorityMatch().get());

        MdocWalletSimulationService.Trace trace = result.trace();
        MdocWalletSimulationService.PresentationDiagnostics diagnostics =
                trace.presentations().get(0);
        assertEquals(repository.expectedDeviceResponseHash(), diagnostics.deviceResponseHash());
        assertEquals(
                Map.of(
                        "$.pid.family_name", true,
                        "$.pid.given_name", true,
                        "$.pid.birth_date", true),
                diagnostics.claimsSatisfied());

        assertEquals("oid4vp.wallet.responded", result.telemetry().event());
        assertEquals("HAIP-MDOC-001", telemetry.lastRequestId);
        assertTrue(encryptionHook.invokedWith(request));
    }

    @Test
    void inlineDeviceResponseOverridesPresetMetadata() throws Exception {
        FixtureMdocPresetRepository repository = new FixtureMdocPresetRepository();
        RecordingTelemetry telemetry = new RecordingTelemetry();
        RecordingEncryptionHook encryptionHook = new RecordingEncryptionHook();

        MdocWalletSimulationService service = new MdocWalletSimulationService(
                new MdocWalletSimulationService.Dependencies(repository, telemetry, encryptionHook));

        MdocWalletSimulationService.DeviceResponsePreset basePreset = repository.load(PRESET_ID);
        String inlineDeviceResponse = basePreset.deviceResponseBase64();
        Map<String, String> inlineClaims = new LinkedHashMap<>(basePreset.claimsPathPointers());
        inlineClaims.put("$.pid.given_name", "Beatrice");

        MdocWalletSimulationService.InlineMdoc inline = new MdocWalletSimulationService.InlineMdoc(
                INLINE_ID, "mso_mdoc", inlineDeviceResponse, inlineClaims, List.of(TRUSTED_POLICY));

        MdocWalletSimulationService.SimulateRequest request = new MdocWalletSimulationService.SimulateRequest(
                "INLINE-MDOC-001",
                MdocWalletSimulationService.Profile.BASELINE,
                MdocWalletSimulationService.ResponseMode.DIRECT_POST,
                Optional.empty(),
                Optional.of(inline),
                Optional.of(TRUSTED_POLICY));

        MdocWalletSimulationService.SimulationResult result = service.simulate(request);

        assertEquals("INLINE-MDOC-001", result.requestId());
        MdocWalletSimulationService.Presentation presentation =
                result.presentations().get(0);
        assertEquals(INLINE_ID, presentation.credentialId());
        assertEquals("mso_mdoc", presentation.format());
        assertEquals(Optional.of(TRUSTED_POLICY), presentation.trustedAuthorityMatch());

        MdocWalletSimulationService.PresentationDiagnostics diagnostics =
                result.trace().presentations().get(0);
        assertEquals(repository.expectedDeviceResponseHash(), diagnostics.deviceResponseHash());
        assertTrue(diagnostics.claimsSatisfied().containsKey("$.pid.given_name"));
    }

    @Test
    void haipProfileWithDirectPostJwtTriggersEncryptionHook() {
        FixtureMdocPresetRepository repository = new FixtureMdocPresetRepository();
        RecordingTelemetry telemetry = new RecordingTelemetry();
        RecordingEncryptionHook encryptionHook = new RecordingEncryptionHook();

        MdocWalletSimulationService service = new MdocWalletSimulationService(
                new MdocWalletSimulationService.Dependencies(repository, telemetry, encryptionHook));

        MdocWalletSimulationService.SimulateRequest request = new MdocWalletSimulationService.SimulateRequest(
                "HAIP-ENC-001",
                MdocWalletSimulationService.Profile.HAIP,
                MdocWalletSimulationService.ResponseMode.DIRECT_POST_JWT,
                Optional.of(PRESET_ID),
                Optional.empty(),
                Optional.of(TRUSTED_POLICY));

        service.simulate(request);

        assertTrue(encryptionHook.invokedWith(request));
    }

    private static final class FixtureMdocPresetRepository
            implements MdocWalletSimulationService.DeviceResponsePresetRepository {

        private static final Path FIXTURE_ROOT =
                Path.of("docs", "test-vectors", "eudiw", "openid4vp", "fixtures", "synthetic", "mdoc");

        private final MdocWalletSimulationService.DeviceResponsePreset fixture;
        private final String expectedHash;

        private FixtureMdocPresetRepository() {
            try {
                Path presetDir = resolve(FIXTURE_ROOT.resolve("pid-haip-baseline"));
                String deviceResponse = read(presetDir.resolve("device-response.base64"));
                Map<String, String> claims = readJsonMap(presetDir.resolve("claims-pointer.json"));
                this.fixture = new MdocWalletSimulationService.DeviceResponsePreset(
                        PRESET_ID, "pid-haip-baseline", "mso_mdoc", deviceResponse, claims, List.of(TRUSTED_POLICY));
                this.expectedHash = sha256Base64(deviceResponse);
            } catch (IOException | NoSuchAlgorithmException e) {
                throw new IllegalStateException("Unable to load mdoc fixture", e);
            }
        }

        @Override
        public MdocWalletSimulationService.DeviceResponsePreset load(String presetId) {
            if (!PRESET_ID.equals(presetId)) {
                throw new IllegalArgumentException("Unknown preset: " + presetId);
            }
            return fixture;
        }

        String expectedDeviceResponseHash() {
            return expectedHash;
        }

        private static String read(Path path) throws IOException {
            return Files.readString(resolve(path), StandardCharsets.UTF_8).trim();
        }

        private static Map<String, String> readJsonMap(Path path) throws IOException {
            String json = Files.readString(resolve(path), StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = (Map<String, Object>) io.openauth.sim.core.json.SimpleJson.parse(json);
            Map<String, String> ordered = new LinkedHashMap<>();
            parsed.forEach((key, value) -> ordered.put(String.valueOf(key), String.valueOf(value)));
            return ordered;
        }

        private static Path resolve(Path relative) {
            Path direct = Path.of("").resolve(relative);
            if (Files.exists(direct)) {
                return direct;
            }
            Path workspaceRoot = Path.of("..").resolve(relative);
            if (Files.exists(workspaceRoot)) {
                return workspaceRoot;
            }
            throw new IllegalStateException("Fixture path not found: " + relative);
        }
    }

    private static String sha256Base64(String base64) throws NoSuchAlgorithmException {
        byte[] decoded = Base64.getDecoder().decode(base64);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(decoded);
        StringBuilder builder = new StringBuilder();
        for (byte b : hash) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private static final class RecordingTelemetry
            implements MdocWalletSimulationService.TelemetryPublisher, MdocWalletSimulationService.TelemetrySignal {

        private String lastRequestId;

        @Override
        public MdocWalletSimulationService.TelemetrySignal walletResponded(
                String requestId, MdocWalletSimulationService.Profile profile, int presentations) {
            this.lastRequestId = requestId;
            return this;
        }

        @Override
        public String event() {
            return "oid4vp.wallet.responded";
        }

        @Override
        public Map<String, Object> fields() {
            return Map.of();
        }
    }

    private static final class RecordingEncryptionHook implements MdocWalletSimulationService.HaipEncryptionHook {

        private MdocWalletSimulationService.SimulateRequest lastRequest;

        @Override
        public void ensureEncryption(
                MdocWalletSimulationService.Profile profile,
                MdocWalletSimulationService.ResponseMode responseMode,
                String requestId) {
            this.lastRequest = new MdocWalletSimulationService.SimulateRequest(
                    requestId, profile, responseMode, Optional.empty(), Optional.empty(), Optional.empty());
        }

        private boolean invokedWith(MdocWalletSimulationService.SimulateRequest request) {
            return lastRequest != null && lastRequest.requestId().equals(request.requestId());
        }
    }
}
