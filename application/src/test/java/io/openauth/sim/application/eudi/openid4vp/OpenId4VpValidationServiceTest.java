package io.openauth.sim.application.eudi.openid4vp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.Profile;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.ResponseMode;
import io.openauth.sim.core.eudi.openid4vp.TrustedAuthorityFixtures;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Red tests for the validation-mode entry point (Feature 040, task T4012). */
final class OpenId4VpValidationServiceTest {

    private static final TrustedAuthorityEvaluator TRUSTED_AUTHORITY_EVALUATOR =
            TrustedAuthorityEvaluator.fromSnapshot(TrustedAuthorityFixtures.loadSnapshot("haip-baseline"));

    @Test
    void storedPresentationValidationSucceedsAndEmitsTelemetry() {
        RecordingStoredPresentationRepository repository = new RecordingStoredPresentationRepository();
        RecordingTelemetry telemetry = new RecordingTelemetry();

        OpenId4VpValidationService service = new OpenId4VpValidationService(
                new OpenId4VpValidationService.Dependencies(repository, TRUSTED_AUTHORITY_EVALUATOR, telemetry));

        repository.storePreset(new OpenId4VpValidationService.StoredPresentation(
                "pid-haip-baseline",
                "pid-haip-baseline",
                "dc+sd-jwt",
                Profile.HAIP,
                ResponseMode.DIRECT_POST_JWT,
                Map.of(
                        "vp_token",
                        "eyJ2cC10b2tlbiI6InByZXNldCJ9",
                        "presentation_submission",
                        Map.of("descriptor_map", List.of(Map.of("id", "pid-haip-baseline")))),
                Optional.of("kb-jwt-token"),
                List.of("disclosure-1"),
                List.of("aki:s9tIpP7qrS9="),
                Optional.of("{\"type\":\"pid-haip-baseline\"}")));

        OpenId4VpValidationService.ValidateRequest request = new OpenId4VpValidationService.ValidateRequest(
                "REQ-0001",
                Profile.BASELINE,
                Optional.of(ResponseMode.FRAGMENT),
                Optional.of("pid-haip-baseline"),
                Optional.empty(),
                Optional.of("aki:s9tIpP7qrS9="),
                Optional.empty());

        OpenId4VpValidationService.ValidationResult result = service.validate(request);

        assertEquals("REQ-0001", result.requestId());
        assertEquals(OpenId4VpValidationService.Status.SUCCESS, result.status());
        assertEquals(Profile.HAIP, result.profile());
        assertEquals(ResponseMode.FRAGMENT, result.responseMode());
        assertEquals(1, result.presentations().size());
        OpenId4VpValidationService.Presentation presentation =
                result.presentations().get(0);
        assertEquals("pid-haip-baseline", presentation.credentialId());
        assertEquals("dc+sd-jwt", presentation.format());
        assertTrue(presentation.holderBinding());
        assertEquals(
                "aki:s9tIpP7qrS9=",
                presentation.trustedAuthorityMatch().orElseThrow().policy());
        assertEquals("eyJ2cC10b2tlbiI6InByZXNldCJ9", presentation.vpToken().vpToken());

        OpenId4VpValidationService.Trace trace = result.trace();
        assertEquals(Optional.of("pid-haip-baseline"), trace.walletPresetId());
        assertEquals(Optional.of("{\"type\":\"pid-haip-baseline\"}"), trace.dcqlPreview());
        assertEquals("sha-256:25e71aa5d863fbb34beb41e8a4bc608ee246508d3b278b061f1de60ad74a5fb2", trace.vpTokenHash());
        assertEquals(
                Optional.of("sha-256:dd2092f6f82cb321a63a69cc9e0580cb43a343f1da2f275623bcb3f06bc7747e"),
                trace.kbJwtHash());
        assertEquals(
                List.of("sha-256:d0af4710ab371f1bb3d44cddd741b786ca58ea0bc4efa8dbe5e5e89cd719003a"),
                trace.disclosureHashes());
        assertEquals(1, trace.presentations().size());
        OpenId4VpValidationService.PresentationTrace presentationTrace =
                trace.presentations().get(0);
        assertEquals("pid-haip-baseline", presentationTrace.presentationId());
        assertEquals("dc+sd-jwt", presentationTrace.format());
        assertTrue(presentationTrace.holderBinding());
        assertEquals(
                "sha-256:25e71aa5d863fbb34beb41e8a4bc608ee246508d3b278b061f1de60ad74a5fb2",
                presentationTrace.vpTokenHash());
        assertEquals(
                Optional.of("sha-256:dd2092f6f82cb321a63a69cc9e0580cb43a343f1da2f275623bcb3f06bc7747e"),
                presentationTrace.kbJwtHash());
        assertEquals(
                List.of("sha-256:d0af4710ab371f1bb3d44cddd741b786ca58ea0bc4efa8dbe5e5e89cd719003a"),
                presentationTrace.disclosureHashes());
        assertEquals(
                "aki:s9tIpP7qrS9=",
                presentationTrace.trustedAuthorityMatch().orElseThrow().policy());

        OpenId4VpValidationService.TelemetrySignal signal = result.telemetry();
        assertEquals("oid4vp.response.validated", signal.event());
        assertEquals("REQ-0001", telemetry.lastRequestId);
        assertEquals(Profile.HAIP, telemetry.lastProfile);
        assertEquals(1, telemetry.lastPresentationCount);
        assertEquals("aki:s9tIpP7qrS9=", telemetry.lastTrustedAuthorityMatch.policy());
    }

