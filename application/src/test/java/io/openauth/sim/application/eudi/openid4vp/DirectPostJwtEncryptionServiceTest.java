package io.openauth.sim.application.eudi.openid4vp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.eudi.openid4vp.DirectPostJwtEncryptionService.DecryptRequest;
import io.openauth.sim.application.eudi.openid4vp.DirectPostJwtEncryptionService.Dependencies;
import io.openauth.sim.application.eudi.openid4vp.DirectPostJwtEncryptionService.EncryptRequest;
import io.openauth.sim.application.eudi.openid4vp.DirectPostJwtEncryptionService.EncryptResult;
import io.openauth.sim.application.eudi.openid4vp.DirectPostJwtEncryptionService.EncryptionKeyMaterial;
import io.openauth.sim.application.eudi.openid4vp.DirectPostJwtEncryptionService.EncryptionKeyRepository;
import io.openauth.sim.application.eudi.openid4vp.DirectPostJwtEncryptionService.EncryptionTelemetryPublisher;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.Profile;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.ResponseMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Red tests for Feature 040 S4 direct_post.jwt encryption (task T4010). */
final class DirectPostJwtEncryptionServiceTest {

    @Test
    void haipDirectPostJwtEncryptsPayloadAndCapturesLatency() {
        FixtureEncryptionKeys keys = new FixtureEncryptionKeys();
        RecordingTelemetry telemetry = new RecordingTelemetry();
        IncrementingClock clock = new IncrementingClock();

        DirectPostJwtEncryptionService service =
                new DirectPostJwtEncryptionService(new Dependencies(keys, telemetry, clock));

        Map<String, Object> payload = samplePayload();
        EncryptRequest request = new EncryptRequest(
                "HAIP-ENC-001",
                Profile.HAIP,
                ResponseMode.DIRECT_POST_JWT,
                "https://verifier.example/callback",
                payload);

        EncryptResult result = service.encrypt(request);

        assertEquals("HAIP-ENC-001", result.requestId());
        assertTrue(result.encryptionRequired(), "HAIP + direct_post.jwt must enforce encryption");
        assertTrue(result.directPostJwt().isPresent(), "direct_post.jwt payload should be present");
        assertTrue(result.algorithm().isPresent(), "encryption algorithm metadata is required");
        assertEquals("ECDH-ES+A128GCM", result.algorithm().orElseThrow());
        assertTrue(result.latencyMillis() > 0, "latency should reflect measured duration");
        assertEquals(result.latencyMillis(), telemetry.lastLatencyMillis);
        assertTrue(telemetry.lastEnforced);
        assertEquals(Profile.HAIP, telemetry.lastProfile);
        assertEquals(ResponseMode.DIRECT_POST_JWT, telemetry.lastResponseMode);
        assertEquals("HAIP-ENC-001", telemetry.lastRequestId());

        DirectPostJwtEncryptionService.DecryptResult decrypted = service.decrypt(new DecryptRequest(
                "HAIP-ENC-001", Profile.HAIP, result.directPostJwt().orElseThrow()));
        assertEquals(payload, decrypted.payload(), "round-trip decrypt should reproduce the original payload");
    }

    @Test
    void baselineProfileSkipsEncryptionWhenNotEnforced() {
        FixtureEncryptionKeys keys = new FixtureEncryptionKeys();
        RecordingTelemetry telemetry = new RecordingTelemetry();
        IncrementingClock clock = new IncrementingClock();

        DirectPostJwtEncryptionService service =
                new DirectPostJwtEncryptionService(new Dependencies(keys, telemetry, clock));

        EncryptRequest request = new EncryptRequest(
                "BASELINE-001",
                Profile.BASELINE,
                ResponseMode.DIRECT_POST,
                "https://verifier.example/callback",
                samplePayload());

        EncryptResult result = service.encrypt(request);

        assertEquals("BASELINE-001", result.requestId());
        assertFalse(result.encryptionRequired(), "Baseline profile should not force encryption");
        assertTrue(result.directPostJwt().isEmpty(), "non-HAIP requests should bypass encryption payloads");
        assertEquals(0L, result.latencyMillis());
        assertFalse(telemetry.lastEnforced);
        assertEquals("BASELINE-001", telemetry.lastRequestId());
    }

