package io.openauth.sim.application.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared telemetry assertions used across facade contract tests. Intentionally red to drive the
 * shared telemetry adapter implementation.
 */
final class TelemetryContractTestSupport {

  private static final String EVALUATION_EVENT = "ocra.evaluate";

  private TelemetryContractTestSupport() {
    throw new AssertionError("No instances");
  }

  static String telemetryId() {
    return "telemetry-042";
  }

  static Map<String, Object> evaluationSuccessFields() {
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

  static Map<String, Object> evaluationValidationFields() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("field", "suite");
    fields.put("reason", "suite is required");
    fields.put("hasClientChallenge", Boolean.FALSE);
    fields.put("hasServerChallenge", Boolean.FALSE);
    fields.put("hasPin", Boolean.FALSE);
    fields.put("hasTimestamp", Boolean.FALSE);
    return fields;
  }

  static Map<String, Object> evaluationErrorFields() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("reason", "Unexpected failure during evaluation");
    fields.put("exception", "java.lang.IllegalStateException: boom");
    fields.put("hasClientChallenge", Boolean.TRUE);
    fields.put("hasServerChallenge", Boolean.TRUE);
    fields.put("hasPin", Boolean.FALSE);
    fields.put("hasTimestamp", Boolean.TRUE);
    return fields;
  }

  static void assertEvaluationSuccessFrame(Object frame) {
    assertFrame(frame, EVALUATION_EVENT, "success", "success", true, evaluationSuccessFields());
  }

  static void assertEvaluationValidationFrame(Object frame, boolean sanitized) {
    assertFrame(
        frame,
        EVALUATION_EVENT,
        "invalid",
        "validation_error",
        sanitized,
        evaluationValidationFields());
  }

  static void assertEvaluationErrorFrame(Object frame) {
    assertFrame(
        frame, EVALUATION_EVENT, "error", "unexpected_error", false, evaluationErrorFields());
  }

  @SuppressWarnings("unchecked")
  private static void assertFrame(
      Object frame,
      String expectedEvent,
      String expectedStatus,
      String expectedReasonCode,
      boolean expectedSanitized,
      Map<String, Object> expectedFields) {
    assertNotNull(frame, "Telemetry frame should not be null");

    Class<?> frameType = frame.getClass();

    String event = (String) invoke(frame, frameType, "event");
    assertEquals(expectedEvent, event, "Unexpected telemetry event");

    String status = (String) invoke(frame, frameType, "status");
    assertEquals(expectedStatus, status, "Unexpected telemetry status");

    Boolean sanitized = (Boolean) invoke(frame, frameType, "sanitized");
    assertEquals(expectedSanitized, sanitized, "Unexpected sanitized flag");

    Object fieldsObject = invoke(frame, frameType, "fields");
    assertNotNull(fieldsObject, "Telemetry fields must not be null");
    Map<String, Object> actualFields = (Map<String, Object>) fieldsObject;
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

  private static Object invoke(Object target, Class<?> type, String methodName) {
    try {
      Method method = type.getMethod(methodName);
      return method.invoke(target);
    } catch (NoSuchMethodException | IllegalAccessException ex) {
      throw new AssertionError(
          "Telemetry contract is missing method '" + methodName + "' on " + type.getName(), ex);
    } catch (InvocationTargetException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new AssertionError("Telemetry frame accessor failed", cause);
    }
  }
}
