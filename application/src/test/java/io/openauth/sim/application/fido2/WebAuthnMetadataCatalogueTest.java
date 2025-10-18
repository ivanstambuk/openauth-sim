package io.openauth.sim.application.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.fido2.WebAuthnMetadataCatalogue.MetadataSource;
import io.openauth.sim.application.fido2.WebAuthnMetadataCatalogue.TrustAnchor;
import io.openauth.sim.application.fido2.WebAuthnMetadataCatalogue.WebAuthnMetadataEntry;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class WebAuthnMetadataCatalogueTest {

  private static final String SAMPLE_ENTRY_ID = "mds-w3c-packed-es256";
  private static final UUID SAMPLE_AAGUID = UUID.fromString("876ca4f5-2071-c3e9-b255-09ef2cdf7ed6");
  private static final String SAMPLE_FINGERPRINT =
      "f0f517576cf721fb564b64d723ea22152cf2f453de4e08b491fde7161659bc45";

  private static final String LEDGER_ENTRY_ID = "mds-ledger-nanox-packed";
  private static final String TPM_ENTRY_ID = "mds-winmagic-fidoeazy-tpm";
  private static final String ANDROID_ENTRY_ID = "mds-idmelon-android-key";
  private static final String U2F_ENTRY_ID = "mds-yubikey5-u2f";

  @Test
  void exposesOfflineMdsEntriesWithTrustAnchors() {
    var entries = WebAuthnMetadataCatalogue.entries();

    assertFalse(entries.isEmpty(), "Expected offline MDS catalogue to expose at least one entry");

    Optional<WebAuthnMetadataEntry> maybeEntry =
        entries.stream().filter(entry -> SAMPLE_ENTRY_ID.equals(entry.entryId())).findFirst();

    assertTrue(
        maybeEntry.isPresent(), () -> "Expected catalogue to expose entry " + SAMPLE_ENTRY_ID);

    WebAuthnMetadataEntry entry = maybeEntry.orElseThrow();

    assertEquals(SAMPLE_AAGUID, entry.aaguid(), "Expected entry to expose canonical AAGUID");
    assertEquals(
        WebAuthnAttestationFormat.PACKED,
        entry.attestationFormat(),
        "Expected entry to record attestation format");
    assertEquals(
        "W3C Packed ES256 Sample Authenticator",
        entry.description(),
        "Expected entry to surface metadata description");

    assertFalse(entry.trustAnchors().isEmpty(), "Expected trust anchors to be populated");
    assertTrue(
        entry.trustAnchors().stream()
            .map(TrustAnchor::fingerprintSha256)
            .anyMatch(SAMPLE_FINGERPRINT::equals),
        "Expected trust anchor fingerprint to match offline dataset");
    assertTrue(
        entry.trustAnchors().stream()
            .map(TrustAnchor::certificatePem)
            .allMatch(pem -> pem.contains("-----BEGIN CERTIFICATE-----")),
        "Expected trust anchors to expose PEM material");

    assertFalse(entry.sources().isEmpty(), "Expected at least one metadata source");
    assertTrue(
        entry.sources().stream().anyMatch(sourceMatches("fixture", "w3c-packed-es256")),
        "Expected fixture source to reference attestation vector");

    // Curated MDS v3 samples should also be present for each supported format
    assertTrue(
        entries.stream().anyMatch(e -> LEDGER_ENTRY_ID.equals(e.entryId())),
        "Expected curated packed Ledger entry");
    assertTrue(
        entries.stream().anyMatch(e -> TPM_ENTRY_ID.equals(e.entryId())),
        "Expected curated TPM entry");
    assertTrue(
        entries.stream().anyMatch(e -> ANDROID_ENTRY_ID.equals(e.entryId())),
        "Expected curated Android Key entry");
    assertTrue(
        entries.stream().anyMatch(e -> U2F_ENTRY_ID.equals(e.entryId())),
        "Expected curated U2F entry");

    // Vendors sourced from the production MDS bundle should be represented
    assertTrue(
        entries.stream()
            .map(WebAuthnMetadataEntry::entryId)
            .anyMatch(id -> id.startsWith("mds-yubico-")),
        "Expected at least one Yubico entry");
    assertTrue(
        entries.stream()
            .map(WebAuthnMetadataEntry::entryId)
            .anyMatch(id -> id.startsWith("mds-thales-")),
        "Expected at least one Thales entry");
    assertTrue(
        entries.stream()
            .map(WebAuthnMetadataEntry::entryId)
            .anyMatch(id -> id.startsWith("mds-microsoft-")),
        "Expected at least one Microsoft entry");
    assertTrue(
        entries.stream()
            .map(WebAuthnMetadataEntry::entryId)
            .anyMatch(id -> id.startsWith("mds-google-")),
        "Expected at least one Google entry");
    assertFalse(
        entries.stream()
            .map(WebAuthnMetadataEntry::entryId)
            .anyMatch(id -> id.startsWith("mds-apple-")),
        "No Apple FIDO2 entries should be present in the curated bundle");

    assertTrue(
        entries.stream()
            .filter(e -> e.entryId().startsWith("mds-microsoft-"))
            .anyMatch(e -> e.attestationFormat() == WebAuthnAttestationFormat.TPM),
        "Expected Microsoft catalogue to expose TPM-backed attestation");
  }

  private static java.util.function.Predicate<MetadataSource> sourceMatches(
      String type, String vectorId) {
    return source -> type.equals(source.type()) && vectorId.equals(source.vectorId());
  }
}
