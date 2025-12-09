package io.openauth.sim.cli.support;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal in-memory {@link CredentialStore} for inline CLI flows.
 *
 * <p>Designed to keep thin standalone executions free of MapDB dependencies while still satisfying
 * application service contracts that expect a {@link CredentialStore} instance.
 */
public final class EphemeralCredentialStore implements CredentialStore {

    private final Map<String, Credential> credentials = new LinkedHashMap<>();

    @Override
    public void save(Credential credential) {
        credentials.put(credential.name(), credential);
    }

    @Override
    public Optional<Credential> findByName(String name) {
        return Optional.ofNullable(credentials.get(name));
    }

    @Override
    public List<Credential> findAll() {
        return List.copyOf(credentials.values());
    }

    @Override
    public boolean delete(String name) {
        return credentials.remove(name) != null;
    }

    @Override
    public void close() {
        credentials.clear();
    }
}
