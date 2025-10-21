package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.fido2.WebAuthnAttestationSamples;
import io.openauth.sim.core.fido2.WebAuthnAttestationCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.GenerationResult;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.SigningMode;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.fido2.WebAuthnFixtures.WebAuthnFixture;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import io.openauth.sim.infra.persistence.CredentialStoreFactory;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

@Tag("cli")
final class Fido2CliAttestationSeedTest {

    private static final List<WebAuthnAttestationVector> CANONICAL_VECTORS = selectCanonicalVectors();

    @TempDir
    Path tempDir;

    @Test
    void seedAttestationsPopulatesCuratedStore() throws Exception {
        Path database = tempDir.resolve("attestation-seed.db");
        CommandHarness harness = CommandHarness.create(database);

        int exitCode = harness.execute("seed-attestations");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        assertTrue(harness.stdout().contains("Seeded stored attestation credentials"), harness.stdout());

        try (CredentialStore store = CredentialStoreFactory.openFileStore(database)) {
            List<String> storedNames = store.findAll().stream()
                    .map(credential -> credential.name())
                    .sorted()
                    .toList();
            List<String> expectedNames = CANONICAL_VECTORS.stream()
                    .map(WebAuthnAttestationVector::vectorId)
                    .sorted()
                    .toList();
            assertEquals(expectedNames, storedNames);
        }
    }

    @Test
    void seedAttestationsSkipsExistingCredentials() throws Exception {
        Path database = tempDir.resolve("attestation-seed.db");
        try (CredentialStore store = CredentialStoreFactory.openFileStore(database)) {
            for (WebAuthnAttestationVector vector : CANONICAL_VECTORS) {
                StoredAttestationSeed seed = StoredAttestationSeed.fromVector(vector);
                store.save(VersionedCredentialRecordMapper.toCredential(
                        new io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter()
                                .serializeAttestation(seed.descriptor())));
            }
        }

        CommandHarness harness = CommandHarness.create(database);
        int exitCode = harness.execute("seed-attestations");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        assertTrue(
                harness.stdout().contains("All stored attestation credentials are already present"), harness.stdout());
    }

    private static List<WebAuthnAttestationVector> selectCanonicalVectors() {
        WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();
        Set<WebAuthnAttestationFormat> formats = new LinkedHashSet<>();
        List<WebAuthnAttestationVector> selected = new ArrayList<>();
        for (WebAuthnAttestationVector vector : WebAuthnAttestationSamples.vectors()) {
            if (formats.add(vector.format())) {
                Optional<WebAuthnFixture> fixture = findFixture(vector);
                if (fixture.isEmpty()) {
                    formats.remove(vector.format());
                    continue;
                }
                if (!isInlineCompatible(generator, vector)) {
                    formats.remove(vector.format());
                    continue;
                }
                selected.add(vector);
            }
        }
        return List.copyOf(selected);
    }

    private static final class CommandHarness {

        private final Fido2Cli cli;
        private final CommandLine commandLine;
        private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        private CommandHarness(Path database) {
            this.cli = new Fido2Cli();
            cli.overrideDatabase(database);
            this.commandLine = new CommandLine(cli);
            commandLine.setOut(new PrintWriter(stdout, true, StandardCharsets.UTF_8));
            commandLine.setErr(new PrintWriter(stderr, true, StandardCharsets.UTF_8));
        }

        static CommandHarness create(Path database) {
            return new CommandHarness(database);
        }

        int execute(String... args) {
            stdout.reset();
            stderr.reset();
            return commandLine.execute(args);
        }

        String stdout() {
            return stdout.toString(StandardCharsets.UTF_8);
        }

        String stderr() {
            return stderr.toString(StandardCharsets.UTF_8);
        }
    }

    private record StoredAttestationSeed(WebAuthnAttestationCredentialDescriptor descriptor) {

        static StoredAttestationSeed fromVector(WebAuthnAttestationVector vector) {
            WebAuthnFixture fixture = findFixture(vector)
                    .orElseThrow(() -> new IllegalStateException("Missing stored fixture for " + vector.vectorId()));

            WebAuthnCredentialDescriptor credentialDescriptor = WebAuthnCredentialDescriptor.builder()
                    .name(vector.vectorId())
                    .relyingPartyId(fixture.storedCredential().relyingPartyId())
                    .credentialId(fixture.storedCredential().credentialId())
                    .publicKeyCose(fixture.storedCredential().publicKeyCose())
                    .signatureCounter(fixture.storedCredential().signatureCounter())
                    .userVerificationRequired(fixture.storedCredential().userVerificationRequired())
                    .algorithm(fixture.algorithm())
                    .build();

            WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();
            GenerationResult generationResult =
                    generator.generate(new WebAuthnAttestationGenerator.GenerationCommand.Inline(
                            vector.vectorId(),
                            vector.format(),
                            vector.relyingPartyId(),
                            vector.origin(),
                            vector.registration().challenge(),
                            vector.keyMaterial().credentialPrivateKeyBase64Url(),
                            vector.keyMaterial().attestationPrivateKeyBase64Url(),
                            vector.keyMaterial().attestationCertificateSerialBase64Url(),
                            SigningMode.SELF_SIGNED,
                            List.of()));

            WebAuthnAttestationCredentialDescriptor descriptor = WebAuthnAttestationCredentialDescriptor.builder()
                    .name(vector.vectorId())
                    .format(vector.format())
                    .signingMode(SigningMode.SELF_SIGNED)
                    .credentialDescriptor(credentialDescriptor)
                    .relyingPartyId(vector.relyingPartyId())
                    .origin(vector.origin())
                    .attestationId(vector.vectorId())
                    .credentialPrivateKeyBase64Url(vector.keyMaterial().credentialPrivateKeyBase64Url())
                    .attestationPrivateKeyBase64Url(vector.keyMaterial().attestationPrivateKeyBase64Url())
                    .attestationCertificateSerialBase64Url(vector.keyMaterial().attestationCertificateSerialBase64Url())
                    .certificateChainPem(generationResult.certificateChainPem())
                    .customRootCertificatesPem(List.of())
                    .build();
            return new StoredAttestationSeed(descriptor);
        }
    }

    private static boolean isInlineCompatible(
            WebAuthnAttestationGenerator generator, WebAuthnAttestationVector vector) {
        try {
            generator.generate(new WebAuthnAttestationGenerator.GenerationCommand.Inline(
                    vector.vectorId(),
                    vector.format(),
                    vector.relyingPartyId(),
                    vector.origin(),
                    vector.registration().challenge(),
                    vector.keyMaterial().credentialPrivateKeyBase64Url(),
                    vector.keyMaterial().attestationPrivateKeyBase64Url(),
                    vector.keyMaterial().attestationCertificateSerialBase64Url(),
                    SigningMode.SELF_SIGNED,
                    List.of()));
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static Optional<WebAuthnFixture> findFixture(WebAuthnAttestationVector vector) {
        Optional<WebAuthnFixture> exact = WebAuthnFixtures.w3cFixtures().stream()
                .filter(candidate -> candidate.id().equals(vector.vectorId()))
                .findFirst();
        if (exact.isPresent()) {
            return exact;
        }
        return WebAuthnFixtures.w3cFixtures().stream()
                .filter(candidate -> candidate.algorithm().equals(vector.algorithm()))
                .findFirst();
    }
}
