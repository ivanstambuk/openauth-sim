package io.openauth.sim.application.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.telemetry.Fido2TelemetryAdapter;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationRequest;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerification;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerifier;
import io.openauth.sim.core.fido2.WebAuthnVerificationError;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class WebAuthnAttestationVerificationApplicationServiceVerboseTraceTest {

    private WebAuthnAttestationVerificationApplicationService service;
    private WebAuthnAttestationVector vector;
    private List<X509Certificate> trustAnchors;

    @BeforeEach
    void setUp() {
        service = new WebAuthnAttestationVerificationApplicationService(
                new WebAuthnAttestationVerifier(), new Fido2TelemetryAdapter("fido2.attest"));

        vector = WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
                .findFirst()
                .orElseThrow();

        WebAuthnAttestationVerification verification = new WebAuthnAttestationVerifier().verify(toRequest(vector));
        trustAnchors = verification.certificateChain().isEmpty()
                ? List.of()
                : List.of(verification
                        .certificateChain()
                        .get(verification.certificateChain().size() - 1));

        if (trustAnchors.isEmpty()) {
            throw new IllegalStateException("Attestation fixture missing trust anchor");
        }
    }

    private static WebAuthnAttestationRequest toRequest(WebAuthnAttestationVector vector) {
        return new WebAuthnAttestationRequest(
                vector.format(),
                vector.registration().attestationObject(),
                vector.registration().clientDataJson(),
                vector.registration().challenge(),
                vector.relyingPartyId(),
                vector.origin());
    }

    @Test
    void attestationVerificationWithVerboseCapturesTrace() {
        WebAuthnAttestationVerificationApplicationService.VerificationCommand.Inline command =
                new WebAuthnAttestationVerificationApplicationService.VerificationCommand.Inline(
                        vector.vectorId(),
                        vector.format(),
                        vector.relyingPartyId(),
                        vector.origin(),
                        vector.registration().attestationObject(),
                        vector.registration().clientDataJson(),
                        vector.registration().challenge(),
                        trustAnchors,
                        false,
                        WebAuthnTrustAnchorResolver.Source.MANUAL,
                        null,
                        List.of());

        WebAuthnAttestationVerificationApplicationService.VerificationResult result = service.verify(command, true);

        assertTrue(result.verboseTrace().isPresent());
        var trace = result.verboseTrace().orElseThrow();
        assertEquals("fido2.attestation.verify", trace.operation());
        assertEquals("FIDO2", trace.metadata().get("protocol"));
        assertEquals(vector.format().label(), trace.metadata().get("format"));

        assertTrue(trace.steps().stream().anyMatch(step -> "parse.request".equals(step.id())));
        assertTrue(trace.steps().stream().anyMatch(step -> "verify.attestation".equals(step.id())));
        assertTrue(trace.steps().stream().anyMatch(step -> "assemble.result".equals(step.id())));
    }

    @Test
    void verboseDisabledLeavesTraceEmpty() {
        WebAuthnAttestationVerificationApplicationService.VerificationCommand.Inline command =
                new WebAuthnAttestationVerificationApplicationService.VerificationCommand.Inline(
                        vector.vectorId(),
                        vector.format(),
                        vector.relyingPartyId(),
                        vector.origin(),
                        vector.registration().attestationObject(),
                        vector.registration().clientDataJson(),
                        vector.registration().challenge(),
                        trustAnchors,
                        false,
                        WebAuthnTrustAnchorResolver.Source.MANUAL,
                        null,
                        List.of());

        WebAuthnAttestationVerificationApplicationService.VerificationResult result = service.verify(command);

        assertTrue(result.verboseTrace().isEmpty());
    }

    @Test
    void invalidAttestationRecordsFailureInTrace() {
        byte[] tamperedChallenge = vector.registration().challenge().clone();
        tamperedChallenge[0] ^= 0x02;

        WebAuthnAttestationVerificationApplicationService.VerificationCommand.Inline command =
                new WebAuthnAttestationVerificationApplicationService.VerificationCommand.Inline(
                        vector.vectorId(),
                        vector.format(),
                        vector.relyingPartyId(),
                        vector.origin(),
                        vector.registration().attestationObject(),
                        vector.registration().clientDataJson(),
                        tamperedChallenge,
                        trustAnchors,
                        false,
                        WebAuthnTrustAnchorResolver.Source.MANUAL,
                        null,
                        List.of());

        WebAuthnAttestationVerificationApplicationService.VerificationResult result = service.verify(command, true);

        assertTrue(result.verboseTrace().isPresent());
        var trace = result.verboseTrace().orElseThrow();
        assertTrue(trace.steps().stream()
                .anyMatch(step -> "verify.attestation".equals(step.id())
                        && Boolean.FALSE.equals(step.attributes().get("valid"))));
        assertEquals(Optional.of(WebAuthnVerificationError.CLIENT_DATA_CHALLENGE_MISMATCH), result.error());
    }
}
