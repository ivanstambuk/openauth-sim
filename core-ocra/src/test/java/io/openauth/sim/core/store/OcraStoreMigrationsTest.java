package io.openauth.sim.core.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.ocra.OcraStoreMigrations;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

final class OcraStoreMigrationsTest {

  @TempDir Path tempDir;

  @Test
  void failsWhenLegacyRecordsExistWithoutMigration() {
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
              Instant.parse("2025-09-20T10:15:30Z"),
              Instant.parse("2025-09-21T11:16:31Z"),
              Map.of(
                  "suite", "OCRA-1:HOTP-SHA1-6:C-QN08",
                  "counter", "5",
                  "metadata.environment", "production")));
      map.put(
          "legacy-pin",
          new VersionedCredentialRecord(
              0,
              "legacy-pin",
              CredentialType.OATH_OCRA,
              SecretMaterial.fromHex(
                  "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
              Instant.parse("2025-09-22T09:00:00Z"),
              Instant.parse("2025-09-22T09:10:00Z"),
              Map.of(
                  "suite", "OCRA-1:HOTP-SHA1-6:C-QN08-PSHA1",
                  "counter", "12",
                  "pinHash", "5e884898da28047151d0e56f8dc6292773603d0d",
                  "metadata.operator", "ops")));
      db.commit();
    }

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () -> {
              try (MapDbCredentialStore store =
                  OcraStoreMigrations.apply(MapDbCredentialStore.file(dbPath)).open()) {
                store.findByName("legacy-token");
              }
            });
    assertTrue(failure.getMessage().contains("No migration path to latest schema"));
  }

  @Test
  void opensCurrentSchemaRecords() {
    Path dbPath = tempDir.resolve("current.db");

    try (DB db = DBMaker.fileDB(dbPath.toFile()).transactionEnable().closeOnJvmShutdown().make()) {
      @SuppressWarnings("unchecked")
      var map =
          (org.mapdb.HTreeMap<String, VersionedCredentialRecord>)
              db.hashMap("credentials", Serializer.STRING, Serializer.JAVA).createOrOpen();
      map.put(
          "ocra-current",
          new VersionedCredentialRecord(
              VersionedCredentialRecord.CURRENT_VERSION,
              "ocra-current",
              CredentialType.OATH_OCRA,
              SecretMaterial.fromHex("3132333435363738393031323334353637383930"),
              Instant.parse("2025-10-02T10:15:30Z"),
              Instant.parse("2025-10-02T10:16:30Z"),
              Map.of("ocra.suite", "OCRA-1:HOTP-SHA1-6:C-QN08")));
      db.commit();
    }

    try (MapDbCredentialStore store =
        OcraStoreMigrations.apply(MapDbCredentialStore.file(dbPath)).open()) {
      Credential credential = store.findByName("ocra-current").orElseThrow();
      assertEquals(CredentialType.OATH_OCRA, credential.type());
      assertEquals("OCRA-1:HOTP-SHA1-6:C-QN08", credential.attributes().get("ocra.suite"));
    }
  }
}
