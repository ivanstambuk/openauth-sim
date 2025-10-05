package io.openauth.sim.rest.hotp;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.hotp.HotpEvaluationApplicationService;
import io.openauth.sim.core.store.CredentialStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

final class HotpApplicationConfigurationTest {

  @Test
  @DisplayName("Configuration wires HotpEvaluationApplicationService when store present")
  void configurationProvidesService() {
    CredentialStore store = Mockito.mock(CredentialStore.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<CredentialStore> provider = Mockito.mock(ObjectProvider.class);
    Mockito.when(provider.getIfAvailable()).thenReturn(store);

    HotpApplicationConfiguration configuration = new HotpApplicationConfiguration();
    HotpEvaluationApplicationService service =
        configuration.hotpEvaluationApplicationService(provider);

    assertNotNull(service);
  }

  @Test
  @DisplayName("Configuration throws when CredentialStore missing")
  void configurationRequiresCredentialStore() {
    @SuppressWarnings("unchecked")
    ObjectProvider<CredentialStore> provider = Mockito.mock(ObjectProvider.class);
    Mockito.when(provider.getIfAvailable()).thenReturn(null);

    HotpApplicationConfiguration configuration = new HotpApplicationConfiguration();

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> configuration.hotpEvaluationApplicationService(provider));
    assertTrue(exception.getMessage().contains("CredentialStore"));
  }
}
