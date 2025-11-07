package io.openauth.sim.core.eudi.openid4vp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Red tests for Feature 040 S2 – ensure mdoc DeviceResponse fixtures expose metadata + claims pointers.
 */
final class MdocDeviceResponseFixturesTest {

    private static final Path FIXTURE_ROOT =
            Path.of("docs", "test-vectors", "eudiw", "openid4vp", "fixtures", "synthetic", "mdoc");

    @Test
    void loadReturnsMetadataAndClaimsPointersForPidHaipBaseline() throws IOException {
        MdocDeviceResponseFixtures fixtures = new MdocDeviceResponseFixtures(FIXTURE_ROOT);

        MdocDeviceResponseFixtures.DeviceResponseFixture fixture = fixtures.load("pid-haip-baseline");

        assertEquals("pid-haip-baseline", fixture.presetId());
        assertEquals("pid-haip-baseline", fixture.credentialId());
        assertEquals("mso_mdoc", fixture.docType());
        assertEquals(readFixtureString("pid-haip-baseline", "device-response.base64"), fixture.deviceResponseBase64());

        Map<String, String> claimsPointers = fixture.claimsPathPointers();
        assertEquals("Riviera", claimsPointers.get("$.pid.family_name"));
        assertEquals("Alice", claimsPointers.get("$.pid.given_name"));
        assertTrue(claimsPointers.containsKey("$.pid.birth_date"));
    }

    private static String readFixtureString(String presetId, String fileName) throws IOException {
        Path path = resolve(FIXTURE_ROOT.resolve(Path.of(presetId, fileName)));
        return Files.readString(path, StandardCharsets.UTF_8).trim();
    }

    private static Path resolve(Path relative) {
        Path direct = Path.of("").resolve(relative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path workspaceRoot = Path.of("..").resolve(relative);
        if (Files.exists(workspaceRoot)) {
            return workspaceRoot;
        }
        throw new IllegalStateException("Unable to resolve fixture path: " + relative);
    }
}
