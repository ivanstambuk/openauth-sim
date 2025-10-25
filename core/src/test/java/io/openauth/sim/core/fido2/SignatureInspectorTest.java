package io.openauth.sim.core.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.fido2.SignatureInspector.EcdsaSignatureDetails;
import io.openauth.sim.core.fido2.SignatureInspector.RsaSignatureDetails;
import io.openauth.sim.core.fido2.SignatureInspector.SignatureDetails;
import io.openauth.sim.core.fido2.SignatureInspector.SignatureEncoding;
import java.math.BigInteger;
import java.util.Base64;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

final class SignatureInspectorTest {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    @Test
    void inspectEcdsaParsesDerAndComputesLowS() {
        WebAuthnFixtures.WebAuthnFixture fixture = WebAuthnFixtures.loadPackedEs256();
        byte[] signature = fixture.request().signature();

        SignatureDetails details = SignatureInspector.inspect(WebAuthnSignatureAlgorithm.ES256, signature);

        assertEquals(SignatureEncoding.DER, details.encoding());
        assertEquals(URL_ENCODER.encodeToString(signature), details.base64Url());
        assertEquals(signature.length, details.length());
        assertTrue(details.ecdsa().isPresent());

        EcdsaSignatureDetails ecdsa = details.ecdsa().orElseThrow();
        DecodedEcdsa expected = decodeEcdsa(signature);
        assertEquals(expected.rHex(), ecdsa.rHex());
        assertEquals(expected.sHex(), ecdsa.sHex());
        assertEquals(expected.lowS(), ecdsa.lowS());
    }

    @Test
    void inspectRs256ReportsPaddingAndHash() {
        byte[] signature = new byte[] {0x01, 0x02, 0x03};

        SignatureDetails details = SignatureInspector.inspect(WebAuthnSignatureAlgorithm.RS256, signature);

        assertEquals(SignatureEncoding.RAW, details.encoding());
        assertEquals(URL_ENCODER.encodeToString(signature), details.base64Url());
        RsaSignatureDetails rsa = details.rsa().orElseThrow();
        assertEquals("PKCS1v1_5", rsa.padding());
        assertEquals("SHA-256", rsa.hash());
        assertEquals(OptionalInt.empty(), rsa.pssSaltLength());
    }

    @Test
    void inspectPs256ReportsSaltLength() {
        byte[] signature = new byte[] {0x10, 0x20};

        SignatureDetails details = SignatureInspector.inspect(WebAuthnSignatureAlgorithm.PS256, signature);

        RsaSignatureDetails rsa = details.rsa().orElseThrow();
        assertEquals("PSS", rsa.padding());
        assertEquals("SHA-256", rsa.hash());
        assertEquals(32, rsa.pssSaltLength().orElse(-1));
    }

    @Test
    void inspectEdDsaReportsRawEncoding() {
        byte[] signature = new byte[] {0x05, 0x06, 0x07, 0x08};

        SignatureDetails details = SignatureInspector.inspect(WebAuthnSignatureAlgorithm.EDDSA, signature);

        assertEquals(SignatureEncoding.RAW, details.encoding());
        assertEquals(URL_ENCODER.encodeToString(signature), details.base64Url());
        assertTrue(details.ecdsa().isEmpty());
        assertTrue(details.rsa().isEmpty());
    }

    @SuppressWarnings("PMD.AssignmentInOperand")
    private static DecodedEcdsa decodeEcdsa(byte[] der) {
        int offset = 0;
        if (der[offset++] != 0x30) {
            throw new IllegalArgumentException("Not a DER sequence");
        }
        LengthResult sequence = readLength(der, offset);
        offset = sequence.nextOffset();
        if (der[offset++] != 0x02) {
            throw new IllegalArgumentException("Missing R integer");
        }
        LengthResult rLength = readLength(der, offset);
        offset = rLength.nextOffset();
        byte[] r = new byte[rLength.length()];
        System.arraycopy(der, offset, r, 0, r.length);
        offset += r.length;
        if (der[offset++] != 0x02) {
            throw new IllegalArgumentException("Missing S integer");
        }
        LengthResult sLength = readLength(der, offset);
        offset = sLength.nextOffset();
        byte[] s = new byte[sLength.length()];
        System.arraycopy(der, offset, s, 0, s.length);
        byte[] normalizedR = stripLeadingZeros(r);
        byte[] normalizedS = stripLeadingZeros(s);
        BigInteger sValue = new BigInteger(1, normalizedS);
        BigInteger order = new BigInteger("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16);
        boolean lowS = sValue.compareTo(order.shiftRight(1)) <= 0;
        return new DecodedEcdsa(hex(normalizedR), hex(normalizedS), lowS);
    }

    @SuppressWarnings("PMD.AssignmentInOperand")
    private static LengthResult readLength(byte[] der, int offset) {
        int first = der[offset++] & 0xFF;
        if ((first & 0x80) == 0) {
            return new LengthResult(first, offset);
        }
        int byteCount = first & 0x7F;
        int length = 0;
        for (int i = 0; i < byteCount; i++) {
            length = (length << 8) | (der[offset++] & 0xFF);
        }
        return new LengthResult(length, offset);
    }

    private static byte[] stripLeadingZeros(byte[] value) {
        int index = 0;
        while (index < value.length - 1 && value[index] == 0) {
            index++;
        }
        byte[] result = new byte[value.length - index];
        System.arraycopy(value, index, result, 0, result.length);
        return result;
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private record LengthResult(int length, int nextOffset) {
        // Mirrors DER length parsing contract for the fixture decoder helper.
    }

    private record DecodedEcdsa(String rHex, String sHex, boolean lowS) {
        // Holds expected ECDSA components for comparison against inspector output.
    }
}
