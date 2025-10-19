package io.openauth.sim.application.fido2;

import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Shared catalogue exposing attestation fixtures across application-facing components. */
public final class WebAuthnAttestationSamples {

    private static final List<WebAuthnAttestationVector> VECTORS = loadVectors();
    private static final Map<WebAuthnAttestationFormat, List<WebAuthnAttestationVector>> VECTORS_BY_FORMAT =
            indexByFormat(VECTORS);

    private WebAuthnAttestationSamples() {
        throw new AssertionError("Utility class");
    }

    /** Returns all available attestation vectors. */
    public static List<WebAuthnAttestationVector> vectors() {
        return VECTORS;
    }

    /** Returns attestation vectors for the supplied format. */
    public static List<WebAuthnAttestationVector> vectorsFor(WebAuthnAttestationFormat format) {
        if (format == null) {
            return List.of();
        }
        return VECTORS_BY_FORMAT.getOrDefault(format, List.of());
    }

    /** Resolves a specific attestation vector and fails fast if it is not present. */
    public static WebAuthnAttestationVector require(String vectorId) {
        return WebAuthnAttestationFixtures.findById(vectorId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown attestation vector: " + vectorId));
    }

    private static List<WebAuthnAttestationVector> loadVectors() {
        return WebAuthnAttestationFixtures.allVectors().toList();
    }

    private static Map<WebAuthnAttestationFormat, List<WebAuthnAttestationVector>> indexByFormat(
            List<WebAuthnAttestationVector> vectors) {
        EnumMap<WebAuthnAttestationFormat, List<WebAuthnAttestationVector>> grouped =
                new EnumMap<>(WebAuthnAttestationFormat.class);
        for (WebAuthnAttestationVector vector : vectors) {
            grouped.computeIfAbsent(vector.format(), key -> new ArrayList<>()).add(vector);
        }
        grouped.replaceAll((format, byFormat) -> List.copyOf(byFormat));
        return Map.copyOf(grouped);
    }
}
