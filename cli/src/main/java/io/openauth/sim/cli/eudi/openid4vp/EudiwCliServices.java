package io.openauth.sim.cli.eudi.openid4vp;

import io.openauth.sim.application.eudi.openid4vp.OpenId4VpAuthorizationRequestService;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpValidationService;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService;
import io.openauth.sim.application.eudi.openid4vp.TrustedAuthorityEvaluator;
import io.openauth.sim.application.eudi.openid4vp.fixtures.FixtureDcqlPresetRepository;
import io.openauth.sim.application.eudi.openid4vp.fixtures.FixtureQrCodeEncoder;
import io.openauth.sim.application.eudi.openid4vp.fixtures.FixtureRequestUriFactory;
import io.openauth.sim.application.eudi.openid4vp.fixtures.FixtureSeedSequence;
import io.openauth.sim.application.eudi.openid4vp.fixtures.FixtureStoredPresentationRepository;
import io.openauth.sim.application.eudi.openid4vp.fixtures.FixtureWalletPresetRepository;
import io.openauth.sim.application.eudi.openid4vp.fixtures.Oid4vpTelemetryPublisher;
import io.openauth.sim.core.eudi.openid4vp.TrustedAuthorityFixtures;

final class EudiwCliServices {
    private final FixtureSeedSequence seedSequence;
    private final OpenId4VpAuthorizationRequestService authorizationService;
    private final OpenId4VpWalletSimulationService walletSimulationService;
    private final OpenId4VpValidationService validationService;

    private EudiwCliServices(
            FixtureSeedSequence seedSequence,
            OpenId4VpAuthorizationRequestService authorizationService,
            OpenId4VpWalletSimulationService walletSimulationService,
            OpenId4VpValidationService validationService) {
        this.seedSequence = seedSequence;
        this.authorizationService = authorizationService;
        this.walletSimulationService = walletSimulationService;
        this.validationService = validationService;
    }

    static EudiwCliServices createDefault() {
        FixtureSeedSequence seeds = new FixtureSeedSequence();
        FixtureDcqlPresetRepository dcqlPresets = new FixtureDcqlPresetRepository();
        FixtureWalletPresetRepository walletPresets = new FixtureWalletPresetRepository();
        FixtureStoredPresentationRepository storedPresentations = new FixtureStoredPresentationRepository();
        FixtureRequestUriFactory requestUriFactory = new FixtureRequestUriFactory();
        FixtureQrCodeEncoder qrCodeEncoder = new FixtureQrCodeEncoder();
        Oid4vpTelemetryPublisher telemetryPublisher = new Oid4vpTelemetryPublisher();
        TrustedAuthorityEvaluator evaluator =
                TrustedAuthorityEvaluator.fromSnapshot(TrustedAuthorityFixtures.loadSnapshot("haip-baseline"));
        OpenId4VpAuthorizationRequestService authorization =
                new OpenId4VpAuthorizationRequestService(new OpenId4VpAuthorizationRequestService.Dependencies(
                        seeds, dcqlPresets, requestUriFactory, qrCodeEncoder, telemetryPublisher, evaluator));
        OpenId4VpWalletSimulationService wallet = new OpenId4VpWalletSimulationService(
                new OpenId4VpWalletSimulationService.Dependencies(walletPresets, telemetryPublisher, evaluator));
        OpenId4VpValidationService validation = new OpenId4VpValidationService(
                new OpenId4VpValidationService.Dependencies(storedPresentations, evaluator, telemetryPublisher));
        return new EudiwCliServices(seeds, authorization, wallet, validation);
    }

    FixtureSeedSequence seedSequence() {
        return seedSequence;
    }

    OpenId4VpAuthorizationRequestService authorizationService() {
        return authorizationService;
    }

    OpenId4VpWalletSimulationService walletSimulationService() {
        return walletSimulationService;
    }

    OpenId4VpValidationService validationService() {
        return validationService;
    }
}
