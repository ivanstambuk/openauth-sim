package io.openauth.sim.rest.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.rest.ocra.OcraEvaluationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OcraEvaluationFormTest {

    @Test
    @DisplayName("inline mode builds request with trimmed fields")
    void inlineModeBuildsRequest() {
        OcraEvaluationForm form = new OcraEvaluationForm();
        form.setMode("inline");
        form.setSuite("  OCRA-1:HOTP-SHA1-6:QN08  ");
        form.setSharedSecretHex(" 31323334 ");
        form.setChallenge(" 12345678 ");
        form.setSessionHex("  AAFF  ");
        form.setClientChallenge(" CLIENT ");
        form.setServerChallenge(" SERVER ");
        form.setPinHashHex(" PIN ");
        form.setTimestampHex(" 00000001 ");
        form.setCounter(42L);

        OcraEvaluationRequest request = form.toOcraRequest();

        assertTrue(form.isInlineMode());
        assertEquals(
                new OcraEvaluationRequest(
                        null,
                        "OCRA-1:HOTP-SHA1-6:QN08",
                        "31323334",
                        "12345678",
                        "AAFF",
                        "CLIENT",
                        "SERVER",
                        "PIN",
                        "00000001",
                        42L),
                request);
    }

    @Test
    @DisplayName("credential mode requires credentialId and omits shared secret")
    void credentialModeBuildsRequest() {
        OcraEvaluationForm form = new OcraEvaluationForm();
        form.setMode("credential");
        form.setCredentialId("  cred-123  ");
        form.setSuite(" OCRA-1:HOTP-SHA1-6:QN08 ");
        form.setChallenge(" 12345678 ");
        form.setSessionHex("  AA  ");
        form.setClientChallenge(" CLIENT ");
        form.setServerChallenge(" SERVER ");
        form.setPinHashHex(" PIN ");
        form.setTimestampHex(" 00000002 ");
        form.setCounter(7L);

        OcraEvaluationRequest request = form.toOcraRequest();

        assertTrue(form.isCredentialMode());
        assertEquals(
                new OcraEvaluationRequest(
                        "cred-123",
                        "OCRA-1:HOTP-SHA1-6:QN08",
                        null,
                        "12345678",
                        "AA",
                        "CLIENT",
                        "SERVER",
                        "PIN",
                        "00000002",
                        7L),
                request);
    }

    @Test
    @DisplayName("credential mode without credentialId fails fast")
    void credentialModeMissingCredentialIdThrows() {
        OcraEvaluationForm form = new OcraEvaluationForm();
        form.setMode("credential");

        assertThrows(NullPointerException.class, form::toOcraRequest);
    }

    @Test
    @DisplayName("scrubSecrets clears only PIN material")
    void scrubSecretsClearsPinOnly() {
        OcraEvaluationForm form = new OcraEvaluationForm();
        form.setPinHashHex("PIN");
        form.setSharedSecretHex("ABC");

        form.scrubSecrets();

        assertNull(form.getPinHashHex());
        assertEquals("ABC", form.getSharedSecretHex());
    }

    @Test
    @DisplayName("mode defaults to inline and treats null as inline")
    void modeDefaultsToInline() {
        OcraEvaluationForm form = new OcraEvaluationForm();

        assertTrue(form.isInlineMode());
        form.setMode(null);
        assertTrue(form.isInlineMode());

        form.setMode("credential");
        assertTrue(form.isCredentialMode());
        form.setMode("inline");
        assertTrue(form.isInlineMode());
    }

    @Test
    @DisplayName("policy preset setter trims and blank values null out")
    void policyPresetTrimming() {
        OcraEvaluationForm form = new OcraEvaluationForm();

        form.setPolicyPreset("  preset-A  ");
        assertEquals("preset-A", form.getPolicyPreset());

        form.setPolicyPreset("   ");
        assertNull(form.getPolicyPreset());
    }

    @Test
    @DisplayName("blank setters normalise to null")
    void blankInputsBecomeNull() {
        OcraEvaluationForm form = new OcraEvaluationForm();
        form.setCredentialId("   ");
        form.setSharedSecretHex("\t\n");
        form.setChallenge(" ");

        assertNull(form.getCredentialId());
        assertNull(form.getSharedSecretHex());
        assertNull(form.getChallenge());
    }
}
