package io.openauth.sim.core.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

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

    Optional<Credential> loaded;
    try (var store = MapDbCredentialStore.file(dbPath).open()) {
      loaded = store.findByName("credential-1");
    }

    assertTrue(loaded.isPresent(), "credential should be reloaded from disk");
    assertEquals(credential.name(), loaded.orElseThrow().name());
    assertEquals(credential.secret(), loaded.orElseThrow().secret());
    assertEquals(
        VersionedCredentialRecord.CURRENT_VERSION,
        readRawRecord(dbPath, "credential-1").schemaVersion());
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

  @Test
  void upgradesLegacyOcraRecord() {
    Path dbPath = tempDir.resolve("legacy.db");

    try (DB db = DBMaker.fileDB(dbPath.toFile()).transactionEnable().closeOnJvmShutdown().make()) {
      @SuppressWarnings("unchecked")
      var map =
          (org.mapdb.HTreeMap<String, VersionedCredentialRecord>)
              db.hashMap("credentials", Serializer.STRING, Serializer.JAVA).createOrOpen();
      map.put(
          "legacy-token",
          new VersionedCredentialRecord(
              0,
              "legacy-token",
              CredentialType.OATH_OCRA,
              SecretMaterial.fromHex("3132333435363738393031323334353637383930"),
              java.time.Instant.parse("2025-09-20T10:15:30Z"),
              java.time.Instant.parse("2025-09-21T11:16:31Z"),
              Map.of(
                  "suite", "OCRA-1:HOTP-SHA1-6:C-QN08",
                  "counter", "5",
                  "metadata.environment", "production")));
      db.commit();
    }

    try (var store = MapDbCredentialStore.file(dbPath).open()) {
      Credential credential = store.findByName("legacy-token").orElseThrow();
      assertEquals(CredentialType.OATH_OCRA, credential.type());
      assertEquals("legacy-token", credential.name());
      assertEquals("production", credential.attributes().get("ocra.metadata.environment"));
    }

    VersionedCredentialRecord migrated = readRawRecord(dbPath, "legacy-token");
    assertEquals(VersionedCredentialRecord.CURRENT_VERSION, migrated.schemaVersion());
    assertEquals("OCRA-1:HOTP-SHA1-6:C-QN08", migrated.attributes().get("ocra.suite"));
    assertEquals("5", migrated.attributes().get("ocra.counter"));
  }

  private VersionedCredentialRecord readRawRecord(Path dbPath, String name) {
    try (DB db = DBMaker.fileDB(dbPath.toFile()).transactionEnable().closeOnJvmShutdown().make()) {
      @SuppressWarnings("unchecked")
      var map =
          (org.mapdb.HTreeMap<String, VersionedCredentialRecord>)
              db.hashMap("credentials", Serializer.STRING, Serializer.JAVA).createOrOpen();
      return map.get(name);
    }
  }
}
