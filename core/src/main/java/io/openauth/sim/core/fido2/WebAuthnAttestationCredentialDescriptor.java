package io.openauth.sim.core.fido2;

import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.SigningMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Descriptor representing a persisted WebAuthn attestation preset coupled with a stored credential. */
public final class WebAuthnAttestationCredentialDescriptor {

    private final String name;
    private final WebAuthnAttestationFormat format;
    private final SigningMode signingMode;
    private final WebAuthnCredentialDescriptor credentialDescriptor;
    private final String attestationId;
    private final String origin;
    private final String credentialPrivateKeyBase64Url;
    private final String attestationPrivateKeyBase64Url;
    private final String attestationCertificateSerialBase64Url;
    private final List<String> certificateChainPem;
    private final List<String> customRootCertificatesPem;

    private WebAuthnAttestationCredentialDescriptor(Builder builder) {
        this.name = requireText(builder.name, "name");
        this.format = Objects.requireNonNull(builder.format, "format");
        this.signingMode = Objects.requireNonNull(builder.signingMode, "signingMode");
        this.credentialDescriptor = normalizeCredentialDescriptor(builder);
        this.attestationId = builder.attestationId == null ? this.name : builder.attestationId.trim();
        this.origin = requireText(builder.origin, "origin");
        this.credentialPrivateKeyBase64Url =
                requireText(builder.credentialPrivateKeyBase64Url, "credentialPrivateKeyBase64Url");
        this.attestationPrivateKeyBase64Url = sanitize(builder.attestationPrivateKeyBase64Url);
        this.attestationCertificateSerialBase64Url =
                requireText(builder.attestationCertificateSerialBase64Url, "attestationCertificateSerialBase64Url");
        this.certificateChainPem = immutableList(builder.certificateChainPem);
        this.customRootCertificatesPem = immutableList(builder.customRootCertificatesPem);
    }

    public String name() {
        return name;
    }

    public WebAuthnAttestationFormat format() {
        return format;
    }

    public SigningMode signingMode() {
        return signingMode;
    }

    public WebAuthnCredentialDescriptor credentialDescriptor() {
        return credentialDescriptor;
    }

    public String attestationId() {
        return attestationId;
    }

    public String origin() {
        return origin;
    }

    public String credentialPrivateKeyBase64Url() {
        return credentialPrivateKeyBase64Url;
    }

    public String attestationPrivateKeyBase64Url() {
        return attestationPrivateKeyBase64Url;
    }

    public String attestationCertificateSerialBase64Url() {
        return attestationCertificateSerialBase64Url;
    }

    public List<String> certificateChainPem() {
        return certificateChainPem;
    }

