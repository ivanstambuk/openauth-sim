package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.fido2.WebAuthnAttestationCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.GenerationResult;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.SigningMode;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.fido2.WebAuthnFixtures.WebAuthnFixture;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import io.openauth.sim.infra.persistence.CredentialStoreFactory;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

@Tag("cli")
final class Fido2CliAttestationStoredTest {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    @TempDir
    Path tempDir;

    @Test
    void attestStoredGeneratesAttestationForPersistedCredential() throws Exception {
        Path database = tempDir.resolve("fido2-attestation-stored.db");
        CommandHarness harness = CommandHarness.create(database);
        StoredAttestationSeed seed = harness.savePackedSeed("stored-packed-es256");

        int exitCode = harness.execute(
                "attest",
                "--input-source",
                "stored",
                "--format",
                seed.format().label(),
                "--credential-id",
                seed.credentialName(),
                "--relying-party-id",
                seed.relyingPartyId(),
                "--origin",
                seed.origin(),
                "--challenge",
                seed.challengeBase64());

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("Generated attestation"), stdout);
        assertTrue(stdout.contains("inputSource=stored"), stdout);
        assertTrue(stdout.contains("storedCredentialId=" + seed.credentialName()), stdout);
        assertTrue(stdout.contains("response.attestationObject=" + seed.expectedAttestationObject()), stdout);
        assertTrue(stdout.contains("response.clientDataJSON=" + seed.expectedClientData()), stdout);
    }

    @Test
    void attestStoredRequiresCredentialId() throws Exception {
        Path database = tempDir.resolve("fido2-attestation-stored.db");
        CommandHarness harness = CommandHarness.create(database);
        harness.savePackedSeed("stored-packed-es256");

        int exitCode = harness.execute(
                "attest",
                "--input-source",
                "stored",
                "--format",
                "packed",
                "--challenge",
                encode("stored-challenge".getBytes(StandardCharsets.UTF_8)));

        assertEquals(CommandLine.ExitCode.USAGE, exitCode, harness.stderr());
        String stderr = harness.stderr();
        assertTrue(stderr.contains("reasonCode=credential_id_required"), stderr);
    }

    @Test
    void attestStoredFailsWhenCredentialNotFound() throws Exception {
        Path database = tempDir.resolve("fido2-attestation-stored.db");
        CommandHarness harness = CommandHarness.create(database);

        int exitCode = harness.execute(
                "attest",
                "--input-source",
                "stored",
                "--format",
                "packed",
                "--credential-id",
                "missing-attestation",
                "--challenge",
                encode("stored-challenge".getBytes(StandardCharsets.UTF_8)));

        assertEquals(CommandLine.ExitCode.USAGE, exitCode, harness.stderr());
        String stderr = harness.stderr();
        assertTrue(stderr.contains("reasonCode=stored_credential_not_found"), stderr);
    }

    private static String encode(byte[] value) {
        return URL_ENCODER.encodeToString(value);
    }

    private record StoredAttestationSeed(
            String credentialName,
            WebAuthnAttestationFormat format,
            String relyingPartyId,
            String origin,
            byte[] challenge,
            String expectedAttestationObject,
            String expectedClientData) {

        String challengeBase64() {
            return encode(challenge);
        }
    }

    private static final class CommandHarness {

        private final Fido2Cli cli;
        private final CommandLine commandLine;
        private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        private final Path database;

        private CommandHarness(Path database) {
            this.database = database;
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

        StoredAttestationSeed savePackedSeed(String credentialName) throws Exception {
            WebAuthnAttestationVector vector =
                    WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("Missing packed attestation vector"));
            WebAuthnFixture fixture = WebAuthnFixtures.loadPackedEs256();

            WebAuthnCredentialDescriptor credentialDescriptor = WebAuthnCredentialDescriptor.builder()
                    .name(credentialName)
                    .relyingPartyId(fixture.storedCredential().relyingPartyId())
                    .credentialId(fixture.storedCredential().credentialId())
                    .publicKeyCose(fixture.storedCredential().publicKeyCose())
                    .signatureCounter(fixture.storedCredential().signatureCounter())
                    .userVerificationRequired(fixture.storedCredential().userVerificationRequired())
                    .algorithm(WebAuthnSignatureAlgorithm.ES256)
                    .build();

            WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();
            byte[] challenge = vector.registration().challenge();
            GenerationResult seedResult = generator.generate(new WebAuthnAttestationGenerator.GenerationCommand.Inline(
                    vector.vectorId(),
                    vector.format(),
                    vector.relyingPartyId(),
                    vector.origin(),
                    challenge,
                    vector.keyMaterial().credentialPrivateKeyBase64Url(),
                    vector.keyMaterial().attestationPrivateKeyBase64Url(),
                    vector.keyMaterial().attestationCertificateSerialBase64Url(),
                    SigningMode.SELF_SIGNED,
                    List.of()));

            WebAuthnAttestationCredentialDescriptor descriptor = WebAuthnAttestationCredentialDescriptor.builder()
                    .name(credentialName)
                    .format(vector.format())
                    .signingMode(SigningMode.SELF_SIGNED)
                    .credentialDescriptor(credentialDescriptor)
                    .relyingPartyId(vector.relyingPartyId())
                    .origin(vector.origin())
                    .attestationId(vector.vectorId())
                    .credentialPrivateKeyBase64Url(vector.keyMaterial().credentialPrivateKeyBase64Url())
                    .attestationPrivateKeyBase64Url(vector.keyMaterial().attestationPrivateKeyBase64Url())
                    .attestationCertificateSerialBase64Url(vector.keyMaterial().attestationCertificateSerialBase64Url())
                    .certificateChainPem(seedResult.certificateChainPem())
                    .customRootCertificatesPem(List.of())
                    .build();

            try (CredentialStore store = CredentialStoreFactory.openFileStore(database)) {
                Credential credential = VersionedCredentialRecordMapper.toCredential(
                        new io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter()
                                .serializeAttestation(descriptor));
                store.save(credential);
            }

            return new StoredAttestationSeed(
                    credentialName,
                    vector.format(),
                    vector.relyingPartyId(),
                    vector.origin(),
                    challenge,
                    encode(seedResult.attestationObject()),
                    encode(seedResult.clientDataJson()));
        }
    }
}
