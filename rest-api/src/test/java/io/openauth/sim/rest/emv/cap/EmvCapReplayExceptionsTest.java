package io.openauth.sim.rest.emv.cap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class EmvCapReplayExceptionsTest {

    @Test
    void validationExceptionDefaultsToEmptyUnmodifiableDetails() {
        EmvCapReplayValidationException exception =
                new EmvCapReplayValidationException("invalid_input", "otp is required", null);

        assertEquals("invalid_input", exception.reasonCode());
        assertTrue(exception.details().isEmpty(), "Null details should map to an empty set");
        assertThrows(
                UnsupportedOperationException.class,
                () -> exception.details().put("field", "otp"),
                "Returned details map must be unmodifiable");
    }

    @Test
    void validationExceptionWrapsProvidedDetails() {
        Map<String, Object> details = new HashMap<>();
        details.put("field", "otp");

        EmvCapReplayValidationException exception =
                new EmvCapReplayValidationException("invalid_input", "otp is required", details);

        assertEquals("invalid_input", exception.reasonCode());
        assertEquals("otp", exception.details().get("field"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> exception.details().put("extra", "value"),
                "Wrapped details map must be unmodifiable");
    }

    @Test
    void unexpectedExceptionDefaultsToEmptyDetails() {
        EmvCapReplayUnexpectedException exception = new EmvCapReplayUnexpectedException("failure", null);

        assertTrue(exception.details().isEmpty(), "Null details should map to an empty set");
        assertThrows(
                UnsupportedOperationException.class,
                () -> exception.details().put("field", "otp"),
                "Returned details map must be unmodifiable");
    }

    @Test
    void unexpectedExceptionWrapsProvidedDetails() {
        Map<String, Object> details = new HashMap<>();
        details.put("mode", "SIGN");

        EmvCapReplayUnexpectedException exception = new EmvCapReplayUnexpectedException("failure", details);

        assertEquals("SIGN", exception.details().get("mode"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> exception.details().put("extra", "value"),
                "Wrapped details map must be unmodifiable");
    }
}
