package io.openauth.sim.core.otp.totp;

import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.store.serialization.CredentialPersistenceAdapter;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Persistence bridge responsible for serialising TOTP descriptors. */
public final class TotpCredentialPersistenceAdapter implements CredentialPersistenceAdapter<TotpDescriptor> {

    public static final int SCHEMA_VERSION = VersionedCredentialRecord.CURRENT_VERSION;

    public static final String ATTR_ALGORITHM = "totp.algorithm";
    public static final String ATTR_DIGITS = "totp.digits";
    public static final String ATTR_STEP_SECONDS = "totp.stepSeconds";
    public static final String ATTR_DRIFT_BACKWARD = "totp.drift.backward";
    public static final String ATTR_DRIFT_FORWARD = "totp.drift.forward";

    private final Clock clock;

    public TotpCredentialPersistenceAdapter() {
        this(Clock.systemUTC());
    }

    public TotpCredentialPersistenceAdapter(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CredentialType type() {
        return CredentialType.OATH_TOTP;
    }

    @Override
    public VersionedCredentialRecord serialize(TotpDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");

        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(ATTR_ALGORITHM, descriptor.algorithm().name());
        attributes.put(ATTR_DIGITS, Integer.toString(descriptor.digits()));
        attributes.put(ATTR_STEP_SECONDS, Long.toString(descriptor.stepSeconds()));
        attributes.put(
                ATTR_DRIFT_BACKWARD, Integer.toString(descriptor.driftWindow().backwardSteps()));
        attributes.put(
                ATTR_DRIFT_FORWARD, Integer.toString(descriptor.driftWindow().forwardSteps()));

        Instant now = clock.instant();
        return new VersionedCredentialRecord(
                SCHEMA_VERSION, descriptor.name(), type(), descriptor.secret(), now, now, attributes);
    }

    @Override
    public TotpDescriptor deserialize(VersionedCredentialRecord record) {
        Objects.requireNonNull(record, "record");

        if (record.schemaVersion() != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schema version: " + record.schemaVersion());
        }
        if (record.type() != CredentialType.OATH_TOTP) {
            throw new IllegalArgumentException("Unsupported credential type: " + record.type());
        }

        Map<String, String> attributes = record.attributes();

        String algorithmValue = required(attributes, ATTR_ALGORITHM);
        String digitsValue = required(attributes, ATTR_DIGITS);
        String stepValue = required(attributes, ATTR_STEP_SECONDS);
        String driftBackwardValue = required(attributes, ATTR_DRIFT_BACKWARD);
        String driftForwardValue = required(attributes, ATTR_DRIFT_FORWARD);

        TotpHashAlgorithm algorithm = parseAlgorithm(algorithmValue);
        int digits = parsePositiveInt(digitsValue, ATTR_DIGITS);
        long stepSeconds = parsePositiveLong(stepValue, ATTR_STEP_SECONDS);
        int backwardSteps = parseNonNegativeInt(driftBackwardValue, ATTR_DRIFT_BACKWARD);
        int forwardSteps = parseNonNegativeInt(driftForwardValue, ATTR_DRIFT_FORWARD);

        TotpDriftWindow driftWindow = TotpDriftWindow.of(backwardSteps, forwardSteps);

        return TotpDescriptor.create(
                record.name(), record.secret(), algorithm, digits, Duration.ofSeconds(stepSeconds), driftWindow);
    }

    private static String required(Map<String, String> attributes, String key) {
        String value = attributes.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing attribute: " + key);
        }
        return value.trim();
    }

    private static TotpHashAlgorithm parseAlgorithm(String value) {
        try {
            return TotpHashAlgorithm.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported TOTP algorithm: " + value, ex);
        }
    }

    private static int parsePositiveInt(String value, String attribute) {
        int parsed = parseInt(value, attribute);
        if (parsed <= 0) {
            throw new IllegalArgumentException(attribute + " must be positive");
        }
        return parsed;
    }

    private static int parseNonNegativeInt(String value, String attribute) {
        int parsed = parseInt(value, attribute);
        if (parsed < 0) {
            throw new IllegalArgumentException(attribute + " must be >= 0");
        }
        return parsed;
    }

    private static int parseInt(String value, String attribute) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(attribute + " must be numeric", ex);
        }
    }

    private static long parsePositiveLong(String value, String attribute) {
        long parsed;
        try {
            parsed = Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(attribute + " must be numeric", ex);
        }
        if (parsed <= 0) {
            throw new IllegalArgumentException(attribute + " must be positive");
        }
        return parsed;
    }
}
