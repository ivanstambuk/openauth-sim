package io.openauth.sim.core.emv.cap;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/** Core EMV/CAP computation engine – derives session keys, generates AC payloads, and extracts OTP digits. */
public final class EmvCapEngine {

    private static final HexFormat HEX = HexFormat.of().withUpperCase();
    private static final int DES_BLOCK_SIZE = 8;

    private EmvCapEngine() {
        throw new AssertionError("Utility class");
    }

    /** Execute the EMV/CAP derivation flow for the supplied inputs. */
    public static EmvCapResult evaluate(EmvCapInput input) {
        Objects.requireNonNull(input, "input");
        input.mode().validateCustomerInputs(input.customerInputs());

        byte[] masterKey = decodeFixed(input.masterKeyHex(), 16, "masterKey");
        byte[] iv = decodeFixed(input.ivHex(), 16, "iv");
        byte[] atcBytes = decodeFixed(input.atcHex(), 2, "atc");
        int atcValue = Integer.parseUnsignedInt(input.atcHex(), 16);

        byte[] sessionKey = SessionKeyDeriver.derive(masterKey, atcValue, input.branchFactor(), input.height(), iv);

        String iccHex = resolveIccPayload(input);
        byte[] iccBytes = HEX.parseHex(iccHex);

        String terminalHex = resolveTerminalPayload(input);
        byte[] terminalBytes = HEX.parseHex(terminalHex);

        byte[] issuerApplicationData = HEX.parseHex(input.issuerApplicationDataHex());

        byte[] acBytes = GenerateAc.compute(sessionKey, terminalBytes, iccBytes, issuerApplicationData);

        byte cid = (byte) 0x80; // CAP flows treat the derived cryptogram as TC-formatted for OTP derivation.
        byte[] generateAcResult = assembleGenerateAcResult(cid, atcBytes, acBytes, issuerApplicationData);

        byte[] issuerBitmap = HEX.parseHex(input.issuerProprietaryBitmapHex());
        if (issuerBitmap.length != generateAcResult.length) {
            throw new IllegalArgumentException("issuerProprietaryBitmap length (" + issuerBitmap.length
                    + " bytes) must match generate AC result length (" + generateAcResult.length + " bytes)");
        }

        String generateAcResultHex = HEX.formatHex(generateAcResult);
        String bitmaskOverlay = renderBitmaskOverlay(input.issuerProprietaryBitmapHex());
        String maskedDigitsOverlay = renderMaskedDigitsOverlay(input.issuerProprietaryBitmapHex(), generateAcResultHex);

        OtpComputation otp = OtpComputation.extract(issuerBitmap, generateAcResult);

        return new EmvCapResult(
                HEX.formatHex(sessionKey),
                new EmvCapResult.GenerateAcInput(terminalHex, iccHex),
                generateAcResultHex,
                bitmaskOverlay,
                maskedDigitsOverlay,
                new EmvCapResult.Otp(otp.decimal(), otp.hex()));
    }

    private static String resolveIccPayload(EmvCapInput input) {
        Optional<String> override = input.transactionData().iccHexOverride();
        if (override.isPresent()) {
            return override.get();
        }
        String template = input.iccDataTemplateHex();
        String atc = input.atcHex();
        String expanded;
        if (template.contains("xxxx")) {
            expanded = template.replace("xxxx", atc);
        } else if (template.contains("XXXX")) {
            expanded = template.replace("XXXX", atc);
        } else {
            expanded = template;
        }
        if ((expanded.length() & 1) == 1) {
            throw new IllegalArgumentException("Expanded ICC payload must contain an even number of hex characters");
        }
        return expanded.toUpperCase(Locale.ROOT);
    }

    private static String resolveTerminalPayload(EmvCapInput input) {
        Optional<String> override = input.transactionData().terminalHexOverride();
        if (override.isPresent()) {
            return override.get();
        }
        byte[] payload = CdolAssembler.build(input);
        return HEX.formatHex(payload);
    }

    private static byte[] decodeFixed(String hex, int expectedBytes, String field) {
        byte[] bytes = HEX.parseHex(hex);
        if (bytes.length != expectedBytes) {
            throw new IllegalArgumentException(
                    field + " must decode to " + expectedBytes + " bytes but was " + bytes.length);
        }
        return bytes;
    }

    private static byte[] assembleGenerateAcResult(
            byte cid, byte[] atcBytes, byte[] acBytes, byte[] issuerApplicationData) {
        byte[] result = new byte[1 + atcBytes.length + acBytes.length + issuerApplicationData.length];
        result[0] = cid;
        System.arraycopy(atcBytes, 0, result, 1, atcBytes.length);
        System.arraycopy(acBytes, 0, result, 1 + atcBytes.length, acBytes.length);
        System.arraycopy(
                issuerApplicationData, 0, result, 1 + atcBytes.length + acBytes.length, issuerApplicationData.length);
        return result;
    }

