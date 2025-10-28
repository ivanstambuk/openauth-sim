package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.fido2.WebAuthnAttestationSamples;
import io.openauth.sim.application.fido2.WebAuthnAttestationSeedService;
import io.openauth.sim.application.fido2.WebAuthnAttestationSeedService.SeedCommand;
import io.openauth.sim.application.fido2.WebAuthnGeneratorSamples;
import io.openauth.sim.core.fido2.WebAuthnAttestationCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.GenerationResult;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.SigningMode;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.fido2.WebAuthnFixtures.WebAuthnFixture;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.infra.persistence.CredentialStoreFactory;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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
                    .map(Fido2CliAttestationSeedTest::resolveSampleFor)
                    .map(WebAuthnGeneratorSamples.Sample::key)
                    .distinct()
                    .sorted()
                    .toList();
            assertEquals(expectedNames, storedNames);

            String ps256Key = WebAuthnGeneratorSamples.samples().stream()
                    .filter(sample -> sample.algorithm() == io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm.PS256)
                    .map(WebAuthnGeneratorSamples.Sample::key)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("PS256 generator preset unavailable"));
            assertTrue(storedNames.contains(ps256Key), "expected PS256 stored credential to be seeded");
        }
    }

    @Test
    void seedAttestationsSkipsExistingCredentials() throws Exception {
        Path database = tempDir.resolve("attestation-seed.db");
        WebAuthnAttestationSeedService seedService = new WebAuthnAttestationSeedService();
        List<SeedCommand> seedCommands = CANONICAL_VECTORS.stream()
                .map(StoredAttestationSeed::fromVector)
                .map(StoredAttestationSeed::toSeedCommand)
                .toList();
        try (CredentialStore store = CredentialStoreFactory.openFileStore(database)) {
            seedService.seed(seedCommands, store);
        }

        CommandHarness harness = CommandHarness.create(database);
        int exitCode = harness.execute("seed-attestations");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        assertTrue(
                harness.stdout().contains("All stored attestation credentials are already present"), harness.stdout());
    }

    @Test
    void canonicalVectorsIncludeSyntheticPackedPs256() {
        boolean present =
                CANONICAL_VECTORS.stream().anyMatch(vector -> "synthetic-packed-ps256".equals(vector.vectorId()));

        assertTrue(
                present,
                "expected CLI canonical attestation catalogue to include synthetic-packed-ps256 so PS256 seeds are available");
    }

    private static List<WebAuthnAttestationVector> selectCanonicalVectors() {
        WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();
        Set<WebAuthnSignatureAlgorithm> algorithms = new LinkedHashSet<>();
        List<WebAuthnAttestationVector> selected = new ArrayList<>();
        for (WebAuthnAttestationVector vector : WebAuthnAttestationSamples.vectors()) {
            if (!algorithms.add(vector.algorithm())) {
                continue;
            }

            Optional<WebAuthnFixture> fixture = findFixture(vector);
            boolean supported = true;
            if (fixture.isEmpty()) {
                try {
                    resolveSampleFor(vector);
                } catch (IllegalStateException ex) {
                    supported = false;
                }
            }

            if (!supported || !isInlineCompatible(generator, vector)) {
                algorithms.remove(vector.algorithm());
                continue;
            }

            selected.add(vector);
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

    private record StoredAttestationSeed(
            WebAuthnAttestationCredentialDescriptor descriptor,
            byte[] attestationObject,
            byte[] clientDataJson,
            byte[] expectedChallenge) {

        static StoredAttestationSeed fromVector(WebAuthnAttestationVector vector) {
            Optional<WebAuthnFixture> fixture = findFixture(vector);
            WebAuthnCredentialDescriptor credentialDescriptor;
            if (fixture.isPresent()) {
                WebAuthnFixture resolved = fixture.get();
                credentialDescriptor = WebAuthnCredentialDescriptor.builder()
                        .name(vector.vectorId())
                        .relyingPartyId(resolved.storedCredential().relyingPartyId())
                        .credentialId(resolved.storedCredential().credentialId())
                        .publicKeyCose(resolved.storedCredential().publicKeyCose())
                        .signatureCounter(resolved.storedCredential().signatureCounter())
                        .userVerificationRequired(resolved.storedCredential().userVerificationRequired())
                        .algorithm(resolved.algorithm())
                        .build();
            } else {
                WebAuthnGeneratorSamples.Sample sample = resolveSampleFor(vector);
                credentialDescriptor = WebAuthnCredentialDescriptor.builder()
                        .name(vector.vectorId())
                        .relyingPartyId(sample.relyingPartyId())
                        .credentialId(sample.credentialId())
                        .publicKeyCose(sample.publicKeyCose())
                        .signatureCounter(sample.signatureCounter())
                        .userVerificationRequired(sample.userVerificationRequired())
                        .algorithm(sample.algorithm())
                        .build();
            }

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
            return new StoredAttestationSeed(
                    descriptor,
                    generationResult.attestationObject(),
                    generationResult.clientDataJson(),
                    vector.registration().challenge());
        }

        SeedCommand toSeedCommand() {
            return new SeedCommand(
                    descriptor,
                    Arrays.copyOf(attestationObject, attestationObject.length),
                    Arrays.copyOf(clientDataJson, clientDataJson.length),
                    Arrays.copyOf(expectedChallenge, expectedChallenge.length));
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

    private static WebAuthnGeneratorSamples.Sample resolveSampleFor(WebAuthnAttestationVector vector) {
        return WebAuthnGeneratorSamples.samples().stream()
                .filter(sample -> Arrays.equals(
                        sample.credentialId(), vector.registration().credentialId()))
                .findFirst()
                .or(() -> WebAuthnGeneratorSamples.samples().stream()
                        .filter(sample -> sample.algorithm() == vector.algorithm())
                        .findFirst())
                .orElseThrow(() -> new IllegalStateException("Unable to resolve generator sample for " + vector));
    }
}
