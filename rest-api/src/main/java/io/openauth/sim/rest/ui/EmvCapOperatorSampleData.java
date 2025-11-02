package io.openauth.sim.rest.ui;

import io.openauth.sim.application.emv.cap.EmvCapSeedSamples;
import io.openauth.sim.application.emv.cap.EmvCapSeedSamples.SeedSample;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** Provides EMV/CAP seed metadata for operator console affordances. */
final class EmvCapOperatorSampleData {

    private static final String META_PRESET_LABEL = "presetLabel";
    private static final String META_PRESET_KEY = "presetKey";
    private static final String META_DESCRIPTION = "description";
    private static final String META_VECTOR_ID = "vectorId";

    private EmvCapOperatorSampleData() {
        // utility
    }

    static List<SeedDefinition> seedDefinitions() {
        return EmvCapSeedSamples.samples().stream()
                .map(EmvCapOperatorSampleData::toSeedDefinition)
                .collect(Collectors.toUnmodifiableList());
    }

    private static SeedDefinition toSeedDefinition(SeedSample sample) {
        Map<String, String> metadata = sample.metadata();
        String label = metadata.getOrDefault(META_PRESET_LABEL, sample.credentialId());
        String presetKey = metadata.getOrDefault(META_PRESET_KEY, sample.credentialId());
        String description = metadata.getOrDefault(META_DESCRIPTION, "");
        String vectorId = metadata.getOrDefault(META_VECTOR_ID, sample.vectorId());
        return new SeedDefinition(
                sample.credentialId(),
                requireText(label, "label"),
                requireText(presetKey, "presetKey"),
                requireText(sample.mode().name(), "mode"),
                requireText(sample.vectorId(), "vectorId"),
                vectorId,
                description);
    }

    private static String requireText(String value, String field) {
        return Optional.ofNullable(value)
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .orElseThrow(() -> new IllegalStateException("Seed definition missing " + field));
    }

    record SeedDefinition(
            String credentialId,
            String label,
            String presetKey,
            String mode,
            String canonicalVectorId,
            String vectorId,
            String description) {

        SeedDefinition {
            Objects.requireNonNull(credentialId, "credentialId");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(presetKey, "presetKey");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(canonicalVectorId, "canonicalVectorId");
            Objects.requireNonNull(vectorId, "vectorId");
            description = description == null ? "" : description.trim();
        }
    }
}
