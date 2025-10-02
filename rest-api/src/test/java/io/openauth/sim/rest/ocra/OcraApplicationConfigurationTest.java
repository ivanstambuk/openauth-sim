package io.openauth.sim.rest.ocra;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.openauth.sim.application.ocra.OcraEvaluationApplicationService;
import io.openauth.sim.application.ocra.OcraVerificationApplicationService;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

final class OcraApplicationConfigurationTest {

  private final OcraApplicationConfiguration configuration = new OcraApplicationConfiguration();

  @Test
  @DisplayName("Evaluation application service falls back to empty resolver when store missing")
  void evaluationServiceWithoutStore() {
    ObjectProvider<Clock> clockProvider = providerOf(Clock.systemUTC());
    ObjectProvider<CredentialStore> storeProvider = providerOf(null);

    OcraEvaluationApplicationService service =
        configuration.ocraEvaluationApplicationService(clockProvider, storeProvider);

    assertNotNull(service);
  }

  @Test
  @DisplayName("Evaluation application service uses provided credential store")
  void evaluationServiceWithStore() {
    ObjectProvider<Clock> clockProvider = providerOf(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    CredentialStore store = new InMemoryStore();
    ObjectProvider<CredentialStore> storeProvider = providerOf(store);

    OcraEvaluationApplicationService service =
        configuration.ocraEvaluationApplicationService(clockProvider, storeProvider);

    assertNotNull(service);
  }

  @Test
  @DisplayName("Verification application service accepts optional store")
  void verificationServiceWithAndWithoutStore() {
    ObjectProvider<Clock> clockProvider = providerOf(Clock.systemUTC());

    OcraVerificationApplicationService withoutStore =
        configuration.ocraVerificationApplicationService(clockProvider, providerOf(null));
    assertNotNull(withoutStore);

    OcraVerificationApplicationService withStore =
        configuration.ocraVerificationApplicationService(
            clockProvider, providerOf(new InMemoryStore()));
    assertNotNull(withStore);
  }

  private static <T> ObjectProvider<T> providerOf(T value) {
    return new ObjectProvider<>() {
      @Override
      public T getObject(Object... args) {
        throw new UnsupportedOperationException("Not required for test");
      }

      @Override
      public T getIfAvailable() {
        return value;
      }

      @Override
      public T getIfUnique() {
        return value;
      }

      @Override
      public T getObject() {
        if (value == null) {
          throw new IllegalStateException("No instance available");
        }
        return value;
      }
    };
  }

  private static final class InMemoryStore implements CredentialStore {
    @Override
    public void save(Credential credential) {
      // No-op for test stub
    }

    @Override
    public Optional<Credential> findByName(String name) {
      return Optional.empty();
    }

    @Override
    public List<Credential> findAll() {
      return Collections.emptyList();
    }

    @Override
    public boolean delete(String name) {
      return false;
    }

    @Override
    public void close() {
      // No-op for test stub
    }
  }
}
