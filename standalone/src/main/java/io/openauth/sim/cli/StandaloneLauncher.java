package io.openauth.sim.cli;

import java.util.Arrays;

/**
 * Standalone entry point for the aggregated fat JAR.
 *
 * <p>This launcher preserves the existing maintenance/OCRA helper behaviour while exposing the
 * full FIDO2 Picocli facade under the {@code fido2} subcommand:
 *
 * <pre>
 *   java -jar openauth-sim-standalone-&lt;version&gt;.jar fido2 --help
 *   java -jar openauth-sim-standalone-&lt;version&gt;.jar fido2 evaluate --preset-id packed-es256
 * </pre>
 *
 * Other arguments are delegated to {@link MaintenanceCli} to keep MapDB maintenance and the
 * lightweight OCRA generator available for existing workflows.
 */
public final class StandaloneLauncher {

    private StandaloneLauncher() {
        // Utility class
    }

    public static void main(String[] args) {
        int exitCode = execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int execute(String... args) {
        if (args != null && args.length > 0) {
            if ("fido2".equals(args[0])) {
                String[] fidoArgs = Arrays.copyOfRange(args, 1, args.length);
                return new picocli.CommandLine(new Fido2Cli()).execute(fidoArgs);
            }
            if ("ocra".equals(args[0])) {
                String[] ocraArgs = Arrays.copyOfRange(args, 1, args.length);
                return new picocli.CommandLine(new OcraCli()).execute(ocraArgs);
            }
        }

        // Fallback to existing maintenance/ocra behaviour for all other invocations.
        MaintenanceCli maintenanceCli = new MaintenanceCli();
        return maintenanceCli.run(args, System.out, System.err);
    }
}
