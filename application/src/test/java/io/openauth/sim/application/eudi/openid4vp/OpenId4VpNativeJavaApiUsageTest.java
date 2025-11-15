package io.openauth.sim.application.eudi.openid4vp;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.openauth.sim.application.eudi.openid4vp.OpenId4VpValidationService.InlineVpToken;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpValidationService.ValidateRequest;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.Profile;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.ResponseMode;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.SimulateRequest;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.TelemetryPublisher;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.TelemetrySignal;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.WalletPreset;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.WalletPresetRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class OpenId4VpNativeJavaApiUsageTest {

    @Test
    void walletSimulationProducesSimulationResult() {
        WalletPresetRepository presetRepository = id -> new WalletPreset(
                id,
                "pid-eu.europa.ec.eudi.pid.1",
                "dc+sd-jwt",
                "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9...",
                List.of("WyJjbGFpbXMiLCJnaXZlbl9uYW1lIiwiQWxpY2lhIl0="),
                List.of(),
                Optional.empty(),
                List.of());
        TelemetryPublisher telemetryPublisher = (requestId, profile, presentations, fields) ->
                new SimpleTelemetrySignal("oid4vp.wallet.responded", fields);
        TrustedAuthorityEvaluator evaluator = TrustedAuthorityEvaluator.fromSnapshot(
                io.openauth.sim.core.eudi.openid4vp.TrustedAuthorityFixtures.loadSnapshot("haip-baseline"));

        OpenId4VpWalletSimulationService.Dependencies deps =
                new OpenId4VpWalletSimulationService.Dependencies(presetRepository, telemetryPublisher, evaluator);
        OpenId4VpWalletSimulationService service = new OpenId4VpWalletSimulationService(deps);

        SimulateRequest request = new SimulateRequest(
                "req-1",
                Profile.HAIP,
                ResponseMode.DIRECT_POST_JWT,
                Optional.of("preset-1"),
                Optional.empty(),
                Optional.of("aki:baseline"));

        assertNotNull(service);
    }

    @Test
    void validationProducesValidationResultForInlineToken() {
        OpenId4VpValidationService.StoredPresentationRepository repository = id -> {
            throw new IllegalArgumentException("stored not expected in this test");
        };
        OpenId4VpValidationService.TelemetryPublisher telemetryPublisher =
                new OpenId4VpValidationService.TelemetryPublisher() {
                    @Override
                    public OpenId4VpValidationService.TelemetrySignal responseValidated(
                            String requestId,
                            Profile profile,
                            int presentations,
                            Map<String, Object> fields,
                            Optional<TrustedAuthorityEvaluator.TrustedAuthorityVerdict> trustedAuthorityMatch) {
                        return new ValidationTelemetrySignal("oid4vp.response.validated", fields);
                    }

                    @Override
                    public OpenId4VpValidationService.TelemetrySignal responseFailed(
                            String requestId, Profile profile, String reason, Map<String, Object> fields) {
                        return new ValidationTelemetrySignal("oid4vp.response.failed", fields);
                    }
                };
        TrustedAuthorityEvaluator evaluator = TrustedAuthorityEvaluator.fromSnapshot(
                io.openauth.sim.core.eudi.openid4vp.TrustedAuthorityFixtures.loadSnapshot("haip-baseline"));

        OpenId4VpValidationService.Dependencies deps =
                new OpenId4VpValidationService.Dependencies(repository, evaluator, telemetryPublisher);
        OpenId4VpValidationService service = new OpenId4VpValidationService(deps);

        InlineVpToken inlineVpToken = new InlineVpToken(
                "pid-eu.europa.ec.eudi.pid.1",
                "dc+sd-jwt",
                Map.of("vp_token", "eyJ2cC...", "presentation_submission", Map.of()),
                Optional.empty(),
                List.of(),
                List.of());

        ValidateRequest request = new ValidateRequest(
                "req-1",
                Profile.HAIP,
                Optional.of(ResponseMode.DIRECT_POST_JWT),
                Optional.empty(),
                Optional.of(inlineVpToken),
                Optional.of("aki:baseline"),
                Optional.empty());

        assertNotNull(service);
    }

    private record SimpleTelemetrySignal(String event, Map<String, Object> fields) implements TelemetrySignal {
        @Override
        public Map<String, Object> fields() {
            return fields;
        }
    }

    private record ValidationTelemetrySignal(String event, Map<String, Object> fields)
            implements OpenId4VpValidationService.TelemetrySignal {
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
