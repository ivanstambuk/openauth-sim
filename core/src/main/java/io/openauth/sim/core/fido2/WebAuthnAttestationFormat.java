package io.openauth.sim.core.fido2;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Supported WebAuthn attestation statement formats handled by the simulator. */
public enum WebAuthnAttestationFormat {
  PACKED("packed"),
  FIDO_U2F("fido-u2f"),
  TPM("tpm"),
  ANDROID_KEY("android-key");

  private static final Map<String, WebAuthnAttestationFormat> BY_LABEL =
      Stream.of(values())
          .collect(Collectors.toUnmodifiableMap(WebAuthnAttestationFormat::label, f -> f));

  private final String label;

  WebAuthnAttestationFormat(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  public static WebAuthnAttestationFormat fromLabel(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Attestation format label cannot be null");
    }
    WebAuthnAttestationFormat format = BY_LABEL.get(value.toLowerCase(Locale.ROOT));
    if (format == null) {
      throw new IllegalArgumentException("Unsupported attestation format: " + value);
    }
    return format;
  }
}
