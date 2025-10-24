package io.openauth.sim.core.fido2;

import java.util.Objects;

/** Container for raw WebAuthn attestation verification inputs. */
public record WebAuthnAttestationRequest(
        WebAuthnAttestationFormat format,
        byte[] attestationObject,
        byte[] clientDataJson,
        byte[] expectedChallenge,
        String relyingPartyId,
        String origin) {

    public WebAuthnAttestationRequest {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(attestationObject, "attestationObject");
        Objects.requireNonNull(clientDataJson, "clientDataJson");
        Objects.requireNonNull(expectedChallenge, "expectedChallenge");
        relyingPartyId = WebAuthnRelyingPartyId.canonicalize(Objects.requireNonNull(relyingPartyId, "relyingPartyId"));
        Objects.requireNonNull(origin, "origin");
        attestationObject = attestationObject.clone();
        clientDataJson = clientDataJson.clone();
        expectedChallenge = expectedChallenge.clone();
    }

    @Override
    public byte[] attestationObject() {
        return attestationObject.clone();
    }

    @Override
    public byte[] clientDataJson() {
        return clientDataJson.clone();
    }

    @Override
    public byte[] expectedChallenge() {
        return expectedChallenge.clone();
    }
}
