package io.openauth.sim.core.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Describes capabilities and requirements for a credential type. */
public record CredentialCapability(
        CredentialType type,
        String protocol,
        String displayName,
        Set<String> requiredAttributes,
        Set<String> optionalAttributes,
        Set<String> supportedCryptoFunctions,
        Map<String, String> metadata)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public CredentialCapability {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(requiredAttributes, "requiredAttributes");
        Objects.requireNonNull(optionalAttributes, "optionalAttributes");
        Objects.requireNonNull(supportedCryptoFunctions, "supportedCryptoFunctions");
        Objects.requireNonNull(metadata, "metadata");

        if (protocol.isBlank()) {
            throw new IllegalArgumentException("protocol must not be blank");
        }
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (requiredAttributes.isEmpty()) {
            throw new IllegalArgumentException("requiredAttributes must not be empty");
        }

        requiredAttributes = Set.copyOf(requiredAttributes);
        optionalAttributes = Set.copyOf(optionalAttributes);
        supportedCryptoFunctions = Set.copyOf(supportedCryptoFunctions);
        metadata = Map.copyOf(metadata);
    }
}
