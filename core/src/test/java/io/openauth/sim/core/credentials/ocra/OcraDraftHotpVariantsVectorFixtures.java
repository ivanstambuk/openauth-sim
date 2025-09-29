package io.openauth.sim.core.credentials.ocra;

import io.openauth.sim.core.model.SecretEncoding;
import java.util.List;

/** Test vectors derived from draft-mraihi-mutual-oath-hotp-variants Appendix B generator. */
final class OcraDraftHotpVariantsVectorFixtures {

  private static final String STANDARD_KEY_32 =
      "3132333435363738393031323334353637383930313233343536373839303132";
  private static final String HEX_CHALLENGE_64 =
      "00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF";

  private OcraDraftHotpVariantsVectorFixtures() {
    // Utility class
  }

  static List<OcraRfc6287VectorFixtures.OneWayVector> counterHexVectors() {
    return List.of(
        new OcraRfc6287VectorFixtures.OneWayVector(
            "Draft HOTP SHA256-6 counter 0 with QH64",
            "OCRA-1:HOTP-SHA256-6:C-QH64",
            STANDARD_KEY_32,
            SecretEncoding.HEX,
            HEX_CHALLENGE_64,
            0L,
            null,
            null,
            null,
            "213703"),
        new OcraRfc6287VectorFixtures.OneWayVector(
            "Draft HOTP SHA256-6 counter 1 with QH64",
            "OCRA-1:HOTP-SHA256-6:C-QH64",
            STANDARD_KEY_32,
            SecretEncoding.HEX,
            HEX_CHALLENGE_64,
            1L,
            null,
            null,
            null,
            "429968"),
        new OcraRfc6287VectorFixtures.OneWayVector(
            "Draft HOTP SHA256-6 counter 2 with QH64",
            "OCRA-1:HOTP-SHA256-6:C-QH64",
            STANDARD_KEY_32,
            SecretEncoding.HEX,
            HEX_CHALLENGE_64,
            2L,
            null,
            null,
            null,
            "053393"));
  }
}
