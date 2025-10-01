package io.openauth.sim.rest.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.CredentialStore;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class OcraCredentialDirectoryControllerTest {

  @Test
  @DisplayName("returns empty list when credential store unavailable")
  void listCredentialsWithoutStore() {
    OcraCredentialDirectoryController controller =
        new OcraCredentialDirectoryController(provider(null));

    List<OcraCredentialSummary> summaries = controller.listCredentials();

    assertTrue(summaries.isEmpty());
  }

  @Test
  @DisplayName("filters to OCRA credentials and sorts summaries")
  void listCredentialsFiltersAndSorts() {
    Credential ocraA = credential("Beta", "OCRA-1:HOTP-SHA1-6:QA08");
    Credential generic =
        new Credential(
            "Ignored",
            CredentialType.GENERIC,
            SecretMaterial.fromHex("313233343536"),
            Map.of(),
            Instant.now(),
            Instant.now());
    Credential ocraB = credential("alpha", null);

    FixedCredentialStore store = new FixedCredentialStore(List.of(ocraA, generic, ocraB));
    OcraCredentialDirectoryController controller =
        new OcraCredentialDirectoryController(provider(store));

    List<OcraCredentialSummary> summaries = controller.listCredentials();

    assertEquals(2, summaries.size());
    assertEquals("alpha", summaries.get(0).getId());
    assertEquals("alpha", summaries.get(0).getLabel());
    assertEquals("Beta", summaries.get(1).getId());
    assertEquals("Beta (OCRA-1:HOTP-SHA1-6:QA08)", summaries.get(1).getLabel());
  }

  private Credential credential(String name, String suite) {
    Map<String, String> attributes =
        suite == null ? Map.of() : Map.of(OcraCredentialPersistenceAdapter.ATTR_SUITE, suite);
    return new Credential(
        name,
        CredentialType.OATH_OCRA,
        SecretMaterial.fromHex("3132333435363738"),
        attributes,
        Instant.now(),
        Instant.now());
  }

  private static ObjectProvider<CredentialStore> provider(CredentialStore store) {
    return new ObjectProvider<>() {
      @Override
      public CredentialStore getObject(Object... args) {
        return store;
      }

      @Override
      public CredentialStore getObject() {
        return store;
      }

      @Override
      public CredentialStore getIfAvailable() {
        return store;
      }

      @Override
      public CredentialStore getIfUnique() {
        return store;
      }

      @Override
      public java.util.stream.Stream<CredentialStore> stream() {
        return store == null ? java.util.stream.Stream.empty() : java.util.stream.Stream.of(store);
      }

      @Override
      public java.util.stream.Stream<CredentialStore> orderedStream() {
        return stream();
      }
    };
  }

  private static final class FixedCredentialStore implements CredentialStore {
    private final List<Credential> credentials;

    private FixedCredentialStore(List<Credential> credentials) {
      this.credentials = List.copyOf(credentials);
    }

    @Override
    public void save(Credential credential) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<Credential> findAll() {
      return credentials;
    }

    @Override
    public java.util.Optional<Credential> findByName(String name) {
      return credentials.stream().filter(c -> c.name().equals(name)).findFirst();
    }

    @Override
    public boolean delete(String name) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
      // no-op
    }
  }
}
