package io.openauth.sim.application.contract.eudi.openid4vp;

import io.openauth.sim.application.contract.CanonicalFacadeResult;
import io.openauth.sim.application.contract.CanonicalScenario;
import io.openauth.sim.application.contract.ScenarioEnvironment;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpAuthorizationRequestService;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpValidationService;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService;
import java.util.List;
import java.util.Optional;

public final class OpenId4VpCanonicalScenarios {

    private OpenId4VpCanonicalScenarios() {}

    public static List<CanonicalScenario> scenarios(ScenarioEnvironment env) {
        OpenId4VpAuthorizationRequestService.CreateRequest createRequest =
                new OpenId4VpAuthorizationRequestService.CreateRequest(
                        OpenId4VpAuthorizationRequestService.Profile.HAIP,
                        OpenId4VpAuthorizationRequestService.ResponseMode.DIRECT_POST_JWT,
                        Optional.of("pid-haip-baseline"),
                        Optional.empty(),
                        true,
                        true,
                        false);

        CanonicalScenario requestCreate = new CanonicalScenario(
                "S-006-CF-01-request-create",
                CanonicalScenario.Protocol.EUDIW_OPENID4VP,
                CanonicalScenario.Kind.EVALUATE_INLINE,
                createRequest,
                new CanonicalFacadeResult(true, "success", null, null, null, null, null, false, true));

        OpenId4VpWalletSimulationService.SimulateRequest walletSimulateRequest =
                new OpenId4VpWalletSimulationService.SimulateRequest(
                        "REQ-7K3D",
                        OpenId4VpWalletSimulationService.Profile.HAIP,
                        OpenId4VpWalletSimulationService.ResponseMode.DIRECT_POST_JWT,
                        Optional.of("pid-haip-baseline"),
                        Optional.empty(),
                        Optional.of("aki:s9tIpP7qrS9="));

        CanonicalScenario walletSimulate = new CanonicalScenario(
                "S-006-CF-02-wallet-simulate",
                CanonicalScenario.Protocol.EUDIW_OPENID4VP,
                CanonicalScenario.Kind.EVALUATE_STORED,
                walletSimulateRequest,
                new CanonicalFacadeResult(true, "success", null, null, null, null, null, false, true));

        OpenId4VpValidationService.ValidateRequest validateRequest = new OpenId4VpValidationService.ValidateRequest(
                "HAIP-0001",
                OpenId4VpWalletSimulationService.Profile.HAIP,
                Optional.of(OpenId4VpWalletSimulationService.ResponseMode.DIRECT_POST_JWT),
                Optional.of("pid-haip-baseline"),
                Optional.empty(),
                Optional.of("aki:s9tIpP7qrS9="),
                Optional.empty());

        CanonicalScenario validateSuccess = new CanonicalScenario(
                "S-006-CF-03-validate-success",
                CanonicalScenario.Protocol.EUDIW_OPENID4VP,
                CanonicalScenario.Kind.REPLAY_STORED,
                validateRequest,
                new CanonicalFacadeResult(true, "success", null, null, null, null, null, false, true));

        OpenId4VpValidationService.ValidateRequest validateFailureRequest =
                new OpenId4VpValidationService.ValidateRequest(
                        "HAIP-0002",
                        OpenId4VpWalletSimulationService.Profile.HAIP,
                        Optional.of(OpenId4VpWalletSimulationService.ResponseMode.DIRECT_POST_JWT),
                        Optional.of("pid-haip-baseline"),
                        Optional.empty(),
                        Optional.of("aki:unknown"),
                        Optional.empty());

        CanonicalScenario validateFailure = new CanonicalScenario(
                "S-006-CF-04-validate-invalid-scope",
                CanonicalScenario.Protocol.EUDIW_OPENID4VP,
                CanonicalScenario.Kind.FAILURE_STORED,
                validateFailureRequest,
                new CanonicalFacadeResult(false, "invalid_scope", null, null, null, null, null, false, true));

        return List.of(requestCreate, walletSimulate, validateSuccess, validateFailure);
    }
}
