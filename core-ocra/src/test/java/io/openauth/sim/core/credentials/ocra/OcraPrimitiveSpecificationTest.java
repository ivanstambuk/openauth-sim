package io.openauth.sim.core.credentials.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class OcraPrimitiveSpecificationTest {

    @Test
    @DisplayName("OcraChallengeQuestion enforces positive length")
    void ocraChallengeQuestionValidation() {
        OcraChallengeQuestion question = new OcraChallengeQuestion(OcraChallengeFormat.HEX, 8);

        assertEquals(OcraChallengeFormat.HEX, question.format());
        assertEquals(8, question.length());

        assertThrows(NullPointerException.class, () -> new OcraChallengeQuestion(null, 8));
        assertThrows(IllegalArgumentException.class, () -> new OcraChallengeQuestion(OcraChallengeFormat.NUMERIC, 0));
    }

    @Test
    @DisplayName("OcraSessionSpecification requires positive length")
    void ocraSessionSpecificationValidation() {
        OcraSessionSpecification specification = new OcraSessionSpecification(16);
        assertEquals(16, specification.lengthBytes());

        assertThrows(IllegalArgumentException.class, () -> new OcraSessionSpecification(0));
    }

    @Test
    @DisplayName("OcraTimestampSpecification requires positive duration")
    void ocraTimestampSpecificationValidation() {
        OcraTimestampSpecification specification = new OcraTimestampSpecification(Duration.ofSeconds(30));
        assertEquals(Duration.ofSeconds(30), specification.step());

        assertThrows(IllegalArgumentException.class, () -> new OcraTimestampSpecification(Duration.ZERO));
    }

    @Test
    @DisplayName("OcraCryptoFunction validates response digits and preserves optional time step")
    void ocraCryptoFunctionValidation() {
        OcraCryptoFunction function =
                new OcraCryptoFunction(OcraHashAlgorithm.SHA1, 6, Optional.of(Duration.ofSeconds(60)));

        assertEquals(OcraHashAlgorithm.SHA1, function.hashAlgorithm());
        assertEquals(6, function.responseDigits());
        assertEquals(Optional.of(Duration.ofSeconds(60)), function.timeStep());

        assertThrows(NullPointerException.class, () -> new OcraCryptoFunction(null, 6, Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OcraCryptoFunction(OcraHashAlgorithm.SHA1, 0, Optional.empty()));

        OcraCryptoFunction withoutTimeStep = new OcraCryptoFunction(OcraHashAlgorithm.SHA256, 8, Optional.empty());
        assertEquals(Optional.empty(), withoutTimeStep.timeStep());
    }
}
