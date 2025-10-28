package io.openauth.sim.rest.webauthn;

import io.openauth.sim.application.fido2.WebAuthnAttestationSamples;
import io.openauth.sim.application.fido2.WebAuthnAttestationSeedService;
import io.openauth.sim.application.fido2.WebAuthnAttestationSeedService.SeedCommand;
import io.openauth.sim.application.fido2.WebAuthnAttestationSeedService.SeedResult;
import io.openauth.sim.application.fido2.WebAuthnGeneratorSamples;
import io.openauth.sim.core.fido2.WebAuthnAttestationCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.SigningMode;
import io.openauth.sim.core.fido2.WebAuthnAttestationRequest;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerification;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerifier;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.fido2.WebAuthnFixtures.WebAuthnFixture;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.fido2.WebAuthnStoredCredential;
import io.openauth.sim.core.store.CredentialStore;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webauthn/attestations")
final class WebAuthnAttestationSeedController {

    private static final Base64.Encoder PEM_ENCODER = Base64.getMimeEncoder(64, new byte[] {'\n'});
    private static final String CERT_BEGIN = "-----BEGIN CERTIFICATE-----\n";
    private static final String CERT_END = "\n-----END CERTIFICATE-----\n";

    private final WebAuthnAttestationSeedService seedService;
    private final CredentialStore credentialStore;

    WebAuthnAttestationSeedController(WebAuthnAttestationSeedService seedService, CredentialStore credentialStore) {
        this.seedService = seedService;
        this.credentialStore = credentialStore;
    }

    @PostMapping("/seed")
    @ResponseStatus(HttpStatus.OK)
    SeedResponse seedStoredAttestations() {
        List<SeedCommand> commands = buildSeedCommands();
        SeedResult result = seedService.seed(commands, credentialStore);
        return new SeedResponse(result.addedCount(), result.addedCredentialIds());
    }

    private static List<SeedCommand> buildSeedCommands() {
        WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();
        Set<WebAuthnSignatureAlgorithm> algorithms = EnumSet.noneOf(WebAuthnSignatureAlgorithm.class);
        List<SeedCommand> commands = new ArrayList<>();
        for (WebAuthnAttestationVector vector : WebAuthnAttestationSamples.vectors()) {
            if (algorithms.add(vector.algorithm())) {
                Optional<WebAuthnStoredCredential> stored = resolveStoredCredential(vector);
                if (stored.isEmpty()) {
                    algorithms.remove(vector.algorithm());
                    continue;
                }
                commands.add(buildSeedCommand(generator, stored.get(), vector));
            }
        }
        return List.copyOf(commands);
    }

    private static SeedCommand buildSeedCommand(
            WebAuthnAttestationGenerator generator,
            WebAuthnStoredCredential storedCredential,
            WebAuthnAttestationVector vector) {
        try {
            return buildSeedCommandWithGenerator(generator, storedCredential, vector);
        } catch (IllegalArgumentException ex) {
            return buildSeedCommandFromStoredCredential(storedCredential, vector);
        }
    }

    private static SeedCommand buildSeedCommandWithGenerator(
            WebAuthnAttestationGenerator generator,
            WebAuthnStoredCredential storedCredential,
            WebAuthnAttestationVector vector) {
        WebAuthnCredentialDescriptor credentialDescriptor = WebAuthnCredentialDescriptor.builder()
                .name(vector.vectorId())
                .relyingPartyId(storedCredential.relyingPartyId())
                .credentialId(storedCredential.credentialId())
                .publicKeyCose(storedCredential.publicKeyCose())
                .signatureCounter(storedCredential.signatureCounter())
                .userVerificationRequired(storedCredential.userVerificationRequired())
                .algorithm(storedCredential.algorithm())
                .build();

        WebAuthnAttestationGenerator.GenerationResult generationResult =
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

        return new SeedCommand(
                descriptor,
                generationResult.attestationObject(),
                generationResult.clientDataJson(),
                vector.registration().challenge());
    }

