package io.openauth.sim.core.model;

/** Supported credential categories for the emulator. */
public enum CredentialType {
  FIDO2,
  OATH_OCRA,
  OATH_HOTP,
  EUDI_MDL,
  EMV_CA,
  GENERIC
}
