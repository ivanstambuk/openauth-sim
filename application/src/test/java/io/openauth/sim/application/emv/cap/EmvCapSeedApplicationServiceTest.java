package io.openauth.sim.application.emv.cap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.emv.cap.EmvCapSeedApplicationService.SeedCommand;
import io.openauth.sim.application.emv.cap.EmvCapSeedApplicationService.SeedResult;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures.EmvCapVector;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures.Resolved;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.CredentialStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Specification tests for {@link EmvCapSeedApplicationService}. */
final class EmvCapSeedApplicationServiceTest {

    private static final List<SeedSample> CANONICAL_SAMPLES = List.of(
            new SeedSample(
                    "emv-cap-identify-baseline",
                    EmvCapVectorFixtures.load("identify-baseline"),
                    Map.of(
                            "presetKey", "emv-cap.identify.baseline",
                            "presetLabel", "CAP Identify baseline",
                            "description", "Identify mode baseline sourced from emvlab.org (2025-11-01)")),
            new SeedSample(
                    "emv-cap-respond-baseline",
                    EmvCapVectorFixtures.load("respond-baseline"),
                    Map.of(
                            "presetKey", "emv-cap.respond.baseline",
                            "presetLabel", "CAP Respond baseline",
                            "description", "Respond mode baseline sourced from emvlab.org (2025-11-01)")),
            new SeedSample(
                    "emv-cap-sign-baseline",
                    EmvCapVectorFixtures.load("sign-baseline"),
                    Map.of(
                            "presetKey", "emv-cap.sign.baseline",
                            "presetLabel", "CAP Sign baseline",
                            "description", "Sign mode baseline sourced from emvlab.org (2025-11-01)")));

    private final EmvCapSeedApplicationService service = new EmvCapSeedApplicationService();
    private final CapturingCredentialStore credentialStore = new CapturingCredentialStore();

    @BeforeEach
    void resetStore() {
        credentialStore.reset();
    }

    @Test
    @DisplayName("Seeds canonical EMV/CAP credentials when missing")
    void seedsCanonicalEmvCapCredentials() {
        List<SeedCommand> commands =
                CANONICAL_SAMPLES.stream().map(SeedSample::toSeedCommand).toList();

        SeedResult result = service.seed(commands, credentialStore);

        List<String> expectedIds = CANONICAL_SAMPLES.stream()
                .map(SeedSample::credentialId)
                .sorted()
                .toList();

        assertEquals(expectedIds, result.addedCredentialIds().stream().sorted().toList());

        assertEquals(CANONICAL_SAMPLES.size(), credentialStore.stored().size());

        for (SeedSample sample : CANONICAL_SAMPLES) {
            Credential credential =
                    credentialStore.findByName(sample.credentialId()).orElseThrow();
            assertEquals(CredentialType.EMV_CA, credential.type());
            assertEquals(
                    sample.vector().input().masterKeyHex(),
                    credential.secret().asHex().toUpperCase());

            Map<String, String> attributes = credential.attributes();
            assertEquals(
                    Integer.toString(sample.vector().input().branchFactor()), attributes.get("emv.cap.branchFactor"));
            assertEquals(Integer.toString(sample.vector().input().height()), attributes.get("emv.cap.height"));
            assertEquals(sample.vector().input().ivHex(), attributes.get("emv.cap.iv"));
            assertEquals(sample.vector().input().cdol1Hex(), attributes.get("emv.cap.cdol1"));
            assertEquals(
                    sample.vector().input().issuerProprietaryBitmapHex(),
                    attributes.get("emv.cap.issuerProprietaryBitmap"));
            assertEquals(sample.vector().input().iccDataTemplateHex(), attributes.get("emv.cap.icc.template"));
            assertEquals(
                    sample.vector().input().issuerApplicationDataHex(),
                    attributes.get("emv.cap.issuerApplicationData"));
            assertEquals(sample.vector().input().atcHex(), attributes.get("emv.cap.defaults.atc"));
            assertEquals(
                    sample.vector().input().customerInputs().challenge(), attributes.get("emv.cap.defaults.challenge"));
            assertEquals(
                    sample.vector().input().customerInputs().reference(), attributes.get("emv.cap.defaults.reference"));
            assertEquals(sample.vector().input().customerInputs().amount(), attributes.get("emv.cap.defaults.amount"));
            assertEquals(sample.vector().input().mode().name(), attributes.get("emv.cap.mode"));

            optionalOf(sample.vector().outputs().generateAcInputTerminalHex())
                    .ifPresent(value -> assertEquals(value, attributes.get("emv.cap.transaction.terminal")));

            optionalOf(sample.vector().outputs().generateAcInputIccHex())
                    .ifPresent(value -> assertEquals(value, attributes.get("emv.cap.transaction.icc")));

            Optional.ofNullable(sample.vector().resolved())
                    .map(Resolved::iccDataHex)
                    .flatMap(EmvCapSeedApplicationServiceTest::optionalOf)
                    .ifPresent(value -> assertEquals(value, attributes.get("emv.cap.transaction.iccResolved")));

            sample.metadata()
                    .forEach((key, value) -> assertEquals(
                            value,
                            attributes.get("emv.cap.metadata." + key),
                            () -> "Missing metadata attribute emv.cap.metadata." + key));
        }

        assertIterableEquals(
                CANONICAL_SAMPLES.stream()
                        .map(sample -> Operation.save(sample.credentialId(), CredentialType.EMV_CA))
                        .toList(),
                credentialStore.operations());
    }

