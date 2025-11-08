package io.openauth.sim.application.eudi.openid4vp.fixtures;

import io.openauth.sim.application.eudi.openid4vp.OpenId4VpAuthorizationRequestService;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpValidationService;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService;
import io.openauth.sim.application.eudi.openid4vp.TrustedAuthorityEvaluator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public final class Oid4vpTelemetryPublisher
        implements OpenId4VpAuthorizationRequestService.TelemetryPublisher,
                OpenId4VpWalletSimulationService.TelemetryPublisher,
                OpenId4VpValidationService.TelemetryPublisher {

    private static final Logger LOGGER = Logger.getLogger("io.openauth.sim.telemetry.oid4vp");

    @Override
    public OpenId4VpAuthorizationRequestService.TelemetrySignal requestCreated(
            String requestId,
            OpenId4VpAuthorizationRequestService.Profile profile,
            OpenId4VpAuthorizationRequestService.ResponseMode responseMode,
            boolean haipMode,
            Map<String, Object> fields) {
        Map<String, Object> payload = new LinkedHashMap<>(fields);
        payload.put("requestId", requestId);
        payload.put("haipMode", haipMode);
        payload.put("telemetryId", nextTelemetryId());
        return emit("oid4vp.request.created", payload);
    }

    @Override
    public OpenId4VpWalletSimulationService.TelemetrySignal walletResponded(
            String requestId,
            OpenId4VpWalletSimulationService.Profile profile,
            int presentations,
            Map<String, Object> fields) {
        Map<String, Object> payload = new LinkedHashMap<>(fields);
        payload.put("requestId", requestId);
        payload.put("presentations", presentations);
        payload.put("telemetryId", nextTelemetryId());
        return emit("oid4vp.wallet.responded", payload);
    }

    @Override
    public OpenId4VpValidationService.TelemetrySignal responseValidated(
            String requestId,
            OpenId4VpWalletSimulationService.Profile profile,
            int presentations,
            Map<String, Object> fields,
            java.util.Optional<TrustedAuthorityEvaluator.TrustedAuthorityVerdict> trustedAuthorityMatch) {
        Map<String, Object> payload = new LinkedHashMap<>(fields);
        payload.put("requestId", requestId);
        payload.put("presentations", presentations);
        payload.put("telemetryId", nextTelemetryId());
        trustedAuthorityMatch.ifPresent(match -> payload.put("trustedAuthorityMatch", match.policy()));
        return emit("oid4vp.response.validated", payload);
    }

    @Override
    public OpenId4VpValidationService.TelemetrySignal responseFailed(
            String requestId,
            OpenId4VpWalletSimulationService.Profile profile,
            String reason,
            Map<String, Object> fields) {
        Map<String, Object> payload = new LinkedHashMap<>(fields);
        payload.put("requestId", requestId);
        payload.put("telemetryId", nextTelemetryId());
        payload.put("reason", reason);
        return emit("oid4vp.response.failed", payload);
    }

    private static DefaultTelemetrySignal emit(String event, Map<String, Object> fields) {
        LOGGER.fine(() -> event + " " + fields);
        return new DefaultTelemetrySignal(event, Map.copyOf(fields));
    }

    private static String nextTelemetryId() {
        return "oid4vp-" + UUID.randomUUID();
    }
}
