package io.openauth.sim.core.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MapDbCredentialStoreTest {

  @TempDir Path tempDir;

  @Test
  void persistsAndLoadsCredentials() {
    Path dbPath = tempDir.resolve("credentials.db");
    Credential credential =
        Credential.create(
            "credential-1",
            CredentialType.GENERIC,
            SecretMaterial.fromHex("a1b2c3d4"),
            Map.of("label", "demo"));

    try (var store = MapDbCredentialStore.file(dbPath).open()) {
      store.save(credential);
    }

    try (var store = MapDbCredentialStore.file(dbPath).open()) {
      Optional<Credential> loaded = store.findByName("credential-1");
      assertTrue(loaded.isPresent(), "credential should be reloaded from disk");
      assertEquals(credential.name(), loaded.orElseThrow().name());
      assertEquals(credential.secret(), loaded.orElseThrow().secret());
    }
  }

  @Test
  void deletingRemovesCredentialAndCache() {
    Path dbPath = tempDir.resolve("credentials.db");
    Credential credential =
        Credential.create(
            "credential-2",
            CredentialType.OATH_OCRA,
            SecretMaterial.fromStringUtf8("otp-secret"),
            Map.of());

    try (var store = MapDbCredentialStore.file(dbPath).open()) {
      store.save(credential);
      assertTrue(store.delete("credential-2"));
      assertFalse(store.delete("credential-2"), "second delete should report false");
      assertTrue(store.findAll().isEmpty(), "store should be empty after delete");
    }
  }

  @Test
  void saveOverridesExistingCredential() {
    Path dbPath = tempDir.resolve("credentials.db");
    Credential initial =
        Credential.create(
            "credential-3", CredentialType.FIDO2, SecretMaterial.fromHex("0011"), Map.of());

    try (var store = MapDbCredentialStore.file(dbPath).open()) {
      store.save(initial);
      Credential updated = initial.withSecret(SecretMaterial.fromHex("ffee"));
      store.save(updated);
      Credential resolved = store.findByName("credential-3").orElseThrow();
      assertEquals(updated.secret(), resolved.secret());
      assertNotEquals(
          initial.updatedAt(), resolved.updatedAt(), "save should refresh update timestamp");
    }
  }

  @Test
  void inMemoryStoreSupportsTransientUsage() {
    try (var store = MapDbCredentialStore.inMemory().open()) {
      store.save(
          Credential.create(
              "mem",
              CredentialType.EMV_CA,
              SecretMaterial.fromBase64("YWJj"),
              Map.of("note", "transient")));
      List<Credential> credentials = store.findAll();
      assertEquals(1, credentials.size());
      assertEquals("mem", credentials.get(0).name());
    }
  }
}
