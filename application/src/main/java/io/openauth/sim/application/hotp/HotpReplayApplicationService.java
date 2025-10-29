package io.openauth.sim.application.hotp;

import io.openauth.sim.application.hotp.HotpTraceCalculator.HotpTraceComputation;
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
import io.openauth.sim.core.trace.VerboseTrace;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Application-layer HOTP replay orchestrator (stored + inline flows, non-mutating). */
public final class HotpReplayApplicationService {

    private static final String SPEC_HOTP_INPUT = "rfc4226§5.1";
    private static final String SPEC_HOTP_MOD = "rfc4226§5.4";
    private static final int DEFAULT_LOOK_AHEAD = 10;
    private static final String SECRET_FORMAT_HEX = "hex";

    private static final String ATTR_ALGORITHM = "hotp.algorithm";
    private static final String ATTR_DIGITS = "hotp.digits";
    private static final String ATTR_COUNTER = "hotp.counter";
    private static final String INLINE_DESCRIPTOR_NAME = "hotp-inline-replay";

    private final CredentialStore credentialStore;

    public HotpReplayApplicationService(CredentialStore credentialStore) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
    }

    public ReplayResult replay(ReplayCommand command) {
        return replay(command, false);
    }

    public ReplayResult replay(ReplayCommand command, boolean verbose) {
        Objects.requireNonNull(command, "command");

        if (command instanceof ReplayCommand.Stored stored) {
            return replayStored(stored, verbose);
        }
        if (command instanceof ReplayCommand.Inline inline) {
            return replayInline(inline, verbose);
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

    private static long safeWindowUpperBound(long counter, int lookAhead) {
        if (lookAhead < 0) {
            return counter;
        }
        long maxAllowed = Long.MAX_VALUE - lookAhead;
        if (counter > maxAllowed) {
            return Long.MAX_VALUE;
        }
        return counter + lookAhead;
    }

    private static long safeWindowLowerBound(long counter, int lookAhead) {
        if (lookAhead < 0) {
            return counter;
        }
        long minAllowed = Long.MIN_VALUE + lookAhead;
        if (counter < minAllowed) {
            return Long.MIN_VALUE;
        }
        return counter - lookAhead;
    }

    private record AttemptTrace(long counter, HotpTraceComputation computation, boolean match) {
        // Captures a single verification attempt and whether it matched the provided OTP.
    }

    public record ReplayResult(
            TelemetrySignal telemetry,
            boolean credentialReference,
            String credentialId,
            long previousCounter,
            long nextCounter,
            HotpHashAlgorithm algorithm,
            Integer digits,
            VerboseTrace trace) {

        public ReplayFrame replayFrame(HotpTelemetryAdapter adapter, String telemetryId) {
            return new ReplayFrame(telemetry.emit(adapter, telemetryId));
        }

        public Optional<VerboseTrace> verboseTrace() {
            return Optional.ofNullable(trace);
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

    private ReplayResult replayStored(ReplayCommand.Stored command, boolean verbose) {
        VerboseTrace.Builder trace = newTrace(verbose, "hotp.replay.stored");
        metadata(trace, "protocol", "HOTP");
        metadata(trace, "mode", "stored");
        metadata(trace, "credentialId", command.credentialId());

        StoredCredential storedCredential = null;
        try {
            storedCredential = resolveStored(command.credentialId());
            if (storedCredential == null) {
                addStep(trace, step -> step.id("normalize.input")
                        .summary("Normalize stored HOTP replay request")
                        .detail("CredentialStore.findByName")
                        .spec(SPEC_HOTP_INPUT)
                        .attribute(VerboseTrace.AttributeType.STRING, "op", "replay.stored")
                        .attribute(VerboseTrace.AttributeType.STRING, "credentialId", command.credentialId())
                        .attribute(VerboseTrace.AttributeType.BOOL, "found", false));
                return notFoundResult(command.credentialId(), buildTrace(trace));
            }
            StoredCredential resolved = storedCredential;
            HotpDescriptor descriptor = resolved.descriptor();
            long counter = resolved.counter();

            return replayDescriptor(descriptor, command.otp(), counter, true, descriptor.name(), "stored", trace);
        } catch (IllegalArgumentException ex) {
            addStep(trace, step -> step.id("normalize.input")
                    .summary("Normalize stored HOTP replay request")
                    .detail("CredentialStore.findByName")
                    .spec(SPEC_HOTP_INPUT)
                    .attribute(VerboseTrace.AttributeType.STRING, "op", "replay.stored")
                    .attribute(VerboseTrace.AttributeType.STRING, "credentialId", command.credentialId())
                    .note("failure", safeMessage(ex)));
            return invalidMetadataResult(command.credentialId(), ex.getMessage(), buildTrace(trace));
        } catch (RuntimeException ex) {
            addStep(trace, step -> step.id("error")
                    .summary("Unexpected error during stored replay")
                    .detail(ex.getClass().getSimpleName())
                    .note("message", safeMessage(ex)));
            long counter = storedCredential != null ? storedCredential.counter() : 0L;
            HotpHashAlgorithm algorithm =
                    storedCredential != null ? storedCredential.descriptor().algorithm() : null;
            Integer digits =
                    storedCredential != null ? storedCredential.descriptor().digits() : null;
            return unexpectedErrorResult(
                    command.credentialId(), true, "stored", algorithm, digits, counter, ex, buildTrace(trace));
        }
    }

    private ReplayResult replayInline(ReplayCommand.Inline command, boolean verbose) {
        VerboseTrace.Builder trace = newTrace(verbose, "hotp.replay.inline");
        metadata(trace, "protocol", "HOTP");
        metadata(trace, "mode", "inline");
        Map<String, String> metadata = command.metadata();
        if (metadata != null) {
            metadata(trace, "size", Integer.toString(metadata.size()));
        }
        try {
            HotpDescriptor descriptor = HotpDescriptor.create(
                    INLINE_DESCRIPTOR_NAME,
                    SecretMaterial.fromHex(command.sharedSecretHex()),
                    command.algorithm(),
                    command.digits());

            return replayDescriptor(
                    descriptor, command.otp(), command.counter(), false, INLINE_DESCRIPTOR_NAME, "inline", trace);
        } catch (IllegalArgumentException ex) {
            addStep(trace, step -> step.id("normalize.input")
                    .summary("Normalize inline HOTP replay request")
                    .detail("HotpDescriptor.create")
                    .spec(SPEC_HOTP_INPUT)
                    .attribute(VerboseTrace.AttributeType.STRING, "op", "replay.inline")
                    .note("failure", safeMessage(ex)));
            return validationFailure(
                    INLINE_DESCRIPTOR_NAME,
                    false,
                    "inline",
                    command.algorithm(),
                    command.digits(),
                    command.counter(),
                    "validation_error",
                    ex.getMessage(),
                    buildTrace(trace));
        } catch (RuntimeException ex) {
            addStep(trace, step -> step.id("error")
                    .summary("Unexpected error during inline replay")
                    .detail(ex.getClass().getSimpleName())
                    .note("message", safeMessage(ex)));
            return unexpectedErrorResult(
                    INLINE_DESCRIPTOR_NAME,
                    false,
                    "inline",
                    command.algorithm(),
                    command.digits(),
                    command.counter(),
                    ex,
                    buildTrace(trace));
        }
    }

    private ReplayResult replayDescriptor(
            HotpDescriptor descriptor,
            String otp,
            long counter,
            boolean credentialReference,
            String credentialId,
            String credentialSource,
            VerboseTrace.Builder trace) {

        try {
            final long maxCounter = safeWindowUpperBound(counter, DEFAULT_LOOK_AHEAD);
            final long minCounter = safeWindowLowerBound(counter, DEFAULT_LOOK_AHEAD);
            byte[] secretBytes = descriptor.secret().value();
            HotpVerificationResult verificationResult = HotpVerificationResult.failure(counter);
            HotpTraceComputation matchComputation = null;
            Long matchedCounter = null;
            java.util.List<AttemptTrace> attempts = new java.util.ArrayList<>();

            for (long candidate = counter; candidate <= maxCounter; candidate++) {
                HotpTraceComputation computation = HotpTraceCalculator.compute(descriptor, secretBytes, candidate);
                HotpVerificationResult candidateResult = HotpValidator.verify(descriptor, candidate, otp);
                boolean match = candidateResult.valid();
                attempts.add(new AttemptTrace(candidate, computation, match));
                if (match) {
                    verificationResult = candidateResult;
                    matchComputation = computation;
                    matchedCounter = candidate;
                    break;
                }
            }

            HotpTraceComputation baseline = attempts.isEmpty()
                    ? HotpTraceCalculator.compute(descriptor, secretBytes, counter)
                    : attempts.get(0).computation();

            final String operationDetail = credentialReference ? "CredentialStore.findByName" : "HotpDescriptor.create";
            final String operationCode = credentialReference ? "replay.stored" : "replay.inline";
            String providedOtp = otp == null ? "" : otp.trim();

            addStep(trace, step -> {
                step.id("normalize.input")
                        .summary("Normalize HOTP replay request")
                        .detail(operationDetail)
                        .spec(SPEC_HOTP_INPUT)
                        .attribute(VerboseTrace.AttributeType.STRING, "op", operationCode)
                        .attribute(
                                VerboseTrace.AttributeType.STRING,
                                "alg",
                                descriptor.algorithm().traceLabel())
                        .attribute(VerboseTrace.AttributeType.INT, "digits", descriptor.digits())
                        .attribute(VerboseTrace.AttributeType.STRING, "otp.provided", providedOtp)
                        .attribute(VerboseTrace.AttributeType.INT, "counter.hint", counter)
                        .attribute(VerboseTrace.AttributeType.INT, "window", DEFAULT_LOOK_AHEAD)
                        .attribute(VerboseTrace.AttributeType.INT, "secret.len.bytes", baseline.secretLength())
                        .attribute(VerboseTrace.AttributeType.STRING, "secret.sha256", baseline.secretHash());
                if (!credentialReference) {
                    step.attribute(VerboseTrace.AttributeType.STRING, "secret.format", SECRET_FORMAT_HEX);
                }
                if (descriptor.algorithm() != HotpHashAlgorithm.SHA1) {
                    step.note("non_standard_hash", Boolean.toString(true));
                }
            });

            HotpTraceComputation matchDetails = matchComputation;
            final Long matchedWindowCounter = matchedCounter;
            addStep(trace, step -> {
                step.id("search.window")
                        .summary("Search HOTP verification window")
                        .detail("HotpValidator.verify")
                        .spec(SPEC_HOTP_MOD);
                step.attribute(
                        VerboseTrace.AttributeType.STRING, "window.range", formatWindowRange(minCounter, maxCounter));
                step.attribute(VerboseTrace.AttributeType.STRING, "order", "ascending");
                attempts.forEach(attempt -> {
                    String attemptValue =
                            attempt.computation().otpString() + (attempt.match() ? " (match=true)" : " (match=false)");
                    step.attribute(
                            VerboseTrace.AttributeType.STRING, "attempt." + attempt.counter() + ".otp", attemptValue);
                });
                if (matchDetails != null && matchedWindowCounter != null) {
                    step.attribute(
                            VerboseTrace.AttributeType.STRING,
                            "match.hmac.compute.detail",
                            descriptor.algorithm().traceLabel());
                    step.attribute(
                            VerboseTrace.AttributeType.STRING, "match.marker.begin", "-- begin match.derivation --");
                    step.attribute(
                            VerboseTrace.AttributeType.INT, "match.prepare.counter.counter.int", matchedWindowCounter);
                    step.attribute(
                            VerboseTrace.AttributeType.HEX,
                            "match.prepare.counter.counter.bytes.big_endian",
                            matchDetails.counterHex());
                    step.attribute(
                            VerboseTrace.AttributeType.INT,
                            "match.hmac.compute.hash.block_len",
                            matchDetails.hashBlockLength());
                    step.attribute(
                            VerboseTrace.AttributeType.STRING, "match.hmac.compute.key.mode", matchDetails.keyMode());
                    step.attribute(
                            VerboseTrace.AttributeType.STRING,
                            "match.hmac.compute.key'.sha256",
                            matchDetails.keyPrimeSha256());
                    step.attribute(
                            VerboseTrace.AttributeType.STRING,
                            "match.hmac.compute.ipad.byte",
                            HotpTraceCalculator.formatByte(HotpTraceCalculator.ipadByte()));
                    step.attribute(
                            VerboseTrace.AttributeType.STRING,
                            "match.hmac.compute.opad.byte",
                            HotpTraceCalculator.formatByte(HotpTraceCalculator.opadByte()));
                    step.attribute(
                            VerboseTrace.AttributeType.HEX,
                            "match.hmac.compute.inner.input",
                            matchDetails.innerInputHex());
                    step.attribute(
                            VerboseTrace.AttributeType.HEX,
                            "match.hmac.compute.inner.hash",
                            matchDetails.innerHashHex());
                    step.attribute(
                            VerboseTrace.AttributeType.HEX,
                            "match.hmac.compute.outer.input",
                            matchDetails.outerInputHex());
                    step.attribute(
                            VerboseTrace.AttributeType.HEX, "match.hmac.compute.hmac.final", matchDetails.hmacHex());
                    step.attribute(
                            VerboseTrace.AttributeType.STRING,
                            "match.truncate.dynamic.last.byte",
                            matchDetails.lastByteHex());
                    step.attribute(
                            VerboseTrace.AttributeType.INT,
                            "match.truncate.dynamic.offset.nibble",
                            matchDetails.offset());
                    step.attribute(
                            VerboseTrace.AttributeType.HEX,
                            "match.truncate.dynamic.slice.bytes",
                            matchDetails.sliceHex());
                    step.attribute(
                            VerboseTrace.AttributeType.STRING,
                            "match.truncate.dynamic.slice.bytes[0]_masked",
                            matchDetails.sliceMaskedHex());
                    step.attribute(
                            VerboseTrace.AttributeType.INT,
                            "match.truncate.dynamic.dynamic_binary_code.31bit.big_endian",
                            matchDetails.truncatedInt());
                    step.attribute(VerboseTrace.AttributeType.INT, "match.mod.reduce.modulus", matchDetails.modulus());
                    step.attribute(
                            VerboseTrace.AttributeType.INT, "match.mod.reduce.otp.decimal", matchDetails.otpDecimal());
                    step.attribute(
                            VerboseTrace.AttributeType.STRING,
                            "match.mod.reduce.otp.string.leftpad",
                            matchDetails.otpString());
                    step.attribute(
                            VerboseTrace.AttributeType.STRING, "match.result.output.otp", matchDetails.otpString());
                    step.attribute(VerboseTrace.AttributeType.STRING, "match.marker.end", "-- end match.derivation --");
                }
            });

            final boolean match =
                    matchComputation != null && matchedWindowCounter != null && verificationResult.valid();
            final Long decisionMatchedCounter = matchedWindowCounter;
            final HotpVerificationResult decisionResult = verificationResult;
            addStep(trace, step -> {
                step.id("decision")
                        .summary("Derive HOTP verification decision")
                        .detail("HotpValidator.verify")
                        .spec(SPEC_HOTP_MOD)
                        .attribute(VerboseTrace.AttributeType.BOOL, "verify.match", match);
                if (match) {
                    step.attribute(VerboseTrace.AttributeType.INT, "matched.counter", decisionMatchedCounter);
                    step.attribute(
                            VerboseTrace.AttributeType.INT, "next.expected.counter", decisionResult.nextCounter());
                } else {
                    step.attribute(VerboseTrace.AttributeType.INT, "matched.counter", counter);
                    step.attribute(VerboseTrace.AttributeType.INT, "next.expected.counter", counter);
                    step.note("failure", "otp_mismatch");
                }
            });

            if (match) {
                String matchedCounterLabel =
                        matchedCounter != null ? matchedCounter.toString() : Long.toString(counter);
                metadata(trace, "matchedCounter", matchedCounterLabel);
                long previousCounterValue = matchedCounter != null ? matchedCounter.longValue() : counter;
                long telemetryNextCounter = safeIncrement(previousCounterValue);
                long nextCounterValue = telemetryNextCounter;
                return successResult(
                        credentialReference,
                        credentialId,
                        credentialSource,
                        descriptor.algorithm(),
                        descriptor.digits(),
                        previousCounterValue,
                        nextCounterValue,
                        telemetryNextCounter,
                        buildTrace(trace));
            }

            return mismatchResult(
                    credentialReference,
                    credentialId,
                    credentialSource,
                    descriptor.algorithm(),
                    descriptor.digits(),
                    counter,
                    credentialReference ? safeIncrement(counter) : counter,
                    counter,
                    buildTrace(trace));
        } catch (IllegalArgumentException ex) {
            addStep(trace, step -> step.id("decision")
                    .summary("Derive HOTP verification decision")
                    .detail("HotpValidator.verify")
                    .note("failure", safeMessage(ex)));
            return validationFailure(
                    credentialId,
                    credentialReference,
                    credentialSource,
                    descriptor.algorithm(),
                    descriptor.digits(),
                    counter,
                    "validation_error",
                    ex.getMessage(),
                    buildTrace(trace));
        } catch (RuntimeException ex) {
            addStep(trace, step -> step.id("error")
                    .summary("Unexpected error during HOTP replay")
                    .detail(ex.getClass().getSimpleName())
                    .note("message", safeMessage(ex)));
            return unexpectedErrorResult(
                    credentialId,
                    credentialReference,
                    credentialSource,
                    descriptor.algorithm(),
                    descriptor.digits(),
                    counter,
                    ex,
                    buildTrace(trace));
        }
    }

    private ReplayResult successResult(
            boolean credentialReference,
            String credentialId,
            String credentialSource,
            HotpHashAlgorithm algorithm,
            int digits,
            long previousCounter,
            long nextCounter,
            long telemetryNextCounter,
            VerboseTrace trace) {

        Map<String, Object> fields =
                replayFields(credentialSource, credentialId, algorithm, digits, previousCounter, telemetryNextCounter);
        TelemetrySignal signal = new TelemetrySignal(TelemetryStatus.SUCCESS, "match", null, true, fields, null);
        return new ReplayResult(
                signal, credentialReference, credentialId, previousCounter, nextCounter, algorithm, digits, trace);
    }

    private ReplayResult mismatchResult(
            boolean credentialReference,
            String credentialId,
            String credentialSource,
            HotpHashAlgorithm algorithm,
            int digits,
            long previousCounter,
            long nextCounter,
            long telemetryNextCounter,
            VerboseTrace trace) {

        Map<String, Object> fields =
                replayFields(credentialSource, credentialId, algorithm, digits, previousCounter, telemetryNextCounter);
        TelemetrySignal signal =
                new TelemetrySignal(TelemetryStatus.INVALID, "otp_mismatch", "OTP mismatch", true, fields, null);
        return new ReplayResult(
                signal, credentialReference, credentialId, previousCounter, nextCounter, algorithm, digits, trace);
    }

    private ReplayResult validationFailure(
            String credentialId,
            boolean credentialReference,
            String credentialSource,
            HotpHashAlgorithm algorithm,
            Integer digits,
            long counter,
            String reasonCode,
            String reason,
            VerboseTrace trace) {

        Map<String, Object> fields = replayFields(credentialSource, credentialId, algorithm, digits, counter, counter);
        TelemetrySignal signal =
                new TelemetrySignal(TelemetryStatus.INVALID, reasonCode, safeMessage(reason), true, fields, null);
        return new ReplayResult(signal, credentialReference, credentialId, counter, counter, algorithm, digits, trace);
    }

    private ReplayResult notFoundResult(String credentialId, VerboseTrace trace) {
        Map<String, Object> fields = replayFields("stored", credentialId, null, null, 0L, 0L);
        TelemetrySignal signal = new TelemetrySignal(
                TelemetryStatus.INVALID,
                "credential_not_found",
                "credentialId " + credentialId + " not found",
                true,
                fields,
                null);
        return new ReplayResult(signal, true, credentialId, 0L, 0L, null, null, trace);
    }

    private ReplayResult invalidMetadataResult(String credentialId, String reason, VerboseTrace trace) {
        Map<String, Object> fields = replayFields("stored", credentialId, null, null, 0L, 0L);
        TelemetrySignal signal = new TelemetrySignal(
                TelemetryStatus.INVALID, "invalid_hotp_metadata", safeMessage(reason), true, fields, null);
        return new ReplayResult(signal, true, credentialId, 0L, 0L, null, null, trace);
    }

    private ReplayResult unexpectedErrorResult(
            String credentialId,
            boolean credentialReference,
            String credentialSource,
            HotpHashAlgorithm algorithm,
            Integer digits,
            long counter,
            Throwable error,
            VerboseTrace trace) {

        Map<String, Object> fields = replayFields(credentialSource, credentialId, algorithm, digits, counter, counter);
        if (error != null) {
            fields.put("exception", error.getClass().getName() + ": " + safeMessage(error));
        }
        TelemetrySignal signal =
                new TelemetrySignal(TelemetryStatus.ERROR, "unexpected_error", safeMessage(error), false, fields, null);
        return new ReplayResult(signal, credentialReference, credentialId, counter, counter, algorithm, digits, trace);
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

    private static String formatWindowRange(long lowerBound, long upperBound) {
        return "[" + lowerBound + ", " + upperBound + "]";
    }

    private static long safeIncrement(long counter) {
        return counter == Long.MAX_VALUE ? Long.MAX_VALUE : counter + 1L;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static VerboseTrace.Builder newTrace(boolean verbose, String operation) {
        return verbose ? VerboseTrace.builder(operation) : null;
    }

    private static void metadata(VerboseTrace.Builder trace, String key, String value) {
        if (trace != null && value != null) {
            trace.withMetadata(key, value);
        }
    }

    private static void addStep(VerboseTrace.Builder trace, Consumer<VerboseTrace.TraceStep.Builder> configurer) {
        if (trace != null && configurer != null) {
            trace.addStep(configurer);
        }
    }

    private static VerboseTrace buildTrace(VerboseTrace.Builder trace) {
        return trace == null ? null : trace.build();
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
