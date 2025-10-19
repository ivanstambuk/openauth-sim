package io.openauth.sim.application.hotp;

import io.openauth.sim.application.telemetry.HotpTelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.hotp.HotpDescriptor;
import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import io.openauth.sim.core.otp.hotp.HotpValidator;
import io.openauth.sim.core.otp.hotp.HotpVerificationResult;
import io.openauth.sim.core.store.CredentialStore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Application-layer HOTP replay orchestrator (stored + inline flows, non-mutating). */
public final class HotpReplayApplicationService {

    private static final String ATTR_ALGORITHM = "hotp.algorithm";
    private static final String ATTR_DIGITS = "hotp.digits";
    private static final String ATTR_COUNTER = "hotp.counter";
    private static final String INLINE_DESCRIPTOR_NAME = "hotp-inline-replay";

    private final CredentialStore credentialStore;

    public HotpReplayApplicationService(CredentialStore credentialStore) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
    }

    public ReplayResult replay(ReplayCommand command) {
        Objects.requireNonNull(command, "command");

        if (command instanceof ReplayCommand.Stored stored) {
            return replayStored(stored);
        }
        if (command instanceof ReplayCommand.Inline inline) {
            return replayInline(inline);
        }
        throw new IllegalStateException("Unsupported HOTP replay command: " + command);
    }

    public sealed interface ReplayCommand permits ReplayCommand.Stored, ReplayCommand.Inline {
        String otp();

        record Stored(String credentialId, String otp) implements ReplayCommand {

            public Stored {
                credentialId =
                        Objects.requireNonNull(credentialId, "credentialId").trim();
                otp = Objects.requireNonNull(otp, "otp").trim();
            }
        }

        record Inline(
                String sharedSecretHex,
                HotpHashAlgorithm algorithm,
                int digits,
                long counter,
                String otp,
                Map<String, String> metadata)
                implements ReplayCommand {

            public Inline {
                sharedSecretHex = Objects.requireNonNull(sharedSecretHex, "sharedSecretHex")
                        .trim();
                algorithm = Objects.requireNonNull(algorithm, "algorithm");
                metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
                otp = Objects.requireNonNull(otp, "otp").trim();
            }
        }
    }

    public record ReplayResult(
            TelemetrySignal telemetry,
            boolean credentialReference,
            String credentialId,
            long previousCounter,
            long nextCounter,
            HotpHashAlgorithm algorithm,
            Integer digits) {

        public ReplayFrame replayFrame(HotpTelemetryAdapter adapter, String telemetryId) {
            return new ReplayFrame(telemetry.emit(adapter, telemetryId));
        }
    }

    public record ReplayFrame(TelemetryFrame frame) {
        // Marker wrapper for API symmetry.
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

    private ReplayResult replayStored(ReplayCommand.Stored command) {
        StoredCredential storedCredential = null;
        try {
            storedCredential = resolveStored(command.credentialId());
            if (storedCredential == null) {
                return notFoundResult(command.credentialId());
            }

            return replayDescriptor(
                    storedCredential.descriptor(),
                    command.otp(),
                    storedCredential.counter(),
                    true,
                    storedCredential.descriptor().name(),
                    "stored");
        } catch (IllegalArgumentException ex) {
            return invalidMetadataResult(command.credentialId(), ex.getMessage());
        } catch (RuntimeException ex) {
            long counter = storedCredential != null ? storedCredential.counter() : 0L;
            HotpHashAlgorithm algorithm =
                    storedCredential != null ? storedCredential.descriptor().algorithm() : null;
            Integer digits =
                    storedCredential != null ? storedCredential.descriptor().digits() : null;
            return unexpectedErrorResult(command.credentialId(), true, "stored", algorithm, digits, counter, ex);
        }
    }

    private ReplayResult replayInline(ReplayCommand.Inline command) {
        try {
            HotpDescriptor descriptor = HotpDescriptor.create(
                    INLINE_DESCRIPTOR_NAME,
                    SecretMaterial.fromHex(command.sharedSecretHex()),
                    command.algorithm(),
                    command.digits());

            return replayDescriptor(
                    descriptor, command.otp(), command.counter(), false, INLINE_DESCRIPTOR_NAME, "inline");
        } catch (IllegalArgumentException ex) {
            return validationFailure(
                    INLINE_DESCRIPTOR_NAME,
                    false,
                    "inline",
                    command.algorithm(),
                    command.digits(),
                    command.counter(),
                    "validation_error",
                    ex.getMessage());
        } catch (RuntimeException ex) {
            return unexpectedErrorResult(
                    INLINE_DESCRIPTOR_NAME,
                    false,
                    "inline",
                    command.algorithm(),
                    command.digits(),
                    command.counter(),
                    ex);
        }
    }

    private ReplayResult replayDescriptor(
            HotpDescriptor descriptor,
            String otp,
            long counter,
            boolean credentialReference,
            String credentialId,
            String credentialSource) {

        try {
            HotpVerificationResult verification = HotpValidator.verify(descriptor, counter, otp);
            if (verification.valid()) {
                return successResult(
                        credentialReference,
                        credentialId,
                        credentialSource,
                        descriptor.algorithm(),
                        descriptor.digits(),
                        counter);
            }

            return mismatchResult(
                    credentialReference,
                    credentialId,
                    credentialSource,
                    descriptor.algorithm(),
                    descriptor.digits(),
                    counter);
        } catch (IllegalArgumentException ex) {
            return validationFailure(
                    credentialId,
                    credentialReference,
                    credentialSource,
                    descriptor.algorithm(),
                    descriptor.digits(),
                    counter,
                    "validation_error",
                    ex.getMessage());
        } catch (RuntimeException ex) {
            return unexpectedErrorResult(
                    credentialId,
                    credentialReference,
                    credentialSource,
                    descriptor.algorithm(),
                    descriptor.digits(),
                    counter,
                    ex);
        }
    }

    private ReplayResult successResult(
            boolean credentialReference,
            String credentialId,
            String credentialSource,
            HotpHashAlgorithm algorithm,
            int digits,
            long counter) {

        Map<String, Object> fields = replayFields(credentialSource, credentialId, algorithm, digits, counter, counter);
        TelemetrySignal signal = new TelemetrySignal(TelemetryStatus.SUCCESS, "match", null, true, fields, null);
        return new ReplayResult(signal, credentialReference, credentialId, counter, counter, algorithm, digits);
    }

    private ReplayResult mismatchResult(
            boolean credentialReference,
            String credentialId,
            String credentialSource,
            HotpHashAlgorithm algorithm,
            int digits,
            long counter) {

        Map<String, Object> fields = replayFields(credentialSource, credentialId, algorithm, digits, counter, counter);
        TelemetrySignal signal =
                new TelemetrySignal(TelemetryStatus.INVALID, "otp_mismatch", "OTP mismatch", true, fields, null);
        return new ReplayResult(signal, credentialReference, credentialId, counter, counter, algorithm, digits);
    }

    private ReplayResult validationFailure(
            String credentialId,
            boolean credentialReference,
            String credentialSource,
            HotpHashAlgorithm algorithm,
            Integer digits,
            long counter,
            String reasonCode,
            String reason) {

        Map<String, Object> fields = replayFields(credentialSource, credentialId, algorithm, digits, counter, counter);
        TelemetrySignal signal =
                new TelemetrySignal(TelemetryStatus.INVALID, reasonCode, safeMessage(reason), true, fields, null);
        return new ReplayResult(signal, credentialReference, credentialId, counter, counter, algorithm, digits);
    }

    private ReplayResult notFoundResult(String credentialId) {
        Map<String, Object> fields = replayFields("stored", credentialId, null, null, 0L, 0L);
        TelemetrySignal signal = new TelemetrySignal(
                TelemetryStatus.INVALID,
                "credential_not_found",
                "credentialId " + credentialId + " not found",
                true,
                fields,
                null);
        return new ReplayResult(signal, true, credentialId, 0L, 0L, null, null);
    }

    private ReplayResult invalidMetadataResult(String credentialId, String reason) {
        Map<String, Object> fields = replayFields("stored", credentialId, null, null, 0L, 0L);
        TelemetrySignal signal = new TelemetrySignal(
                TelemetryStatus.INVALID, "invalid_hotp_metadata", safeMessage(reason), true, fields, null);
        return new ReplayResult(signal, true, credentialId, 0L, 0L, null, null);
    }

    private ReplayResult unexpectedErrorResult(
            String credentialId,
            boolean credentialReference,
            String credentialSource,
            HotpHashAlgorithm algorithm,
            Integer digits,
            long counter,
            Throwable error) {

        Map<String, Object> fields = replayFields(credentialSource, credentialId, algorithm, digits, counter, counter);
        if (error != null) {
            fields.put("exception", error.getClass().getName() + ": " + safeMessage(error));
        }
        TelemetrySignal signal =
                new TelemetrySignal(TelemetryStatus.ERROR, "unexpected_error", safeMessage(error), false, fields, null);
        return new ReplayResult(signal, credentialReference, credentialId, counter, counter, algorithm, digits);
    }

    private StoredCredential resolveStored(String credentialId) {
        Optional<Credential> credentialOptional = credentialStore.findByName(credentialId);
        if (credentialOptional.isEmpty()) {
            return null;
        }

        Credential credential = credentialOptional.get();
        if (credential.type() != CredentialType.OATH_HOTP) {
            return null;
        }

        Map<String, String> attributes = credential.attributes();
        String algorithmName = attributes.get(ATTR_ALGORITHM);
        String digitsValue = attributes.get(ATTR_DIGITS);
        String counterValue = attributes.get(ATTR_COUNTER);

        if (!hasText(algorithmName) || !hasText(digitsValue) || !hasText(counterValue)) {
            throw new IllegalArgumentException("Missing HOTP metadata for credential " + credentialId);
        }

        try {
            HotpHashAlgorithm algorithm = HotpHashAlgorithm.valueOf(algorithmName);
            int digits = Integer.parseInt(digitsValue);
            long counter = Long.parseLong(counterValue);

            HotpDescriptor descriptor =
                    HotpDescriptor.create(credential.name(), credential.secret(), algorithm, digits);
            return new StoredCredential(credential, descriptor, counter);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Invalid HOTP metadata for credential " + credentialId + ": " + safeMessage(ex), ex);
        }
    }

    private static Map<String, Object> replayFields(
            String credentialSource,
            String credentialId,
            HotpHashAlgorithm algorithm,
            Integer digits,
            long previousCounter,
            long nextCounter) {

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("credentialSource", credentialSource);
        fields.put("credentialId", credentialId);
        if (algorithm != null) {
            fields.put("hashAlgorithm", algorithm.name());
        }
        if (digits != null) {
            fields.put("digits", digits);
        }
        fields.put("previousCounter", previousCounter);
        fields.put("nextCounter", nextCounter);
        return fields;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
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

    private record StoredCredential(Credential credential, HotpDescriptor descriptor, long counter) {
        // Tuple carrying resolved state.
    }
}
