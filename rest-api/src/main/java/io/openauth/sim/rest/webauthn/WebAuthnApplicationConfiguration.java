package io.openauth.sim.rest.webauthn;

import io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService;
import io.openauth.sim.application.fido2.WebAuthnReplayApplicationService;
import io.openauth.sim.application.fido2.WebAuthnSeedApplicationService;
import io.openauth.sim.core.fido2.WebAuthnAssertionVerifier;
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.store.CredentialStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class WebAuthnApplicationConfiguration {

  @Bean
  WebAuthnEvaluationApplicationService webAuthnEvaluationApplicationService(
      CredentialStore credentialStore) {
    return new WebAuthnEvaluationApplicationService(
        credentialStore,
        new WebAuthnAssertionVerifier(),
        new WebAuthnCredentialPersistenceAdapter());
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
}
