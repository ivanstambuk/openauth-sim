package io.openauth.sim.rest.hotp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.hotp.HotpEvaluationApplicationService;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.EvaluationCommand;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.EvaluationResult;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.TelemetrySignal;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.TelemetryStatus;
import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

final class HotpEvaluationServiceTest {

  private HotpEvaluationApplicationService applicationService;

  @BeforeEach
  void setUp() {
    applicationService = Mockito.mock(HotpEvaluationApplicationService.class);
  }

  @Test
  @DisplayName("Stored evaluation delegates to application service and returns match response")
  void storedEvaluationReturnsMatch() {
    HotpEvaluationService service = new HotpEvaluationService(applicationService);

    TelemetrySignal signal =
        new TelemetrySignal(
            TelemetryStatus.SUCCESS,
            "match",
            null,
            true,
            Map.of("credentialSource", "stored"),
            null);
    EvaluationResult result =
        new EvaluationResult(signal, true, "demo", 0L, 1L, HotpHashAlgorithm.SHA1, 6);

    Mockito.when(applicationService.evaluate(Mockito.any(EvaluationCommand.Stored.class)))
        .thenReturn(result);

    HotpEvaluationResponse response =
        service.evaluateStored(new HotpStoredEvaluationRequest("demo", "123456"));

    assertEquals("match", response.status());
    assertEquals("match", response.reasonCode());
    assertEquals("stored", response.metadata().credentialSource());
    assertEquals(1L, response.metadata().nextCounter());
    Mockito.verify(applicationService).evaluate(Mockito.any(EvaluationCommand.Stored.class));
  }

  @Test
  @DisplayName("Inline evaluation returns mismatch response when telemetry reports otp_mismatch")
  void inlineEvaluationReturnsMismatch() {
    HotpEvaluationService service = new HotpEvaluationService(applicationService);

    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("credentialSource", "inline");
    fields.put("optional", null);
    TelemetrySignal signal =
        new TelemetrySignal(
            TelemetryStatus.INVALID, "otp_mismatch", "OTP mismatch", true, fields, null);
    EvaluationResult result =
        new EvaluationResult(signal, false, null, 5L, 5L, HotpHashAlgorithm.SHA1, 6);

    Mockito.when(applicationService.evaluate(Mockito.any(EvaluationCommand.Inline.class)))
        .thenReturn(result);

    HotpEvaluationResponse response =
        service.evaluateInline(
            new HotpInlineEvaluationRequest(
                "3132333435363738393031323334353637383930", "SHA1", 6, 5L, "000000", Map.of()));

    assertEquals("mismatch", response.status());
    assertEquals("otp_mismatch", response.reasonCode());
    assertEquals("inline", response.metadata().credentialSource());
    assertFalse(Boolean.TRUE.equals(response.metadata().credentialReference()));
    assertEquals(null, response.metadata().credentialId());
    Mockito.verify(applicationService).evaluate(Mockito.any(EvaluationCommand.Inline.class));
  }

  @Test
  @DisplayName("Inline evaluation returns match response without credential reference")
  void inlineEvaluationReturnsMatch() {
    HotpEvaluationService service = new HotpEvaluationService(applicationService);

    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("credentialSource", "inline");
    fields.put("hashAlgorithm", HotpHashAlgorithm.SHA1.name());
    fields.put("digits", 6);
    TelemetrySignal signal =
        new TelemetrySignal(TelemetryStatus.SUCCESS, "match", null, true, fields, null);
    EvaluationResult result =
        new EvaluationResult(signal, false, null, 10L, 11L, HotpHashAlgorithm.SHA1, 6);

    Mockito.when(applicationService.evaluate(Mockito.any(EvaluationCommand.Inline.class)))
        .thenReturn(result);

    HotpEvaluationResponse response =
        service.evaluateInline(
            new HotpInlineEvaluationRequest(
                "3132333435363738393031323334353637383930", "SHA1", 6, 10L, "1234567", Map.of()));

    assertEquals("match", response.status());
    assertEquals("match", response.reasonCode());
    assertEquals("inline", response.metadata().credentialSource());
    assertEquals(null, response.metadata().credentialId());
    assertTrue(response.metadata().telemetryId().startsWith("rest-hotp-"));
    Mockito.verify(applicationService).evaluate(Mockito.any(EvaluationCommand.Inline.class));
  }

