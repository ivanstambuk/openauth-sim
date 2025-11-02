package io.openauth.sim.core.emv.cap;

import io.openauth.sim.core.json.SimpleJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Loader for EMV/CAP simulation vectors stored under {@code docs/test-vectors/emv-cap/}. */
public final class EmvCapVectorFixtures {

    private EmvCapVectorFixtures() {
        throw new AssertionError("Utility class");
    }

    /** Load a baseline vector by identifier (e.g., {@code identify-baseline}). */
    public static EmvCapVector load(String vectorId) {
        Objects.requireNonNull(vectorId, "vectorId");
        Path path = resolveVectorFile(vectorId);
        String json;
        try {
            json = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read EMV/CAP vector " + vectorId, ex);
        }

        Object parsed = SimpleJson.parse(json);
        if (!(parsed instanceof Map<?, ?> rootMap)) {
            throw new IllegalStateException("Vector " + vectorId + " must be a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) rootMap;

        EmvCapInput input = toInput(root, vectorId);
        Outputs outputs = toOutputs(root, vectorId);
        Resolved resolved = toResolved(root);
        return new EmvCapVector(vectorId, input, resolved, outputs);
    }

    private static Path resolveVectorFile(String vectorId) {
        String fileName = vectorId.toLowerCase(Locale.ROOT) + ".json";
        Path candidate = Path.of("docs", "test-vectors", "emv-cap", fileName);
        if (Files.exists(candidate)) {
            return candidate;
        }
        Path moduleCandidate = Path.of("..", "docs", "test-vectors", "emv-cap", fileName);
        if (Files.exists(moduleCandidate)) {
            return moduleCandidate;
        }
        throw new IllegalStateException("Unable to locate EMV/CAP vector file " + fileName);
    }

    private static EmvCapInput toInput(Map<String, Object> root, String vectorId) {
        Map<String, Object> inputs = requireObject(root, "inputs", vectorId);

        EmvCapMode mode = EmvCapMode.fromLabel(requireString(root, "mode", vectorId));
        String masterKey = requireHex(inputs, "masterKey", vectorId);
        String atc = requireHex(inputs, "atc", vectorId);
        int branchFactor = requireNumber(inputs, "branchFactor", vectorId);
        int height = requireNumber(inputs, "height", vectorId);
        String iv = requireHex(inputs, "iv", vectorId);
        String cdol1 = requireHex(inputs, "cdol1", vectorId);
        String ipb = requireHex(inputs, "issuerProprietaryBitmap", vectorId);
        String iccTemplate = requireTemplate(inputs, "iccDataTemplate", vectorId);
        String issuerApplicationData = requireHex(inputs, "issuerApplicationData", vectorId);

        String challenge = optionalString(inputs, "challenge");
        String reference = optionalString(inputs, "reference");
        String amount = optionalString(inputs, "amount");

        EmvCapInput.CustomerInputs customerInputs = new EmvCapInput.CustomerInputs(challenge, reference, amount);

        EmvCapInput.TransactionData transactionData = inputs.containsKey("transactionData")
                ? toTransactionData(inputs.get("transactionData"))
                : EmvCapInput.TransactionData.empty();

        return new EmvCapInput(
                mode,
                masterKey,
                atc,
                branchFactor,
                height,
                iv,
                cdol1,
                ipb,
                customerInputs,
                transactionData,
                iccTemplate,
                issuerApplicationData);
    }

    private static EmvCapInput.TransactionData toTransactionData(Object value) {
        if (!(value instanceof Map<?, ?> mapValue)) {
            throw new IllegalArgumentException("transactionData must be an object when provided");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) mapValue;
        Optional<String> terminal = optionalHex(map, "terminal");
        Optional<String> icc = optionalHex(map, "icc");
        return new EmvCapInput.TransactionData(terminal, icc);
    }

    private static Resolved toResolved(Map<String, Object> root) {
        if (!root.containsKey("resolved")) {
            return new Resolved(null);
        }
        Object resolvedObject = root.get("resolved");
        if (!(resolvedObject instanceof Map<?, ?> resolvedMap)) {
            throw new IllegalStateException("resolved entry must be an object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> resolved = (Map<String, Object>) resolvedMap;
        String iccData = optionalHex(resolved, "iccData").orElse(null);
        return new Resolved(iccData);
    }

    private static Outputs toOutputs(Map<String, Object> root, String vectorId) {
        Map<String, Object> outputs = requireObject(root, "outputs", vectorId);

        String sessionKey = requireHex(outputs, "sessionKey", vectorId);

        Map<String, Object> generateAcInput = requireObject(outputs, "generateAcInput", vectorId);
        String terminalHex = requireHex(generateAcInput, "terminal", vectorId);
        String iccHex = requireHex(generateAcInput, "icc", vectorId);

        String generateAcResult = requireHex(outputs, "generateAcResult", vectorId);
        String bitmask = requireString(outputs, "bitmask", vectorId);
        String digitsOverlay = requireString(outputs, "maskedDigitsOverlay", vectorId);

        Map<String, Object> otp = requireObject(outputs, "otp", vectorId);
        String otpDecimal = requireString(otp, "decimal", vectorId);
        String otpHex = requireLenientHex(otp, "hex", vectorId);

        return new Outputs(
                sessionKey, terminalHex, iccHex, generateAcResult, bitmask, digitsOverlay, otpDecimal, otpHex);
    }

    private static Map<String, Object> requireObject(Map<String, Object> root, String key, String vectorId) {
        Object value = root.get(key);
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Vector " + vectorId + " is missing object field '" + key + "'");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> cast = (Map<String, Object>) map;
        return cast;
    }

    private static String requireString(Map<String, Object> object, String key, String vectorId) {
        Object value = object.get(key);
        if (value == null) {
            throw new IllegalStateException("Vector " + vectorId + " is missing string field '" + key + "'");
        }
        return value.toString().trim();
    }

    private static int requireNumber(Map<String, Object> object, String key, String vectorId) {
        Object value = object.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("Vector " + vectorId + " field '" + key + "' must be numeric");
        }
        return number.intValue();
    }

    private static String requireHex(Map<String, Object> object, String key, String vectorId) {
        String text = requireString(object, key, vectorId).toUpperCase(Locale.ROOT);
        if ((text.length() & 1) == 1) {
            throw new IllegalStateException("Vector " + vectorId + " hex field '" + key + "' must have an even length");
        }
        if (!text.matches("[0-9A-F]+")) {
            throw new IllegalStateException("Vector " + vectorId + " field '" + key + "' must be hexadecimal");
        }
        return text;
    }

    private static String requireLenientHex(Map<String, Object> object, String key, String vectorId) {
        String text = requireString(object, key, vectorId).toUpperCase(Locale.ROOT);
        if (!text.matches("[0-9A-F]+")) {
            throw new IllegalStateException("Vector " + vectorId + " field '" + key + "' must be hexadecimal");
        }
        return text;
    }

    private static String requireTemplate(Map<String, Object> object, String key, String vectorId) {
        String text = requireString(object, key, vectorId).trim();
        if ((text.length() & 1) == 1) {
            throw new IllegalStateException(
                    "Vector " + vectorId + " template field '" + key + "' must have an even length");
        }
        String normalized = text.toUpperCase(Locale.ROOT);
        if (!normalized.matches("[0-9A-FX]+")) {
            throw new IllegalStateException("Vector " + vectorId + " template field '" + key
                    + "' must contain hexadecimal characters or 'X' placeholders");
        }
        return normalized;
    }

    private static Optional<String> optionalHex(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (value == null) {
            return Optional.empty();
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        String uppercase = text.toUpperCase(Locale.ROOT);
        if ((uppercase.length() & 1) == 1 || !uppercase.matches("[0-9A-F]+")) {
            throw new IllegalStateException("Optional field '" + key + "' must be hexadecimal");
        }
        return Optional.of(uppercase);
    }

    private static String optionalString(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (value == null) {
            return "";
        }
        return value.toString().trim();
    }

    /** Representation of a parsed EMV/CAP fixture. */
    public record EmvCapVector(String vectorId, EmvCapInput input, Resolved resolved, Outputs outputs) {
        // Data carrier for fixture inputs/outputs.
    }

    /** Resolved helper values captured in the fixture (e.g., ICC data template expansion). */
    public record Resolved(String iccDataHex) {
        // Holder for pre-resolved values (e.g., ICC payload templates).
    }

    /** Expected output payloads for the fixture. */
    public record Outputs(
            String sessionKeyHex,
            String generateAcInputTerminalHex,
            String generateAcInputIccHex,
            String generateAcResultHex,
            String bitmaskOverlay,
            String maskedDigitsOverlay,
            String otpDecimal,
            String otpHex) {
        // Convenience projection for expected outputs.
    }
}
