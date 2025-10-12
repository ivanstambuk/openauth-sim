package io.openauth.sim.application.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared telemetry assertions used across facade contract tests. Intentionally red to drive the
 * shared telemetry adapter implementation.
 */
public final class TelemetryContractTestSupport {

  private static final String OCRA_EVALUATION_EVENT = "ocra.evaluate";
  private static final String HOTP_EVALUATION_EVENT = "hotp.evaluate";
  private static final String HOTP_ISSUANCE_EVENT = "hotp.issue";
  private static final String HOTP_REPLAY_EVENT = "hotp.replay";
  private static final String HOTP_SEED_EVENT = "hotp.seed";
  private static final String HOTP_INLINE_REPLAY_ID = "hotp-inline-replay";
  private static final String TOTP_REPLAY_EVENT = "totp.replay";
  private static final String TOTP_SEED_EVENT = "totp.seed";
  private static final String TOTP_SAMPLE_EVENT = "totp.sample";

  private TelemetryContractTestSupport() {
    throw new AssertionError("No instances");
  }

  public static String telemetryId() {
    return "telemetry-042";
  }

  public static Map<String, Object> evaluationSuccessFields() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("credentialSource", "stored");
    fields.put("suite", "OCRA-1:HOTP-SHA1-6:QN08");
    fields.put("hasClientChallenge", Boolean.TRUE);
    fields.put("hasServerChallenge", Boolean.FALSE);
    fields.put("hasPin", Boolean.FALSE);
    fields.put("hasTimestamp", Boolean.TRUE);
    fields.put("durationMillis", 12L);
    return fields;
  }

  public static Map<String, Object> evaluationValidationFields() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("field", "suite");
    fields.put("reason", "suite is required");
    fields.put("hasClientChallenge", Boolean.FALSE);
    fields.put("hasServerChallenge", Boolean.FALSE);
    fields.put("hasPin", Boolean.FALSE);
    fields.put("hasTimestamp", Boolean.FALSE);
    return fields;
  }

  public static Map<String, Object> evaluationErrorFields() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("reason", "Unexpected failure during evaluation");
    fields.put("exception", "java.lang.IllegalStateException: boom");
    fields.put("hasClientChallenge", Boolean.TRUE);
    fields.put("hasServerChallenge", Boolean.TRUE);
    fields.put("hasPin", Boolean.FALSE);
    fields.put("hasTimestamp", Boolean.TRUE);
    return fields;
  }

  public static void assertEvaluationSuccessFrame(TelemetryFrame frame) {
    assertFrame(
        frame, OCRA_EVALUATION_EVENT, "success", "success", true, evaluationSuccessFields());
  }

  public static void assertEvaluationValidationFrame(TelemetryFrame frame, boolean sanitized) {
    assertFrame(
        frame,
        OCRA_EVALUATION_EVENT,
        "invalid",
        "validation_error",
        sanitized,
        evaluationValidationFields());
  }

  public static void assertEvaluationErrorFrame(TelemetryFrame frame) {
    assertFrame(
        frame, OCRA_EVALUATION_EVENT, "error", "unexpected_error", false, evaluationErrorFields());
  }

  public static Map<String, Object> hotpEvaluationSuccessFields() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("credentialSource", "stored");
    fields.put("credentialId", "hotp-credential");
    fields.put("hashAlgorithm", "SHA1");
    fields.put("digits", 6);
    fields.put("previousCounter", 0L);
    fields.put("nextCounter", 1L);
    return fields;
  }

  public static Map<String, Object> hotpEvaluationValidationFields() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("credentialSource", "stored");
    fields.put("credentialId", "hotp-credential");
    fields.put("hashAlgorithm", "SHA1");
    fields.put("digits", 6);
    fields.put("previousCounter", 0L);
    fields.put("nextCounter", 0L);
    fields.put("reason", "OTP mismatch");
    return fields;
  }

  public static Map<String, Object> hotpEvaluationErrorFields() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("credentialSource", "stored");
    fields.put("credentialId", "hotp-credential");
    fields.put("hashAlgorithm", "SHA1");
    fields.put("digits", 6);
    fields.put("previousCounter", 0L);
    fields.put("exception", "java.lang.IllegalStateException: boom");
    return fields;
  }

  public static Map<String, Object> hotpIssuanceSuccessFields() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("credentialId", "hotp-credential");
    fields.put("hashAlgorithm", "SHA1");
    fields.put("digits", 6);
    fields.put("initialCounter", 0L);
    fields.put("metadataSize", 1);
    fields.put("created", Boolean.TRUE);
    return fields;
  }

  public static void assertHotpEvaluationSuccessFrame(TelemetryFrame frame) {
    assertFrame(
        frame, HOTP_EVALUATION_EVENT, "success", "match", true, hotpEvaluationSuccessFields());
  }

  public static void assertHotpEvaluationValidationFrame(TelemetryFrame frame, boolean sanitized) {
    assertFrame(
        frame,
        HOTP_EVALUATION_EVENT,
        "invalid",
        "otp_mismatch",
        sanitized,
        hotpEvaluationValidationFields());
  }

  public static void assertHotpEvaluationErrorFrame(TelemetryFrame frame) {
    assertFrame(
        frame,
        HOTP_EVALUATION_EVENT,
        "error",
        "unexpected_error",
        false,
        hotpEvaluationErrorFields());
  }

  public static void assertHotpIssuanceSuccessFrame(TelemetryFrame frame) {
    assertFrame(frame, HOTP_ISSUANCE_EVENT, "issued", "issued", true, hotpIssuanceSuccessFields());
  }

  public static void assertHotpReplaySuccessFrame(
      TelemetryFrame frame, String credentialSource, long counter) {
    assertFrame(
        frame,
        HOTP_REPLAY_EVENT,
        "success",
        "match",
        true,
        hotpReplayFields(credentialSource, counter, null));
  }

  public static void assertHotpReplayValidationFrame(
      TelemetryFrame frame, boolean sanitized, String credentialSource, long counter) {
    assertFrame(
        frame,
        HOTP_REPLAY_EVENT,
        "invalid",
        "otp_mismatch",
        sanitized,
        hotpReplayFields(credentialSource, counter, "OTP mismatch"));
  }

  public static void assertHotpReplayErrorFrame(
      TelemetryFrame frame, String credentialSource, long counter) {
    assertFrame(
        frame,
        HOTP_REPLAY_EVENT,
        "error",
        "unexpected_error",
        false,
        hotpReplayFields(credentialSource, counter, "java.lang.IllegalStateException: boom"));
  }

  public static void assertTotpReplaySuccessFrame(
      TelemetryFrame frame, String credentialSource, int matchedSkewSteps) {
    assertFrame(
        frame,
        TOTP_REPLAY_EVENT,
        "success",
        "match",
        true,
        totpReplaySuccessFields(credentialSource, matchedSkewSteps));
  }

  public static void assertTotpReplayValidationFrame(
      TelemetryFrame frame, String credentialSource) {
    assertFrame(
        frame,
        TOTP_REPLAY_EVENT,
        "invalid",
        "otp_out_of_window",
        true,
        totpReplayValidationFields(credentialSource));
  }

  public static Map<String, Object> hotpSeedFields() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("addedCount", 3);
    fields.put("canonicalCount", 3);
    fields.put("existingCount", 0);
    fields.put("trigger", "ui");
    fields.put(
        "addedCredentialIds",
        List.of("ui-hotp-demo", "ui-hotp-demo-sha256", "ui-hotp-demo-sha512"));
    return fields;
  }

  public static void assertHotpSeedFrame(TelemetryFrame frame) {
    assertFrame(frame, HOTP_SEED_EVENT, "seeded", "seeded", true, hotpSeedFields());
  }

  public static Map<String, Object> totpSeedFields() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("addedCount", 2);
    fields.put("canonicalCount", 2);
    fields.put("existingCount", 0);
    fields.put("trigger", "ui");
    fields.put("addedCredentialIds", List.of("ui-totp-demo", "ui-totp-demo-sha512"));
    fields.put("algorithms", List.of("SHA1", "SHA512"));
    fields.put("stepSeconds", List.of(30L, 60L));
    fields.put("digits", List.of(6, 8));
    return fields;
  }

  public static void assertTotpSeedFrame(TelemetryFrame frame) {
    assertFrame(frame, TOTP_SEED_EVENT, "seeded", "seeded", true, totpSeedFields());
  }

  public static Map<String, Object> totpSampleFields() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("credentialId", "totp-sample-credential");
    fields.put("algorithm", "SHA1");
    fields.put("digits", 6);
    fields.put("stepSeconds", 30L);
    fields.put("driftBackwardSteps", 1);
    fields.put("driftForwardSteps", 1);
    fields.put("timestampEpochSeconds", 1_111_111_111L);
    return fields;
  }

  public static void assertTotpSampleFrame(TelemetryFrame frame) {
    assertFrame(frame, TOTP_SAMPLE_EVENT, "sampled", "sampled", true, totpSampleFields());
  }

  private static Map<String, Object> hotpReplayFields(
      String credentialSource, long counter, String reason) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("credentialSource", credentialSource);
    fields.put(
        "credentialId",
        "inline".equals(credentialSource) ? HOTP_INLINE_REPLAY_ID : "hotp-credential");
    fields.put("hashAlgorithm", "SHA1");
    fields.put("digits", 6);
    fields.put("previousCounter", counter);
    fields.put("nextCounter", counter);
    if (reason != null) {
      fields.put("reason", reason);
    }
    return fields;
  }

  private static Map<String, Object> totpReplaySuccessFields(
      String credentialSource, int matchedSkewSteps) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("credentialSource", credentialSource);
    fields.put("credentialReference", true);
    fields.put("credentialId", "totp-replay-credential");
    fields.put("algorithm", "SHA1");
    fields.put("digits", 6);
    fields.put("stepSeconds", 30L);
    fields.put("driftBackwardSteps", 1);
    fields.put("driftForwardSteps", 1);
    fields.put("timestampOverrideProvided", false);
    fields.put("matchedSkewSteps", matchedSkewSteps);
    return fields;
  }

  private static Map<String, Object> totpReplayValidationFields(String credentialSource) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("credentialSource", credentialSource);
    fields.put("credentialReference", false);
    fields.put("algorithm", "SHA512");
    fields.put("digits", 8);
    fields.put("stepSeconds", 60L);
    fields.put("driftBackwardSteps", 0);
    fields.put("driftForwardSteps", 0);
    fields.put("timestampOverrideProvided", true);
    fields.put("matchedSkewSteps", Integer.MIN_VALUE);
    return fields;
  }

  private static void assertFrame(
      TelemetryFrame frame,
      String expectedEvent,
      String expectedStatus,
      String expectedReasonCode,
      boolean expectedSanitized,
      Map<String, Object> expectedFields) {
    assertNotNull(frame, "Telemetry frame should not be null");

    assertEquals(expectedEvent, frame.event(), "Unexpected telemetry event");
    assertEquals(expectedStatus, frame.status(), "Unexpected telemetry status");
    assertEquals(expectedSanitized, frame.sanitized(), "Unexpected sanitized flag");

    Map<String, Object> actualFields = frame.fields();
    assertNotNull(actualFields, "Telemetry fields must not be null");
    assertEquals(
        telemetryId(),
        actualFields.get("telemetryId"),
        "Telemetry frame should include telemetryId");
    assertEquals(
        expectedReasonCode,
        actualFields.get("reasonCode"),
        "Telemetry frame should expose reasonCode");
    for (Map.Entry<String, Object> entry : expectedFields.entrySet()) {
      assertEquals(
          entry.getValue(),
          actualFields.get(entry.getKey()),
          () -> "Field mismatch for " + entry.getKey());
    }
  }
}
