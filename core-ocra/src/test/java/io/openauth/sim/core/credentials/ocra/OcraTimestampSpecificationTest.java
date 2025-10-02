package io.openauth.sim.core.credentials.ocra;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OcraTimestampSpecificationTest {

  @Test
  @DisplayName("constructor rejects non-positive durations")
  void constructorRejectsNonPositiveDurations() {
    assertThrows(
        IllegalArgumentException.class, () -> new OcraTimestampSpecification(Duration.ZERO));
    assertThrows(
        IllegalArgumentException.class,
        () -> new OcraTimestampSpecification(Duration.ofSeconds(-1)));
  }
}
