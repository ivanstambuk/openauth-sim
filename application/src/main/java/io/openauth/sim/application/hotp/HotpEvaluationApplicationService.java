package io.openauth.sim.application.hotp;

import io.openauth.sim.application.telemetry.HotpTelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.hotp.HotpDescriptor;
import io.openauth.sim.core.otp.hotp.HotpGenerator;
import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.trace.VerboseTrace;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Application-level HOTP evaluation orchestrator (tests drive implementation). */
public final class HotpEvaluationApplicationService {

    private static final String ATTR_ALGORITHM = "hotp.algorithm";
    private static final String ATTR_DIGITS = "hotp.digits";
    private static final String ATTR_COUNTER = "hotp.counter";

    private static final String INLINE_DESCRIPTOR_NAME = "hotp-inline-request";

    private final CredentialStore credentialStore;

    public HotpEvaluationApplicationService(CredentialStore credentialStore) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
    }

    public EvaluationResult evaluate(EvaluationCommand command) {
        return evaluate(command, false);
    }

    public EvaluationResult evaluate(EvaluationCommand command, boolean verbose) {
        Objects.requireNonNull(command, "command");

        if (command instanceof EvaluationCommand.Stored stored) {
            return evaluateStored(stored, verbose);
        }
        if (command instanceof EvaluationCommand.Inline inline) {
            return evaluateInline(inline, verbose);
        }
        throw new IllegalStateException("Unsupported HOTP evaluation command: " + command);
    }

    public sealed interface EvaluationCommand permits EvaluationCommand.Stored, EvaluationCommand.Inline {

        record Stored(String credentialId) implements EvaluationCommand {

            public Stored {
                credentialId =
                        Objects.requireNonNull(credentialId, "credentialId").trim();
            }
        }

        record Inline(
                String sharedSecretHex,
                HotpHashAlgorithm algorithm,
                int digits,
                Long counter,
                Map<String, String> metadata)
                implements EvaluationCommand {

            public Inline {
                sharedSecretHex = Objects.requireNonNull(sharedSecretHex, "sharedSecretHex")
                        .trim();
                algorithm = Objects.requireNonNull(algorithm, "algorithm");
                metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
            }
        }
    }

    public record EvaluationResult(
            TelemetrySignal telemetry,
            boolean credentialReference,
            String credentialId,
            long previousCounter,
            long nextCounter,
            HotpHashAlgorithm algorithm,
            Integer digits,
            String otp,
            String samplePresetKey,
            String samplePresetLabel,
            VerboseTrace trace) {

        public EvaluationFrame evaluationFrame(HotpTelemetryAdapter adapter, String telemetryId) {
            return new EvaluationFrame(telemetry.emit(adapter, telemetryId));
        }

        public Optional<VerboseTrace> verboseTrace() {
            return Optional.ofNullable(trace);
        }
    }

    public record EvaluationFrame(TelemetryFrame frame) {
        // Marker type for fluent telemetry access.
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

    private EvaluationResult evaluateStored(EvaluationCommand.Stored command, boolean verbose) {
        VerboseTrace.Builder trace = newTrace(verbose, "hotp.evaluate.stored");
        metadata(trace, "protocol", "HOTP");
        metadata(trace, "mode", "stored");
        metadata(trace, "credentialId", command.credentialId());

        try {
            StoredCredential storedCredential = resolveStored(command.credentialId());
            if (storedCredential == null) {
                addStep(trace, step -> step.id("resolve.credential")
                        .summary("Resolve stored HOTP credential")
                        .detail("CredentialStore.findByName")
                        .attribute("credentialId", command.credentialId())
                        .attribute("found", false));
                return notFoundResult(command.credentialId(), buildTrace(trace));
            }

            addStep(trace, step -> step.id("resolve.credential")
                    .summary("Resolve stored HOTP credential")
                    .detail("CredentialStore.findByName")
                    .attribute("credentialId", command.credentialId())
                    .attribute("found", true)
                    .attribute(
                            "algorithm",
                            storedCredential.descriptor().algorithm().name())
                    .attribute("digits", storedCredential.descriptor().digits())
                    .attribute("counter.before", storedCredential.counter()));

            long previousCounter = storedCredential.counter();
            String otp = HotpGenerator.generate(storedCredential.descriptor(), previousCounter);
            addStep(trace, step -> step.id("generate.otp")
                    .summary("Generate HOTP code")
                    .detail("HotpGenerator.generate")
                    .attribute(
                            "algorithm",
                            storedCredential.descriptor().algorithm().name())
                    .attribute("digits", storedCredential.descriptor().digits())
                    .attribute("counter.input", previousCounter)
                    .attribute("otp", otp));

            long nextCounter;
            try {
                nextCounter = Math.addExact(previousCounter, 1L);
            } catch (ArithmeticException ex) {
                addStep(trace, step -> step.id("counter.increment")
                        .summary("Increment counter")
                        .detail("Math.addExact")
                        .attribute("counter.before", previousCounter)
                        .note("failure", safeMessage(ex)));
                return validationFailure(
                        command.credentialId(),
                        true,
                        "stored",
                        storedCredential.descriptor().algorithm(),
                        storedCredential.descriptor().digits(),
                        previousCounter,
                        previousCounter,
                        "counter_overflow",
                        ex.getMessage(),
                        null,
                        null,
                        buildTrace(trace));
            }

            addStep(trace, step -> step.id("persist.counter")
                    .summary("Persist incremented counter")
                    .detail("CredentialStore.save")
                    .attribute("counter.next", nextCounter));
            persistCounter(storedCredential.credential(), nextCounter);
            return successResult(
                    true,
                    storedCredential.descriptor().name(),
                    "stored",
                    storedCredential.descriptor().algorithm(),
                    storedCredential.descriptor().digits(),
                    previousCounter,
                    nextCounter,
                    otp,
                    null,
                    null,
                    buildTrace(trace));
        } catch (IllegalArgumentException ex) {
            addStep(trace, step -> step.id("resolve.credential")
                    .summary("Resolve stored HOTP credential")
                    .detail("CredentialStore.findByName")
                    .attribute("credentialId", command.credentialId())
                    .note("failure", safeMessage(ex)));
            return invalidMetadataResult(command.credentialId(), ex.getMessage(), buildTrace(trace));
        } catch (RuntimeException ex) {
            addStep(trace, step -> step.id("error")
                    .summary("Unexpected error during HOTP evaluation")
                    .detail(ex.getClass().getName())
                    .note("message", safeMessage(ex)));
            return unexpectedErrorResult(
                    command.credentialId(), true, "stored", null, null, 0L, null, null, ex, buildTrace(trace));
        }
    }

    private EvaluationResult evaluateInline(EvaluationCommand.Inline command, boolean verbose) {
        Map<String, String> metadata = command.metadata();
        String presetKey = normalize(metadata.get("presetKey"));
        String presetLabel = normalize(metadata.get("presetLabel"));
        VerboseTrace.Builder trace = newTrace(verbose, "hotp.evaluate.inline");
        metadata(trace, "protocol", "HOTP");
        metadata(trace, "mode", "inline");
        if (hasText(presetKey)) {
            metadata(trace, "presetKey", presetKey);
        }
        try {
            Long counterValue = command.counter();
            if (counterValue == null) {
                return validationFailure(
                        null,
                        false,
                        "inline",
                        command.algorithm(),
                        command.digits(),
                        0L,
                        0L,
                        "counter_required",
                        "counter is required",
                        presetKey,
                        presetLabel,
                        buildTrace(trace));
            }
            if (!hasText(command.sharedSecretHex())) {
                return validationFailure(
                        null,
                        false,
                        "inline",
                        command.algorithm(),
                        command.digits(),
                        0L,
                        0L,
                        "sharedSecretHex_required",
                        "sharedSecretHex is required",
                        presetKey,
                        presetLabel,
                        buildTrace(trace));
            }
            HotpDescriptor descriptor = HotpDescriptor.create(
                    INLINE_DESCRIPTOR_NAME,
                    SecretMaterial.fromHex(command.sharedSecretHex()),
                    command.algorithm(),
                    command.digits());

            addStep(trace, step -> step.id("normalize.input")
                    .summary("Prepare inline HOTP request")
                    .detail("HotpDescriptor.create")
                    .attribute("algorithm", descriptor.algorithm().name())
                    .attribute("digits", descriptor.digits())
                    .attribute("counter.input", counterValue));

            long previousCounter = counterValue;
            String otp = HotpGenerator.generate(descriptor, previousCounter);
            addStep(trace, step -> step.id("generate.otp")
                    .summary("Generate HOTP code")
                    .detail("HotpGenerator.generate")
                    .attribute("algorithm", descriptor.algorithm().name())
                    .attribute("digits", descriptor.digits())
                    .attribute("counter.input", previousCounter)
                    .attribute("otp", otp));

            long nextCounter;
            try {
                nextCounter = Math.addExact(previousCounter, 1L);
            } catch (ArithmeticException ex) {
                addStep(trace, step -> step.id("counter.increment")
                        .summary("Increment counter")
                        .detail("Math.addExact")
                        .attribute("counter.before", previousCounter)
                        .note("failure", safeMessage(ex)));
                return validationFailure(
                        null,
                        false,
                        "inline",
                        descriptor.algorithm(),
                        descriptor.digits(),
                        previousCounter,
                        previousCounter,
                        "counter_overflow",
                        ex.getMessage(),
                        presetKey,
                        presetLabel,
                        buildTrace(trace));
            }

            return successResult(
                    false,
                    null,
                    "inline",
                    descriptor.algorithm(),
                    descriptor.digits(),
                    previousCounter,
                    nextCounter,
                    otp,
                    presetKey,
                    presetLabel,
                    buildTrace(trace));
        } catch (IllegalArgumentException ex) {
            addStep(trace, step -> step.id("normalize.input")
                    .summary("Prepare inline HOTP request")
                    .detail("HotpDescriptor.create")
                    .note("failure", safeMessage(ex)));
            return validationFailure(
                    null,
                    false,
                    "inline",
                    command.algorithm(),
                    command.digits(),
                    command.counter() != null ? command.counter() : 0L,
                    command.counter() != null ? command.counter() : 0L,
                    "validation_error",
                    ex.getMessage(),
                    presetKey,
                    presetLabel,
                    buildTrace(trace));
        } catch (RuntimeException ex) {
            addStep(trace, step -> step.id("error")
                    .summary("Unexpected error during HOTP inline evaluation")
                    .detail(ex.getClass().getName())
                    .note("message", safeMessage(ex)));
            return unexpectedErrorResult(
                    null,
                    false,
                    "inline",
                    command.algorithm(),
                    command.digits(),
                    command.counter(),
                    presetKey,
                    presetLabel,
                    ex,
                    buildTrace(trace));
        }
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

    private EvaluationResult successResult(
            boolean credentialReference,
            String credentialId,
            String credentialSource,
            HotpHashAlgorithm algorithm,
            int digits,
            long previousCounter,
            long nextCounter,
            String otp,
            String samplePresetKey,
            String samplePresetLabel,
            VerboseTrace trace) {

        Map<String, Object> fields = evaluationFields(
                credentialSource,
                credentialId,
                algorithm,
                digits,
                previousCounter,
                nextCounter,
                samplePresetKey,
                samplePresetLabel);
        TelemetrySignal signal = new TelemetrySignal(TelemetryStatus.SUCCESS, "generated", null, true, fields, null);
        return new EvaluationResult(
                signal,
                credentialReference,
                credentialId,
                previousCounter,
                nextCounter,
                algorithm,
                digits,
                otp,
                samplePresetKey,
                samplePresetLabel,
                trace);
    }

    private EvaluationResult validationFailure(
            String credentialId,
            boolean credentialReference,
            String credentialSource,
            HotpHashAlgorithm algorithm,
            Integer digits,
            long previousCounter,
            long nextCounter,
            String reasonCode,
            String reason,
            String samplePresetKey,
            String samplePresetLabel,
            VerboseTrace trace) {

        Map<String, Object> fields = evaluationFields(
                credentialSource,
                credentialId,
                algorithm,
                digits,
                previousCounter,
                nextCounter,
                samplePresetKey,
                samplePresetLabel);
        TelemetrySignal signal =
                new TelemetrySignal(TelemetryStatus.INVALID, reasonCode, safeMessage(reason), true, fields, null);
        return new EvaluationResult(
                signal,
                credentialReference,
                credentialId,
                previousCounter,
                nextCounter,
                algorithm,
                digits,
                null,
                samplePresetKey,
                samplePresetLabel,
                trace);
    }

    private EvaluationResult notFoundResult(String credentialId, VerboseTrace trace) {
        Map<String, Object> fields = evaluationFields("stored", credentialId, null, null, 0L, 0L, null, null);
        TelemetrySignal signal = new TelemetrySignal(
                TelemetryStatus.INVALID,
                "credential_not_found",
                "credentialId " + credentialId + " not found",
                true,
                fields,
                null);
        return new EvaluationResult(signal, true, credentialId, 0L, 0L, null, null, null, null, null, trace);
    }

    private EvaluationResult invalidMetadataResult(String credentialId, String reason, VerboseTrace trace) {
        Map<String, Object> fields = evaluationFields("stored", credentialId, null, null, 0L, 0L, null, null);
        TelemetrySignal signal = new TelemetrySignal(
                TelemetryStatus.INVALID, "invalid_hotp_metadata", safeMessage(reason), true, fields, null);
        return new EvaluationResult(signal, true, credentialId, 0L, 0L, null, null, null, null, null, trace);
    }

    private EvaluationResult unexpectedErrorResult(
            String credentialId,
            boolean credentialReference,
            String credentialSource,
            HotpHashAlgorithm algorithm,
            Integer digits,
            long previousCounter,
            String samplePresetKey,
            String samplePresetLabel,
            Throwable error,
            VerboseTrace trace) {

        Map<String, Object> fields = evaluationFields(
                credentialSource,
                credentialId,
                algorithm,
                digits,
                previousCounter,
                previousCounter,
                samplePresetKey,
                samplePresetLabel);
        if (error != null) {
            fields.put("exception", error.getClass().getName() + ": " + safeMessage(error));
        }
        TelemetrySignal signal =
                new TelemetrySignal(TelemetryStatus.ERROR, "unexpected_error", safeMessage(error), false, fields, null);
        return new EvaluationResult(
                signal,
                credentialReference,
                credentialId,
                previousCounter,
                previousCounter,
                algorithm,
                digits,
                null,
                samplePresetKey,
                samplePresetLabel,
                trace);
    }

    private void persistCounter(Credential credential, long nextCounter) {
        Map<String, String> updated = new LinkedHashMap<>(credential.attributes());
        updated.put(ATTR_COUNTER, Long.toString(nextCounter));
        credentialStore.save(credential.withAttributes(updated));
    }

    private static Map<String, Object> evaluationFields(
            String credentialSource,
            String credentialId,
            HotpHashAlgorithm algorithm,
            Integer digits,
            long previousCounter,
            long nextCounter,
            String samplePresetKey,
            String samplePresetLabel) {

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
        if (hasText(samplePresetKey)) {
            fields.put("inlinePresetKey", samplePresetKey);
        }
        if (hasText(samplePresetLabel)) {
            fields.put("inlinePresetLabel", samplePresetLabel);
        }
        return fields;
    }

    private static VerboseTrace.Builder newTrace(boolean verbose, String operation) {
        return verbose ? VerboseTrace.builder(operation) : null;
    }

    private static void metadata(VerboseTrace.Builder trace, String key, String value) {
        if (trace != null && value != null) {
            trace.withMetadata(key, value);
        }
    }

    private static void addStep(
            VerboseTrace.Builder trace, java.util.function.Consumer<VerboseTrace.TraceStep.Builder> configurer) {
        if (trace != null) {
            trace.addStep(configurer);
        }
    }

    private static VerboseTrace buildTrace(VerboseTrace.Builder trace) {
        return trace == null ? null : trace.build();
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
        // Simple tuple carrying resolved state.
    }
}
