package io.openauth.sim.rest.totp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openauth.sim.application.totp.TotpEvaluationApplicationService.TelemetrySignal;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.TelemetryStatus;
import io.openauth.sim.application.totp.TotpReplayApplicationService;
import io.openauth.sim.application.totp.TotpReplayApplicationService.ReplayCommand;
import io.openauth.sim.application.totp.TotpReplayApplicationService.ReplayResult;
import io.openauth.sim.core.encoding.Base32SecretCodec;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.otp.totp.TotpHashAlgorithm;
import io.openauth.sim.core.trace.VerboseTrace;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

final class TotpReplayServiceTest {

    private TotpReplayApplicationService applicationService;
    private TotpReplayService service;

    @BeforeEach
    void setUp() {
        applicationService = Mockito.mock(TotpReplayApplicationService.class);
        service = new TotpReplayService(applicationService);
    }

    @Test
    @DisplayName("Inline replay accepts Base32 shared secrets")
    void inlineReplayAcceptsBase32Secrets() {
        Map<String, Object> telemetryFields = new LinkedHashMap<>();
        telemetryFields.put("telemetryId", "rest-totp-inline");
        TelemetrySignal signal = new TelemetrySignal(TelemetryStatus.SUCCESS, "match", null, true, telemetryFields);
        ReplayResult result = new ReplayResult(
                signal,
                false,
                null,
                true,
                0,
                TotpHashAlgorithm.SHA1,
                6,
                Duration.ofSeconds(30),
                TotpDriftWindow.of(1, 1),
                "inline",
                false,
                (VerboseTrace) null);
        when(applicationService.replay(any(ReplayCommand.class), anyBoolean())).thenReturn(result);

        String base32 = "MFRGGZDFMZTWQ2LK";
        TotpReplayRequest request =
                new TotpReplayRequest(null, "123456", null, null, 1, 1, null, base32, "SHA1", 6, 30L, Boolean.FALSE);

        TotpReplayResponse response = service.replay(request);

        assertEquals("match", response.reasonCode());
        assertEquals("inline", response.metadata().credentialSource());

        ArgumentCaptor<ReplayCommand.Inline> captor = ArgumentCaptor.forClass(ReplayCommand.Inline.class);
        verify(applicationService).replay(captor.capture(), anyBoolean());
        assertEquals(Base32SecretCodec.toUpperHex(base32), captor.getValue().sharedSecretHex());
    }

    @Test
    @DisplayName("Inline replay rejects invalid Base32 secrets")
    void inlineReplayRejectsInvalidBase32Secrets() {
        TotpReplayRequest request = new TotpReplayRequest(
                null, "123456", null, null, null, null, null, "!!!!", "SHA1", 6, 30L, Boolean.FALSE);

        TotpReplayValidationException exception =
                assertThrows(TotpReplayValidationException.class, () -> service.replay(request));

        assertEquals("shared_secret_base32_invalid", exception.reasonCode());
        assertEquals("sharedSecretBase32", exception.details().get("field"));
    }
}
