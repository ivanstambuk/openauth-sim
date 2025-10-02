package io.openauth.sim.core.store;

import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;

/**
 * Represents a migration step that can upgrade a stored {@link VersionedCredentialRecord} to a
 * newer schema version.
 */
public interface VersionedCredentialRecordMigration {

  /**
   * @return {@code true} when this migration handles the supplied credential type and schema
   *     version.
   */
  boolean supports(CredentialType type, int fromVersion);

  /** Perform the upgrade, returning a record with a greater schema version. */
  VersionedCredentialRecord upgrade(VersionedCredentialRecord record);
}
