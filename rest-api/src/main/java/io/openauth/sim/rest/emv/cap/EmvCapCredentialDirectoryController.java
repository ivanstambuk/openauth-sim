package io.openauth.sim.rest.emv.cap;

import io.openauth.sim.application.emv.cap.EmvCapCredentialDirectoryApplicationService;
import io.openauth.sim.application.emv.cap.EmvCapCredentialDirectoryApplicationService.Hydration;
import io.openauth.sim.application.emv.cap.EmvCapCredentialDirectoryApplicationService.Summary;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/emv/cap", produces = MediaType.APPLICATION_JSON_VALUE)
final class EmvCapCredentialDirectoryController {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmvCapCredentialDirectoryController.class);
    private static final Comparator<EmvCapCredentialSummary> SUMMARY_COMPARATOR =
            Comparator.comparing(EmvCapCredentialSummary::label, String.CASE_INSENSITIVE_ORDER);

    private final EmvCapCredentialDirectoryApplicationService directoryService;

    EmvCapCredentialDirectoryController(ObjectProvider<EmvCapCredentialDirectoryApplicationService> directoryService) {
        this.directoryService = directoryService.getIfAvailable();
    }

    @GetMapping("/credentials")
    List<EmvCapCredentialSummary> listCredentials() {
        if (directoryService == null) {
            return List.of();
        }
        return directoryService.list().stream()
                .map(EmvCapCredentialDirectoryController::toSummary)
                .sorted(SUMMARY_COMPARATOR)
                .collect(Collectors.toUnmodifiableList());
    }

    @GetMapping("/credentials/{credentialId}")
    ResponseEntity<EmvCapCredentialHydration> credentialDetail(@PathVariable("credentialId") String credentialId) {
        if (directoryService == null || !StringUtils.hasText(credentialId)) {
            return ResponseEntity.notFound().build();
        }
        LOGGER.info("Hydrating EMV/CAP credential {}", credentialId);
        return directoryService
                .detail(credentialId.trim())
                .map(EmvCapCredentialDirectoryController::toHydrationResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static EmvCapCredentialSummary toSummary(Summary summary) {
        return new EmvCapCredentialSummary(
                summary.credentialId(),
                summary.label(),
                summary.mode(),
                summary.masterKeySha256(),
                summary.masterKeyHexLength(),
                summary.defaultAtc(),
                summary.branchFactor(),
                summary.height(),
                summary.iv(),
                summary.cdol1HexLength(),
                summary.issuerProprietaryBitmapHexLength(),
                summary.iccDataTemplateHexLength(),
                summary.issuerApplicationDataHexLength(),
                new Defaults(
                        summary.defaults().challenge(),
                        summary.defaults().reference(),
                        summary.defaults().amount()),
                new Transaction(
                        summary.transaction().terminal(),
                        summary.transaction().icc(),
                        summary.transaction().iccResolved()),
                summary.metadata());
    }

    private static EmvCapCredentialHydration toHydrationResponse(Hydration hydration) {
        return new EmvCapCredentialHydration(
                hydration.credentialId(),
                hydration.mode(),
                hydration.masterKey(),
                hydration.cdol1(),
                hydration.issuerProprietaryBitmap(),
                hydration.iccDataTemplate(),
                hydration.issuerApplicationData(),
                new HydrationDefaults(
                        hydration.defaults().challenge(),
                        hydration.defaults().reference(),
                        hydration.defaults().amount()));
    }

    record EmvCapCredentialSummary(
            String id,
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
            Map<String, String> metadata) {

        EmvCapCredentialSummary {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(masterKeySha256, "masterKeySha256");
            Objects.requireNonNull(defaultAtc, "defaultAtc");
            Objects.requireNonNull(iv, "iv");
            Objects.requireNonNull(defaults, "defaults");
            Objects.requireNonNull(transaction, "transaction");
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    record Defaults(String challenge, String reference, String amount) {
        Defaults {
            Objects.requireNonNull(challenge, "challenge");
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(amount, "amount");
        }
    }

    record Transaction(String terminal, String icc, String iccResolved) {}

    record EmvCapCredentialHydration(
            String id,
            String mode,
            String masterKey,
            String cdol1,
            String issuerProprietaryBitmap,
            String iccDataTemplate,
            String issuerApplicationData,
            HydrationDefaults defaults) {

        EmvCapCredentialHydration {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(masterKey, "masterKey");
            Objects.requireNonNull(cdol1, "cdol1");
            Objects.requireNonNull(issuerProprietaryBitmap, "issuerProprietaryBitmap");
            Objects.requireNonNull(iccDataTemplate, "iccDataTemplate");
            Objects.requireNonNull(issuerApplicationData, "issuerApplicationData");
            Objects.requireNonNull(defaults, "defaults");
        }
    }

    record HydrationDefaults(String challenge, String reference, String amount) {
        HydrationDefaults {
            Objects.requireNonNull(challenge, "challenge");
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(amount, "amount");
        }
    }
}
