package io.openauth.sim.core.fido2;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Utility for parsing WebAuthn authenticator data (authData) blobs into structured components the
 * application layer can leverage for tracing and diagnostics.
 */
public final class WebAuthnAuthenticatorDataParser {

    private static final int RP_ID_HASH_LENGTH = 32;
    private static final int FLAGS_LENGTH = 1;
    private static final int COUNTER_LENGTH = 4;
    private static final int BASE_LENGTH = RP_ID_HASH_LENGTH + FLAGS_LENGTH + COUNTER_LENGTH;
    private static final int AAGUID_LENGTH = 16;

    private WebAuthnAuthenticatorDataParser() {
        // Utility class
    }

    /**
     * Parse authenticator data into discrete components. The method never throws—any structural
     * issues are recorded on the returned record so callers can emit trace notes without disrupting
     * control flow.
     */
    public static ParsedAuthenticatorData parse(byte[] authenticatorData) {
        byte[] raw = authenticatorData == null ? new byte[0] : authenticatorData.clone();
        if (raw.length < BASE_LENGTH) {
            return new ParsedAuthenticatorData(
                    raw,
                    new byte[0],
                    0,
                    0L,
                    Optional.empty(),
                    new byte[0],
                    Optional.of("authenticator_data_too_short"),
                    Optional.empty());
        }

        ByteBuffer buffer = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);
        byte[] rpIdHash = new byte[RP_ID_HASH_LENGTH];
        buffer.get(rpIdHash);

        int flags = buffer.get() & 0xFF;
        long counter = buffer.getInt() & 0xFFFFFFFFL;

        Optional<AttestedCredentialData> attestedCredential = Optional.empty();
        String attestedError = null;
        if ((flags & 0x40) != 0) {
            AttestedCredentialParseResult attestedResult = parseAttestedCredential(buffer);
            attestedCredential = attestedResult.credential();
            attestedError = attestedResult.error().orElse(null);
        }

        byte[] extensions = new byte[buffer.remaining()];
        buffer.get(extensions);

        String extensionsError = null;
        boolean extensionFlag = (flags & 0x80) != 0;
        if (extensionFlag && extensions.length == 0) {
            extensionsError = "extensions_flag_set_but_missing";
        } else if (!extensionFlag && extensions.length > 0) {
            extensionsError = "extensions_present_without_flag";
        }

