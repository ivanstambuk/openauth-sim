package io.openauth.sim.rest.webauthn;

import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService;
import io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationService;
import io.openauth.sim.application.fido2.WebAuthnAttestationReplayApplicationService;
import io.openauth.sim.application.fido2.WebAuthnAttestationSeedService;
import io.openauth.sim.application.fido2.WebAuthnAttestationStoredMetadataApplicationService;
import io.openauth.sim.application.fido2.WebAuthnAttestationVerificationApplicationService;
import io.openauth.sim.application.fido2.WebAuthnCredentialDirectoryApplicationService;
import io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService;
import io.openauth.sim.application.fido2.WebAuthnReplayApplicationService;
import io.openauth.sim.application.fido2.WebAuthnSeedApplicationService;
import io.openauth.sim.application.fido2.WebAuthnTrustAnchorResolver;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.core.store.CredentialStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class WebAuthnApplicationConfiguration {

    @Bean
    WebAuthnEvaluationApplicationService webAuthnEvaluationApplicationService(CredentialStore credentialStore) {
        return WebAuthnEvaluationApplicationService.usingDefaults(credentialStore);
    }

    @Bean
    WebAuthnAssertionGenerationApplicationService webAuthnAssertionGenerationApplicationService(
            CredentialStore credentialStore) {
        return WebAuthnAssertionGenerationApplicationService.usingDefaults(credentialStore);
    }

    @Bean
    WebAuthnReplayApplicationService webAuthnReplayApplicationService(
            WebAuthnEvaluationApplicationService evaluationApplicationService) {
        return new WebAuthnReplayApplicationService(evaluationApplicationService);
    }

    @Bean
    WebAuthnSeedApplicationService webAuthnSeedApplicationService() {
        return new WebAuthnSeedApplicationService();
    }

    @Bean
    WebAuthnAttestationGenerationApplicationService webAuthnAttestationGenerationApplicationService(
            CredentialStore credentialStore) {
        return WebAuthnAttestationGenerationApplicationService.usingDefaults(
                credentialStore, TelemetryContracts.fido2AttestAdapter());
    }

    @Bean
    WebAuthnAttestationVerificationApplicationService webAuthnAttestationVerificationApplicationService() {
        return WebAuthnAttestationVerificationApplicationService.usingDefaults(TelemetryContracts.fido2AttestAdapter());
    }

    @Bean
    WebAuthnAttestationReplayApplicationService webAuthnAttestationReplayApplicationService(
            CredentialStore credentialStore) {
        return WebAuthnAttestationReplayApplicationService.usingDefaults(
                credentialStore, TelemetryContracts.fido2AttestReplayAdapter());
    }

    @Bean
    WebAuthnTrustAnchorResolver webAuthnTrustAnchorResolver() {
        return new WebAuthnTrustAnchorResolver();
    }

    @Bean
    WebAuthnAttestationSeedService webAuthnAttestationSeedService() {
        return new WebAuthnAttestationSeedService();
    }

    @Bean
    WebAuthnCredentialDirectoryApplicationService webAuthnCredentialDirectoryApplicationService(
            CredentialStore credentialStore) {
        return new WebAuthnCredentialDirectoryApplicationService(credentialStore);
    }

    @Bean
    WebAuthnAttestationStoredMetadataApplicationService webAuthnAttestationStoredMetadataApplicationService(
            CredentialStore credentialStore) {
        return WebAuthnAttestationStoredMetadataApplicationService.usingDefaults(credentialStore);
    }
}
