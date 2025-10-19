package io.openauth.sim.core.fido2;

import java.util.Objects;

/** Encapsulates the data required to verify a WebAuthn assertion response. */
public record WebAuthnAssertionRequest(
        String relyingPartyId,
        String origin,
        byte[] expectedChallenge,
        byte[] clientDataJson,
        byte[] authenticatorData,
        byte[] signature,
        String expectedType) {

    public WebAuthnAssertionRequest {
        Objects.requireNonNull(relyingPartyId, "relyingPartyId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(expectedChallenge, "expectedChallenge");
        Objects.requireNonNull(clientDataJson, "clientDataJson");
        Objects.requireNonNull(authenticatorData, "authenticatorData");
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(expectedType, "expectedType");
        expectedChallenge = expectedChallenge.clone();
        clientDataJson = clientDataJson.clone();
        authenticatorData = authenticatorData.clone();
        signature = signature.clone();
    }

    @Override
    public byte[] expectedChallenge() {
        return expectedChallenge.clone();
    }

    @Override
    public byte[] clientDataJson() {
        return clientDataJson.clone();
    }

    @Override
    public byte[] authenticatorData() {
        return authenticatorData.clone();
    }

    @Override
    public byte[] signature() {
        return signature.clone();
    }
}
