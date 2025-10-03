package io.openauth.sim.core.store.ocra;

import io.openauth.sim.core.store.MapDbCredentialStore;
import java.util.Objects;

/**
 * Helper to centralise OCRA-specific persistence configuration. Legacy migrations have been
 * retired, but facades continue to invoke this entry point so future schema changes can be applied
 * consistently.
 */
public final class OcraStoreMigrations {

  private OcraStoreMigrations() {
    throw new AssertionError("No instances");
  }

  /**
   * Return the supplied builder after verifying it is non-null so facades keep a single entry point
   * for future schema adjustments.
   */
  public static MapDbCredentialStore.Builder apply(MapDbCredentialStore.Builder builder) {
    Objects.requireNonNull(builder, "builder");
    return builder;
  }
}
