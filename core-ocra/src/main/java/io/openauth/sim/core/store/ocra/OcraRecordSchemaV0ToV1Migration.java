package io.openauth.sim.core.store.ocra;

import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptorFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.store.VersionedCredentialRecordMigration;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Migrates legacy OCRA credential records (schema version 0) into the schema-v1 envelope that uses
 * namespaced attributes.
 */
final class OcraRecordSchemaV0ToV1Migration implements VersionedCredentialRecordMigration {

  private static final String LEGACY_SUITE = "suite";
  private static final String LEGACY_COUNTER = "counter";
  private static final String LEGACY_PIN_HASH = "pinHash";
  private static final String LEGACY_ALLOWED_DRIFT_SECONDS = "allowedDriftSeconds";
  private static final String LEGACY_METADATA_PREFIX = "metadata.";

  private final OcraCredentialDescriptorFactory descriptorFactory;

  OcraRecordSchemaV0ToV1Migration() {
    this(new OcraCredentialDescriptorFactory());
  }

  OcraRecordSchemaV0ToV1Migration(OcraCredentialDescriptorFactory descriptorFactory) {
    this.descriptorFactory = Objects.requireNonNull(descriptorFactory, "descriptorFactory");
  }

  @Override
  public boolean supports(CredentialType type, int fromVersion) {
    return type == CredentialType.OATH_OCRA && fromVersion == 0;
  }

  @Override
  public VersionedCredentialRecord upgrade(VersionedCredentialRecord record) {
    Map<String, String> attributes = record.attributes();

    String suite = attributes.get(LEGACY_SUITE);
    if (suite == null || suite.isBlank()) {
      throw new IllegalArgumentException(
          "Legacy OCRA record missing required attribute '" + LEGACY_SUITE + "'");
    }

    Long counter = parseLong(attributes.get(LEGACY_COUNTER));
    String pinHash = sanitize(attributes.get(LEGACY_PIN_HASH));
    Duration drift = parseDuration(attributes.get(LEGACY_ALLOWED_DRIFT_SECONDS));
    Map<String, String> metadata = extractMetadata(attributes);

    OcraCredentialDescriptor descriptor =
        descriptorFactory.create(
            record.name(), suite, record.secret(), counter, pinHash, drift, metadata);

    Map<String, String> upgradedAttributes = new LinkedHashMap<>();
    upgradedAttributes.put(OcraCredentialPersistenceAdapter.ATTR_SUITE, descriptor.suite().value());
    descriptor
        .counter()
        .ifPresent(
            value ->
                upgradedAttributes.put(
                    OcraCredentialPersistenceAdapter.ATTR_COUNTER, Long.toString(value)));
    descriptor
        .pinHash()
        .ifPresent(
            value ->
                upgradedAttributes.put(
                    OcraCredentialPersistenceAdapter.ATTR_PIN_HASH, value.asHex()));
    descriptor
        .allowedTimestampDrift()
        .ifPresent(
            d ->
                upgradedAttributes.put(
                    OcraCredentialPersistenceAdapter.ATTR_ALLOWED_DRIFT_SECONDS,
                    Long.toString(d.toSeconds())));
    descriptor
        .metadata()
        .forEach(
            (key, value) ->
                upgradedAttributes.put(
                    OcraCredentialPersistenceAdapter.ATTR_METADATA_PREFIX + key, value));

    return new VersionedCredentialRecord(
        VersionedCredentialRecord.CURRENT_VERSION,
        record.name(),
        record.type(),
        record.secret(),
        record.createdAt(),
        record.updatedAt(),
        upgradedAttributes);
  }

  private static Long parseLong(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Legacy counter must be numeric", ex);
    }
  }

  private static Duration parseDuration(String value) {
    Long seconds = parseLong(value);
    return seconds == null ? null : Duration.ofSeconds(seconds);
  }

  private static String sanitize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static Map<String, String> extractMetadata(Map<String, String> attributes) {
    Map<String, String> metadata = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      String key = entry.getKey();
      if (!key.startsWith(LEGACY_METADATA_PREFIX)) {
        continue;
      }
      String metadataKey = key.substring(LEGACY_METADATA_PREFIX.length());
      if (metadataKey.isBlank()) {
        throw new IllegalArgumentException("Legacy metadata key must not be blank");
      }
      metadata.put(metadataKey, entry.getValue());
    }
    return metadata;
  }
}
