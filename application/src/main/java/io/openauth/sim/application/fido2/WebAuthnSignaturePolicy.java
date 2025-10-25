package io.openauth.sim.application.fido2;

import java.util.Objects;

/** Configures signature verification policies for WebAuthn operations. */
public final class WebAuthnSignaturePolicy {

    private final boolean enforceLowS;

    private WebAuthnSignaturePolicy(boolean enforceLowS) {
        this.enforceLowS = enforceLowS;
    }

    /** Returns a policy that observes signature characteristics without enforcing them. */
    public static WebAuthnSignaturePolicy observeOnly() {
        return new WebAuthnSignaturePolicy(false);
    }

    /** Returns a policy that enforces low-S ECDSA signatures. */
    public static WebAuthnSignaturePolicy enforceLowSPolicy() {
        return new WebAuthnSignaturePolicy(true);
    }

    /** Creates a custom policy. */
    public static WebAuthnSignaturePolicy of(boolean enforceLowS) {
        return new WebAuthnSignaturePolicy(enforceLowS);
    }

    public boolean enforceLowS() {
        return enforceLowS;
    }

    @Override
    public String toString() {
        return "WebAuthnSignaturePolicy{" + "enforceLowS=" + enforceLowS + '}';
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof WebAuthnSignaturePolicy that)) {
            return false;
        }
        return enforceLowS == that.enforceLowS;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enforceLowS);
    }
}
