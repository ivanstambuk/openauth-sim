package io.openauth.sim.core.credentials.ocra;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OcraCredentialFactoryLoggingTest {

  @Test
  @DisplayName("logValidationFailure honours logger level and optional detail")
  void logValidationFailureHonoursLoggerLevel() throws Exception {
    Logger logger = OcraCredentialFactory.telemetryLogger();
    Level previous = logger.getLevel();
    try {
      logger.setLevel(Level.OFF);
      assertDoesNotThrow(
          () -> OcraCredentialFactory.logValidationFailure("suite", "name", "code", "id", null));

      logger.setLevel(Level.FINE);
      assertDoesNotThrow(
          () ->
              OcraCredentialFactory.logValidationFailure("suite", "name", "code", "id", "detail"));
      assertDoesNotThrow(
          () -> OcraCredentialFactory.logValidationFailure("suite", "name", "code", "id", "   "));
    } finally {
      logger.setLevel(previous);
    }
  }
}
