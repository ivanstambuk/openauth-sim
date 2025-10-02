package io.openauth.sim.core.store.ocra;

import io.openauth.sim.core.store.MapDbCredentialStore;
import java.util.Objects;

/** Helper to register OCRA-specific migrations with shared persistence builders. */
public final class OcraStoreMigrations {

  private OcraStoreMigrations() {
    throw new AssertionError("No instances");
  }

  /**
   * Register the OCRA record migration with the supplied builder, returning the same builder for
   * fluent chaining.
   */
  public static MapDbCredentialStore.Builder apply(MapDbCredentialStore.Builder builder) {
    Objects.requireNonNull(builder, "builder");
    return builder.registerMigration(new OcraRecordSchemaV0ToV1Migration());
  }
}
