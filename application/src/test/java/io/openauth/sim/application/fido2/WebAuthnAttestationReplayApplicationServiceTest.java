package io.openauth.sim.application.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.fido2.WebAuthnAttestationReplayApplicationService.ReplayCommand;
import io.openauth.sim.application.fido2.WebAuthnAttestationReplayApplicationService.ReplayResult;
import io.openauth.sim.application.fido2.WebAuthnAttestationReplayApplicationService.TelemetrySignal;
import io.openauth.sim.application.fido2.WebAuthnAttestationReplayApplicationService.TelemetryStatus;
import io.openauth.sim.application.telemetry.Fido2TelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationRequest;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerification;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerifier;
import io.openauth.sim.core.fido2.WebAuthnVerificationError;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class WebAuthnAttestationReplayApplicationServiceTest {

    private WebAuthnAttestationReplayApplicationService service;
    private WebAuthnAttestationVector vector;
    private List<X509Certificate> trustAnchors;
    private String expectedCredentialId;

    @BeforeEach
    void setUp() {
        service = new WebAuthnAttestationReplayApplicationService(
                new WebAuthnAttestationVerifier(), new Fido2TelemetryAdapter("fido2.attestReplay"));

        vector = WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.ANDROID_KEY).stream()
                .findFirst()
                .orElseThrow();

        WebAuthnAttestationVerification verification = new WebAuthnAttestationVerifier().verify(toRequest(vector));

        trustAnchors = verification.certificateChain().isEmpty()
                ? List.of()
                : List.of(verification
                        .certificateChain()
                        .get(verification.certificateChain().size() - 1));

        expectedCredentialId = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(vector.registration().credentialId());
    }

    @Test
    void replayAttestationEmitsReplayTelemetry() {
        ReplayCommand.Inline command = new ReplayCommand.Inline(
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

        ReplayResult result = service.replay(command);

        assertTrue(result.valid());
        assertFalse(result.attestedCredential().isEmpty());
        assertTrue(result.anchorProvided());

        TelemetrySignal telemetry = result.telemetry();
        assertEquals(TelemetryStatus.SUCCESS, telemetry.status());
        assertEquals("match", telemetry.reasonCode());
        Map<String, Object> fields = telemetry.fields();
        assertEquals(vector.format().label(), fields.get("attestationFormat"));
        assertEquals(vector.relyingPartyId(), fields.get("relyingPartyId"));
        assertEquals(formatAaguid(vector.registration().aaguid()), fields.get("aaguid"));
        assertEquals("provided", fields.get("anchorSource"));
        assertEquals("manual", fields.get("anchorSourceType"));
        assertEquals("fresh", fields.get("anchorMode"));
        assertFalse(fields.containsKey("attestationObject"));

        TelemetryFrame frame =
                telemetry.emit(new Fido2TelemetryAdapter("fido2.attestReplay"), "telemetry-replay-success");
        assertEquals("fido2.attestReplay", frame.event());
        assertEquals("success", frame.status());
        assertTrue(frame.sanitized());
        assertEquals("telemetry-replay-success", frame.fields().get("telemetryId"));

        assertEquals(
                expectedCredentialId, result.attestedCredential().orElseThrow().credentialId());
    }

    @Test
    void replayRejectsTamperedAttestationObject() {
        byte[] tamperedObject = vector.registration().attestationObject().clone();
        tamperedObject[tamperedObject.length - 1] ^= 0x01;

        ReplayCommand.Inline command = new ReplayCommand.Inline(
                vector.vectorId(),
                vector.format(),
                vector.relyingPartyId(),
                vector.origin(),
                tamperedObject,
                vector.registration().clientDataJson(),
                vector.registration().challenge(),
                trustAnchors,
                false,
                WebAuthnTrustAnchorResolver.Source.MANUAL,
                null,
                List.of());

        ReplayResult result = service.replay(command);

        assertFalse(result.valid());
        assertEquals(Optional.of(WebAuthnVerificationError.SIGNATURE_INVALID), result.error());
        assertTrue(result.anchorProvided());

        TelemetrySignal telemetry = result.telemetry();
        assertEquals(TelemetryStatus.INVALID, telemetry.status());
        assertEquals("signature_invalid", telemetry.reasonCode());
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

    private static String formatAaguid(byte[] aaguid) {
        if (aaguid == null || aaguid.length != 16) {
            return "";
        }
        String hex = HexFormat.of().formatHex(aaguid).toUpperCase(Locale.ROOT);
        return "%s-%s-%s-%s-%s"
                .formatted(
                        hex.substring(0, 8),
                        hex.substring(8, 12),
                        hex.substring(12, 16),
                        hex.substring(16, 20),
                        hex.substring(20));
    }
}
