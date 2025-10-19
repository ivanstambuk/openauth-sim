package io.openauth.sim.core.otp.totp;

import io.openauth.sim.core.json.SimpleJson;
import io.openauth.sim.core.model.SecretMaterial;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/** Access point for the RFC 6238 TOTP validation vectors JSON catalogue. */
public final class TotpJsonVectorFixtures {

    private static final List<TotpJsonVector> CATALOG = loadCatalog();
    private static final Map<String, TotpJsonVector> CATALOG_BY_ID = indexById(CATALOG);

    private TotpJsonVectorFixtures() {
        throw new AssertionError("Utility class");
    }

    /** Load all known TOTP validation vectors from the JSON bundle. */
    public static Stream<TotpJsonVector> loadAll() {
        return CATALOG.stream();
    }

    /** Locate a TOTP validation vector by identifier. */
    public static TotpJsonVector getById(String vectorId) {
        Objects.requireNonNull(vectorId, "vectorId");
        TotpJsonVector vector = CATALOG_BY_ID.get(vectorId);
        if (vector == null) {
            throw new IllegalArgumentException("Unknown TOTP validation vector: " + vectorId);
        }
        return vector;
    }

    private static List<TotpJsonVector> loadCatalog() {
        Path bundle = resolveBundlePath();
        String json;
        try {
            json = Files.readString(bundle, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read TOTP validation vectors", ex);
        }

        Object parsed = SimpleJson.parse(json);
        if (!(parsed instanceof List<?> entries)) {
            throw new IllegalStateException("Expected top-level JSON array in TOTP vector bundle");
        }

        List<TotpJsonVector> vectors = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            Map<String, Object> root = asObject(entry, "vector");
            vectors.add(toVector(root));
        }
        return Collections.unmodifiableList(vectors);
    }

    private static Map<String, TotpJsonVector> indexById(List<TotpJsonVector> vectors) {
        Map<String, TotpJsonVector> index = new LinkedHashMap<>();
        for (TotpJsonVector vector : vectors) {
            if (index.put(vector.vectorId(), vector) != null) {
                throw new IllegalStateException("Duplicate TOTP vector id: " + vector.vectorId());
            }
        }
        return Collections.unmodifiableMap(index);
    }

    private static Path resolveBundlePath() {
        Path candidate = Path.of("docs", "totp_validation_vectors.json");
        if (Files.exists(candidate)) {
            return candidate;
        }
        Path moduleCandidate = Path.of("..", "docs", "totp_validation_vectors.json");
        if (Files.exists(moduleCandidate)) {
            return moduleCandidate;
        }
        throw new IllegalStateException("Unable to locate docs/totp_validation_vectors.json from working directory "
                + Path.of("").toAbsolutePath());
    }

    private static TotpJsonVector toVector(Map<String, Object> root) {
        String vectorId = requireString(root, "vectorId");
        String secretEncoding = requireString(root, "secretEncoding");
        if (!"hex".equalsIgnoreCase(secretEncoding)) {
            throw new IllegalStateException(
                    "Unsupported TOTP secret encoding '" + secretEncoding + "' for vector " + vectorId);
        }
        SecretMaterial secret = SecretMaterial.fromHex(requireString(root, "secret"));

        String algorithmLabel = requireString(root, "algorithm");
        TotpHashAlgorithm algorithm =
                TotpHashAlgorithm.valueOf(algorithmLabel.trim().toUpperCase(Locale.ROOT));

        int digits = requireInt(root, "digits");
        long stepSeconds = requireLong(root, "stepSeconds");
        long timestampEpochSeconds = requireLong(root, "timestamp");
        String otp = requireString(root, "otp");
        if (otp.length() != digits) {
            throw new IllegalStateException("OTP length mismatch for vector " + vectorId + ": expected " + digits);
        }

        int driftBackward = optionalInt(root, "driftBackwardSteps").orElse(0);
        int driftForward = optionalInt(root, "driftForwardSteps").orElse(0);
        Optional<String> label = optionalString(root, "label");
        Optional<String> notes = optionalString(root, "notes");

        return new TotpJsonVector(
                vectorId,
                secret,
                algorithm,
                digits,
                stepSeconds,
                timestampEpochSeconds,
                otp,
                driftBackward,
                driftForward,
                label,
                notes);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value, String description) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalStateException("Expected object for " + description);
    }

    private static String requireString(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (value == null) {
            throw new IllegalStateException("Missing required field '" + key + "'");
        }
        return value.toString();
    }

    private static int requireInt(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("Field '" + key + "' must be numeric");
        }
        return number.intValue();
    }

    private static long requireLong(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("Field '" + key + "' must be numeric");
        }
        return number.longValue();
    }

    private static Optional<Integer> optionalInt(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("Field '" + key + "' must be numeric when provided");
        }
        return Optional.of(number.intValue());
    }

    private static Optional<String> optionalString(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (value == null) {
            return Optional.empty();
        }
        String text = value.toString().trim();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    /** Representation of a single TOTP validation vector entry. */
    public record TotpJsonVector(
            String vectorId,
            SecretMaterial secret,
            TotpHashAlgorithm algorithm,
            int digits,
            long stepSeconds,
            long timestampEpochSeconds,
            String otp,
            int driftBackwardSteps,
            int driftForwardSteps,
            Optional<String> label,
            Optional<String> notes) {

        public TotpJsonVector {
            Objects.requireNonNull(vectorId, "vectorId");
            Objects.requireNonNull(secret, "secret");
            Objects.requireNonNull(algorithm, "algorithm");
            Objects.requireNonNull(otp, "otp");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(notes, "notes");
        }

        public Duration stepDuration() {
            return Duration.ofSeconds(stepSeconds);
        }

        public Instant timestamp() {
            return Instant.ofEpochSecond(timestampEpochSeconds);
        }
    }
}
