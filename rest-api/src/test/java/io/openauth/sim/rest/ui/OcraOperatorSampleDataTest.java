package io.openauth.sim.rest.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OcraOperatorSampleDataTest {

    @Test
    @DisplayName("findByCredentialName returns sample for canonical identifier")
    void findByCredentialNameMatchesCanonical() {
        Optional<OcraOperatorSampleData.SampleDefinition> definition =
                OcraOperatorSampleData.findByCredentialName("sample-qa08-s064");

        assertThat(definition).isPresent();
        assertThat(definition.get().suite()).isEqualTo("OCRA-1:HOTP-SHA256-8:QA08-S064");
    }

    @Test
    @DisplayName("findByCredentialName resolves alias credentials")
    void findByCredentialNameResolvesAlias() {
        Optional<OcraOperatorSampleData.SampleDefinition> definition =
                OcraOperatorSampleData.findByCredentialName("operator-demo");

        assertThat(definition).isPresent();
        assertThat(definition.get().metadata().get("presetKey")).isEqualTo("qa08-s064");
    }

    @Test
    @DisplayName("findByCredentialName returns empty for blank input")
    void findByCredentialNameBlank() {
        Optional<OcraOperatorSampleData.SampleDefinition> definition =
                OcraOperatorSampleData.findByCredentialName("   ");

        assertThat(definition).isEmpty();
    }

    @Test
    @DisplayName("findByCredentialName returns empty for unknown identifier")
    void findByCredentialNameUnknown() {
        Optional<OcraOperatorSampleData.SampleDefinition> definition =
                OcraOperatorSampleData.findByCredentialName("does-not-exist");

        assertThat(definition).isEmpty();
    }

    @Test
    @DisplayName("findByPresetKey matches irrespective of case")
    void findByPresetKeyCaseInsensitive() {
        Optional<OcraOperatorSampleData.SampleDefinition> definition =
                OcraOperatorSampleData.findByPresetKey("QA08-S256");

        assertThat(definition).isPresent();
        assertThat(definition.get().expectedOtp()).isEqualTo("77715695");
    }

    @Test
    @DisplayName("findByPresetKey returns empty for blank input")
    void findByPresetKeyBlank() {
        Optional<OcraOperatorSampleData.SampleDefinition> definition = OcraOperatorSampleData.findByPresetKey("   ");

        assertThat(definition).isEmpty();
    }

    @Test
    @DisplayName("findByPresetKey returns empty for unknown preset")
    void findByPresetKeyUnknown() {
        Optional<OcraOperatorSampleData.SampleDefinition> definition =
                OcraOperatorSampleData.findByPresetKey("unknown");

        assertThat(definition).isEmpty();
    }

    @Test
    @DisplayName("findBySuite falls back to suite lookup")
    void findBySuiteMatches() {
        Optional<OcraOperatorSampleData.SampleDefinition> definition =
                OcraOperatorSampleData.findBySuite("OCRA-1:HOTP-SHA256-6:C-QH64");

        assertThat(definition).isPresent();
        assertThat(definition.get().counter()).isEqualTo(Long.valueOf(1));
    }

    @Test
    @DisplayName("findBySuite returns empty for blank suite")
    void findBySuiteBlank() {
        Optional<OcraOperatorSampleData.SampleDefinition> definition = OcraOperatorSampleData.findBySuite("  ");

        assertThat(definition).isEmpty();
    }

    @Test
    @DisplayName("SampleDefinition normalizes null metadata to empty map")
    void sampleDefinitionHandlesNullMetadata() {
        OcraOperatorSampleData.SampleDefinition definition = new OcraOperatorSampleData.SampleDefinition(
                "key",
                "label",
                "credential",
                "OCRA-1:HOTP-SHA1-6:QA08",
                "00",
                "CHALLENGE",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "123456");

        assertThat(definition.metadata()).isEmpty();
    }
}
