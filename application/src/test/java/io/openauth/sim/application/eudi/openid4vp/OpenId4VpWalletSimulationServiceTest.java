package io.openauth.sim.application.eudi.openid4vp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.json.SimpleJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Red tests for the SD-JWT wallet simulation service (Feature 040, task T4004). */
final class OpenId4VpWalletSimulationServiceTest {

    private static final Path FIXTURE_ROOT =
            Path.of("docs", "test-vectors", "eudiw", "openid4vp", "fixtures", "synthetic", "sdjwt-vc");
    private static final String PRESET_ID = "pid-haip-baseline";
    private static final String TRUSTED_AUTHORITY_POLICY = "aki:s9tIpP7qrS9=";

    @Test
    void presetWalletProducesDeterministicSdJwtPresentation() throws IOException, NoSuchAlgorithmException {
        FixtureWalletPresetRepository presets = new FixtureWalletPresetRepository();
        RecordingTelemetry telemetry = new RecordingTelemetry();

        OpenId4VpWalletSimulationService service = new OpenId4VpWalletSimulationService(
                new OpenId4VpWalletSimulationService.Dependencies(presets, telemetry));

        OpenId4VpWalletSimulationService.SimulateRequest request = new OpenId4VpWalletSimulationService.SimulateRequest(
                "HAIP-000001",
                OpenId4VpWalletSimulationService.Profile.HAIP,
                OpenId4VpWalletSimulationService.ResponseMode.DIRECT_POST_JWT,
                Optional.of(PRESET_ID),
                Optional.empty(),
                Optional.of(TRUSTED_AUTHORITY_POLICY));

        OpenId4VpWalletSimulationService.SimulationResult result = service.simulate(request);

        assertEquals("HAIP-000001", result.requestId());
        assertEquals(OpenId4VpWalletSimulationService.Status.SUCCESS, result.status());
        assertEquals(OpenId4VpWalletSimulationService.Profile.HAIP, result.profile());
        assertEquals(OpenId4VpWalletSimulationService.ResponseMode.DIRECT_POST_JWT, result.responseMode());

        List<OpenId4VpWalletSimulationService.Presentation> presentations = result.presentations();
        assertEquals(1, presentations.size());
        OpenId4VpWalletSimulationService.Presentation presentation = presentations.get(0);
        assertEquals("pid-haip-baseline", presentation.credentialId());
        assertEquals("dc+sd-jwt", presentation.format());
        assertTrue(presentation.holderBinding());
        assertEquals(Optional.of(TRUSTED_AUTHORITY_POLICY), presentation.trustedAuthorityMatch());

        OpenId4VpWalletSimulationService.VpToken vpToken = presentation.vpToken();
        assertEquals(presets.compactSdJwt(), vpToken.vpToken());
        assertEquals("pid-haip-baseline", vpToken.presentationSubmission().get("presentation_definition_id"));

        List<String> expectedDisclosureHashes = presets.disclosureHashes();
        assertEquals(expectedDisclosureHashes, presentation.disclosureHashes());

        OpenId4VpWalletSimulationService.Trace trace = result.trace();
        assertEquals(presets.expectedVpTokenHash(), trace.vpTokenHash());
        assertEquals(Optional.of(presets.expectedKbJwtHash()), trace.kbJwtHash());
        assertEquals(expectedDisclosureHashes, trace.disclosureHashes());

        OpenId4VpWalletSimulationService.TelemetrySignal telemetrySignal = result.telemetry();
        assertEquals("oid4vp.wallet.responded", telemetrySignal.event());
        assertEquals("HAIP-000001", telemetry.lastRequestId);
        assertEquals(OpenId4VpWalletSimulationService.Profile.HAIP, telemetry.lastProfile);
        assertEquals(1, telemetry.lastPresentationCount);
    }

