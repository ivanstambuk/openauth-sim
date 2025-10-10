package io.openauth.sim.core.fido2;

import java.util.Objects;

/** Descriptor representing a persisted WebAuthn credential entry. */
public record WebAuthnCredentialDescriptor(
    String name,
    String relyingPartyId,
    byte[] credentialId,
    byte[] publicKeyCose,
    long signatureCounter,
    boolean userVerificationRequired,
    WebAuthnSignatureAlgorithm algorithm) {

  public WebAuthnCredentialDescriptor {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(relyingPartyId, "relyingPartyId");
    Objects.requireNonNull(credentialId, "credentialId");
    Objects.requireNonNull(publicKeyCose, "publicKeyCose");
    Objects.requireNonNull(algorithm, "algorithm");

    name = name.trim();
    if (name.isEmpty()) {
      throw new IllegalArgumentException("Credential name must not be blank");
    }

    relyingPartyId = relyingPartyId.trim();
    if (relyingPartyId.isEmpty()) {
      throw new IllegalArgumentException("Relying party identifier must not be blank");
    }

    credentialId = credentialId.clone();
    publicKeyCose = publicKeyCose.clone();

    if (signatureCounter < 0) {
      throw new IllegalArgumentException("Signature counter must be >= 0");
    }
  }

  @Override
  public byte[] credentialId() {
    return credentialId.clone();
  }

  @Override
  public byte[] publicKeyCose() {
    return publicKeyCose.clone();
  }

  /**
   * @return immutable stored credential value used by the verification engine.
   */
  public WebAuthnStoredCredential toStoredCredential() {
    return new WebAuthnStoredCredential(
        relyingPartyId,
        credentialId(),
        publicKeyCose(),
        signatureCounter,
        userVerificationRequired);
  }

  /** Fluent builder to make descriptor construction readable from fixtures and tests. */
  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String name;
    private String relyingPartyId;
    private byte[] credentialId;
    private byte[] publicKeyCose;
    private Long signatureCounter;
    private Boolean userVerificationRequired;
    private WebAuthnSignatureAlgorithm algorithm;

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder relyingPartyId(String relyingPartyId) {
      this.relyingPartyId = relyingPartyId;
      return this;
    }

    public Builder credentialId(byte[] credentialId) {
      this.credentialId = credentialId == null ? null : credentialId.clone();
      return this;
    }

    public Builder publicKeyCose(byte[] publicKeyCose) {
      this.publicKeyCose = publicKeyCose == null ? null : publicKeyCose.clone();
      return this;
    }

    public Builder signatureCounter(long signatureCounter) {
      this.signatureCounter = signatureCounter;
      return this;
    }

    public Builder userVerificationRequired(boolean userVerificationRequired) {
      this.userVerificationRequired = userVerificationRequired;
      return this;
    }

    public Builder algorithm(WebAuthnSignatureAlgorithm algorithm) {
      this.algorithm = algorithm;
      return this;
    }

    public WebAuthnCredentialDescriptor build() {
      if (signatureCounter == null) {
        throw new IllegalStateException("signatureCounter must be set");
      }
      if (userVerificationRequired == null) {
        throw new IllegalStateException("userVerificationRequired must be set");
      }
      return new WebAuthnCredentialDescriptor(
          name,
          relyingPartyId,
          credentialId,
          publicKeyCose,
          signatureCounter,
          userVerificationRequired,
          algorithm);
    }
  }
}