    private static SeedCommand buildSeedCommandFromStoredCredential(
            WebAuthnStoredCredential fallbackCredential, WebAuthnAttestationVector vector) {
        WebAuthnAttestationVerifier verifier = new WebAuthnAttestationVerifier();
        WebAuthnAttestationVerification verification = verifier.verify(new WebAuthnAttestationRequest(
                vector.format(),
                vector.registration().attestationObject(),
                vector.registration().clientDataJson(),
                vector.registration().challenge(),
                vector.relyingPartyId(),
                vector.origin()));

        WebAuthnStoredCredential attestedCredential =
                verification.attestedCredential().orElse(fallbackCredential);

        WebAuthnCredentialDescriptor credentialDescriptor = WebAuthnCredentialDescriptor.builder()
                .name(vector.vectorId())
                .relyingPartyId(attestedCredential.relyingPartyId())
                .credentialId(attestedCredential.credentialId())
                .publicKeyCose(attestedCredential.publicKeyCose())
                .signatureCounter(attestedCredential.signatureCounter())
                .userVerificationRequired(attestedCredential.userVerificationRequired())
                .algorithm(attestedCredential.algorithm())
                .build();

        List<String> certificateChainPem = convertCertificatesToPem(verification.certificateChain());

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
                .certificateChainPem(certificateChainPem)
                .customRootCertificatesPem(List.of())
                .build();

        return new SeedCommand(
                descriptor,
                vector.registration().attestationObject(),
                vector.registration().clientDataJson(),
                vector.registration().challenge());
    }

    private static List<String> convertCertificatesToPem(List<X509Certificate> certificates) {
        if (certificates == null || certificates.isEmpty()) {
            return List.of();
        }
        List<String> pemCertificates = new ArrayList<>(certificates.size());
        for (X509Certificate certificate : certificates) {
            pemCertificates.add(toPem(certificate));
        }
        return List.copyOf(pemCertificates);
    }

    private static String toPem(X509Certificate certificate) {
        try {
            String encoded = PEM_ENCODER.encodeToString(certificate.getEncoded());
            return CERT_BEGIN + encoded + CERT_END;
        } catch (CertificateEncodingException ex) {
            throw new IllegalArgumentException("Unable to encode certificate to PEM", ex);
        }
    }

    private static Optional<WebAuthnStoredCredential> resolveStoredCredential(WebAuthnAttestationVector vector) {
        Optional<WebAuthnStoredCredential> fromFixture = findFixture(vector).map(WebAuthnFixture::storedCredential);
        if (fromFixture.isPresent()) {
            return fromFixture;
        }
        return resolveGeneratorSample(vector)
                .map(sample -> new WebAuthnStoredCredential(
                        sample.relyingPartyId(),
                        sample.credentialId(),
                        sample.publicKeyCose(),
                        sample.signatureCounter(),
                        sample.userVerificationRequired(),
                        sample.algorithm()));
    }

    private static Optional<WebAuthnFixture> findFixture(WebAuthnAttestationVector vector) {
        Optional<WebAuthnFixture> exact = WebAuthnFixtures.w3cFixtures().stream()
                .filter(candidate -> candidate.id().equals(vector.vectorId()))
                .findFirst();
        if (exact.isPresent()) {
            return exact;
        }
        if (vector.vectorId().startsWith("w3c-")) {
            String trimmed = vector.vectorId().substring(4);
            exact = WebAuthnFixtures.w3cFixtures().stream()
                    .filter(candidate -> candidate.id().equals(trimmed))
                    .findFirst();
            if (exact.isPresent()) {
                return exact;
            }
        }
        return WebAuthnFixtures.w3cFixtures().stream()
                .filter(candidate -> candidate.algorithm().equals(vector.algorithm()))
                .findFirst();
    }

    private static Optional<WebAuthnGeneratorSamples.Sample> resolveGeneratorSample(WebAuthnAttestationVector vector) {
        return WebAuthnGeneratorSamples.samples().stream()
                .filter(sample -> Arrays.equals(
                        sample.credentialId(), vector.registration().credentialId()))
                .findFirst()
                .or(() -> WebAuthnGeneratorSamples.samples().stream()
                        .filter(sample -> sample.algorithm() == vector.algorithm())
                        .findFirst());
    }

    record SeedResponse(int addedCount, List<String> addedCredentialIds) {
        // marker record
    }
}
