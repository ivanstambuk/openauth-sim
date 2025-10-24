package io.openauth.sim.application.hotp;

import io.openauth.sim.core.otp.hotp.HotpDescriptor;
import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

final class HotpTraceCalculator {

    private static final byte IPAD_BYTE = (byte) 0x36;
    private static final byte OPAD_BYTE = (byte) 0x5C;

    private HotpTraceCalculator() {
        // Utility class.
    }

    static HotpTraceComputation compute(HotpDescriptor descriptor, byte[] secret, long counter) {
        byte[] counterBytes = longToBytes(counter);
        String secretHash = sha256Digest(secret);
        int blockLength = hmacBlockLength(descriptor.algorithm());
        DerivedKey key = deriveKey(secret, descriptor.algorithm(), blockLength);
        byte[] innerPad = xorWith(key.blockKey(), IPAD_BYTE);
        byte[] innerInput = concat(innerPad, counterBytes);
        byte[] innerHash = digest(innerInput, descriptor.algorithm());
        byte[] outerPad = xorWith(key.blockKey(), OPAD_BYTE);
        byte[] outerInput = concat(outerPad, innerHash);
        byte[] hmac = digest(outerInput, descriptor.algorithm());
        int offset = dynamicOffset(hmac);
        byte[] slice = Arrays.copyOfRange(hmac, offset, offset + 4);
        int truncated = dynamicBinaryCode(slice);
        int modulus = decimalModulus(descriptor.digits());
        int otpDecimal = truncated % modulus;
        String otp = formatOtp(truncated, descriptor.digits());
        return new HotpTraceComputation(
                counter,
                counterBytes,
                secret.length,
                secretHash,
                blockLength,
                key.mode(),
                sha256Digest(key.blockKey()),
                innerInput,
                innerHash,
                outerInput,
                hmac,
                slice,
                offset,
                truncated,
                modulus,
                otpDecimal,
                otp);
    }

    static String formatByte(byte value) {
        return String.format("0x%02x", value & 0xFF);
    }

    static byte ipadByte() {
        return IPAD_BYTE;
    }

    static byte opadByte() {
        return OPAD_BYTE;
    }

    static String sha256Digest(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + hex(digest.digest(value));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static byte[] longToBytes(long value) {
        return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte aByte : bytes) {
            builder.append(String.format("%02x", aByte));
        }
        return builder.toString();
    }

    private static DerivedKey deriveKey(byte[] secret, HotpHashAlgorithm algorithm, int blockLength) {
        byte[] working = secret;
        String mode = "padded";
        if (secret.length > blockLength) {
            working = digest(secret, algorithm);
            mode = "hashed_then_padded";
        }
        byte[] blockKey = new byte[blockLength];
        System.arraycopy(working, 0, blockKey, 0, Math.min(working.length, blockLength));
        return new DerivedKey(blockKey, mode);
    }

    private static byte[] digest(byte[] value, HotpHashAlgorithm algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(digestAlgorithm(algorithm));
            return digest.digest(value);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Digest unavailable for " + algorithm, ex);
        }
    }

    private static String digestAlgorithm(HotpHashAlgorithm algorithm) {
        return switch (algorithm) {
            case SHA1 -> "SHA-1";
            case SHA256 -> "SHA-256";
            case SHA512 -> "SHA-512";
        };
    }

    private static int hmacBlockLength(HotpHashAlgorithm algorithm) {
        return switch (algorithm) {
            case SHA1, SHA256 -> 64;
            case SHA512 -> 128;
        };
    }

    private static byte[] xorWith(byte[] key, byte padByte) {
        byte[] result = Arrays.copyOf(key, key.length);
        for (int index = 0; index < result.length; index++) {
            result[index] = (byte) (result[index] ^ padByte);
        }
        return result;
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] result = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return result;
    }

    private static int dynamicOffset(byte[] hmac) {
        return hmac[hmac.length - 1] & 0x0F;
    }

    private static int dynamicBinaryCode(byte[] slice) {
        return ((slice[0] & 0x7F) << 24) | ((slice[1] & 0xFF) << 16) | ((slice[2] & 0xFF) << 8) | (slice[3] & 0xFF);
    }

    private static int decimalModulus(int digits) {
        int modulus = 1;
        for (int i = 0; i < digits; i++) {
            modulus *= 10;
        }
        return modulus;
    }

    private static String formatOtp(int truncatedInt, int digits) {
        int modulus = decimalModulus(digits);
        int otpValue = truncatedInt % modulus;
        return String.format("%0" + digits + "d", otpValue);
    }

    private record DerivedKey(byte[] blockKey, String mode) {
        // Tuple describing the derived HMAC key + mode used by this computation.
    }

    static final class HotpTraceComputation {
        private final long counter;
        private final byte[] counterBytes;
        private final int secretLength;
        private final String secretHash;
        private final int hashBlockLength;
        private final String keyMode;
        private final String keyPrimeSha256;
        private final byte[] innerInput;
        private final byte[] innerHash;
        private final byte[] outerInput;
        private final byte[] hmac;
        private final byte[] slice;
        private final int offset;
        private final int truncatedInt;
        private final int modulus;
        private final int otpDecimal;
        private final String otpString;

        HotpTraceComputation(
                long counter,
                byte[] counterBytes,
                int secretLength,
                String secretHash,
                int hashBlockLength,
                String keyMode,
                String keyPrimeSha256,
                byte[] innerInput,
                byte[] innerHash,
                byte[] outerInput,
                byte[] hmac,
                byte[] slice,
                int offset,
                int truncatedInt,
                int modulus,
                int otpDecimal,
                String otpString) {
            this.counter = counter;
            this.counterBytes = counterBytes;
            this.secretLength = secretLength;
            this.secretHash = secretHash;
            this.hashBlockLength = hashBlockLength;
            this.keyMode = keyMode;
            this.keyPrimeSha256 = keyPrimeSha256;
            this.innerInput = innerInput;
            this.innerHash = innerHash;
            this.outerInput = outerInput;
            this.hmac = hmac;
            this.slice = slice;
            this.offset = offset;
            this.truncatedInt = truncatedInt;
            this.modulus = modulus;
            this.otpDecimal = otpDecimal;
            this.otpString = otpString;
        }

        long counter() {
            return counter;
        }

        int secretLength() {
            return secretLength;
        }

        String secretHash() {
            return secretHash;
        }

        int hashBlockLength() {
            return hashBlockLength;
        }

        String keyMode() {
            return keyMode;
        }

        String keyPrimeSha256() {
            return keyPrimeSha256;
        }

        String counterHex() {
            return hex(counterBytes);
        }

        String innerInputHex() {
            return hex(innerInput);
        }

        String innerHashHex() {
            return hex(innerHash);
        }

        String outerInputHex() {
            return hex(outerInput);
        }

        String hmacHex() {
            return hex(hmac);
        }

        String sliceHex() {
            return hex(slice);
        }

        String lastByteHex() {
            return formatByte(hmac[hmac.length - 1]);
        }

        String sliceMaskedHex() {
            return formatByte((byte) (slice[0] & 0x7F));
        }

        int offset() {
            return offset;
        }

        int truncatedInt() {
            return truncatedInt;
        }

        int modulus() {
            return modulus;
        }

        int otpDecimal() {
            return otpDecimal;
        }

        String otpString() {
            return otpString;
        }
    }
}
