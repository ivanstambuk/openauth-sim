package io.openauth.sim.core.emv.cap;

import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.store.serialization.CredentialPersistenceAdapter;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Persistence adapter for {@link EmvCapCredentialDescriptor} entries. */
public final class EmvCapCredentialPersistenceAdapter
        implements CredentialPersistenceAdapter<EmvCapCredentialDescriptor> {

    public static final String ATTR_MODE = "emv.cap.mode";
    public static final String ATTR_BRANCH_FACTOR = "emv.cap.branchFactor";
    public static final String ATTR_HEIGHT = "emv.cap.height";
    public static final String ATTR_IV = "emv.cap.iv";
    public static final String ATTR_CDOL1 = "emv.cap.cdol1";
    public static final String ATTR_IPB = "emv.cap.issuerProprietaryBitmap";
    public static final String ATTR_ICC_TEMPLATE = "emv.cap.icc.template";
    public static final String ATTR_ISSUER_APPLICATION_DATA = "emv.cap.issuerApplicationData";
    public static final String ATTR_DEFAULT_ATC = "emv.cap.defaults.atc";
    public static final String ATTR_DEFAULT_CHALLENGE = "emv.cap.defaults.challenge";
    public static final String ATTR_DEFAULT_REFERENCE = "emv.cap.defaults.reference";
    public static final String ATTR_DEFAULT_AMOUNT = "emv.cap.defaults.amount";
    public static final String ATTR_TRANSACTION_TERMINAL = "emv.cap.transaction.terminal";
    public static final String ATTR_TRANSACTION_ICC = "emv.cap.transaction.icc";
    public static final String ATTR_TRANSACTION_ICC_RESOLVED = "emv.cap.transaction.iccResolved";

    private final Clock clock;

    public EmvCapCredentialPersistenceAdapter() {
        this(Clock.systemUTC());
    }

    public EmvCapCredentialPersistenceAdapter(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CredentialType type() {
        return CredentialType.EMV_CA;
    }

    @Override
    public VersionedCredentialRecord serialize(EmvCapCredentialDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");

        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(ATTR_MODE, descriptor.mode().name());
        attributes.put(ATTR_BRANCH_FACTOR, Integer.toString(descriptor.branchFactor()));
        attributes.put(ATTR_HEIGHT, Integer.toString(descriptor.height()));
        attributes.put(ATTR_IV, descriptor.ivHex());
        attributes.put(ATTR_CDOL1, descriptor.cdol1Hex());
        attributes.put(ATTR_IPB, descriptor.issuerProprietaryBitmapHex());
        attributes.put(ATTR_ICC_TEMPLATE, descriptor.iccDataTemplateHex());
        attributes.put(ATTR_ISSUER_APPLICATION_DATA, descriptor.issuerApplicationDataHex());
        attributes.put(ATTR_DEFAULT_ATC, descriptor.defaultAtcHex());
        attributes.put(ATTR_DEFAULT_CHALLENGE, descriptor.defaultChallenge());
        attributes.put(ATTR_DEFAULT_REFERENCE, descriptor.defaultReference());
        attributes.put(ATTR_DEFAULT_AMOUNT, descriptor.defaultAmount());
        descriptor.terminalDataHex().ifPresent(value -> attributes.put(ATTR_TRANSACTION_TERMINAL, value));
        descriptor.iccDataHex().ifPresent(value -> attributes.put(ATTR_TRANSACTION_ICC, value));
        descriptor.resolvedIccDataHex().ifPresent(value -> attributes.put(ATTR_TRANSACTION_ICC_RESOLVED, value));

        Instant now = clock.instant();
        return new VersionedCredentialRecord(
                VersionedCredentialRecord.CURRENT_VERSION,
                descriptor.name(),
                type(),
                descriptor.masterKey(),
                now,
                now,
                attributes);
    }

    @Override
    public EmvCapCredentialDescriptor deserialize(VersionedCredentialRecord record) {
        Objects.requireNonNull(record, "record");
        if (record.schemaVersion() != VersionedCredentialRecord.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported schema version: " + record.schemaVersion());
        }
        if (record.type() != CredentialType.EMV_CA) {
            throw new IllegalArgumentException("Unsupported credential type: " + record.type());
        }

        Map<String, String> attributes = record.attributes();

        String modeValue = require(attributes, ATTR_MODE);
        EmvCapMode mode = EmvCapMode.fromLabel(modeValue.toUpperCase(Locale.ROOT));
        int branchFactor = parsePositiveInt(attributes.get(ATTR_BRANCH_FACTOR), ATTR_BRANCH_FACTOR);
        int height = parsePositiveInt(attributes.get(ATTR_HEIGHT), ATTR_HEIGHT);

        return new EmvCapCredentialDescriptor(
                record.name(),
                mode,
                record.secret(),
                require(attributes, ATTR_DEFAULT_ATC),
                branchFactor,
                height,
                require(attributes, ATTR_IV),
                require(attributes, ATTR_CDOL1),
                require(attributes, ATTR_IPB),
                require(attributes, ATTR_ICC_TEMPLATE),
                require(attributes, ATTR_ISSUER_APPLICATION_DATA),
                attributes.getOrDefault(ATTR_DEFAULT_CHALLENGE, ""),
                attributes.getOrDefault(ATTR_DEFAULT_REFERENCE, ""),
                attributes.getOrDefault(ATTR_DEFAULT_AMOUNT, ""),
                optional(attributes, ATTR_TRANSACTION_TERMINAL),
                optional(attributes, ATTR_TRANSACTION_ICC),
                optional(attributes, ATTR_TRANSACTION_ICC_RESOLVED));
    }

    private static String require(Map<String, String> attributes, String key) {
        String value = attributes.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing attribute: " + key);
        }
        return value.trim();
    }

    private static int parsePositiveInt(String value, String attribute) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing attribute: " + attribute);
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) {
                throw new IllegalArgumentException(attribute + " must be positive");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(attribute + " must be numeric", ex);
        }
    }

    private static Optional<String> optional(Map<String, String> attributes, String key) {
        String value = attributes.get(key);
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(trimmed);
    }
}
