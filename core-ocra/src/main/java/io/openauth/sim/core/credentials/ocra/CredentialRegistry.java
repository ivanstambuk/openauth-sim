package io.openauth.sim.core.credentials.ocra;

import io.openauth.sim.core.model.CredentialCapability;
import io.openauth.sim.core.model.CredentialType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Registry exposing credential factories and their capability metadata. */
public final class CredentialRegistry {

    private final Map<CredentialType, CredentialCapability> capabilities;
    private final Map<CredentialType, Object> factories;

    private CredentialRegistry(
            Map<CredentialType, CredentialCapability> capabilities, Map<CredentialType, Object> factories) {
        EnumMap<CredentialType, CredentialCapability> capabilityCopy = new EnumMap<>(CredentialType.class);
        capabilityCopy.putAll(capabilities);
        this.capabilities = Collections.unmodifiableMap(capabilityCopy);

        EnumMap<CredentialType, Object> factoryCopy = new EnumMap<>(CredentialType.class);
        factoryCopy.putAll(factories);
        this.factories = Collections.unmodifiableMap(factoryCopy);
    }

    /**
     * @return registry seeded with built-in credential types starting with OATH OCRA.
     */
    public static CredentialRegistry withDefaults() {
        OcraCredentialFactory ocraFactory = new OcraCredentialFactory();
        CredentialCapability ocraCapability = OcraCapabilityMetadata.capability();

        Map<CredentialType, CredentialCapability> capabilityMap = new EnumMap<>(CredentialType.class);
        capabilityMap.put(CredentialType.OATH_OCRA, ocraCapability);

        Map<CredentialType, Object> factoryMap = new EnumMap<>(CredentialType.class);
        factoryMap.put(CredentialType.OATH_OCRA, ocraFactory);

        return new CredentialRegistry(capabilityMap, factoryMap);
    }

    public Optional<CredentialCapability> capability(CredentialType type) {
        Objects.requireNonNull(type, "type");
        return Optional.ofNullable(capabilities.get(type));
    }

    public List<CredentialCapability> capabilities() {
        return List.copyOf(capabilities.values());
    }

    public <T> Optional<T> factory(CredentialType type, Class<T> factoryType) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(factoryType, "factoryType");
        Object factory = factories.get(type);
        if (factoryType.isInstance(factory)) {
            return Optional.of(factoryType.cast(factory));
        }
        return Optional.empty();
    }
}
