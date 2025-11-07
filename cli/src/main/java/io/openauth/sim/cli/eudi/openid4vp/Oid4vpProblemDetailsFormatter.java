package io.openauth.sim.cli.eudi.openid4vp;

import io.openauth.sim.application.eudi.openid4vp.Oid4vpProblemDetails;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Oid4vpProblemDetailsFormatter {

    private Oid4vpProblemDetailsFormatter() {
        throw new AssertionError("Utility class");
    }

    public static List<String> format(Oid4vpProblemDetails problem) {
        Objects.requireNonNull(problem, "problem");
        List<String> lines = new ArrayList<>();
        lines.add(problem.title() + ": " + problem.detail());
        for (Oid4vpProblemDetails.Violation violation : problem.violations()) {
            lines.add(" - " + violation.field() + ": " + violation.message());
        }
        return List.copyOf(lines);
    }
}
