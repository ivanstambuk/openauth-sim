package io.openauth.sim.application.fido2;

import io.openauth.sim.core.fido2.WebAuthnAttestationCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Service that seeds curated WebAuthn attestation credentials into the shared credential store. */
public final class WebAuthnAttestationSeedService {

    private static final String ATTR_ATTESTATION_OBJECT = "fido2.attestation.stored.attestationObject";
    private static final String ATTR_CLIENT_DATA_JSON = "fido2.attestation.stored.clientDataJson";
    private static final String ATTR_EXPECTED_CHALLENGE = "fido2.attestation.stored.expectedChallenge";

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final WebAuthnCredentialPersistenceAdapter persistenceAdapter;

    public WebAuthnAttestationSeedService() {
        this(new WebAuthnCredentialPersistenceAdapter());
    }

    WebAuthnAttestationSeedService(WebAuthnCredentialPersistenceAdapter persistenceAdapter) {
        this.persistenceAdapter = Objects.requireNonNull(persistenceAdapter, "persistenceAdapter");
    }

    /**
     * Seeds attestation credentials when they are not already present.
     *
     * @param commands list of descriptors and deterministic payloads to seed
     * @param credentialStore shared credential store
     * @return result describing how many credentials were added
     */
    public SeedResult seed(List<SeedCommand> commands, CredentialStore credentialStore) {
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(credentialStore, "credentialStore");
        if (commands.isEmpty()) {
            return new SeedResult(List.of());
        }

        List<String> addedCredentialIds = new ArrayList<>();
        for (SeedCommand command : commands) {
            WebAuthnAttestationCredentialDescriptor descriptor = command.descriptor();
            WebAuthnAttestationCredentialDescriptor normalizedDescriptor = normalizeDescriptor(descriptor);
            String canonicalName = normalizedDescriptor.name();

            Optional<Credential> existing = credentialStore.findByName(canonicalName);

            VersionedCredentialRecord baseRecord = persistenceAdapter.serializeAttestation(normalizedDescriptor);

            Map<String, String> attributes = new LinkedHashMap<>();
            existing.ifPresent(credential -> attributes.putAll(credential.attributes()));
            attributes.putAll(baseRecord.attributes());
            attributes.put(ATTR_ATTESTATION_OBJECT, encode(command.attestationObject()));
            attributes.put(ATTR_CLIENT_DATA_JSON, encode(command.clientDataJson()));
            attributes.put(ATTR_EXPECTED_CHALLENGE, encode(command.expectedChallenge()));
            attributes.put(
                    WebAuthnCredentialPersistenceAdapter.ATTR_METADATA_LABEL, resolveLabel(normalizedDescriptor));

            VersionedCredentialRecord enriched = new VersionedCredentialRecord(
                    baseRecord.schemaVersion(),
                    canonicalName,
                    baseRecord.type(),
                    baseRecord.secret(),
                    baseRecord.createdAt(),
                    baseRecord.updatedAt(),
                    Map.copyOf(attributes));

            Credential merged = VersionedCredentialRecordMapper.toCredential(enriched);
            SecretMaterial secret = existing.map(Credential::secret).orElse(merged.secret());
            Instant createdAt = existing.map(Credential::createdAt).orElse(merged.createdAt());
            Instant updatedAt = merged.updatedAt();
            Map<String, String> mergedAttributes = merged.attributes();
            Credential persisted =
                    new Credential(merged.name(), merged.type(), secret, mergedAttributes, createdAt, updatedAt);

            credentialStore.save(persisted);
            if (existing.isEmpty()) {
                addedCredentialIds.add(canonicalName);
            }
        }

        return new SeedResult(addedCredentialIds);
    }

    private static String encode(byte[] value) {
        return URL_ENCODER.encodeToString(value);
    }

    private static byte[] cloneBytes(byte[] value, String attribute) {
        Objects.requireNonNull(value, attribute + " must not be null");
        return value.clone();
    }

    public record SeedCommand(
            WebAuthnAttestationCredentialDescriptor descriptor,
            byte[] attestationObject,
            byte[] clientDataJson,
            byte[] expectedChallenge) {

        public SeedCommand(
                WebAuthnAttestationCredentialDescriptor descriptor,
                byte[] attestationObject,
                byte[] clientDataJson,
                byte[] expectedChallenge) {
            this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
            this.attestationObject = cloneBytes(attestationObject, "attestationObject");
            this.clientDataJson = cloneBytes(clientDataJson, "clientDataJson");
            this.expectedChallenge = cloneBytes(expectedChallenge, "expectedChallenge");
        }

        @Override
        public byte[] attestationObject() {
            return cloneBytes(attestationObject, "attestationObject");
        }

        @Override
        public byte[] clientDataJson() {
            return cloneBytes(clientDataJson, "clientDataJson");
        }

        @Override
        public byte[] expectedChallenge() {
            return cloneBytes(expectedChallenge, "expectedChallenge");
        }
    }

    public record SeedResult(int addedCount, List<String> addedCredentialIds) {

        public SeedResult(int addedCount, List<String> addedCredentialIds) {
            List<String> ids = addedCredentialIds == null ? List.of() : List.copyOf(addedCredentialIds);
            this.addedCredentialIds = ids;
            this.addedCount = ids.size();
        }

