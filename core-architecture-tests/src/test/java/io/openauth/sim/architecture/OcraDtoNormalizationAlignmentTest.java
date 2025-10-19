package io.openauth.sim.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openauth.sim.application.ocra.OcraEvaluationApplicationService;
import io.openauth.sim.application.ocra.OcraEvaluationRequests;
import io.openauth.sim.application.ocra.OcraVerificationApplicationService;
import io.openauth.sim.application.ocra.OcraVerificationRequests;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class OcraDtoNormalizationAlignmentTest {

    @Test
    @DisplayName("Stored evaluation requests normalize consistently across facades")
    void storedEvaluationRequestsAlign() {
        String pinHex = " aaff ";
        String timestampHex = " 1a2b ";
        var restCommand = OcraEvaluationRequests.stored(new OcraEvaluationRequests.StoredInputs(
                " stored-credential ", " challenge ", " session ", " client ", " server ", pinHex, timestampHex, 5L));
        var cliCommand = OcraEvaluationRequests.stored(new OcraEvaluationRequests.StoredInputs(
                "stored-credential", "challenge", "session", "client", "server", pinHex, timestampHex, 5L));

        var restNormalized = (OcraEvaluationApplicationService.NormalizedRequest.StoredCredential)
                OcraEvaluationApplicationService.NormalizedRequest.from(restCommand);
        var cliNormalized = (OcraEvaluationApplicationService.NormalizedRequest.StoredCredential)
                OcraEvaluationApplicationService.NormalizedRequest.from(cliCommand);

        assertEquals(cliNormalized, restNormalized);
        assertEquals("stored-credential", restNormalized.credentialId());
        assertEquals("challenge", restNormalized.challenge());
        assertEquals("session", restNormalized.sessionHex());
    }

    @Test
    @DisplayName("Inline evaluation identifiers align once DTO normalizer is shared")
    void inlineEvaluationIdentifiersAlign() {
        String suite = "OCRA-1:HOTP-SHA256-8:QA08";
        String secret = "313233";

        String pinHex = " aaff ";
        String timestampHex = " 1a2b ";
        var cliCommand = OcraEvaluationRequests.inline(new OcraEvaluationRequests.InlineInputs(
                sharedInlineIdentifier(suite, secret),
                suite,
                secret,
                " challenge ",
                " session ",
                " client ",
                " server ",
                pinHex,
                timestampHex,
                7L,
                Duration.ofSeconds(30)));
        var restCommand = OcraEvaluationRequests.inline(new OcraEvaluationRequests.InlineInputs(
                sharedInlineIdentifier(suite, secret),
                suite,
                secret,
                " challenge ",
                " session ",
                " client ",
                " server ",
                pinHex,
                timestampHex,
                7L,
                Duration.ofSeconds(30)));

        var cliNormalized = (OcraEvaluationApplicationService.NormalizedRequest.InlineSecret)
                OcraEvaluationApplicationService.NormalizedRequest.from(cliCommand);
        var restNormalized = (OcraEvaluationApplicationService.NormalizedRequest.InlineSecret)
                OcraEvaluationApplicationService.NormalizedRequest.from(restCommand);

        String expectedPin = pinHex.replace(" ", "").trim();
        String expectedTimestamp = timestampHex.replace(" ", "").trim();

        assertEquals(cliNormalized.identifier(), restNormalized.identifier());
        assertEquals("challenge", restNormalized.challenge());
        assertEquals("session", restNormalized.sessionHex());
        assertEquals(expectedPin, restNormalized.pinHashHex());
        assertEquals(expectedPin, cliNormalized.pinHashHex());
        assertEquals(expectedTimestamp, restNormalized.timestampHex());
        assertEquals(expectedTimestamp, cliNormalized.timestampHex());
    }

    @Test
    @DisplayName("Stored verification requests normalize consistently across facades")
    void storedVerificationRequestsAlign() {
        String pinHex = " aaff ";
        String timestampHex = " 1a2b ";
        var restStored = OcraVerificationRequests.stored(new OcraVerificationRequests.StoredInputs(
                " stored-credential ",
                " otp ",
                " challenge ",
                " client ",
                " server ",
                " session ",
                pinHex,
                timestampHex,
                9L));
        var cliStored = OcraVerificationRequests.stored(new OcraVerificationRequests.StoredInputs(
                "stored-credential", "otp", "challenge", "client", "server", "session", pinHex, timestampHex, 9L));

        var restNormalized = (OcraVerificationApplicationService.NormalizedRequest.Stored)
                OcraVerificationApplicationService.NormalizedRequest.from(restStored);
        var cliNormalized = (OcraVerificationApplicationService.NormalizedRequest.Stored)
                OcraVerificationApplicationService.NormalizedRequest.from(cliStored);

        assertEquals("stored-credential", restNormalized.credentialId());
        assertEquals(restNormalized.credentialId(), cliNormalized.credentialId());
        assertEquals("otp", restNormalized.otp());
        assertEquals(restNormalized.otp(), cliNormalized.otp());
        assertEquals("challenge", restNormalized.context().challenge());
        assertEquals(
                restNormalized.context().challenge(), cliNormalized.context().challenge());
        assertEquals("stored", restNormalized.context().credentialSource());
        assertEquals(
                restNormalized.context().credentialSource(),
                cliNormalized.context().credentialSource());
        String expectedTimestamp = timestampHex.replace(" ", "").trim();
        assertEquals(expectedTimestamp, restNormalized.context().timestampHex());
        assertEquals(expectedTimestamp, cliNormalized.context().timestampHex());
    }

    @Test
    @DisplayName("Inline verification identifiers align once DTO normalizer is shared")
    void inlineVerificationIdentifiersAlign() {
        String suite = "OCRA-1:HOTP-SHA256-8:QA08";
        String secret = "313233";

        String pinHex = " aaff ";
        String timestampHex = " 1a2b ";
        var cliCommand = OcraVerificationRequests.inline(new OcraVerificationRequests.InlineInputs(
                sharedInlineIdentifier(suite, secret),
                suite,
                secret,
                " otp ",
                " challenge ",
                " client ",
                " server ",
                " session ",
                pinHex,
                timestampHex,
                11L,
                Duration.ofSeconds(45)));
        var restCommand = OcraVerificationRequests.inline(new OcraVerificationRequests.InlineInputs(
                sharedInlineIdentifier(suite, secret),
                suite,
                secret,
                " otp ",
                " challenge ",
                " client ",
                " server ",
                " session ",
                pinHex,
                timestampHex,
                11L,
                Duration.ofSeconds(45)));

        var cliNormalized = (OcraVerificationApplicationService.NormalizedRequest.Inline)
                OcraVerificationApplicationService.NormalizedRequest.from(cliCommand);
        var restNormalized = (OcraVerificationApplicationService.NormalizedRequest.Inline)
                OcraVerificationApplicationService.NormalizedRequest.from(restCommand);

        assertEquals(cliNormalized.identifier(), restNormalized.identifier());
        assertEquals("otp", restNormalized.otp());
        assertEquals(restNormalized.otp(), cliNormalized.otp());
        String expectedPin = pinHex.replace(" ", "").trim();
        String expectedTimestamp = timestampHex.replace(" ", "").trim();

        assertEquals("challenge", restNormalized.context().challenge());
        assertEquals(
                restNormalized.context().challenge(), cliNormalized.context().challenge());
        assertEquals(expectedPin, restNormalized.pinHashHex());
        assertEquals(expectedPin, cliNormalized.pinHashHex());
        assertEquals(expectedTimestamp, restNormalized.context().timestampHex());
        assertEquals(expectedTimestamp, cliNormalized.context().timestampHex());
    }

    private static String sharedInlineIdentifier(String suite, String secret) {
        return io.openauth.sim.application.ocra.OcraInlineIdentifiers.sharedIdentifier(suite, secret);
    }
}
