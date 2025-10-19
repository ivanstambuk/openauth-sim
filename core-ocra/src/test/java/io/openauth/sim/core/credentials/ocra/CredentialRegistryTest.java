package io.openauth.sim.core.credentials.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.model.CredentialCapability;
import io.openauth.sim.core.model.CredentialType;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class CredentialRegistryTest {

    @Test
    @DisplayName("default registry exposes OCRA capability metadata")
    void defaultRegistryExposesOcraCapability() {
        CredentialRegistry registry = CredentialRegistry.withDefaults();

        Optional<CredentialCapability> capability = registry.capability(CredentialType.OATH_OCRA);
        assertTrue(capability.isPresent(), "OCRA capability should be registered");

        capability.ifPresent(value -> {
            assertEquals("OATH-OCRA", value.protocol());
            assertEquals("OATH OCRA", value.displayName());
            assertEquals(Set.of("name", "ocraSuite", "sharedSecretKey"), value.requiredAttributes());
            assertTrue(
                    value.optionalAttributes().contains("counterValue"),
                    "counterValue should be documented as optional");
            assertTrue(
                    value.supportedCryptoFunctions().contains("HOTP-SHA256"), "hash functions should include SHA256");
            assertEquals("RFC 6287", value.metadata().get("rfc"));
        });
    }

    @Test
    @DisplayName("default registry exposes OCRA factory via type lookup")
    void defaultRegistryProvidesOcraFactory() {
        CredentialRegistry registry = CredentialRegistry.withDefaults();

        Optional<OcraCredentialFactory> factory =
                registry.factory(CredentialType.OATH_OCRA, OcraCredentialFactory.class);

        assertTrue(factory.isPresent(), "OCRA factory should be registered");

        assertFalse(
                registry.factory(CredentialType.FIDO2, OcraCredentialFactory.class)
                        .isPresent(),
                "No factory should exist for unregistered credential type");

        assertFalse(
                registry.factory(CredentialType.OATH_OCRA, String.class).isPresent(),
                "Factory lookup with mismatched type should return empty");
    }

    @Test
    @DisplayName("lookup returns empty for unknown capability")
    void missingCapabilityReturnsEmpty() {
        CredentialRegistry registry = CredentialRegistry.withDefaults();

        assertTrue(registry.capability(CredentialType.FIDO2).isEmpty());
    }
}
