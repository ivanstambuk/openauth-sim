package io.openauth.sim.rest.hotp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.hotp.HotpSeedApplicationService;
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

class HotpCredentialDirectoryControllerTest {

  @Test
  @DisplayName("returns empty list when credential store unavailable")
  void listCredentialsWithoutStore() {
    HotpCredentialDirectoryController controller = controller(provider(null));

    assertTrue(controller.listCredentials().isEmpty());
  }

  @Test
  @DisplayName("filters HOTP credentials and sorts summaries")
  void listCredentialsFiltersAndSorts() {
    Credential hotpA =
        credential("beta-device", Map.of("hotp.digits", "6", "hotp.algorithm", "SHA1"));
    Credential other =
        new Credential(
            "ignored",
            CredentialType.OATH_OCRA,
            SecretMaterial.fromHex("3132333435"),
            Map.of(),
            Instant.now(),
            Instant.now());
    Credential hotpB =
        credential("alpha-device", Map.of("hotp.digits", "8", "hotp.algorithm", "SHA256"));

    FixedCredentialStore store = new FixedCredentialStore(List.of(hotpA, other, hotpB));
    HotpCredentialDirectoryController controller = controller(provider(store));

    List<HotpCredentialDirectoryController.HotpCredentialSummary> summaries =
        controller.listCredentials();

    assertEquals(2, summaries.size());
    HotpCredentialDirectoryController.HotpCredentialSummary first = summaries.get(0);
    HotpCredentialDirectoryController.HotpCredentialSummary second = summaries.get(1);
    assertEquals("alpha-device", first.id());
    assertTrue(first.label().contains("SHA256"));
    assertTrue(first.label().contains("8 digits"));
    assertEquals("beta-device", second.id());
    assertTrue(second.label().contains("SHA1"));
    assertTrue(second.label().contains("6 digits"));
  }

  @Test
  @DisplayName("summary exposes counter metadata when available")
  void summaryIncludesCounterMetadata() {
    Credential hotp =
        credential(
            "counter-device",
            Map.of("hotp.digits", "6", "hotp.counter", "42", "hotp.algorithm", "SHA512"));

    FixedCredentialStore store = new FixedCredentialStore(List.of(hotp));
    HotpCredentialDirectoryController controller = controller(provider(store));

    List<HotpCredentialDirectoryController.HotpCredentialSummary> summaries =
        controller.listCredentials();

    assertEquals(1, summaries.size());
    HotpCredentialDirectoryController.HotpCredentialSummary summary = summaries.get(0);
    assertEquals(42L, summary.counter());
    assertEquals(6, summary.digits());
  }

  @Test
  @DisplayName("label falls back when metadata is absent or malformed")
  void summaryHandlesMissingMetadata() {
    Credential hotp =
        credential(
            "minimal-device",
            Map.of("hotp.digits", "not-a-number", "hotp.counter", "NaN", "hotp.algorithm", " "));

    FixedCredentialStore store = new FixedCredentialStore(List.of(hotp));
    HotpCredentialDirectoryController controller = controller(provider(store));

    List<HotpCredentialDirectoryController.HotpCredentialSummary> summaries =
        controller.listCredentials();

    assertEquals(1, summaries.size());
    HotpCredentialDirectoryController.HotpCredentialSummary summary = summaries.get(0);
    assertEquals("minimal-device", summary.id());
    assertEquals("minimal-device", summary.label());
    assertEquals(null, summary.digits());
    assertEquals(null, summary.counter());
  }

  @Test
  @DisplayName(
      "labels include available algorithm or digit metadata when partial information exists")
  void summaryHandlesPartialMetadata() {
    Credential algorithmOnly = credential("algorithm-only", Map.of("hotp.algorithm", "SHA256"));
    Credential digitsOnly = credential("digits-only", Map.of("hotp.digits", "7"));

    FixedCredentialStore store = new FixedCredentialStore(List.of(algorithmOnly, digitsOnly));
    HotpCredentialDirectoryController controller = controller(provider(store));

    List<HotpCredentialDirectoryController.HotpCredentialSummary> summaries =
        controller.listCredentials();

    HotpCredentialDirectoryController.HotpCredentialSummary first = summaries.get(0);
    HotpCredentialDirectoryController.HotpCredentialSummary second = summaries.get(1);

    assertEquals("algorithm-only", first.id());
    assertEquals("algorithm-only (SHA256)", first.label());
    assertEquals(null, first.digits());

    assertEquals("digits-only", second.id());
    assertEquals("digits-only (7 digits)", second.label());
    assertEquals(7, second.digits());
  }

  private HotpCredentialDirectoryController controller(ObjectProvider<CredentialStore> provider) {
    HotpSeedApplicationService applicationService = new HotpSeedApplicationService();
    HotpCredentialSeedService seedService =
        new HotpCredentialSeedService(provider, applicationService);
    return new HotpCredentialDirectoryController(provider, seedService);
  }

  private Credential credential(String name, Map<String, String> attributes) {
    return new Credential(
        name,
        CredentialType.OATH_HOTP,
        SecretMaterial.fromHex("3132333435363738393031323334353637383930"),
        attributes,
        Instant.now(),
        Instant.now());
  }

  private ObjectProvider<CredentialStore> provider(CredentialStore store) {
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
