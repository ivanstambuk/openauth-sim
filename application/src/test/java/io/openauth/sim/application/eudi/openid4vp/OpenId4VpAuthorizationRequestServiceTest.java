package io.openauth.sim.application.eudi.openid4vp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class OpenId4VpAuthorizationRequestServiceTest {

    private static final String PRESET_ID = "pid-haip-baseline";
    private static final String PRESENTATION_DEFINITION = "{\"dcql\":\"pid-haip-baseline\"}";
    private static final String CLIENT_ID = "x509_hash:fixture";
    private static final OpenId4VpAuthorizationRequestService.RequestUriFactory REQUEST_URI_FACTORY =
            requestId -> "https://simulator.test/requests/" + requestId;
    private static final OpenId4VpAuthorizationRequestService.QrCodeEncoder QR_CODE_ENCODER =
            payload -> "[QR]" + payload;

    @Test
    void haipProfileGeneratesDeterministicRequestPayload() {
        RecordingTelemetryPublisher telemetry = new RecordingTelemetryPublisher();
        OpenId4VpAuthorizationRequestService service =
                serviceWith(new StubSeedSequence("HAIP-000001", "nonce-abc", "state-xyz"), telemetry);

        OpenId4VpAuthorizationRequestService.CreateRequest request =
                new OpenId4VpAuthorizationRequestService.CreateRequest(
                        OpenId4VpAuthorizationRequestService.Profile.HAIP,
                        OpenId4VpAuthorizationRequestService.ResponseMode.DIRECT_POST_JWT,
                        Optional.of(PRESET_ID),
                        Optional.empty(),
                        true,
                        true,
                        true);

        OpenId4VpAuthorizationRequestService.AuthorizationRequestResult result = service.create(request);

        assertEquals("HAIP-000001", result.requestId());
        assertEquals("nonce-abc", result.authorizationRequest().nonce());
        assertEquals("state-xyz", result.authorizationRequest().state());
        assertEquals("direct_post.jwt", result.authorizationRequest().responseMode());
        assertEquals(CLIENT_ID, result.authorizationRequest().clientId());
        assertEquals(PRESENTATION_DEFINITION, result.authorizationRequest().presentationDefinition());

        assertEquals("HAIP-000001", telemetry.lastRequestId);
        assertEquals(OpenId4VpAuthorizationRequestService.Profile.HAIP, telemetry.lastProfile);
        assertEquals(OpenId4VpAuthorizationRequestService.ResponseMode.DIRECT_POST_JWT, telemetry.lastResponseMode);
        assertEquals("oid4vp.request.created", telemetry.lastSignal.event());
        assertEquals(true, telemetry.lastHaipMode);
    }

    @Test
    void dcqlOverrideTakesPrecedenceOverPreset() {
        OpenId4VpAuthorizationRequestService service = serviceWith(
                new StubSeedSequence("HAIP-000001", "nonce-abc", "state-xyz"), new RecordingTelemetryPublisher());

        OpenId4VpAuthorizationRequestService.CreateRequest request =
                new OpenId4VpAuthorizationRequestService.CreateRequest(
                        OpenId4VpAuthorizationRequestService.Profile.HAIP,
                        OpenId4VpAuthorizationRequestService.ResponseMode.DIRECT_POST_JWT,
                        Optional.of(PRESET_ID),
                        Optional.of("{\"dcql\":\"override\"}"),
                        true,
                        false,
                        false);

        OpenId4VpAuthorizationRequestService.AuthorizationRequestResult result = service.create(request);

        assertEquals("{\"dcql\":\"override\"}", result.authorizationRequest().presentationDefinition());
    }

    @Test
    void missingDcqlPresetAndOverrideRaisesInvalidRequest() {
        OpenId4VpAuthorizationRequestService service = serviceWith(
                new StubSeedSequence("HAIP-000001", "nonce-abc", "state-xyz"), new RecordingTelemetryPublisher());

        OpenId4VpAuthorizationRequestService.CreateRequest request =
                new OpenId4VpAuthorizationRequestService.CreateRequest(
                        OpenId4VpAuthorizationRequestService.Profile.HAIP,
                        OpenId4VpAuthorizationRequestService.ResponseMode.DIRECT_POST_JWT,
                        Optional.empty(),
                        Optional.empty(),
                        true,
                        false,
                        false);

        assertThrows(IllegalArgumentException.class, () -> service.create(request));
    }

    @Test
    void identicalSeedSourceProducesStableIdentifiers() {
        OpenId4VpAuthorizationRequestService.CreateRequest request =
                new OpenId4VpAuthorizationRequestService.CreateRequest(
                        OpenId4VpAuthorizationRequestService.Profile.HAIP,
                        OpenId4VpAuthorizationRequestService.ResponseMode.DIRECT_POST_JWT,
                        Optional.of(PRESET_ID),
                        Optional.empty(),
                        true,
                        false,
                        false);

        OpenId4VpAuthorizationRequestService.AuthorizationRequestResult first = serviceWith(
                        new StubSeedSequence("HAIP-000001", "nonce-abc", "state-xyz"),
                        new RecordingTelemetryPublisher())
                .create(request);
        OpenId4VpAuthorizationRequestService.AuthorizationRequestResult second = serviceWith(
                        new StubSeedSequence("HAIP-000001", "nonce-abc", "state-xyz"),
                        new RecordingTelemetryPublisher())
                .create(request);

        assertEquals(first.requestId(), second.requestId());
        assertEquals(
                first.authorizationRequest().nonce(),
                second.authorizationRequest().nonce());
        assertEquals(
                first.authorizationRequest().state(),
                second.authorizationRequest().state());
    }

    @Test
    void includeQrRequestsShouldEmitQrAsciiAndRequestUriTelemetry() {
        RecordingTelemetryPublisher telemetry = new RecordingTelemetryPublisher();
        OpenId4VpAuthorizationRequestService service =
                serviceWith(new StubSeedSequence("HAIP-000001", "nonce-abc", "state-xyz"), telemetry);

        OpenId4VpAuthorizationRequestService.CreateRequest request =
                new OpenId4VpAuthorizationRequestService.CreateRequest(
                        OpenId4VpAuthorizationRequestService.Profile.HAIP,
                        OpenId4VpAuthorizationRequestService.ResponseMode.DIRECT_POST_JWT,
                        Optional.of(PRESET_ID),
                        Optional.empty(),
                        true,
                        true,
                        true);

        service.create(request);

        assertNotNull(telemetry.lastSignal, "telemetry event should be recorded");
        Map<String, Object> fields = telemetry.lastSignal.fields();
        assertNotNull(fields.get("qrAscii"), "QR rendering should emit ASCII preview telemetry");
        assertNotNull(fields.get("requestUri"), "Authorization requests should emit request URI telemetry");
    }

    @Test
    void verboseRequestsShouldEmitMaskedAndFullNonceTraceTelemetry() {
        RecordingTelemetryPublisher telemetry = new RecordingTelemetryPublisher();
        OpenId4VpAuthorizationRequestService service =
                serviceWith(new StubSeedSequence("HAIP-000001", "nonce-abc", "state-xyz"), telemetry);

        OpenId4VpAuthorizationRequestService.CreateRequest request =
                new OpenId4VpAuthorizationRequestService.CreateRequest(
                        OpenId4VpAuthorizationRequestService.Profile.HAIP,
                        OpenId4VpAuthorizationRequestService.ResponseMode.DIRECT_POST_JWT,
                        Optional.of(PRESET_ID),
                        Optional.empty(),
                        true,
                        false,
                        true);

        service.create(request);

        Map<String, Object> fields = telemetry.lastSignal.fields();
        assertEquals(
                "******ce-abc",
                fields.get("nonceMasked"),
                "Nonce traces should only expose the trailing six characters");
        assertEquals("nonce-abc", fields.get("nonceFull"));
        assertEquals(
                "******te-xyz",
                fields.get("stateMasked"),
                "State traces should only expose the trailing six characters");
        assertEquals("state-xyz", fields.get("stateFull"));
    }

    @Test
    void trustedAuthorityMetadataShouldPropagateIntoTelemetry() {
        RecordingTelemetryPublisher telemetry = new RecordingTelemetryPublisher();
        OpenId4VpAuthorizationRequestService service =
                serviceWith(new StubSeedSequence("HAIP-000001", "nonce-abc", "state-xyz"), telemetry);

        OpenId4VpAuthorizationRequestService.CreateRequest request =
                new OpenId4VpAuthorizationRequestService.CreateRequest(
                        OpenId4VpAuthorizationRequestService.Profile.HAIP,
                        OpenId4VpAuthorizationRequestService.ResponseMode.DIRECT_POST_JWT,
                        Optional.of(PRESET_ID),
                        Optional.empty(),
                        true,
                        false,
                        false);

        service.create(request);

        Map<String, Object> fields = telemetry.lastSignal.fields();
        assertEquals(List.of("aki:s9tIpP7qrS9="), fields.get("trustedAuthorities"));
    }

    @Test
    void signedInlineRequestsWithoutPresetAreSupported() {
        RecordingTelemetryPublisher telemetry = new RecordingTelemetryPublisher();
        OpenId4VpAuthorizationRequestService service =
                serviceWith(new StubSeedSequence("HAIP-000001", "nonce-abc", "state-xyz"), telemetry);

        OpenId4VpAuthorizationRequestService.CreateRequest request =
                new OpenId4VpAuthorizationRequestService.CreateRequest(
                        OpenId4VpAuthorizationRequestService.Profile.HAIP,
                        OpenId4VpAuthorizationRequestService.ResponseMode.DIRECT_POST_JWT,
                        Optional.empty(),
                        Optional.of(PRESENTATION_DEFINITION),
                        true,
                        false,
                        false);

        assertDoesNotThrow(() -> service.create(request));
    }

    private static OpenId4VpAuthorizationRequestService serviceWith(
            OpenId4VpAuthorizationRequestService.SeedSequence seedSequence, RecordingTelemetryPublisher telemetry) {
        return new OpenId4VpAuthorizationRequestService(new OpenId4VpAuthorizationRequestService.Dependencies(
                seedSequence, new InMemoryPresetRepository(), REQUEST_URI_FACTORY, QR_CODE_ENCODER, telemetry));
    }

    private static final class RecordingTelemetryPublisher
            implements OpenId4VpAuthorizationRequestService.TelemetryPublisher {

        private String lastRequestId;
        private OpenId4VpAuthorizationRequestService.Profile lastProfile;
        private OpenId4VpAuthorizationRequestService.ResponseMode lastResponseMode;
        private boolean lastHaipMode;
        private OpenId4VpAuthorizationRequestService.TelemetrySignal lastSignal;

        @Override
        public OpenId4VpAuthorizationRequestService.TelemetrySignal requestCreated(
                String requestId,
                OpenId4VpAuthorizationRequestService.Profile profile,
                OpenId4VpAuthorizationRequestService.ResponseMode responseMode,
                boolean haipMode,
                Map<String, Object> fields) {
            this.lastRequestId = requestId;
            this.lastProfile = profile;
            this.lastResponseMode = responseMode;
            this.lastHaipMode = haipMode;
            this.lastSignal = new SimpleTelemetrySignal("oid4vp.request.created", Map.copyOf(fields));
            return lastSignal;
        }
    }

    private record StubSeedSequence(String requestId, String nonce, String state)
            implements OpenId4VpAuthorizationRequestService.SeedSequence {

        @Override
        public String nextRequestId() {
            return requestId;
        }

        @Override
        public String nextNonce() {
            return nonce;
        }

        @Override
        public String nextState() {
            return state;
        }
    }

    private static final class InMemoryPresetRepository
            implements OpenId4VpAuthorizationRequestService.DcqlPresetRepository {

        private final Map<String, OpenId4VpAuthorizationRequestService.DcqlPreset> presets = Map.of(
                "pid-haip-baseline",
                new OpenId4VpAuthorizationRequestService.DcqlPreset(
                        "pid-haip-baseline",
                        "x509_hash:fixture",
                        "{\"dcql\":\"pid-haip-baseline\"}",
                        List.of("aki:s9tIpP7qrS9=")));

        @Override
        public OpenId4VpAuthorizationRequestService.DcqlPreset load(String presetId) {
            OpenId4VpAuthorizationRequestService.DcqlPreset preset = presets.get(presetId);
            if (preset == null) {
                throw new IllegalArgumentException("Unknown preset: " + presetId);
            }
            return preset;
        }
    }

    private record SimpleTelemetrySignal(String event, Map<String, Object> fields)
            implements OpenId4VpAuthorizationRequestService.TelemetrySignal {

        @Override
        public String event() {
            return event;
        }

        @Override
        public Map<String, Object> fields() {
            return fields;
        }
    }
}
