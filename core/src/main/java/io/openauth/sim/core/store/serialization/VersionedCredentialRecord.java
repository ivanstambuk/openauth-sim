package io.openauth.sim.core.store.serialization;

import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Value object capturing the data required to persist a credential along with a schema version for
 * future migrations.
 */
public record VersionedCredentialRecord(
    int schemaVersion,
    String name,
    CredentialType type,
    SecretMaterial secret,
    Instant createdAt,
    Instant updatedAt,
    Map<String, String> attributes)
    implements Serializable {

  public static final int CURRENT_VERSION = 1;

  @Serial private static final long serialVersionUID = 1L;

  public VersionedCredentialRecord {
    if (schemaVersion < 0) {
      throw new IllegalArgumentException("schemaVersion must be >= 0");
    }
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(secret, "secret");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
    if (updatedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("updatedAt must not be before createdAt");
    }
    Objects.requireNonNull(attributes, "attributes");
    name = name.trim();
    if (name.isEmpty()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    attributes = Map.copyOf(attributes);
  }
}
