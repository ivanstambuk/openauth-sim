package io.openauth.sim.rest.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.store.CredentialStore;
import java.lang.reflect.InvocationTargetException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;

class OcraVerificationServiceTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2025-10-01T00:00:00Z"), ZoneOffset.UTC);

  private final TestHandler handler = new TestHandler();
  private final Logger logger = Logger.getLogger("io.openauth.sim.rest.ocra.telemetry");
  private final OcraVerificationTelemetry telemetry = new OcraVerificationTelemetry();

  @BeforeEach
  void attachHandler() {
    logger.addHandler(handler);
    logger.setLevel(Level.ALL);
  }

  @AfterEach
  void detachHandler() {
    logger.removeHandler(handler);
    handler.records.clear();
  }

  @Test
  @DisplayName("inline verification returns match and records hashed telemetry")
  void inlineVerificationMatch() {
    OcraVerificationService service =
        new OcraVerificationService(telemetry, provider(null), provider(FIXED_CLOCK));

    OcraVerificationRequest request = inlineRequest(buildMatchingOtp());
    OcraVerificationAuditContext auditContext =
        new OcraVerificationAuditContext("req-inline-1", "client-77", "operator-a");

    OcraVerificationResponse response = service.verify(request, auditContext);

    assertEquals("match", response.status());
    assertEquals("match", response.reasonCode());
    assertNotNull(response.metadata().contextFingerprint());

    assertTrueMessageLogged("status=match");
    assertTrueMessageLogged("otpHash=");
    assertTrueMessageLogged("contextFingerprint=");
  }

  @Test
  @DisplayName("inline verification returns mismatch and records strict mismatch telemetry")
  void inlineVerificationMismatch() {
    OcraVerificationService service =
        new OcraVerificationService(telemetry, provider(null), provider(FIXED_CLOCK));

    OcraVerificationRequest request = inlineRequest("999999");
    OcraVerificationAuditContext auditContext =
        new OcraVerificationAuditContext("req-inline-2", null, null);

    OcraVerificationResponse response = service.verify(request, auditContext);

    assertEquals("mismatch", response.status());
    assertEquals("strict_mismatch", response.reasonCode());
    assertTrueMessageLogged("status=mismatch");
    assertTrueMessageLogged("reasonCode=strict_mismatch");
  }

  @Test
  @DisplayName(
      "inline suite requiring timestamp without timestamp triggers replay validation failure")
  void inlineVerificationMissingTimestampRequired() {
    OcraVerificationService service =
        new OcraVerificationService(telemetry, provider(null), provider(FIXED_CLOCK));

    OcraVerificationInlineCredential inlineCredential =
        new OcraVerificationInlineCredential(
            "OCRA-1:HOTP-SHA1-6:QN08-T1M", "31323334353637383930313233343536");
    OcraVerificationContext context =
        new OcraVerificationContext("12345678", null, null, null, null, null, null);
    OcraVerificationRequest request =
        new OcraVerificationRequest("123456", null, inlineCredential, context);
    OcraVerificationAuditContext auditContext =
        new OcraVerificationAuditContext("req-inline-timestamp", "client-199", "operator-t");

    OcraVerificationValidationException exception =
        assertThrows(
            OcraVerificationValidationException.class, () -> service.verify(request, auditContext));

    assertEquals("validation_failure", exception.reasonCode());
    assertTrueMessageLogged("status=invalid");
    assertTrueMessageLogged("reasonCode=validation_failure");
  }

  @Test
  @DisplayName("stored credential verification succeeds and emits stored telemetry")
  void storedVerificationMatch() {
    InMemoryCredentialStore store = new InMemoryCredentialStore();
    OcraCredentialDescriptor descriptor = inlineDescriptor();
    OcraCredentialPersistenceAdapter adapter = new OcraCredentialPersistenceAdapter();
    io.openauth.sim.core.store.serialization.VersionedCredentialRecord record =
        adapter.serialize(descriptor);
    store.save(
        io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper.toCredential(
            record));

    OcraVerificationService service =
        new OcraVerificationService(telemetry, provider(store), provider(FIXED_CLOCK));

    OcraVerificationContext context =
        new OcraVerificationContext("12345678", null, null, null, null, null, null);
    OcraVerificationRequest request =
        new OcraVerificationRequest(buildMatchingOtp(), descriptor.name(), null, context);
    OcraVerificationAuditContext auditContext =
        new OcraVerificationAuditContext("req-stored-2", "client-101", "operator-c");

    OcraVerificationResponse response = service.verify(request, auditContext);

    assertEquals("match", response.status());
    assertEquals("stored", response.metadata().credentialSource());
    assertTrueMessageLogged("credentialSource=stored");
  }

  @Test
  @DisplayName("missing stored credential emits telemetry and throws validation error")
  void storedCredentialNotFound() {
    InMemoryCredentialStore store = new InMemoryCredentialStore();
    OcraVerificationService service =
        new OcraVerificationService(telemetry, provider(store), provider(FIXED_CLOCK));

    OcraVerificationRequest request = storedRequest("missing-credential");
    OcraVerificationAuditContext auditContext =
        new OcraVerificationAuditContext("req-stored-1", null, "operator-b");

    OcraVerificationValidationException exception =
        assertThrows(
            OcraVerificationValidationException.class, () -> service.verify(request, auditContext));

    assertEquals("credential_not_found", exception.reasonCode());
    assertTrueMessageLogged("status=invalid");
    assertTrueMessageLogged("reasonCode=credential_not_found");
  }

  @Test
  @DisplayName("stored verification without store emits credential_not_found telemetry")
  void storedVerificationWithoutStore() {
    OcraVerificationService service =
        new OcraVerificationService(telemetry, provider(null), provider(FIXED_CLOCK));

    OcraVerificationRequest request = storedRequest("inline-test");
    OcraVerificationAuditContext auditContext =
        new OcraVerificationAuditContext("req-stored-3", null, null);

    OcraVerificationValidationException exception =
        assertThrows(
            OcraVerificationValidationException.class, () -> service.verify(request, auditContext));

    assertEquals("credential_not_found", exception.reasonCode());
    assertTrueMessageLogged("reasonCode=credential_not_found");
  }

  @Test
  @DisplayName(
      "stored credential disappearing between reads surfaces credential_not_found via replay")
  void storedVerificationCredentialDisappearsDuringReplay() {
    FlakyCredentialStore store = new FlakyCredentialStore();
    store.setAfterFirstLookupBehavior(AfterFirstLookupBehavior.RETURN_EMPTY);

    OcraCredentialDescriptor descriptor = inlineDescriptor();
    OcraCredentialPersistenceAdapter adapter = new OcraCredentialPersistenceAdapter();
    io.openauth.sim.core.store.serialization.VersionedCredentialRecord record =
        adapter.serialize(descriptor);
    store.save(
        io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper.toCredential(
            record));

    OcraVerificationService service =
        new OcraVerificationService(telemetry, provider(store), provider(FIXED_CLOCK));

    OcraVerificationRequest request = storedRequest(descriptor.name());
    OcraVerificationAuditContext auditContext =
        new OcraVerificationAuditContext("req-stored-race", "client-202", "operator-race");

    OcraVerificationValidationException exception =
        assertThrows(
            OcraVerificationValidationException.class, () -> service.verify(request, auditContext));

    assertEquals("credential_not_found", exception.reasonCode());
    assertTrueMessageLogged("credentialSource=stored");
    assertTrueMessageLogged("outcome=invalid");
  }

  @Test
  @DisplayName("credential_not_found telemetry handles null request fallback")
  void credentialNotFoundTelemetryNullRequest() throws Exception {
    OcraVerificationService service =
        new OcraVerificationService(telemetry, provider(null), provider(FIXED_CLOCK));

    java.lang.reflect.Method emitter =
        OcraVerificationService.class.getDeclaredMethod(
            "emitCredentialNotFoundTelemetry",
            OcraVerificationAuditContext.class,
            String.class,
            Class.forName("io.openauth.sim.rest.ocra.OcraVerificationService$NormalizedRequest"),
            String.class,
            long.class);
    emitter.setAccessible(true);

    emitter.invoke(
        service,
        new OcraVerificationAuditContext("audit-null", null, null),
        "telemetry-null",
        null,
        "credential store not configured",
        4L);

    assertTrueMessageLogged("credentialSource=stored");
    assertTrueMessageLogged("credentialId=unknown");
    assertTrueMessageLogged("otpHash=unavailable");
  }

  @Test
  @DisplayName("stored verification unexpected error surfaces sanitized telemetry")
  void storedVerificationUnexpectedErrorTelemetry() {
    OcraCredentialDescriptor descriptor = inlineDescriptor();
    OcraCredentialPersistenceAdapter adapter = new OcraCredentialPersistenceAdapter();
    io.openauth.sim.core.store.serialization.VersionedCredentialRecord record =
        adapter.serialize(descriptor);
    Credential valid =
        io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper.toCredential(
            record);

    java.util.Map<String, String> corruptedAttributes = new java.util.HashMap<>(valid.attributes());
    corruptedAttributes.put(
        io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter.ATTR_METADATA_PREFIX
            + "source",
        null);

    Credential corrupted =
        new Credential(
            valid.name(),
            valid.type(),
            valid.secret(),
            corruptedAttributes,
            valid.createdAt(),
            valid.updatedAt());

    CorruptingCredentialStore store = new CorruptingCredentialStore(valid, corrupted);

    OcraVerificationService service =
        new OcraVerificationService(telemetry, provider(store), provider(FIXED_CLOCK));

    OcraVerificationRequest request = storedRequest(descriptor.name());
    OcraVerificationAuditContext auditContext =
        new OcraVerificationAuditContext("req-stored-unexpected", "client-303", "operator-x");

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> service.verify(request, auditContext));

    assertEquals("Unexpected error during OCRA verification", exception.getMessage());
    assertTrueMessageLogged("reasonCode=unexpected_error");
    assertTrueMessageLogged("status=error");
  }

  @Test
  @DisplayName("inline verification missing challenge surfaces validation failure telemetry")
  void inlineVerificationMissingChallenge() {
    OcraVerificationService service =
        new OcraVerificationService(telemetry, provider(null), provider(FIXED_CLOCK));

    OcraVerificationInlineCredential inlineCredential =
        new OcraVerificationInlineCredential(
            "OCRA-1:HOTP-SHA1-6:QN08", "31323334353637383930313233343536");
    OcraVerificationContext context =
        new OcraVerificationContext(null, null, null, null, null, null, null);
    OcraVerificationRequest request =
        new OcraVerificationRequest("123456", null, inlineCredential, context);
    OcraVerificationAuditContext auditContext =
        new OcraVerificationAuditContext("req-inline-3", null, null);

    OcraVerificationValidationException exception =
        assertThrows(
            OcraVerificationValidationException.class, () -> service.verify(request, auditContext));

    assertEquals("validation_failure", exception.reasonCode());
    assertTrueMessageLogged("reasonCode=validation_failure");
  }

  @Test
  @DisplayName("inline secret with invalid hex triggers validation failure via service catch")
  void inlineSecretInvalidHexValidation() {
    OcraVerificationService service =
        new OcraVerificationService(telemetry, provider(null), provider(FIXED_CLOCK));

    OcraVerificationInlineCredential inlineCredential =
        new OcraVerificationInlineCredential("OCRA-1:HOTP-SHA1-6:QN08", "XYZ123");
    OcraVerificationContext context =
        new OcraVerificationContext("12345678", null, null, null, null, null, null);
    OcraVerificationRequest request =
        new OcraVerificationRequest("654321", null, inlineCredential, context);
    OcraVerificationAuditContext auditContext =
        new OcraVerificationAuditContext("req-inline-invalid-hex", null, "operator-hex");

    OcraVerificationValidationException exception =
        assertThrows(
            OcraVerificationValidationException.class, () -> service.verify(request, auditContext));

    assertEquals("validation_failure", exception.reasonCode());
    assertTrueMessageLogged("status=invalid");
    assertTrueMessageLogged("reasonCode=validation_failure");
  }

  @Test
  @DisplayName("inline verification with invalid challenge triggers replay validation failure")
  void inlineVerificationInvalidChallengeLength() {
    OcraVerificationService service =
        new OcraVerificationService(telemetry, provider(null), provider(FIXED_CLOCK));
    OcraVerificationInlineCredential inlineCredential =
        new OcraVerificationInlineCredential(
            "OCRA-1:HOTP-SHA1-6:QN08", "31323334353637383930313233343536");
    OcraVerificationContext context =
        new OcraVerificationContext("ABC", null, null, null, null, null, null);
    OcraVerificationRequest request =
        new OcraVerificationRequest("123456", null, inlineCredential, context);

    OcraVerificationAuditContext auditContext =
        new OcraVerificationAuditContext("req-invalid-challenge", null, null);

    OcraVerificationValidationException exception =
        assertThrows(
            OcraVerificationValidationException.class, () -> service.verify(request, auditContext));

    assertEquals("validation_failure", exception.reasonCode());
  }

  @Test
  @DisplayName("missing OTP raises otp_missing validation error")
  void otpMissingValidation() {
    OcraVerificationService service =
        new OcraVerificationService(telemetry, provider(null), provider(FIXED_CLOCK));
    OcraVerificationContext context =
        new OcraVerificationContext("12345678", null, null, null, null, null, null);
    OcraVerificationRequest request = new OcraVerificationRequest(null, "cred-1", null, context);

    OcraVerificationValidationException exception =
        assertThrows(
            OcraVerificationValidationException.class,
            () ->
                service.verify(
                    request, new OcraVerificationAuditContext("req-missing-otp", null, null)));

    assertEquals("otp_missing", exception.reasonCode());
  }

  @Test
  @DisplayName("whitespace OTP raises otp_missing validation error and records sanitized telemetry")
  void otpWhitespaceValidation() {
    OcraVerificationService service =
        new OcraVerificationService(telemetry, provider(null), provider(FIXED_CLOCK));
    OcraVerificationContext context =
        new OcraVerificationContext("12345678", null, null, null, null, null, null);
    OcraVerificationRequest request =
        new OcraVerificationRequest("    ", "cred-otp", null, context);

    OcraVerificationValidationException exception =
        assertThrows(
            OcraVerificationValidationException.class,
            () ->
                service.verify(
                    request, new OcraVerificationAuditContext("req-otp-whitespace", null, null)));

    assertEquals("otp_missing", exception.reasonCode());
    assertTrueMessageLogged("otpHash=unavailable");
  }

  @Test
  @DisplayName("credential conflict validation surfaces credential_conflict reason")
  void credentialConflictValidation() {
    OcraVerificationService service =
        new OcraVerificationService(telemetry, provider(null), provider(FIXED_CLOCK));
    OcraVerificationInlineCredential inlineCredential =
        new OcraVerificationInlineCredential(
            "OCRA-1:HOTP-SHA1-6:QN08", "31323334353637383930313233343536");
    OcraVerificationContext context =
        new OcraVerificationContext("12345678", null, null, null, null, null, null);

    OcraVerificationRequest request =
        new OcraVerificationRequest("123456", "cred-2", inlineCredential, context);

    OcraVerificationValidationException exception =
        assertThrows(
            OcraVerificationValidationException.class,
            () ->
                service.verify(
                    request, new OcraVerificationAuditContext("req-conflict", null, null)));

    assertEquals("credential_conflict", exception.reasonCode());
  }

  @Test
  @DisplayName("credential missing validation surfaces credential_missing reason")
  void credentialMissingValidation() {
    OcraVerificationService service =
        new OcraVerificationService(telemetry, provider(null), provider(FIXED_CLOCK));
    OcraVerificationContext context =
        new OcraVerificationContext("12345678", null, null, null, null, null, null);
    OcraVerificationRequest request = new OcraVerificationRequest("123456", null, null, context);

    OcraVerificationValidationException exception =
        assertThrows(
            OcraVerificationValidationException.class,
            () ->
                service.verify(
                    request, new OcraVerificationAuditContext("req-missing", null, null)));

    assertEquals("credential_missing", exception.reasonCode());
  }

  @Test
  @DisplayName("inline credential missing suite surfaces suite_missing reason")
  void inlineSuiteMissingValidation() {
    OcraVerificationService service =
        new OcraVerificationService(telemetry, provider(null), provider(FIXED_CLOCK));
    OcraVerificationInlineCredential inlineCredential =
        new OcraVerificationInlineCredential(null, "31323334");
    OcraVerificationContext context =
        new OcraVerificationContext("12345678", null, null, null, null, null, null);

    OcraVerificationRequest request =
        new OcraVerificationRequest("123456", null, inlineCredential, context);

    OcraVerificationValidationException exception =
        assertThrows(
            OcraVerificationValidationException.class,
            () ->
                service.verify(request, new OcraVerificationAuditContext("req-suite", null, null)));

    assertEquals("suite_missing", exception.reasonCode());
  }

  @Test
  @DisplayName("inline credential blank suite surfaces suite_missing reason")
  void inlineSuiteBlankValidation() {
    OcraVerificationService service =
        new OcraVerificationService(telemetry, provider(null), provider(FIXED_CLOCK));
    OcraVerificationInlineCredential inlineCredential =
        new OcraVerificationInlineCredential("   ", "31323334");
    OcraVerificationContext context =
        new OcraVerificationContext("12345678", null, null, null, null, null, null);

    OcraVerificationRequest request =
        new OcraVerificationRequest("123456", null, inlineCredential, context);

    OcraVerificationValidationException exception =
        assertThrows(
            OcraVerificationValidationException.class,
            () ->
                service.verify(
                    request, new OcraVerificationAuditContext("req-suite-blank", null, null)));

    assertEquals("suite_missing", exception.reasonCode());
  }

  @Test
  @DisplayName("inline credential missing secret surfaces secret_missing reason")
  void inlineSecretMissingValidation() {
    OcraVerificationService service =
        new OcraVerificationService(telemetry, provider(null), provider(FIXED_CLOCK));
    OcraVerificationInlineCredential inlineCredential =
        new OcraVerificationInlineCredential("OCRA-1:HOTP-SHA1-6:QN08", "   ");
    OcraVerificationContext context =
        new OcraVerificationContext("12345678", null, null, null, null, null, null);

    OcraVerificationRequest request =
        new OcraVerificationRequest("123456", null, inlineCredential, context);

    OcraVerificationValidationException exception =
        assertThrows(
            OcraVerificationValidationException.class,
            () ->
                service.verify(
                    request, new OcraVerificationAuditContext("req-secret", null, null)));

    assertEquals("secret_missing", exception.reasonCode());
  }

  @Test
  @DisplayName("inline credential object missing triggers inline_secret_missing problem")
  void inlineCredentialObjectMissingValidation() throws Exception {
    Class<?> verificationContextClass =
        Class.forName("io.openauth.sim.rest.ocra.OcraVerificationService$VerificationContext");
    Class<?> inlineSecretClass =
        Class.forName("io.openauth.sim.rest.ocra.OcraVerificationService$InlineSecret");

    java.lang.reflect.Method emptyMethod = verificationContextClass.getDeclaredMethod("empty");
    emptyMethod.setAccessible(true);
    Object context = emptyMethod.invoke(null);

    java.lang.reflect.Method fromMethod =
        inlineSecretClass.getDeclaredMethod(
            "from", OcraVerificationInlineCredential.class, verificationContextClass);
    fromMethod.setAccessible(true);

    InvocationTargetException exception =
        assertThrows(InvocationTargetException.class, () -> fromMethod.invoke(null, null, context));

    Throwable cause = exception.getCause();
    assertEquals("inlineCredential.sharedSecretHex is required", cause.getMessage());
  }

  @Test
  @DisplayName("missing context surfaces context_missing reason")
  void contextMissingValidation() {
    OcraVerificationService service =
        new OcraVerificationService(telemetry, provider(null), provider(FIXED_CLOCK));
    OcraVerificationInlineCredential inlineCredential =
        new OcraVerificationInlineCredential(
            "OCRA-1:HOTP-SHA1-6:QN08", "31323334353637383930313233343536");

    OcraVerificationRequest request =
        new OcraVerificationRequest("123456", null, inlineCredential, null);

    OcraVerificationValidationException exception =
        assertThrows(
            OcraVerificationValidationException.class,
            () ->
                service.verify(
                    request, new OcraVerificationAuditContext("req-context", null, null)));

    assertEquals("context_missing", exception.reasonCode());
  }

  @Test
  @DisplayName("unexpected strict mismatch state triggers error telemetry and exception")
  void handleInvalidStrictMismatchUnexpectedState() throws Exception {
    OcraVerificationService service =
        new OcraVerificationService(telemetry, provider(null), provider(FIXED_CLOCK));

    Object normalized = normalizedRequestFor(inlineRequest(buildMatchingOtp()));
    java.lang.reflect.Method method =
        OcraVerificationService.class.getDeclaredMethod(
            "handleInvalid",
            normalized.getClass(),
            OcraCredentialDescriptor.class,
            String.class,
            String.class,
            OcraReplayVerifier.OcraVerificationReason.class,
            String.class,
            OcraVerificationAuditContext.class,
            long.class);
    method.setAccessible(true);

    InvocationTargetException thrown =
        assertThrows(
            InvocationTargetException.class,
            () ->
                method.invoke(
                    service,
                    normalized,
                    inlineDescriptor(),
                    "inline",
                    "inline-test",
                    OcraReplayVerifier.OcraVerificationReason.STRICT_MISMATCH,
                    "telemetry-strict",
                    new OcraVerificationAuditContext("audit-strict", null, null),
                    3L));

    assertTrue(thrown.getCause() instanceof IllegalStateException);
    assertTrue(thrown.getCause().getMessage().contains("Unexpected verification state"));
    assertTrueMessageLogged("reasonCode=unexpected_state");
    assertTrueMessageLogged("status=error");
  }

  @Test
  @DisplayName("unexpected match state triggers error telemetry and exception")
  void handleInvalidMatchUnexpectedState() throws Exception {
    OcraVerificationService service =
        new OcraVerificationService(telemetry, provider(null), provider(FIXED_CLOCK));

    Object normalized = normalizedRequestFor(inlineRequest(buildMatchingOtp()));
    java.lang.reflect.Method method =
        OcraVerificationService.class.getDeclaredMethod(
            "handleInvalid",
            normalized.getClass(),
            OcraCredentialDescriptor.class,
            String.class,
            String.class,
            OcraReplayVerifier.OcraVerificationReason.class,
            String.class,
            OcraVerificationAuditContext.class,
            long.class);
    method.setAccessible(true);

    InvocationTargetException thrown =
        assertThrows(
            InvocationTargetException.class,
            () ->
                method.invoke(
                    service,
                    normalized,
                    inlineDescriptor(),
                    "inline",
                    "inline-test",
                    OcraReplayVerifier.OcraVerificationReason.MATCH,
                    "telemetry-match",
                    new OcraVerificationAuditContext("audit-match", null, null),
                    2L));

    assertTrue(thrown.getCause() instanceof IllegalStateException);
    assertTrue(thrown.getCause().getMessage().contains("Unexpected verification state"));
    assertTrueMessageLogged("reasonCode=unexpected_state");
    assertTrueMessageLogged("status=error");
  }

  private OcraVerificationRequest inlineRequest(String otp) {
    OcraVerificationInlineCredential inlineCredential =
        new OcraVerificationInlineCredential(
            "OCRA-1:HOTP-SHA1-6:QN08", "31323334353637383930313233343536");
    OcraVerificationContext context =
        new OcraVerificationContext("12345678", null, null, null, null, null, null);
    return new OcraVerificationRequest(otp, null, inlineCredential, context);
  }

  private OcraVerificationRequest storedRequest(String credentialId) {
    OcraVerificationContext context =
        new OcraVerificationContext("12345678", null, null, null, null, null, null);
    return new OcraVerificationRequest("123456", credentialId, null, context);
  }

  private Object normalizedRequestFor(OcraVerificationRequest request) {
    try {
      Class<?> normalizedClass =
          Class.forName("io.openauth.sim.rest.ocra.OcraVerificationService$NormalizedRequest");
      java.lang.reflect.Method from =
          normalizedClass.getDeclaredMethod(
              "from", io.openauth.sim.rest.ocra.OcraVerificationRequest.class);
      from.setAccessible(true);
      return from.invoke(null, request);
    } catch (ClassNotFoundException
        | NoSuchMethodException
        | IllegalAccessException
        | InvocationTargetException ex) {
      throw new AssertionError("Unable to construct NormalizedRequest", ex);
    }
  }

  private OcraCredentialDescriptor inlineDescriptor() {
    OcraCredentialFactory factory = new OcraCredentialFactory();
    return factory.createDescriptor(
        new OcraCredentialFactory.OcraCredentialRequest(
            "inline-test",
            "OCRA-1:HOTP-SHA1-6:QN08",
            "31323334353637383930313233343536",
            SecretEncoding.HEX,
            null,
            null,
            null,
            Map.of()));
  }

  private String buildMatchingOtp() {
    OcraCredentialDescriptor descriptor = inlineDescriptor();
    OcraResponseCalculator.OcraExecutionContext executionContext =
        new OcraResponseCalculator.OcraExecutionContext(
            null, "12345678", null, null, null, null, null);
    return OcraResponseCalculator.generate(descriptor, executionContext);
  }

  private void assertTrueMessageLogged(String contains) {
    boolean matched =
        handler.records.stream().map(LogRecord::getMessage).anyMatch(msg -> msg.contains(contains));
    if (!matched) {
      throw new AssertionError("Expected telemetry message containing: " + contains);
    }
  }

  private static <T> ObjectProvider<T> provider(T instance) {
    return new SimpleObjectProvider<>(instance);
  }

  private static final class SimpleObjectProvider<T> implements ObjectProvider<T> {

    private final T instance;

    private SimpleObjectProvider(T instance) {
      this.instance = instance;
    }

    @Override
    public T getObject(Object... args) {
      if (instance == null) {
        throw new NoSuchBeanDefinitionException("test", "No instance available");
      }
      return instance;
    }

    @Override
    public T getObject() {
      return getObject(new Object[0]);
    }

    @Override
    public T getIfAvailable() {
      return instance;
    }

    @Override
    public T getIfAvailable(java.util.function.Supplier<T> supplier) {
      return instance != null ? instance : supplier.get();
    }

    @Override
    public T getIfUnique() {
      return instance;
    }

    @Override
    public T getIfUnique(java.util.function.Supplier<T> supplier) {
      return instance != null ? instance : supplier.get();
    }

    @Override
    public java.util.stream.Stream<T> stream() {
      return instance == null
          ? java.util.stream.Stream.empty()
          : java.util.stream.Stream.of(instance);
    }

    @Override
    public java.util.stream.Stream<T> orderedStream() {
      return stream();
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
    private final java.util.Map<String, Credential> storage = new java.util.HashMap<>();

    @Override
    public void save(Credential credential) {
      storage.put(credential.name(), credential);
    }

    @Override
    public Optional<Credential> findByName(String name) {
      return Optional.ofNullable(storage.get(name));
    }

    @Override
    public java.util.List<Credential> findAll() {
      return java.util.List.copyOf(storage.values());
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

  private static final class FlakyCredentialStore implements CredentialStore {
    private final java.util.Map<String, Credential> storage = new java.util.HashMap<>();
    private AfterFirstLookupBehavior afterFirstLookup = AfterFirstLookupBehavior.STABLE;
    private boolean servedInitialLookup;

    void setAfterFirstLookupBehavior(AfterFirstLookupBehavior behavior) {
      this.afterFirstLookup = behavior;
    }

    @Override
    public void save(Credential credential) {
      storage.put(credential.name(), credential);
    }

    @Override
    public Optional<Credential> findByName(String name) {
      if (!servedInitialLookup) {
        servedInitialLookup = true;
        return Optional.ofNullable(storage.get(name));
      }
      return switch (afterFirstLookup) {
        case RETURN_EMPTY -> Optional.empty();
        case STABLE -> Optional.ofNullable(storage.get(name));
      };
    }

    @Override
    public java.util.List<Credential> findAll() {
      return java.util.List.copyOf(storage.values());
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

  private static final class CorruptingCredentialStore implements CredentialStore {
    private final Credential initial;
    private final Credential corrupted;
    private int lookups;

    private CorruptingCredentialStore(Credential initial, Credential corrupted) {
      this.initial = initial;
      this.corrupted = corrupted;
    }

    @Override
    public void save(Credential credential) {
      // no-op: credentials supplied via constructor
    }

    @Override
    public Optional<Credential> findByName(String name) {
      if (lookups == 0) {
        lookups++;
        return Optional.of(initial);
      }
      lookups++;
      return Optional.of(corrupted);
    }

    @Override
    public java.util.List<Credential> findAll() {
      return java.util.List.of(initial, corrupted);
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

  private enum AfterFirstLookupBehavior {
    STABLE,
    RETURN_EMPTY
  }
}
