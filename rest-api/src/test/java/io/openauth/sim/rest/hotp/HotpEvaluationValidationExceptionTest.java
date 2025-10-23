package io.openauth.sim.rest.hotp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class HotpEvaluationValidationExceptionTest {

    @Test
    @DisplayName("Details map defaults to empty when null supplied")
    void detailsDefaultToEmptyMap() {
        HotpEvaluationValidationException exception = new HotpEvaluationValidationException(
                "telemetry-1", "stored", "demo", "credentialId_required", true, null, "credentialId missing", null);

        assertTrue(exception.details().isEmpty());
        assertEquals("telemetry-1", exception.telemetryId());
        assertEquals("stored", exception.credentialSource());
        assertEquals("demo", exception.credentialId());
        assertEquals("credentialId_required", exception.reasonCode());
        assertTrue(exception.sanitized());
    }

    @Test
    @DisplayName("Details map is unmodifiable")
    void detailsUnmodifiable() {
        HotpEvaluationValidationException exception = new HotpEvaluationValidationException(
                "telemetry-2",
                "inline",
                null,
                "algorithm_invalid",
                true,
                Map.<String, Object>of("field", "algorithm"),
                "algorithm invalid",
                null);

        assertThrows(
                UnsupportedOperationException.class, () -> exception.details().put("x", "y"));
    }
}