  @Test
  @DisplayName("Inline evaluation rejects unknown algorithms with sanitized details")
  void inlineEvaluationRejectsUnknownAlgorithm() {
    HotpEvaluationService service = new HotpEvaluationService(applicationService);

    HotpEvaluationValidationException exception =
        assertThrows(
            HotpEvaluationValidationException.class,
            () ->
                service.evaluateInline(
                    new HotpInlineEvaluationRequest(
                        "3132333435363738393031323334353637383930",
                        "sha999",
                        6,
                        0L,
                        "123456",
                        Map.of())));

    assertEquals("algorithm_invalid", exception.reasonCode());
    assertTrue(exception.details().containsKey("field"));
    assertEquals("algorithm", exception.details().get("field"));
    Mockito.verifyNoInteractions(applicationService);
  }

  @Test
  @DisplayName("Inline evaluation requires counter metadata")
  void inlineEvaluationRequiresCounter() {
    HotpEvaluationService service = new HotpEvaluationService(applicationService);

    HotpEvaluationValidationException exception =
        assertThrows(
            HotpEvaluationValidationException.class,
            () ->
                service.evaluateInline(
                    new HotpInlineEvaluationRequest(
                        "3132333435363738393031323334353637383930",
                        "SHA1",
                        6,
                        null,
                        "123456",
                        Map.of())));

    assertEquals("counter_required", exception.reasonCode());
    assertEquals("true", exception.details().get("sanitized"));
    Mockito.verifyNoInteractions(applicationService);
  }

  @Test
  @DisplayName("Inline evaluation requires shared secret material")
  void inlineEvaluationRequiresSecret() {
    HotpEvaluationService service = new HotpEvaluationService(applicationService);

    HotpEvaluationValidationException exception =
        assertThrows(
            HotpEvaluationValidationException.class,
            () ->
                service.evaluateInline(
                    new HotpInlineEvaluationRequest("   ", "SHA1", 6, 0L, "123456", Map.of())));

    assertEquals("sharedSecretHex_required", exception.reasonCode());
    assertEquals("sharedSecretHex", exception.details().get("field"));
    assertEquals("true", exception.details().get("sanitized"));
    Mockito.verifyNoInteractions(applicationService);
  }

  @Test
  @DisplayName("Inline evaluation requires OTP values")
  void inlineEvaluationRequiresOtp() {
    HotpEvaluationService service = new HotpEvaluationService(applicationService);

    HotpEvaluationValidationException exception =
        assertThrows(
            HotpEvaluationValidationException.class,
            () ->
                service.evaluateInline(
                    new HotpInlineEvaluationRequest(
                        "3132333435363738393031323334353637383930",
                        "SHA1",
                        6,
                        0L,
                        "   ",
                        Map.of())));

    assertEquals("otp_required", exception.reasonCode());
    assertEquals("otp", exception.details().get("field"));
    assertEquals("true", exception.details().get("sanitized"));
    Mockito.verifyNoInteractions(applicationService);
  }

  @Test
  @DisplayName("Stored evaluation requires non-blank OTP values")
  void storedEvaluationRequiresOtp() {
    HotpEvaluationService service = new HotpEvaluationService(applicationService);

    HotpEvaluationValidationException exception =
        assertThrows(
            HotpEvaluationValidationException.class,
            () -> service.evaluateStored(new HotpStoredEvaluationRequest("demo", "   ")));

    assertEquals("otp_required", exception.reasonCode());
    assertEquals("demo", exception.details().get("credentialId"));
    Mockito.verifyNoInteractions(applicationService);
  }

