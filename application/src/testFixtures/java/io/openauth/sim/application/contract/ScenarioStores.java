package io.openauth.sim.application.contract;

import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.infra.persistence.CredentialStoreFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class ScenarioStores {

    private ScenarioStores() {
        throw new AssertionError("No instances");
    }

    public static void seedFileStore(
            Path databasePath, Instant instant, Function<ScenarioEnvironment, List<CanonicalScenario>> scenarios)
            throws IOException {
        Objects.requireNonNull(databasePath, "databasePath");
        Objects.requireNonNull(instant, "instant");
        Objects.requireNonNull(scenarios, "scenarios");

        try (CredentialStore store = CredentialStoreFactory.openFileStore(databasePath)) {
            ScenarioEnvironment env = new ScenarioEnvironment(store, Clock.fixed(instant, ZoneOffset.UTC));
            scenarios.apply(env);
        }
    }
}
