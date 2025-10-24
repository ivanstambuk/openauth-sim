package io.openauth.sim.application.totp;

import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.totp.TotpCredentialPersistenceAdapter;
import io.openauth.sim.core.otp.totp.TotpDescriptor;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.otp.totp.TotpGenerator;
import io.openauth.sim.core.otp.totp.TotpHashAlgorithm;
import io.openauth.sim.core.otp.totp.TotpValidator;
import io.openauth.sim.core.otp.totp.TotpVerificationResult;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import io.openauth.sim.core.trace.VerboseTrace;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Application-level orchestrator for validating TOTP submissions. */
public final class TotpEvaluationApplicationService {

    private static final String INLINE_DESCRIPTOR_NAME = "totp-inline-request";
    private static final String SPEC_HOTP_INPUT = "rfc4226§5.1";
    private static final String SPEC_HOTP_HMAC = "rfc4226§5.2";
    private static final String SPEC_HOTP_TRUNCATE = "rfc4226§5.3";
    private static final String SPEC_HOTP_MOD = "rfc4226§5.4";
    private static final String SPEC_TOTP_COUNTER = "rfc6238§4.2";
    private static final String SPEC_TOTP_WINDOW = "rfc6238§4.1";
    private static final String SPEC_TOTP_INPUT = "rfc6238§1,§2";

    private final CredentialStore credentialStore;
    private final Clock clock;
    private final TotpCredentialPersistenceAdapter persistenceAdapter;

    public TotpEvaluationApplicationService(CredentialStore credentialStore) {
        this(credentialStore, Clock.systemUTC());
    }

