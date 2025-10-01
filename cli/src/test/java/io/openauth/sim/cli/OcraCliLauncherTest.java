package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

  @Test
  void mainDoesNotExitProcessWhenExitCodeZero() {
    assertDoesNotThrow(() -> OcraCliLauncher.main(new String[] {"--help"}));
  }

  @Test
  void mainExitsProcessWhenExitCodeNonZero() throws Exception {
    List<String> command = new ArrayList<>();
    command.add(javaCommand());
    String agent = jacocoAgentArgument();
    if (agent != null) {
      command.add(agent);
    }
    command.add("-cp");
    command.add(System.getProperty("java.class.path"));
    command.add(OcraCliLauncher.class.getName());
    command.add("import");

    Process process = new ProcessBuilder().command(command).redirectErrorStream(true).start();

    try (InputStream stdout = process.getInputStream()) {
      int status = process.waitFor();
      byte[] bodyBytes = stdout.readAllBytes();
      String body = new String(bodyBytes, StandardCharsets.UTF_8);
      assertEquals(CommandLine.ExitCode.USAGE, status, () -> body);
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
      }
    }
  }

  private static String javaCommand() {
    Path javaBin = Path.of(System.getProperty("java.home"), "bin", "java");
    return javaBin.toString();
  }

  private static String jacocoAgentArgument() {
    return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
        .filter(arg -> arg.startsWith("-javaagent:"))
        .filter(arg -> arg.contains("jacocoagent"))
        .findFirst()
        .orElse(null);
  }
}