        public SeedResult(List<String> addedCredentialIds) {
            this(addedCredentialIds == null ? 0 : addedCredentialIds.size(), addedCredentialIds);
        }
    }

    private static WebAuthnAttestationCredentialDescriptor normalizeDescriptor(
            WebAuthnAttestationCredentialDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        String canonicalName = resolveCanonicalName(descriptor);
        WebAuthnCredentialDescriptor credentialDescriptor =
                rebuildCredentialDescriptor(descriptor.credentialDescriptor(), canonicalName);
        String relyingPartyId = credentialDescriptor.relyingPartyId();
        return WebAuthnAttestationCredentialDescriptor.builder()
                .name(canonicalName)
                .format(descriptor.format())
                .signingMode(descriptor.signingMode())
                .credentialDescriptor(credentialDescriptor)
                .relyingPartyId(relyingPartyId)
                .origin(descriptor.origin())
                .attestationId(descriptor.attestationId())
                .credentialPrivateKeyBase64Url(descriptor.credentialPrivateKeyBase64Url())
                .attestationPrivateKeyBase64Url(descriptor.attestationPrivateKeyBase64Url())
                .attestationCertificateSerialBase64Url(descriptor.attestationCertificateSerialBase64Url())
                .certificateChainPem(descriptor.certificateChainPem())
                .customRootCertificatesPem(descriptor.customRootCertificatesPem())
                .build();
    }

    private static WebAuthnCredentialDescriptor rebuildCredentialDescriptor(
            WebAuthnCredentialDescriptor descriptor, String canonicalName) {
        return WebAuthnCredentialDescriptor.builder()
                .name(canonicalName)
                .relyingPartyId(descriptor.relyingPartyId())
                .credentialId(descriptor.credentialId())
                .publicKeyCose(descriptor.publicKeyCose())
                .signatureCounter(descriptor.signatureCounter())
                .userVerificationRequired(descriptor.userVerificationRequired())
                .algorithm(descriptor.algorithm())
                .build();
    }

    private static String resolveCanonicalName(WebAuthnAttestationCredentialDescriptor descriptor) {
        byte[] credentialId = descriptor.credentialDescriptor().credentialId();
        Optional<WebAuthnGeneratorSamples.Sample> directMatch = WebAuthnGeneratorSamples.samples().stream()
                .filter(sample -> Arrays.equals(sample.credentialId(), credentialId))
                .findFirst();
        if (directMatch.isPresent()) {
            return directMatch.get().key();
        }

        Optional<WebAuthnGeneratorSamples.Sample> byAlgorithm = WebAuthnGeneratorSamples.samples().stream()
                .filter(sample ->
                        sample.algorithm() == descriptor.credentialDescriptor().algorithm())
                .findFirst();
        if (byAlgorithm.isPresent()) {
            return byAlgorithm.get().key();
        }

        String attestationId = descriptor.attestationId();
        if (attestationId != null && attestationId.startsWith("w3c-")) {
            String trimmed = attestationId.substring("w3c-".length());
            Optional<WebAuthnGeneratorSamples.Sample> byTrimmedName = WebAuthnGeneratorSamples.samples().stream()
                    .filter(sample -> sample.key().equals(trimmed))
                    .findFirst();
            if (byTrimmedName.isPresent()) {
                return byTrimmedName.get().key();
            }
        }

        throw new IllegalStateException("Unable to resolve canonical credential name for " + descriptor.name());
    }

    private static String resolveLabel(WebAuthnAttestationCredentialDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        String algorithmLabel = descriptor.credentialDescriptor().algorithm() == null
                ? ""
                : descriptor.credentialDescriptor().algorithm().label();
        String formatLabel =
                descriptor.format() == null ? "" : descriptor.format().label();
        String descriptorOrigin =
                descriptor.origin() == null ? "" : descriptor.origin().trim();
        String attestationId = descriptor.attestationId();
        String section = "";
        String origin = descriptorOrigin;
        if (attestationId != null && !attestationId.isBlank()) {
            try {
                io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector vector =
                        WebAuthnAttestationSamples.require(attestationId);
                section = vector.w3cSection() == null ? "" : vector.w3cSection().trim();
                if (origin.isBlank() && vector.origin() != null) {
                    origin = vector.origin().trim();
                }
            } catch (IllegalArgumentException ignored) {
                // fall through to defaults
            }
        }
        if (!algorithmLabel.isBlank() && !formatLabel.isBlank() && !section.isBlank()) {
            return algorithmLabel + " (" + formatLabel + ", W3C " + section + ")";
        }
        if (!algorithmLabel.isBlank() && !formatLabel.isBlank() && !origin.isBlank()) {
            return algorithmLabel + " (" + formatLabel + ", " + origin + ")";
        }
        if (!algorithmLabel.isBlank() && !formatLabel.isBlank()) {
            return algorithmLabel + " (" + formatLabel + ")";
        }
        if (!algorithmLabel.isBlank()) {
            return algorithmLabel;
        }
        return descriptor.name();
    }
}
