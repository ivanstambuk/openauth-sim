package io.openauth.sim.core.credentials.ocra;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OcraCredentialFactoryLoggingTest {

  @Test
  @DisplayName("logValidationFailure honours logger level and optional detail")
  void logValidationFailureHonoursLoggerLevel() throws Exception {
    Logger logger = getTelemetryLogger();
    Level previous = logger.getLevel();
    try {
      logger.setLevel(Level.OFF);
      assertDoesNotThrow(() -> invokeLogValidationFailure("suite", "name", "code", "id", null));

      logger.setLevel(Level.FINE);
      assertDoesNotThrow(() -> invokeLogValidationFailure("suite", "name", "code", "id", "detail"));
      assertDoesNotThrow(() -> invokeLogValidationFailure("suite", "name", "code", "id", "   "));
    } finally {
      logger.setLevel(previous);
    }
  }

  private static Logger getTelemetryLogger() throws NoSuchFieldException, IllegalAccessException {
    Field loggerField = OcraCredentialFactory.class.getDeclaredField("TELEMETRY_LOGGER");
    loggerField.setAccessible(true);
    return (Logger) loggerField.get(null);
  }

  private static void invokeLogValidationFailure(
      String suite, String credentialName, String failureCode, String messageId, String detail)
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Method method =
        OcraCredentialFactory.class.getDeclaredMethod(
            "logValidationFailure",
            String.class,
            String.class,
            String.class,
            String.class,
            String.class);
    method.setAccessible(true);
    method.invoke(null, suite, credentialName, failureCode, messageId, detail);
  }
}
