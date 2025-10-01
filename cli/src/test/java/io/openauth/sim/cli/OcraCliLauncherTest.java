package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class OcraCliLauncherTest {

  @Test
  void mainPrintsUsageWithoutExitingWhenExitCodeZero() {
    int exitCode = OcraCliLauncher.execute("--help");
    assertEquals(CommandLine.ExitCode.OK, exitCode);
    String usage = OcraCliLauncher.commandLine().getUsageMessage(CommandLine.Help.Ansi.OFF);
    assertTrue(usage.contains("Usage:"));
  }

  @Test
  void mainInvokesSystemExitForNonZeroCode() {
    int exitCode = OcraCliLauncher.execute("import");
    assertEquals(CommandLine.ExitCode.USAGE, exitCode);
  }
}
