package io.openauth.sim.core.fido2;

import io.openauth.sim.core.json.SimpleJson;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.serialization.CredentialPersistenceAdapter;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Persistence adapter bridging WebAuthn credential descriptors and MapDB records. */
public final class WebAuthnCredentialPersistenceAdapter
        implements CredentialPersistenceAdapter<WebAuthnCredentialDescriptor> {

    public static final int SCHEMA_VERSION = VersionedCredentialRecord.CURRENT_VERSION;

    static final String ATTR_RP_ID = "fido2.rpId";
    static final String ATTR_CREDENTIAL_ID = "fido2.credentialId";
    static final String ATTR_PUBLIC_KEY_COSE = "fido2.publicKeyCose";
    static final String ATTR_SIGNATURE_COUNTER = "fido2.signatureCounter";
    static final String ATTR_UV_REQUIRED = "fido2.userVerificationRequired";
    static final String ATTR_ALGORITHM = "fido2.algorithm";
    static final String ATTR_ALGORITHM_COSE = "fido2.algorithm.cose";
    public static final String ATTR_METADATA_LABEL = "fido2.metadata.label";
    static final String ATTR_ATTESTATION_ENABLED = "fido2.attestation.enabled";
    static final String ATTR_ATTESTATION_VERSION = "fido2.attestation.version";
    static final String ATTR_ATTESTATION_FORMAT = "fido2.attestation.format";
    static final String ATTR_ATTESTATION_SIGNING_MODE = "fido2.attestation.signingMode";
    static final String ATTR_ATTESTATION_ID = "fido2.attestation.id";
    static final String ATTR_ATTESTATION_ORIGIN = "fido2.attestation.origin";
    static final String ATTR_ATTESTATION_CREDENTIAL_PRIVATE_KEY = "fido2.attestation.credentialPrivateKey";
    static final String ATTR_ATTESTATION_PRIVATE_KEY = "fido2.attestation.privateKey";
    static final String ATTR_ATTESTATION_CERTIFICATE_SERIAL = "fido2.attestation.certificateSerial";
    static final String ATTR_ATTESTATION_CERTIFICATE_CHAIN = "fido2.attestation.certificateChainPem";
    static final String ATTR_ATTESTATION_CUSTOM_ROOTS = "fido2.attestation.customRootPem";

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final Clock clock;

    public WebAuthnCredentialPersistenceAdapter() {
        this(Clock.systemUTC());
    }

    public WebAuthnCredentialPersistenceAdapter(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CredentialType type() {
        return CredentialType.FIDO2;
    }

    @Override
    public VersionedCredentialRecord serialize(WebAuthnCredentialDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");

        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(ATTR_RP_ID, descriptor.relyingPartyId());
        attributes.put(ATTR_CREDENTIAL_ID, URL_ENCODER.encodeToString(descriptor.credentialId()));
        attributes.put(ATTR_PUBLIC_KEY_COSE, URL_ENCODER.encodeToString(descriptor.publicKeyCose()));
        attributes.put(ATTR_SIGNATURE_COUNTER, Long.toString(descriptor.signatureCounter()));
        attributes.put(ATTR_UV_REQUIRED, Boolean.toString(descriptor.userVerificationRequired()));
        attributes.put(ATTR_ALGORITHM, descriptor.algorithm().label());
        attributes.put(
                ATTR_ALGORITHM_COSE, Integer.toString(descriptor.algorithm().coseIdentifier()));

        Instant now = clock.instant();
        return new VersionedCredentialRecord(
                SCHEMA_VERSION,
                descriptor.name(),
                type(),
                SecretMaterial.fromBytes(descriptor.credentialId()),
                now,
                now,
                attributes);
    }

    @Override
    public WebAuthnCredentialDescriptor deserialize(VersionedCredentialRecord record) {
        Objects.requireNonNull(record, "record");
        if (record.schemaVersion() != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schema version: " + record.schemaVersion());
        }
        if (record.type() != CredentialType.FIDO2) {
            throw new IllegalArgumentException("Unsupported credential type: " + record.type());
        }

        Map<String, String> attributes = record.attributes();
        String relyingPartyId = require(attributes, ATTR_RP_ID);
        byte[] credentialId = decode(require(attributes, ATTR_CREDENTIAL_ID), ATTR_CREDENTIAL_ID);
        byte[] publicKey = decode(require(attributes, ATTR_PUBLIC_KEY_COSE), ATTR_PUBLIC_KEY_COSE);
        long signatureCounter =
                parseNonNegativeLong(require(attributes, ATTR_SIGNATURE_COUNTER), ATTR_SIGNATURE_COUNTER);
        boolean uvRequired = parseBoolean(require(attributes, ATTR_UV_REQUIRED), ATTR_UV_REQUIRED);

        WebAuthnSignatureAlgorithm algorithm = resolveAlgorithm(attributes);

        return WebAuthnCredentialDescriptor.builder()
                .name(record.name())
                .relyingPartyId(relyingPartyId)
                .credentialId(credentialId)
                .publicKeyCose(publicKey)
                .signatureCounter(signatureCounter)
                .userVerificationRequired(uvRequired)
                .algorithm(algorithm)
                .build();
    }

    private static WebAuthnSignatureAlgorithm resolveAlgorithm(Map<String, String> attributes) {
        String label = attributes.get(ATTR_ALGORITHM);
        String coseValue = attributes.get(ATTR_ALGORITHM_COSE);

        if (label != null && !label.isBlank()) {
            try {
                WebAuthnSignatureAlgorithm resolved = WebAuthnSignatureAlgorithm.fromLabel(label);
                if (coseValue != null && !coseValue.isBlank()) {
                    int coseId = parseInt(coseValue, ATTR_ALGORITHM_COSE);
                    if (coseId != resolved.coseIdentifier()) {
                        throw new IllegalArgumentException(
                                "Algorithm metadata mismatch between label and COSE identifier");
                    }
                }
                return resolved;
            } catch (IllegalArgumentException ex) {
                // fall back to COSE identifier if provided
                if (coseValue == null || coseValue.isBlank()) {
                    throw ex;
                }
            }
        }

        if (coseValue == null || coseValue.isBlank()) {
            throw new IllegalArgumentException("Missing algorithm metadata");
        }
        int coseId = parseInt(coseValue, ATTR_ALGORITHM_COSE);
        return WebAuthnSignatureAlgorithm.fromCoseIdentifier(coseId);
    }

    private static String require(Map<String, String> attributes, String key) {
        String value = attributes.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing attribute: " + key);
        }
        return value.trim();
    }

    private static byte[] decode(String value, String attribute) {
        try {
            return URL_DECODER.decode(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(attribute + " must be Base64URL encoded", ex);
        }
    }

    private static long parseNonNegativeLong(String value, String attribute) {
        long parsed = parseLong(value, attribute);
        if (parsed < 0) {
            throw new IllegalArgumentException(attribute + " must be >= 0");
        }
        return parsed;
    }

    private static long parseLong(String value, String attribute) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(attribute + " must be numeric", ex);
        }
    }

    private static int parseInt(String value, String attribute) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(attribute + " must be numeric", ex);
        }
    }

    private static boolean parseBoolean(String value, String attribute) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException(attribute + " must be true or false");
    }

    /**
     * Serializes a stored attestation descriptor into a {@link VersionedCredentialRecord}. Placeholder until stored
     * attestation persistence is implemented.
     */
    public VersionedCredentialRecord serializeAttestation(WebAuthnAttestationCredentialDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");

        VersionedCredentialRecord baseRecord = serialize(descriptor.credentialDescriptor());
        Map<String, String> attributes = new LinkedHashMap<>(baseRecord.attributes());

        attributes.put(ATTR_ATTESTATION_ENABLED, "true");
        attributes.put(ATTR_ATTESTATION_VERSION, "1");
        attributes.put(ATTR_ATTESTATION_FORMAT, descriptor.format().label());
        attributes.put(ATTR_ATTESTATION_SIGNING_MODE, descriptor.signingMode().name());
        attributes.put(ATTR_ATTESTATION_ID, descriptor.attestationId());
        attributes.put(ATTR_ATTESTATION_ORIGIN, descriptor.origin());
        attributes.put(ATTR_ATTESTATION_CREDENTIAL_PRIVATE_KEY, descriptor.credentialPrivateKeyBase64Url());
        if (descriptor.attestationPrivateKeyBase64Url() != null) {
            attributes.put(ATTR_ATTESTATION_PRIVATE_KEY, descriptor.attestationPrivateKeyBase64Url());
        } else {
            attributes.remove(ATTR_ATTESTATION_PRIVATE_KEY);
        }
        attributes.put(ATTR_ATTESTATION_CERTIFICATE_SERIAL, descriptor.attestationCertificateSerialBase64Url());
        attributes.put(ATTR_ATTESTATION_CERTIFICATE_CHAIN, encodeStringList(descriptor.certificateChainPem()));
        if (!descriptor.customRootCertificatesPem().isEmpty()) {
            attributes.put(ATTR_ATTESTATION_CUSTOM_ROOTS, encodeStringList(descriptor.customRootCertificatesPem()));
        } else {
            attributes.remove(ATTR_ATTESTATION_CUSTOM_ROOTS);
        }

        SecretMaterial secret = SecretMaterial.fromBytes(
                decode(descriptor.credentialPrivateKeyBase64Url(), ATTR_ATTESTATION_CREDENTIAL_PRIVATE_KEY));
        Instant now = clock.instant();
        return new VersionedCredentialRecord(SCHEMA_VERSION, descriptor.name(), type(), secret, now, now, attributes);
    }

    /**
     * Deserializes a {@link VersionedCredentialRecord} into a stored attestation descriptor. Placeholder until stored
     * attestation persistence is implemented.
     */
    public WebAuthnAttestationCredentialDescriptor deserializeAttestation(VersionedCredentialRecord record) {
        Objects.requireNonNull(record, "record");
        if (record.schemaVersion() != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schema version: " + record.schemaVersion());
        }
        if (record.type() != CredentialType.FIDO2) {
            throw new IllegalArgumentException("Unsupported credential type: " + record.type());
        }

        Map<String, String> attributes = record.attributes();
        if (!"true".equals(attributes.get(ATTR_ATTESTATION_ENABLED))) {
            throw new IllegalArgumentException("Record does not contain stored attestation metadata");
        }

        WebAuthnCredentialDescriptor credentialDescriptor = deserialize(record);

        WebAuthnAttestationFormat format =
                WebAuthnAttestationFormat.fromLabel(require(attributes, ATTR_ATTESTATION_FORMAT));
        WebAuthnAttestationGenerator.SigningMode signingMode =
                WebAuthnAttestationGenerator.SigningMode.valueOf(require(attributes, ATTR_ATTESTATION_SIGNING_MODE));
        String attestationId = require(attributes, ATTR_ATTESTATION_ID);
        String origin = require(attributes, ATTR_ATTESTATION_ORIGIN);
        String credentialPrivateKey = require(attributes, ATTR_ATTESTATION_CREDENTIAL_PRIVATE_KEY);
        String attestationPrivateKey = sanitize(attributes.get(ATTR_ATTESTATION_PRIVATE_KEY));
        String certificateSerial = require(attributes, ATTR_ATTESTATION_CERTIFICATE_SERIAL);
        List<String> certificateChain = decodeStringList(attributes.get(ATTR_ATTESTATION_CERTIFICATE_CHAIN));
        List<String> customRoots = decodeStringList(attributes.getOrDefault(ATTR_ATTESTATION_CUSTOM_ROOTS, "[]"));

        return WebAuthnAttestationCredentialDescriptor.builder()
                .name(record.name())
                .format(format)
                .signingMode(signingMode)
                .credentialDescriptor(credentialDescriptor)
                .relyingPartyId(credentialDescriptor.relyingPartyId())
                .origin(origin)
                .attestationId(attestationId)
                .credentialPrivateKeyBase64Url(credentialPrivateKey)
                .attestationPrivateKeyBase64Url(attestationPrivateKey)
                .attestationCertificateSerialBase64Url(certificateSerial)
                .certificateChainPem(certificateChain)
                .customRootCertificatesPem(customRoots)
                .build();
    }

    private static List<String> decodeStringList(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return List.of();
        }
        Object parsed = SimpleJson.parse(encoded);
        if (!(parsed instanceof List<?> list)) {
            throw new IllegalArgumentException("Expected JSON array for attestation metadata");
        }
        List<String> values = new LinkedList<>();
        for (Object value : list) {
            if (value == null) {
                continue;
            }
            String text = value.toString().trim();
            if (!text.isEmpty()) {
                values.add(text);
            }
        }
        return List.copyOf(values);
    }

    private static String encodeStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('"').append(escapeJson(values.get(i))).append('"');
        }
        builder.append(']');
        return builder.toString();
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (char ch : value.toCharArray()) {
            switch (ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        return builder.toString();
    }

    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
