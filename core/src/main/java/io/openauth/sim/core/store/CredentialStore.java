package io.openauth.sim.core.store;

import io.openauth.sim.core.model.Credential;
import java.util.List;
import java.util.Optional;

/** Abstraction for credential persistence used by higher-level modules. */
public interface CredentialStore extends AutoCloseable {

    /** Persist or replace the provided credential. */
    void save(Credential credential);

    /** Retrieve a credential by its unique name. */
    Optional<Credential> findByName(String name);

    /**
     * @return immutable snapshot of all persisted credentials.
     */
    List<Credential> findAll();

    /**
     * Delete the credential with the given name if it exists.
     *
     * @return {@code true} if a credential was removed, otherwise {@code false}.
     */
    boolean delete(String name);

    /**
     * @return {@code true} when a credential with the provided name exists.
     */
    default boolean exists(String name) {
        return findByName(name).isPresent();
    }

    @Override
    void close();
}