    private static String renderBitmaskOverlay(String bitmapHex) {
        StringBuilder builder = new StringBuilder(bitmapHex.length());
        for (int i = 0; i < bitmapHex.length(); i++) {
            char ch = bitmapHex.charAt(i);
            builder.append(ch == '0' ? '.' : ch);
        }
        return builder.toString();
    }

    private static String renderMaskedDigitsOverlay(String bitmapHex, String resultHex) {
        if (bitmapHex.length() != resultHex.length()) {
            throw new IllegalArgumentException("Bitmask and result lengths must be equal");
        }
        StringBuilder builder = new StringBuilder(bitmapHex.length());
        for (int i = 0; i < bitmapHex.length(); i++) {
            char maskChar = bitmapHex.charAt(i);
            if (maskChar == '0') {
                builder.append('.');
            } else {
                int maskNibble = Character.digit(maskChar, 16);
                int dataNibble = Character.digit(resultHex.charAt(i), 16);
                int overlay = maskNibble & dataNibble;
                builder.append(Character.toUpperCase(Character.forDigit(overlay, 16)));
            }
        }
        return builder.toString();
    }

    private record OtpComputation(String decimal, String hex) {
        private static OtpComputation extract(byte[] bitmap, byte[] result) {
            StringBuilder bits = new StringBuilder();
            for (int i = 0; i < bitmap.length; i++) {
                int maskByte = Byte.toUnsignedInt(bitmap[i]);
                int dataByte = Byte.toUnsignedInt(result[i]);
                for (int bit = 7; bit >= 0; bit--) {
                    int mask = 1 << bit;
                    if ((maskByte & mask) != 0) {
                        bits.append(((dataByte & mask) != 0) ? '1' : '0');
                    }
                }
            }
            if (bits.length() == 0) {
                throw new IllegalArgumentException(
                        "Issuer proprietary bitmap selects zero bits – unable to derive OTP");
            }
            BigInteger value = new BigInteger(bits.toString(), 2);
            return new OtpComputation(value.toString(), value.toString(16).toUpperCase(Locale.ROOT));
        }
    }

    private static final class CdolAssembler {
        private static byte[] build(EmvCapInput input) {
            byte[] cdol = HEX.parseHex(input.cdol1Hex());
            List<CdolField> fields = parseFields(cdol);
            byte[] output =
                    new byte[fields.stream().mapToInt(field -> field.length()).sum()];
            int offset = 0;
            for (CdolField field : fields) {
                byte[] value = resolveValue(field, input);
                if (value.length != field.length) {
                    throw new IllegalArgumentException("CDOL field " + field.tagHex()
                            + " expected length " + field.length()
                            + " bytes but resolved to " + value.length);
                }
                System.arraycopy(value, 0, output, offset, field.length());
                offset += field.length();
            }
            return output;
        }

        private static List<CdolField> parseFields(byte[] cdol) {
            List<CdolField> fields = new ArrayList<>();
            int index = 0;
            while (index < cdol.length) {
                int tagByte = Byte.toUnsignedInt(cdol[index]);
                index++;
                int tag = tagByte;
                if ((tagByte & 0x1F) == 0x1F) {
                    if (index >= cdol.length) {
                        throw new IllegalArgumentException("Incomplete multi-byte tag in CDOL definition");
                    }
                    int tagContinuation = Byte.toUnsignedInt(cdol[index]);
                    index++;
                    tag = (tagByte << 8) | tagContinuation;
                }
                if (index >= cdol.length) {
                    throw new IllegalArgumentException("Missing length for CDOL tag " + Integer.toHexString(tag));
                }
                int length = Byte.toUnsignedInt(cdol[index]);
                index++;
                fields.add(new CdolField(tag, length));
            }
            return fields;
        }

        private static byte[] resolveValue(CdolField field, EmvCapInput input) {
            EmvCapInput.CustomerInputs inputs = input.customerInputs();
            return switch (field.tag()) {
                case 0x9F02 -> encodeBcd(inputs.amount(), field.length());
                case 0x9F03 -> new byte[field.length()];
                case 0x9F1A -> new byte[field.length()];
                case 0x95 -> defaultTvr(field.length());
                case 0x5F2A -> new byte[field.length()];
                case 0x9A -> new byte[field.length()];
                case 0x9C -> new byte[field.length()];
                case 0x9F37 -> encodeUnpredictableNumber(input.mode(), inputs, field.length());
                default -> new byte[field.length()];
            };
        }

