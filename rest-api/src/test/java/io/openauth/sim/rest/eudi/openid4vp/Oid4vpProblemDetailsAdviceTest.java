package io.openauth.sim.rest.eudi.openid4vp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.openauth.sim.application.eudi.openid4vp.Oid4vpProblemDetails;
import io.openauth.sim.application.eudi.openid4vp.Oid4vpProblemDetailsMapper;
import io.openauth.sim.application.eudi.openid4vp.Oid4vpValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

final class Oid4vpProblemDetailsAdviceTest {

    private final Oid4vpProblemDetailsAdvice advice = new Oid4vpProblemDetailsAdvice();

    @Test
    void mapsProblemDetailsResponse() {
        Oid4vpProblemDetails problem = Oid4vpProblemDetailsMapper.invalidRequest(
                "Missing dcql", List.of(new Oid4vpProblemDetails.Violation("dcqlPreset", "required")));
        Oid4vpValidationException exception = new Oid4vpValidationException(problem);

        ResponseEntity<Oid4vpProblemDetailsResponse> response = advice.handleOid4vpValidation(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Oid4vpProblemDetailsResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("invalid_request", body.title());
        assertEquals(1, body.violations().size());
        assertEquals("dcqlPreset", body.violations().get(0).field());
    }
}
