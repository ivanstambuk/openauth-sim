package io.openauth.sim.core.eudi.openid4vp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;

final class FixtureDatasetsTest {

    @Test
    void syntheticDatasetLoadsProvenanceMetadata() {
        FixtureDatasets.FixtureDataset dataset = FixtureDatasets.load(FixtureDatasets.Source.SYNTHETIC);

        assertEquals(FixtureDatasets.Source.SYNTHETIC, dataset.source());
        assertTrue(Files.isDirectory(dataset.rootDirectory()), "synthetic fixture root missing");
        assertEquals("Synthetic PID fixtures", dataset.provenance().source());
        assertEquals("2025-11-01", dataset.provenance().version());
        assertEquals(
                "sha256:synthetic-openid4vp-v1", dataset.provenance().sha256(), "synthetic provenance hash mismatch");
    }

    @Test
    void conformanceDatasetLoadsPlaceholderProvenance() {
        FixtureDatasets.FixtureDataset dataset = FixtureDatasets.load(FixtureDatasets.Source.CONFORMANCE);

        assertEquals(FixtureDatasets.Source.CONFORMANCE, dataset.source());
        assertTrue(Files.isDirectory(dataset.rootDirectory()), "conformance fixture root missing");
        assertEquals(
                "OpenID Foundation conformance bundle (placeholder)",
                dataset.provenance().source());
        assertEquals("2025-10-15", dataset.provenance().version());
        assertEquals(
                "sha256:conformance-openid4vp-placeholder",
                dataset.provenance().sha256(),
                "conformance provenance hash mismatch");
    }
}
