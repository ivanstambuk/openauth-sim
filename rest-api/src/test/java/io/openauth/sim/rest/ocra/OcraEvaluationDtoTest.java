package io.openauth.sim.rest.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class OcraEvaluationDtoTest {

    @Test
    @DisplayName("OcraEvaluationRequest trims whitespace and normalises blanks to null")
    void requestTrimsAndNormalisesInput() {
        OcraEvaluationRequest request = new OcraEvaluationRequest(
                "  credential-123  ",
                "  OCRA-1:HOTP-SHA1-6:QA08  ",
                "  313233  ",
                "  12345678  ",
                "   ",
                null,
                " server-challenge ",
                "   ",
                "  0000000F  ",
                42L,
                null);

        assertEquals("credential-123", request.credentialId());
        assertEquals("OCRA-1:HOTP-SHA1-6:QA08", request.suite());
        assertEquals("313233", request.sharedSecretHex());
        assertEquals("12345678", request.challenge());
        assertNull(request.sessionHex());
        assertNull(request.clientChallenge());
        assertEquals("server-challenge", request.serverChallenge());
        assertNull(request.pinHashHex());
        assertEquals("0000000F", request.timestampHex());
        assertEquals(42L, request.counter());
    }

    @Test
    @DisplayName("OcraEvaluationResponse trims optional fields while preserving null values")
    void responseTrimsOptionalFields() {
        OcraEvaluationResponse response =
                new OcraEvaluationResponse("  OCRA-1:HOTP-SHA1-6:QA08  ", " 012345  ", null, null);

        assertEquals("OCRA-1:HOTP-SHA1-6:QA08", response.suite());
        assertEquals("012345", response.otp());
        assertNull(response.telemetryId());

        OcraEvaluationResponse nullResponse = new OcraEvaluationResponse(null, null, "  telemetry-001  ", null);

        assertNull(nullResponse.suite());
        assertNull(nullResponse.otp());
        assertEquals("telemetry-001", nullResponse.telemetryId());
    }

    @Test
    @DisplayName("OcraEvaluationErrorResponse defaults to an empty defensive copy of details")
    void errorResponseProvidesDefensiveCopy() {
        OcraEvaluationErrorResponse empty = new OcraEvaluationErrorResponse("invalid", "message", null, null);

        assertTrue(empty.details().isEmpty());

        Map<String, String> mutableDetails = new HashMap<>();
        mutableDetails.put("reason", "ignored");

        OcraEvaluationErrorResponse response =
                new OcraEvaluationErrorResponse("invalid", "message", mutableDetails, null);

        assertEquals(1, response.details().size());
        assertEquals("ignored", response.details().get("reason"));
        assertNotSame(mutableDetails, response.details());

        mutableDetails.put("new", "value");
        assertEquals(1, response.details().size(), "mutating caller map must not affect response");

        assertThrows(
                UnsupportedOperationException.class, () -> response.details().put("x", "y"));
    }
}
