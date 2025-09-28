package io.openauth.sim.core.credentials.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OcraPersistenceAdapterTest {

  private OcraCredentialFactory factory;
  private OcraCredentialPersistenceAdapter adapter;
  private Clock fixedClock;

  @BeforeEach
  void setUp() {
    factory = new OcraCredentialFactory();
    fixedClock = Clock.fixed(Instant.parse("2025-09-28T12:00:00Z"), ZoneOffset.UTC);
    adapter =
        new OcraCredentialPersistenceAdapter(new OcraCredentialDescriptorFactory(), fixedClock);
  }

  @Test
  void serializeDescriptorProducesVersionedRecord() {
    OcraCredentialDescriptor descriptor = descriptorWithMetadata();

    VersionedCredentialRecord record = adapter.serialize(descriptor);

    assertEquals(OcraCredentialPersistenceAdapter.SCHEMA_VERSION, record.schemaVersion());
    assertEquals("token-1", record.name());
    assertEquals(CredentialType.OATH_OCRA, record.type());
    assertEquals(descriptor.sharedSecret(), record.secret());
    assertEquals(fixedClock.instant(), record.createdAt());
    assertEquals(fixedClock.instant(), record.updatedAt());
    assertEquals(
        descriptor.suite().value(),
        record.attributes().get(OcraCredentialPersistenceAdapter.ATTR_SUITE));
    assertEquals("1", record.attributes().get(OcraCredentialPersistenceAdapter.ATTR_COUNTER));
    assertEquals(
        "5e884898da28047151d0e56f8dc6292773603d0d",
        record.attributes().get(OcraCredentialPersistenceAdapter.ATTR_PIN_HASH));
    assertEquals(
        String.valueOf(Duration.ofMinutes(5).toSeconds()),
        record.attributes().get(OcraCredentialPersistenceAdapter.ATTR_ALLOWED_DRIFT_SECONDS));
    assertEquals(
        "Example Bank",
        record.attributes().get(OcraCredentialPersistenceAdapter.ATTR_METADATA_PREFIX + "issuer"));
  }

  @Test
  void deserializeRecordRestoresDescriptor() {
    Map<String, String> attributes = new HashMap<>();
    attributes.put(OcraCredentialPersistenceAdapter.ATTR_SUITE, "OCRA-1:HOTP-SHA1-6:C-QN08-PSHA1");
    attributes.put(OcraCredentialPersistenceAdapter.ATTR_COUNTER, "10");
    attributes.put(
        OcraCredentialPersistenceAdapter.ATTR_PIN_HASH, "5e884898da28047151d0e56f8dc6292773603d0d");
    attributes.put(
        OcraCredentialPersistenceAdapter.ATTR_ALLOWED_DRIFT_SECONDS,
        String.valueOf(Duration.ofSeconds(30).toSeconds()));
    attributes.put(
        OcraCredentialPersistenceAdapter.ATTR_METADATA_PREFIX + "environment", "staging");

    VersionedCredentialRecord record =
        new VersionedCredentialRecord(
            OcraCredentialPersistenceAdapter.SCHEMA_VERSION,
            "persisted-token",
            CredentialType.OATH_OCRA,
            SecretMaterial.fromHex("3132333435363738393031323334353637383930"),
            Instant.parse("2025-09-20T10:15:30Z"),
            Instant.parse("2025-09-21T11:16:31Z"),
            attributes);

    OcraCredentialDescriptor descriptor = adapter.deserialize(record);

    assertEquals("persisted-token", descriptor.name());
    assertEquals("OCRA-1:HOTP-SHA1-6:C-QN08-PSHA1", descriptor.suite().value());
    assertTrue(descriptor.counter().isPresent());
    assertEquals(10L, descriptor.counter().orElseThrow());
    assertTrue(descriptor.pinHash().isPresent());
    assertEquals(
        "5e884898da28047151d0e56f8dc6292773603d0d", descriptor.pinHash().orElseThrow().asHex());
    assertTrue(descriptor.allowedTimestampDrift().isPresent());
    assertEquals(Duration.ofSeconds(30), descriptor.allowedTimestampDrift().orElseThrow());
    assertEquals("staging", descriptor.metadata().get("environment"));
  }

  @Test
  void deserializeRejectsMissingSuite() {
    Map<String, String> attributes = Map.of();
    VersionedCredentialRecord record =
        new VersionedCredentialRecord(
            OcraCredentialPersistenceAdapter.SCHEMA_VERSION,
            "broken-token",
            CredentialType.OATH_OCRA,
            SecretMaterial.fromBase64("YWJj"),
            fixedClock.instant(),
            fixedClock.instant(),
            attributes);

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> adapter.deserialize(record));
    assertTrue(ex.getMessage().contains(OcraCredentialPersistenceAdapter.ATTR_SUITE));
  }

  private OcraCredentialDescriptor descriptorWithMetadata() {
    OcraCredentialFactory.OcraCredentialRequest request =
        new OcraCredentialFactory.OcraCredentialRequest(
            "token-1",
            "OCRA-1:HOTP-SHA1-6:C-QN08-PSHA1",
            "3132333435363738393031323334353637383930",
            SecretEncoding.HEX,
            1L,
            "5e884898da28047151d0e56f8dc6292773603d0d",
            Duration.ofMinutes(5),
            Map.of("issuer", "Example Bank"));
    return factory.createDescriptor(request);
  }
}
