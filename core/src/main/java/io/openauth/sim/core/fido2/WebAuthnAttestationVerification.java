package io.openauth.sim.core.fido2;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Result wrapper for WebAuthn attestation verification. */
public final class WebAuthnAttestationVerification {

  private final WebAuthnVerificationResult result;
  private final Optional<WebAuthnStoredCredential> attestedCredential;
  private final List<X509Certificate> certificateChain;
  private final byte[] aaguid;

  private WebAuthnAttestationVerification(
      WebAuthnVerificationResult result,
      Optional<WebAuthnStoredCredential> attestedCredential,
      List<X509Certificate> certificateChain,
      byte[] aaguid) {
    this.result = Objects.requireNonNull(result, "result");
    this.attestedCredential = Objects.requireNonNull(attestedCredential, "attestedCredential");
    this.certificateChain =
        Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(certificateChain)));
    this.aaguid = (aaguid == null) ? new byte[0] : aaguid.clone();
  }

  public static WebAuthnAttestationVerification success(
      WebAuthnStoredCredential credential, List<X509Certificate> certificates, byte[] aaguid) {
    return new WebAuthnAttestationVerification(
        WebAuthnVerificationResult.successResult(),
        Optional.ofNullable(credential),
        certificates == null ? List.of() : certificates,
        aaguid);
  }

  public static WebAuthnAttestationVerification failure(
      WebAuthnVerificationError error, String message) {
    return new WebAuthnAttestationVerification(
        WebAuthnVerificationResult.failure(error, message),
        Optional.empty(),
        List.of(),
        new byte[0]);
  }

  public WebAuthnVerificationResult result() {
    return result;
  }

  public Optional<WebAuthnStoredCredential> attestedCredential() {
    return attestedCredential;
  }

  public List<X509Certificate> certificateChain() {
    return certificateChain;
  }

  public byte[] aaguid() {
    return aaguid.clone();
  }
}