    public TotpEvaluationApplicationService(CredentialStore credentialStore, Clock clock) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.persistenceAdapter = new TotpCredentialPersistenceAdapter();
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
        throw new IllegalStateException("Unsupported TOTP evaluation command: " + command);
    }

    private EvaluationResult evaluateStored(EvaluationCommand.Stored command, boolean verbose) {
        VerboseTrace.Builder trace = newTrace(verbose, "totp.evaluate.stored");
        metadata(trace, "protocol", "TOTP");
        metadata(trace, "mode", "stored");
        metadata(trace, "credentialId", command.credentialId());

        Credential credential =
                credentialStore.findByName(command.credentialId()).orElse(null);
        if (credential == null) {
            addStep(trace, step -> step.id("resolve.credential")
                    .summary("Resolve stored TOTP credential")
                    .detail("CredentialStore.findByName")
                    .attribute("credentialId", command.credentialId())
                    .attribute("found", false));
            return credentialNotFound(command.credentialId(), buildTrace(trace));
        }
        if (credential.type() != CredentialType.OATH_TOTP) {
            return validationFailure(
                    command.credentialId(),
                    true,
                    null,
                    null,
                    null,
                    command.driftWindow(),
                    "credential_not_totp",
                    "Credential is not a TOTP entry",
                    command.timestampOverride().isPresent(),
                    Integer.MIN_VALUE,
                    buildTrace(trace));
        }

        TotpDescriptor descriptor;
        try {
            descriptor = persistenceAdapter.deserialize(VersionedCredentialRecordMapper.toRecord(credential));
        } catch (IllegalArgumentException ex) {
            return validationFailure(
                    command.credentialId(),
                    true,
                    null,
                    null,
                    null,
                    command.driftWindow(),
                    "credential_metadata_invalid",
                    ex.getMessage(),
                    command.timestampOverride().isPresent(),
                    Integer.MIN_VALUE,
                    buildTrace(trace));
        }

        String candidateOtp = sanitizeOtp(command.otp());
        boolean validationRequested = !candidateOtp.isEmpty();

        String secretHash = sha256Digest(descriptor.secret().value());
        int stepSeconds = Math.toIntExact(descriptor.stepSeconds());

        addStep(trace, step -> step.id("resolve.credential")
                .summary("Resolve stored TOTP credential")
                .detail("CredentialStore.findByName")
                .spec(SPEC_TOTP_INPUT)
                .attribute(VerboseTrace.AttributeType.STRING, "credentialId", command.credentialId())
                .attribute(
                        VerboseTrace.AttributeType.STRING,
                        "alg",
                        descriptor.algorithm().traceLabel())
                .attribute(VerboseTrace.AttributeType.INT, "digits", descriptor.digits())
                .attribute(VerboseTrace.AttributeType.INT, "stepSeconds", stepSeconds)
                .attribute(
                        VerboseTrace.AttributeType.INT,
                        "drift.backward",
                        descriptor.driftWindow().backwardSteps())
                .attribute(
                        VerboseTrace.AttributeType.INT,
                        "drift.forward",
                        descriptor.driftWindow().forwardSteps())
                .attribute(VerboseTrace.AttributeType.STRING, "secret.hash", secretHash));
        if (!validationRequested) {
            Instant evaluationInstant = defaultInstant(command.evaluationInstant());
            Instant generationInstant = command.timestampOverride().orElse(evaluationInstant);
            TotpCounter counter = deriveCounter(generationInstant, descriptor);
            TotpComputation computation =
                    computeTotp(descriptor, descriptor.secret().value(), counter.counter());
            appendTotpSteps(trace, descriptor, counter, descriptor.driftWindow(), computation);
            return successResult(
                    true,
                    command.credentialId(),
                    descriptor.algorithm(),
                    descriptor.digits(),
                    descriptor.stepDuration(),
                    command.driftWindow(),
                    0,
                    command.timestampOverride().isPresent(),
                    "generated",
                    computation.otp(),
                    buildTrace(trace));
        }

        if (!isValidOtpFormat(candidateOtp, descriptor.digits())) {
            return validationFailure(
                    command.credentialId(),
                    true,
                    descriptor.algorithm(),
                    descriptor.digits(),
                    descriptor.stepDuration(),
                    command.driftWindow(),
                    "otp_invalid_format",
                    "OTP must be numeric and match the required digit length",
                    command.timestampOverride().isPresent(),
                    Integer.MIN_VALUE,
                    buildTrace(trace));
        }

        Instant evaluationInstant = defaultInstant(command.evaluationInstant());
        Instant referenceInstant = command.timestampOverride().orElse(evaluationInstant);
        TotpCounter validationCounter = deriveCounter(referenceInstant, descriptor);
        TotpComputation validationComputation =
                computeTotp(descriptor, descriptor.secret().value(), validationCounter.counter());
        appendTotpSteps(trace, descriptor, validationCounter, command.driftWindow(), validationComputation);

        TotpVerificationResult verification = TotpValidator.verify(
                descriptor,
                candidateOtp,
                evaluationInstant,
                command.driftWindow(),
                command.timestampOverride().orElse(null));

        if (verification.valid()) {
            addStep(trace, step -> step.id("validate.otp")
                    .summary("Validate stored credential OTP")
                    .detail("TotpValidator.verify")
                    .spec(SPEC_TOTP_WINDOW)
                    .attribute(VerboseTrace.AttributeType.STRING, "evaluationInstant", evaluationInstant.toString())
                    .attribute(VerboseTrace.AttributeType.BOOL, "valid", true)
                    .attribute(VerboseTrace.AttributeType.INT, "matchedSkewSteps", verification.matchedSkewSteps()));
            return successResult(
                    true,
                    command.credentialId(),
                    descriptor.algorithm(),
                    descriptor.digits(),
                    descriptor.stepDuration(),
                    command.driftWindow(),
                    verification.matchedSkewSteps(),
                    command.timestampOverride().isPresent(),
                    "validated",
                    null,
                    buildTrace(trace));
        }

        addStep(trace, step -> step.id("validate.otp")
                .summary("Validate stored credential OTP")
                .detail("TotpValidator.verify")
                .spec(SPEC_TOTP_WINDOW)
                .attribute(VerboseTrace.AttributeType.STRING, "evaluationInstant", evaluationInstant.toString())
                .attribute(VerboseTrace.AttributeType.BOOL, "valid", false)
                .attribute(VerboseTrace.AttributeType.INT, "matchedSkewSteps", verification.matchedSkewSteps())
                .note("reason", "otp_out_of_window"));
        return validationFailure(
                command.credentialId(),
                true,
                descriptor.algorithm(),
                descriptor.digits(),
                descriptor.stepDuration(),
                command.driftWindow(),
                "otp_out_of_window",
                "OTP did not match within the permitted drift window",
                command.timestampOverride().isPresent(),
                verification.matchedSkewSteps(),
                buildTrace(trace));
    }

    private EvaluationResult evaluateInline(EvaluationCommand.Inline command, boolean verbose) {
        SecretMaterial secret;
        VerboseTrace.Builder trace = newTrace(verbose, "totp.evaluate.inline");
        metadata(trace, "protocol", "TOTP");
        metadata(trace, "mode", "inline");

        try {
            secret = SecretMaterial.fromHex(command.sharedSecretHex().trim());
        } catch (IllegalArgumentException ex) {
            return validationFailure(
                    null,
                    false,
                    command.algorithm(),
                    command.digits(),
                    command.stepDuration(),
                    command.driftWindow(),
                    "shared_secret_invalid",
                    ex.getMessage(),
                    command.timestampOverride().isPresent(),
                    Integer.MIN_VALUE,
                    buildTrace(trace));
        }

        TotpDescriptor descriptor;
        try {
            descriptor = TotpDescriptor.create(
                    INLINE_DESCRIPTOR_NAME,
                    secret,
                    command.algorithm(),
                    command.digits(),
                    command.stepDuration(),
                    command.driftWindow());
        } catch (IllegalArgumentException ex) {
            return validationFailure(
                    null,
                    false,
                    command.algorithm(),
                    command.digits(),
                    command.stepDuration(),
                    command.driftWindow(),
                    "validation_error",
                    ex.getMessage(),
                    command.timestampOverride().isPresent(),
                    Integer.MIN_VALUE,
                    buildTrace(trace));
        }

        String inlineSecretHash = sha256Digest(descriptor.secret().value());
        int inlineStepSeconds = Math.toIntExact(descriptor.stepSeconds());

        addStep(trace, step -> step.id("normalize.input")
                .summary("Construct inline TOTP descriptor")
                .detail("TotpDescriptor.create")
                .spec(SPEC_TOTP_INPUT)
                .attribute(
                        VerboseTrace.AttributeType.STRING,
                        "alg",
                        descriptor.algorithm().traceLabel())
                .attribute(VerboseTrace.AttributeType.INT, "digits", descriptor.digits())
                .attribute(VerboseTrace.AttributeType.INT, "stepSeconds", inlineStepSeconds)
                .attribute(
                        VerboseTrace.AttributeType.INT,
                        "drift.backward",
                        descriptor.driftWindow().backwardSteps())
                .attribute(
                        VerboseTrace.AttributeType.INT,
                        "drift.forward",
                        descriptor.driftWindow().forwardSteps())
                .attribute(VerboseTrace.AttributeType.STRING, "secret.hash", inlineSecretHash));

        String candidateOtp = sanitizeOtp(command.otp());
        boolean validationRequested = !candidateOtp.isEmpty();

        if (!validationRequested) {
            Instant evaluationInstant = defaultInstant(command.evaluationInstant());
            Instant generationInstant = command.timestampOverride().orElse(evaluationInstant);
            TotpCounter counter = deriveCounter(generationInstant, descriptor);
            TotpComputation computation =
                    computeTotp(descriptor, descriptor.secret().value(), counter.counter());
            appendTotpSteps(trace, descriptor, counter, descriptor.driftWindow(), computation);
            return successResult(
                    false,
                    null,
                    descriptor.algorithm(),
                    descriptor.digits(),
                    descriptor.stepDuration(),
                    descriptor.driftWindow(),
                    0,
                    command.timestampOverride().isPresent(),
                    "generated",
                    computation.otp(),
                    buildTrace(trace));
        }

        if (!isValidOtpFormat(candidateOtp, descriptor.digits())) {
            return validationFailure(
                    null,
                    false,
                    descriptor.algorithm(),
                    descriptor.digits(),
                    descriptor.stepDuration(),
                    descriptor.driftWindow(),
                    "otp_invalid_format",
                    "OTP must be numeric and match the required digit length",
                    command.timestampOverride().isPresent(),
                    Integer.MIN_VALUE,
                    buildTrace(trace));
        }

        Instant evaluationInstant = defaultInstant(command.evaluationInstant());
        Instant referenceInstant = command.timestampOverride().orElse(evaluationInstant);
        TotpCounter inlineCounter = deriveCounter(referenceInstant, descriptor);
        TotpComputation inlineComputation =
                computeTotp(descriptor, descriptor.secret().value(), inlineCounter.counter());
        appendTotpSteps(trace, descriptor, inlineCounter, descriptor.driftWindow(), inlineComputation);

        TotpVerificationResult verification = TotpValidator.verify(
                descriptor,
                candidateOtp,
                evaluationInstant,
                descriptor.driftWindow(),
                command.timestampOverride().orElse(null));

        if (verification.valid()) {
            addStep(trace, step -> step.id("validate.otp")
                    .summary("Validate inline OTP")
                    .detail("TotpValidator.verify")
                    .spec(SPEC_TOTP_WINDOW)
                    .attribute(VerboseTrace.AttributeType.STRING, "evaluationInstant", evaluationInstant.toString())
                    .attribute(VerboseTrace.AttributeType.BOOL, "valid", true)
                    .attribute(VerboseTrace.AttributeType.INT, "matchedSkewSteps", verification.matchedSkewSteps()));
            return successResult(
                    false,
                    null,
                    descriptor.algorithm(),
                    descriptor.digits(),
                    descriptor.stepDuration(),
                    descriptor.driftWindow(),
                    verification.matchedSkewSteps(),
                    command.timestampOverride().isPresent(),
                    "validated",
                    null,
                    buildTrace(trace));
        }

        addStep(trace, step -> step.id("validate.otp")
                .summary("Validate inline OTP")
                .detail("TotpValidator.verify")
                .spec(SPEC_TOTP_WINDOW)
                .attribute(VerboseTrace.AttributeType.STRING, "evaluationInstant", evaluationInstant.toString())
                .attribute(VerboseTrace.AttributeType.BOOL, "valid", false)
                .attribute(VerboseTrace.AttributeType.INT, "matchedSkewSteps", verification.matchedSkewSteps())
                .note("reason", "otp_out_of_window"));
        return validationFailure(
                null,
                false,
                descriptor.algorithm(),
                descriptor.digits(),
                descriptor.stepDuration(),
                descriptor.driftWindow(),
                "otp_out_of_window",
                "OTP did not match within the permitted drift window",
                command.timestampOverride().isPresent(),
                verification.matchedSkewSteps(),
                buildTrace(trace));
    }

    private EvaluationResult credentialNotFound(String credentialId, VerboseTrace trace) {
        return validationFailure(
                credentialId,
                false,
                null,
                null,
                null,
                TotpDriftWindow.of(0, 0),
                "credential_not_found",
                "Credential not found",
                false,
                Integer.MIN_VALUE,
                trace);
    }

    private EvaluationResult successResult(
            boolean credentialReference,
            String credentialId,
            TotpHashAlgorithm algorithm,
            Integer digits,
            Duration stepDuration,
            TotpDriftWindow driftWindow,
            int matchedSkewSteps,
            boolean timestampOverrideProvided,
            String reasonCode,
            String otp,
            VerboseTrace trace) {

        Map<String, Object> fields = telemetryFields(
                credentialReference,
                credentialId,
                algorithm,
                digits,
                stepDuration,
                driftWindow,
                timestampOverrideProvided,
                matchedSkewSteps);

        TelemetrySignal telemetry = new TelemetrySignal(TelemetryStatus.SUCCESS, reasonCode, null, true, fields);

        return new EvaluationResult(
                telemetry,
                credentialReference,
                credentialId,
                true,
                matchedSkewSteps,
                algorithm,
                digits,
                stepDuration,
                driftWindow,
                otp,
                trace);
    }

    private EvaluationResult validationFailure(
            String credentialId,
            boolean credentialReference,
            TotpHashAlgorithm algorithm,
            Integer digits,
            Duration stepDuration,
            TotpDriftWindow driftWindow,
            String reasonCode,
            String reason,
            boolean timestampOverrideProvided,
            int matchedSkewSteps,
            VerboseTrace trace) {

        Map<String, Object> fields = telemetryFields(
                credentialReference,
                credentialId,
                algorithm,
                digits,
                stepDuration,
                driftWindow,
                timestampOverrideProvided,
                matchedSkewSteps);

        TelemetrySignal telemetry = new TelemetrySignal(TelemetryStatus.INVALID, reasonCode, reason, true, fields);

        return new EvaluationResult(
                telemetry,
                credentialReference,
                credentialId,
                false,
                matchedSkewSteps,
                algorithm,
                digits,
                stepDuration,
                driftWindow,
                null,
                trace);
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

    private Map<String, Object> telemetryFields(
            boolean credentialReference,
            String credentialId,
            TotpHashAlgorithm algorithm,
            Integer digits,
            Duration stepDuration,
            TotpDriftWindow driftWindow,
            boolean timestampOverrideProvided,
            int matchedSkewSteps) {

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("credentialReference", credentialReference);
        if (credentialReference && credentialId != null) {
            fields.put("credentialId", credentialId);
        }
        if (algorithm != null) {
            fields.put("algorithm", algorithm.name());
        }
        if (digits != null) {
            fields.put("digits", digits);
        }
        if (stepDuration != null) {
            fields.put("stepSeconds", stepDuration.getSeconds());
        }
        if (driftWindow != null) {
            fields.put("driftBackwardSteps", driftWindow.backwardSteps());
            fields.put("driftForwardSteps", driftWindow.forwardSteps());
        }
        fields.put("timestampOverrideProvided", timestampOverrideProvided);
        fields.put("matchedSkewSteps", matchedSkewSteps);
        return fields;
    }

    private record TotpCounter(
            long epochSeconds, long counter, long stepSeconds, long stepStartSeconds, long stepEndSeconds) {
        // Marker record for time-counter derivation details.
    }

    private record TotpComputation(
            long counter,
            String counterHex,
            String secretHash,
            String hmacHex,
            int offset,
            int truncatedInt,
            String otp) {
        // Marker record for TOTP computation snapshot.
    }

    private static TotpCounter deriveCounter(Instant instant, TotpDescriptor descriptor) {
        long epochSeconds = instant.getEpochSecond();
        long stepSeconds = descriptor.stepSeconds();
        long counter = Math.floorDiv(epochSeconds, stepSeconds);
        long stepStart = counter * stepSeconds;
        long stepEnd = stepStart + stepSeconds;
        return new TotpCounter(epochSeconds, counter, stepSeconds, stepStart, stepEnd);
    }

    private static TotpComputation computeTotp(TotpDescriptor descriptor, byte[] secret, long counter) {
        byte[] counterBytes = longToBytes(counter);
        String counterHex = hex(counterBytes);
        String secretHash = sha256Digest(secret);
        byte[] hmac = hmac(descriptor.algorithm(), secret, counterBytes);
        String hmacHex = hex(hmac);
        int offset = dynamicOffset(hmac);
        int truncated = dynamicBinaryCode(hmac, offset);
        String otp = formatOtp(truncated, descriptor.digits());
        return new TotpComputation(counter, counterHex, secretHash, hmacHex, offset, truncated, otp);
    }

    private static void appendTotpSteps(
            VerboseTrace.Builder trace,
            TotpDescriptor descriptor,
            TotpCounter counter,
            TotpDriftWindow driftWindow,
            TotpComputation computation) {
        addStep(trace, step -> step.id("derive.time-counter")
                .summary("Derive TOTP counter")
                .detail("floor((epoch - T0)/step)")
                .spec(SPEC_TOTP_COUNTER)
                .attribute(VerboseTrace.AttributeType.INT, "epoch.seconds", Math.toIntExact(counter.epochSeconds()))
                .attribute(VerboseTrace.AttributeType.INT, "t0.seconds", 0)
                .attribute(VerboseTrace.AttributeType.INT, "step.seconds", Math.toIntExact(counter.stepSeconds()))
                .attribute(VerboseTrace.AttributeType.INT, "time.counter.int", Math.toIntExact(counter.counter()))
                .attribute(
                        VerboseTrace.AttributeType.STRING,
                        "time.counter.hex",
                        String.format("%016x", counter.counter()))
                .attribute(
                        VerboseTrace.AttributeType.INT,
                        "step.start.seconds",
                        Math.toIntExact(counter.stepStartSeconds()))
                .attribute(
                        VerboseTrace.AttributeType.INT, "step.end.seconds", Math.toIntExact(counter.stepEndSeconds())));

        addStep(trace, step -> {
            step.id("evaluate.window");
            step.summary("Evaluate drift window candidates");
            step.detail("TotpGenerator.generate");
            step.spec(SPEC_TOTP_WINDOW);
            step.attribute(VerboseTrace.AttributeType.INT, "skew.backward", driftWindow.backwardSteps());
            step.attribute(VerboseTrace.AttributeType.INT, "skew.forward", driftWindow.forwardSteps());
            for (int offset = -driftWindow.backwardSteps(); offset <= driftWindow.forwardSteps(); offset++) {
                String offsetOtp = generateTotpForOffset(descriptor, counter.counter(), offset);
                if (offsetOtp != null) {
                    step.attribute(VerboseTrace.AttributeType.STRING, "offset." + offset + ".otp", offsetOtp);
                }
            }
        });

        addStep(trace, step -> step.id("prepare.counter")
                .summary("Prepare HOTP counter for HMAC input")
                .detail("ByteBuffer.putLong")
                .spec(SPEC_HOTP_INPUT)
                .attribute(VerboseTrace.AttributeType.INT, "counter.int", Math.toIntExact(counter.counter()))
                .attribute(VerboseTrace.AttributeType.STRING, "counter.hex", computation.counterHex())
                .attribute(VerboseTrace.AttributeType.STRING, "counter.bytes.hex", computation.counterHex()));

        addStep(trace, step -> step.id("compute.hmac")
                .summary("Compute TOTP HMAC")
                .detail(descriptor.algorithm().traceLabel())
                .spec(SPEC_HOTP_HMAC)
                .attribute(
                        VerboseTrace.AttributeType.STRING,
                        "alg",
                        descriptor.algorithm().traceLabel())
                .attribute(VerboseTrace.AttributeType.STRING, "key.hash", computation.secretHash())
                .attribute(VerboseTrace.AttributeType.STRING, "message.hex", computation.counterHex())
                .attribute(VerboseTrace.AttributeType.STRING, "hmac.hex", computation.hmacHex()));

        addStep(trace, step -> step.id("truncate.dynamic")
                .summary("Apply dynamic truncation")
                .detail("RFC4226 bit extraction")
                .spec(SPEC_HOTP_TRUNCATE)
                .attribute(VerboseTrace.AttributeType.INT, "offset", computation.offset())
                .attribute(VerboseTrace.AttributeType.INT, "dynamic_binary_code", computation.truncatedInt()));

        addStep(trace, step -> step.id("mod.reduce")
                .summary("Reduce truncated value to digits")
                .detail("binary % 10^digits")
                .spec(SPEC_HOTP_MOD)
                .attribute(VerboseTrace.AttributeType.INT, "digits", descriptor.digits())
                .attribute(VerboseTrace.AttributeType.STRING, "otp", computation.otp()));
    }

    private static String generateTotpForOffset(TotpDescriptor descriptor, long counter, int offset) {
        long candidate = counter + offset;
        if (candidate < 0) {
            return null;
        }
        long stepSeconds = descriptor.stepSeconds();
        Instant instant = Instant.ofEpochSecond(candidate * stepSeconds);
        return TotpGenerator.generate(descriptor, instant);
    }

    private static byte[] longToBytes(long value) {
        return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte aByte : bytes) {
            builder.append(String.format("%02x", aByte));
        }
        return builder.toString();
    }

    private static String sha256Digest(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + hex(digest.digest(value));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static byte[] hmac(TotpHashAlgorithm algorithm, byte[] secret, byte[] message) {
        try {
            Mac mac = Mac.getInstance(algorithm.macAlgorithm());
            mac.init(new SecretKeySpec(secret, algorithm.macAlgorithm()));
            return mac.doFinal(message);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to compute TOTP HMAC", ex);
        }
    }

    private static int dynamicOffset(byte[] hmac) {
        return hmac[hmac.length - 1] & 0x0F;
    }

    private static int dynamicBinaryCode(byte[] hmac, int offset) {
        return ((hmac[offset] & 0x7F) << 24)
                | ((hmac[offset + 1] & 0xFF) << 16)
                | ((hmac[offset + 2] & 0xFF) << 8)
                | (hmac[offset + 3] & 0xFF);
    }

    private static String formatOtp(int truncatedInt, int digits) {
        int modulus = decimalModulus(digits);
        int otpValue = truncatedInt % modulus;
        return String.format("%0" + digits + "d", otpValue);
    }

    private static int decimalModulus(int digits) {
        int modulus = 1;
        for (int i = 0; i < digits; i++) {
            modulus *= 10;
        }
        return modulus;
    }

    private boolean isValidOtpFormat(String otp, int expectedDigits) {
        return otp.length() == expectedDigits && otp.chars().allMatch(Character::isDigit);
    }

    private String sanitizeOtp(String otp) {
        return otp == null ? "" : otp.trim();
    }

    private Instant defaultInstant(Instant provided) {
        return provided != null ? provided : clock.instant();
    }

    public sealed interface EvaluationCommand permits EvaluationCommand.Stored, EvaluationCommand.Inline {

        record Stored(
                String credentialId,
                String otp,
                TotpDriftWindow driftWindow,
                Instant evaluationInstant,
                Optional<Instant> timestampOverride)
                implements EvaluationCommand {

            public Stored {
                credentialId =
                        Objects.requireNonNull(credentialId, "credentialId").trim();
                otp = Objects.requireNonNull(otp, "otp");
                driftWindow = Objects.requireNonNull(driftWindow, "driftWindow");
                timestampOverride = timestampOverride == null ? Optional.empty() : timestampOverride;
            }
        }

        record Inline(
                String sharedSecretHex,
                TotpHashAlgorithm algorithm,
                int digits,
                Duration stepDuration,
                String otp,
                TotpDriftWindow driftWindow,
                Instant evaluationInstant,
                Optional<Instant> timestampOverride)
                implements EvaluationCommand {

            public Inline {
                sharedSecretHex = Objects.requireNonNull(sharedSecretHex, "sharedSecretHex");
                algorithm = Objects.requireNonNull(algorithm, "algorithm");
                stepDuration = Objects.requireNonNull(stepDuration, "stepDuration");
                otp = Objects.requireNonNull(otp, "otp");
                driftWindow = Objects.requireNonNull(driftWindow, "driftWindow");
                timestampOverride = timestampOverride == null ? Optional.empty() : timestampOverride;
            }
        }
    }

    public record EvaluationResult(
            TelemetrySignal telemetry,
            boolean credentialReference,
            String credentialId,
            boolean valid,
            int matchedSkewSteps,
            TotpHashAlgorithm algorithm,
            Integer digits,
            Duration stepDuration,
            TotpDriftWindow driftWindow,
            String otp,
            VerboseTrace trace) {

        public TelemetryFrame evaluationFrame(String telemetryId) {
            return telemetry.emit(TelemetryContracts.totpEvaluationAdapter(), telemetryId);
        }

        public Optional<VerboseTrace> verboseTrace() {
            return Optional.ofNullable(trace);
        }
    }

    public record TelemetrySignal(
            TelemetryStatus status, String reasonCode, String reason, boolean sanitized, Map<String, Object> fields) {

        public TelemetryFrame emit(
                io.openauth.sim.application.telemetry.TotpTelemetryAdapter adapter, String telemetryId) {
            Objects.requireNonNull(adapter, "adapter");
            Objects.requireNonNull(telemetryId, "telemetryId");
            String statusKey =
                    switch (status) {
                        case SUCCESS -> "success";
                        case INVALID -> "invalid";
                        case ERROR -> "error";
                    };
            return adapter.status(statusKey, telemetryId, reasonCode, sanitized, reason, fields);
        }
    }

    public enum TelemetryStatus {
        SUCCESS,
        INVALID,
        ERROR
    }
}