    public List<String> customRootCertificatesPem() {
        return customRootCertificatesPem;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof WebAuthnAttestationCredentialDescriptor other)) {
            return false;
        }
        return name.equals(other.name)
                && format == other.format
                && signingMode == other.signingMode
                && credentialDescriptor.equals(other.credentialDescriptor)
                && attestationId.equals(other.attestationId)
                && origin.equals(other.origin)
                && credentialPrivateKeyBase64Url.equals(other.credentialPrivateKeyBase64Url)
                && Objects.equals(attestationPrivateKeyBase64Url, other.attestationPrivateKeyBase64Url)
                && attestationCertificateSerialBase64Url.equals(other.attestationCertificateSerialBase64Url)
                && certificateChainPem.equals(other.certificateChainPem)
                && customRootCertificatesPem.equals(other.customRootCertificatesPem);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                name,
                format,
                signingMode,
                credentialDescriptor,
                attestationId,
                origin,
                credentialPrivateKeyBase64Url,
                attestationPrivateKeyBase64Url,
                attestationCertificateSerialBase64Url,
                certificateChainPem,
                customRootCertificatesPem);
    }

    @Override
    public String toString() {
        return "WebAuthnAttestationCredentialDescriptor{"
                + "name='"
                + name
                + '\''
                + ", format="
                + format
                + ", signingMode="
                + signingMode
                + ", credentialDescriptor="
                + credentialDescriptor
                + ", attestationId='"
                + attestationId
                + '\''
                + ", origin='"
                + origin
                + '\''
                + ", credentialPrivateKeyBase64Url='"
                + credentialPrivateKeyBase64Url
                + '\''
                + ", attestationPrivateKeyBase64Url='"
                + attestationPrivateKeyBase64Url
                + '\''
                + ", attestationCertificateSerialBase64Url='"
                + attestationCertificateSerialBase64Url
                + '\''
                + ", certificateChainPem="
                + certificateChainPem
                + ", customRootCertificatesPem="
                + customRootCertificatesPem
                + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String requireText(String value, String attribute) {
        if (value == null) {
            throw new IllegalArgumentException(attribute + " must not be null");
        }
        String sanitized = value.trim();
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException(attribute + " must not be blank");
        }
        return sanitized;
    }

    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = value.trim();
        return sanitized.isEmpty() ? null : sanitized;
    }

    private static List<String> immutableList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> sanitized = new ArrayList<>(values.size());
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                sanitized.add(trimmed);
            }
        }
        return Collections.unmodifiableList(sanitized);
    }

    private static WebAuthnCredentialDescriptor normalizeCredentialDescriptor(Builder builder) {
        WebAuthnCredentialDescriptor descriptor =
                Objects.requireNonNull(builder.credentialDescriptor, "credentialDescriptor");
        if (builder.name != null && !builder.name.trim().isEmpty()) {
            if (!descriptor.name().equals(builder.name.trim())) {
                throw new IllegalArgumentException("credentialDescriptor.name must match descriptor name");
            }
        }
        if (builder.relyingPartyId != null && !builder.relyingPartyId.trim().isEmpty()) {
            String normalized = builder.relyingPartyId.trim();
            if (!descriptor.relyingPartyId().equals(normalized)) {
                throw new IllegalArgumentException("relyingPartyId mismatch between descriptor and credential");
            }
        }
        return descriptor;
    }

    /** Fluent builder for attestation credential descriptors. */
    public static final class Builder {
        private String name;
        private WebAuthnAttestationFormat format;
        private SigningMode signingMode;
        private WebAuthnCredentialDescriptor credentialDescriptor;
        private String relyingPartyId;
        private String origin;
        private String attestationId;
        private String credentialPrivateKeyBase64Url;
        private String attestationPrivateKeyBase64Url;
        private String attestationCertificateSerialBase64Url;
        private List<String> certificateChainPem = List.of();
        private List<String> customRootCertificatesPem = List.of();

        private Builder() {
            // use factory method
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder format(WebAuthnAttestationFormat format) {
            this.format = format;
            return this;
        }

        public Builder signingMode(SigningMode signingMode) {
            this.signingMode = signingMode;
            return this;
        }

        public Builder credentialDescriptor(WebAuthnCredentialDescriptor credentialDescriptor) {
            this.credentialDescriptor = credentialDescriptor;
            return this;
        }

        public Builder relyingPartyId(String relyingPartyId) {
            this.relyingPartyId = relyingPartyId;
            return this;
        }

        public Builder origin(String origin) {
            this.origin = origin;
            return this;
        }

        public Builder attestationId(String attestationId) {
            this.attestationId = attestationId;
            return this;
        }

        public Builder credentialPrivateKeyBase64Url(String credentialPrivateKeyBase64Url) {
            this.credentialPrivateKeyBase64Url = credentialPrivateKeyBase64Url;
            return this;
        }

        public Builder attestationPrivateKeyBase64Url(String attestationPrivateKeyBase64Url) {
            this.attestationPrivateKeyBase64Url = attestationPrivateKeyBase64Url;
            return this;
        }

        public Builder attestationCertificateSerialBase64Url(String attestationCertificateSerialBase64Url) {
            this.attestationCertificateSerialBase64Url = attestationCertificateSerialBase64Url;
            return this;
        }

        public Builder certificateChainPem(List<String> certificateChainPem) {
            this.certificateChainPem = certificateChainPem == null ? List.of() : List.copyOf(certificateChainPem);
            return this;
        }

        public Builder customRootCertificatesPem(List<String> customRootCertificatesPem) {
            this.customRootCertificatesPem =
                    customRootCertificatesPem == null ? List.of() : List.copyOf(customRootCertificatesPem);
            return this;
        }

        public WebAuthnAttestationCredentialDescriptor build() {
            return new WebAuthnAttestationCredentialDescriptor(this);
        }
    }
}
