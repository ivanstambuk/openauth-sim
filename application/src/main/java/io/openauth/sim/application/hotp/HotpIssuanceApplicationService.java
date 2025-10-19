package io.openauth.sim.application.hotp;

import io.openauth.sim.application.telemetry.HotpTelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.hotp.HotpDescriptor;
import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Application-level HOTP issuance orchestrator (tests drive implementation). */
public final class HotpIssuanceApplicationService {

    private static final String ATTR_ALGORITHM = "hotp.algorithm";
    private static final String ATTR_DIGITS = "hotp.digits";
    private static final String ATTR_COUNTER = "hotp.counter";
    private static final String ATTR_METADATA_PREFIX = "hotp.metadata.";

    private final CredentialStore credentialStore;

    public HotpIssuanceApplicationService(CredentialStore credentialStore) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
    }

    public IssuanceResult issue(IssuanceCommand command) {
        Objects.requireNonNull(command, "command");

        Map<String, String> metadata = Map.of();
        try {
            if (command.initialCounter() < 0) {
                throw new IllegalArgumentException("initialCounter must be non-negative");
            }

            metadata = normalizeMetadata(command.metadata());
            SecretMaterial secret = SecretMaterial.fromHex(command.sharedSecretHex());
            HotpDescriptor descriptor =
                    HotpDescriptor.create(command.credentialId(), secret, command.algorithm(), command.digits());

            Optional<Credential> existing = credentialStore.findByName(command.credentialId());
            if (existing.isPresent() && existing.get().type() != CredentialType.OATH_HOTP) {
                return typeMismatchResult(command, existing.get().type(), metadata.size());
            }

            boolean created = existing.isEmpty();
            Map<String, String> attributes = buildAttributes(command, metadata);

            Credential persisted = existing.map(current -> new Credential(
                            current.name(),
                            CredentialType.OATH_HOTP,
                            descriptor.secret(),
                            attributes,
                            current.createdAt(),
                            Instant.now()))
                    .orElseGet(() -> Credential.create(
                            command.credentialId(), CredentialType.OATH_HOTP, descriptor.secret(), attributes));

            credentialStore.save(persisted);

            TelemetrySignal signal = issuanceSuccessSignal(command, metadata.size(), created);
            return new IssuanceResult(signal, command.credentialId(), created);
        } catch (IllegalArgumentException ex) {
            TelemetrySignal signal = issuanceValidationFailure(command, ex.getMessage(), metadata.size());
            return new IssuanceResult(signal, command.credentialId(), false);
        } catch (RuntimeException ex) {
            TelemetrySignal signal = issuanceError(command, ex, metadata.size());
            return new IssuanceResult(signal, command.credentialId(), false);
        }
    }

    public record IssuanceCommand(
            String credentialId,
            String sharedSecretHex,
            HotpHashAlgorithm algorithm,
            int digits,
            long initialCounter,
            Map<String, String> metadata) {

        public IssuanceCommand {
            credentialId = Objects.requireNonNull(credentialId, "credentialId").trim();
            sharedSecretHex =
                    Objects.requireNonNull(sharedSecretHex, "sharedSecretHex").trim();
            algorithm = Objects.requireNonNull(algorithm, "algorithm");
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    public record IssuanceResult(TelemetrySignal telemetry, String credentialId, boolean created) {

        public IssuanceFrame issuanceFrame(HotpTelemetryAdapter adapter, String telemetryId) {
            return new IssuanceFrame(telemetry.emit(adapter, telemetryId));
        }
    }

    public record IssuanceFrame(TelemetryFrame frame) {
        // Marker type ensuring consistent API with evaluation frames.
    }

    public record TelemetrySignal(
            TelemetryStatus status,
            String reasonCode,
            String reason,
            boolean sanitized,
            Map<String, Object> fields,
            String statusOverride) {

        public TelemetryFrame emit(HotpTelemetryAdapter adapter, String telemetryId) {
            Objects.requireNonNull(adapter, "adapter");
            Objects.requireNonNull(telemetryId, "telemetryId");
            Objects.requireNonNull(fields, "fields");

            if (statusOverride != null && !statusOverride.isBlank()) {
                return adapter.status(statusOverride, telemetryId, reasonCode, sanitized, reason, fields);
            }

            return switch (status) {
                case SUCCESS -> adapter.success(telemetryId, fields);
                case INVALID -> adapter.validationFailure(telemetryId, reasonCode, reason, sanitized, fields);
                case ERROR -> adapter.error(telemetryId, reasonCode, reason, sanitized, fields);
            };
        }
    }

    public enum TelemetryStatus {
        SUCCESS,
        INVALID,
        ERROR
    }

    private TelemetrySignal issuanceSuccessSignal(IssuanceCommand command, int metadataSize, boolean created) {
        Map<String, Object> fields = issuanceFields(command, metadataSize, created);
        return new TelemetrySignal(TelemetryStatus.SUCCESS, "issued", null, true, fields, "issued");
    }

    private TelemetrySignal issuanceValidationFailure(IssuanceCommand command, String reason, int metadataSize) {
        Map<String, Object> fields = issuanceFields(command, metadataSize, false);
        return new TelemetrySignal(
                TelemetryStatus.INVALID, "validation_error", safeMessage(reason), true, fields, null);
    }

    private TelemetrySignal issuanceError(IssuanceCommand command, Throwable error, int metadataSize) {
        Map<String, Object> fields = issuanceFields(command, metadataSize, false);
        if (error != null) {
            fields.put("exception", error.getClass().getName() + ": " + safeMessage(error));
        }
        return new TelemetrySignal(TelemetryStatus.ERROR, "unexpected_error", safeMessage(error), false, fields, null);
    }

    private IssuanceResult typeMismatchResult(IssuanceCommand command, CredentialType actualType, int metadataSize) {
        Map<String, Object> fields = issuanceFields(command, metadataSize, false);
        fields.put("existingType", actualType.name());
        TelemetrySignal signal = new TelemetrySignal(
                TelemetryStatus.INVALID,
                "type_mismatch",
                "Existing credential is type " + actualType,
                true,
                fields,
                null);
        return new IssuanceResult(signal, command.credentialId(), false);
    }

    private Map<String, Object> issuanceFields(IssuanceCommand command, int metadataSize, boolean created) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("credentialId", command.credentialId());
        fields.put("hashAlgorithm", command.algorithm().name());
        fields.put("digits", command.digits());
        fields.put("initialCounter", command.initialCounter());
        fields.put("metadataSize", metadataSize);
        fields.put("created", created);
        return fields;
    }

    private Map<String, String> buildAttributes(IssuanceCommand command, Map<String, String> metadata) {
        Map<String, String> attributes = new LinkedHashMap<>();
        metadata.forEach((key, value) -> attributes.put(ATTR_METADATA_PREFIX + key, value));
        attributes.put(ATTR_ALGORITHM, command.algorithm().name());
        attributes.put(ATTR_DIGITS, Integer.toString(command.digits()));
        attributes.put(ATTR_COUNTER, Long.toString(command.initialCounter()));
        return attributes;
    }

    private static Map<String, String> normalizeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Metadata key must not be blank");
            }
            String value = entry.getValue();
            normalized.put(key.trim(), value == null ? "" : value.trim());
        }
        return normalized;
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        return safeMessage(throwable.getMessage());
    }

    private static String safeMessage(String message) {
        if (message == null) {
            return "";
        }
        return message.trim().replaceAll("\\s+", " ");
    }
}
