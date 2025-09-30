package io.openauth.sim.rest.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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

  private static final class RecordingTelemetry extends OcraEvaluationTelemetry {

    private final List<String> reasonCodes = new ArrayList<>();

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
      // ignore in tests
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
      // ignore in tests
    }

    void assertValidationFailure(String expectedReasonCode) {
      assertFalse(reasonCodes.isEmpty(), "Expected validation failure to be recorded");
      assertEquals(expectedReasonCode, reasonCodes.get(reasonCodes.size() - 1));
    }
  }
}
