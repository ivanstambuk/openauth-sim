package io.openauth.sim.application.emv.cap;

import io.openauth.sim.core.emv.cap.EmvCapCredentialDescriptor;
import io.openauth.sim.core.emv.cap.EmvCapCredentialPersistenceAdapter;
import io.openauth.sim.core.emv.cap.EmvCapMode;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Placeholder seeding service for EMV/CAP stored credentials.
 *
 * <p>The implementation will populate MapDB persistence once the seeding specification is wired in.
 */
public final class EmvCapSeedApplicationService {

    private static final String METADATA_PREFIX = "emv.cap.metadata.";

    private final EmvCapCredentialPersistenceAdapter persistenceAdapter = new EmvCapCredentialPersistenceAdapter();

    /** Seeds canonical EMV/CAP credentials into the provided {@link CredentialStore}. */
    public SeedResult seed(List<SeedCommand> commands, CredentialStore credentialStore) {
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(credentialStore, "credentialStore");

        List<String> added = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (SeedCommand command : commands) {
            if (!seen.add(command.credentialId())) {
                continue;
            }
            if (credentialStore.exists(command.credentialId())) {
                continue;
            }

            EmvCapCredentialDescriptor descriptor = toDescriptor(command);
            Credential baseCredential =
                    VersionedCredentialRecordMapper.toCredential(persistenceAdapter.serialize(descriptor));

            Map<String, String> attributes = new LinkedHashMap<>(baseCredential.attributes());
            command.metadata().forEach((key, value) -> {
                if (value == null) {
                    return;
                }
                String sanitized = value.trim();
                if (!sanitized.isEmpty()) {
                    attributes.put(METADATA_PREFIX + key, sanitized);
                }
            });

            Credential persisted = new Credential(
                    baseCredential.name(),
                    CredentialType.EMV_CA,
                    baseCredential.secret(),
                    attributes,
                    baseCredential.createdAt(),
                    baseCredential.updatedAt());

            credentialStore.save(persisted);
            added.add(command.credentialId());
        }

        return new SeedResult(List.copyOf(added));
    }

    private static EmvCapCredentialDescriptor toDescriptor(SeedCommand command) {
        SecretMaterial masterKey = SecretMaterial.fromHex(command.masterKeyHex());
        return new EmvCapCredentialDescriptor(
                command.credentialId(),
                command.mode(),
                masterKey,
                command.atcHex(),
                command.branchFactor(),
                command.height(),
                command.ivHex(),
                command.cdol1Hex(),
                command.issuerProprietaryBitmapHex(),
                command.iccTemplateHex(),
                command.issuerApplicationDataHex(),
                command.defaultChallenge(),
                command.defaultReference(),
                command.defaultAmount(),
                command.terminalDataHex(),
                command.iccDataHex(),
                command.resolvedIccDataHex());
    }

    /** Command describing an EMV/CAP credential to seed. */
    public record SeedCommand(
            String credentialId,
            EmvCapMode mode,
            String masterKeyHex,
            String atcHex,
            int branchFactor,
            int height,
            String ivHex,
            String cdol1Hex,
            String issuerProprietaryBitmapHex,
            String iccTemplateHex,
            String issuerApplicationDataHex,
            String defaultChallenge,
            String defaultReference,
            String defaultAmount,
            Optional<String> terminalDataHex,
            Optional<String> iccDataHex,
            Optional<String> resolvedIccDataHex,
            Map<String, String> metadata) {

        public SeedCommand {
            Objects.requireNonNull(credentialId, "credentialId");
            Objects.requireNonNull(mode, "mode");
            masterKeyHex = normalizeHex(masterKeyHex, "masterKeyHex");
            atcHex = normalizeHex(atcHex, "atcHex");
            branchFactor = requirePositive(branchFactor, "branchFactor");
            height = requirePositive(height, "height");
            ivHex = normalizeHex(ivHex, "ivHex");
            cdol1Hex = normalizeHex(cdol1Hex, "cdol1Hex");
            issuerProprietaryBitmapHex = normalizeHex(issuerProprietaryBitmapHex, "issuerProprietaryBitmapHex");
            iccTemplateHex = normalizeTemplate(iccTemplateHex, "iccTemplateHex");
            issuerApplicationDataHex = normalizeHex(issuerApplicationDataHex, "issuerApplicationDataHex");
            defaultChallenge = normalizeDigits(defaultChallenge);
            defaultReference = normalizeDigits(defaultReference);
            defaultAmount = normalizeDigits(defaultAmount);
            terminalDataHex = terminalDataHex == null ? Optional.empty() : terminalDataHex;
            iccDataHex = iccDataHex == null ? Optional.empty() : iccDataHex;
            resolvedIccDataHex = resolvedIccDataHex == null ? Optional.empty() : resolvedIccDataHex;
            metadata = normalizeMetadata(metadata);
        }

        private static int requirePositive(int value, String field) {
            if (value <= 0) {
                throw new IllegalArgumentException(field + " must be positive");
            }
            return value;
        }

        private static String normalizeHex(String value, String field) {
            Objects.requireNonNull(value, field);
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException(field + " must not be blank");
            }
            if ((normalized.length() & 1) == 1) {
                throw new IllegalArgumentException(field + " must contain an even number of hexadecimal characters");
            }
            if (!normalized.matches("[0-9A-F]+")) {
                throw new IllegalArgumentException(field + " must be hexadecimal");
            }
            return normalized;
        }

        private static String normalizeTemplate(String value, String field) {
            Objects.requireNonNull(value, field);
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException(field + " must not be blank");
            }
            if ((normalized.length() & 1) == 1) {
                throw new IllegalArgumentException(field + " must contain an even number of characters");
            }
            if (!normalized.matches("[0-9A-FX]+")) {
                throw new IllegalArgumentException(field + " must contain hexadecimal characters or 'X'");
            }
            return normalized;
        }

        private static String normalizeDigits(String value) {
            if (value == null) {
                return "";
            }
            return value.trim();
        }

        private static Map<String, String> normalizeMetadata(Map<String, String> metadata) {
            if (metadata == null || metadata.isEmpty()) {
                return Map.of();
            }
            Map<String, String> sanitized = new LinkedHashMap<>();
            metadata.forEach((key, value) -> {
                if (key == null || key.isBlank() || value == null) {
                    return;
                }
                String trimmedValue = value.trim();
                if (!trimmedValue.isEmpty()) {
                    sanitized.put(key.trim(), trimmedValue);
                }
            });
            return Map.copyOf(sanitized);
        }
    }

    /** Result capturing newly added credential identifiers. */
    public record SeedResult(List<String> addedCredentialIds) {

        public SeedResult {
            addedCredentialIds = List.copyOf(Objects.requireNonNull(addedCredentialIds, "addedCredentialIds"));
        }
    }
}
