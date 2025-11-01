package io.openauth.sim.cli;

import io.openauth.sim.application.preview.OtpPreview;
import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Utility for rendering OTP preview rows in a compact table suitable for CLI output.
 */
final class OtpPreviewTableFormatter {

    private static final String HEADER_COUNTER = "Counter";
    private static final String HEADER_DELTA = "Δ";
    private static final String HEADER_OTP = "OTP";

    private OtpPreviewTableFormatter() {
        // Utility class
    }

    static void print(PrintWriter writer, List<OtpPreview> previews) {
        Objects.requireNonNull(writer, "writer");
        if (previews == null || previews.isEmpty()) {
            return;
        }
        List<OtpPreview> entries = previews.stream().filter(Objects::nonNull).collect(Collectors.toList());
        if (entries.isEmpty()) {
            return;
        }

        int counterWidth = Math.max(
                HEADER_COUNTER.length(),
                entries.stream()
                        .map(OtpPreviewTableFormatter::counterLabel)
                        .mapToInt(String::length)
                        .max()
                        .orElse(0));
        int deltaWidth = Math.max(
                HEADER_DELTA.length(),
                entries.stream()
                        .map(OtpPreviewTableFormatter::deltaLabel)
                        .mapToInt(String::length)
                        .max()
                        .orElse(0));

        writer.println("Preview window:");
        writer.printf(
                Locale.ROOT,
                " %-" + counterWidth + "s %-" + deltaWidth + "s %s%n",
                HEADER_COUNTER,
                HEADER_DELTA,
                HEADER_OTP);
        for (OtpPreview entry : entries) {
            String marker = entry.delta() == 0 ? ">" : " ";
            writer.printf(
                    Locale.ROOT,
                    "%s %-" + counterWidth + "s %-" + deltaWidth + "s %s%n",
                    marker,
                    counterLabel(entry),
                    deltaLabel(entry),
                    entry.otp());
        }
        writer.flush();
    }

    private static String counterLabel(OtpPreview preview) {
        String counter = preview.counter();
        if (counter == null || counter.isBlank()) {
            return "-";
        }
        return counter;
    }

    private static String deltaLabel(OtpPreview preview) {
        int delta = preview.delta();
        if (delta == 0) {
            return "[0]";
        }
        return String.format(Locale.ROOT, "%+d", delta);
    }
}