    @Test
    void inlineVpTokenValidationProvidesDcqlPreview() {
        RecordingStoredPresentationRepository repository = new RecordingStoredPresentationRepository();
        RecordingTelemetry telemetry = new RecordingTelemetry();

        OpenId4VpValidationService service = new OpenId4VpValidationService(
                new OpenId4VpValidationService.Dependencies(repository, TRUSTED_AUTHORITY_EVALUATOR, telemetry));

        Map<String, Object> vpToken = new LinkedHashMap<>();
        vpToken.put("vp_token", "eyJ2cC10b2tlbiI6ImlubGluZSJ9");
        vpToken.put(
                "presentation_submission",
                Map.of("descriptor_map", List.of(Map.of("id", "pid-inline", "format", "dc+sd-jwt"))));
        OpenId4VpValidationService.InlineVpToken inline = new OpenId4VpValidationService.InlineVpToken(
                "pid-inline",
                "dc+sd-jwt",
                vpToken,
                Optional.empty(),
                List.of("disclosure-inline"),
                List.of("aki:s9tIpP7qrS9="));

        OpenId4VpValidationService.ValidateRequest request = new OpenId4VpValidationService.ValidateRequest(
                "REQ-INLINE",
                Profile.BASELINE,
                Optional.of(ResponseMode.DIRECT_POST),
                Optional.empty(),
                Optional.of(inline),
                Optional.of("aki:s9tIpP7qrS9="),
                Optional.of("{\"type\":\"inline\"}"));

        OpenId4VpValidationService.ValidationResult result = service.validate(request);

        assertEquals("REQ-INLINE", result.requestId());
        assertEquals(OpenId4VpValidationService.Status.SUCCESS, result.status());
        assertEquals(Profile.BASELINE, result.profile());
        assertEquals(ResponseMode.DIRECT_POST, result.responseMode());
        assertEquals(1, result.presentations().size());
        OpenId4VpValidationService.Presentation presentation =
                result.presentations().get(0);
        assertEquals("pid-inline", presentation.credentialId());
        assertEquals("dc+sd-jwt", presentation.format());
        assertTrue(presentation.trustedAuthorityMatch().isPresent());
        assertEquals(
                "aki:s9tIpP7qrS9=",
                presentation.trustedAuthorityMatch().orElseThrow().policy());

        OpenId4VpValidationService.Trace trace = result.trace();
        assertTrue(trace.walletPresetId().isEmpty());
        assertEquals(Optional.of("{\"type\":\"inline\"}"), trace.dcqlPreview());
        assertEquals(
                List.of("sha-256:feb9fc6814fc5eb657b00a4567abdfe4815debca1e04260d023856d126fc3667"),
                trace.disclosureHashes());
        assertTrue(trace.kbJwtHash().isEmpty());
        assertEquals(1, trace.presentations().size());
        OpenId4VpValidationService.PresentationTrace inlineTrace =
                trace.presentations().get(0);
        assertEquals("pid-inline", inlineTrace.presentationId());
        assertEquals("dc+sd-jwt", inlineTrace.format());
        assertTrue(inlineTrace.vpTokenHash().startsWith("sha-256:"));
        assertTrue(inlineTrace.trustedAuthorityMatch().isPresent());
        assertFalse(inlineTrace.holderBinding());
        assertTrue(inlineTrace.kbJwtHash().isEmpty());
        assertEquals(
                List.of("sha-256:feb9fc6814fc5eb657b00a4567abdfe4815debca1e04260d023856d126fc3667"),
                inlineTrace.disclosureHashes());
    }

    @Test
    void trustedAuthorityMismatchRaisesInvalidScope() {
        RecordingStoredPresentationRepository repository = new RecordingStoredPresentationRepository();
        RecordingTelemetry telemetry = new RecordingTelemetry();

        OpenId4VpValidationService service = new OpenId4VpValidationService(
                new OpenId4VpValidationService.Dependencies(repository, TRUSTED_AUTHORITY_EVALUATOR, telemetry));

        repository.storePreset(new OpenId4VpValidationService.StoredPresentation(
                "pid-haip-baseline",
                "pid-haip-baseline",
                "dc+sd-jwt",
                Profile.HAIP,
                ResponseMode.DIRECT_POST_JWT,
                Map.of(
                        "vp_token",
                        "eyJ2cC10b2tlbiI6InByZXNldCJ9",
                        "presentation_submission",
                        Map.of("descriptor_map", List.of(Map.of("id", "pid-haip-baseline")))),
                Optional.of("kb-jwt-token"),
                List.of("disclosure-1"),
                List.of("aki:unmatched"),
                Optional.empty()));

        OpenId4VpValidationService.ValidateRequest request = new OpenId4VpValidationService.ValidateRequest(
                "REQ-FAIL-SCOPE",
                Profile.HAIP,
                Optional.empty(),
                Optional.of("pid-haip-baseline"),
                Optional.empty(),
                Optional.of("aki:s9tIpP7qrS9="),
                Optional.empty());

        Oid4vpValidationException exception =
                assertThrows(Oid4vpValidationException.class, () -> service.validate(request));
        assertEquals("invalid_scope", exception.problemDetails().title());
        assertEquals(
                "Trusted Authority policy aki:s9tIpP7qrS9= not satisfied by wallet",
                exception.problemDetails().detail());
        assertEquals("oid4vp.response.failed", telemetry.lastEvent);
        assertEquals("REQ-FAIL-SCOPE", telemetry.lastRequestId);
    }

