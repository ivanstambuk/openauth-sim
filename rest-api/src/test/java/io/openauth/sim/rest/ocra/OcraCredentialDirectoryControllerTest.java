package io.openauth.sim.rest.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.CredentialStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class OcraCredentialDirectoryControllerTest {

  @Test
  @DisplayName("listCredentials returns empty list when store unavailable")
  void listCredentialsWithoutStore() {
    OcraCredentialDirectoryController controller =
        new OcraCredentialDirectoryController(emptyProvider());

    List<OcraCredentialSummary> result = controller.listCredentials();

    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("listCredentials sorts summaries and includes suite labels")
  void listCredentialsReturnsSortedSummaries() {
    InMemoryCredentialStore store = new InMemoryCredentialStore();
    store.saveCredential(
        credential(
            "beta",
            Map.of(OcraCredentialPersistenceAdapter.ATTR_SUITE, "OCRA-1:HOTP-SHA1-6:QA08")));
    store.saveCredential(
        credential(
            "alpha",
            Map.of(OcraCredentialPersistenceAdapter.ATTR_SUITE, "OCRA-1:HOTP-SHA256-6:QA08-S064")));
    store.saveCredential(credential("gamma", Map.of("non.ocra", "ignored")));

    OcraCredentialDirectoryController controller =
        new OcraCredentialDirectoryController(provider(store));

    List<OcraCredentialSummary> result = controller.listCredentials();

    assertEquals(3, result.size());
    assertEquals("alpha", result.get(0).getId());
    assertEquals("alpha (OCRA-1:HOTP-SHA256-6:QA08-S064)", result.get(0).getLabel());
    assertEquals("beta", result.get(1).getId());
    assertEquals("beta (OCRA-1:HOTP-SHA1-6:QA08)", result.get(1).getLabel());
    assertEquals("gamma", result.get(2).getId());
    assertEquals("gamma", result.get(2).getLabel());
  }

  private static Credential credential(String name, Map<String, String> attributes) {
    return new Credential(
        name,
        CredentialType.OATH_OCRA,
        SecretMaterial.fromHex("31323334"),
        attributes,
        Instant.parse("2025-09-30T12:00:00Z"),
        Instant.parse("2025-09-30T12:00:00Z"));
  }

  private static ObjectProvider<CredentialStore> provider(CredentialStore store) {
    DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
    factory.registerSingleton("credentialStore", store);
    factory.registerResolvableDependency(CredentialStore.class, store);
    return factory.getBeanProvider(CredentialStore.class);
  }

  private static ObjectProvider<CredentialStore> emptyProvider() {
    DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
    return factory.getBeanProvider(CredentialStore.class);
  }

  private static final class InMemoryCredentialStore implements CredentialStore {

    private final List<Credential> credentials = new ArrayList<>();

    @Override
    public void save(Credential credential) {
      credentials.add(credential);
    }

    void saveCredential(Credential credential) {
      credentials.add(credential);
    }

    @Override
    public Optional<Credential> findByName(String name) {
      return credentials.stream().filter(c -> c.name().equals(name)).findFirst();
    }

    @Override
    public List<Credential> findAll() {
      return new ArrayList<>(credentials);
    }

    @Override
    public boolean delete(String name) {
      return credentials.removeIf(c -> c.name().equals(name));
    }

    @Override
    public void close() {
      credentials.clear();
    }
  }
}
