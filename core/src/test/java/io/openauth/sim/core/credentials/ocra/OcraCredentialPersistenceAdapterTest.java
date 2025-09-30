package io.openauth.sim.core.credentials.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OcraCredentialPersistenceAdapterTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2025-09-30T12:00:00Z"), ZoneOffset.UTC);
  private static final SecretMaterial SECRET = SecretMaterial.fromHex("31323334");

  @Test
  @DisplayName("serialize writes optional attributes when present")
  void serializeWritesOptionalAttributes() {
    OcraCredentialDescriptor descriptor =
        new OcraCredentialDescriptor(
            "credential",
            OcraSuiteParser.parse("OCRA-1:HOTP-SHA1-6:C-QN08-PSHA1"),
            SECRET,
            java.util.Optional.of(10L),
            java.util.Optional.of(SECRET),
            java.util.Optional.of(Duration.ofSeconds(30)),
            Map.of("source", "test"));

    OcraCredentialPersistenceAdapter adapter =
        new OcraCredentialPersistenceAdapter(new OcraCredentialDescriptorFactory(), FIXED_CLOCK);

    VersionedCredentialRecord record = adapter.serialize(descriptor);

    assertEquals(CredentialType.OATH_OCRA, record.type());
    assertEquals("10", record.attributes().get(OcraCredentialPersistenceAdapter.ATTR_COUNTER));
    assertEquals(
        SECRET.asHex(), record.attributes().get(OcraCredentialPersistenceAdapter.ATTR_PIN_HASH));
    assertEquals(
        "30", record.attributes().get(OcraCredentialPersistenceAdapter.ATTR_ALLOWED_DRIFT_SECONDS));
    assertEquals(
        "test",
        record.attributes().get(OcraCredentialPersistenceAdapter.ATTR_METADATA_PREFIX + "source"));
  }

  @Test
  @DisplayName("deserialize validates schema, type, and metadata keys")
  void deserializeValidatesAttributes() {
    OcraCredentialPersistenceAdapter adapter =
        new OcraCredentialPersistenceAdapter(new OcraCredentialDescriptorFactory(), FIXED_CLOCK);

    VersionedCredentialRecord invalidSchema =
        new VersionedCredentialRecord(
            99, "name", CredentialType.OATH_OCRA, SECRET, Instant.now(), Instant.now(), Map.of());
    assertThrows(IllegalArgumentException.class, () -> adapter.deserialize(invalidSchema));

    VersionedCredentialRecord invalidType =
        new VersionedCredentialRecord(
            OcraCredentialPersistenceAdapter.SCHEMA_VERSION,
            "name",
            CredentialType.GENERIC,
            SECRET,
            Instant.now(),
            Instant.now(),
            Map.of());
    assertThrows(IllegalArgumentException.class, () -> adapter.deserialize(invalidType));

    VersionedCredentialRecord missingSuite =
        new VersionedCredentialRecord(
            OcraCredentialPersistenceAdapter.SCHEMA_VERSION,
            "name",
            CredentialType.OATH_OCRA,
            SECRET,
            Instant.now(),
            Instant.now(),
            Map.of());
    assertThrows(IllegalArgumentException.class, () -> adapter.deserialize(missingSuite));

    VersionedCredentialRecord invalidCounter =
        new VersionedCredentialRecord(
            OcraCredentialPersistenceAdapter.SCHEMA_VERSION,
            "name",
            CredentialType.OATH_OCRA,
            SECRET,
            Instant.now(),
            Instant.now(),
            Map.of(
                OcraCredentialPersistenceAdapter.ATTR_SUITE, "OCRA-1:HOTP-SHA1-6:C",
                OcraCredentialPersistenceAdapter.ATTR_COUNTER, "not-a-number"));
    assertThrows(IllegalArgumentException.class, () -> adapter.deserialize(invalidCounter));

    VersionedCredentialRecord invalidMetadataKey =
        new VersionedCredentialRecord(
            OcraCredentialPersistenceAdapter.SCHEMA_VERSION,
            "name",
            CredentialType.OATH_OCRA,
            SECRET,
            Instant.now(),
            Instant.now(),
            Map.of(
                OcraCredentialPersistenceAdapter.ATTR_SUITE,
                "OCRA-1:HOTP-SHA1-6:C",
                OcraCredentialPersistenceAdapter.ATTR_METADATA_PREFIX + " ",
                "value"));
    assertThrows(IllegalArgumentException.class, () -> adapter.deserialize(invalidMetadataKey));
  }

  @Test
  @DisplayName("deserialize trims optional attributes")
  void deserializeTrimsOptionalAttributes() {
    Map<String, String> attributes =
        Map.of(
            OcraCredentialPersistenceAdapter.ATTR_SUITE, "OCRA-1:HOTP-SHA1-6:C-QN08-PSHA1",
            OcraCredentialPersistenceAdapter.ATTR_COUNTER, " 5 ",
            OcraCredentialPersistenceAdapter.ATTR_PIN_HASH,
                " abcdef1234567890abcdef1234567890abcdef12 ",
            OcraCredentialPersistenceAdapter.ATTR_ALLOWED_DRIFT_SECONDS, " 60 ");

    VersionedCredentialRecord record =
        new VersionedCredentialRecord(
            OcraCredentialPersistenceAdapter.SCHEMA_VERSION,
            "name",
            CredentialType.OATH_OCRA,
            SECRET,
            Instant.now(),
            Instant.now(),
            attributes);

    OcraCredentialPersistenceAdapter adapter =
        new OcraCredentialPersistenceAdapter(new OcraCredentialDescriptorFactory(), FIXED_CLOCK);

    OcraCredentialDescriptor descriptor = adapter.deserialize(record);

    assertEquals("OCRA-1:HOTP-SHA1-6:C-QN08-PSHA1", descriptor.suite().value());
    assertEquals(5L, descriptor.counter().orElseThrow().longValue());
    assertTrue(descriptor.pinHash().isPresent());
    assertEquals(Duration.ofSeconds(60), descriptor.allowedTimestampDrift().orElseThrow());
  }
}
