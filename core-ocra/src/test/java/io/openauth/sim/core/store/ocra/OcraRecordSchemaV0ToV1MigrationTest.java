package io.openauth.sim.core.store.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.VersionedCredentialRecordMigration;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class OcraRecordSchemaV0ToV1MigrationTest {

  private static final SecretMaterial SECRET =
      SecretMaterial.fromHex("31323334353637383930313233343536");

  @Test
  @DisplayName("supports only OATH OCRA schema version zero")
  void supportsOnlyOcraSchemaZero() {
    VersionedCredentialRecordMigration migration = new OcraRecordSchemaV0ToV1Migration();

    assertTrue(migration.supports(CredentialType.OATH_OCRA, 0));
    assertFalse(migration.supports(CredentialType.OATH_OCRA, 1));
    assertFalse(migration.supports(CredentialType.FIDO2, 0));
  }

  @Test
  @DisplayName("missing suite causes migration failure")
  void missingSuiteFailsMigration() {
    VersionedCredentialRecord legacy =
        new VersionedCredentialRecord(
            0,
            "legacy",
            CredentialType.OATH_OCRA,
            SECRET,
            Instant.parse("2025-09-20T10:15:30Z"),
            Instant.parse("2025-09-21T11:16:31Z"),
            Map.of());

    assertThrows(
        IllegalArgumentException.class,
        () -> new OcraRecordSchemaV0ToV1Migration().upgrade(legacy));
  }

  @Test
  @DisplayName("metadata keys are namespaced during upgrade")
  void metadataKeysRenamed() {
    VersionedCredentialRecord legacy =
        new VersionedCredentialRecord(
            0,
            "legacy",
            CredentialType.OATH_OCRA,
            SECRET,
            Instant.now(),
            Instant.now(),
            Map.of(
                "suite", "OCRA-1:HOTP-SHA1-6:QN08",
                "metadata.location", "lab",
                "metadata.operator", "qa"));

    VersionedCredentialRecord migrated = new OcraRecordSchemaV0ToV1Migration().upgrade(legacy);

    assertEquals("lab", migrated.attributes().get("ocra.metadata.location"));
    assertEquals("qa", migrated.attributes().get("ocra.metadata.operator"));
  }

  @Test
  @DisplayName("non-numeric counter fails migration")
  void nonNumericCounterFailsMigration() {
    VersionedCredentialRecord legacy =
        new VersionedCredentialRecord(
            0,
            "legacy",
            CredentialType.OATH_OCRA,
            SECRET,
            Instant.now(),
            Instant.now(),
            Map.of("suite", "OCRA-1:HOTP-SHA1-6:QN08", "counter", "not-a-number"));

    assertThrows(
        IllegalArgumentException.class,
        () -> new OcraRecordSchemaV0ToV1Migration().upgrade(legacy));
  }

  @Test
  @DisplayName("blank metadata key triggers validation error")
  void blankMetadataKeyFails() {
    VersionedCredentialRecord legacy =
        new VersionedCredentialRecord(
            0,
            "legacy",
            CredentialType.OATH_OCRA,
            SECRET,
            Instant.now(),
            Instant.now(),
            Map.of("suite", "OCRA-1:HOTP-SHA1-6:QN08", "metadata.", "invalid"));

    assertThrows(
        IllegalArgumentException.class,
        () -> new OcraRecordSchemaV0ToV1Migration().upgrade(legacy));
  }
}
