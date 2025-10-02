package io.openauth.sim.rest.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openauth.sim.application.ocra.OcraCredentialResolvers;
import io.openauth.sim.application.ocra.OcraVerificationApplicationService;
import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OcraVerificationServiceBranchCoverageTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2025-10-01T00:00:00Z"), ZoneOffset.UTC);
  private static final String DEFAULT_SECRET_HEX = "31323334353637383930313233343536";

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
  }

  @Test
  @DisplayName("corrupted stored credential yields unexpected error validation")
  void corruptedStoredCredentialTriggersUnexpected() {
    OcraCredentialDescriptor descriptor = inlineDescriptor();
    var adapter = new OcraCredentialPersistenceAdapter();
    var record = adapter.serialize(descriptor);
    var credential = VersionedCredentialRecordMapper.toCredential(record);
    Map<String, String> corruptedAttributes = new HashMap<>(credential.attributes());
    corruptedAttributes.put("bad", null);
    Credential corrupted =
        new Credential(
            credential.name(),
            credential.type(),
            credential.secret(),
            corruptedAttributes,
            credential.createdAt(),
            credential.updatedAt());

    OcraVerificationApplicationService.CredentialResolver resolver =
        credentialId ->
            descriptor.name().equals(credentialId) ? Optional.of(descriptor) : Optional.empty();
    CredentialStore store = new SingleCredentialStore(corrupted);
    RecordingTelemetry telemetry = new RecordingTelemetry();
    OcraVerificationApplicationService applicationService =
        new OcraVerificationApplicationService(FIXED_CLOCK, resolver, store);
    OcraVerificationService service = new OcraVerificationService(applicationService, telemetry);

    OcraVerificationRequest request = storedRequest(descriptor.name());

    OcraVerificationValidationException exception =
        assertThrows(
            OcraVerificationValidationException.class,
            () ->
                service.verify(
                    request, new OcraVerificationAuditContext("req-corrupted", null, null)));

    assertEquals("unexpected_error", exception.reasonCode());
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
    return new OcraVerificationService(applicationService, new OcraVerificationTelemetry());
  }

  private OcraVerificationRequest inlineRequest(String otp) {
    return new OcraVerificationRequest(otp, null, inlineCredential(), context());
  }

  private OcraVerificationRequest storedRequest(String credentialId) {
    return new OcraVerificationRequest("123456", credentialId, null, context());
  }

  private OcraVerificationInlineCredential inlineCredential() {
    return new OcraVerificationInlineCredential("OCRA-1:HOTP-SHA1-6:QN08", DEFAULT_SECRET_HEX);
  }

  private OcraVerificationContext context() {
    return new OcraVerificationContext("12345678", null, null, null, null, null, null);
  }

  private OcraCredentialDescriptor inlineDescriptor() {
    return new OcraCredentialFactory()
        .createDescriptor(
            new OcraCredentialRequest(
                "stored-token",
                "OCRA-1:HOTP-SHA1-6:QN08",
                DEFAULT_SECRET_HEX,
                SecretEncoding.HEX,
                null,
                null,
                null,
                Map.of("source", "branch-test")));
  }

  private String buildMatchingOtp() {
    OcraCredentialDescriptor descriptor = inlineDescriptor();
    OcraResponseCalculator.OcraExecutionContext executionContext =
        new OcraResponseCalculator.OcraExecutionContext(
            null, "12345678", null, null, null, null, null);
    return OcraResponseCalculator.generate(descriptor, executionContext);
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

  private static final class SingleCredentialStore implements CredentialStore {
    private final Credential credential;

    private SingleCredentialStore(Credential credential) {
      this.credential = credential;
    }

    @Override
    public void save(Credential credential) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Credential> findByName(String name) {
      return Optional.of(credential);
    }

    @Override
    public List<Credential> findAll() {
      return List.of(credential);
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

  private static final class RecordingTelemetry extends OcraVerificationTelemetry {
    private final List<String> messages = new ArrayList<>();

    @Override
    void recordValidationFailure(
        OcraVerificationAuditContext context, TelemetryFrame frame, String reason) {
      messages.add(frame.reasonCode());
    }

    @Override
    void recordUnexpectedError(
        OcraVerificationAuditContext context, TelemetryFrame frame, String reason) {
      messages.add(frame.reasonCode());
    }
  }
}
