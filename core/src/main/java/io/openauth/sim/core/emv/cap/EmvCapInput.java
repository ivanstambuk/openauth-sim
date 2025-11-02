package io.openauth.sim.core.emv.cap;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Immutable request payload for EMV/CAP simulations. */
public record EmvCapInput(
        EmvCapMode mode,
        String masterKeyHex,
        String atcHex,
        int branchFactor,
        int height,
        String ivHex,
        String cdol1Hex,
        String issuerProprietaryBitmapHex,
        CustomerInputs customerInputs,
        TransactionData transactionData,
        String iccDataTemplateHex,
        String issuerApplicationDataHex) {

    public EmvCapInput {
        Objects.requireNonNull(mode, "mode");
        masterKeyHex = normalizeHex(masterKeyHex, "masterKey");
        atcHex = normalizeHex(atcHex, "atc");
        if ((atcHex.length() & 1) == 1) {
            throw new IllegalArgumentException("atc must contain an even number of hex characters");
        }
        if (branchFactor <= 0) {
            throw new IllegalArgumentException("branchFactor must be positive");
        }
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive");
        }
        ivHex = normalizeHex(ivHex, "iv");
        cdol1Hex = normalizeHex(cdol1Hex, "cdol1");
        issuerProprietaryBitmapHex = normalizeHex(issuerProprietaryBitmapHex, "issuerProprietaryBitmap");
        customerInputs = Objects.requireNonNull(customerInputs, "customerInputs");
        transactionData = transactionData == null ? TransactionData.empty() : transactionData;
        iccDataTemplateHex = normalizeTemplate(iccDataTemplateHex, "iccDataTemplate");
        issuerApplicationDataHex = normalizeHex(issuerApplicationDataHex, "issuerApplicationData");
    }

    private static String normalizeHex(String hex, String field) {
        Objects.requireNonNull(hex, field);
        String normalized = hex.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        if ((normalized.length() & 1) == 1) {
            throw new IllegalArgumentException(field + " must contain an even number of hex characters");
        }
        if (!normalized.matches("[0-9A-F]+")) {
            throw new IllegalArgumentException(field + " must be an uppercase hexadecimal string");
        }
        return normalized;
    }

    private static String normalizeTemplate(String template, String field) {
        Objects.requireNonNull(template, field);
        String normalized = template.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        if ((normalized.length() & 1) == 1) {
            throw new IllegalArgumentException(field + " must contain an even number of characters");
        }
        if (!normalized.matches("[0-9A-FX]+")) {
            throw new IllegalArgumentException(field + " may only contain hexadecimal characters or 'X' placeholders");
        }
        return normalized;
    }

    /** Holder for user-provided challenge/reference/amount inputs. */
    public record CustomerInputs(String challenge, String reference, String amount) {

        public CustomerInputs {
            challenge = normalizeDecimal(challenge);
            reference = normalizeDecimal(reference);
            amount = normalizeDecimal(amount);
        }

        public CustomerInputs withChallenge(String updatedChallenge) {
            return new CustomerInputs(updatedChallenge, reference, amount);
        }

        public CustomerInputs withReference(String updatedReference) {
            return new CustomerInputs(challenge, updatedReference, amount);
        }

        public CustomerInputs withAmount(String updatedAmount) {
            return new CustomerInputs(challenge, reference, updatedAmount);
        }

        private static String normalizeDecimal(String value) {
            if (value == null) {
                return "";
            }
            return value.trim();
        }
    }

    /** Optional overrides for pre-resolved terminal and ICC payloads. */
    public record TransactionData(Optional<String> terminalHexOverride, Optional<String> iccHexOverride) {

        private static final TransactionData EMPTY = new TransactionData(Optional.empty(), Optional.empty());

        public TransactionData {
            terminalHexOverride = normalizeOptionalHex(terminalHexOverride, "transactionData.terminal");
            iccHexOverride = normalizeOptionalHex(iccHexOverride, "transactionData.icc");
        }

        public static TransactionData empty() {
            return EMPTY;
        }

        private static Optional<String> normalizeOptionalHex(Optional<String> value, String field) {
            if (value == null || value.isEmpty()) {
                return Optional.empty();
            }
            String text = value.get().trim().toUpperCase(Locale.ROOT);
            if (text.isEmpty()) {
                return Optional.empty();
            }
            if ((text.length() & 1) == 1) {
                throw new IllegalArgumentException(field + " override must contain an even number of hex characters");
            }
            if (!text.matches("[0-9A-F]+")) {
                throw new IllegalArgumentException(field + " override must be an uppercase hexadecimal string");
            }
            return Optional.of(text);
        }
    }
}
