package io.openauth.sim.rest.ocra;

import io.openauth.sim.application.ocra.OcraCredentialResolvers;
import io.openauth.sim.application.ocra.OcraEvaluationApplicationService;
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
      ObjectProvider<Clock> clockProvider, ObjectProvider<CredentialStore> storeProvider) {
    Clock clock = Optional.ofNullable(clockProvider.getIfAvailable()).orElse(Clock.systemUTC());
    CredentialStore store = storeProvider.getIfAvailable();
    return new OcraVerificationApplicationService(
        clock,
        store != null
            ? OcraCredentialResolvers.forVerificationStore(store)
            : OcraCredentialResolvers.emptyVerificationResolver(),
        store);
  }
}
