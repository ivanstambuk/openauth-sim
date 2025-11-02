package io.openauth.sim.application.emv.cap;

import io.openauth.sim.application.emv.cap.EmvCapSeedApplicationService.SeedCommand;
import io.openauth.sim.core.emv.cap.EmvCapMode;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures.EmvCapVector;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures.Resolved;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Canonical EMV/CAP seed samples shared across facades. */
public final class EmvCapSeedSamples {

    private static final List<SeedSample> SAMPLES = List.of(
            new SeedSample(
                    "emv-cap-identify-baseline",
                    "identify-baseline",
                    EmvCapMode.IDENTIFY,
                    Map.of(
                            "presetKey", "emv-cap.identify.baseline",
                            "presetLabel", "CAP Identify baseline",
                            "description", "Identify mode baseline sourced from emvlab.org (2025-11-01)",
                            "vectorId", "identify-baseline")),
            new SeedSample(
                    "emv-cap-respond-baseline",
                    "respond-baseline",
                    EmvCapMode.RESPOND,
                    Map.of(
                            "presetKey", "emv-cap.respond.baseline",
                            "presetLabel", "CAP Respond baseline",
                            "description", "Respond mode baseline sourced from emvlab.org (2025-11-01)",
                            "vectorId", "respond-baseline")),
            new SeedSample(
                    "emv-cap-sign-baseline",
                    "sign-baseline",
                    EmvCapMode.SIGN,
                    Map.of(
                            "presetKey", "emv-cap.sign.baseline",
                            "presetLabel", "CAP Sign baseline",
                            "description", "Sign mode baseline sourced from emvlab.org (2025-11-01)",
                            "vectorId", "sign-baseline")));

    private EmvCapSeedSamples() {
        // utility class
    }

    /** @return immutable catalogue of canonical seed samples. */
    public static List<SeedSample> samples() {
        return SAMPLES;
    }

    /** Representation of a canonical EMV/CAP seed sample. */
    public record SeedSample(String credentialId, String vectorId, EmvCapMode mode, Map<String, String> metadata) {

        public SeedSample {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }

        /** Load the backing vector for this sample. */
        public EmvCapVector vector() {
            return EmvCapVectorFixtures.load(vectorId);
        }

        /** Convert this sample to a {@link SeedCommand}. */
        public SeedCommand toSeedCommand() {
            EmvCapVector vector = vector();
            return new SeedCommand(
                    credentialId,
                    mode,
                    vector.input().masterKeyHex(),
                    vector.input().atcHex(),
                    vector.input().branchFactor(),
                    vector.input().height(),
                    vector.input().ivHex(),
                    vector.input().cdol1Hex(),
                    vector.input().issuerProprietaryBitmapHex(),
                    vector.input().iccDataTemplateHex(),
                    vector.input().issuerApplicationDataHex(),
                    vector.input().customerInputs().challenge(),
                    vector.input().customerInputs().reference(),
                    vector.input().customerInputs().amount(),
                    optionalOf(vector.outputs().generateAcInputTerminalHex()),
                    optionalOf(vector.outputs().generateAcInputIccHex()),
                    Optional.ofNullable(vector.resolved())
                            .map(Resolved::iccDataHex)
                            .flatMap(EmvCapSeedSamples::optionalOf),
                    metadata);
        }

        private static Optional<String> optionalOf(String value) {
            if (value == null) {
                return Optional.empty();
            }
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(trimmed);
        }
    }

    private static Optional<String> optionalOf(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(trimmed);
    }
}
