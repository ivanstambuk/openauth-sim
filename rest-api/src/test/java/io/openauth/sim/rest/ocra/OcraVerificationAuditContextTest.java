package io.openauth.sim.rest.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OcraVerificationAuditContextTest {

    @Test
    @DisplayName("normalises identifiers and operator principal")
    void normalisesIdentifiers() {
        OcraVerificationAuditContext context =
                new OcraVerificationAuditContext("  req-1  ", "  client-9  ", "  operator@example.com  ");

        assertEquals("req-1", context.requestId());
        assertEquals("client-9", context.clientId());
        assertEquals("operator@example.com", context.resolvedOperatorPrincipal());
    }

    @Test
    @DisplayName("blank values collapse to null and anonymous")
    void blankValuesCollapse() {
        OcraVerificationAuditContext context = new OcraVerificationAuditContext("   ", "   ", "   ");

        assertEquals(null, context.requestId());
        assertEquals(null, context.clientId());
        assertEquals("anonymous", context.resolvedOperatorPrincipal());
    }
}
