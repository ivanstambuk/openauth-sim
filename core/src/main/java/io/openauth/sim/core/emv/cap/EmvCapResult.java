package io.openauth.sim.core.emv.cap;

import java.util.Locale;
import java.util.Objects;

/** Domain response containing all EMV/CAP derivation artefacts. */
public record EmvCapResult(
        String sessionKeyHex,
        GenerateAcInput generateAcInput,
        String generateAcResultHex,
        String bitmaskOverlay,
        String maskedDigitsOverlay,
        Otp otp) {

    public EmvCapResult {
        sessionKeyHex = normalizeHex(sessionKeyHex, "sessionKey");
        generateAcInput = Objects.requireNonNull(generateAcInput, "generateAcInput");
        generateAcResultHex = normalizeHex(generateAcResultHex, "generateAcResult");
        bitmaskOverlay = Objects.requireNonNull(bitmaskOverlay, "bitmaskOverlay");
        maskedDigitsOverlay = Objects.requireNonNull(maskedDigitsOverlay, "maskedDigitsOverlay");
        otp = Objects.requireNonNull(otp, "otp");
    }

    private static String normalizeHex(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ((normalized.length() & 1) == 1) {
            throw new IllegalArgumentException(field + " must contain an even number of hex characters");
        }
        if (!normalized.matches("[0-9A-F]+")) {
            throw new IllegalArgumentException(field + " must be hexadecimal");
        }
        return normalized;
    }

    /** Terminal and ICC payload inputs fed into {@code GENERATE AC}. */
    public record GenerateAcInput(String terminalHex, String iccHex) {

        public GenerateAcInput {
            terminalHex = normalizeHex(terminalHex, "terminal");
            iccHex = normalizeHex(iccHex, "icc");
        }
    }

    /** OTP payload derived from the issuer proprietary bitmask. */
    public record Otp(String decimal, String hex) {

        public Otp {
            decimal = decimal == null ? "" : decimal.trim();
            hex = normalizeLenientHex(hex, "otp.hex");
        }
    }

    private static String normalizeLenientHex(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[0-9A-F]+")) {
            throw new IllegalArgumentException(field + " must be hexadecimal");
        }
        return normalized;
    }
}