    @Test
    void missingVpTokenRaisesInvalidPresentation() {
        RecordingStoredPresentationRepository repository = new RecordingStoredPresentationRepository();
        RecordingTelemetry telemetry = new RecordingTelemetry();

        OpenId4VpValidationService service = new OpenId4VpValidationService(
                new OpenId4VpValidationService.Dependencies(repository, TRUSTED_AUTHORITY_EVALUATOR, telemetry));

        Map<String, Object> vpToken = new LinkedHashMap<>();
        vpToken.put("presentation_submission", Map.of("descriptor_map", List.of()));
        OpenId4VpValidationService.InlineVpToken inline = new OpenId4VpValidationService.InlineVpToken(
                "pid-inline", "dc+sd-jwt", vpToken, Optional.empty(), List.of(), List.of());

        OpenId4VpValidationService.ValidateRequest request = new OpenId4VpValidationService.ValidateRequest(
                "REQ-INVALID",
                Profile.BASELINE,
                Optional.empty(),
                Optional.empty(),
                Optional.of(inline),
                Optional.empty(),
                Optional.empty());

        Oid4vpValidationException exception =
                assertThrows(Oid4vpValidationException.class, () -> service.validate(request));
        assertEquals("invalid_presentation", exception.problemDetails().title());
        assertEquals("vp_token field is required", exception.problemDetails().detail());
        assertEquals("oid4vp.response.failed", telemetry.lastEvent);
        assertEquals("REQ-INVALID", telemetry.lastRequestId);
    }

    private static final class RecordingStoredPresentationRepository
            implements OpenId4VpValidationService.StoredPresentationRepository {

        private OpenId4VpValidationService.StoredPresentation preset;

        void storePreset(OpenId4VpValidationService.StoredPresentation preset) {
            this.preset = preset;
        }

        @Override
        public OpenId4VpValidationService.StoredPresentation load(String presentationId) {
            if (this.preset == null || !this.preset.presentationId().equals(presentationId)) {
                throw new IllegalArgumentException("Unknown stored presentation: " + presentationId);
            }
            return this.preset;
        }
    }

    private static final class RecordingTelemetry implements OpenId4VpValidationService.TelemetryPublisher {

        private String lastEvent;
        private String lastRequestId;
        private Profile lastProfile;
        private int lastPresentationCount;
        private OpenId4VpValidationService.TelemetrySignal lastSignal;
        private TrustedAuthorityEvaluator.TrustedAuthorityVerdict lastTrustedAuthorityMatch;

        @Override
        public OpenId4VpValidationService.TelemetrySignal responseValidated(
                String requestId,
                Profile profile,
                int presentations,
                Map<String, Object> fields,
                Optional<TrustedAuthorityEvaluator.TrustedAuthorityVerdict> trustedAuthorityMatch) {
            this.lastEvent = "oid4vp.response.validated";
            this.lastRequestId = requestId;
            this.lastProfile = profile;
            this.lastPresentationCount = presentations;
            this.lastTrustedAuthorityMatch = trustedAuthorityMatch.orElse(null);
            this.lastSignal = new SimpleTelemetrySignal(this.lastEvent, fields);
            return this.lastSignal;
        }

        @Override
        public OpenId4VpValidationService.TelemetrySignal responseFailed(
                String requestId, Profile profile, String reason, Map<String, Object> fields) {
            this.lastEvent = "oid4vp.response.failed";
            this.lastRequestId = requestId;
            this.lastProfile = profile;
            this.lastPresentationCount = 0;
            this.lastSignal = new SimpleTelemetrySignal(this.lastEvent, fields);
            return this.lastSignal;
        }
    }

    private static final class SimpleTelemetrySignal implements OpenId4VpValidationService.TelemetrySignal {
        private final String event;
        private final Map<String, Object> fields;

        private SimpleTelemetrySignal(String event, Map<String, Object> fields) {
            this.event = event;
            this.fields = fields;
        }

        @Override
        public String event() {
            return this.event;
        }

        @Override
        public Map<String, Object> fields() {
            return this.fields;
        }
    }
}
