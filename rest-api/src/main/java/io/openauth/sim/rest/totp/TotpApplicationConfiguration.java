package io.openauth.sim.rest.totp;

import io.openauth.sim.application.totp.TotpEvaluationApplicationService;
import io.openauth.sim.application.totp.TotpReplayApplicationService;
import io.openauth.sim.application.totp.TotpSampleApplicationService;
import io.openauth.sim.application.totp.TotpSeedApplicationService;
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

  @Bean
  TotpSeedApplicationService totpSeedApplicationService() {
    return new TotpSeedApplicationService();
  }

  @Bean
  TotpSampleApplicationService totpSampleApplicationService(CredentialStore credentialStore) {
    return new TotpSampleApplicationService(credentialStore);
  }
}
