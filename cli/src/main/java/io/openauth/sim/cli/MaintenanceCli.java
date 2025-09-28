package io.openauth.sim.cli;

import io.openauth.sim.core.store.MapDbCredentialStore;
import io.openauth.sim.core.store.MapDbCredentialStore.MaintenanceBundle;
import io.openauth.sim.core.store.MapDbCredentialStore.MaintenanceHelper;
import io.openauth.sim.core.store.MapDbCredentialStore.MaintenanceOperation;
import io.openauth.sim.core.store.MapDbCredentialStore.MaintenanceResult;
import io.openauth.sim.core.store.MapDbCredentialStore.MaintenanceStatus;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Simple CLI wrapper for MapDB maintenance operations. Supports {@code compact} and {@code verify}
 * commands against a MapDB file database.
 */
public final class MaintenanceCli {

  public static void main(String[] args) {
    MaintenanceCli cli = new MaintenanceCli();
    int exitCode = cli.run(args, System.out, System.err);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  /** Execute the CLI using the provided streams. Visible for testing. */
  int run(String[] args, PrintStream out, PrintStream err) {
    ParsedArguments parsed = parseArguments(args, err);
    if (parsed == null || !parsed.valid()) {
      return 1;
    }

    Path databasePath = parsed.databasePath();
    if (databasePath == null) {
      err.println("error: --database=<path> is required");
      err.println(usage());
      return 1;
    }

    MapDbCredentialStore.Builder builder = MapDbCredentialStore.file(databasePath);

    try {
      Path parent = databasePath.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
    } catch (Exception ioe) {
      err.println("error: unable to prepare database directory - " + ioe.getMessage());
      return 1;
    }

    MaintenanceResult result;
    try (MaintenanceBundle bundle = builder.openWithMaintenance()) {
      MaintenanceHelper helper = bundle.maintenance();
      if (parsed.operation() == MaintenanceOperation.COMPACTION) {
        result = helper.compact();
      } else {
        result = helper.verifyIntegrity();
      }
    } catch (Exception ex) {
      err.println("error: maintenance command failed - " + ex.getMessage());
      return 1;
    }

    long durationMicros = TimeUnit.NANOSECONDS.toMicros(result.duration().toNanos());
    out.printf(
        Locale.ROOT,
        "operation=%s status=%s durationMicros=%d entriesScanned=%d entriesRepaired=%d issues=%d%n",
        result.operation(),
        result.status(),
        durationMicros,
        result.entriesScanned(),
        result.entriesRepaired(),
        result.issues().size());
    if (!result.issues().isEmpty()) {
      result.issues().forEach(issue -> out.printf(Locale.ROOT, "issue=%s%n", issue));
    }

    return result.status() == MaintenanceStatus.FAIL ? 2 : 0;
  }

  private ParsedArguments parseArguments(String[] args, PrintStream err) {
    if (args == null || args.length == 0) {
      err.println("usage: maintenance <compact|verify> --database=<path>");
      return ParsedArguments.invalid();
    }

    MaintenanceOperation operation;
    String command = args[0].toLowerCase(Locale.ROOT);
    switch (command) {
      case "compact" -> operation = MaintenanceOperation.COMPACTION;
      case "verify", "integrity" -> operation = MaintenanceOperation.INTEGRITY_CHECK;
      default -> {
        err.printf(Locale.ROOT, "error: unknown command '%s'%n", args[0]);
        err.println(usage());
        return ParsedArguments.invalid();
      }
    }

    Path databasePath = null;
    for (int i = 1; i < args.length; i++) {
      String arg = args[i];
      if (arg.startsWith("--database=")) {
        databasePath = Paths.get(arg.substring("--database=".length()).trim());
      } else if (arg.startsWith("-d=")) {
        databasePath = Paths.get(arg.substring(3).trim());
      } else if ("--help".equals(arg) || "-h".equals(arg)) {
        err.println(usage());
        return ParsedArguments.invalid();
      } else {
        err.printf(Locale.ROOT, "error: unrecognised option '%s'%n", arg);
        err.println(usage());
        return ParsedArguments.invalid();
      }
    }

    return new ParsedArguments(operation, databasePath, true);
  }

  private String usage() {
    return "usage: maintenance <compact|verify> --database=<path>";
  }

  private record ParsedArguments(MaintenanceOperation operation, Path databasePath, boolean valid) {

    static ParsedArguments invalid() {
      return new ParsedArguments(null, null, false);
    }
  }
}
