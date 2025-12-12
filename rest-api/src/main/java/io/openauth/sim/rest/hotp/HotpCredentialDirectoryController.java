package io.openauth.sim.rest.hotp;

import io.openauth.sim.application.hotp.HotpCredentialDirectoryApplicationService;
import io.openauth.sim.application.hotp.HotpCredentialDirectoryApplicationService.Summary;
import io.openauth.sim.application.hotp.HotpSampleApplicationService;
import io.openauth.sim.rest.ui.HotpOperatorSampleData;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/hotp", produces = MediaType.APPLICATION_JSON_VALUE)
final class HotpCredentialDirectoryController {

    private static final Comparator<HotpCredentialSummary> SUMMARY_COMPARATOR =
            Comparator.comparing(HotpCredentialSummary::id, String.CASE_INSENSITIVE_ORDER);

    private final HotpCredentialDirectoryApplicationService directoryService;
    private final HotpCredentialSeedService seedService;
    private final HotpSampleApplicationService sampleService;

    HotpCredentialDirectoryController(
            ObjectProvider<HotpCredentialDirectoryApplicationService> directoryServiceProvider,
            HotpCredentialSeedService seedService,
            HotpSampleApplicationService sampleService) {
        this.directoryService = directoryServiceProvider.getIfAvailable();
        this.seedService = Objects.requireNonNull(seedService, "seedService");
        this.sampleService = Objects.requireNonNull(sampleService, "sampleService");
    }

    @GetMapping("/credentials")
    List<HotpCredentialSummary> listCredentials() {
        if (directoryService == null) {
            return List.of();
        }
        return directoryService.list().stream()
                .map(HotpCredentialDirectoryController::toSummary)
                .sorted(SUMMARY_COMPARATOR)
                .toList();
    }

    @PostMapping(value = "/credentials/seed", produces = MediaType.APPLICATION_JSON_VALUE)
    SeedResponse seedCredentials() {
        HotpCredentialSeedService.SeedResult result = seedService.seedCanonicalCredentials();
        return new SeedResponse(result.addedCount(), result.canonicalCount(), result.addedCredentialIds());
    }

    @GetMapping("/credentials/{credentialId}/sample")
    ResponseEntity<HotpStoredSampleResponse> storedSample(@PathVariable("credentialId") String credentialId) {
        if (!StringUtils.hasText(credentialId)) {
            return ResponseEntity.notFound().build();
        }

        return sampleService
                .storedSample(credentialId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private HotpStoredSampleResponse toResponse(HotpSampleApplicationService.StoredSample sample) {
        return new HotpStoredSampleResponse(
                sample.credentialId(),
                sample.otp(),
                sample.counter(),
                sample.algorithm().name(),
                sample.digits(),
                sample.metadata());
    }

    private static HotpCredentialSummary toSummary(Summary summary) {
        Integer digits = parseInteger(summary.digits());
        Long counter = parseLong(summary.counter());
        String label = HotpOperatorSampleData.findByCredentialId(summary.credentialId())
                .map(HotpOperatorSampleData.SampleDefinition::optionLabel)
                .orElseGet(() -> buildLabel(summary.label(), summary.credentialId(), summary.algorithm(), digits));
        return new HotpCredentialSummary(summary.credentialId(), label, digits, counter);
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String buildLabel(String preferredLabel, String id, String algorithm, Integer digits) {
        if (preferredLabel != null && !preferredLabel.isBlank()) {
            return preferredLabel.trim();
        }
        if (id == null) {
            return null;
        }
        String trimmedId = id.trim();
        boolean hasAlgorithm = algorithm != null && !algorithm.isBlank();
        boolean hasDigits = digits != null;
        if (!hasAlgorithm && !hasDigits) {
            return trimmedId;
        }

        StringBuilder builder = new StringBuilder(trimmedId).append(" (");
        if (hasAlgorithm) {
            builder.append(algorithm);
            if (hasDigits) {
                builder.append(", ");
            }
        }
        if (hasDigits) {
            builder.append(digits).append(" digits");
        }
        builder.append(')');
        return builder.toString();
    }

    record HotpCredentialSummary(String id, String label, Integer digits, Long counter) {
        HotpCredentialSummary {
            Objects.requireNonNull(id, "id");
        }
    }

    static final class SeedResponse {
        private final int addedCount;
        private final int canonicalCount;
        private final List<String> addedCredentialIds;

        SeedResponse(int addedCount, int canonicalCount, List<String> addedCredentialIds) {
            this.addedCount = addedCount;
            this.canonicalCount = canonicalCount;
            this.addedCredentialIds = List.copyOf(addedCredentialIds);
        }

        public int getAddedCount() {
            return addedCount;
        }

        public int getCanonicalCount() {
            return canonicalCount;
        }

        public List<String> getAddedCredentialIds() {
            return addedCredentialIds;
        }
    }
}
