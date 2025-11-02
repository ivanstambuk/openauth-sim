package io.openauth.sim.core.emv.cap;

import io.openauth.sim.core.json.SimpleJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Loader for EMV/CAP replay fixtures stored under {@code docs/test-vectors/emv-cap/}. */
public final class EmvCapReplayFixtures {

    private static final String FIXTURE_FILE = "replay-fixtures.json";
    private static final List<ReplayFixture> FIXTURES = loadFixtures();

    private EmvCapReplayFixtures() {
        throw new AssertionError("Utility class");
    }

    /** Return all configured replay fixtures. */
    public static List<ReplayFixture> fixtures() {
        return FIXTURES;
    }

    /** Locate a replay fixture by identifier. */
    public static ReplayFixture load(String fixtureId) {
        Objects.requireNonNull(fixtureId, "fixtureId");
        return FIXTURES.stream()
                .filter(fixture -> fixture.id().equals(fixtureId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown EMV/CAP replay fixture " + fixtureId));
    }

    private static List<ReplayFixture> loadFixtures() {
        Path file = resolveFixtureFile();
        String json;
        try {
            json = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read EMV/CAP replay fixtures from " + file, ex);
        }

        Object parsed = SimpleJson.parse(json);
        if (!(parsed instanceof Map<?, ?> rootObject)) {
            throw new IllegalStateException("Replay fixture catalogue must be a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) rootObject;
        Object fixturesNode = root.get("fixtures");
        if (!(fixturesNode instanceof List<?> fixturesList)) {
            throw new IllegalStateException("'fixtures' entry must be an array");
        }

        List<ReplayFixture> fixtures = new ArrayList<>();
        for (Object entry : fixturesList) {
            if (!(entry instanceof Map<?, ?> fixtureMap)) {
                throw new IllegalStateException("Each replay fixture must be a JSON object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> fixture = (Map<String, Object>) fixtureMap;
            String id = requireString(fixture, "id");
            String credentialId = requireString(fixture, "credentialId");
            String vectorId = requireString(fixture, "vectorId");
            EmvCapMode mode = EmvCapMode.fromLabel(requireString(fixture, "mode"));
            String otpDecimal = requireNumericString(fixture, "otpDecimal");
            String otpHex = requireHex(fixture, "otpHex");
            String mismatchOtpDecimal = requireNumericString(fixture, "mismatchOtpDecimal");

            Map<String, Object> previewWindowMap = requireObject(fixture, "previewWindow");
            int backward = requireInt(previewWindowMap, "backward");
            int forward = requireInt(previewWindowMap, "forward");

            fixtures.add(new ReplayFixture(
                    id,
                    credentialId,
                    vectorId,
                    mode,
                    otpDecimal,
                    otpHex,
                    mismatchOtpDecimal,
                    new PreviewWindow(backward, forward)));
        }

        return Collections.unmodifiableList(fixtures);
    }

    private static Path resolveFixtureFile() {
        Path preferred = Path.of("docs", "test-vectors", "emv-cap", FIXTURE_FILE);
        if (Files.exists(preferred)) {
            return preferred;
        }
        Path moduleRelative = Path.of("..", "docs", "test-vectors", "emv-cap", FIXTURE_FILE);
        if (Files.exists(moduleRelative)) {
            return moduleRelative;
        }
        throw new IllegalStateException("Unable to locate EMV/CAP replay fixture file " + FIXTURE_FILE);
    }

    private static Map<String, Object> requireObject(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Replay fixture field '" + key + "' must be an object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> cast = (Map<String, Object>) map;
        return cast;
    }

    private static String requireString(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (value == null) {
            throw new IllegalStateException("Replay fixture missing string field '" + key + "'");
        }
        return value.toString().trim();
    }

    private static String requireNumericString(Map<String, Object> parent, String key) {
        String value = requireString(parent, key);
        if (!value.matches("\\d+")) {
            throw new IllegalStateException("Replay fixture field '" + key + "' must contain only digits");
        }
        return value;
    }

    private static String requireHex(Map<String, Object> parent, String key) {
        String value = requireString(parent, key).toUpperCase(Locale.ROOT);
        if (!value.matches("[0-9A-F]+")) {
            throw new IllegalStateException("Replay fixture field '" + key + "' must be hexadecimal");
        }
        return value;
    }

    private static int requireInt(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank() && text.matches("-?\\d+")) {
            return Integer.parseInt(text, 10);
        }
        throw new IllegalStateException("Replay fixture field '" + key + "' must be an integer");
    }

    /** Window configuration describing backward/forward preview search bounds. */
    public record PreviewWindow(int backward, int forward) {

        public PreviewWindow {
            if (backward < 0 || forward < 0) {
                throw new IllegalArgumentException("Preview window bounds must be non-negative");
            }
        }
    }

    /** Representation of an EMV/CAP replay fixture. */
    public record ReplayFixture(
            String id,
            String credentialId,
            String vectorId,
            EmvCapMode mode,
            String otpDecimal,
            String otpHex,
            String mismatchOtpDecimal,
            PreviewWindow previewWindow) {

        public ReplayFixture {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(credentialId, "credentialId");
            Objects.requireNonNull(vectorId, "vectorId");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(otpDecimal, "otpDecimal");
            Objects.requireNonNull(otpHex, "otpHex");
            Objects.requireNonNull(mismatchOtpDecimal, "mismatchOtpDecimal");
            Objects.requireNonNull(previewWindow, "previewWindow");
        }

        /** Resolve the evaluation vector referenced by this fixture. */
        public EmvCapVectorFixtures.EmvCapVector referencedVector() {
            return EmvCapVectorFixtures.load(vectorId);
        }

        /** @return {@code true} when this fixture represents a stored credential scenario. */
        public boolean stored() {
            return Optional.ofNullable(credentialId).filter(id -> !id.isBlank()).isPresent();
        }
    }
}
