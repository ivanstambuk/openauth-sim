package io.openauth.sim.core.otp.totp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TotpCredentialPersistenceAdapterTest {

  private static final Instant FIXED_INSTANT = Instant.parse("2025-10-08T10:15:30Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
  private static final SecretMaterial SECRET =
      SecretMaterial.fromHex("31323334353637383930313233343536");

  private final TotpCredentialPersistenceAdapter adapter =
      new TotpCredentialPersistenceAdapter(FIXED_CLOCK);

  @Test
  void serializeProducesVersionedRecordWithNormalizedAttributes() {
    TotpDescriptor descriptor =
        TotpDescriptor.create(
            "totp-demo",
            SECRET,
            TotpHashAlgorithm.SHA256,
            8,
            Duration.ofSeconds(60),
            TotpDriftWindow.of(2, 3));

    VersionedCredentialRecord record = adapter.serialize(descriptor);

    assertEquals(
        TotpCredentialPersistenceAdapter.SCHEMA_VERSION, record.schemaVersion(), "schema version");
    assertEquals(CredentialType.OATH_TOTP, record.type(), "credential type");
    assertEquals(SECRET, record.secret(), "secret material");
    assertEquals(FIXED_INSTANT, record.createdAt(), "createdAt timestamp");
    assertEquals(FIXED_INSTANT, record.updatedAt(), "updatedAt timestamp");

    Map<String, String> attributes = record.attributes();
    assertEquals("SHA256", attributes.get("totp.algorithm"));
    assertEquals("8", attributes.get("totp.digits"));
    assertEquals("60", attributes.get("totp.stepSeconds"));
    assertEquals("2", attributes.get("totp.drift.backward"));
    assertEquals("3", attributes.get("totp.drift.forward"));
  }

  @Test
  void deserializeRestoresDescriptorFromAttributes() {
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put("totp.algorithm", "SHA512");
    attributes.put("totp.digits", "6");
    attributes.put("totp.stepSeconds", "30");
    attributes.put("totp.drift.backward", "1");
    attributes.put("totp.drift.forward", "2");

    VersionedCredentialRecord record =
        new VersionedCredentialRecord(
            TotpCredentialPersistenceAdapter.SCHEMA_VERSION,
            "totp-record",
            CredentialType.OATH_TOTP,
            SECRET,
            FIXED_INSTANT,
            FIXED_INSTANT,
            attributes);

    TotpDescriptor descriptor = adapter.deserialize(record);

    assertEquals("totp-record", descriptor.name());
    assertEquals(SECRET, descriptor.secret());
    assertEquals(TotpHashAlgorithm.SHA512, descriptor.algorithm());
    assertEquals(6, descriptor.digits());
    assertEquals(Duration.ofSeconds(30), descriptor.stepDuration());
    assertEquals(1, descriptor.driftWindow().backwardSteps());
    assertEquals(2, descriptor.driftWindow().forwardSteps());
  }

  @Test
  void deserializeRejectsMissingAttributes() {
    Map<String, String> attributes = Map.of("totp.algorithm", "SHA1");
    VersionedCredentialRecord record =
        new VersionedCredentialRecord(
            TotpCredentialPersistenceAdapter.SCHEMA_VERSION,
            "totp-record",
            CredentialType.OATH_TOTP,
            SECRET,
            FIXED_INSTANT,
            FIXED_INSTANT,
            attributes);

    assertThrows(IllegalArgumentException.class, () -> adapter.deserialize(record));
  }
}
