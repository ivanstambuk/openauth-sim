package io.openauth.sim.core.credentials.ocra;

import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.serialization.CredentialPersistenceAdapter;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Persistence bridge responsible for serialising OCRA descriptors. */
public final class OcraCredentialPersistenceAdapter implements CredentialPersistenceAdapter<OcraCredentialDescriptor> {

    public static final int SCHEMA_VERSION = 1;
    public static final String ATTR_SUITE = "ocra.suite";
    public static final String ATTR_COUNTER = "ocra.counter";
    public static final String ATTR_PIN_HASH = "ocra.pinHash";
    public static final String ATTR_ALLOWED_DRIFT_SECONDS = "ocra.allowedTimestampDriftSeconds";
    public static final String ATTR_METADATA_PREFIX = "ocra.metadata.";

    private final OcraCredentialDescriptorFactory descriptorFactory;
    private final Clock clock;

    public OcraCredentialPersistenceAdapter() {
        this(new OcraCredentialDescriptorFactory(), Clock.systemUTC());
    }

    public OcraCredentialPersistenceAdapter(OcraCredentialDescriptorFactory descriptorFactory) {
        this(descriptorFactory, Clock.systemUTC());
    }

    public OcraCredentialPersistenceAdapter(OcraCredentialDescriptorFactory descriptorFactory, Clock clock) {
        this.descriptorFactory = Objects.requireNonNull(descriptorFactory, "descriptorFactory");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CredentialType type() {
        return CredentialType.OATH_OCRA;
    }

    @Override
    public VersionedCredentialRecord serialize(OcraCredentialDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");

        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(ATTR_SUITE, descriptor.suite().value());
        descriptor.counter().ifPresent(counter -> attributes.put(ATTR_COUNTER, Long.toString(counter)));
        descriptor.pinHash().map(SecretMaterial::asHex).ifPresent(pinHex -> attributes.put(ATTR_PIN_HASH, pinHex));
        descriptor
                .allowedTimestampDrift()
                .ifPresent(drift -> attributes.put(ATTR_ALLOWED_DRIFT_SECONDS, Long.toString(drift.toSeconds())));

        descriptor.metadata().forEach((key, value) -> attributes.put(ATTR_METADATA_PREFIX + key, value));

        Instant now = clock.instant();
        return new VersionedCredentialRecord(
                SCHEMA_VERSION, descriptor.name(), type(), descriptor.sharedSecret(), now, now, attributes);
    }

    @Override
    public OcraCredentialDescriptor deserialize(VersionedCredentialRecord record) {
        Objects.requireNonNull(record, "record");

        if (record.schemaVersion() != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schema version: " + record.schemaVersion());
        }
        if (record.type() != CredentialType.OATH_OCRA) {
            throw new IllegalArgumentException("Unsupported credential type: " + record.type());
        }

        Map<String, String> attributes = record.attributes();

        String suite = attributes.get(ATTR_SUITE);
        if (suite == null || suite.isBlank()) {
            throw new IllegalArgumentException("Missing attribute: " + ATTR_SUITE);
        }

        Long counterValue = parseLongAttribute(attributes.get(ATTR_COUNTER), ATTR_COUNTER);
        String pinHashHex = sanitizeAttribute(attributes.get(ATTR_PIN_HASH));
        Duration allowedDrift = parseDurationAttribute(attributes.get(ATTR_ALLOWED_DRIFT_SECONDS));

        Map<String, String> metadata = extractMetadata(attributes);

        return descriptorFactory.create(
                record.name(), suite, record.secret(), counterValue, pinHashHex, allowedDrift, metadata);
    }

    private static Long parseLongAttribute(String value, String attributeName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(attributeName + " must be numeric", ex);
        }
    }

    private static Duration parseDurationAttribute(String value) {
        Long seconds = parseLongAttribute(value, ATTR_ALLOWED_DRIFT_SECONDS);
        if (seconds == null) {
            return null;
        }
        return Duration.ofSeconds(seconds);
    }

    private static String sanitizeAttribute(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static Map<String, String> extractMetadata(Map<String, String> attributes) {
        Map<String, String> metadata = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(ATTR_METADATA_PREFIX)) {
                continue;
            }
            String metadataKey = key.substring(ATTR_METADATA_PREFIX.length());
            if (metadataKey.isBlank()) {
                throw new IllegalArgumentException("Metadata key must not be blank");
            }
            metadata.put(metadataKey, entry.getValue());
        }
        return metadata;
    }
}
