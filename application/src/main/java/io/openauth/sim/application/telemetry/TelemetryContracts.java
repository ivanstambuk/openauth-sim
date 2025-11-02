package io.openauth.sim.application.telemetry;

/** Factory for shared telemetry adapters. */
public final class TelemetryContracts {

    private static final OcraTelemetryAdapter OCRA_EVALUATION_ADAPTER = new OcraTelemetryAdapter("ocra.evaluate");

    private static final OcraTelemetryAdapter OCRA_VERIFICATION_ADAPTER = new OcraTelemetryAdapter("ocra.verify");

    private static final OcraTelemetryAdapter OCRA_SEEDING_ADAPTER = new OcraTelemetryAdapter("ocra.seed");

    private static final HotpTelemetryAdapter HOTP_EVALUATION_ADAPTER = new HotpTelemetryAdapter("hotp.evaluate");

    private static final HotpTelemetryAdapter HOTP_ISSUANCE_ADAPTER = new HotpTelemetryAdapter("hotp.issue");

    private static final HotpTelemetryAdapter HOTP_REPLAY_ADAPTER = new HotpTelemetryAdapter("hotp.replay");

    private static final HotpTelemetryAdapter HOTP_SEEDING_ADAPTER = new HotpTelemetryAdapter("hotp.seed");

    private static final TotpTelemetryAdapter TOTP_EVALUATION_ADAPTER = new TotpTelemetryAdapter("totp.evaluate");
    private static final TotpTelemetryAdapter TOTP_REPLAY_ADAPTER = new TotpTelemetryAdapter("totp.replay");
    private static final TotpTelemetryAdapter TOTP_SEEDING_ADAPTER = new TotpTelemetryAdapter("totp.seed");
    private static final TotpTelemetryAdapter TOTP_SAMPLE_ADAPTER = new TotpTelemetryAdapter("totp.sample");
    private static final Fido2TelemetryAdapter FIDO2_EVALUATION_ADAPTER = new Fido2TelemetryAdapter("fido2.evaluate");
    private static final Fido2TelemetryAdapter FIDO2_REPLAY_ADAPTER = new Fido2TelemetryAdapter("fido2.replay");
    private static final Fido2TelemetryAdapter FIDO2_ATTEST_ADAPTER = new Fido2TelemetryAdapter("fido2.attest");
    private static final Fido2TelemetryAdapter FIDO2_ATTEST_REPLAY_ADAPTER =
            new Fido2TelemetryAdapter("fido2.attestReplay");
    private static final EmvCapTelemetryAdapter EMV_CAP_IDENTIFY_ADAPTER =
            new EmvCapTelemetryAdapter("emv.cap.identify");
    private static final EmvCapTelemetryAdapter EMV_CAP_RESPOND_ADAPTER = new EmvCapTelemetryAdapter("emv.cap.respond");
    private static final EmvCapTelemetryAdapter EMV_CAP_SIGN_ADAPTER = new EmvCapTelemetryAdapter("emv.cap.sign");
    private static final EmvCapTelemetryAdapter EMV_CAP_SEED_ADAPTER = new EmvCapTelemetryAdapter("emv.cap.seed");
    private static final EmvCapTelemetryAdapter EMV_CAP_REPLAY_IDENTIFY_ADAPTER =
            new EmvCapTelemetryAdapter("emv.cap.replay.identify");
    private static final EmvCapTelemetryAdapter EMV_CAP_REPLAY_RESPOND_ADAPTER =
            new EmvCapTelemetryAdapter("emv.cap.replay.respond");
    private static final EmvCapTelemetryAdapter EMV_CAP_REPLAY_SIGN_ADAPTER =
            new EmvCapTelemetryAdapter("emv.cap.replay.sign");

    private TelemetryContracts() {
        throw new AssertionError("No instances");
    }

    /** Returns the shared adapter for OCRA evaluation telemetry. */
    public static OcraTelemetryAdapter ocraEvaluationAdapter() {
        return OCRA_EVALUATION_ADAPTER;
    }

    /** Returns the shared adapter for OCRA verification telemetry. */
    public static OcraTelemetryAdapter ocraVerificationAdapter() {
        return OCRA_VERIFICATION_ADAPTER;
    }

