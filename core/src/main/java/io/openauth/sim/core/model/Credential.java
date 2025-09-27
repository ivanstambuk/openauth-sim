package io.openauth.sim.core.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/** Represents a single credential stored and replayed by the emulator. */
public record Credential(
    String name,
    CredentialType type,
    SecretMaterial secret,
    Map<String, String> attributes,
    Instant createdAt,
    Instant updatedAt)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  public Credential {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(secret, "secret");
    Objects.requireNonNull(attributes, "attributes");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");

    name = name.trim();
    if (name.isEmpty()) {
      throw new IllegalArgumentException("Credential name must not be blank");
    }

    attributes = Collections.unmodifiableMap(attributes);
  }

  public static Credential create(
      String name, CredentialType type, SecretMaterial secret, Map<String, String> attributes) {
    Instant now = Instant.now();
    return new Credential(name, type, secret, Map.copyOf(attributes), now, now);
  }

  public Credential withSecret(SecretMaterial secretMaterial) {
    Objects.requireNonNull(secretMaterial, "secretMaterial");
    return new Credential(name, type, secretMaterial, attributes, createdAt, Instant.now());
  }

  public Credential withAttributes(Map<String, String> newAttributes) {
    Objects.requireNonNull(newAttributes, "newAttributes");
    return new Credential(name, type, secret, Map.copyOf(newAttributes), createdAt, Instant.now());
  }
}
