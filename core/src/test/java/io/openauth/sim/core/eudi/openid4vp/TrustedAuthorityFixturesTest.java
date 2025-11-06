package io.openauth.sim.core.eudi.openid4vp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.eudi.openid4vp.TrustedAuthorityFixtures.TrustedAuthorityPolicy;
import io.openauth.sim.core.eudi.openid4vp.TrustedAuthorityFixtures.TrustedAuthoritySnapshot;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

/**
 * Red tests for Feature 040 – T3999.
 *
 * <p>The implementation will ingest ETSI Trust List / OpenID Federation snapshots and make them
 * available as DCQL metadata. Until then, these tests are expected to fail.
 */
final class TrustedAuthorityFixturesTest {

    @Test
    void haipBaselineSnapshotIncludesTrustedAuthorityMetadata() {
        TrustedAuthoritySnapshot snapshot = TrustedAuthorityFixtures.loadSnapshot("haip-baseline");

        assertEquals("haip-baseline", snapshot.presetId());
        assertTrue(
                snapshot.storedPresentationIds().contains("pid-haip-baseline"),
                "baseline snapshot must seed stored presentations for reuse");

        assertTrue(
                snapshot.authorities().stream().anyMatch(policy -> policy.type().equals("aki")),
                "aki authorities missing");
        assertTrue(
                snapshot.authorities().stream().anyMatch(policy -> policy.type().equals("etsi_tl")),
                "etsi_tl authorities missing");
        assertTrue(
                snapshot.authorities().stream().anyMatch(policy -> policy.type().equals("openid_federation")),
                "openid_federation authorities missing");

        assertTrustedAuthorityValue(snapshot, "aki", new TrustedAuthorityExpectation("s9tIpP7qrS9=", "EU PID Issuer"));
        assertTrustedAuthorityValue(
                snapshot,
                "etsi_tl",
                new TrustedAuthorityExpectation("EU-2025-09-TL", "ETSI Trusted List – 2025-09 snapshot"));
        assertTrustedAuthorityValue(
                snapshot,
                "openid_federation",
                new TrustedAuthorityExpectation(
                        "https://haip.ec.europa.eu/trust-list", "EU Wallet Federation Trust List"));
    }

    @Test
    void missingSnapshotThrows() {
        assertThrows(IllegalStateException.class, () -> TrustedAuthorityFixtures.loadSnapshot("not-defined"));
    }

    @Test
    void parseSnapshotRejectsMissingStoredPresentationIds() {
        Map<String, Object> root = Map.of(
                "presetId",
                "invalid",
                "authorities",
                List.of(Map.of("type", "aki", "values", List.of(Map.of("value", "v", "label", "l")))));

        assertThrows(IllegalStateException.class, () -> TrustedAuthorityFixtures.parseSnapshot(root, "invalid"));
    }

    @Test
    void parseSnapshotRejectsNonArrayAuthorities() {
        Map<String, Object> root = Map.of(
                "presetId", "invalid",
                "storedPresentationIds", List.of("pid-haip-baseline"),
                "authorities", "not-an-array");

        assertThrows(IllegalStateException.class, () -> TrustedAuthorityFixtures.parseSnapshot(root, "invalid"));
    }

    @Test
    void parseSnapshotRejectsAuthorityWithInvalidValues() {
        Map<String, Object> root = Map.of(
                "presetId",
                "invalid",
                "storedPresentationIds",
                List.of("pid-haip-baseline"),
                "authorities",
                List.of(Map.of("type", "aki", "values", List.of("not-an-object"))));

        assertThrows(IllegalStateException.class, () -> TrustedAuthorityFixtures.parseSnapshot(root, "invalid"));
    }

    @Test
    void parseSnapshotRejectsMissingPresetId() {
        Map<String, Object> root = Map.of(
                "storedPresentationIds", List.of("pid-haip-baseline"),
                "authorities", List.of(Map.of("type", "aki", "values", List.of(Map.of("value", "v", "label", "l")))));

        assertThrows(IllegalStateException.class, () -> TrustedAuthorityFixtures.parseSnapshot(root, "invalid"));
    }

    @Test
    void parseSnapshotRejectsAuthorityMissingLabel() {
        Map<String, Object> root = Map.of(
                "presetId",
                "invalid",
                "storedPresentationIds",
                List.of("pid-haip-baseline"),
                "authorities",
                List.of(Map.of("type", "aki", "values", List.of(Map.of("value", "v")))));

        assertThrows(IllegalStateException.class, () -> TrustedAuthorityFixtures.parseSnapshot(root, "invalid"));
    }

    private static void assertTrustedAuthorityValue(
            TrustedAuthoritySnapshot snapshot, String type, TrustedAuthorityExpectation expectation) {
        TrustedAuthorityPolicy policy = snapshot.authorities().stream()
                .filter(candidate -> candidate.type().equals(type))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Missing policy type: " + type));

        assertTrue(
                policy.values().stream()
                        .anyMatch(value -> value.value().equals(expectation.value())
                                && value.label().equals(expectation.label())),
                () -> "policy mismatch for " + type);
    }

    private record TrustedAuthorityExpectation(String value, String label) {
        // Test helper record.
    }
}
