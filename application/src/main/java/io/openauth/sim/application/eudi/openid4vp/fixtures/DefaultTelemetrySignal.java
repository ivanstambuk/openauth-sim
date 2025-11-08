package io.openauth.sim.application.eudi.openid4vp.fixtures;

import io.openauth.sim.application.eudi.openid4vp.OpenId4VpAuthorizationRequestService;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpFixtureIngestionService;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpValidationService;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService;
import java.util.Map;

record DefaultTelemetrySignal(String event, Map<String, Object> fields)
        implements OpenId4VpAuthorizationRequestService.TelemetrySignal,
                OpenId4VpWalletSimulationService.TelemetrySignal,
                OpenId4VpValidationService.TelemetrySignal,
                OpenId4VpFixtureIngestionService.TelemetrySignal {}
