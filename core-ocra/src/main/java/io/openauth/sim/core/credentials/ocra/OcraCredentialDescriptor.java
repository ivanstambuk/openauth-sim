package io.openauth.sim.core.credentials.ocra;

import io.openauth.sim.core.model.SecretMaterial;
import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable descriptor representing a parsed OCRA credential definition. */
public record OcraCredentialDescriptor(
        String name,
        OcraSuite suite,
        SecretMaterial sharedSecret,
        Optional<Long> counter,
        Optional<SecretMaterial> pinHash,
        Optional<Duration> allowedTimestampDrift,
        Map<String, String> metadata)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public OcraCredentialDescriptor {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(suite, "suite");
        Objects.requireNonNull(sharedSecret, "sharedSecret");
        Objects.requireNonNull(counter, "counter");
        Objects.requireNonNull(pinHash, "pinHash");
        Objects.requireNonNull(allowedTimestampDrift, "allowedTimestampDrift");
        Objects.requireNonNull(metadata, "metadata");

        name = name.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Descriptor name must not be blank");
        }

        metadata = Map.copyOf(metadata);
    }

    public Optional<OcraPinSpecification> pinHashSpecification() {
        return suite.dataInput().pin();
    }
}
