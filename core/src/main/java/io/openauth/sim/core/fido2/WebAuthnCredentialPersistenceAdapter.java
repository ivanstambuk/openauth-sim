package io.openauth.sim.core.fido2;

import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.serialization.CredentialPersistenceAdapter;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Persistence adapter bridging WebAuthn credential descriptors and MapDB records. */
public final class WebAuthnCredentialPersistenceAdapter
    implements CredentialPersistenceAdapter<WebAuthnCredentialDescriptor> {

  public static final int SCHEMA_VERSION = VersionedCredentialRecord.CURRENT_VERSION;

  static final String ATTR_RP_ID = "fido2.rpId";
  static final String ATTR_CREDENTIAL_ID = "fido2.credentialId";
  static final String ATTR_PUBLIC_KEY_COSE = "fido2.publicKeyCose";
  static final String ATTR_SIGNATURE_COUNTER = "fido2.signatureCounter";
  static final String ATTR_UV_REQUIRED = "fido2.userVerificationRequired";
  static final String ATTR_ALGORITHM = "fido2.algorithm";
  static final String ATTR_ALGORITHM_COSE = "fido2.algorithm.cose";

  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

  private final Clock clock;

  public WebAuthnCredentialPersistenceAdapter() {
    this(Clock.systemUTC());
  }

  public WebAuthnCredentialPersistenceAdapter(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public CredentialType type() {
    return CredentialType.FIDO2;
  }

  @Override
  public VersionedCredentialRecord serialize(WebAuthnCredentialDescriptor descriptor) {
    Objects.requireNonNull(descriptor, "descriptor");

    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put(ATTR_RP_ID, descriptor.relyingPartyId());
    attributes.put(ATTR_CREDENTIAL_ID, URL_ENCODER.encodeToString(descriptor.credentialId()));
    attributes.put(ATTR_PUBLIC_KEY_COSE, URL_ENCODER.encodeToString(descriptor.publicKeyCose()));
    attributes.put(ATTR_SIGNATURE_COUNTER, Long.toString(descriptor.signatureCounter()));
    attributes.put(ATTR_UV_REQUIRED, Boolean.toString(descriptor.userVerificationRequired()));
    attributes.put(ATTR_ALGORITHM, descriptor.algorithm().label());
    attributes.put(ATTR_ALGORITHM_COSE, Integer.toString(descriptor.algorithm().coseIdentifier()));

    Instant now = clock.instant();
    return new VersionedCredentialRecord(
        SCHEMA_VERSION,
        descriptor.name(),
        type(),
        SecretMaterial.fromBytes(descriptor.credentialId()),
        now,
        now,
        attributes);
  }

  @Override
  public WebAuthnCredentialDescriptor deserialize(VersionedCredentialRecord record) {
    Objects.requireNonNull(record, "record");
    if (record.schemaVersion() != SCHEMA_VERSION) {
      throw new IllegalArgumentException("Unsupported schema version: " + record.schemaVersion());
    }
    if (record.type() != CredentialType.FIDO2) {
      throw new IllegalArgumentException("Unsupported credential type: " + record.type());
    }

    Map<String, String> attributes = record.attributes();
    String relyingPartyId = require(attributes, ATTR_RP_ID);
    byte[] credentialId = decode(require(attributes, ATTR_CREDENTIAL_ID), ATTR_CREDENTIAL_ID);
    byte[] publicKey = decode(require(attributes, ATTR_PUBLIC_KEY_COSE), ATTR_PUBLIC_KEY_COSE);
    long signatureCounter =
        parseNonNegativeLong(require(attributes, ATTR_SIGNATURE_COUNTER), ATTR_SIGNATURE_COUNTER);
    boolean uvRequired = parseBoolean(require(attributes, ATTR_UV_REQUIRED), ATTR_UV_REQUIRED);

    WebAuthnSignatureAlgorithm algorithm = resolveAlgorithm(attributes);

    return WebAuthnCredentialDescriptor.builder()
        .name(record.name())
        .relyingPartyId(relyingPartyId)
        .credentialId(credentialId)
        .publicKeyCose(publicKey)
        .signatureCounter(signatureCounter)
        .userVerificationRequired(uvRequired)
        .algorithm(algorithm)
        .build();
  }

  private static WebAuthnSignatureAlgorithm resolveAlgorithm(Map<String, String> attributes) {
    String label = attributes.get(ATTR_ALGORITHM);
    String coseValue = attributes.get(ATTR_ALGORITHM_COSE);

    if (label != null && !label.isBlank()) {
      try {
        WebAuthnSignatureAlgorithm resolved = WebAuthnSignatureAlgorithm.fromLabel(label);
        if (coseValue != null && !coseValue.isBlank()) {
          int coseId = parseInt(coseValue, ATTR_ALGORITHM_COSE);
          if (coseId != resolved.coseIdentifier()) {
            throw new IllegalArgumentException(
                "Algorithm metadata mismatch between label and COSE identifier");
          }
        }
        return resolved;
      } catch (IllegalArgumentException ex) {
        // fall back to COSE identifier if provided
        if (coseValue == null || coseValue.isBlank()) {
          throw ex;
        }
      }
    }

    if (coseValue == null || coseValue.isBlank()) {
      throw new IllegalArgumentException("Missing algorithm metadata");
    }
    int coseId = parseInt(coseValue, ATTR_ALGORITHM_COSE);
    return WebAuthnSignatureAlgorithm.fromCoseIdentifier(coseId);
  }

  private static String require(Map<String, String> attributes, String key) {
    String value = attributes.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing attribute: " + key);
    }
    return value.trim();
  }

  private static byte[] decode(String value, String attribute) {
    try {
      return URL_DECODER.decode(value);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(attribute + " must be Base64URL encoded", ex);
    }
  }

  private static long parseNonNegativeLong(String value, String attribute) {
    long parsed = parseLong(value, attribute);
    if (parsed < 0) {
      throw new IllegalArgumentException(attribute + " must be >= 0");
    }
    return parsed;
  }

  private static long parseLong(String value, String attribute) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(attribute + " must be numeric", ex);
    }
  }

  private static int parseInt(String value, String attribute) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(attribute + " must be numeric", ex);
    }
  }

  private static boolean parseBoolean(String value, String attribute) {
    if ("true".equalsIgnoreCase(value)) {
      return true;
    }
    if ("false".equalsIgnoreCase(value)) {
      return false;
    }
    throw new IllegalArgumentException(attribute + " must be true or false");
  }
}
