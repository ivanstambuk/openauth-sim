package io.openauth.sim.rest.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openauth.sim.application.ocra.OcraCredentialResolvers;
import io.openauth.sim.application.ocra.OcraVerificationApplicationService;
import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OcraVerificationServiceTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2025-10-01T00:00:00Z"), ZoneOffset.UTC);
  private static final String DEFAULT_SECRET_HEX = "31323334353637383930313233343536";

  private final TestHandler handler = new TestHandler();
  private final Logger logger = Logger.getLogger("io.openauth.sim.rest.ocra.telemetry");

  @BeforeEach
  void attachHandler() {
    logger.addHandler(handler);
    logger.setLevel(Level.ALL);
  }

  @Test
  @DisplayName("invalid challenge format yields validation failure")
  void invalidChallengeFormatTriggersValidationFailure() {
    OcraVerificationService service = service();
    OcraVerificationInlineCredential inline = inlineCredential();
    OcraVerificationContext invalidContext =
        new OcraVerificationContext("ABC", null, null, null, null, null, null);
    OcraVerificationRequest request =
        new OcraVerificationRequest(buildMatchingOtp(), null, inline, invalidContext);

    OcraVerificationValidationException exception =
        assertThrows(
            OcraVerificationValidationException.class,
            () ->
                service.verify(
                    request, new OcraVerificationAuditContext("req-invalid", null, null)));

    assertEquals("validation_error", exception.reasonCode());
    assertTelemetryMessageContains("validation_failure");
  }

  @AfterEach
  void detachHandler() {
    logger.removeHandler(handler);
    handler.records.clear();
  }

  @Test
  @DisplayName("inline verification returns match and records hashed telemetry")
  void inlineVerificationMatch() {
    OcraVerificationService service = service();

    OcraVerificationRequest request = inlineRequest(buildMatchingOtp());
    OcraVerificationAuditContext auditContext =
        new OcraVerificationAuditContext("req-inline-1", "client-77", "operator-a");

    OcraVerificationResponse response = service.verify(request, auditContext);

    assertEquals("match", response.status());
    assertEquals("match", response.reasonCode());
    assertNotNull(response.metadata().contextFingerprint());
    assertTelemetryMessageContains("status=match");
    assertTelemetryMessageContains("otpHash=");
  }

  @Test
  @DisplayName("inline verification mismatch reports strict mismatch")
  void inlineVerificationMismatch() {
    OcraVerificationService service = service();

    OcraVerificationRequest request = inlineRequest("654321");
    OcraVerificationAuditContext auditContext =
        new OcraVerificationAuditContext("req-inline-2", null, null);

    OcraVerificationResponse response = service.verify(request, auditContext);

    assertEquals("mismatch", response.status());
    assertEquals("strict_mismatch", response.reasonCode());
    assertTelemetryMessageContains("status=mismatch");
    assertTelemetryMessageContains("reasonCode=strict_mismatch");
  }

  @Test
  @DisplayName("otp omission surfaces otp_missing validation error")
  void otpMissingValidation() {
    OcraVerificationService service = service();
    OcraVerificationRequest request =
        new OcraVerificationRequest(null, null, inlineCredential(), context());

    OcraVerificationValidationException exception =
        assertThrows(
            OcraVerificationValidationException.class,
            () -> service.verify(request, new OcraVerificationAuditContext("req-otp", null, null)));

    assertEquals("otp_missing", exception.reasonCode());
    assertTelemetryMessageContains("reasonCode=otp_missing");
  }

  @Test
  @DisplayName("inline credential missing suite surfaces suite_missing")
  void inlineSuiteMissingValidation() {
    OcraVerificationService service = service();
    OcraVerificationInlineCredential inlineCredential =
        new OcraVerificationInlineCredential("   ", "31323334");
    OcraVerificationRequest request =
        new OcraVerificationRequest("123456", null, inlineCredential, context());

    OcraVerificationValidationException exception =
        assertThrows(
            OcraVerificationValidationException.class,
            () ->
                service.verify(request, new OcraVerificationAuditContext("req-suite", null, null)));

    assertEquals("suite_missing", exception.reasonCode());
    assertTelemetryMessageContains("reasonCode=suite_missing");
  }

  @Test
  @DisplayName("inline credential missing shared secret surfaces shared_secret_missing")
  void inlineSecretMissingValidation() {
    OcraVerificationService service = service();
    OcraVerificationInlineCredential inlineCredential =
        new OcraVerificationInlineCredential("OCRA-1:HOTP-SHA1-6:QN08", "   ");
    OcraVerificationRequest request =
        new OcraVerificationRequest("123456", null, inlineCredential, context());

    OcraVerificationValidationException exception =
        assertThrows(
            OcraVerificationValidationException.class,
            () ->
                service.verify(
                    request, new OcraVerificationAuditContext("req-secret", null, null)));

    assertEquals("shared_secret_missing", exception.reasonCode());
    assertTelemetryMessageContains("reasonCode=shared_secret_missing");
  }

  @Test
  @DisplayName("stored credential verification succeeds and records stored telemetry")
  void storedVerificationMatch() {
    InMemoryCredentialStore store = new InMemoryCredentialStore();
    OcraCredentialDescriptor descriptor = inlineDescriptor();
    var persistenceAdapter = new OcraCredentialPersistenceAdapter();
    var record = persistenceAdapter.serialize(descriptor);
    store.save(VersionedCredentialRecordMapper.toCredential(record));

    OcraVerificationService service = service(store);

    OcraVerificationRequest request =
        new OcraVerificationRequest(buildMatchingOtp(), descriptor.name(), null, context());
    OcraVerificationResponse response =
        service.verify(request, new OcraVerificationAuditContext("req-stored", null, "operator"));

    assertEquals("match", response.status());
    assertEquals("stored", response.metadata().credentialSource());
    assertTelemetryMessageContains("credentialSource=stored");
  }

  @Test
  @DisplayName("stored credential not found surfaces credential_not_found")
  void storedCredentialMissing() {
    InMemoryCredentialStore store = new InMemoryCredentialStore();
    OcraVerificationService service = service(store);

    OcraVerificationRequest request = storedRequest("missing-credential");

    OcraVerificationValidationException exception =
        assertThrows(
            OcraVerificationValidationException.class,
            () ->
                service.verify(
                    request, new OcraVerificationAuditContext("req-missing", null, null)));

    assertEquals("credential_not_found", exception.reasonCode());
    assertTelemetryMessageContains("credential_not_found");
  }

  @Test
  @DisplayName("supplying stored and inline credential payload surfaces credential_conflict")
  void storedAndInlineConflict() {
    OcraVerificationService service = service();
    OcraVerificationInlineCredential inline =
        new OcraVerificationInlineCredential("OCRA-1:HOTP-SHA1-6:QN08", DEFAULT_SECRET_HEX);
    OcraVerificationRequest request =
        new OcraVerificationRequest(buildMatchingOtp(), "stored-token", inline, context());

    OcraVerificationValidationException exception =
        assertThrows(
            OcraVerificationValidationException.class,
            () ->
                service.verify(
                    request, new OcraVerificationAuditContext("req-conflict", null, null)));

    assertEquals("credential_conflict", exception.reasonCode());
    assertTelemetryMessageContains("credential_conflict");
  }

  @Test
  @DisplayName("missing stored and inline payload surfaces credential_missing")
  void missingStoredAndInlineCredential() {
    OcraVerificationService service = service();
    OcraVerificationRequest request =
        new OcraVerificationRequest(buildMatchingOtp(), null, null, context());

    OcraVerificationValidationException exception =
        assertThrows(
            OcraVerificationValidationException.class,
            () ->
                service.verify(
                    request, new OcraVerificationAuditContext("req-missing", null, null)));

    assertEquals("credential_missing", exception.reasonCode());
    assertTelemetryMessageContains("credential_missing");
  }

  @Test
  @DisplayName("resolver failures propagate as unexpected errors")
  void resolverFailureTriggersUnexpectedError() {
    OcraVerificationApplicationService applicationService =
        new OcraVerificationApplicationService(
            FIXED_CLOCK,
            credentialId -> {
              throw new IllegalStateException("resolver failure");
            },
            new InMemoryCredentialStore());
    OcraVerificationService service = new OcraVerificationService(applicationService);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                service.verify(
                    storedRequest("stored-token"),
                    new OcraVerificationAuditContext("req-unexpected", null, null)));

    assertEquals("resolver failure", exception.getMessage());
    assertTelemetryMessageContains("unexpected_error");
  }

  @Test
  @DisplayName("store failures surface unexpected_error and propagate")
  void storedVerificationUnexpectedError() {
    FailingCredentialStore failingStore = new FailingCredentialStore();
    OcraVerificationService service = service(failingStore);

    OcraVerificationRequest request = storedRequest("stored-token");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                service.verify(
                    request, new OcraVerificationAuditContext("req-offline", null, null)));

    assertEquals("store offline", exception.getMessage());
    assertTelemetryMessageContains("reasonCode=unexpected_error");
  }

  @Test
  @DisplayName("corrupted stored credential yields unexpected error validation")
  void corruptedStoredCredentialTriggersUnexpected() {
    OcraCredentialDescriptor descriptor = inlineDescriptor();
    var adapter = new OcraCredentialPersistenceAdapter();
    var record = adapter.serialize(descriptor);
    var credential = VersionedCredentialRecordMapper.toCredential(record);
    java.util.Map<String, String> corruptedAttributes =
        new java.util.HashMap<>(credential.attributes());
    corruptedAttributes.put("bad", null);
    Credential corrupted =
        new Credential(
            credential.name(),
            credential.type(),
            credential.secret(),
            corruptedAttributes,
            credential.createdAt(),
            credential.updatedAt());

    CredentialStore corruptedStore =
        new CredentialStore() {
          @Override
          public void save(Credential credential) {
            throw new UnsupportedOperationException();
          }

          @Override
          public Optional<Credential> findByName(String name) {
            if (descriptor.name().equals(name)) {
              return Optional.of(corrupted);
            }
            return Optional.empty();
          }

          @Override
          public List<Credential> findAll() {
            return List.of(corrupted);
          }

          @Override
          public boolean delete(String name) {
            return false;
          }

          @Override
          public void close() {
            // no-op
          }
        };

    OcraVerificationApplicationService applicationService =
        new OcraVerificationApplicationService(
            FIXED_CLOCK,
            credentialId ->
                descriptor.name().equals(credentialId) ? Optional.of(descriptor) : Optional.empty(),
            corruptedStore);
    OcraVerificationService service = new OcraVerificationService(applicationService);

    OcraVerificationRequest request = storedRequest(descriptor.name());

    OcraVerificationValidationException exception =
        assertThrows(
            OcraVerificationValidationException.class,
            () ->
                service.verify(
                    request, new OcraVerificationAuditContext("req-corrupted", null, null)));

    assertEquals("unexpected_error", exception.reasonCode());
    assertTelemetryMessageContains("unexpected_error");
  }

  @Test
  @DisplayName("stored verification with extraneous timestamp returns validation error")
  void storedVerificationValidationFailure() {
    InMemoryCredentialStore store = new InMemoryCredentialStore();
    OcraCredentialDescriptor descriptor = inlineDescriptor();
    var persistenceAdapter = new OcraCredentialPersistenceAdapter();
    store.save(
        VersionedCredentialRecordMapper.toCredential(persistenceAdapter.serialize(descriptor)));

    OcraVerificationService service = service(store);

    OcraVerificationContext invalidContext =
        new OcraVerificationContext("12345678", null, null, null, "00FF", null, null);
    OcraVerificationRequest request =
        new OcraVerificationRequest(buildMatchingOtp(), descriptor.name(), null, invalidContext);

    OcraVerificationValidationException exception =
        assertThrows(
            OcraVerificationValidationException.class,
            () ->
                service.verify(
                    request, new OcraVerificationAuditContext("req-invalid-ts", null, null)));

    assertEquals("validation_error", exception.reasonCode());
    assertTelemetryMessageContains("validation_failure");
  }

  private OcraVerificationService service() {
    return service(null);
  }

  private OcraVerificationService service(CredentialStore store) {
    OcraVerificationApplicationService applicationService =
        new OcraVerificationApplicationService(
            FIXED_CLOCK,
            store != null
                ? OcraCredentialResolvers.forVerificationStore(store)
                : OcraCredentialResolvers.emptyVerificationResolver(),
            store);
    return new OcraVerificationService(applicationService);
  }

  private OcraVerificationRequest inlineRequest(String otp) {
    return new OcraVerificationRequest(otp, null, inlineCredential(), context());
  }

  private OcraVerificationRequest storedRequest(String credentialId) {
    return new OcraVerificationRequest("123456", credentialId, null, context());
  }

  private OcraVerificationInlineCredential inlineCredential() {
    return new OcraVerificationInlineCredential(
        "OCRA-1:HOTP-SHA1-6:QN08", "31323334353637383930313233343536");
  }

  private OcraVerificationContext context() {
    return new OcraVerificationContext("12345678", null, null, null, null, null, null);
  }

  private OcraCredentialDescriptor inlineDescriptor() {
    return new OcraCredentialFactory()
        .createDescriptor(
            new OcraCredentialFactory.OcraCredentialRequest(
                "stored-token",
                "OCRA-1:HOTP-SHA1-6:QN08",
                "31323334353637383930313233343536",
                SecretEncoding.HEX,
                null,
                null,
                null,
                Map.of("source", "service-test")));
  }

  private String buildMatchingOtp() {
    OcraCredentialDescriptor descriptor = inlineDescriptor();
    OcraResponseCalculator.OcraExecutionContext executionContext =
        new OcraResponseCalculator.OcraExecutionContext(
            null, "12345678", null, null, null, null, null);
    return OcraResponseCalculator.generate(descriptor, executionContext);
  }

  private void assertTelemetryMessageContains(String token) {
    boolean matched =
        handler.records.stream().map(LogRecord::getMessage).anyMatch(msg -> msg.contains(token));
    if (!matched) {
      throw new AssertionError("Expected telemetry message containing: " + token);
    }
  }

  private static final class TestHandler extends Handler {
    private final List<LogRecord> records = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      records.add(record);
    }

    @Override
    public void flush() {
      // no-op
    }

    @Override
    public void close() {
      records.clear();
    }
  }

  private static final class InMemoryCredentialStore implements CredentialStore {
    private final Map<String, Credential> storage = new HashMap<>();

    @Override
    public void save(Credential credential) {
      storage.put(credential.name(), credential);
    }

    @Override
    public Optional<Credential> findByName(String name) {
      return Optional.ofNullable(storage.get(name));
    }

    @Override
    public List<Credential> findAll() {
      return List.copyOf(storage.values());
    }

    @Override
    public boolean delete(String name) {
      return storage.remove(name) != null;
    }

    @Override
    public void close() {
      storage.clear();
    }
  }

  private static final class FailingCredentialStore implements CredentialStore {
    @Override
    public void save(Credential credential) {
      throw new UnsupportedOperationException("save not supported");
    }

    @Override
    public Optional<Credential> findByName(String name) {
      throw new IllegalStateException("store offline");
    }

    @Override
    public List<Credential> findAll() {
      return List.of();
    }

    @Override
    public boolean delete(String name) {
      return false;
    }

    @Override
    public void close() {
      // no-op
    }
  }
}
