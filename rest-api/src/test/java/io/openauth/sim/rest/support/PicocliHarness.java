package io.openauth.sim.rest.support;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import picocli.CommandLine;

public final class PicocliHarness {

    private PicocliHarness() {
        throw new AssertionError("No instances");
    }

    public static ExecutionResult execute(Object command, String... args) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(args, "args");

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        CommandLine cmd = new CommandLine(command);
        cmd.setOut(new PrintWriter(stdout, true, StandardCharsets.UTF_8));
        cmd.setErr(new PrintWriter(stderr, true, StandardCharsets.UTF_8));

        int exitCode = cmd.execute(args);
        return new ExecutionResult(
                exitCode, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
    }

    public record ExecutionResult(int exitCode, String stdout, String stderr) {}
}
