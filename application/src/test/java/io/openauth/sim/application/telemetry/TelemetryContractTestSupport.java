package io.openauth.sim.application.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared telemetry assertions used across facade contract tests. Intentionally red to drive the
 * shared telemetry adapter implementation.
 */
public final class TelemetryContractTestSupport {

  private static final String OCRA_EVALUATION_EVENT = "ocra.evaluate";
  private static final String HOTP_EVALUATION_EVENT = "hotp.evaluate";
  private static final String HOTP_ISSUANCE_EVENT = "hotp.issue";

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