    @Test
    void missingKeyMaterialRaisesInvalidRequestProblemDetails() {
        RecordingTelemetry telemetry = new RecordingTelemetry();
        IncrementingClock clock = new IncrementingClock();
        DirectPostJwtEncryptionService service =
                new DirectPostJwtEncryptionService(new Dependencies(new MissingKeyRepository(), telemetry, clock));

        EncryptRequest encryptRequest = new EncryptRequest(
                "HAIP-ERR-001",
                Profile.HAIP,
                ResponseMode.DIRECT_POST_JWT,
                "https://verifier.example/callback",
                samplePayload());

        assertThrows(
                Oid4vpValidationException.class,
                () -> service.encrypt(encryptRequest),
                "Missing verifier keys must surface invalid_request problem details");

        DirectPostJwtEncryptionService.DecryptRequest decryptRequest =
                new DecryptRequest("HAIP-ERR-001", Profile.HAIP, "ey.invalid.direct.post.jwt");

        assertThrows(
                Oid4vpValidationException.class,
                () -> service.decrypt(decryptRequest),
                "Decrypting without key material must also raise invalid_request");
    }

    private static Map<String, Object> samplePayload() {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("id", "pid-haip-baseline");
        descriptor.put("format", "dc+sd-jwt");
        descriptor.put("path", "$.presentation_submission.descriptor_map[0]");

        Map<String, Object> submission = new LinkedHashMap<>();
        submission.put("definition_id", "pid-haip-baseline");
        submission.put("descriptor_map", List.of(descriptor));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vp_token", "fixture-sd-jwt-token");
        payload.put("presentation_submission", submission);
        payload.put("state", "STATE-123");
        return payload;
    }

    private static final class FixtureEncryptionKeys implements EncryptionKeyRepository {
        private final String verifierJwk;

        private FixtureEncryptionKeys() {
            this.verifierJwk = readFixture(Path.of(
                    "docs",
                    "test-vectors",
                    "eudiw",
                    "openid4vp",
                    "keys",
                    "encryption",
                    "direct-post-jwt-verifier.jwk.json"));
        }

        @Override
        public EncryptionKeyMaterial load(Profile profile) {
            return new EncryptionKeyMaterial("haip-direct-post-jwt", verifierJwk, verifierJwk);
        }

        private static String readFixture(Path path) {
            Path resolved = resolve(path);
            try {
                return Files.readString(resolved, StandardCharsets.UTF_8).trim();
            } catch (IOException e) {
                throw new IllegalStateException("Unable to read encryption key fixture", e);
            }
        }
    }

    private static final class MissingKeyRepository implements EncryptionKeyRepository {
        @Override
        public EncryptionKeyMaterial load(Profile profile) {
            throw new IllegalStateException("Missing key material for profile " + profile);
        }
    }

    private static final class RecordingTelemetry implements EncryptionTelemetryPublisher {
        private String lastRequestId;
        private boolean lastEnforced;
        private long lastLatencyMillis;
        private Profile lastProfile;
        private ResponseMode lastResponseMode;

        @Override
        public void recordEncryption(
                String requestId, boolean enforced, long latencyMillis, Profile profile, ResponseMode responseMode) {
            this.lastRequestId = requestId;
            this.lastEnforced = enforced;
            this.lastLatencyMillis = latencyMillis;
            this.lastProfile = profile;
            this.lastResponseMode = responseMode;
        }

        String lastRequestId() {
            return lastRequestId;
        }
    }

    private static final class IncrementingClock extends Clock {
        private Instant current = Instant.parse("2025-11-07T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            Instant now = current;
            current = current.plusMillis(5);
            return now;
        }
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
        throw new IllegalStateException("Fixture not found: " + relative);
    }
}
