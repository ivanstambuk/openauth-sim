package io.openauth.sim.tools.mcp.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

public final class ContentLengthMessageFramer {
    private final InputStream input;
    private final OutputStream output;

    public ContentLengthMessageFramer(InputStream input, OutputStream output) {
        this.input = input;
        this.output = output;
    }

    public Optional<String> read() throws IOException {
        int contentLength = -1;
        while (true) {
            String header = readLine();
            if (header == null) {
                return Optional.empty();
            }
            if (header.isEmpty()) {
                break;
            }
            String lower = header.toLowerCase(Locale.ROOT);
            if (lower.startsWith("content-length:")) {
                String value = header.substring("content-length:".length()).trim();
                contentLength = Integer.parseInt(value);
            }
        }
        if (contentLength < 0) {
            throw new IOException("Missing Content-Length header");
        }
        byte[] payload = input.readNBytes(contentLength);
        if (payload.length < contentLength) {
            throw new IOException("Unexpected end of stream while reading JSON body");
        }
        return Optional.of(new String(payload, StandardCharsets.UTF_8));
    }

    public synchronized void write(String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        String header = "Content-Length: " + bytes.length + "\r\n\r\n";
        output.write(header.getBytes(StandardCharsets.US_ASCII));
        output.write(bytes);
        output.flush();
    }

    private String readLine() throws IOException {
        StringBuilder builder = new StringBuilder();
        while (true) {
            int b = input.read();
            if (b == -1) {
                if (builder.length() == 0) {
                    return null;
                }
                throw new IOException("Unexpected end of stream whilst reading header");
            }
            if (b == '\r') {
                int next = input.read();
                if (next != '\n') {
                    throw new IOException("Expected LF after CR in header");
                }
                return builder.toString();
            }
            builder.append((char) b);
        }
    }
}
