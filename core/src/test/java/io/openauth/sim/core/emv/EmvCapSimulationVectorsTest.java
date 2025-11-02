package io.openauth.sim.core.emv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.emv.cap.EmvCapEngine;
import io.openauth.sim.core.emv.cap.EmvCapInput;
import io.openauth.sim.core.emv.cap.EmvCapMode;
import io.openauth.sim.core.emv.cap.EmvCapResult;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures.EmvCapVector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmvCapSimulationVectorsTest {

    @ParameterizedTest(name = "{0} matches EMV/CAP fixture outputs")
    @ValueSource(
            strings = {
                "identify-baseline",
                "identify-b2-h6",
                "identify-b6-h10",
                "respond-baseline",
                "respond-challenge4",
                "respond-challenge8",
                "sign-baseline",
                "sign-amount-0845",
                "sign-amount-50375"
            })
    void emvCapVectorsMatchFixtures(String vectorId) {
        EmvCapVector vector = EmvCapVectorFixtures.load(vectorId);
        EmvCapResult result = EmvCapEngine.evaluate(vector.input());

        assertFixtureOutputs(vector, result);
    }

    @Test
    void rejectsNonHexMasterKey() {
        EmvCapVector vector = EmvCapVectorFixtures.load("identify-baseline");
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> new EmvCapInput(
                        vector.input().mode(),
                        "0123456789ABCDEG0123456789ABCDEF",
                        vector.input().atcHex(),
                        vector.input().branchFactor(),
                        vector.input().height(),
                        vector.input().ivHex(),
                        vector.input().cdol1Hex(),
                        vector.input().issuerProprietaryBitmapHex(),
                        vector.input().customerInputs(),
                        vector.input().transactionData(),
                        vector.input().iccDataTemplateHex(),
                        vector.input().issuerApplicationDataHex()));
        assertTrue(thrown.getMessage().contains("masterKey"));
    }

    @Test
    void rejectsUnsupportedModePayloads() {
        EmvCapVector vector = EmvCapVectorFixtures.load("identify-baseline");
        EmvCapInput invalid = new EmvCapInput(
                EmvCapMode.IDENTIFY,
                vector.input().masterKeyHex(),
                vector.input().atcHex(),
                vector.input().branchFactor(),
                vector.input().height(),
                vector.input().ivHex(),
                vector.input().cdol1Hex(),
                vector.input().issuerProprietaryBitmapHex(),
                vector.input().customerInputs().withChallenge("12345678"), // Identify mode forbids challenge input.
                vector.input().transactionData(),
                vector.input().iccDataTemplateHex(),
                vector.input().issuerApplicationDataHex());

        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> EmvCapEngine.evaluate(invalid));
        assertTrue(thrown.getMessage().contains("Identify mode"));
    }

    private static void assertFixtureOutputs(EmvCapVector vector, EmvCapResult result) {
        assertEquals(vector.outputs().sessionKeyHex(), result.sessionKeyHex(), "session key");
        assertEquals(
                vector.outputs().generateAcInputTerminalHex(),
                result.generateAcInput().terminalHex(),
                "terminal CDOL1 payload");
        assertEquals(
                vector.outputs().generateAcInputIccHex(),
                result.generateAcInput().iccHex(),
                "ICC payload");
        assertEquals(vector.outputs().generateAcResultHex(), result.generateAcResultHex(), "generate AC result");
        assertEquals(vector.outputs().bitmaskOverlay(), result.bitmaskOverlay(), "bitmask overlay");
        assertEquals(vector.outputs().maskedDigitsOverlay(), result.maskedDigitsOverlay(), "masked digits overlay");
        assertEquals(vector.outputs().otpDecimal(), result.otp().decimal(), "OTP decimal");
        assertEquals(vector.outputs().otpHex(), result.otp().hex(), "OTP hex");
    }
}
