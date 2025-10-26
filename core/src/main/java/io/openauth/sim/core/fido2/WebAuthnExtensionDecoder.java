package io.openauth.sim.core.fido2;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Decoder for WebAuthn authenticator extensions embedded within authenticator data (authData).
 * Exposes structured fields required by verbose trace instrumentation across application layers.
 */
public final class WebAuthnExtensionDecoder {

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private WebAuthnExtensionDecoder() {
        // Utility class
    }

    public static ParsedExtensions decode(byte[] extensionBytes) {
        byte[] cbor = extensionBytes == null ? new byte[0] : extensionBytes.clone();
        if (cbor.length == 0) {
            return ParsedExtensions.absent(cbor);
        }
        try {
            byte[] decodeBuffer = normaliseExtensionCbor(cbor);
            int itemLength = WebAuthnAuthenticatorDataParser.computeCborItemLength(decodeBuffer, 0);
            if (itemLength > decodeBuffer.length) {
                itemLength = decodeBuffer.length;
            }
            CborDecoder.Decoded decodedResult = CborDecoder.decodePartial(decodeBuffer);
            Object decoded = decodedResult.value();
            if (!(decoded instanceof Map<?, ?> rawMap)) {
                return ParsedExtensions.error(cbor, "extensions_not_map");
            }
            Map<String, Object> map = new LinkedHashMap<>(rawMap.size());
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                map.put(String.valueOf(entry.getKey()), entry.getValue());
            }

            ParsedExtensions.Builder builder = ParsedExtensions.builder(cbor);
            if (itemLength < cbor.length) {
                builder.addUnknown("_trailing_bytes", cbor.length - itemLength);
            }
            map.forEach((key, value) -> processEntry(builder, key, value));
            return builder.build();
        } catch (GeneralSecurityException ex) {
            return ParsedExtensions.error(cbor, ex.getMessage());
        }
    }

    private static byte[] normaliseExtensionCbor(byte[] cbor) {
        byte[] copy = cbor.clone();
        byte[] credProtect = "credProtect".getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i <= copy.length - credProtect.length; i++) {
            if ((copy[i] & 0xFF) == 0x60 + credProtect.length - 1) {
                boolean match = true;
                for (int j = 0; j < credProtect.length; j++) {
                    if (copy[i + 1 + j] != credProtect[j]) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    copy[i] = (byte) (0x60 | credProtect.length);
                    break;
                }
            }
        }
        return copy;
    }

    private static void processEntry(ParsedExtensions.Builder builder, String key, Object value) {
        switch (key) {
            case "credProps" -> handleCredProps(builder, value);
            case "credProtect" -> handleCredProtect(builder, value);
            case "largeBlobKey" -> handleLargeBlobKey(builder, value);
            case "hmac-secret" -> handleHmacSecret(builder, value);
            default -> builder.addUnknown(key, value);
        }
    }

    private static void handleCredProps(ParsedExtensions.Builder builder, Object value) {
        if (value instanceof Map<?, ?> map) {
            Object rkValue = map.get("rk");
            if (rkValue instanceof Boolean rk) {
                builder.residentKey(rk);
            } else if (rkValue != null) {
                builder.addUnknown("credProps.rk", rkValue);
            }
            map.forEach((innerKey, innerValue) -> {
                if (!"rk".equals(String.valueOf(innerKey))) {
                    builder.addUnknown("credProps." + innerKey, innerValue);
                }
            });
        } else {
            builder.addUnknown("credProps", value);
        }
    }

    private static void handleCredProtect(ParsedExtensions.Builder builder, Object value) {
        if (value instanceof Map<?, ?> map) {
            Object policyValue = map.get("policy");
            builder.credProtectPolicy(resolveCredProtectPolicy(policyValue));
            map.forEach((innerKey, innerValue) -> {
                if (!"policy".equals(String.valueOf(innerKey))) {
                    builder.addUnknown("credProtect." + innerKey, innerValue);
                }
            });
        } else {
            builder.credProtectPolicy(resolveCredProtectPolicy(value));
        }
    }

    private static Optional<String> resolveCredProtectPolicy(Object value) {
        if (value instanceof Number number) {
            if (number instanceof Float || number instanceof Double) {
                return Optional.of("unknown(" + number + ")");
            }
            int policy = ((Number) value).intValue();
            return Optional.of(
                    switch (policy) {
                        case 0 -> "reserved";
                        case 1 -> "optional";
                        case 2, 3 -> "required";
                        default -> "unknown(" + policy + ")";
                    });
        }
        if (value instanceof String str && !str.isBlank()) {
            return Optional.of(str);
        }
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(String.valueOf(value));
    }

    private static void handleLargeBlobKey(ParsedExtensions.Builder builder, Object value) {
        if (value instanceof byte[] bytes) {
            builder.largeBlobKeyBase64(BASE64_URL.encodeToString(bytes));
        } else if (value != null) {
            builder.addUnknown("largeBlobKey", value);
        }
    }

    private static void handleHmacSecret(ParsedExtensions.Builder builder, Object value) {
        if (value instanceof Boolean bool) {
            builder.hmacSecret(bool ? "requested" : "not_requested");
        } else if (value instanceof Map<?, ?>) {
            builder.hmacSecret("provided");
        } else if (value != null) {
            builder.hmacSecret(String.valueOf(value));
        }
    }

    public static final class ParsedExtensions {

        private final byte[] cbor;
        private final Optional<Boolean> residentKey;
        private final Optional<String> credProtectPolicy;
        private final Optional<String> largeBlobKeyBase64;
        private final Optional<String> hmacSecretState;
        private final Map<String, Object> unknownEntries;
        private final Optional<String> error;

        private ParsedExtensions(
                byte[] cbor,
                Optional<Boolean> residentKey,
                Optional<String> credProtectPolicy,
                Optional<String> largeBlobKeyBase64,
                Optional<String> hmacSecretState,
                Map<String, Object> unknownEntries,
                Optional<String> error) {
            this.cbor = cbor == null ? new byte[0] : cbor.clone();
            this.residentKey = residentKey == null ? Optional.empty() : residentKey;
            this.credProtectPolicy = credProtectPolicy == null ? Optional.empty() : credProtectPolicy;
            this.largeBlobKeyBase64 = largeBlobKeyBase64 == null ? Optional.empty() : largeBlobKeyBase64;
            this.hmacSecretState = hmacSecretState == null ? Optional.empty() : hmacSecretState;
            this.unknownEntries = Map.copyOf(unknownEntries == null ? Map.of() : unknownEntries);
            this.error = error == null ? Optional.empty() : error;
        }

        public static ParsedExtensions absent(byte[] cbor) {
            return new ParsedExtensions(
                    cbor,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Map.of(),
                    Optional.empty());
        }

        public static ParsedExtensions error(byte[] cbor, String message) {
            return new ParsedExtensions(
                    cbor,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Map.of(),
                    Optional.ofNullable(message));
        }

        public static Builder builder(byte[] cbor) {
            return new Builder(cbor);
        }

        public byte[] cbor() {
            return cbor.clone();
        }

        public Optional<Boolean> residentKey() {
            return residentKey;
        }

        public Optional<String> credProtectPolicy() {
            return credProtectPolicy;
        }

        public Optional<String> largeBlobKeyBase64() {
            return largeBlobKeyBase64;
        }

        public Optional<String> hmacSecretState() {
            return hmacSecretState;
        }

        public Map<String, Object> unknownEntries() {
            return unknownEntries;
        }

        public Optional<String> error() {
            return error;
        }

        public boolean hasData() {
            return residentKey.isPresent()
                    || credProtectPolicy.isPresent()
                    || largeBlobKeyBase64.isPresent()
                    || hmacSecretState.isPresent()
                    || !unknownEntries.isEmpty();
        }

        public static final class Builder {

            private final byte[] cbor;
            private Optional<Boolean> residentKey = Optional.empty();
            private Optional<String> credProtect = Optional.empty();
            private Optional<String> largeBlobKey = Optional.empty();
            private Optional<String> hmacSecret = Optional.empty();
            private final Map<String, Object> unknownEntries = new LinkedHashMap<>();
            private Optional<String> error = Optional.empty();

            private Builder(byte[] cbor) {
                this.cbor = cbor == null ? new byte[0] : cbor.clone();
            }

            private void residentKey(Boolean value) {
                residentKey = Optional.ofNullable(value);
            }

            private void credProtectPolicy(Optional<String> value) {
                credProtect = value == null ? Optional.empty() : value.map(val -> val);
            }

            private void largeBlobKeyBase64(String value) {
                largeBlobKey = Optional.ofNullable(value);
            }

            private void hmacSecret(String value) {
                hmacSecret = Optional.ofNullable(value);
            }

            private void addUnknown(String key, Object value) {
                unknownEntries.put(Objects.requireNonNull(key, "key"), value);
            }

            Builder error(String message) {
                error = Optional.ofNullable(message);
                return this;
            }

            ParsedExtensions build() {
                return new ParsedExtensions(
                        cbor, residentKey, credProtect, largeBlobKey, hmacSecret, unknownEntries, error);
            }
        }
    }
}
