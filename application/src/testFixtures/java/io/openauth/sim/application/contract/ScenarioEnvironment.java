package io.openauth.sim.application.contract;

import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.infra.persistence.CredentialStoreFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

public record ScenarioEnvironment(CredentialStore store, Clock clock) {

    public ScenarioEnvironment {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(clock, "clock");
    }

    public static ScenarioEnvironment fixedAt(Instant instant) {
        CredentialStore store = CredentialStoreFactory.openInMemoryStore();
        Clock clock = Clock.fixed(Objects.requireNonNull(instant, "instant"), ZoneOffset.UTC);
        return new ScenarioEnvironment(store, clock);
    }
}
