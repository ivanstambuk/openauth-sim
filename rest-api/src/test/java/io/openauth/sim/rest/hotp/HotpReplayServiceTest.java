package io.openauth.sim.rest.hotp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.hotp.HotpReplayApplicationService;
import io.openauth.sim.application.hotp.HotpReplayApplicationService.ReplayCommand;
import io.openauth.sim.application.hotp.HotpReplayApplicationService.ReplayResult;
import io.openauth.sim.application.hotp.HotpReplayApplicationService.TelemetrySignal;
import io.openauth.sim.application.hotp.HotpReplayApplicationService.TelemetryStatus;
import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class HotpReplayServiceTest {

  @Test
  @DisplayName("Stored replay delegates to application service and returns match response")
  void storedReplayReturnsMatch() {
    HotpReplayApplicationService applicationService =
        Mockito.mock(HotpReplayApplicationService.class);
    TelemetrySignal signal =
        new TelemetrySignal(
            TelemetryStatus.SUCCESS,
            "match",
            "otp match",
            false,
            Map.of("credentialSource", "stored"),
            null);
    ReplayResult result =
        new ReplayResult(signal, true, "cred-123", 10L, 10L, HotpHashAlgorithm.SHA1, 6);
    Mockito.when(applicationService.replay(Mockito.any(ReplayCommand.Stored.class)))
        .thenReturn(result);

    HotpReplayService service = new HotpReplayService(applicationService);
    HotpReplayResponse response =
        service.replay(
            new HotpReplayRequest("cred-123", null, null, null, null, null, "755224", null));

    assertEquals("match", response.status());
    assertEquals("match", response.reasonCode());
    assertEquals("stored", response.metadata().credentialSource());
    assertEquals(10L, response.metadata().previousCounter());
    assertEquals(10L, response.metadata().nextCounter());

    ArgumentCaptor<ReplayCommand.Stored> captor =
        ArgumentCaptor.forClass(ReplayCommand.Stored.class);
    Mockito.verify(applicationService).replay(captor.capture());
    assertEquals("cred-123", captor.getValue().credentialId());
    assertEquals("755224", captor.getValue().otp());
  }

  @Test
  @DisplayName("Inline replay returning otp_mismatch yields mismatch status")
  void inlineReplayReturnsMismatch() {
    HotpReplayApplicationService applicationService =
        Mockito.mock(HotpReplayApplicationService.class);
    Map<String, Object> mismatchFields = new LinkedHashMap<>();
    mismatchFields.put("credentialSource", "inline");
    mismatchFields.put("diagnostic", null);
    TelemetrySignal signal =
        new TelemetrySignal(
            TelemetryStatus.INVALID, "otp_mismatch", "otp mismatch", true, mismatchFields, null);
    ReplayResult result =
        new ReplayResult(signal, false, "inline-device", 5L, 5L, HotpHashAlgorithm.SHA1, 6);
    Mockito.when(applicationService.replay(Mockito.any(ReplayCommand.Inline.class)))
        .thenReturn(result);

    HotpReplayService service = new HotpReplayService(applicationService);
    HotpReplayResponse response =
        service.replay(
            new HotpReplayRequest(
                null,
                "device-inline",
                "3132333435363738393031323334353637383930",
                "SHA1",
                6,
                5L,
                "254676",
                Map.of("label", "demo")));

    assertEquals("mismatch", response.status());
    assertEquals("otp_mismatch", response.reasonCode());
    assertEquals("inline", response.metadata().credentialSource());
    assertEquals(5L, response.metadata().previousCounter());
    assertEquals(5L, response.metadata().nextCounter());
    assertTrue(response.metadata().telemetryId().startsWith("rest-hotp-"));

    ArgumentCaptor<ReplayCommand.Inline> captor =
        ArgumentCaptor.forClass(ReplayCommand.Inline.class);
    Mockito.verify(applicationService).replay(captor.capture());
    ReplayCommand.Inline command = captor.getValue();
    assertEquals("device-inline", command.identifier());
    assertEquals(HotpHashAlgorithm.SHA1, command.algorithm());
    assertEquals(6, command.digits());
    assertEquals(5L, command.counter());
    assertEquals("254676", command.otp());
    assertThat(command.metadata()).containsEntry("label", "demo");
  }

  @Test
  @DisplayName("Inline replay rejects missing OTP before invoking application service")
  void inlineReplayRequiresOtp() {
    HotpReplayApplicationService applicationService =
        Mockito.mock(HotpReplayApplicationService.class);
    HotpReplayService service = new HotpReplayService(applicationService);

    HotpReplayValidationException exception =
        assertThrows(
            HotpReplayValidationException.class,
            () ->
                service.replay(
                    new HotpReplayRequest(
                        null,
                        "device-inline",
                        "3132333435363738393031323334353637383930",
                        "SHA1",
                        6,
                        5L,
                        "  ",
                        null)));

    assertEquals("otp_required", exception.reasonCode());
    Mockito.verifyNoInteractions(applicationService);
  }

  @Test
  @DisplayName("Replay surfaces validation exceptions when application reports sanitized failures")
  void replayPropagatesValidationFailures() {
    HotpReplayApplicationService applicationService =
        Mockito.mock(HotpReplayApplicationService.class);
    TelemetrySignal signal =
        new TelemetrySignal(
            TelemetryStatus.INVALID,
            "credential_not_found",
            null,
            true,
            Map.of("credentialSource", "stored"),
            null);
    ReplayResult result = new ReplayResult(signal, true, null, 8L, 8L, null, null);
    Mockito.when(applicationService.replay(Mockito.any(ReplayCommand.Stored.class)))
        .thenReturn(result);

    HotpReplayService service = new HotpReplayService(applicationService);

    HotpReplayValidationException exception =
        assertThrows(
            HotpReplayValidationException.class,
            () ->
                service.replay(
                    new HotpReplayRequest(
                        "cred-404", null, null, null, null, null, "123456", null)));

    assertEquals("credential_not_found", exception.reasonCode());
    assertEquals("stored", exception.credentialSource());
    assertEquals("cred-404", exception.credentialId());
  }

  @Test
  @DisplayName("Replay wraps unexpected errors from the application service")
  void replayWrapsUnexpectedErrors() {
    HotpReplayApplicationService applicationService =
        Mockito.mock(HotpReplayApplicationService.class);
    TelemetrySignal signal =
        new TelemetrySignal(
            TelemetryStatus.ERROR,
            "backend_failed",
            null,
            false,
            Map.of("credentialSource", "stored"),
            null);
    ReplayResult result =
        new ReplayResult(signal, true, "cred-500", 12L, 12L, HotpHashAlgorithm.SHA1, 6);
    Mockito.when(applicationService.replay(Mockito.any(ReplayCommand.Stored.class)))
        .thenReturn(result);

    HotpReplayService service = new HotpReplayService(applicationService);

    HotpReplayUnexpectedException exception =
        assertThrows(
            HotpReplayUnexpectedException.class,
            () ->
                service.replay(
                    new HotpReplayRequest(
                        "cred-500", null, null, null, null, null, "987654", null)));

    assertEquals("stored", exception.credentialSource());
    assertThat(exception.details()).containsEntry("telemetryId", exception.telemetryId());
  }

  @Test
  @DisplayName("Stored replay requires an observed OTP before calling the application service")
  void storedReplayRequiresOtp() {
    HotpReplayApplicationService applicationService =
        Mockito.mock(HotpReplayApplicationService.class);
    HotpReplayService service = new HotpReplayService(applicationService);

    HotpReplayValidationException exception =
        assertThrows(
            HotpReplayValidationException.class,
            () ->
                service.replay(
                    new HotpReplayRequest("cred-100", null, null, null, null, null, null, null)));

    assertEquals("otp_required", exception.reasonCode());
    assertEquals("stored", exception.credentialSource());
    Mockito.verifyNoInteractions(applicationService);
  }

  @Test
  @DisplayName("Replay requires secret material when inline identifier is provided")
  void replayRequiresSecretWhenIdentifierProvided() {
    HotpReplayApplicationService applicationService =
        Mockito.mock(HotpReplayApplicationService.class);
    HotpReplayService service = new HotpReplayService(applicationService);

    HotpReplayValidationException exception =
        assertThrows(
            HotpReplayValidationException.class,
            () ->
                service.replay(
                    new HotpReplayRequest(
                        null, "device-inline", null, null, null, null, "987654", null)));

    assertEquals("mode_required", exception.reasonCode());
    Mockito.verifyNoInteractions(applicationService);
  }

  @Test
  @DisplayName("Replay requires identifier when shared secret is supplied")
  void replayRequiresIdentifierWhenSecretProvided() {
    HotpReplayApplicationService applicationService =
        Mockito.mock(HotpReplayApplicationService.class);
    HotpReplayService service = new HotpReplayService(applicationService);

    HotpReplayValidationException exception =
        assertThrows(
            HotpReplayValidationException.class,
            () ->
                service.replay(
                    new HotpReplayRequest(
                        null,
                        null,
                        "3132333435363738393031323334353637383930",
                        null,
                        null,
                        null,
                        "111111",
                        null)));

    assertEquals("mode_required", exception.reasonCode());
    Mockito.verifyNoInteractions(applicationService);
  }

  @Test
  @DisplayName(
      "Replay rejects requests that supply credential and identifier without inline secret")
  void replayRejectsIdentifierWithoutSecret() {
    HotpReplayApplicationService applicationService =
        Mockito.mock(HotpReplayApplicationService.class);
    HotpReplayService service = new HotpReplayService(applicationService);

    HotpReplayValidationException exception =
        assertThrows(
            HotpReplayValidationException.class,
            () ->
                service.replay(
                    new HotpReplayRequest(
                        "cred-identifier-only",
                        "device-inline",
                        null,
                        null,
                        null,
                        null,
                        "222222",
                        null)));

    assertEquals("mode_required", exception.reasonCode());
    Mockito.verifyNoInteractions(applicationService);
  }

  @Test
  @DisplayName("Replay rejects payloads that mix stored and inline fields")
  void replayRejectsMixedModes() {
    HotpReplayApplicationService applicationService =
        Mockito.mock(HotpReplayApplicationService.class);
    HotpReplayService service = new HotpReplayService(applicationService);

    HotpReplayValidationException exception =
        assertThrows(
            HotpReplayValidationException.class,
            () ->
                service.replay(
                    new HotpReplayRequest(
                        "cred-ambiguous",
                        null,
                        "3132333435363738393031323334353637383930",
                        "SHA1",
                        6,
                        5L,
                        "123456",
                        null)));

    assertEquals("mode_ambiguous", exception.reasonCode());
    Mockito.verifyNoInteractions(applicationService);
  }

  @Test
  @DisplayName("Replay requires either stored or inline fields to determine the mode")
  void replayRequiresMode() {
    HotpReplayApplicationService applicationService =
        Mockito.mock(HotpReplayApplicationService.class);
    HotpReplayService service = new HotpReplayService(applicationService);

    HotpReplayValidationException exception =
        assertThrows(
            HotpReplayValidationException.class,
            () ->
                service.replay(
                    new HotpReplayRequest(null, null, null, null, null, null, "123456", null)));

    assertEquals("mode_required", exception.reasonCode());
    Mockito.verifyNoInteractions(applicationService);
  }

  @Test
  @DisplayName("Inline replay requires digits metadata")
  void replayInlineRequiresDigits() {
    HotpReplayApplicationService applicationService =
        Mockito.mock(HotpReplayApplicationService.class);
    HotpReplayService service = new HotpReplayService(applicationService);

    HotpReplayValidationException exception =
        assertThrows(
            HotpReplayValidationException.class,
            () ->
                service.replay(
                    new HotpReplayRequest(
                        null,
                        "device-inline",
                        "3132333435363738393031323334353637383930",
                        "SHA1",
                        null,
                        4L,
                        "987654",
                        null)));

    assertEquals("digits_required", exception.reasonCode());
    assertEquals("inline", exception.credentialSource());
    Mockito.verifyNoInteractions(applicationService);
  }

  @Test
  @DisplayName("Replay helpers handle null-safe utilities")
  void replayUtilityMethodsHandleNulls() throws Exception {
    assertFalse(HotpReplayService.hasText("  "));
    assertTrue(HotpReplayService.hasText("demo"));

    assertEquals("", HotpReplayService.safeMessage(null));
    assertEquals("boom", HotpReplayService.safeMessage("  boom  \n"));
  }

  @Test
  @DisplayName("Inline replay rejects unsupported algorithms")
  void inlineReplayRequiresValidAlgorithm() {
    HotpReplayApplicationService applicationService =
        Mockito.mock(HotpReplayApplicationService.class);
    HotpReplayService service = new HotpReplayService(applicationService);

    HotpReplayValidationException exception =
        assertThrows(
            HotpReplayValidationException.class,
            () ->
                service.replay(
                    new HotpReplayRequest(
                        null,
                        "device-inline",
                        "3132333435363738393031323334353637383930",
                        "SHA256X",
                        6,
                        5L,
                        "254676",
                        null)));

    assertEquals("algorithm_invalid", exception.reasonCode());
    Mockito.verifyNoInteractions(applicationService);
  }

  @Test
  @DisplayName("Inline replay requires digits and counter values")
  void inlineReplayRequiresDigitsAndCounter() {
    HotpReplayApplicationService applicationService =
        Mockito.mock(HotpReplayApplicationService.class);
    HotpReplayService service = new HotpReplayService(applicationService);

    HotpReplayValidationException missingDigits =
        assertThrows(
            HotpReplayValidationException.class,
            () ->
                service.replay(
                    new HotpReplayRequest(
                        null,
                        "device-inline",
                        "3132333435363738393031323334353637383930",
                        "SHA1",
                        null,
                        5L,
                        "254676",
                        null)));
    assertEquals("digits_required", missingDigits.reasonCode());

    HotpReplayValidationException missingCounter =
        assertThrows(
            HotpReplayValidationException.class,
            () ->
                service.replay(
                    new HotpReplayRequest(
                        null,
                        "device-inline",
                        "3132333435363738393031323334353637383930",
                        "SHA1",
                        6,
                        null,
                        "254676",
                        null)));
    assertEquals("counter_required", missingCounter.reasonCode());
    Mockito.verifyNoInteractions(applicationService);
  }
}
