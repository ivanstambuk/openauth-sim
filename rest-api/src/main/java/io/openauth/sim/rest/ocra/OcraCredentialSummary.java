package io.openauth.sim.rest.ocra;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Lightweight view of a stored OCRA credential surfaced to operator UIs. */
@JsonInclude(JsonInclude.Include.NON_NULL)
final class OcraCredentialSummary {

    private final String id;
    private final String label;
    private final String suite;

    OcraCredentialSummary(String id, String label, String suite) {
        this.id = id;
        this.label = label;
        this.suite = suite;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getSuite() {
        return suite;
    }
}
