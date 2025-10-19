package io.openauth.sim.cli;

import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.store.MapDbCredentialStore;
import io.openauth.sim.core.store.MapDbCredentialStore.MaintenanceBundle;
import io.openauth.sim.core.store.MapDbCredentialStore.MaintenanceHelper;
import io.openauth.sim.core.store.MapDbCredentialStore.MaintenanceOperation;
import io.openauth.sim.core.store.MapDbCredentialStore.MaintenanceResult;
import io.openauth.sim.core.store.MapDbCredentialStore.MaintenanceStatus;
import io.openauth.sim.core.store.ocra.OcraStoreMigrations;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
    if (args == null || args.length == 0) {
      err.println(usage());
      return 1;
    }

    String command = args[0].toLowerCase(Locale.ROOT);

    if ("ocra".equals(command)) {
      OcraArguments ocraArguments = parseOcraArguments(args, err);
      if (ocraArguments == null) {
        return 1;
      }
      return runOcra(ocraArguments, out, err);
    }

    ParsedArguments parsed = parseMaintenanceArguments(args, err);
    if (parsed == null || !parsed.valid()) {
      return 1;
    }

    Path databasePath = parsed.databasePath();
    if (databasePath == null) {
      err.println("error: --database=<path> is required");
      err.println(usage());
      return 1;
    }

    MapDbCredentialStore.Builder builder =
        OcraStoreMigrations.apply(MapDbCredentialStore.file(databasePath));

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

  ParsedArguments parseMaintenanceArguments(String[] args, PrintStream err) {
    String command = args[0].toLowerCase(Locale.ROOT);
    MaintenanceOperation operation;
    if ("compact".equals(command)) {
      operation = MaintenanceOperation.COMPACTION;
    } else if ("verify".equals(command) || "integrity".equals(command)) {
      operation = MaintenanceOperation.INTEGRITY_CHECK;
    } else {
      err.printf(Locale.ROOT, "error: unknown command '%s'%n", args[0]);
      err.println(usage());
      return ParsedArguments.invalid();
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

  private int runOcra(OcraArguments arguments, PrintStream out, PrintStream err) {
    try {
      OcraCredentialFactory factory = new OcraCredentialFactory();
      String name = "cli-ocra-" + Integer.toHexString(arguments.suite().hashCode());
      OcraCredentialDescriptor descriptor =
          factory.createDescriptor(
              new OcraCredentialRequest(
                  name,
                  arguments.suite(),
                  arguments.sharedSecretHex(),
                  SecretEncoding.HEX,
                  arguments.counter().orElse(null),
                  arguments.pinHashHex().orElse(null),
                  null,
                  Map.of("source", "cli-ocra")));

      OcraResponseCalculator.OcraExecutionContext context =
          new OcraResponseCalculator.OcraExecutionContext(
              arguments.counter().orElse(null),
              arguments.challenge().orElse(null),
              arguments.sessionInformation().orElse(null),
              arguments.clientChallenge().orElse(null),
              arguments.serverChallenge().orElse(null),
              arguments.pinHashHex().orElse(null),
              arguments.timestampHex().orElse(null));

      String otp = OcraResponseCalculator.generate(descriptor, context);
      out.printf(Locale.ROOT, "suite=%s otp=%s%n", arguments.suite(), otp);
      return 0;
    } catch (IllegalArgumentException ex) {
      err.println("error: " + ex.getMessage());
      return 1;
    } catch (Exception ex) {
      err.println("error: ocra command failed - " + ex.getMessage());
      return 1;
    }
  }

  OcraArguments parseOcraArguments(String[] args, PrintStream err) {
    String suite = null;
    String key = null;
    String challenge = null;
    String session = null;
    String client = null;
    String server = null;
    String pin = null;
    String timestamp = null;
    Long counter = null;

    for (int i = 1; i < args.length; i++) {
      String arg = args[i];
      if (arg.startsWith("--suite=")) {
        suite = arg.substring("--suite=".length()).trim();
      } else if (arg.startsWith("--key=")) {
        key = arg.substring("--key=".length()).trim();
      } else if (arg.startsWith("--challenge=")) {
        challenge = arg.substring("--challenge=".length()).trim();
      } else if (arg.startsWith("--session=")) {
        session = arg.substring("--session=".length()).trim();
      } else if (arg.startsWith("--client=")) {
        client = arg.substring("--client=".length()).trim();
      } else if (arg.startsWith("--server=")) {
        server = arg.substring("--server=".length()).trim();
      } else if (arg.startsWith("--pin=")) {
        pin = arg.substring("--pin=".length()).trim();
      } else if (arg.startsWith("--timestamp=")) {
        timestamp = arg.substring("--timestamp=".length()).trim();
      } else if (arg.startsWith("--counter=")) {
        String raw = arg.substring("--counter=".length()).trim();
        try {
          counter = Long.parseLong(raw);
        } catch (NumberFormatException nfe) {
          err.println("error: counter must be a long value");
          return null;
        }
      } else if ("--help".equals(arg) || "-h".equals(arg)) {
        err.println(usage());
        return null;
      } else {
        err.printf(Locale.ROOT, "error: unrecognised option '%s'%n", arg);
        err.println(usage());
        return null;
      }
    }

    if (suite == null || suite.isBlank()) {
      err.println("error: --suite=<ocra-suite> is required");
      return null;
    }
    if (key == null || key.isBlank()) {
      err.println("error: --key=<hex-shared-secret> is required");
      return null;
    }

    return new OcraArguments(
        suite.trim(),
        key.replace(" ", "").trim(),
        optionalTrimmed(challenge),
        optionalTrimmed(session),
        optionalTrimmed(client),
        optionalTrimmed(server),
        optionalTrimmed(pin),
        optionalTrimmed(timestamp),
        Optional.ofNullable(counter));
  }

  private static Optional<String> optionalTrimmed(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(value.trim());
  }

  private String usage() {
    return "usage: maintenance <compact|verify> --database=<path> | ocra --suite=<suite> --key=<hex> [--challenge=...] [--session=...] [--counter=...] [--client=...] [--server=...] [--pin=...] [--timestamp=...]";
  }

  static record ParsedArguments(MaintenanceOperation operation, Path databasePath, boolean valid) {

    static ParsedArguments invalid() {
      return new ParsedArguments(null, null, false);
    }
  }

  static record OcraArguments(
      String suite,
      String sharedSecretHex,
      Optional<String> challenge,
      Optional<String> sessionInformation,
      Optional<String> clientChallenge,
      Optional<String> serverChallenge,
      Optional<String> pinHashHex,
      Optional<String> timestampHex,
      Optional<Long> counter) {
    // Marker record – no additional members.
  }
}
