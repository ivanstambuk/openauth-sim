package io.openauth.sim.rest.webauthn;

import io.openauth.sim.application.fido2.WebAuthnAttestationSamples;
import io.openauth.sim.application.fido2.WebAuthnAttestationSeedService;
import io.openauth.sim.application.fido2.WebAuthnAttestationSeedService.SeedCommand;
import io.openauth.sim.application.fido2.WebAuthnAttestationSeedService.SeedResult;
import io.openauth.sim.core.fido2.WebAuthnAttestationCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.SigningMode;
import io.openauth.sim.core.fido2.WebAuthnAttestationRequest;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerification;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerifier;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.fido2.WebAuthnFixtures.WebAuthnFixture;
import io.openauth.sim.core.fido2.WebAuthnStoredCredential;
import io.openauth.sim.core.store.CredentialStore;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
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
        Set<WebAuthnAttestationFormat> formats = new LinkedHashSet<>();
        List<SeedCommand> commands = new ArrayList<>();
        for (WebAuthnAttestationVector vector : WebAuthnAttestationSamples.vectors()) {
            if (formats.add(vector.format())) {
                WebAuthnFixture fixture = resolveFixture(vector);
                commands.add(buildSeedCommand(generator, fixture, vector));
            }
        }
        return List.copyOf(commands);
    }

    private static SeedCommand buildSeedCommand(
            WebAuthnAttestationGenerator generator, WebAuthnFixture fixture, WebAuthnAttestationVector vector) {
        try {
            return buildSeedCommandWithGenerator(generator, fixture, vector);
        } catch (IllegalArgumentException ex) {
            return buildSeedCommandFromFixture(fixture, vector);
        }
    }

    private static SeedCommand buildSeedCommandWithGenerator(
            WebAuthnAttestationGenerator generator, WebAuthnFixture fixture, WebAuthnAttestationVector vector) {
        WebAuthnCredentialDescriptor credentialDescriptor = WebAuthnCredentialDescriptor.builder()
                .name(vector.vectorId())
                .relyingPartyId(fixture.storedCredential().relyingPartyId())
                .credentialId(fixture.storedCredential().credentialId())
                .publicKeyCose(fixture.storedCredential().publicKeyCose())
                .signatureCounter(fixture.storedCredential().signatureCounter())
                .userVerificationRequired(fixture.storedCredential().userVerificationRequired())
                .algorithm(fixture.algorithm())
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

    private static SeedCommand buildSeedCommandFromFixture(
            WebAuthnFixture fallbackFixture, WebAuthnAttestationVector vector) {
        WebAuthnAttestationVerifier verifier = new WebAuthnAttestationVerifier();
        WebAuthnAttestationVerification verification = verifier.verify(new WebAuthnAttestationRequest(
                vector.format(),
                vector.registration().attestationObject(),
                vector.registration().clientDataJson(),
                vector.registration().challenge(),
                vector.relyingPartyId(),
                vector.origin()));

        WebAuthnStoredCredential attestedCredential =
                verification.attestedCredential().orElseGet(() -> fallbackFixture.storedCredential());

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

    private static WebAuthnFixture resolveFixture(WebAuthnAttestationVector vector) {
        return findFixture(vector)
                .orElseThrow(() -> new IllegalStateException("Missing fixture for " + vector.vectorId()));
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

    record SeedResponse(int addedCount, List<String> addedCredentialIds) {
        // marker record
    }
}
