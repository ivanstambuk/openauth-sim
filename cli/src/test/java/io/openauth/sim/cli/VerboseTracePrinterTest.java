package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.trace.VerboseTrace;
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
                .withMetadata("mode", "stored")
                .addStep(step -> step.id("decode.payload")
                        .summary("Decode payload")
                        .detail("TotpReplayApplicationService")
                        .attribute("bytes", new byte[] {0x00, (byte) 0xFF})
                        .attribute("emptyBytes", new byte[0])
                        .attribute("numbers", new int[] {1, 2, 3})
                        .note("hint", "verify"))
                .build();

        StringWriter buffer = new StringWriter();
        PrintWriter writer = new PrintWriter(buffer);

        VerboseTracePrinter.print(writer, trace);

        String output = buffer.toString();
        assertTrue(output.contains("=== Verbose Trace ==="));
        assertTrue(output.contains("operation=totp.replay.stored"));
        assertTrue(output.contains("metadata.mode=stored"));
        assertTrue(output.contains("step.1.id=decode.payload"));
        assertTrue(output.contains("step.1.summary=Decode payload"));
        assertTrue(output.contains("step.1.detail=TotpReplayApplicationService"));
        assertTrue(output.contains("step.1.attr.bytes=00ff"));
        assertTrue(output.contains("step.1.attr.emptyBytes="));
        assertTrue(output.contains("step.1.attr.numbers="));
        assertTrue(output.contains("step.1.note.hint=verify"));
        assertTrue(output.contains("=== End Verbose Trace ==="));
    }
}
