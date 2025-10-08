package io.openauth.sim.rest.totp;

import io.openauth.sim.application.totp.TotpEvaluationApplicationService;
import io.openauth.sim.application.totp.TotpReplayApplicationService;
import io.openauth.sim.core.store.CredentialStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TotpApplicationConfiguration {

  @Bean
  TotpEvaluationApplicationService totpEvaluationApplicationService(
      CredentialStore credentialStore) {
    return new TotpEvaluationApplicationService(credentialStore);
  }

  @Bean
  TotpReplayApplicationService totpReplayApplicationService(
      TotpEvaluationApplicationService evaluationApplicationService) {
    return new TotpReplayApplicationService(evaluationApplicationService);
  }
}
