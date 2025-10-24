package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.trace.VerboseTrace;
import io.openauth.sim.core.trace.VerboseTrace.AttributeType;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class VerboseTracePrinterTest {

    @Test
    @DisplayName("print skips rendering when trace is null")
    void printSkipsWhenTraceNull() {
        StringWriter buffer = new StringWriter();
        PrintWriter writer = new PrintWriter(buffer);
        writer.print("baseline");

        VerboseTracePrinter.print(writer, null);

        writer.flush();
        assertEquals("baseline", buffer.toString());
    }

    @Test
    @DisplayName("print renders byte arrays, empty arrays, and primitive arrays consistently")
    void printRendersArrays() {
        VerboseTrace trace = VerboseTrace.builder("totp.replay.stored")
                .withTier(VerboseTrace.Tier.NORMAL)
                .withMetadata("mode", "stored")
                .addStep(step -> step.id("decode.payload")
                        .summary("Decode payload")
                        .detail("TotpReplayApplicationService")
                        .attribute(AttributeType.HEX, "bytes", new byte[] {0x00, (byte) 0xFF})
                        .attribute(AttributeType.HEX, "emptyBytes", new byte[0])
                        .attribute(AttributeType.STRING, "numbers", new int[] {1, 2, 3})
                        .spec("totp-replay")
                        .note("hint", "verify"))
                .build();

        StringWriter buffer = new StringWriter();
        PrintWriter writer = new PrintWriter(buffer);

        VerboseTracePrinter.print(writer, trace);

        String output = buffer.toString();
        assertTrue(output.contains("=== Verbose Trace ==="));
        assertTrue(output.contains("operation=totp.replay.stored"));
        assertTrue(output.contains("metadata.mode=stored"));
        assertTrue(output.contains("metadata.tier=normal"));
        assertTrue(output.contains("step.1: decode.payload"));
        assertTrue(output.contains("  summary = Decode payload"));
        assertTrue(output.contains("  detail = TotpReplayApplicationService"));
        assertTrue(output.contains("  spec = totp-replay"));
        assertTrue(output.contains("  bytes = 00ff"));
        assertTrue(output.contains("  emptyBytes ="));
        assertTrue(output.contains("  numbers ="));
        assertTrue(output.contains("  note.hint = verify"));
        assertTrue(output.contains("=== End Verbose Trace ==="));
    }
}