        return new ParsedAuthenticatorData(
                raw,
                rpIdHash,
                flags,
                counter,
                attestedCredential,
                extensions,
                Optional.ofNullable(attestedError),
                Optional.ofNullable(extensionsError));
    }

    private static AttestedCredentialParseResult parseAttestedCredential(ByteBuffer buffer) {
        int remaining = buffer.remaining();
        if (remaining < AAGUID_LENGTH + 2) {
            return new AttestedCredentialParseResult(Optional.empty(), Optional.of("attested_credential_truncated"));
        }
        byte[] aaguid = new byte[AAGUID_LENGTH];
        buffer.get(aaguid);

        int credentialIdLength = Short.toUnsignedInt(buffer.getShort());
        if (buffer.remaining() < credentialIdLength) {
            return new AttestedCredentialParseResult(Optional.empty(), Optional.of("attested_credential_id_truncated"));
        }
        byte[] credentialId = new byte[credentialIdLength];
        buffer.get(credentialId);

        int publicKeyStart = buffer.position();
        int publicKeyLength;
        try {
            publicKeyLength = computeCborItemLength(buffer.array(), publicKeyStart);
        } catch (GeneralSecurityException ex) {
            return new AttestedCredentialParseResult(Optional.empty(), Optional.of("credential_public_key_invalid"));
        }

        if (buffer.remaining() < publicKeyLength) {
            return new AttestedCredentialParseResult(Optional.empty(), Optional.of("credential_public_key_truncated"));
        }
        byte[] credentialPublicKey = new byte[publicKeyLength];
        buffer.get(credentialPublicKey);

        return new AttestedCredentialParseResult(
                Optional.of(new AttestedCredentialData(aaguid, credentialId, credentialPublicKey)), Optional.empty());
    }

    static int computeCborItemLength(byte[] data, int offset) throws GeneralSecurityException {
        Objects.requireNonNull(data, "data");
        if (offset >= data.length) {
            throw new GeneralSecurityException("CBOR offset exceeds payload");
        }

        int initial = data[offset] & 0xFF;
        int majorType = initial >>> 5;
        int additionalInfo = initial & 0x1F;

        LengthInfo lengthInfo = readLengthInfo(data, offset, additionalInfo);
        int headerSize = 1 + lengthInfo.lengthBytes();
        int cursor = offset + headerSize;
        long length = lengthInfo.value();

        return switch (majorType) {
            case 0, 1, 7 -> {
                if (majorType == 7 && additionalInfo >= 25 && additionalInfo <= 27) {
                    throw new GeneralSecurityException("Floating-point CBOR values unsupported");
                }
                yield headerSize;
            }
            case 2, 3 -> {
                ensureWithinBounds(data.length, cursor, length);
                yield headerSize + (int) length;
            }
            case 4 -> {
                int total = headerSize;
                for (int i = 0; i < length; i++) {
                    int elementLength = computeCborItemLength(data, cursor);
                    total += elementLength;
                    cursor += elementLength;
                }
                yield total;
            }
            case 5 -> {
                int total = headerSize;
                for (int i = 0; i < length; i++) {
                    int keyLength = computeCborItemLength(data, cursor);
                    total += keyLength;
                    cursor += keyLength;
                    int valueLength = computeCborItemLength(data, cursor);
                    total += valueLength;
                    cursor += valueLength;
                }
                yield total;
            }
            case 6 -> {
                int nestedLength = computeCborItemLength(data, cursor);
                yield headerSize + nestedLength;
            }
            default -> throw new GeneralSecurityException("Unsupported CBOR major type: " + majorType);
        };
    }

    private static void ensureWithinBounds(int totalLength, int offset, long length) throws GeneralSecurityException {
        long end = (long) offset + length;
        if (end > totalLength) {
            throw new GeneralSecurityException("CBOR structure exceeds payload length");
        }
    }

    private static LengthInfo readLengthInfo(byte[] data, int offset, int additionalInfo)
            throws GeneralSecurityException {
        if (additionalInfo < 24) {
            return new LengthInfo(additionalInfo, 0);
        }
        int lengthBytes =
                switch (additionalInfo) {
                    case 24 -> 1;
                    case 25 -> 2;
                    case 26 -> 4;
                    case 27 -> 8;
                    default ->
                        throw new GeneralSecurityException(
                                "Unsupported CBOR length additional info: " + additionalInfo);
                };

        int start = offset + 1;
        if (start + lengthBytes > data.length) {
            throw new GeneralSecurityException("Truncated CBOR length");
        }

        long value = 0;
        for (int i = 0; i < lengthBytes; i++) {
            value = (value << 8) | (data[start + i] & 0xFF);
        }
        return new LengthInfo(value, lengthBytes);
    }

    private record LengthInfo(long value, int lengthBytes) {
        // Records the decoded value and the number of bytes consumed.
    }

    private record AttestedCredentialParseResult(Optional<AttestedCredentialData> credential, Optional<String> error) {
        // Holds successful attested credential data or an explanatory error message.
    }

    public record ParsedAuthenticatorData(
            byte[] raw,
            byte[] rpIdHash,
            int flags,
            long counter,
            Optional<AttestedCredentialData> attestedCredential,
            byte[] extensions,
            Optional<String> attestedCredentialError,
            Optional<String> extensionsError) {

        public ParsedAuthenticatorData {
            raw = raw == null ? new byte[0] : raw.clone();
            rpIdHash = rpIdHash == null ? new byte[0] : rpIdHash.clone();
            attestedCredential = attestedCredential == null ? Optional.empty() : attestedCredential;
            extensions = extensions == null ? new byte[0] : extensions.clone();
            attestedCredentialError = attestedCredentialError == null ? Optional.empty() : attestedCredentialError;
            extensionsError = extensionsError == null ? Optional.empty() : extensionsError;
        }

        public boolean userPresence() {
            return (flags & 0x01) != 0;
        }

        public boolean reservedBitRfu1() {
            return (flags & 0x02) != 0;
        }

        public boolean userVerification() {
            return (flags & 0x04) != 0;
        }

        public boolean backupEligible() {
            return (flags & 0x08) != 0;
        }

        public boolean backupState() {
            return (flags & 0x10) != 0;
        }

        public boolean reservedBitRfu2() {
            return (flags & 0x20) != 0;
        }

        public boolean attestedCredentialDataIncluded() {
            return (flags & 0x40) != 0;
        }

        public boolean extensionDataIncluded() {
            return (flags & 0x80) != 0;
        }
    }

    public record AttestedCredentialData(byte[] aaguid, byte[] credentialId, byte[] credentialPublicKey) {

        public AttestedCredentialData {
            aaguid = aaguid == null ? new byte[0] : aaguid.clone();
            credentialId = credentialId == null ? new byte[0] : credentialId.clone();
            credentialPublicKey = credentialPublicKey == null ? new byte[0] : credentialPublicKey.clone();
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("aaguid", Arrays.copyOf(aaguid, aaguid.length));
            map.put("credentialId", Arrays.copyOf(credentialId, credentialId.length));
            map.put("credentialPublicKey", Arrays.copyOf(credentialPublicKey, credentialPublicKey.length));
            return map;
        }
    }
}