    /** Returns the shared adapter for OCRA credential seeding telemetry. */
    public static OcraTelemetryAdapter ocraSeedingAdapter() {
        return OCRA_SEEDING_ADAPTER;
    }

    /** Returns the shared adapter for HOTP evaluation telemetry. */
    public static HotpTelemetryAdapter hotpEvaluationAdapter() {
        return HOTP_EVALUATION_ADAPTER;
    }

    /** Returns the shared adapter for HOTP issuance telemetry. */
    public static HotpTelemetryAdapter hotpIssuanceAdapter() {
        return HOTP_ISSUANCE_ADAPTER;
    }

    /** Returns the shared adapter for HOTP replay telemetry. */
    public static HotpTelemetryAdapter hotpReplayAdapter() {
        return HOTP_REPLAY_ADAPTER;
    }

    /** Returns the shared adapter for HOTP seeding telemetry. */
    public static HotpTelemetryAdapter hotpSeedingAdapter() {
        return HOTP_SEEDING_ADAPTER;
    }

    /** Returns the shared adapter for TOTP evaluation telemetry. */
    public static TotpTelemetryAdapter totpEvaluationAdapter() {
        return TOTP_EVALUATION_ADAPTER;
    }

    /** Returns the shared adapter for TOTP replay telemetry. */
    public static TotpTelemetryAdapter totpReplayAdapter() {
        return TOTP_REPLAY_ADAPTER;
    }

    /** Returns the shared adapter for TOTP seeding telemetry. */
    public static TotpTelemetryAdapter totpSeedingAdapter() {
        return TOTP_SEEDING_ADAPTER;
    }

    /** Returns the shared adapter for TOTP stored sample telemetry. */
    public static TotpTelemetryAdapter totpSampleAdapter() {
        return TOTP_SAMPLE_ADAPTER;
    }

    /** Returns the shared adapter for FIDO2/WebAuthn evaluation telemetry. */
    public static Fido2TelemetryAdapter fido2EvaluationAdapter() {
        return FIDO2_EVALUATION_ADAPTER;
    }

    /** Returns the shared adapter for FIDO2/WebAuthn replay telemetry. */
    public static Fido2TelemetryAdapter fido2ReplayAdapter() {
        return FIDO2_REPLAY_ADAPTER;
    }

    /** Returns the shared adapter for FIDO2/WebAuthn attestation telemetry. */
    public static Fido2TelemetryAdapter fido2AttestAdapter() {
        return FIDO2_ATTEST_ADAPTER;
    }

    /** Returns the shared adapter for FIDO2/WebAuthn attestation replay telemetry. */
    public static Fido2TelemetryAdapter fido2AttestReplayAdapter() {
        return FIDO2_ATTEST_REPLAY_ADAPTER;
    }

    /** Returns the shared adapter for EMV/CAP Identify telemetry. */
    public static EmvCapTelemetryAdapter emvCapIdentifyAdapter() {
        return EMV_CAP_IDENTIFY_ADAPTER;
    }

    /** Returns the shared adapter for EMV/CAP Respond telemetry. */
    public static EmvCapTelemetryAdapter emvCapRespondAdapter() {
        return EMV_CAP_RESPOND_ADAPTER;
    }

    /** Returns the shared adapter for EMV/CAP Sign telemetry. */
    public static EmvCapTelemetryAdapter emvCapSignAdapter() {
        return EMV_CAP_SIGN_ADAPTER;
    }

    /** Returns the shared adapter for EMV/CAP seeding telemetry. */
    public static EmvCapTelemetryAdapter emvCapSeedingAdapter() {
        return EMV_CAP_SEED_ADAPTER;
    }

    /** Returns the shared adapter for EMV/CAP Identify replay telemetry. */
    public static EmvCapTelemetryAdapter emvCapReplayIdentifyAdapter() {
        return EMV_CAP_REPLAY_IDENTIFY_ADAPTER;
    }

    /** Returns the shared adapter for EMV/CAP Respond replay telemetry. */
    public static EmvCapTelemetryAdapter emvCapReplayRespondAdapter() {
        return EMV_CAP_REPLAY_RESPOND_ADAPTER;
    }

    /** Returns the shared adapter for EMV/CAP Sign replay telemetry. */
    public static EmvCapTelemetryAdapter emvCapReplaySignAdapter() {
        return EMV_CAP_REPLAY_SIGN_ADAPTER;
    }
}