    @Test
    void inlineSdJwtOverridesPresetPayloadAndOmitsKeyBindingJwt() throws NoSuchAlgorithmException {
        FixtureWalletPresetRepository presets = new FixtureWalletPresetRepository();
        RecordingTelemetry telemetry = new RecordingTelemetry();

        OpenId4VpWalletSimulationService service = new OpenId4VpWalletSimulationService(
                new OpenId4VpWalletSimulationService.Dependencies(presets, telemetry));

        String inlineSdJwt =
                "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2N1c3RvbS5pc3N1ZXIuZXhhbXBsZS9zcGVjIiwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsImN1c3RvbS12YyJdfX0.c2lnLWlubGluZS1zaWduYXR1cmU";
        String inlineDisclosureJson = "[\"salt-inline\",\"vc.credentialSubject.birthdate\",\"2000-01-01\"]";
        String inlineDisclosure = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(inlineDisclosureJson.getBytes(StandardCharsets.UTF_8));
        OpenId4VpWalletSimulationService.InlineSdJwt inline = new OpenId4VpWalletSimulationService.InlineSdJwt(
                inlineSdJwt, List.of(inlineDisclosure), Optional.empty());

        OpenId4VpWalletSimulationService.SimulateRequest request = new OpenId4VpWalletSimulationService.SimulateRequest(
                "BASELINE-000001",
                OpenId4VpWalletSimulationService.Profile.BASELINE,
                OpenId4VpWalletSimulationService.ResponseMode.FRAGMENT,
                Optional.of(PRESET_ID),
                Optional.of(inline),
                Optional.empty());

        OpenId4VpWalletSimulationService.SimulationResult result = service.simulate(request);

        assertEquals("BASELINE-000001", result.requestId());
        assertEquals(OpenId4VpWalletSimulationService.Status.SUCCESS, result.status());
        assertEquals(OpenId4VpWalletSimulationService.Profile.BASELINE, result.profile());
        assertEquals(OpenId4VpWalletSimulationService.ResponseMode.FRAGMENT, result.responseMode());

        OpenId4VpWalletSimulationService.Presentation presentation =
                result.presentations().get(0);
        assertEquals("pid-haip-baseline", presentation.credentialId());
        assertEquals("dc+sd-jwt", presentation.format());
        assertFalse(presentation.holderBinding());
        assertTrue(presentation.trustedAuthorityMatch().isEmpty());
        assertEquals(inlineSdJwt, presentation.vpToken().vpToken());

        List<String> inlineDisclosureHashes = List.of("sha-256:" + sha256Hex(inlineDisclosure));
        assertEquals(inlineDisclosureHashes, presentation.disclosureHashes());
        assertEquals(inlineDisclosureHashes, result.trace().disclosureHashes());
        assertEquals(sha256Hex(inlineSdJwt), result.trace().vpTokenHash());
        assertTrue(result.trace().kbJwtHash().isEmpty());
    }

    @Test
    void telemetryPublisherReceivesWalletResponseEvent() {
        FixtureWalletPresetRepository presets = new FixtureWalletPresetRepository();
        RecordingTelemetry telemetry = new RecordingTelemetry();

        OpenId4VpWalletSimulationService service = new OpenId4VpWalletSimulationService(
                new OpenId4VpWalletSimulationService.Dependencies(presets, telemetry));

        OpenId4VpWalletSimulationService.SimulateRequest request = new OpenId4VpWalletSimulationService.SimulateRequest(
                "HAIP-TRACE-01",
                OpenId4VpWalletSimulationService.Profile.HAIP,
                OpenId4VpWalletSimulationService.ResponseMode.DIRECT_POST,
                Optional.of(PRESET_ID),
                Optional.empty(),
                Optional.of(TRUSTED_AUTHORITY_POLICY));

        try {
            service.simulate(request);
        } catch (UnsupportedOperationException ignored) {
            // Expected until implementation lands; ensure telemetry contract still recorded.
        }

        assertEquals("HAIP-TRACE-01", telemetry.lastRequestId);
        assertEquals(OpenId4VpWalletSimulationService.Profile.HAIP, telemetry.lastProfile);
    }

