package io.openauth.sim.rest.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.credentials.ocra.OcraCryptoFunction;
import io.openauth.sim.core.credentials.ocra.OcraDataInput;
import io.openauth.sim.core.credentials.ocra.OcraHashAlgorithm;
import io.openauth.sim.core.credentials.ocra.OcraSuite;
import io.openauth.sim.core.credentials.ocra.OcraTimestampSpecification;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;

class OcraEvaluationServiceTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2025-09-30T12:00:00Z"), ZoneOffset.UTC);
  private static final String DEFAULT_SECRET_HEX = "31323334353637383930313233343536";
  private static final String DEFAULT_SUITE = "OCRA-1:HOTP-SHA1-6:QN08";
  private static final String DEFAULT_CHALLENGE = "12345678";

  @Test
  @DisplayName("inline requests with invalid shared secret hex are rejected with sanitized details")
  void invalidSharedSecretHexRejected() {
    RecordingTelemetry telemetry = new RecordingTelemetry();
    OcraEvaluationService service =
        new OcraEvaluationService(telemetry, provider(FIXED_CLOCK), provider(null));

    OcraEvaluationRequest request =
        new OcraEvaluationRequest(
            null, "OCRA-1:HOTP-SHA1-6:QN08", "GHI", "12345678", null, null, null, null, null, null);

    OcraEvaluationValidationException exception =
        assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

    assertEquals("sharedSecretHex", exception.field());
    assertEquals("not_hexadecimal", exception.reasonCode());
    assertTrue(exception.sanitized());
    assertEquals("OCRA-1:HOTP-SHA1-6:QN08", exception.suite());
    telemetry.assertValidationFailure("not_hexadecimal");
  }

  @Test
  @DisplayName("suite requiring session emits session_required when session payload missing")
  void sessionRequiredFailureWhenMissing() {
    RecordingTelemetry telemetry = new RecordingTelemetry();
    OcraEvaluationService service =
        new OcraEvaluationService(telemetry, provider(FIXED_CLOCK), provider(null));

    OcraEvaluationRequest request =
        new OcraEvaluationRequest(
            null,
            "OCRA-1:HOTPT30SHA256-7:QN08-SH512",
            DEFAULT_SECRET_HEX,
            "12345678",
            null,
            null,
            null,
            null,
            "00000001",
            null);

    OcraEvaluationValidationException exception =
        assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

    assertEquals("sessionHex", exception.field());
    assertEquals("session_required", exception.reasonCode());
    assertTrue(exception.sanitized());
    assertEquals("OCRA-1:HOTPT30SHA256-7:QN08-SH512", exception.suite());
    telemetry.assertValidationFailure("session_required");
  }

  @Test
  @DisplayName("invalid challenge format surfaces challenge_format reason code")
  void challengeFormatFailure() {
    RecordingTelemetry telemetry = new RecordingTelemetry();
    OcraEvaluationService service =
        new OcraEvaluationService(telemetry, provider(FIXED_CLOCK), provider(null));

    OcraEvaluationRequest request =
        new OcraEvaluationRequest(
            null,
            "OCRA-1:HOTP-SHA1-6:QN08",
            DEFAULT_SECRET_HEX,
            "ABCDEFGH",
            null,
            null,
            null,
            null,
            null,
            null);

    OcraEvaluationValidationException exception =
        assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

    assertEquals("challenge", exception.field());
    assertEquals("challenge_format", exception.reasonCode());
    assertTrue(exception.sanitized());
    telemetry.assertValidationFailure("challenge_format");
  }

  @Test
  @DisplayName("timestamp not permitted surfaces timestamp_not_permitted reason")
  void timestampNotPermittedFailure() {
    RecordingTelemetry telemetry = new RecordingTelemetry();
    OcraEvaluationService service =
        new OcraEvaluationService(telemetry, provider(FIXED_CLOCK), provider(null));

    OcraEvaluationRequest request =
        new OcraEvaluationRequest(
            null,
            "OCRA-1:HOTP-SHA1-6:QN08",
            DEFAULT_SECRET_HEX,
            "12345678",
            null,
            null,
            null,
            null,
            "00000001",
            null);

    OcraEvaluationValidationException exception =
        assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

    assertEquals("timestampHex", exception.field());
    assertEquals("timestamp_not_permitted", exception.reasonCode());
    telemetry.assertValidationFailure("timestamp_not_permitted");
  }

  @Test
  @DisplayName("session not permitted surfaces session_not_permitted reason")
  void sessionNotPermittedFailure() {
    RecordingTelemetry telemetry = new RecordingTelemetry();
    OcraEvaluationService service =
        new OcraEvaluationService(telemetry, provider(FIXED_CLOCK), provider(null));

    OcraEvaluationRequest request =
        new OcraEvaluationRequest(
            null,
            DEFAULT_SUITE,
            DEFAULT_SECRET_HEX,
            DEFAULT_CHALLENGE,
            "ABCDEF12",
            null,
            null,
            null,
            null,
            null);

    OcraEvaluationValidationException exception =
        assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

    assertEquals("sessionHex", exception.field());
    assertEquals("session_not_permitted", exception.reasonCode());
    telemetry.assertValidationFailure("session_not_permitted");
  }

  @Test
  @DisplayName("timestamp drift exceeded surfaces timestamp_drift_exceeded reason")
  void timestampDriftExceededFailure() {
    RecordingTelemetry telemetry = new RecordingTelemetry();
    OcraEvaluationService service =
        new OcraEvaluationService(telemetry, provider(FIXED_CLOCK), provider(null));

    OcraEvaluationRequest request =
        new OcraEvaluationRequest(
            null,
            "OCRA-1:HOTPT30SHA256-7:QN08",
            DEFAULT_SECRET_HEX,
            DEFAULT_CHALLENGE,
            null,
            null,
            null,
            null,
            "00000000",
            null);

    OcraEvaluationValidationException exception =
        assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

    assertEquals("timestampHex", exception.field());
    assertEquals("timestamp_drift_exceeded", exception.reasonCode());
    telemetry.assertValidationFailure("timestamp_drift_exceeded");
  }

  @Test
  @DisplayName("challenge shorter than required surfaces challenge_length reason")
  void challengeLengthFailure() {
    RecordingTelemetry telemetry = new RecordingTelemetry();
    OcraEvaluationService service =
        new OcraEvaluationService(telemetry, provider(FIXED_CLOCK), provider(null));

    OcraEvaluationRequest request =
        new OcraEvaluationRequest(
            null, DEFAULT_SUITE, DEFAULT_SECRET_HEX, "1234", null, null, null, null, null, null);

    OcraEvaluationValidationException exception =
        assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

    assertEquals("challenge", exception.field());
    assertEquals("challenge_length", exception.reasonCode());
    telemetry.assertValidationFailure("challenge_length");
  }

  @Test
  @DisplayName("credential reference evaluation succeeds and records telemetry")
  void credentialReferenceEvaluationSucceeds() {
    RecordingTelemetry telemetry = new RecordingTelemetry();
    InMemoryCredentialStore store = new InMemoryCredentialStore();

    OcraCredentialFactory factory = new OcraCredentialFactory();
    OcraCredentialDescriptor descriptor =
        factory.createDescriptor(
            new OcraCredentialRequest(
                "stored-token",
                DEFAULT_SUITE,
                DEFAULT_SECRET_HEX,
                SecretEncoding.HEX,
                null,
                null,
                null,
                Map.of("source", "service-test")));
    VersionedCredentialRecord record = new OcraCredentialPersistenceAdapter().serialize(descriptor);
    store.save(VersionedCredentialRecordMapper.toCredential(record));

    OcraEvaluationService service =
        new OcraEvaluationService(telemetry, provider(FIXED_CLOCK), provider(store));

    OcraEvaluationRequest request =
        new OcraEvaluationRequest(
            "stored-token", null, null, DEFAULT_CHALLENGE, null, null, null, null, null, null);

    OcraEvaluationResponse response = service.evaluate(request);

    assertEquals(DEFAULT_SUITE, response.suite());
    assertTrue(response.otp().length() > 0);
    telemetry.assertSuccessRecorded();
  }

  @Test
  @DisplayName("credential reference missing credential surfaces credential_not_found")
  void credentialReferenceMissingCredential() {
    RecordingTelemetry telemetry = new RecordingTelemetry();
    InMemoryCredentialStore store = new InMemoryCredentialStore();

    OcraEvaluationService service =
        new OcraEvaluationService(telemetry, provider(FIXED_CLOCK), provider(store));

    OcraEvaluationRequest request =
        new OcraEvaluationRequest(
            "missing-token", null, null, DEFAULT_CHALLENGE, null, null, null, null, null, null);

    OcraEvaluationValidationException exception =
        assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

    assertEquals("credentialId", exception.field());
    assertEquals("credential_not_found", exception.reasonCode());
    telemetry.assertValidationFailure("credential_not_found");
  }

  @Test
  @DisplayName("counter-required suites surface counter_required reason")
  void counterRequiredFailure() {
    RecordingTelemetry telemetry = new RecordingTelemetry();
    OcraEvaluationService service =
        new OcraEvaluationService(telemetry, provider(FIXED_CLOCK), provider(null));

    OcraEvaluationRequest request =
        new OcraEvaluationRequest(
            null,
            "OCRA-1:HOTP-SHA1-6:C-QN08",
            DEFAULT_SECRET_HEX,
            "12345678",
            null,
            null,
            null,
            null,
            null,
            null);

    OcraEvaluationValidationException exception =
        assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

    assertEquals("counter", exception.field());
    assertEquals("counter_required", exception.reasonCode());
    telemetry.assertValidationFailure("counter_required");
  }

  @Test
  @DisplayName("timestamp hex validation handles invalid format")
  void timestampInvalidFailure() {
    RecordingTelemetry telemetry = new RecordingTelemetry();
    OcraEvaluationService service =
        new OcraEvaluationService(telemetry, provider(FIXED_CLOCK), provider(null));

    OcraEvaluationRequest request =
        new OcraEvaluationRequest(
            null,
            "OCRA-1:HOTPT30SHA256-7:QN08",
            DEFAULT_SECRET_HEX,
            "12345678",
            null,
            null,
            null,
            null,
            "ZZZZ",
            null);

    OcraEvaluationValidationException exception =
        assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

    assertEquals("timestampHex", exception.field());
    assertEquals("not_hexadecimal", exception.reasonCode());
    telemetry.assertValidationFailure("not_hexadecimal");
  }

  @Test
  @DisplayName("pin hash not permitted surfaces correct reason")
  void pinHashNotPermittedFailure() {
    RecordingTelemetry telemetry = new RecordingTelemetry();
    OcraEvaluationService service =
        new OcraEvaluationService(telemetry, provider(FIXED_CLOCK), provider(null));

    OcraEvaluationRequest request =
        new OcraEvaluationRequest(
            null,
            "OCRA-1:HOTP-SHA1-6:QN08",
            DEFAULT_SECRET_HEX,
            "12345678",
            null,
            null,
            null,
            "5e884898da28047151d0e56f8dc6292773603d0d",
            null,
            null);

    OcraEvaluationValidationException exception =
        assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

    assertEquals("pinHashHex", exception.field());
    assertEquals("pin_hash_not_permitted", exception.reasonCode());
    telemetry.assertValidationFailure("pin_hash_not_permitted");
  }

  @Test
  @DisplayName("challenge required surfaces challenge_required reason")
  void challengeRequiredFailure() {
    RecordingTelemetry telemetry = new RecordingTelemetry();
    OcraEvaluationService service =
        new OcraEvaluationService(telemetry, provider(FIXED_CLOCK), provider(null));

    OcraEvaluationRequest request =
        new OcraEvaluationRequest(
            null, DEFAULT_SUITE, DEFAULT_SECRET_HEX, null, null, null, null, null, null, null);

    OcraEvaluationValidationException exception =
        assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

    assertEquals("challenge", exception.field());
    assertEquals("challenge_required", exception.reasonCode());
    telemetry.assertValidationFailure("challenge_required");
  }

  @Test
  @DisplayName("counter not permitted surfaces counter_not_permitted reason")
  void counterNotPermittedFailure() {
    RecordingTelemetry telemetry = new RecordingTelemetry();
    OcraEvaluationService service =
        new OcraEvaluationService(telemetry, provider(FIXED_CLOCK), provider(null));

    OcraEvaluationRequest request =
        new OcraEvaluationRequest(
            null, DEFAULT_SUITE, DEFAULT_SECRET_HEX, "12345678", null, null, null, null, null, 5L);

    OcraEvaluationValidationException exception =
        assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

    assertEquals("counter", exception.field());
    assertEquals("counter_not_permitted", exception.reasonCode());
    telemetry.assertValidationFailure("counter_not_permitted");
  }

  @Test
  @DisplayName("pin hash required surfaces pin_hash_required reason")
  void pinHashRequiredFailure() {
    RecordingTelemetry telemetry = new RecordingTelemetry();
    OcraEvaluationService service =
        new OcraEvaluationService(telemetry, provider(FIXED_CLOCK), provider(null));

    OcraEvaluationRequest request =
        new OcraEvaluationRequest(
            null,
            "OCRA-1:HOTP-SHA1-6:C-QN08-PSHA1",
            DEFAULT_SECRET_HEX,
            "12345678",
            null,
            null,
            null,
            null,
            null,
            1L);

    OcraEvaluationValidationException exception =
        assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

    assertEquals("pinHashHex", exception.field());
    assertEquals("pin_hash_required", exception.reasonCode());
    telemetry.assertValidationFailure("pin_hash_required");
  }

  @Test
  @DisplayName("credential conflict surfaces credential_conflict reason")
  void credentialConflictFailure() {
    RecordingTelemetry telemetry = new RecordingTelemetry();
    OcraEvaluationService service =
        new OcraEvaluationService(telemetry, provider(FIXED_CLOCK), provider(null));

    OcraEvaluationRequest request =
        new OcraEvaluationRequest(
            "stored-token",
            DEFAULT_SUITE,
            DEFAULT_SECRET_HEX,
            DEFAULT_CHALLENGE,
            null,
            null,
            null,
            null,
            null,
            null);

    OcraEvaluationValidationException exception =
        assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

    assertEquals("request", exception.field());
    assertEquals("credential_conflict", exception.reasonCode());
    telemetry.assertValidationFailure("credential_conflict");
  }

  @Test
  @DisplayName("missing credential inputs surface credential_missing reason")
  void credentialMissingFailure() {
    RecordingTelemetry telemetry = new RecordingTelemetry();
    OcraEvaluationService service =
        new OcraEvaluationService(telemetry, provider(FIXED_CLOCK), provider(null));

    OcraEvaluationRequest request =
        new OcraEvaluationRequest(
            "  ", null, null, DEFAULT_CHALLENGE, null, null, null, null, null, null);

    OcraEvaluationValidationException exception =
        assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

    assertEquals("request", exception.field());
    assertEquals("credential_missing", exception.reasonCode());
    telemetry.assertValidationFailure("credential_missing");
  }

  @Test
  @DisplayName("credential store absence surfaces credential_not_found")
  void credentialStoreMissingFailure() {
    RecordingTelemetry telemetry = new RecordingTelemetry();
    OcraEvaluationService service =
        new OcraEvaluationService(telemetry, provider(FIXED_CLOCK), provider(null));

    OcraEvaluationRequest request =
        new OcraEvaluationRequest(
            "stored-token", null, null, DEFAULT_CHALLENGE, null, null, null, null, null, null);

    OcraEvaluationValidationException exception =
        assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

    assertEquals("credentialId", exception.field());
    assertEquals("credential_not_found", exception.reasonCode());
    telemetry.assertValidationFailure("credential_not_found");
  }

  @Test
  @DisplayName("unexpected runtime errors record telemetry and propagate")
  void unexpectedErrorRecordsTelemetry() {
    RecordingTelemetry telemetry = new RecordingTelemetry();
    CredentialStore failingStore = new FailingCredentialStore();
    OcraEvaluationService service =
        new OcraEvaluationService(telemetry, provider(FIXED_CLOCK), provider(failingStore));

    OcraEvaluationRequest request =
        new OcraEvaluationRequest(
            "stored-token", null, null, DEFAULT_CHALLENGE, null, null, null, null, null, null);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> service.evaluate(request));

    assertEquals("store offline", exception.getMessage());
    telemetry.assertErrorRecorded("unexpected_error", false);
  }

  @Test
  @DisplayName("resolveTimestamp returns null when step duration is below one second")
  void resolveTimestampSkipsSubSecondStep() throws Exception {
    OcraCredentialDescriptor descriptor = descriptorWithTimestamp(Duration.ofMillis(500));

    Instant resolved = invokeResolveTimestamp(descriptor, "0F");

    assertNull(resolved);
  }

  @Test
  @DisplayName("resolveTimestamp rejects values that overflow epoch seconds")
  void resolveTimestampRejectsOverflow() throws Exception {
    OcraCredentialDescriptor descriptor = descriptorWithTimestamp(Duration.ofSeconds(30));
    String overflowing = Long.toUnsignedString(Long.divideUnsigned(-1L, 30) + 1, 16).toUpperCase();

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> invokeResolveTimestamp(descriptor, overflowing));

    assertTrue(exception.getMessage().contains("exceeds supported range"));
  }

  @Test
  @DisplayName("validateChallenge ignores empty challenges when suite omits question")
  void validateChallengeAllowsMissingWhenNotDeclared() throws Exception {
    OcraCredentialDescriptor descriptor = descriptorWithoutChallenge();

    invokeValidateChallenge(descriptor, null);
    invokeValidateChallenge(descriptor, "   ");
  }

  @Test
  @DisplayName("validateChallenge rejects unexpected input when suite omits question")
  void validateChallengeRejectsUnexpectedWhenNotDeclared() throws Exception {
    OcraCredentialDescriptor descriptor = descriptorWithoutChallenge();

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> invokeValidateChallenge(descriptor, "ABC"));

    assertTrue(exception.getMessage().contains("not permitted"));
  }

  @Test
  @DisplayName("failure details default to request/invalid_input when message unmapped")
  void failureDetailsDefaultForUnknownMessage() throws Exception {
    OcraEvaluationService.FailureDetails failureDetails =
        invokeFailureDetails(new IllegalArgumentException("unexpected"));

    assertEquals("request", invokeFailureAccessor(failureDetails, "field"));
    assertEquals("invalid_input", invokeFailureAccessor(failureDetails, "reasonCode"));
    assertEquals("unexpected", invokeFailureAccessor(failureDetails, "message"));
  }

  @Test
  @DisplayName("failure details substitute default message when null")
  void failureDetailsDefaultMessageWhenNull() throws Exception {
    OcraEvaluationService.FailureDetails failureDetails =
        invokeFailureDetails(new IllegalArgumentException((String) null));

    assertEquals("Invalid input", invokeFailureAccessor(failureDetails, "message"));
  }

  @Test
  @DisplayName("requireHex enforces presence when marked required")
  void requireHexEnforcesRequiredValues() throws Exception {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> invokeRequireHex(null, "sharedSecretHex", true));

    assertTrue(exception.getMessage().contains("required"));
  }

  @Test
  @DisplayName("requireHex accepts valid hexadecimal input")
  void requireHexAcceptsValidHex() throws Exception {
    invokeRequireHex("DEADBEEF", "sharedSecretHex", false);
  }

  @Test
  @DisplayName("requireHex rejects odd-length hexadecimal input")
  void requireHexRejectsOddLength() throws Exception {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> invokeRequireHex("ABC", "sharedSecretHex", false));

    assertTrue(exception.getMessage().contains("even number"));
  }

  @Test
  @DisplayName("suiteOrUnknown falls back to raw suite or defaults to unknown")
  void suiteOrUnknownFallbacks() throws Exception {
    OcraEvaluationRequest rawWithSuite =
        new OcraEvaluationRequest(
            null, "  OCRA-TEST  ", null, null, null, null, null, null, null, null);
    assertEquals("OCRA-TEST", invokeSuiteOrUnknown(null, rawWithSuite));

    OcraEvaluationRequest rawUnknown =
        new OcraEvaluationRequest(null, null, null, null, null, null, null, null, null, null);
    assertEquals("unknown", invokeSuiteOrUnknown(null, rawUnknown));
  }

  @Test
  @DisplayName("normalized flags fall back to raw request when absent")
  void helperFlagFallbacksUseRawRequest() throws Exception {
    OcraEvaluationRequest raw =
        new OcraEvaluationRequest(
            "credential", null, null, "challenge", "SESSION", "CLIENT", "SERVER", "PIN", "FF", 1L);

    assertTrue(invokeHasSession(null, raw));
    assertTrue(invokeHasClientChallenge(null, raw));
    assertTrue(invokeHasServerChallenge(null, raw));
    assertTrue(invokeHasPin(null, raw));
    assertTrue(invokeHasTimestamp(null, raw));
    assertTrue(invokeHasCredentialReference(null, raw));
  }

  @Test
  @DisplayName("normalized request trims fields and discards blanks")
  void normalizedRequestTrimsAndDiscardsBlanks() throws Exception {
    OcraEvaluationRequest raw =
        new OcraEvaluationRequest(
            null,
            "  suite  ",
            "  deadbeef  ",
            "  challenge  ",
            "  session  ",
            "  client  ",
            "  server  ",
            "  pin  ",
            "  ff  ",
            5L);

    OcraEvaluationService.NormalizedRequest normalized = invokeNormalizedRequestFrom(raw);
    assertEquals(null, invokeNormalizedAccessor(normalized, "credentialId"));
    assertEquals("suite", invokeNormalizedAccessor(normalized, "suite"));
    assertEquals("deadbeef", invokeNormalizedAccessor(normalized, "sharedSecretHex"));
    assertEquals("challenge", invokeNormalizedAccessor(normalized, "challenge"));
    assertEquals("session", invokeNormalizedAccessor(normalized, "sessionHex"));
    assertEquals("client", invokeNormalizedAccessor(normalized, "clientChallenge"));
    assertEquals("server", invokeNormalizedAccessor(normalized, "serverChallenge"));
    assertEquals("pin", invokeNormalizedAccessor(normalized, "pinHashHex"));
    assertEquals("ff", invokeNormalizedAccessor(normalized, "timestampHex"));

    OcraEvaluationRequest blank =
        new OcraEvaluationRequest(
            null, " suite ", "value", "challenge", " ", " ", " ", " ", " ", null);

    OcraEvaluationService.NormalizedRequest blankNormalized = invokeNormalizedRequestFrom(blank);
    assertEquals(null, invokeNormalizedAccessor(blankNormalized, "credentialId"));
    assertEquals("suite", invokeNormalizedAccessor(blankNormalized, "suite"));
    assertEquals(null, invokeNormalizedAccessor(blankNormalized, "sessionHex"));
    assertEquals(null, invokeNormalizedAccessor(blankNormalized, "clientChallenge"));
    assertEquals(null, invokeNormalizedAccessor(blankNormalized, "serverChallenge"));
    assertEquals(null, invokeNormalizedAccessor(blankNormalized, "pinHashHex"));
    assertEquals(null, invokeNormalizedAccessor(blankNormalized, "timestampHex"));
  }

  @Test
  @DisplayName("normalized request trims stored credential identifiers")
  void normalizedRequestStoredCredentialTrimsId() throws Exception {
    OcraEvaluationRequest raw =
        new OcraEvaluationRequest(
            "  credential  ", null, null, "challenge", null, null, null, null, null, null);

    OcraEvaluationService.NormalizedRequest normalized = invokeNormalizedRequestFrom(raw);
    assertEquals("credential", invokeNormalizedAccessor(normalized, "credentialId"));
    assertEquals(null, invokeNormalizedAccessor(normalized, "suite"));
  }

  @Test
  @DisplayName("normalized request handles null values")
  void normalizedRequestHandlesNullValues() throws Exception {
    OcraEvaluationRequest raw =
        new OcraEvaluationRequest(
            null, DEFAULT_SUITE, DEFAULT_SECRET_HEX, null, null, null, null, null, null, null);

    OcraEvaluationService.NormalizedRequest normalized = invokeNormalizedRequestFrom(raw);
    assertEquals(null, invokeNormalizedAccessor(normalized, "credentialId"));
    assertEquals(DEFAULT_SUITE, invokeNormalizedAccessor(normalized, "suite"));
    assertEquals(DEFAULT_SECRET_HEX, invokeNormalizedAccessor(normalized, "sharedSecretHex"));
    assertEquals(null, invokeNormalizedAccessor(normalized, "challenge"));
    assertEquals(null, invokeNormalizedAccessor(normalized, "sessionHex"));
    assertEquals(null, invokeNormalizedAccessor(normalized, "clientChallenge"));
    assertEquals(null, invokeNormalizedAccessor(normalized, "serverChallenge"));
    assertEquals(null, invokeNormalizedAccessor(normalized, "pinHashHex"));
    assertEquals(null, invokeNormalizedAccessor(normalized, "timestampHex"));
  }

  @Test
  @DisplayName("normalized request selects stored credential variant when credentialId present")
  void normalizedRequestStoredVariant() {
    OcraEvaluationRequest raw =
        new OcraEvaluationRequest(
            "stored-id", null, null, "12345678", null, null, null, null, null, null);

    OcraEvaluationService.NormalizedRequest normalized = invokeNormalizedRequestFrom(raw);
    assertTrue(
        normalized instanceof OcraEvaluationService.NormalizedRequest.StoredCredential,
        () ->
            "Expected stored credential variant but was " + normalized.getClass().getSimpleName());
  }

  @Test
  @DisplayName("normalized request selects inline secret variant when sharedSecretHex present")
  void normalizedRequestInlineVariant() {
    OcraEvaluationRequest raw =
        new OcraEvaluationRequest(
            null,
            DEFAULT_SUITE,
            DEFAULT_SECRET_HEX,
            "12345678",
            null,
            null,
            null,
            null,
            null,
            null);

    OcraEvaluationService.NormalizedRequest normalized = invokeNormalizedRequestFrom(raw);
    assertTrue(
        normalized instanceof OcraEvaluationService.NormalizedRequest.InlineSecret,
        () -> "Expected inline secret variant but was " + normalized.getClass().getSimpleName());
  }

  @Test
  @DisplayName("hex character predicate recognises valid ranges")
  void isHexCharacterCoverage() {
    assertTrue(OcraEvaluationService.isHexCharacter('0'));
    assertTrue(OcraEvaluationService.isHexCharacter('A'));
    assertTrue(OcraEvaluationService.isHexCharacter('f'));
    assertFalse(OcraEvaluationService.isHexCharacter('g'));
  }

  @Test
  @DisplayName("failure details map timestamp drift and invalid inputs")
  void failureDetailsTimestampMappings() throws Exception {
    OcraEvaluationService.FailureDetails driftExceeded =
        invokeFailureDetails(new IllegalArgumentException("timestamp outside permitted drift"));
    assertEquals("timestamp_drift_exceeded", invokeFailureAccessor(driftExceeded, "reasonCode"));

    OcraEvaluationService.FailureDetails notPermitted =
        invokeFailureDetails(new IllegalArgumentException("timestamp not permitted for suite"));
    assertEquals("timestamp_not_permitted", invokeFailureAccessor(notPermitted, "reasonCode"));

    OcraEvaluationService.FailureDetails invalid =
        invokeFailureDetails(
            new IllegalArgumentException("timestamp must represent a valid time window"));
    assertEquals("timestamp_invalid", invokeFailureAccessor(invalid, "reasonCode"));
  }

  @Test
  @DisplayName("inline suite omission surfaces missing_required for suite")
  void inlineSuiteMissingFailure() {
    RecordingTelemetry telemetry = new RecordingTelemetry();
    OcraEvaluationService service =
        new OcraEvaluationService(telemetry, provider(FIXED_CLOCK), provider(null));

    OcraEvaluationRequest request =
        new OcraEvaluationRequest(
            null, "  ", DEFAULT_SECRET_HEX, DEFAULT_CHALLENGE, null, null, null, null, null, null);

    OcraEvaluationValidationException exception =
        assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

    assertEquals("suite", exception.field());
    assertEquals("missing_required", exception.reasonCode());
    telemetry.assertValidationFailure("missing_required");
  }

  @Test
  @DisplayName("timestamp with odd length surfaces invalid_hex_length")
  void timestampInvalidLengthFailure() {
    RecordingTelemetry telemetry = new RecordingTelemetry();
    OcraEvaluationService service =
        new OcraEvaluationService(telemetry, provider(FIXED_CLOCK), provider(null));

    OcraEvaluationRequest request =
        new OcraEvaluationRequest(
            null,
            "OCRA-1:HOTPT30SHA256-7:QN08",
            DEFAULT_SECRET_HEX,
            DEFAULT_CHALLENGE,
            null,
            null,
            null,
            null,
            "ABC",
            null);

    OcraEvaluationValidationException exception =
        assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

    assertEquals("timestampHex", exception.field());
    assertEquals("invalid_hex_length", exception.reasonCode());
    telemetry.assertValidationFailure("invalid_hex_length");
  }

  @Test
  @DisplayName("character format challenge is accepted for QC suites")
  void characterFormatChallengeSucceeds() {
    RecordingTelemetry telemetry = new RecordingTelemetry();
    OcraEvaluationService service =
        new OcraEvaluationService(telemetry, provider(FIXED_CLOCK), provider(null));

    OcraEvaluationRequest request =
        new OcraEvaluationRequest(
            null,
            "OCRA-1:HOTP-SHA1-6:QC08",
            DEFAULT_SECRET_HEX,
            "!@#$%^&*",
            null,
            null,
            null,
            null,
            null,
            null);

    OcraEvaluationResponse response = service.evaluate(request);

    assertEquals("OCRA-1:HOTP-SHA1-6:QC08", response.suite());
    assertTrue(response.otp().length() > 0);
    telemetry.assertSuccessRecorded();
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
    public T getIfAvailable(Supplier<T> supplier) {
      return instance != null ? instance : supplier.get();
    }

    @Override
    public T getIfUnique() {
      return instance;
    }

    @Override
    public T getIfUnique(Supplier<T> supplier) {
      return instance != null ? instance : supplier.get();
    }

    @Override
    public Stream<T> stream() {
      return instance == null ? Stream.empty() : Stream.of(instance);
    }

    @Override
    public Stream<T> orderedStream() {
      return stream();
    }

    @Override
    public Iterator<T> iterator() {
      return stream().iterator();
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

  private static final class RecordingTelemetry extends OcraEvaluationTelemetry {

    private final List<String> reasonCodes = new ArrayList<>();
    private boolean successRecorded;
    private boolean errorRecorded;
    private String lastErrorReasonCode;
    private boolean lastErrorSanitized;

    @Override
    void recordValidationFailure(
        String telemetryId,
        String suite,
        boolean hasCredentialReference,
        boolean hasSession,
        boolean hasClientChallenge,
        boolean hasServerChallenge,
        boolean hasPin,
        boolean hasTimestamp,
        String reasonCode,
        String reason,
        boolean sanitized,
        long durationMillis) {
      reasonCodes.add(reasonCode);
    }

    @Override
    void recordSuccess(
        String telemetryId,
        String suite,
        boolean hasCredentialReference,
        boolean hasSession,
        boolean hasClientChallenge,
        boolean hasServerChallenge,
        boolean hasPin,
        boolean hasTimestamp,
        long durationMillis) {
      successRecorded = true;
    }

    @Override
    void recordError(
        String telemetryId,
        String suite,
        boolean hasCredentialReference,
        boolean hasSession,
        boolean hasClientChallenge,
        boolean hasServerChallenge,
        boolean hasPin,
        boolean hasTimestamp,
        String reasonCode,
        String reason,
        boolean sanitized,
        long durationMillis) {
      errorRecorded = true;
      lastErrorReasonCode = reasonCode;
      lastErrorSanitized = sanitized;
    }

    void assertValidationFailure(String expectedReasonCode) {
      assertFalse(reasonCodes.isEmpty(), "Expected validation failure to be recorded");
      assertEquals(expectedReasonCode, reasonCodes.get(reasonCodes.size() - 1));
    }

    void assertSuccessRecorded() {
      assertTrue(successRecorded, "Expected success telemetry to be recorded");
      assertFalse(errorRecorded, "Success telemetry should not co-occur with error telemetry");
    }

    void assertErrorRecorded(String expectedReasonCode, boolean expectedSanitized) {
      assertTrue(errorRecorded, "Expected error telemetry to be recorded");
      assertEquals(expectedReasonCode, lastErrorReasonCode);
      assertEquals(expectedSanitized, lastErrorSanitized);
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

  private static OcraCredentialDescriptor descriptorWithTimestamp(Duration step) {
    OcraCryptoFunction cryptoFunction =
        new OcraCryptoFunction(OcraHashAlgorithm.SHA1, 6, Optional.of(Duration.ofSeconds(1)));
    OcraDataInput dataInput =
        new OcraDataInput(
            false,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(new OcraTimestampSpecification(step)));
    OcraSuite suite = new OcraSuite("TEST", cryptoFunction, dataInput);
    return new OcraCredentialDescriptor(
        "descriptor",
        suite,
        SecretMaterial.fromHex("3132333435363738"),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Map.of());
  }

  private static OcraCredentialDescriptor descriptorWithoutChallenge() {
    OcraCryptoFunction cryptoFunction =
        new OcraCryptoFunction(OcraHashAlgorithm.SHA1, 6, Optional.empty());
    OcraDataInput dataInput =
        new OcraDataInput(
            false, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    OcraSuite suite = new OcraSuite("NO-CHALLENGE", cryptoFunction, dataInput);
    return new OcraCredentialDescriptor(
        "descriptor",
        suite,
        SecretMaterial.fromHex("31323334"),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Map.of());
  }

  private static Instant invokeResolveTimestamp(OcraCredentialDescriptor descriptor, String input) {
    return OcraEvaluationService.resolveTimestamp(descriptor, input);
  }

  private static void invokeValidateChallenge(
      OcraCredentialDescriptor descriptor, String challenge) {
    OcraEvaluationService.validateChallenge(descriptor, challenge);
  }

  private static OcraEvaluationService.FailureDetails invokeFailureDetails(
      IllegalArgumentException exception) {
    return OcraEvaluationService.FailureDetails.fromException(exception);
  }

  private static Object invokeFailureAccessor(
      OcraEvaluationService.FailureDetails failureDetails, String methodName) {
    return switch (methodName) {
      case "field" -> failureDetails.field();
      case "reasonCode" -> failureDetails.reasonCode();
      case "message" -> failureDetails.message();
      case "sanitized" -> failureDetails.sanitized();
      default -> throw new IllegalArgumentException("Unknown accessor: " + methodName);
    };
  }

  private static OcraEvaluationService.NormalizedRequest invokeNormalizedRequestFrom(
      OcraEvaluationRequest raw) {
    return OcraEvaluationService.NormalizedRequest.from(raw);
  }

  private static Object invokeNormalizedAccessor(
      OcraEvaluationService.NormalizedRequest normalized, String methodName) {
    return switch (methodName) {
      case "credentialId" -> normalized.credentialId();
      case "suite" -> normalized.suite();
      case "sharedSecretHex" -> normalized.sharedSecretHex();
      case "challenge" -> normalized.challenge();
      case "sessionHex" -> normalized.sessionHex();
      case "clientChallenge" -> normalized.clientChallenge();
      case "serverChallenge" -> normalized.serverChallenge();
      case "pinHashHex" -> normalized.pinHashHex();
      case "timestampHex" -> normalized.timestampHex();
      case "counter" -> normalized.counter();
      default -> throw new IllegalArgumentException("Unknown accessor: " + methodName);
    };
  }

  private static void invokeRequireHex(String value, String field, boolean required) {
    OcraEvaluationService.requireHex(value, field, required);
  }

  private static String invokeSuiteOrUnknown(
      OcraEvaluationService.NormalizedRequest normalizedRequest, OcraEvaluationRequest rawRequest) {
    return OcraEvaluationService.suiteOrUnknown(normalizedRequest, rawRequest);
  }

  private static boolean invokeHasSession(Object normalized, OcraEvaluationRequest raw) {
    return invokeFlagHelper("hasSession", normalized, raw);
  }

  private static boolean invokeHasClientChallenge(Object normalized, OcraEvaluationRequest raw) {
    return invokeFlagHelper("hasClientChallenge", normalized, raw);
  }

  private static boolean invokeHasServerChallenge(Object normalized, OcraEvaluationRequest raw) {
    return invokeFlagHelper("hasServerChallenge", normalized, raw);
  }

  private static boolean invokeHasPin(Object normalized, OcraEvaluationRequest raw) {
    return invokeFlagHelper("hasPin", normalized, raw);
  }

  private static boolean invokeHasTimestamp(Object normalized, OcraEvaluationRequest raw) {
    return invokeFlagHelper("hasTimestamp", normalized, raw);
  }

  private static boolean invokeHasCredentialReference(
      Object normalized, OcraEvaluationRequest raw) {
    return invokeFlagHelper("hasCredentialReference", normalized, raw);
  }

  private static boolean invokeFlagHelper(
      String methodName, Object normalized, OcraEvaluationRequest raw) {
    OcraEvaluationService.NormalizedRequest typed =
        normalized instanceof OcraEvaluationService.NormalizedRequest record ? record : null;
    return switch (methodName) {
      case "hasSession" -> OcraEvaluationService.hasSession(typed, raw);
      case "hasClientChallenge" -> OcraEvaluationService.hasClientChallenge(typed, raw);
      case "hasServerChallenge" -> OcraEvaluationService.hasServerChallenge(typed, raw);
      case "hasPin" -> OcraEvaluationService.hasPin(typed, raw);
      case "hasTimestamp" -> OcraEvaluationService.hasTimestamp(typed, raw);
      case "hasCredentialReference" -> OcraEvaluationService.hasCredentialReference(typed, raw);
      default -> throw new IllegalArgumentException("Unknown helper: " + methodName);
    };
  }
}
