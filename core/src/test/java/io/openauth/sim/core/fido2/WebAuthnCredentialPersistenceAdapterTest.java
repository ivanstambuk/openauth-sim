package io.openauth.sim.core.fido2;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openauth.sim.core.fido2.WebAuthnFixtures.WebAuthnFixture;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WebAuthnCredentialPersistenceAdapterTest {

  private static final WebAuthnFixture PACKED_ES256 = WebAuthnFixtures.loadPackedEs256();

  private final WebAuthnCredentialPersistenceAdapter adapter =
      new WebAuthnCredentialPersistenceAdapter();

  @Test
  void serializeProducesSchemaAlignedRecord() {
    WebAuthnCredentialDescriptor descriptor =
        WebAuthnCredentialDescriptor.builder()
            .name("packed-es256")
            .relyingPartyId("example.org")
            .credentialId(PACKED_ES256.storedCredential().credentialId())
            .publicKeyCose(PACKED_ES256.storedCredential().publicKeyCose())
            .signatureCounter(0L)
            .userVerificationRequired(false)
            .algorithm(WebAuthnSignatureAlgorithm.ES256)
            .build();

    VersionedCredentialRecord record = adapter.serialize(descriptor);

    assertEquals(
        VersionedCredentialRecord.CURRENT_VERSION, record.schemaVersion(), "schema version");
    assertEquals("packed-es256", record.name(), "credential name");
    assertEquals(CredentialType.FIDO2, record.type(), "stored credential type");
    assertEquals(
        Base64.getEncoder().withoutPadding().encodeToString(descriptor.credentialId()),
        record.secret().asBase64(),
        "credential ID stored as secret");

    assertEquals("example.org", record.attributes().get("fido2.rpId"));
    assertEquals("ES256", record.attributes().get("fido2.algorithm"));
    assertEquals("-7", record.attributes().get("fido2.algorithm.cose"));
    assertEquals(
        Base64.getUrlEncoder().withoutPadding().encodeToString(descriptor.credentialId()),
        record.attributes().get("fido2.credentialId"));
    assertEquals(
        Base64.getUrlEncoder().withoutPadding().encodeToString(descriptor.publicKeyCose()),
        record.attributes().get("fido2.publicKeyCose"));
    assertEquals("0", record.attributes().get("fido2.signatureCounter"));
    assertEquals("false", record.attributes().get("fido2.userVerificationRequired"));
  }

  @Test
  void deserializeRebuildsDescriptor() {
    WebAuthnCredentialDescriptor descriptor =
        WebAuthnCredentialDescriptor.builder()
            .name("packed-es256")
            .relyingPartyId("example.org")
            .credentialId(PACKED_ES256.storedCredential().credentialId())
            .publicKeyCose(PACKED_ES256.storedCredential().publicKeyCose())
            .signatureCounter(5L)
            .userVerificationRequired(true)
            .algorithm(WebAuthnSignatureAlgorithm.ES256)
            .build();

    VersionedCredentialRecord record = adapter.serialize(descriptor);
    VersionedCredentialRecord mappedRecord =
        VersionedCredentialRecordMapper.toRecord(
            VersionedCredentialRecordMapper.toCredential(record));

    WebAuthnCredentialDescriptor deserialized = adapter.deserialize(mappedRecord);

    assertEquals(descriptor.name(), deserialized.name());
    assertEquals(descriptor.relyingPartyId(), deserialized.relyingPartyId());
    assertEquals(descriptor.algorithm(), deserialized.algorithm());
    assertEquals(descriptor.signatureCounter(), deserialized.signatureCounter());
    assertEquals(descriptor.userVerificationRequired(), deserialized.userVerificationRequired());
    assertArrayEquals(descriptor.credentialId(), deserialized.credentialId());
    assertArrayEquals(descriptor.publicKeyCose(), deserialized.publicKeyCose());

    WebAuthnStoredCredential stored = deserialized.toStoredCredential();
    assertEquals("example.org", stored.relyingPartyId());
    assertArrayEquals(descriptor.credentialId(), stored.credentialId());
    assertArrayEquals(descriptor.publicKeyCose(), stored.publicKeyCose());
    assertEquals(5L, stored.signatureCounter());
    assertEquals(true, stored.userVerificationRequired());
    assertEquals(WebAuthnSignatureAlgorithm.ES256, stored.algorithm());
  }

  @Test
  void deserializeRejectsNonFido2Type() {
    Instant now = Instant.now();
    VersionedCredentialRecord invalidRecord =
        new VersionedCredentialRecord(
            VersionedCredentialRecord.CURRENT_VERSION,
            "other",
            CredentialType.OATH_TOTP,
            SecretMaterial.fromHex("00"),
            now,
            now,
            Map.of());

    assertThrows(IllegalArgumentException.class, () -> adapter.deserialize(invalidRecord));
  }
}
