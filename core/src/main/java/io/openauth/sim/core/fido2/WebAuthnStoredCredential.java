package io.openauth.sim.core.fido2;

import java.util.Objects;

/** Immutable representation of a stored WebAuthn credential. */
public record WebAuthnStoredCredential(
    String relyingPartyId,
    byte[] credentialId,
    byte[] publicKeyCose,
    long signatureCounter,
    boolean userVerificationRequired) {

  public WebAuthnStoredCredential {
    Objects.requireNonNull(relyingPartyId, "relyingPartyId");
    Objects.requireNonNull(credentialId, "credentialId");
    Objects.requireNonNull(publicKeyCose, "publicKeyCose");
    credentialId = credentialId.clone();
    publicKeyCose = publicKeyCose.clone();
  }

  @Override
  public byte[] credentialId() {
    return credentialId.clone();
  }

  @Override
  public byte[] publicKeyCose() {
    return publicKeyCose.clone();
  }
}
