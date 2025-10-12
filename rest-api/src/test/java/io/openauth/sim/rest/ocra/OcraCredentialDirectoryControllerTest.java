package io.openauth.sim.rest.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.ocra.OcraSeedApplicationService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class OcraCredentialDirectoryControllerTest {

  @Test
  @DisplayName("returns empty list when credential store unavailable")
  void listCredentialsWithoutStore() {
    OcraCredentialDirectoryController controller =
        new OcraCredentialDirectoryController(provider(null), seedServiceFor(null));

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
        new OcraCredentialDirectoryController(provider(store), seedServiceFor(store));

    List<OcraCredentialSummary> summaries = controller.listCredentials();

    assertEquals(2, summaries.size());
    assertEquals("alpha", summaries.get(0).getId());
    assertEquals("alpha", summaries.get(0).getLabel());
    assertEquals("Beta", summaries.get(1).getId());
    assertEquals("Beta (OCRA-1:HOTP-SHA1-6:QA08)", summaries.get(1).getLabel());
  }

  @Test
  @DisplayName("annotates stored RFC vectors with RFC 6287 label")
  void listCredentialsAnnotatesRfcVectors() {
    Credential rfcVector =
        credential(
            "sample-qa08-s064",
            "OCRA-1:HOTP-SHA256-8:QA08-S064",
            Map.of(
                OcraCredentialPersistenceAdapter.ATTR_METADATA_PREFIX + "presetKey", "qa08-s064"));

    FixedCredentialStore store = new FixedCredentialStore(List.of(rfcVector));
    OcraCredentialDirectoryController controller =
        new OcraCredentialDirectoryController(provider(store), seedServiceFor(store));

    List<OcraCredentialSummary> summaries = controller.listCredentials();

    assertEquals(1, summaries.size());
    assertEquals(
        "sample-qa08-s064 (OCRA-1:HOTP-SHA256-8:QA08-S064, RFC 6287)", summaries.get(0).getLabel());
  }

  @Test
  @DisplayName("returns curated sample when preset metadata present")
  void fetchSampleWithPresetMetadata() {
    Credential credential =
        credentialWithMetadata(
            "sample-qa08-s064",
            "OCRA-1:HOTP-SHA256-8:QA08-S064",
            Map.of(
                OcraCredentialPersistenceAdapter.ATTR_METADATA_PREFIX + "presetKey", "qa08-s064"));

    FixedCredentialStore store = new FixedCredentialStore(List.of(credential));
    OcraCredentialDirectoryController controller =
        new OcraCredentialDirectoryController(provider(store), seedServiceFor(store));

    ResponseEntity<OcraCredentialSampleResponse> response =
        controller.fetchSample("sample-qa08-s064");

    assertEquals(HttpStatus.OK, response.getStatusCode());
    OcraCredentialSampleResponse body = response.getBody();
    assertNotNull(body);
    assertEquals("sample-qa08-s064", body.credentialId());
    assertEquals("qa08-s064", body.presetKey());
    assertEquals("OCRA-1:HOTP-SHA256-8:QA08-S064", body.suite());
    assertEquals("17477202", body.otp());
    OcraCredentialSampleResponse.Context context = body.context();
    assertNotNull(context);
    assertEquals("SESSION01", context.challenge());
    assertTrue(context.sessionHex().startsWith("0011223344"));
  }

  @Test
  @DisplayName("falls back to alias mapping when credential matches curated sample")
  void fetchSampleWithAlias() {
    Credential credential = credential("operator-demo", "OCRA-1:HOTP-SHA256-8:QA08-S064", Map.of());

    FixedCredentialStore store = new FixedCredentialStore(List.of(credential));
    OcraCredentialDirectoryController controller =
        new OcraCredentialDirectoryController(provider(store), seedServiceFor(store));

    ResponseEntity<OcraCredentialSampleResponse> response = controller.fetchSample("operator-demo");

    assertEquals(HttpStatus.OK, response.getStatusCode());
    OcraCredentialSampleResponse body = response.getBody();
    assertNotNull(body);
    assertEquals("operator-demo", body.credentialId());
    assertEquals("qa08-s064", body.presetKey());
    assertEquals("17477202", body.otp());
  }

  @Test
  @DisplayName("returns 404 when curated sample is unavailable")
  void fetchSampleUnavailable() {
    Credential credential = credential("custom-id", "OCRA-1:HOTP-SHA256-8:QA08-S1024", Map.of());

    FixedCredentialStore store = new FixedCredentialStore(List.of(credential));
    OcraCredentialDirectoryController controller =
        new OcraCredentialDirectoryController(provider(store), seedServiceFor(store));

    ResponseEntity<OcraCredentialSampleResponse> response = controller.fetchSample("custom-id");
    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
  }

  @Test
  @DisplayName("returns 404 when credential identifier is blank")
  void fetchSampleWithBlankIdentifier() {
    FixedCredentialStore store = new FixedCredentialStore(List.of());
    OcraCredentialDirectoryController controller =
        new OcraCredentialDirectoryController(provider(store), seedServiceFor(store));

    ResponseEntity<OcraCredentialSampleResponse> response = controller.fetchSample("   ");
    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
  }

  @Test
  @DisplayName("ignores non-OCRA credentials when resolving samples")
  void fetchSampleForNonOcraCredential() {
    Credential generic =
        new Credential(
            "generic",
            CredentialType.GENERIC,
            SecretMaterial.fromHex("313233"),
            Map.of(),
            Instant.now(),
            Instant.now());

    FixedCredentialStore store = new FixedCredentialStore(List.of(generic));
    OcraCredentialDirectoryController controller =
        new OcraCredentialDirectoryController(provider(store), seedServiceFor(store));

    ResponseEntity<OcraCredentialSampleResponse> response = controller.fetchSample("generic");
    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
  }

  private Credential credential(String name, String suite, Map<String, String> extraAttributes) {
    java.util.Map<String, String> attributes = new java.util.LinkedHashMap<>();
    if (suite != null && !suite.isBlank()) {
      attributes.put(OcraCredentialPersistenceAdapter.ATTR_SUITE, suite);
    }
    if (extraAttributes != null) {
      attributes.putAll(extraAttributes);
    }
    return new Credential(
        name,
        CredentialType.OATH_OCRA,
        SecretMaterial.fromHex("3132333435363738"),
        attributes,
        Instant.now(),
        Instant.now());
  }

  private Credential credential(String name, String suite) {
    return credential(name, suite, Map.of());
  }

  private Credential credentialWithMetadata(
      String name, String suite, Map<String, String> metadata) {
    return credential(name, suite, metadata);
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

  private static OcraCredentialSeedService seedServiceFor(CredentialStore store) {
    return new OcraCredentialSeedService(provider(store), new OcraSeedApplicationService());
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
