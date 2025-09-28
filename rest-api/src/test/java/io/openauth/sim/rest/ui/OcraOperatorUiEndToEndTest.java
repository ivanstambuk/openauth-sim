package io.openauth.sim.rest.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
final class OcraOperatorUiEndToEndTest {

  private static final String CREDENTIAL_ID = "operator-demo";
  private static final String SUITE = "OCRA-1:HOTP-SHA256-8:QA08-S064";
  private static final String SHARED_SECRET_HEX =
      "3132333435363738393031323334353637383930313233343536373839303132";
  private static final String SESSION_HEX_64 =
      "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF"
          + "112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567";
  private static final Pattern CSRF_PATTERN =
      Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");

  @TempDir static Path tempDir;
  private static Path databasePath;

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    databasePath = tempDir.resolve("credentials.db");
    registry.add(
        "openauth.sim.persistence.database-path", () -> databasePath.toAbsolutePath().toString());
    registry.add("openauth.sim.persistence.enable-store", () -> "true");
  }

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private CredentialStore credentialStore;

  @BeforeEach
  void seedCredential() {
    credentialStore.delete(CREDENTIAL_ID);
    OcraCredentialFactory factory = new OcraCredentialFactory();
    OcraCredentialRequest request =
        new OcraCredentialRequest(
            CREDENTIAL_ID,
            SUITE,
            SHARED_SECRET_HEX,
            SecretEncoding.HEX,
            null,
            null,
            null,
            java.util.Map.of("source", "test"));
    OcraCredentialDescriptor descriptor = factory.createDescriptor(request);
    Credential credential =
        VersionedCredentialRecordMapper.toCredential(
            new OcraCredentialPersistenceAdapter().serialize(descriptor));
    credentialStore.save(credential);
    assertThat(credentialStore.exists(CREDENTIAL_ID)).isTrue();
  }

  @Test
  @DisplayName("Stored credential evaluation succeeds via operator UI")
  void storedCredentialSubmissionSucceeds() {
    ResponseEntity<String> formResponse =
        restTemplate.getForEntity("/ui/ocra/evaluate", String.class);
    assertThat(formResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    String csrfToken = extractCsrf(formResponse.getBody());
    String sessionCookie = firstCookie(formResponse.getHeaders());

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("_csrf", csrfToken);
    form.add("mode", "credential");
    form.add("credentialId", CREDENTIAL_ID);
    form.add("challenge", "SESSION01");
    form.add("sessionHex", SESSION_HEX_64);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    if (sessionCookie != null) {
      headers.put(HttpHeaders.COOKIE, List.of(sessionCookie));
    }

    HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(form, headers);
    ResponseEntity<String> response =
        restTemplate.postForEntity("/ui/ocra/evaluate", requestEntity, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String body = response.getBody();
    assertThat(body).contains("data-testid=\"ocra-otp\"");
    assertThat(body).contains("17477202");
    assertThat(body).contains("data-testid=\"ocra-telemetry-summary\"");
    assertThat(body).contains("data-testid=\"ocra-reason-code\">success");
  }

  private static String extractCsrf(String html) {
    Matcher matcher = CSRF_PATTERN.matcher(html);
    if (!matcher.find()) {
      throw new IllegalStateException("CSRF token not found in rendered form");
    }
    return matcher.group(1);
  }

  private static String firstCookie(HttpHeaders headers) {
    List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
    if (cookies == null || cookies.isEmpty()) {
      return null;
    }
    return cookies.get(0);
  }
}
