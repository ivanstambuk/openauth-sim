package io.openauth.sim.rest.webauthn;

import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService;
import io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationService;
import io.openauth.sim.application.fido2.WebAuthnAttestationReplayApplicationService;
import io.openauth.sim.application.fido2.WebAuthnAttestationSeedService;
import io.openauth.sim.application.fido2.WebAuthnAttestationVerificationApplicationService;
import io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService;
import io.openauth.sim.application.fido2.WebAuthnReplayApplicationService;
import io.openauth.sim.application.fido2.WebAuthnSeedApplicationService;
import io.openauth.sim.application.fido2.WebAuthnTrustAnchorResolver;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.core.fido2.WebAuthnAssertionVerifier;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerifier;
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.store.CredentialStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class WebAuthnApplicationConfiguration {

    @Bean
    WebAuthnEvaluationApplicationService webAuthnEvaluationApplicationService(CredentialStore credentialStore) {
        return new WebAuthnEvaluationApplicationService(
                credentialStore, new WebAuthnAssertionVerifier(), new WebAuthnCredentialPersistenceAdapter());
    }

    @Bean
    WebAuthnAssertionGenerationApplicationService webAuthnAssertionGenerationApplicationService(
            CredentialStore credentialStore) {
        return new WebAuthnAssertionGenerationApplicationService(
                credentialStore, new WebAuthnCredentialPersistenceAdapter());
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
        return new WebAuthnAttestationGenerationApplicationService(
                new WebAuthnAttestationGenerator(),
                TelemetryContracts.fido2AttestAdapter(),
                credentialStore,
                new WebAuthnCredentialPersistenceAdapter());
    }

    @Bean
    WebAuthnAttestationVerificationApplicationService webAuthnAttestationVerificationApplicationService() {
        return new WebAuthnAttestationVerificationApplicationService(
                new WebAuthnAttestationVerifier(), TelemetryContracts.fido2AttestAdapter());
    }

    @Bean
    WebAuthnAttestationReplayApplicationService webAuthnAttestationReplayApplicationService(
            CredentialStore credentialStore) {
        return new WebAuthnAttestationReplayApplicationService(
                new WebAuthnAttestationVerifier(),
                TelemetryContracts.fido2AttestReplayAdapter(),
                credentialStore,
                new WebAuthnCredentialPersistenceAdapter());
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
    WebAuthnCredentialPersistenceAdapter webAuthnCredentialPersistenceAdapter() {
        return new WebAuthnCredentialPersistenceAdapter();
    }
}
