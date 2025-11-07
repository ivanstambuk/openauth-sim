package io.openauth.sim.cli.eudi.openid4vp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openauth.sim.application.eudi.openid4vp.Oid4vpProblemDetails;
import io.openauth.sim.application.eudi.openid4vp.Oid4vpProblemDetailsMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

final class Oid4vpProblemDetailsFormatterTest {

    @Test
    void formatsProblemDetailsWithViolations() {
        Oid4vpProblemDetails problem = Oid4vpProblemDetailsMapper.invalidRequest(
                "Missing dcql preset", List.of(new Oid4vpProblemDetails.Violation("dcqlPreset", "required")));

        List<String> lines = Oid4vpProblemDetailsFormatter.format(problem);

        assertEquals("invalid_request: Missing dcql preset", lines.get(0));
        assertEquals(" - dcqlPreset: required", lines.get(1));
    }
}
