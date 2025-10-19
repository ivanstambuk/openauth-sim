package io.openauth.sim.application.hotp;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.otp.hotp.HotpDescriptor;
import io.openauth.sim.core.otp.hotp.HotpGenerator;
import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Computes non-mutating HOTP samples for stored credentials. */
public final class HotpSampleApplicationService {

    private static final String ATTR_ALGORITHM = "hotp.algorithm";
    private static final String ATTR_DIGITS = "hotp.digits";
    private static final String ATTR_COUNTER = "hotp.counter";
    private static final String ATTR_METADATA_PREFIX = "hotp.metadata.";

    private final CredentialStore credentialStore;

    public HotpSampleApplicationService(CredentialStore credentialStore) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
    }

    /**
     * Returns a sample for the provided HOTP credential, if available.
     *
     * @param credentialId identifier of the stored HOTP credential
     * @return optional sample including algorithm, digits, counter, OTP, and metadata
     */
    public Optional<StoredSample> storedSample(String credentialId) {
        if (credentialId == null || credentialId.isBlank()) {
            return Optional.empty();
        }

        return credentialStore
                .findByName(credentialId.trim())
                .filter(credential -> credential.type() == CredentialType.OATH_HOTP)
                .flatMap(this::toSample);
    }

    private Optional<StoredSample> toSample(Credential credential) {
        Map<String, String> attributes = credential.attributes();
        String algorithmValue = trim(attributes.get(ATTR_ALGORITHM));
        String digitsValue = trim(attributes.get(ATTR_DIGITS));
        String counterValue = trim(attributes.get(ATTR_COUNTER));

        if (algorithmValue == null || digitsValue == null || counterValue == null) {
            return Optional.empty();
        }

        HotpHashAlgorithm algorithm;
        int digits;
        long counter;
        try {
            algorithm = HotpHashAlgorithm.valueOf(algorithmValue.toUpperCase(Locale.ROOT));
            digits = Integer.parseInt(digitsValue);
            counter = Long.parseLong(counterValue);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }

        try {
            HotpDescriptor descriptor =
                    HotpDescriptor.create(credential.name(), credential.secret(), algorithm, digits);
            String otp = HotpGenerator.generate(descriptor, counter);

            Map<String, String> metadata = extractMetadata(attributes);
            String presetKey = trim(metadata.get("presetKey"));
            if (presetKey != null) {
                metadata.putIfAbsent("samplePresetKey", presetKey);
            }
            String presetLabel = trim(metadata.get("presetLabel"));
            if (presetLabel != null) {
                metadata.putIfAbsent("samplePresetLabel", presetLabel);
            }
            metadata.putIfAbsent("hashAlgorithm", algorithm.name());
            metadata.putIfAbsent("digits", Integer.toString(digits));
            metadata.putIfAbsent("counter", Long.toString(counter));

            return Optional.of(
                    new StoredSample(credential.name(), algorithm, digits, counter, otp, Map.copyOf(metadata)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private Map<String, String> extractMetadata(Map<String, String> attributes) {
        Map<String, String> metadata = new LinkedHashMap<>();
        attributes.forEach((key, value) -> {
            if (key != null && key.startsWith(ATTR_METADATA_PREFIX) && value != null) {
                metadata.put(key.substring(ATTR_METADATA_PREFIX.length()), value);
            }
        });
        return metadata;
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Describes a HOTP stored sample payload. */
    public record StoredSample(
            String credentialId,
            HotpHashAlgorithm algorithm,
            int digits,
            long counter,
            String otp,
            Map<String, String> metadata) {

        public StoredSample {
            Objects.requireNonNull(credentialId, "credentialId");
            Objects.requireNonNull(algorithm, "algorithm");
            Objects.requireNonNull(otp, "otp");
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }
}
