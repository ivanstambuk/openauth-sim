package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

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

  @Test
  void mainInvokesSystemExitForNonZeroCode() {
    SecurityManager originalManager = System.getSecurityManager();
    ExitInterceptingSecurityManager manager = new ExitInterceptingSecurityManager();
    System.setSecurityManager(manager);
    try {
      ExitIntercepted intercepted =
          assertThrows(ExitIntercepted.class, () -> OcraCliLauncher.main(new String[] {"import"}));
      assertEquals(CommandLine.ExitCode.USAGE, intercepted.status);
    } finally {
      System.setSecurityManager(originalManager);
    }
  }

  private static final class ExitInterceptingSecurityManager extends SecurityManager {
    @Override
    public void checkPermission(java.security.Permission perm) {
      // allow
    }

    @Override
    public void checkPermission(java.security.Permission perm, Object context) {
      // allow
    }

    @Override
    public void checkExit(int status) {
      throw new ExitIntercepted(status);
    }
  }

  private static final class ExitIntercepted extends SecurityException {
    final int status;

    ExitIntercepted(int status) {
      this.status = status;
    }
  }
}
