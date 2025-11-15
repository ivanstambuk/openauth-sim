package io.openauth.sim.rest.eudi.openid4vp;

import io.openauth.sim.application.eudi.openid4vp.OpenId4VpAuthorizationRequestService;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpFixtureIngestionService;
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
import io.openauth.sim.application.eudi.openid4vp.fixtures.OpenId4VpStoredPresentationFixtures;
import io.openauth.sim.core.eudi.openid4vp.TrustedAuthorityFixtures;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class Oid4vpApplicationConfiguration {

    @Bean
    FixtureSeedSequence oid4vpSeedSequence() {
        return new FixtureSeedSequence();
    }

    @Bean
    FixtureDcqlPresetRepository oid4vpDcqlPresetRepository() {
        return new FixtureDcqlPresetRepository();
    }

    @Bean
    FixtureWalletPresetRepository oid4vpWalletPresetRepository() {
        return new FixtureWalletPresetRepository();
    }

    @Bean
    FixtureStoredPresentationRepository oid4vpStoredPresentationRepository() {
        return new FixtureStoredPresentationRepository();
    }

    @Bean
    FixtureRequestUriFactory oid4vpRequestUriFactory() {
        return new FixtureRequestUriFactory();
    }

    @Bean
    FixtureQrCodeEncoder oid4vpQrCodeEncoder() {
        return new FixtureQrCodeEncoder();
    }

    @Bean
    OpenId4VpStoredPresentationFixtures oid4vpStoredPresentationFixtures() {
        return new OpenId4VpStoredPresentationFixtures();
    }

    @Bean
    Oid4vpTelemetryPublisher oid4vpTelemetryPublisher() {
        return new Oid4vpTelemetryPublisher();
    }

    @Bean
    TrustedAuthorityEvaluator oid4vpTrustedAuthorityEvaluator() {
        return TrustedAuthorityEvaluator.fromSnapshot(TrustedAuthorityFixtures.loadSnapshot("haip-baseline"));
    }

    @Bean
    OpenId4VpAuthorizationRequestService oid4vpAuthorizationRequestService(
            FixtureSeedSequence seedSequence,
            FixtureDcqlPresetRepository dcqlPresetRepository,
            FixtureRequestUriFactory requestUriFactory,
            FixtureQrCodeEncoder qrCodeEncoder,
            Oid4vpTelemetryPublisher telemetryPublisher,
            TrustedAuthorityEvaluator trustedAuthorityEvaluator) {
        return new OpenId4VpAuthorizationRequestService(new OpenId4VpAuthorizationRequestService.Dependencies(
                seedSequence,
                dcqlPresetRepository,
                requestUriFactory,
                qrCodeEncoder,
                telemetryPublisher,
                trustedAuthorityEvaluator));
    }

    @Bean
    OpenId4VpWalletSimulationService oid4vpWalletSimulationService(
            FixtureWalletPresetRepository walletPresetRepository,
            Oid4vpTelemetryPublisher telemetryPublisher,
            TrustedAuthorityEvaluator trustedAuthorityEvaluator) {
        return new OpenId4VpWalletSimulationService(new OpenId4VpWalletSimulationService.Dependencies(
                walletPresetRepository, telemetryPublisher, trustedAuthorityEvaluator));
    }

    @Bean
    OpenId4VpValidationService oid4vpValidationService(
            FixtureStoredPresentationRepository storedPresentationRepository,
            TrustedAuthorityEvaluator trustedAuthorityEvaluator,
            Oid4vpTelemetryPublisher telemetryPublisher) {
        return new OpenId4VpValidationService(new OpenId4VpValidationService.Dependencies(
                storedPresentationRepository, trustedAuthorityEvaluator, telemetryPublisher));
    }

    @Bean
    OpenId4VpFixtureIngestionService oid4vpFixtureIngestionService(
            OpenId4VpStoredPresentationFixtures storedPresentationFixtures,
            Oid4vpTelemetryPublisher telemetryPublisher) {
        return new OpenId4VpFixtureIngestionService(
                new OpenId4VpFixtureIngestionService.Dependencies(storedPresentationFixtures, telemetryPublisher));
    }
}
