package io.openauth.sim.core.eudi.openid4vp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.json.SimpleJson;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** Smoke tests that ensure synthetic PID fixtures required by Feature 040 are present. */
final class PidFixtureSmokeTest {

    private static final Path FIXTURE_ROOT =
            Path.of("docs", "test-vectors", "eudiw", "openid4vp", "fixtures", "synthetic");
    private static final Path SEED_FILE =
            Path.of("docs", "test-vectors", "eudiw", "openid4vp", "seeds", "default.seed");

    @Test
    void sdJwtFixtureProvidesRequiredArtifacts() throws IOException {
        Path fixtureDir = resolve(FIXTURE_ROOT.resolve(Path.of("sdjwt-vc", "pid-haip-baseline")));
        assertTrue(Files.isDirectory(fixtureDir), "sd-jwt fixture directory missing: " + fixtureDir);

        Map<String, Object> metadata = readJsonObject(fixtureDir.resolve("metadata.json"));
        assertEquals("pid-haip-baseline", metadata.get("credentialId"));
        assertEquals("dc+sd-jwt", metadata.get("format"));
        Map<String, Object> issuer = readNestedObject(metadata, "issuer");
        assertEquals("EU PID Issuer", issuer.get("label"));
        assertEquals("s9tIpP7qrS9=", issuer.get("authorityKeyIdentifier"));

        Map<String, Object> holder = readNestedObject(metadata, "holder");
        assertEquals("Sample PID Holder", holder.get("label"));
        assertEquals("pid-haip-baseline-holder", holder.get("bindingKeyId"));

        Map<String, Object> digests = readJsonObject(fixtureDir.resolve("digests.json"));
        assertEquals(3, digests.size(), "unexpected digest count");

        List<Object> disclosures = readJsonArray(fixtureDir.resolve("disclosures.json"));
        assertEquals(3, disclosures.size(), "unexpected disclosure count");
        disclosures.forEach(disclosure -> {
            assertTrue(disclosure instanceof List<?>, "disclosure entry must be an array");
            @SuppressWarnings("unchecked")
            List<Object> tuple = (List<Object>) disclosure;
            assertEquals(3, tuple.size(), "disclosure tuple must contain salt, claim, value");
            String salt = String.valueOf(tuple.get(0));
            assertFalse(salt.isBlank(), "salt must be non-empty base64");
            Base64.getDecoder().decode(salt); // fails fast if not valid base64 text
        });

        Map<String, Object> keyBindingJwt = readJsonObject(fixtureDir.resolve("kb-jwt.json"));
        assertEquals("eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9", keyBindingJwt.get("protected"));
        assertTrue(keyBindingJwt.containsKey("payload"), "key binding JWT payload missing");
        assertTrue(keyBindingJwt.containsKey("signature"), "key binding JWT signature missing");

        String sdJwtCompact = Files.readString(fixtureDir.resolve("sdjwt.txt"), StandardCharsets.UTF_8)
                .trim();
        String[] segments = sdJwtCompact.split("\\.");
        assertEquals(3, segments.length, "sd-jwt compact representation must have three segments");
        assertTrue(segments[0].startsWith("eyJhbGciOiJFUzI1NiIs"), "unexpected header segment");
    }

    @Test
    void mdocFixtureProvidesDeviceResponseAndMetadata() throws IOException {
        Path fixtureDir = resolve(FIXTURE_ROOT.resolve(Path.of("mdoc", "pid-haip-baseline")));
        assertTrue(Files.isDirectory(fixtureDir), "mdoc fixture directory missing: " + fixtureDir);

        Map<String, Object> metadata = readJsonObject(fixtureDir.resolve("metadata.json"));
        assertEquals("mso_mdoc", metadata.get("format"));
        assertEquals("pid-haip-baseline", metadata.get("credentialId"));
        Map<String, Object> issuer = readNestedObject(metadata, "issuer");
        assertEquals("EU PID Issuer", issuer.get("label"));

        byte[] deviceResponse = Base64.getDecoder()
                .decode(Files.readString(fixtureDir.resolve("device-response.base64"), StandardCharsets.UTF_8)
                        .trim());
        assertTrue(deviceResponse.length > 16, "device response base64 unexpectedly small");

        String diagnostic =
                Files.readString(fixtureDir.resolve("device-response.diagnostic.cbor"), StandardCharsets.UTF_8);
        assertTrue(diagnostic.contains("synthetic-fixture"), "diagnostic CBOR text missing synthetic marker");
    }

    @Test
    void deterministicSeedFileExposesNonceAndStateSeeds() throws IOException {
        Path seedFile = resolve(SEED_FILE);
        assertTrue(Files.exists(seedFile), "seed file missing: " + seedFile);

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(seedFile)) {
            properties.load(input);
        }

        assertEquals("eudiw-openid4vp-nonce-seed-v1", properties.getProperty("nonceSeed"));
        assertEquals("eudiw-openid4vp-state-seed-v1", properties.getProperty("stateSeed"));
        assertEquals("HAIP-", properties.getProperty("requestIdPrefix"));
        assertEquals("eudiw-openid4vp-kb-nonce-seed-v1", properties.getProperty("keyBindingNonceSeed"));
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
        throw new AssertionError("Unable to resolve fixture path: " + relative);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readJsonObject(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        Object parsed = SimpleJson.parse(json);
        assertTrue(parsed instanceof Map<?, ?>, () -> "expected JSON object at " + path);
        return (Map<String, Object>) parsed;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> readJsonArray(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        Object parsed = SimpleJson.parse(json);
        assertTrue(parsed instanceof List<?>, () -> "expected JSON array at " + path);
        return (List<Object>) parsed;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readNestedObject(Map<String, Object> root, String key) {
        Object value = root.get(key);
        assertNotNull(value, () -> "expected JSON object field '" + key + "'");
        assertTrue(value instanceof Map<?, ?>, () -> "field '" + key + "' must be an object");
        return (Map<String, Object>) value;
    }
}
