package io.openauth.sim.tools.mcp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ContentLengthMessageFramerTest {

    @Test
    void roundTripMessage() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ContentLengthMessageFramer writer =
                new ContentLengthMessageFramer(new ByteArrayInputStream(new byte[0]), buffer);
        writer.write("{\"jsonrpc\":\"2.0\"}");

        byte[] frame = buffer.toByteArray();
        ContentLengthMessageFramer reader =
                new ContentLengthMessageFramer(new ByteArrayInputStream(frame), new ByteArrayOutputStream());
        Optional<String> json = reader.read();
        assertTrue(json.isPresent());
        assertEquals("{\"jsonrpc\":\"2.0\"}", json.get());
    }
}
