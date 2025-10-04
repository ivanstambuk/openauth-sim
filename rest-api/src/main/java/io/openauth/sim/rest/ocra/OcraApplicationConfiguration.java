package io.openauth.sim.rest.ocra;

import io.openauth.sim.application.ocra.OcraCredentialResolvers;
import io.openauth.sim.application.ocra.OcraEvaluationApplicationService;
import io.openauth.sim.application.ocra.OcraSeedApplicationService;
import io.openauth.sim.application.ocra.OcraVerificationApplicationService;
import io.openauth.sim.core.store.CredentialStore;
import java.time.Clock;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OcraApplicationConfiguration {

  @Bean
  OcraEvaluationApplicationService ocraEvaluationApplicationService(
      ObjectProvider<Clock> clockProvider,
      ObjectProvider<CredentialStore> credentialStoreProvider) {
    Clock clock = Optional.ofNullable(clockProvider.getIfAvailable()).orElse(Clock.systemUTC());
    CredentialStore store = credentialStoreProvider.getIfAvailable();
    return new OcraEvaluationApplicationService(
        clock,
        store != null
            ? OcraCredentialResolvers.forStore(store)
            : OcraCredentialResolvers.emptyResolver());
  }

  @Bean
  OcraVerificationApplicationService ocraVerificationApplicationService(
      ObjectProvider<CredentialStore> storeProvider) {
    CredentialStore store = storeProvider.getIfAvailable();
    return new OcraVerificationApplicationService(
        store != null
            ? OcraCredentialResolvers.forVerificationStore(store)
            : OcraCredentialResolvers.emptyVerificationResolver(),
        store);
  }

  @Bean
  OcraSeedApplicationService ocraSeedApplicationService() {
    return new OcraSeedApplicationService();
  }
}