  @Test
  @DisplayName("Stored evaluation requires credential identifiers")
  void storedEvaluationRequiresCredentialId() {
    HotpEvaluationService service = new HotpEvaluationService(applicationService);

    HotpEvaluationValidationException exception =
        assertThrows(
            HotpEvaluationValidationException.class,
            () -> service.evaluateStored(new HotpStoredEvaluationRequest("  ", "123456")));

    assertEquals("credentialId_required", exception.reasonCode());
    assertEquals("true", exception.details().get("sanitized"));
    assertEquals("credentialId", exception.details().get("field"));
    Mockito.verifyNoInteractions(applicationService);
  }

  @Test
  @DisplayName("Non-mismatch validation results surface sanitized details")
  void inlineEvaluationPropagatesValidationFailure() {
    HotpEvaluationService service = new HotpEvaluationService(applicationService);

    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("digits", 6);
    fields.put("credentialSource", "inline");
    fields.put("nullable", null);
    TelemetrySignal signal =
        new TelemetrySignal(
            TelemetryStatus.INVALID, "digits_mismatch", "digits mismatch", true, fields, null);
    EvaluationResult result =
        new EvaluationResult(signal, false, null, 0L, 0L, HotpHashAlgorithm.SHA1, 6);

    Mockito.when(applicationService.evaluate(Mockito.any(EvaluationCommand.Inline.class)))
        .thenReturn(result);

    HotpEvaluationValidationException exception =
        assertThrows(
            HotpEvaluationValidationException.class,
            () ->
                service.evaluateInline(
                    new HotpInlineEvaluationRequest(
                        "3132333435363738393031323334353637383930",
                        "SHA1",
                        6,
                        0L,
                        "123456",
                        Map.of("source", "test"))));

    assertEquals("digits_mismatch", exception.reasonCode());
    assertEquals("true", exception.details().get("sanitized"));
    assertEquals("6", exception.details().get("digits"));
    assertFalse(exception.details().containsKey("identifier"));
  }

  @Test
  @DisplayName("Inline validation can surface unsanitized telemetry fields")
  void inlineEvaluationPropagatesUnsanitizedTelemetry() {
    HotpEvaluationService service = new HotpEvaluationService(applicationService);

    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("digits", 8);
    fields.put("credentialSource", "inline");
    TelemetrySignal signal =
        new TelemetrySignal(
            TelemetryStatus.INVALID, "validation_error", "secret malformed", false, fields, null);
    EvaluationResult result =
        new EvaluationResult(signal, false, null, 2L, 2L, HotpHashAlgorithm.SHA1, 8);

    Mockito.when(applicationService.evaluate(Mockito.any(EvaluationCommand.Inline.class)))
        .thenReturn(result);

    HotpEvaluationValidationException exception =
        assertThrows(
            HotpEvaluationValidationException.class,
            () ->
                service.evaluateInline(
                    new HotpInlineEvaluationRequest(
                        "3132333435363738393031323334353637383930",
                        "SHA1",
                        8,
                        2L,
                        "654321",
                        Map.of())));

    assertEquals("validation_error", exception.reasonCode());
    assertEquals("false", exception.details().get("sanitized"));
    assertEquals("inline", exception.details().get("credentialSource"));
    Mockito.verify(applicationService).evaluate(Mockito.any(EvaluationCommand.Inline.class));
  }

  @Test
  @DisplayName("Unexpected errors wrap into HotpEvaluationUnexpectedException")
  void storedEvaluationUnexpectedError() {
    HotpEvaluationService service = new HotpEvaluationService(applicationService);

    TelemetrySignal signal =
        new TelemetrySignal(
            TelemetryStatus.ERROR,
            "unexpected_error",
            null,
            false,
            Map.of("credentialSource", "stored"),
            null);
    EvaluationResult result =
        new EvaluationResult(signal, true, "demo", 1L, 1L, HotpHashAlgorithm.SHA1, 6);

    Mockito.when(applicationService.evaluate(Mockito.any(EvaluationCommand.Stored.class)))
        .thenReturn(result);

    HotpEvaluationUnexpectedException exception =
        assertThrows(
            HotpEvaluationUnexpectedException.class,
            () -> service.evaluateStored(new HotpStoredEvaluationRequest("demo", "123456")));

    assertEquals("", exception.getMessage());
    assertEquals("stored", exception.credentialSource());
    assertFalse(exception.details().isEmpty());
  }
}
