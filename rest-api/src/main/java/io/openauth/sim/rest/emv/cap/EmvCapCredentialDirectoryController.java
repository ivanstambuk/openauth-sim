package io.openauth.sim.rest.emv.cap;

import io.openauth.sim.core.emv.cap.EmvCapCredentialPersistenceAdapter;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.store.CredentialStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/emv/cap", produces = MediaType.APPLICATION_JSON_VALUE)
final class EmvCapCredentialDirectoryController {

    private static final String METADATA_PREFIX = "emv.cap.metadata.";
    private static final Comparator<EmvCapCredentialSummary> SUMMARY_COMPARATOR =
            Comparator.comparing(EmvCapCredentialSummary::label, String.CASE_INSENSITIVE_ORDER);

    private final CredentialStore credentialStore;

    EmvCapCredentialDirectoryController(ObjectProvider<CredentialStore> credentialStoreProvider) {
        this.credentialStore = credentialStoreProvider.getIfAvailable();
    }

    @GetMapping("/credentials")
    List<EmvCapCredentialSummary> listCredentials() {
        if (credentialStore == null) {
            return List.of();
        }
        return credentialStore.findAll().stream()
                .filter(credential -> credential.type() == CredentialType.EMV_CA)
                .map(EmvCapCredentialDirectoryController::toSummary)
                .filter(Objects::nonNull)
                .sorted(SUMMARY_COMPARATOR)
                .collect(Collectors.toUnmodifiableList());
    }

    private static EmvCapCredentialSummary toSummary(Credential credential) {
        Map<String, String> attributes = credential.attributes();

        String mode = attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_MODE);
        String masterKey = credential.secret().asHex();
        String defaultAtc = attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_DEFAULT_ATC);
        Integer branchFactor = parsePositiveInt(attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_BRANCH_FACTOR));
        Integer height = parsePositiveInt(attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_HEIGHT));
        String iv = attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_IV);
        String cdol1 = attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_CDOL1);
        String issuerProprietaryBitmap = attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_IPB);
        String iccTemplate = attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_ICC_TEMPLATE);
        String issuerApplicationData = attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_ISSUER_APPLICATION_DATA);

        if (!StringUtils.hasText(mode)
                || masterKey == null
                || !StringUtils.hasText(defaultAtc)
                || branchFactor == null
                || height == null
                || !StringUtils.hasText(iv)
                || !StringUtils.hasText(cdol1)
                || !StringUtils.hasText(issuerProprietaryBitmap)
                || !StringUtils.hasText(iccTemplate)
                || !StringUtils.hasText(issuerApplicationData)) {
            return null;
        }

        Defaults defaults = new Defaults(
                attributes.getOrDefault(EmvCapCredentialPersistenceAdapter.ATTR_DEFAULT_CHALLENGE, ""),
                attributes.getOrDefault(EmvCapCredentialPersistenceAdapter.ATTR_DEFAULT_REFERENCE, ""),
                attributes.getOrDefault(EmvCapCredentialPersistenceAdapter.ATTR_DEFAULT_AMOUNT, ""));

        Transaction transaction = new Transaction(
                attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_TRANSACTION_TERMINAL),
                attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_TRANSACTION_ICC),
                attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_TRANSACTION_ICC_RESOLVED));

        Map<String, String> metadata = extractMetadata(attributes);
        String masterKeySha256 = computeMasterKeyDigest(masterKey);
        String label = Optional.ofNullable(metadata.get("presetLabel"))
                .filter(StringUtils::hasText)
                .orElse(credential.name());

        return new EmvCapCredentialSummary(
                credential.name(),
                label,
                mode.toUpperCase(Locale.ROOT),
                masterKey,
                masterKeySha256,
                defaultAtc,
                branchFactor,
                height,
                iv,
                cdol1,
                issuerProprietaryBitmap,
                iccTemplate,
                issuerApplicationData,
                defaults,
                transaction,
                metadata);
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
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String computeMasterKeyDigest(String masterKeyHex) {
        if (!StringUtils.hasText(masterKeyHex)) {
            return "sha256:UNAVAILABLE";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = hexToBytes(masterKeyHex.trim());
            byte[] hashed = digest.digest(keyBytes);
            return "sha256:" + toHex(hashed);
        } catch (NoSuchAlgorithmException | IllegalArgumentException ex) {
            return "sha256:UNAVAILABLE";
        }
    }

    private static byte[] hexToBytes(String hex) {
        String normalized = hex.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if ((normalized.length() & 1) == 1) {
            throw new IllegalArgumentException("Hex input must contain an even number of characters");
        }
        byte[] data = new byte[normalized.length() / 2];
        for (int index = 0; index < normalized.length(); index += 2) {
            data[index / 2] = (byte) Integer.parseInt(normalized.substring(index, index + 2), 16);
        }
        return data;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(Locale.ROOT, "%02X", value));
        }
        return builder.toString();
    }

    record EmvCapCredentialSummary(
            String id,
            String label,
            String mode,
            String masterKey,
            String masterKeySha256,
            String defaultAtc,
            Integer branchFactor,
            Integer height,
            String iv,
            String cdol1,
            String issuerProprietaryBitmap,
            String iccDataTemplate,
            String issuerApplicationData,
            Defaults defaults,
            Transaction transaction,
            Map<String, String> metadata) {

        EmvCapCredentialSummary {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(masterKey, "masterKey");
            Objects.requireNonNull(defaultAtc, "defaultAtc");
            Objects.requireNonNull(branchFactor, "branchFactor");
            Objects.requireNonNull(height, "height");
            Objects.requireNonNull(iv, "iv");
            Objects.requireNonNull(cdol1, "cdol1");
            Objects.requireNonNull(issuerProprietaryBitmap, "issuerProprietaryBitmap");
            Objects.requireNonNull(iccDataTemplate, "iccDataTemplate");
            Objects.requireNonNull(issuerApplicationData, "issuerApplicationData");
            Objects.requireNonNull(masterKeySha256, "masterKeySha256");
            defaults = defaults == null ? new Defaults("", "", "") : defaults;
            transaction = transaction == null ? new Transaction(null, null, null) : transaction;
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    record Defaults(String challenge, String reference, String amount) {
        Defaults {
            challenge = challenge == null ? "" : challenge;
            reference = reference == null ? "" : reference;
            amount = amount == null ? "" : amount;
        }
    }

    record Transaction(String terminal, String icc, String iccResolved) {
        // Data holder for optional transaction overrides.
    }
}
