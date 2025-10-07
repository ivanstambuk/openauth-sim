package io.openauth.sim.rest.hotp;

import io.openauth.sim.application.hotp.HotpEvaluationApplicationService;
import io.openauth.sim.application.hotp.HotpReplayApplicationService;
import io.openauth.sim.application.hotp.HotpSampleApplicationService;
import io.openauth.sim.application.hotp.HotpSeedApplicationService;
import io.openauth.sim.core.store.CredentialStore;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class HotpApplicationConfiguration {

  @Bean
  HotpEvaluationApplicationService hotpEvaluationApplicationService(
      ObjectProvider<CredentialStore> storeProvider) {
    CredentialStore store = Optional.ofNullable(storeProvider.getIfAvailable()).orElse(null);
    if (store == null) {
      throw new IllegalStateException(
          "CredentialStore bean is required for HOTP evaluation to operate");
    }
    return new HotpEvaluationApplicationService(store);
  }

  @Bean
  HotpReplayApplicationService hotpReplayApplicationService(
      ObjectProvider<CredentialStore> storeProvider) {
    CredentialStore store = Optional.ofNullable(storeProvider.getIfAvailable()).orElse(null);
    if (store == null) {
      throw new IllegalStateException(
          "CredentialStore bean is required for HOTP replay to operate");
    }
    return new HotpReplayApplicationService(store);
  }

  @Bean
  HotpSampleApplicationService hotpSampleApplicationService(
      ObjectProvider<CredentialStore> storeProvider) {
    CredentialStore store = Optional.ofNullable(storeProvider.getIfAvailable()).orElse(null);
    if (store == null) {
      throw new IllegalStateException(
          "CredentialStore bean is required for HOTP sample generation to operate");
    }
    return new HotpSampleApplicationService(store);
  }

  @Bean
  HotpSeedApplicationService hotpSeedApplicationService() {
    return new HotpSeedApplicationService();
  }
}
