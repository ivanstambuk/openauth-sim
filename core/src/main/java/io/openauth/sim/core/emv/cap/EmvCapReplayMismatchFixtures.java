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

/** Loader for EMV/CAP replay mismatch fixtures stored under {@code docs/test-vectors/emv-cap/}. */
public final class EmvCapReplayMismatchFixtures {

    private static final String FIXTURE_FILE = "replay-mismatch.json";
    private static final List<MismatchFixture> CASES = loadCases();

    private EmvCapReplayMismatchFixtures() {
        throw new AssertionError("Utility class");
    }

    /** Return the immutable list of mismatch cases. */
    public static List<MismatchFixture> cases() {
        return CASES;
    }

    /** Locate a mismatch case by identifier. */
    public static MismatchFixture load(String caseId) {
        Objects.requireNonNull(caseId, "caseId");
        return CASES.stream()
                .filter(caseFixture -> caseFixture.id().equals(caseId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown replay mismatch case " + caseId));
    }

    private static List<MismatchFixture> loadCases() {
        Path file = resolveFixtureFile();
        String json;
        try {
            json = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read EMV/CAP replay mismatch fixtures from " + file, ex);
        }

        Object parsed = SimpleJson.parse(json);
        if (!(parsed instanceof Map<?, ?> rootObject)) {
            throw new IllegalStateException("Replay mismatch catalogue must be a JSON object");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) rootObject;
        Object casesNode = root.get("cases");
        if (!(casesNode instanceof List<?> caseEntries)) {
            throw new IllegalStateException("'cases' entry must be an array");
        }

        List<MismatchFixture> fixtures = new ArrayList<>();
        for (Object entry : caseEntries) {
            if (!(entry instanceof Map<?, ?> caseMap)) {
                throw new IllegalStateException("Each replay mismatch case must be a JSON object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> caseObject = (Map<String, Object>) caseMap;
            String id = requireString(caseObject, "id");
            String fixtureId = requireString(caseObject, "fixtureId");
            String credentialSource = requireString(caseObject, "credentialSource");
            EmvCapMode mode = EmvCapMode.fromLabel(requireString(caseObject, "mode"));
            String expectedOtpDecimal = requireNumericString(caseObject, "expectedOtpDecimal");
            String expectedOtpHash = requireHash(caseObject, "expectedOtpHash");
            String mismatchOtpDecimal = requireNumericString(caseObject, "mismatchOtpDecimal");

            fixtures.add(new MismatchFixture(
                    id, fixtureId, credentialSource, mode, expectedOtpDecimal, expectedOtpHash, mismatchOtpDecimal));
        }

        return Collections.unmodifiableList(fixtures);
    }

    private static Path resolveFixtureFile() {
        Path preferred = Path.of("docs", "test-vectors", "emv-cap", FIXTURE_FILE);
        if (Files.exists(preferred)) {
            return preferred;
        }
        Path modulePath = Path.of("..", "docs", "test-vectors", "emv-cap", FIXTURE_FILE);
        if (Files.exists(modulePath)) {
            return modulePath;
        }
        throw new IllegalStateException("Unable to locate EMV/CAP replay mismatch fixture file " + FIXTURE_FILE);
    }

    private static String requireString(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (value == null) {
            throw new IllegalStateException("Replay mismatch case missing string field '" + key + "'");
        }
        return value.toString().trim();
    }

    private static String requireNumericString(Map<String, Object> parent, String key) {
        String value = requireString(parent, key);
        if (!value.matches("\\d+")) {
            throw new IllegalStateException("Replay mismatch case field '" + key + "' must contain only digits");
        }
        return value;
    }

    private static String requireHash(Map<String, Object> parent, String key) {
        String value = requireString(parent, key).toLowerCase(Locale.ROOT);
        if (!value.startsWith("sha256:")) {
            throw new IllegalStateException("Replay mismatch case field '" + key + "' must begin with sha256:");
        }
        if (value.length() != "sha256:".length() + 64) {
            throw new IllegalStateException(
                    "Replay mismatch case field '" + key + "' must contain a 64-character hex digest");
        }
        for (int index = "sha256:".length(); index < value.length(); index++) {
            char ch = value.charAt(index);
            if (!((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f'))) {
                throw new IllegalStateException("Replay mismatch case field '" + key + "' must be hexadecimal");
            }
        }
        return "sha256:" + value.substring("sha256:".length()).toUpperCase(Locale.ROOT);
    }

    /** Representation of an EMV/CAP replay mismatch case. */
    public record MismatchFixture(
            String id,
            String fixtureId,
            String credentialSource,
            EmvCapMode mode,
            String expectedOtpDecimal,
            String expectedOtpHash,
            String mismatchOtpDecimal) {

        public MismatchFixture {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(fixtureId, "fixtureId");
            Objects.requireNonNull(credentialSource, "credentialSource");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(expectedOtpDecimal, "expectedOtpDecimal");
            Objects.requireNonNull(expectedOtpHash, "expectedOtpHash");
            Objects.requireNonNull(mismatchOtpDecimal, "mismatchOtpDecimal");
        }

        /** Return the replay fixture referenced by this mismatch case. */
        public EmvCapReplayFixtures.ReplayFixture replayFixture() {
            return EmvCapReplayFixtures.load(fixtureId);
        }
    }
}
