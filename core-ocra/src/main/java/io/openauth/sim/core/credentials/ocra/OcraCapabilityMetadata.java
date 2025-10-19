package io.openauth.sim.core.credentials.ocra;

import io.openauth.sim.core.model.CredentialCapability;
import io.openauth.sim.core.model.CredentialType;
import java.util.Map;
import java.util.Set;

public final class OcraCapabilityMetadata {

    private static final Set<String> REQUIRED_ATTRIBUTES = Set.of("name", "ocraSuite", "sharedSecretKey");

    private static final Set<String> OPTIONAL_ATTRIBUTES =
            Set.of("counterValue", "pinHash", "allowedTimestampDrift", "metadata");

    private OcraCapabilityMetadata() {
        // Utility class
    }

    public static CredentialCapability capability() {
        Set<String> cryptoFunctions = Set.of("HOTP-SHA1", "HOTP-SHA256", "HOTP-SHA512");

        return new CredentialCapability(
                CredentialType.OATH_OCRA,
                "OATH-OCRA",
                "OATH OCRA",
                REQUIRED_ATTRIBUTES,
                OPTIONAL_ATTRIBUTES,
                cryptoFunctions,
                Map.of(
                        "rfc", "RFC 6287",
                        "challengeFormats", "A|N|H|C",
                        "pinSupport", "Optional per suite"));
    }
}
