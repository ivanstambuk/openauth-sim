package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class OcraCliLauncherTest {

  @Test
  void mainPrintsUsageWithoutExitingWhenExitCodeZero() {
    PrintStream originalOut = System.out;
    PrintStream originalErr = System.err;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
    try {
      assertDoesNotThrow(() -> OcraCliLauncher.main(new String[] {"--help"}));
    } finally {
      System.setOut(originalOut);
      System.setErr(originalErr);
    }
    String output = out.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("Usage:"));
  }
}
