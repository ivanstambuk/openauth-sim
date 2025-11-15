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
        assertTrue(dataset.provenance().metadata().isEmpty(), "synthetic dataset should not expose extra metadata");
    }

    @Test
    void conformanceDatasetLoadsPlaceholderProvenance() {
        FixtureDatasets.FixtureDataset dataset = FixtureDatasets.load(FixtureDatasets.Source.CONFORMANCE);

        assertEquals(FixtureDatasets.Source.CONFORMANCE, dataset.source());
        assertTrue(Files.isDirectory(dataset.rootDirectory()), "conformance fixture root missing");
        assertEquals(
                "EU LOTL + Member-State Trusted Lists (DE, SI)",
                dataset.provenance().source());
        assertEquals("2025-11-15T13:11:59Z", dataset.provenance().version());
        assertEquals(
                "sha256:cb0dfbf7a8df9d7ea1b36bce46dbfc48ee5c40e8e6c6f43bae48e165b5bc69e5",
                dataset.provenance().sha256(),
                "conformance provenance hash mismatch");
        assertEquals(
                "2025-11-15T13:11:59Z-optionA", dataset.provenance().metadata().get("ingestId"), "ingest id missing");
        Object lotlSequence = dataset.provenance().metadata().get("lotlSequenceNumber");
        assertTrue(lotlSequence instanceof Number, "lotl sequence should be numeric");
        assertEquals(373, ((Number) lotlSequence).intValue());
    }
}