        private static byte[] defaultTvr(int length) {
            byte[] value = new byte[length];
            if (length > 0) {
                value[0] = (byte) 0x80;
            }
            return value;
        }

        private static byte[] encodeUnpredictableNumber(
                EmvCapMode mode, EmvCapInput.CustomerInputs inputs, int length) {
            return switch (mode) {
                case IDENTIFY -> new byte[length];
                case RESPOND -> encodeBcd(inputs.challenge(), length);
                case SIGN -> encodeBcd(inputs.reference(), length);
            };
        }

        private static byte[] encodeBcd(String digits, int byteLength) {
            if (byteLength <= 0) {
                throw new IllegalArgumentException("BCD length must be positive");
            }
            String normalized = digits == null ? "" : digits.trim();
            if (!normalized.matches("\\d*")) {
                throw new IllegalArgumentException("BCD fields must contain decimal digits only");
            }
            int requiredDigits = byteLength * 2;
            if (normalized.length() > requiredDigits) {
                normalized = normalized.substring(0, requiredDigits);
            }
            while (normalized.length() < requiredDigits) {
                normalized = "0" + normalized;
            }
            byte[] payload = new byte[byteLength];
            for (int i = 0; i < byteLength; i++) {
                int high = Character.digit(normalized.charAt(i * 2), 10);
                int low = Character.digit(normalized.charAt(i * 2 + 1), 10);
                payload[i] = (byte) ((high << 4) | low);
            }
            return payload;
        }

        private record CdolField(int tag, int length) {
            String tagHex() {
                return String.format(Locale.ROOT, "%04X", tag);
            }
        }
    }

    private static final class SessionKeyDeriver {

        private static byte[] derive(byte[] masterKey, int atc, int branchFactor, int height, byte[] iv) {
            if (branchFactor < 2 || branchFactor > 16) {
                throw new IllegalArgumentException("branchFactor must be between 2 and 16 (inclusive)");
            }
            if (height < 1 || height > 16) {
                throw new IllegalArgumentException("height must be between 1 and 16 (inclusive)");
            }
            if (iv.length != 16) {
                throw new IllegalArgumentException("Initial vector must be 16 bytes");
            }
            int[] path = computePath(atc, branchFactor, height);

            byte[] grandparent = Arrays.copyOf(masterKey, masterKey.length);
            ensureOddParity(grandparent);

            byte[] parent = phi(grandparent, iv, path[0], branchFactor);
            byte[] currentGrandparent = grandparent;
            byte[] currentParent = parent;

            for (int i = 1; i < height - 1; i++) {
                byte[] next = phi(currentParent, currentGrandparent, path[i], branchFactor);
                currentGrandparent = currentParent;
                currentParent = next;
            }

            byte[] finalPhi = phi(currentParent, currentGrandparent, path[height - 1], branchFactor);
            byte[] sessionKey = xor(finalPhi, currentGrandparent);
            ensureOddParity(sessionKey);
            return sessionKey;
        }

        private static int[] computePath(int value, int branchFactor, int height) {
            int[] digits = new int[height];
            int current = value;
            for (int index = height - 1; index >= 0; index--) {
                digits[index] = Math.floorMod(current, branchFactor);
                current /= branchFactor;
            }
            return digits;
        }

        private static byte[] phi(byte[] key, byte[] data, int branch, int branchFactor) {
            if (data.length != 16) {
                throw new IllegalArgumentException("Phi expects a 16-byte input block");
            }
            byte[] left = Arrays.copyOfRange(data, 0, 8);
            byte[] right = Arrays.copyOfRange(data, 8, 16);
            byte xorValue = (byte) (branch % branchFactor);

            left[7] ^= xorValue;
            right[7] ^= xorValue;
            right[7] ^= (byte) 0xF0;

            byte[] leftEncrypted = tripleDesEncrypt(key, left);
            byte[] rightEncrypted = tripleDesEncrypt(key, right);

            byte[] combined = new byte[16];
            System.arraycopy(leftEncrypted, 0, combined, 0, 8);
            System.arraycopy(rightEncrypted, 0, combined, 8, 8);
            return combined;
        }

        private static byte[] xor(byte[] a, byte[] b) {
            if (a.length != b.length) {
                throw new IllegalArgumentException("Unable to XOR arrays of different lengths");
            }
            byte[] result = new byte[a.length];
            for (int i = 0; i < a.length; i++) {
                result[i] = (byte) (a[i] ^ b[i]);
            }
            return result;
        }

        private static void ensureOddParity(byte[] key) {
            for (int i = 0; i < key.length; i++) {
                int value = key[i] & 0xFE;
                int ones = Integer.bitCount(value);
                if ((ones & 1) == 0) {
                    value |= 0x01;
                }
                key[i] = (byte) value;
            }
        }

