package io.openauth.sim.rest.hotp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class HotpEvaluationUnexpectedExceptionTest {

    @Test
    @DisplayName("Details map defaults to empty and is unmodifiable")
    void detailsDefaultToEmpty() {
        HotpEvaluationUnexpectedException exception =
                new HotpEvaluationUnexpectedException("telemetry-3", "stored", "boom", null, null);

        assertEquals("telemetry-3", exception.telemetryId());
        assertEquals("stored", exception.credentialSource());
        assertTrue(exception.details().isEmpty());
        assertThrows(
                UnsupportedOperationException.class, () -> exception.details().put("x", "y"));
    }

    @Test
    @DisplayName("Details map preserves provided entries")
    void detailsPreserveEntries() {
        HotpEvaluationUnexpectedException exception = new HotpEvaluationUnexpectedException(
                "telemetry-4", "inline", "error", Map.of("status", "error"), null);

        assertEquals("error", exception.details().get("status"));
    }
}
