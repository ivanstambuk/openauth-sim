package io.openauth.sim.rest.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class Fido2OperatorSampleDataTest {

    @Test
    void inlineVectorsLabelW3cFixturesWithProvenanceSuffix() {
        Optional<Fido2OperatorSampleData.InlineVector> w3cVector = Fido2OperatorSampleData.inlineVectors().stream()
                .filter(vector -> "w3c".equalsIgnoreCase(vector.metadata().getOrDefault("fixtureSource", "synthetic")))
                .findFirst();

        assertTrue(w3cVector.isPresent(), "Expected at least one W3C-backed inline vector");
        assertTrue(
                w3cVector.get().label().endsWith(" (W3C Level 3)"),
                () -> "W3C inline vector label missing provenance suffix: "
                        + w3cVector.get().label());
    }

    @Test
    void inlineVectorsLeaveSyntheticLabelsUnchanged() {
        Optional<Fido2OperatorSampleData.InlineVector> syntheticVector =
                Fido2OperatorSampleData.inlineVectors().stream()
                        .filter(vector ->
                                !"w3c".equalsIgnoreCase(vector.metadata().getOrDefault("fixtureSource", "synthetic")))
                        .findFirst();

        assertTrue(syntheticVector.isPresent(), "Expected at least one synthetic inline vector");
        assertFalse(
                syntheticVector.get().label().endsWith(" (W3C Level 3)"),
                () -> "Synthetic inline vector should not include W3C suffix: "
                        + syntheticVector.get().label());
    }

    @Test
    void rs256PresetUsesW3cFixture() {
        Fido2OperatorSampleData.InlineVector rs256 = Fido2OperatorSampleData.inlineVectors().stream()
                .filter(vector -> "RS256".equals(vector.algorithm()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing RS256 inline vector"));

        assertTrue(
                "w3c".equalsIgnoreCase(rs256.metadata().get("fixtureSource")),
                () -> "Expected RS256 fixtureSource to be W3C but was "
                        + rs256.metadata().get("fixtureSource"));
        assertTrue(
                rs256.label().endsWith(" (W3C Level 3)"),
                () -> "Expected RS256 label to include W3C suffix: " + rs256.label());
    }
}
