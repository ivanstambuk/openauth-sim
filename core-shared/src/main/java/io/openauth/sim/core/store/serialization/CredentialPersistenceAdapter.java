package io.openauth.sim.core.store.serialization;

import io.openauth.sim.core.model.CredentialType;

/** Contract for converting protocol-specific descriptors to persistence records and back. */
public interface CredentialPersistenceAdapter<T> {

  /**
   * @return credential type serviced by this adapter.
   */
  CredentialType type();

  /**
   * Serialize the supplied descriptor into a versioned persistence record.
   *
   * @throws NullPointerException when {@code descriptor} is null.
   */
  VersionedCredentialRecord serialize(T descriptor);

  /**
   * Recreate a protocol-specific descriptor from a versioned persistence record.
   *
   * @throws NullPointerException when {@code record} is null.
   */
  T deserialize(VersionedCredentialRecord record);
}
