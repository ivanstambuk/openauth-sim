package io.openauth.sim.rest.ocra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
final class OcraCredentialDirectoryControllerTest {

  private static final String OCRA_SECRET =
      "3132333435363738393031323334353637383930313233343536373839303132";

  @DynamicPropertySource
  static void enableStore(DynamicPropertyRegistry registry) {
    registry.add("openauth.sim.persistence.enable-store", () -> "true");
  }

  @Autowired private TestRestTemplate restTemplate;

  @MockBean private CredentialStore credentialStore;

  @Test
  @DisplayName("Returns OCRA credential summaries in sorted order")
  void listCredentials() throws IOException {
    Credential ocraAlpha = createOcraCredential("alpha", "OCRA-1:HOTP-SHA256-6:QA08-S064");
    Credential generic =
        Credential.create(
            "generic", CredentialType.GENERIC, SecretMaterial.fromStringUtf8("x"), Map.of());
    Credential ocraBeta = createOcraCredential("beta", "OCRA-1:HOTP-SHA256-8:C-QH64");

    when(credentialStore.findAll()).thenReturn(List.of(ocraBeta, generic, ocraAlpha));

    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/v1/ocra/credentials", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();

    List<String> ids = extractArrayValues(response.getBody(), "id");
    List<String> labels = extractArrayValues(response.getBody(), "label");

    assertThat(ids).containsExactly("alpha", "beta");
    assertThat(labels)
        .containsExactly(
            "alpha (OCRA-1:HOTP-SHA256-6:QA08-S064)", "beta (OCRA-1:HOTP-SHA256-8:C-QH64)");
  }

  @Test
  @DisplayName("Returns empty list when underlying store has no entries")
  void returnsEmptyWhenStoreEmpty() throws IOException {
    reset(credentialStore);
    when(credentialStore.findAll()).thenReturn(List.of());

    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/v1/ocra/credentials", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(extractArrayValues(response.getBody(), "id")).isEmpty();
  }

  private static Credential createOcraCredential(String name, String suite) {
    OcraCredentialFactory factory = new OcraCredentialFactory();
    OcraCredentialRequest request =
        new OcraCredentialRequest(
            name,
            suite,
            OCRA_SECRET,
            SecretEncoding.HEX,
            suite.contains(":C-") ? 1L : null,
            null,
            null,
            Map.of());
    OcraCredentialDescriptor descriptor = factory.createDescriptor(request);
    return VersionedCredentialRecordMapper.toCredential(
        new OcraCredentialPersistenceAdapter().serialize(descriptor));
  }

  private static List<String> extractArrayValues(String body, String fieldName) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode node = mapper.readTree(body == null ? "[]" : body);
    List<String> values = new ArrayList<>();
    if (node.isArray()) {
      for (JsonNode element : node) {
        JsonNode valueNode = element.get(fieldName);
        if (valueNode != null && valueNode.isTextual()) {
          values.add(valueNode.asText());
        }
      }
    }
    return values;
  }
}
