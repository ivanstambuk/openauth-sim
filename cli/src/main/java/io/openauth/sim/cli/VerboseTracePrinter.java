package io.openauth.sim.cli;

import io.openauth.sim.core.trace.VerboseTrace;
import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;

final class VerboseTracePrinter {

    private VerboseTracePrinter() {
        // Utility class.
    }

    static void print(PrintWriter writer, VerboseTrace trace) {
        Objects.requireNonNull(writer, "writer");
        if (trace == null) {
            return;
        }
        writer.println();
        writer.println("=== Verbose Trace ===");
        writer.println("operation=" + trace.operation());
        trace.metadata().forEach((key, value) -> writer.println("metadata." + key + "=" + value));

        List<VerboseTrace.TraceStep> steps = trace.steps();
        for (int index = 0; index < steps.size(); index++) {
            VerboseTrace.TraceStep step = steps.get(index);
            String prefix = "step." + (index + 1);
            writer.println(prefix + ".id=" + step.id());
            if (hasText(step.summary())) {
                writer.println(prefix + ".summary=" + step.summary());
            }
            if (hasText(step.detail())) {
                writer.println(prefix + ".detail=" + step.detail());
            }
            step.attributes().forEach((key, value) -> writer.println(prefix + ".attr." + key + "=" + format(value)));
            step.notes().forEach((key, value) -> writer.println(prefix + ".note." + key + "=" + value));
        }

        writer.println("=== End Verbose Trace ===");
        writer.flush();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String format(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof byte[] bytes) {
            return HexFormatter.of(bytes);
        }
        if (value.getClass().isArray()) {
            return value.toString();
        }
        return String.valueOf(value);
    }

    private static final class HexFormatter {
        private static final char[] HEX = "0123456789abcdef".toCharArray();

        private HexFormatter() {
            // Utility class.
        }

        static String of(byte[] bytes) {
            if (bytes.length == 0) {
                return "";
            }
            char[] chars = new char[bytes.length * 2];
            for (int i = 0; i < bytes.length; i++) {
                int value = bytes[i] & 0xFF;
                chars[i * 2] = HEX[value >>> 4];
                chars[i * 2 + 1] = HEX[value & 0x0F];
            }
            return new String(chars);
        }
    }
}
