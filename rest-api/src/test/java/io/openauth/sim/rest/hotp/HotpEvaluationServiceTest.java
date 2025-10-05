package io.openauth.sim.rest.hotp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

  private static final String OTP = "755224";

  private HotpEvaluationApplicationService applicationService;
  private HotpEvaluationService service;

  @BeforeEach
  void setUp() {
    applicationService = Mockito.mock(HotpEvaluationApplicationService.class);
    service = new HotpEvaluationService(applicationService);
  }

  @Test
  @DisplayName("Stored evaluation delegates to application service and returns generated OTP")
  void storedEvaluationReturnsGeneratedOtp() {
    TelemetrySignal signal =
        new TelemetrySignal(
            TelemetryStatus.SUCCESS,
            "generated",
            null,
            true,
            Map.of("credentialSource", "stored"),
            null);
    EvaluationResult result =
        new EvaluationResult(signal, true, "demo", 0L, 1L, HotpHashAlgorithm.SHA1, 6, OTP);
    when(applicationService.evaluate(any(EvaluationCommand.Stored.class))).thenReturn(result);

    HotpEvaluationResponse response =
        service.evaluateStored(new HotpStoredEvaluationRequest("demo"));

    assertEquals("generated", response.status());
    assertEquals(OTP, response.otp());
    assertEquals("stored", response.metadata().credentialSource());
    assertEquals("demo", response.metadata().credentialId());
    verify(applicationService).evaluate(any(EvaluationCommand.Stored.class));
  }

  @Test
  @DisplayName("Inline evaluation delegates to application service and returns generated OTP")
  void inlineEvaluationReturnsGeneratedOtp() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("credentialSource", "inline");
    TelemetrySignal signal =
        new TelemetrySignal(TelemetryStatus.SUCCESS, "generated", null, true, fields, null);
    EvaluationResult result =
        new EvaluationResult(signal, false, null, 10L, 11L, HotpHashAlgorithm.SHA1, 6, OTP);
    when(applicationService.evaluate(any(EvaluationCommand.Inline.class))).thenReturn(result);

    HotpEvaluationResponse response =
        service.evaluateInline(
            new HotpInlineEvaluationRequest(
                "3132333435363738393031323334353637383930", "SHA1", 6, 10L, Map.of()));

    assertEquals("generated", response.status());
    assertEquals(OTP, response.otp());
    assertEquals("inline", response.metadata().credentialSource());
    verify(applicationService).evaluate(any(EvaluationCommand.Inline.class));
  }

  @Test
  @DisplayName("Inline evaluation rejects unknown algorithms with sanitized details")
  void inlineEvaluationRejectsUnknownAlgorithm() {
    HotpEvaluationValidationException exception =
        assertThrows(
            HotpEvaluationValidationException.class,
            () ->
                service.evaluateInline(
                    new HotpInlineEvaluationRequest(
                        "3132333435363738393031323334353637383930", "sha999", 6, 0L, Map.of())));

    assertEquals("algorithm_invalid", exception.reasonCode());
    assertEquals("algorithm", exception.details().get("field"));
    verifyNoInteractions(applicationService);
  }

  @Test
  @DisplayName("Inline evaluation requires counter metadata")
  void inlineEvaluationRequiresCounter() {
    HotpEvaluationValidationException exception =
        assertThrows(
            HotpEvaluationValidationException.class,
            () ->
                service.evaluateInline(
                    new HotpInlineEvaluationRequest(
                        "3132333435363738393031323334353637383930", "SHA1", 6, null, Map.of())));

    assertEquals("counter_required", exception.reasonCode());
    verifyNoInteractions(applicationService);
  }

  @Test
  @DisplayName("Inline evaluation requires shared secret material")
  void inlineEvaluationRequiresSecret() {
    HotpEvaluationValidationException exception =
        assertThrows(
            HotpEvaluationValidationException.class,
            () ->
                service.evaluateInline(
                    new HotpInlineEvaluationRequest("   ", "SHA1", 6, 0L, Map.of())));

    assertEquals("sharedSecretHex_required", exception.reasonCode());
    verifyNoInteractions(applicationService);
  }

  @Test
  @DisplayName("Stored evaluation requires credential identifiers")
  void storedEvaluationRequiresCredentialId() {
    HotpEvaluationValidationException exception =
        assertThrows(
            HotpEvaluationValidationException.class,
            () -> service.evaluateStored(new HotpStoredEvaluationRequest("  ")));

    assertEquals("credentialId_required", exception.reasonCode());
    verifyNoInteractions(applicationService);
  }

  @Test
  @DisplayName("Stored evaluation surfaces counter overflow as validation error")
  void storedEvaluationCounterOverflow() {
    TelemetrySignal signal =
        new TelemetrySignal(
            TelemetryStatus.INVALID,
            "counter_overflow",
            "overflow",
            true,
            Map.of("credentialSource", "stored"),
            null);
    EvaluationResult result =
        new EvaluationResult(
            signal, true, "demo", Long.MAX_VALUE, Long.MAX_VALUE, HotpHashAlgorithm.SHA1, 6, null);
    when(applicationService.evaluate(any(EvaluationCommand.Stored.class))).thenReturn(result);

    HotpEvaluationValidationException exception =
        assertThrows(
            HotpEvaluationValidationException.class,
            () -> service.evaluateStored(new HotpStoredEvaluationRequest("demo")));

    assertEquals("counter_overflow", exception.reasonCode());
    verify(applicationService).evaluate(any(EvaluationCommand.Stored.class));
  }

  @Test
  @DisplayName("Inline evaluation surfaces unexpected errors")
  void inlineEvaluationUnexpectedError() {
    TelemetrySignal signal =
        new TelemetrySignal(
            TelemetryStatus.ERROR,
            "unexpected_error",
            "boom",
            false,
            Map.of("credentialSource", "inline"),
            null);
    EvaluationResult result =
        new EvaluationResult(signal, false, null, 5L, 5L, HotpHashAlgorithm.SHA1, 6, null);
    when(applicationService.evaluate(any(EvaluationCommand.Inline.class))).thenReturn(result);

    HotpEvaluationUnexpectedException exception =
        assertThrows(
            HotpEvaluationUnexpectedException.class,
            () ->
                service.evaluateInline(
                    new HotpInlineEvaluationRequest(
                        "3132333435363738393031323334353637383930", "SHA1", 6, 5L, Map.of())));

    assertEquals("inline", exception.credentialSource());
    assertEquals("false", exception.details().get("sanitized"));
    assertTrue(exception.getMessage().contains("boom"));
    verify(applicationService).evaluate(any(EvaluationCommand.Inline.class));
  }
}
