package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.fido2.WebAuthnAttestationCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.GenerationCommand;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.GenerationResult;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.SigningMode;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.fido2.WebAuthnFixtures.WebAuthnFixture;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import io.openauth.sim.infra.persistence.CredentialStoreFactory;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

@Tag("cli")
final class Fido2CliAttestationReplayStoredTest {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    @TempDir
    Path tempDir;

    @Test
    void replayStoredUsesPersistedAttestationPayload() throws Exception {
        Path database = tempDir.resolve("fido2-attestation-replay.db");
        CommandHarness harness = CommandHarness.create(database);
        StoredReplaySeed seed = harness.savePackedReplaySeed("stored-packed-es256");

        int exitCode = harness.execute(
                "attest-replay",
                "--input-source",
                "stored",
                "--credential-id",
                seed.credentialName(),
                "--format",
                seed.format().label());

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("event=cli.fido2.attestReplay status=success"), stdout);
        assertTrue(stdout.contains("inputSource=stored"), stdout);
        assertTrue(stdout.contains("storedCredentialId=" + seed.credentialName()), stdout);
        assertTrue(
                stdout.contains("reasonCode=match")
                        || stdout.toLowerCase(Locale.ROOT).contains("reasoncode=match"),
                stdout);
    }

    private static String encode(byte[] value) {
        return URL_ENCODER.encodeToString(value);
    }

    private static final class StoredReplaySeed {
        private final String credentialName;
        private final WebAuthnAttestationFormat format;

        private StoredReplaySeed(String credentialName, WebAuthnAttestationFormat format) {
            this.credentialName = credentialName;
            this.format = format;
        }

        private String credentialName() {
            return credentialName;
        }

        private WebAuthnAttestationFormat format() {
            return format;
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

        StoredReplaySeed savePackedReplaySeed(String credentialName) throws Exception {
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
            GenerationResult generation = generator.generate(new GenerationCommand.Inline(
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
                    .certificateChainPem(generation.certificateChainPem())
                    .customRootCertificatesPem(List.of())
                    .build();

            try (CredentialStore store = CredentialStoreFactory.openFileStore(database)) {
                VersionedCredentialRecord record = new io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter()
                        .serializeAttestation(descriptor);

                LinkedHashMap<String, String> attributes = new LinkedHashMap<>(record.attributes());
                attributes.put("fido2.attestation.stored.attestationObject", encode(generation.attestationObject()));
                attributes.put("fido2.attestation.stored.clientDataJson", encode(generation.clientDataJson()));
                attributes.put(
                        "fido2.attestation.stored.expectedChallenge",
                        encode(vector.registration().challenge()));

                VersionedCredentialRecord enriched = new VersionedCredentialRecord(
                        record.schemaVersion(),
                        record.name(),
                        record.type(),
                        record.secret(),
                        record.createdAt(),
                        record.updatedAt(),
                        attributes);

                Credential credential = VersionedCredentialRecordMapper.toCredential(enriched);
                store.save(credential);
            }

            return new StoredReplaySeed(credentialName, vector.format());
        }
    }
}
