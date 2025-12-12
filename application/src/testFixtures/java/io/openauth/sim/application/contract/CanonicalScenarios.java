package io.openauth.sim.application.contract;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class CanonicalScenarios {

    private CanonicalScenarios() {
        throw new AssertionError("No instances");
    }

    public static CanonicalScenario scenarioForDescriptor(
            ScenarioEnvironment env,
            CanonicalScenario descriptor,
            Function<ScenarioEnvironment, List<CanonicalScenario>> scenarios) {
        Objects.requireNonNull(descriptor, "descriptor");
        return scenarioById(env, descriptor.scenarioId(), scenarios);
    }

    public static CanonicalScenario scenarioById(
            ScenarioEnvironment env,
            String scenarioId,
            Function<ScenarioEnvironment, List<CanonicalScenario>> scenarios) {
        Objects.requireNonNull(env, "env");
        Objects.requireNonNull(scenarioId, "scenarioId");
        Objects.requireNonNull(scenarios, "scenarios");

        return scenarios.apply(env).stream()
                .filter(scenario -> scenarioId.equals(scenario.scenarioId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Canonical scenario not found: " + scenarioId));
    }
}