    @Test
    @DisplayName("Skips existing EMV/CAP credential identifiers during reseeding")
    void skipsExistingCredentialsDuringReseed() {
        SeedSample existing = CANONICAL_SAMPLES.get(0);
        existing.save(credentialStore);
        credentialStore.resetOperations();

        SeedResult result = service.seed(
                CANONICAL_SAMPLES.stream().map(SeedSample::toSeedCommand).toList(), credentialStore);

        List<String> expected = CANONICAL_SAMPLES.stream()
                .map(SeedSample::credentialId)
                .filter(id -> !id.equals(existing.credentialId()))
                .sorted()
                .toList();
        assertEquals(expected, result.addedCredentialIds().stream().sorted().toList());

        assertTrue(credentialStore.operations().stream()
                .allMatch(operation -> operation.credentialType() == CredentialType.EMV_CA));
    }

    private record SeedSample(String credentialId, EmvCapVector vector, Map<String, String> metadata) {

        SeedSample {
            Objects.requireNonNull(credentialId, "credentialId");
            Objects.requireNonNull(vector, "vector");
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }

        SeedCommand toSeedCommand() {
            return new SeedCommand(
                    credentialId,
                    vector.input().mode(),
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
                            .flatMap(EmvCapSeedApplicationServiceTest::optionalOf),
                    metadata);
        }

        void save(CapturingCredentialStore store) {
            store.save(credentialId, vector);
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

    private static final class CapturingCredentialStore implements CredentialStore {

        private final Map<String, Credential> store = new LinkedHashMap<>();
        private final List<Operation> operations = new ArrayList<>();

        void reset() {
            store.clear();
            operations.clear();
        }

        void resetOperations() {
            operations.clear();
        }

        List<Credential> stored() {
            return List.copyOf(store.values());
        }

        List<Operation> operations() {
            return List.copyOf(operations);
        }

        void save(String credentialId, EmvCapVector vector) {
            store.put(
                    credentialId,
                    new Credential(
                            credentialId,
                            CredentialType.EMV_CA,
                            SecretMaterial.fromHex(vector.input().masterKeyHex()),
                            Map.of("emv.cap.icc.template", vector.input().iccDataTemplateHex()),
                            Instant.EPOCH,
                            Instant.EPOCH));
        }

        @Override
        public void save(Credential credential) {
            store.put(credential.name(), credential);
            operations.add(Operation.save(credential.name(), credential.type()));
        }

        @Override
        public Optional<Credential> findByName(String name) {
            return Optional.ofNullable(store.get(name));
        }

        @Override
        public List<Credential> findAll() {
            return List.copyOf(store.values());
        }

        @Override
        public boolean delete(String name) {
            return store.remove(name) != null;
        }

        @Override
        public void close() {
            // no-op
        }
    }

    private record Operation(OperationType type, String credentialId, CredentialType credentialType) {

        static Operation save(String credentialId, CredentialType credentialType) {
            return new Operation(OperationType.SAVE, credentialId, credentialType);
        }

        enum OperationType {
            SAVE
        }
    }
}
