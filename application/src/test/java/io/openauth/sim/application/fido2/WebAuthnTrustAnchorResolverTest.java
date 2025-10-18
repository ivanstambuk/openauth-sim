package io.openauth.sim.application.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationRequest;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerification;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerifier;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

final class WebAuthnTrustAnchorResolverTest {

  private static final Base64.Encoder MIME_ENCODER = Base64.getMimeEncoder(64, new byte[] {'\n'});

  @Test
  void resolvesMetadataAnchorsWhenNoManualAnchorsProvided() {
    WebAuthnTrustAnchorResolver resolver = new WebAuthnTrustAnchorResolver();

    WebAuthnTrustAnchorResolver.Resolution resolution =
        resolver.resolvePemStrings("w3c-packed-es256", WebAuthnAttestationFormat.PACKED, List.of());

    assertTrue(resolution.hasAnchors());
    assertTrue(resolution.cached());
    assertEquals(WebAuthnTrustAnchorResolver.Source.METADATA, resolution.source());
    assertEquals("mds-w3c-packed-es256", resolution.metadataEntryId());
    assertTrue(resolution.warnings().isEmpty());
  }

  @Test
  void combinesMetadataAndManualAnchorsWithoutDuplicates() throws Exception {
    WebAuthnAttestationVector vector =
        WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
            .findFirst()
            .orElseThrow();

    WebAuthnAttestationVerification verification =
        new WebAuthnAttestationVerifier()
            .verify(
                new WebAuthnAttestationRequest(
                    vector.format(),
                    vector.registration().attestationObject(),
                    vector.registration().clientDataJson(),
                    vector.registration().challenge(),
                    vector.relyingPartyId(),
                    vector.origin()));

    X509Certificate root =
        verification.certificateChain().get(verification.certificateChain().size() - 1);

    WebAuthnTrustAnchorResolver resolver = new WebAuthnTrustAnchorResolver();
    WebAuthnTrustAnchorResolver.Resolution resolution =
        resolver.resolvePemStrings(
            vector.vectorId(), vector.format(), List.of(toPem(root), toPem(root)));

    assertTrue(resolution.hasAnchors());
    assertFalse(resolution.cached());
    assertEquals(WebAuthnTrustAnchorResolver.Source.COMBINED, resolution.source());
    assertEquals("mds-w3c-packed-es256", resolution.metadataEntryId());
    assertEquals(1, resolution.anchors().size());
  }

  @Test
  void returnsEmptyResolutionWhenMetadataMissingAndNoAnchorsProvided() {
    WebAuthnTrustAnchorResolver resolver = new WebAuthnTrustAnchorResolver();
    WebAuthnTrustAnchorResolver.Resolution resolution =
        resolver.resolvePemStrings(
            "unknown-attestation", WebAuthnAttestationFormat.PACKED, List.of());

    assertFalse(resolution.hasAnchors());
    assertFalse(resolution.cached());
    assertEquals(WebAuthnTrustAnchorResolver.Source.NONE, resolution.source());
    assertTrue(resolution.warnings().isEmpty());
    assertEquals(null, resolution.metadataEntryId());
  }

  private static String toPem(X509Certificate certificate) throws CertificateEncodingException {
    String encoded = MIME_ENCODER.encodeToString(certificate.getEncoded());
    return "-----BEGIN CERTIFICATE-----\n" + encoded + "\n-----END CERTIFICATE-----";
  }
}