    private static String sha256Hex(String value) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte b : hash) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private static final class FixtureWalletPresetRepository
            implements OpenId4VpWalletSimulationService.WalletPresetRepository {

        private final Map<String, Object> digests;
        private final List<String> disclosureHashes;
        private final List<String> disclosures;
        private final String compactSdJwt;
        private final String compactKbJwt;

        private FixtureWalletPresetRepository() {
            try {
                Path fixtureDir = resolveFixture(FIXTURE_ROOT.resolve(PRESET_ID));
                this.digests = readJson(fixtureDir.resolve("digests.json"));
                this.disclosureHashes = extractDisclosureHashes();
                this.disclosures = loadDisclosures(readString(fixtureDir.resolve("disclosures.json")));
                this.compactSdJwt = readString(fixtureDir.resolve("sdjwt.txt"));
                this.compactKbJwt = buildCompactKbJwt(fixtureDir.resolve("kb-jwt.json"));
            } catch (IOException e) {
                throw new IllegalStateException("Unable to load SD-JWT fixtures", e);
            }
        }

        @Override
        public OpenId4VpWalletSimulationService.WalletPreset load(String presetId) {
            if (!PRESET_ID.equals(presetId)) {
                throw new IllegalArgumentException("Unknown preset: " + presetId);
            }
            return new OpenId4VpWalletSimulationService.WalletPreset(
                    PRESET_ID,
                    "pid-haip-baseline",
                    "dc+sd-jwt",
                    compactSdJwt,
                    disclosures,
                    disclosureHashes,
                    Optional.of(compactKbJwt),
                    List.of(TRUSTED_AUTHORITY_POLICY));
        }

        private List<String> loadDisclosures(String json) {
            Object parsed = SimpleJson.parse(json);
            if (!(parsed instanceof List<?> list)) {
                throw new IllegalStateException("disclosures.json must contain an array");
            }
            List<String> encoded = new ArrayList<>();
            for (Object entry : list) {
                if (!(entry instanceof List<?> tuple) || tuple.size() != 3) {
                    throw new IllegalStateException("Disclosure tuple must contain three entries");
                }
                encoded.add(encodeDisclosure(tuple));
            }
            return encoded;
        }

        private String encodeDisclosure(List<?> tuple) {
            String json = "[" + quote(String.valueOf(tuple.get(0)))
                    + "," + quote(String.valueOf(tuple.get(1)))
                    + "," + quote(String.valueOf(tuple.get(2))) + "]";
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        }

        private String quote(String value) {
            return "\"" + value.replace("\"", "\\\"") + "\"";
        }

        private List<String> extractDisclosureHashes() {
            List<String> hashes = new ArrayList<>();
            for (Object value : digests.values()) {
                hashes.add(String.valueOf(value));
            }
            return hashes;
        }

        private String buildCompactKbJwt(Path path) throws IOException {
            Map<String, Object> json = readJson(path);
            String protectedHeader = (String) json.get("protected");
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) json.get("payload");
            String payloadJson = toJson(payload);
            String payloadEncoded = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signature = (String) json.get("signature");
            return protectedHeader + "." + payloadEncoded + "." + signature;
        }

        private Map<String, Object> readJson(Path path) throws IOException {
            Object parsed = SimpleJson.parse(readString(path));
            if (!(parsed instanceof Map<?, ?> map)) {
                throw new IllegalStateException("Expected JSON object at " + path);
            }
            Map<String, Object> ordered = new LinkedHashMap<>();
            map.forEach((key, value) -> ordered.put(String.valueOf(key), value));
            return ordered;
        }

        private String readString(Path path) throws IOException {
            return Files.readString(resolveFixture(path), StandardCharsets.UTF_8)
                    .trim();
        }

        private String toJson(Object value) {
            if (value instanceof Map<?, ?> map) {
                StringBuilder builder = new StringBuilder();
                builder.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!first) {
                        builder.append(',');
                    }
                    first = false;
                    builder.append(quote(String.valueOf(entry.getKey())));
                    builder.append(':');
                    builder.append(toJson(entry.getValue()));
                }
                builder.append('}');
                return builder.toString();
            }
            if (value instanceof List<?> list) {
                StringBuilder builder = new StringBuilder();
                builder.append('[');
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        builder.append(',');
                    }
                    builder.append(toJson(list.get(i)));
                }
                builder.append(']');
                return builder.toString();
            }
            if (value instanceof String string) {
                return quote(string);
            }
            if (value == null) {
                return "null";
            }
            if (value instanceof Boolean bool) {
                return bool.toString();
            }
            return String.valueOf(value);
        }

        String compactSdJwt() {
            return compactSdJwt;
        }

        String expectedVpTokenHash() throws NoSuchAlgorithmException {
            return sha256Hex(compactSdJwt);
        }

        String expectedKbJwtHash() throws NoSuchAlgorithmException {
            return sha256Hex(compactKbJwt);
        }

        List<String> disclosureHashes() {
            return disclosureHashes;
        }
    }

    private static final class RecordingTelemetry implements OpenId4VpWalletSimulationService.TelemetryPublisher {

        private String lastRequestId;
        private OpenId4VpWalletSimulationService.Profile lastProfile;
        private int lastPresentationCount;
        private OpenId4VpWalletSimulationService.TelemetrySignal lastSignal;

        @Override
        public OpenId4VpWalletSimulationService.TelemetrySignal walletResponded(
                String requestId, OpenId4VpWalletSimulationService.Profile profile, int presentationCount) {
            this.lastRequestId = requestId;
            this.lastProfile = profile;
            this.lastPresentationCount = presentationCount;
            this.lastSignal = new SimpleTelemetrySignal(
                    "oid4vp.wallet.responded", Map.of("profile", profile.name(), "presentations", presentationCount));
            return lastSignal;
        }
    }

    private record SimpleTelemetrySignal(String event, Map<String, Object> fields)
            implements OpenId4VpWalletSimulationService.TelemetrySignal {

        @Override
        public String event() {
            return event;
        }

        @Override
        public Map<String, Object> fields() {
            return fields;
        }
    }

    private static Path resolveFixture(Path relative) {
        Path direct = Path.of("").resolve(relative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path workspaceRoot = Path.of("..").resolve(relative);
        if (Files.exists(workspaceRoot)) {
            return workspaceRoot;
        }
        throw new IllegalStateException("Fixture not found: " + relative);
    }
}
