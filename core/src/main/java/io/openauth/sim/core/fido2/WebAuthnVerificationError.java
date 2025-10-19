package io.openauth.sim.core.fido2;

/** Enumerates classification codes for WebAuthn assertion verification failures. */
public enum WebAuthnVerificationError {
    CLIENT_DATA_TYPE_MISMATCH,
    CLIENT_DATA_CHALLENGE_MISMATCH,
    ORIGIN_MISMATCH,
    RP_ID_HASH_MISMATCH,
    SIGNATURE_INVALID,
    USER_VERIFICATION_REQUIRED,
    COUNTER_REGRESSION,
    ATTESTATION_FORMAT_MISMATCH,
    ATTESTATION_OBJECT_INVALID
}
