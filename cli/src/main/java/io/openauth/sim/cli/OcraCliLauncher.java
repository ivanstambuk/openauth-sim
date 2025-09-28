package io.openauth.sim.cli;

import picocli.CommandLine;

/** Entry point wiring {@link OcraCli} into a standalone executable. */
public final class OcraCliLauncher {

  private OcraCliLauncher() {
    // Utility class
  }

  public static void main(String[] args) {
    int exitCode = new CommandLine(new OcraCli()).execute(args);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }
}