        private static byte[] tripleDesEncrypt(byte[] key, byte[] block) {
            try {
                Cipher cipher = CipherHolder.DES_EDE.get();
                SecretKey secretKey = new SecretKeySpec(expandTo24Bytes(key), "DESede");
                cipher.init(Cipher.ENCRYPT_MODE, secretKey);
                return cipher.doFinal(block);
            } catch (GeneralSecurityException ex) {
                throw new IllegalStateException("Unable to execute 3DES operation", ex);
            }
        }
    }

    private static final class GenerateAc {
        private static byte[] compute(byte[] sessionKey, byte[] terminal, byte[] icc, byte[] issuerApplicationData) {
            byte[] message = concat(terminal, icc);
            byte[] padded = iso9797Method2(message);

            byte[] ksl = Arrays.copyOfRange(sessionKey, 0, 8);
            byte[] ksr = Arrays.copyOfRange(sessionKey, 8, 16);

            byte[] chaining = new byte[DES_BLOCK_SIZE];
            Cipher desEncrypt = CipherHolder.DES_ENCRYPT.get();
            Cipher desDecrypt = CipherHolder.DES_DECRYPT.get();
            SecretKey leftKey = new SecretKeySpec(ksl, "DES");
            SecretKey rightKey = new SecretKeySpec(ksr, "DES");

            try {
                for (int offset = 0; offset < padded.length - DES_BLOCK_SIZE; offset += DES_BLOCK_SIZE) {
                    byte[] block = Arrays.copyOfRange(padded, offset, offset + DES_BLOCK_SIZE);
                    byte[] xored = xor(block, chaining);
                    desEncrypt.init(Cipher.ENCRYPT_MODE, leftKey);
                    chaining = desEncrypt.doFinal(xored);
                }

                byte[] lastBlock = Arrays.copyOfRange(padded, padded.length - DES_BLOCK_SIZE, padded.length);
                byte[] lastXor = xor(lastBlock, chaining);

                desEncrypt.init(Cipher.ENCRYPT_MODE, leftKey);
                byte[] h = desEncrypt.doFinal(lastXor);

                desDecrypt.init(Cipher.DECRYPT_MODE, rightKey);
                byte[] intermediate = desDecrypt.doFinal(h);

                desEncrypt.init(Cipher.ENCRYPT_MODE, leftKey);
                return desEncrypt.doFinal(intermediate);
            } catch (GeneralSecurityException ex) {
                throw new IllegalStateException("Unable to compute application cryptogram", ex);
            }
        }

        private static byte[] iso9797Method2(byte[] message) {
            int remainder = message.length % DES_BLOCK_SIZE;
            int padding = remainder == 0 ? DES_BLOCK_SIZE : DES_BLOCK_SIZE - remainder;
            byte[] padded = Arrays.copyOf(message, message.length + padding);
            padded[message.length] = (byte) 0x80;
            return padded;
        }

        private static byte[] xor(byte[] left, byte[] right) {
            byte[] result = new byte[left.length];
            for (int i = 0; i < left.length; i++) {
                result[i] = (byte) (left[i] ^ right[i]);
            }
            return result;
        }

        private static byte[] concat(byte[]... segments) {
            int total = 0;
            for (byte[] segment : segments) {
                total += segment.length;
            }
            byte[] result = new byte[total];
            int offset = 0;
            for (byte[] segment : segments) {
                System.arraycopy(segment, 0, result, offset, segment.length);
                offset += segment.length;
            }
            return result;
        }
    }

    private static final class CipherHolder {
        private static final ThreadLocal<Cipher> DES_EDE =
                ThreadLocal.withInitial(() -> initCipher("DESede/ECB/NoPadding"));
        private static final ThreadLocal<Cipher> DES_ENCRYPT =
                ThreadLocal.withInitial(() -> initCipher("DES/ECB/NoPadding"));
        private static final ThreadLocal<Cipher> DES_DECRYPT =
                ThreadLocal.withInitial(() -> initCipher("DES/ECB/NoPadding"));

        private static Cipher initCipher(String transformation) {
            try {
                return Cipher.getInstance(transformation);
            } catch (GeneralSecurityException ex) {
                throw new IllegalStateException("Unable to initialize cipher " + transformation, ex);
            }
        }
    }

    private static byte[] expandTo24Bytes(byte[] key16) {
        if (key16.length != 16 && key16.length != 24) {
            throw new IllegalArgumentException("Triple DES keys must be 16 or 24 bytes");
        }
        if (key16.length == 24) {
            return key16;
        }
        byte[] expanded = new byte[24];
        System.arraycopy(key16, 0, expanded, 0, 16);
        System.arraycopy(key16, 0, expanded, 16, 8);
        return expanded;
    }
}
