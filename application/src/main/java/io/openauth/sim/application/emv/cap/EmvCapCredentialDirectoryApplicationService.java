package io.openauth.sim.application.emv.cap;

import io.openauth.sim.core.emv.cap.EmvCapCredentialDescriptor;
import io.openauth.sim.core.emv.cap.EmvCapCredentialPersistenceAdapter;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** Lists and hydrates EMV/CAP credentials via the application layer. */
public final class EmvCapCredentialDirectoryApplicationService {

    private static final String METADATA_PREFIX = "emv.cap.metadata.";
    private static final EmvCapCredentialPersistenceAdapter PERSISTENCE_ADAPTER =
            new EmvCapCredentialPersistenceAdapter();

    private final CredentialStore credentialStore;

    public EmvCapCredentialDirectoryApplicationService(CredentialStore credentialStore) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
    }

    public List<Summary> list() {
        return credentialStore.findAll().stream()
                .filter(credential -> credential.type() == CredentialType.EMV_CA)
                .map(this::toSummary)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(Summary::label, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toUnmodifiableList());
    }

    public Optional<Hydration> detail(String credentialId) {
        Objects.requireNonNull(credentialId, "credentialId");
        return credentialStore
                .findByName(credentialId)
                .filter(credential -> credential.type() == CredentialType.EMV_CA)
                .flatMap(this::toHydration);
    }

    private Optional<Summary> toSummary(Credential credential) {
        Map<String, String> attributes = credential.attributes();

        String mode = attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_MODE);
        String masterKeyHex = credential.secret().asHex();
        String defaultAtc = attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_DEFAULT_ATC);
        Integer branchFactor = parsePositiveInt(attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_BRANCH_FACTOR));
        Integer height = parsePositiveInt(attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_HEIGHT));
        String iv = attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_IV);
        String cdol1 = attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_CDOL1);
        String issuerProprietaryBitmap = attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_IPB);
        String iccTemplate = attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_ICC_TEMPLATE);
        String issuerApplicationData = attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_ISSUER_APPLICATION_DATA);

        if (!isPresent(mode)
                || !isPresent(masterKeyHex)
                || !isPresent(defaultAtc)
                || branchFactor == null
                || height == null
                || !isPresent(iv)
                || !isPresent(cdol1)
                || !isPresent(issuerProprietaryBitmap)
                || !isPresent(iccTemplate)
                || !isPresent(issuerApplicationData)) {
            return Optional.empty();
        }

        String masterKeyTrimmed = masterKeyHex.trim();
        String cdol1Trimmed = cdol1.trim();
        String issuerBitmapTrimmed = issuerProprietaryBitmap.trim();
        String iccTemplateTrimmed = iccTemplate.trim();
        String issuerApplicationDataTrimmed = issuerApplicationData.trim();

        Defaults defaults = new Defaults(
                attributes.getOrDefault(EmvCapCredentialPersistenceAdapter.ATTR_DEFAULT_CHALLENGE, ""),
                attributes.getOrDefault(EmvCapCredentialPersistenceAdapter.ATTR_DEFAULT_REFERENCE, ""),
                attributes.getOrDefault(EmvCapCredentialPersistenceAdapter.ATTR_DEFAULT_AMOUNT, ""));

        Transaction transaction = new Transaction(
                attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_TRANSACTION_TERMINAL),
                attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_TRANSACTION_ICC),
                attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_TRANSACTION_ICC_RESOLVED));

        Map<String, String> metadata = extractMetadata(attributes);
        String masterKeySha256 = computeSha256(masterKeyTrimmed);
        String label = Optional.ofNullable(metadata.get("presetLabel"))
                .filter(EmvCapCredentialDirectoryApplicationService::isPresent)
                .orElse(credential.name());

        return Optional.of(new Summary(
                credential.name(),
                label,
                mode.toUpperCase(Locale.ROOT),
                masterKeySha256,
                masterKeyTrimmed.length(),
                defaultAtc,
                branchFactor,
                height,
                iv,
                cdol1Trimmed.length(),
                issuerBitmapTrimmed.length(),
                iccTemplateTrimmed.length(),
                issuerApplicationDataTrimmed.length(),
                defaults,
                transaction,
                metadata));
    }

    private Optional<Hydration> toHydration(Credential credential) {
        try {
            EmvCapCredentialDescriptor descriptor =
                    PERSISTENCE_ADAPTER.deserialize(VersionedCredentialRecordMapper.toRecord(credential));
            return Optional.of(new Hydration(
                    credential.name(),
                    descriptor.mode().name(),
                    descriptor.masterKey().asHex().toUpperCase(Locale.ROOT),
                    descriptor.defaultAtcHex().toUpperCase(Locale.ROOT),
                    descriptor.branchFactor(),
                    descriptor.height(),
                    descriptor.ivHex().toUpperCase(Locale.ROOT),
                    descriptor.cdol1Hex().toUpperCase(Locale.ROOT),
                    descriptor.issuerProprietaryBitmapHex().toUpperCase(Locale.ROOT),
                    descriptor.iccDataTemplateHex().toUpperCase(Locale.ROOT),
                    descriptor.issuerApplicationDataHex().toUpperCase(Locale.ROOT),
                    descriptor.terminalDataHex().map(value -> value.toUpperCase(Locale.ROOT)),
                    descriptor.iccDataHex().map(value -> value.toUpperCase(Locale.ROOT)),
                    descriptor.resolvedIccDataHex().map(value -> value.toUpperCase(Locale.ROOT)),
                    new HydrationDefaults(
                            descriptor.defaultChallenge(), descriptor.defaultReference(), descriptor.defaultAmount())));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static Map<String, String> extractMetadata(Map<String, String> attributes) {
        Map<String, String> metadata = new LinkedHashMap<>();
        attributes.forEach((key, value) -> {
            if (key != null && key.startsWith(METADATA_PREFIX) && value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    metadata.put(key.substring(METADATA_PREFIX.length()), trimmed);
                }
            }
        });
        return metadata;
    }

    private static Integer parsePositiveInt(String value) {
        if (!isPresent(value)) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String computeSha256(String hex) {
        if (!isPresent(hex)) {
            return "sha256:UNAVAILABLE";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(HexFormat.of().parseHex(hex.trim()));
            return "sha256:" + HexFormat.of().withUpperCase().formatHex(hashed);
        } catch (NoSuchAlgorithmException | IllegalArgumentException ex) {
            return "sha256:UNAVAILABLE";
        }
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public record Summary(
            String credentialId,
            String label,
            String mode,
            String masterKeySha256,
            int masterKeyHexLength,
            String defaultAtc,
            Integer branchFactor,
            Integer height,
            String iv,
            int cdol1HexLength,
            int issuerProprietaryBitmapHexLength,
            int iccDataTemplateHexLength,
            int issuerApplicationDataHexLength,
            Defaults defaults,
            Transaction transaction,
            Map<String, String> metadata) {}

    public record Defaults(String challenge, String reference, String amount) {}

    public record Transaction(String terminal, String icc, String iccResolved) {}

    public record Hydration(
            String credentialId,
            String mode,
            String masterKey,
            String defaultAtc,
            int branchFactor,
            int height,
            String iv,
            String cdol1,
            String issuerProprietaryBitmap,
            String iccDataTemplate,
            String issuerApplicationData,
            Optional<String> terminalData,
            Optional<String> iccData,
            Optional<String> resolvedIccData,
            HydrationDefaults defaults) {}

    public record HydrationDefaults(String challenge, String reference, String amount) {}
}
