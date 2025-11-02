package io.openauth.sim.core.emv.cap;

import io.openauth.sim.core.model.SecretMaterial;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Descriptor capturing the data required to persist an EMV/CAP credential. */
public record EmvCapCredentialDescriptor(
        String name,
        EmvCapMode mode,
        SecretMaterial masterKey,
        String defaultAtcHex,
        int branchFactor,
        int height,
        String ivHex,
        String cdol1Hex,
        String issuerProprietaryBitmapHex,
        String iccDataTemplateHex,
        String issuerApplicationDataHex,
        String defaultChallenge,
        String defaultReference,
        String defaultAmount,
        Optional<String> terminalDataHex,
        Optional<String> iccDataHex,
        Optional<String> resolvedIccDataHex) {

    public EmvCapCredentialDescriptor {
        name = requireText(name, "name");
        mode = Objects.requireNonNull(mode, "mode");
        masterKey = Objects.requireNonNull(masterKey, "masterKey");
        defaultAtcHex = normalizeHex(defaultAtcHex, "defaultAtcHex");
        branchFactor = requirePositive(branchFactor, "branchFactor");
        height = requirePositive(height, "height");
        ivHex = normalizeHex(ivHex, "ivHex");
        cdol1Hex = normalizeHex(cdol1Hex, "cdol1Hex");
        issuerProprietaryBitmapHex = normalizeHex(issuerProprietaryBitmapHex, "issuerProprietaryBitmapHex");
        iccDataTemplateHex = normalizeTemplate(iccDataTemplateHex, "iccDataTemplateHex");
        issuerApplicationDataHex = normalizeHex(issuerApplicationDataHex, "issuerApplicationDataHex");
        defaultChallenge = normalizeDigits(defaultChallenge);
        defaultReference = normalizeDigits(defaultReference);
        defaultAmount = normalizeDigits(defaultAmount);
        terminalDataHex = normalizeOptionalHex(terminalDataHex);
        iccDataHex = normalizeOptionalHex(iccDataHex);
        resolvedIccDataHex = normalizeOptionalHex(resolvedIccDataHex);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static int requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static String normalizeHex(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if ((normalized.length() & 1) == 1) {
            throw new IllegalArgumentException(field + " must contain an even number of hexadecimal characters");
        }
        if (!normalized.matches("[0-9A-F]+")) {
            throw new IllegalArgumentException(field + " must be hexadecimal");
        }
        return normalized;
    }

    private static String normalizeTemplate(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if ((normalized.length() & 1) == 1) {
            throw new IllegalArgumentException(field + " must contain an even number of characters");
        }
        if (!normalized.matches("[0-9A-FX]+")) {
            throw new IllegalArgumentException(field + " must contain hexadecimal characters or 'X'");
        }
        return normalized;
    }

    private static String normalizeDigits(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private static Optional<String> normalizeOptionalHex(Optional<String> value) {
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        String text = value.get().trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        String normalized = text.toUpperCase(Locale.ROOT);
        if ((normalized.length() & 1) == 1) {
            throw new IllegalArgumentException("optional hex value must contain an even number of characters");
        }
        if (!normalized.matches("[0-9A-F]+")) {
            throw new IllegalArgumentException("optional hex value must be hexadecimal");
        }
        return Optional.of(normalized);
    }
}
