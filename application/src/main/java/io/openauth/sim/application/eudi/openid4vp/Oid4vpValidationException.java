package io.openauth.sim.application.eudi.openid4vp;

/** Runtime exception carrying RFC 7807 problem details for OpenID4VP flows. */
public final class Oid4vpValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Oid4vpProblemDetails problemDetails;

    public Oid4vpValidationException(Oid4vpProblemDetails problemDetails) {
        super(problemDetails == null ? "OID4VP validation failed" : problemDetails.detail());
        this.problemDetails = problemDetails;
    }

    public Oid4vpProblemDetails problemDetails() {
        return problemDetails;
    }
}
