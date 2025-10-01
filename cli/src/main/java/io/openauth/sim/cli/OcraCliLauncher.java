package io.openauth.sim.cli;

import picocli.CommandLine;

/** Entry point wiring {@link OcraCli} into a standalone executable. */
public final class OcraCliLauncher {

  private OcraCliLauncher() {
    // Utility class
  }

  public static void main(String[] args) {
    int exitCode = execute(args);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  /** Execute the CLI and return the resulting exit code (visible for tests). */
  static int execute(String... args) {
    return commandLine().execute(args);
  }

  /** Build a fresh {@link CommandLine} instance for integration or usage tests. */
  static CommandLine commandLine() {
    return new CommandLine(new OcraCli());
  }
}
