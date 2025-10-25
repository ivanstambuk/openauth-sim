package io.openauth.sim.core.fido2;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Utility that inspects signature payloads for trace instrumentation. Supports ECDSA (DER), RSA
 * (PKCS#1 / RSASSA-PSS), and EdDSA signatures.
 */
public final class SignatureInspector {

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private static final BigInteger P256_ORDER =
            new BigInteger("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16);
    private static final BigInteger P384_ORDER = new BigInteger(
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFC7634D81F4372DDF581A0DB248B0A77AECEC196ACCC52973", 16);
    private static final BigInteger P521_ORDER = new BigInteger(
            "01FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
            16);

    private SignatureInspector() {
        throw new AssertionError("Utility class");
    }

    public static SignatureDetails inspect(WebAuthnSignatureAlgorithm algorithm, byte[] signatureBytes) {
        byte[] signature = signatureBytes == null ? new byte[0] : signatureBytes.clone();
        return switch (algorithm) {
            case ES256, ES384, ES512 -> inspectEcdsa(algorithm, signature);
            case RS256 -> inspectRsa(signature, "PKCS1v1_5", "SHA-256", OptionalInt.empty());
            case PS256 -> inspectRsa(signature, "PSS", "SHA-256", OptionalInt.of(32));
            case EDDSA -> inspectEdDsa(signature);
        };
    }

    private static SignatureDetails inspectEcdsa(WebAuthnSignatureAlgorithm algorithm, byte[] signature) {
        DecodedEcdsaSignature decoded = decodeEcdsa(signature);
        boolean lowS = isLowS(algorithm, decoded.s());
        return SignatureDetails.ecdsa(
                SignatureEncoding.DER,
                BASE64_URL.encodeToString(signature),
                signature.length,
                decoded.rHex(),
                decoded.sHex(),
                lowS);
    }

    private static SignatureDetails inspectRsa(
            byte[] signature, String padding, String hash, OptionalInt pssSaltLength) {
        return SignatureDetails.rsa(
                SignatureEncoding.RAW,
                BASE64_URL.encodeToString(signature),
                signature.length,
                padding,
                hash,
                pssSaltLength);
    }

    private static SignatureDetails inspectEdDsa(byte[] signature) {
        return SignatureDetails.eddsa(SignatureEncoding.RAW, BASE64_URL.encodeToString(signature), signature.length);
    }

    @SuppressWarnings("PMD.AssignmentInOperand")
    private static DecodedEcdsaSignature decodeEcdsa(byte[] signature) {
        int offset = 0;
        if (signature.length < 8 || signature[offset++] != 0x30) {
            throw new IllegalArgumentException("ECDSA signature must be a DER sequence");
        }
        LengthResult sequence = readLength(signature, offset);
        offset = sequence.nextOffset();
        if (offset + sequence.length() > signature.length) {
            throw new IllegalArgumentException("ECDSA signature truncated");
        }
        if (signature[offset++] != 0x02) {
            throw new IllegalArgumentException("ECDSA signature missing R integer");
        }
        LengthResult rLength = readLength(signature, offset);
        offset = rLength.nextOffset();
        byte[] rBytes = Arrays.copyOfRange(signature, offset, offset + rLength.length());
        offset += rLength.length();
        if (signature[offset++] != 0x02) {
            throw new IllegalArgumentException("ECDSA signature missing S integer");
        }
        LengthResult sLength = readLength(signature, offset);
        offset = sLength.nextOffset();
        byte[] sBytes = Arrays.copyOfRange(signature, offset, offset + sLength.length());

        byte[] normalizedR = stripLeadingZeros(rBytes);
        byte[] normalizedS = stripLeadingZeros(sBytes);
        BigInteger r = new BigInteger(1, normalizedR);
        BigInteger s = new BigInteger(1, normalizedS);
        return new DecodedEcdsaSignature(r, s, toHex(normalizedR), toHex(normalizedS));
    }

    @SuppressWarnings("PMD.AssignmentInOperand")
    private static LengthResult readLength(byte[] der, int offset) {
        int first = der[offset++] & 0xFF;
        if ((first & 0x80) == 0) {
            return new LengthResult(first, offset);
        }
        int byteCount = first & 0x7F;
        if (byteCount == 0 || byteCount > 4) {
            throw new IllegalArgumentException("Unsupported DER length encoding");
        }
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
        return Arrays.copyOfRange(value, index, value.length);
    }

    private static boolean isLowS(WebAuthnSignatureAlgorithm algorithm, BigInteger s) {
        BigInteger order =
                switch (algorithm) {
                    case ES256 -> P256_ORDER;
                    case ES384 -> P384_ORDER;
                    case ES512 -> P521_ORDER;
                    default -> throw new IllegalArgumentException("Unsupported ECDSA algorithm: " + algorithm);
                };
        return s.compareTo(order.shiftRight(1)) <= 0;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private record DecodedEcdsaSignature(BigInteger r, BigInteger s, String rHex, String sHex) {
        // Captures DER-decoded R/S values with their hex expansions.
    }

    private record LengthResult(int length, int nextOffset) {
        // Represents a parsed DER length and the next cursor position.
    }

    public enum SignatureEncoding {
        DER,
        RAW
    }

    public record SignatureDetails(
            SignatureEncoding encoding,
            String base64Url,
            int length,
            Optional<EcdsaSignatureDetails> ecdsa,
            Optional<RsaSignatureDetails> rsa) {

        public static SignatureDetails ecdsa(
                SignatureEncoding encoding, String base64Url, int length, String rHex, String sHex, boolean lowS) {
            return new SignatureDetails(
                    encoding,
                    base64Url,
                    length,
                    Optional.of(new EcdsaSignatureDetails(rHex, sHex, lowS)),
                    Optional.empty());
        }

        public static SignatureDetails rsa(
                SignatureEncoding encoding,
                String base64Url,
                int length,
                String padding,
                String hash,
                OptionalInt pssSaltLength) {
            return new SignatureDetails(
                    encoding,
                    base64Url,
                    length,
                    Optional.empty(),
                    Optional.of(new RsaSignatureDetails(padding, hash, pssSaltLength)));
        }

        public static SignatureDetails eddsa(SignatureEncoding encoding, String base64Url, int length) {
            return new SignatureDetails(encoding, base64Url, length, Optional.empty(), Optional.empty());
        }

        public static SignatureDetails raw(byte[] signature) {
            byte[] safe = signature == null ? new byte[0] : signature.clone();
            return new SignatureDetails(
                    SignatureEncoding.RAW,
                    BASE64_URL.encodeToString(safe),
                    safe.length,
                    Optional.empty(),
                    Optional.empty());
        }
    }

    public record EcdsaSignatureDetails(String rHex, String sHex, boolean lowS) {
        // Exposes decoded ECDSA coordinate values alongside low-S evaluation.
    }

    public record RsaSignatureDetails(String padding, String hash, OptionalInt pssSaltLength) {
        // Summarises RSA signature metadata for trace rendering.
    }
}
