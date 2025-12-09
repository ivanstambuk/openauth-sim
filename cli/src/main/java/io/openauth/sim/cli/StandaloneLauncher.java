package io.openauth.sim.cli;

import io.openauth.sim.cli.eudi.openid4vp.EudiwCli;
import picocli.CommandLine;

/**
 * Multi-protocol entry point for the standalone JAR.
 *
 * <p>Registers all authentication protocol CLIs (HOTP, TOTP, OCRA, FIDO2/WebAuthn, EMV/CAP,
 * EUDIW/OpenID4VP) behind a single launcher so users can invoke them via:
 *
 * <pre>{@code
 * java -jar openauth-sim-standalone-<version>.jar --help
 * java -jar openauth-sim-standalone-<version>.jar hotp evaluate ...
 * java -jar openauth-sim-standalone-<version>.jar fido2 evaluate ...
 * }</pre>
 */
@CommandLine.Command(
        name = "openauth-sim",
        mixinStandardHelpOptions = true,
        description = "Authentication simulator CLI (HOTP, TOTP, OCRA, FIDO2/WebAuthn, EMV/CAP, EUDIW)",
        subcommands = {HotpCli.class, TotpCli.class, OcraCli.class, Fido2Cli.class, EmvCli.class, EudiwCli.class})
public final class StandaloneLauncher implements Runnable {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new StandaloneLauncher()).execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    @Override
    public void run() {
        // Show top-level usage when no subcommand is provided.
        new CommandLine(this).usage(System.out);
    }
}
