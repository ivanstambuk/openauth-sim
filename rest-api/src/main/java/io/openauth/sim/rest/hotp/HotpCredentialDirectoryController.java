package io.openauth.sim.rest.hotp;

import io.openauth.sim.application.hotp.HotpSampleApplicationService;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.store.CredentialStore;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
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

    private static final String ATTR_DIGITS = "hotp.digits";
    private static final String ATTR_COUNTER = "hotp.counter";
    private static final String ATTR_ALGORITHM = "hotp.algorithm";

    private final CredentialStore credentialStore;
    private final HotpCredentialSeedService seedService;
    private final HotpSampleApplicationService sampleService;

    HotpCredentialDirectoryController(
            ObjectProvider<CredentialStore> credentialStoreProvider,
            HotpCredentialSeedService seedService,
            HotpSampleApplicationService sampleService) {
        this.credentialStore = credentialStoreProvider.getIfAvailable();
        this.seedService = Objects.requireNonNull(seedService, "seedService");
        this.sampleService = Objects.requireNonNull(sampleService, "sampleService");
    }

    @GetMapping("/credentials")
    List<HotpCredentialSummary> listCredentials() {
        if (credentialStore == null) {
            return List.of();
        }
        return credentialStore.findAll().stream()
                .filter(credential -> credential.type() == CredentialType.OATH_HOTP)
                .map(HotpCredentialDirectoryController::toSummary)
                .sorted(SUMMARY_COMPARATOR)
                .collect(Collectors.toUnmodifiableList());
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

    private static HotpCredentialSummary toSummary(Credential credential) {
        Map<String, String> attributes = credential.attributes();
        Integer digits = parseInteger(attributes.get(ATTR_DIGITS));
        Long counter = parseLong(attributes.get(ATTR_COUNTER));
        String algorithm = attributes.get(ATTR_ALGORITHM);
        String label = buildLabel(credential.name(), algorithm, digits, attributes);
        return new HotpCredentialSummary(credential.name(), label, digits, counter);
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

    private static String buildLabel(String id, String algorithm, Integer digits, Map<String, String> attributes) {
        if (id == null) {
            return null;
        }
        String trimmedId = id.trim();
        boolean hasAlgorithm = algorithm != null && !algorithm.isBlank();
        boolean hasDigits = digits != null;
        if (!hasAlgorithm && !hasDigits) {
            return trimmedId;
        }

        boolean isRfc4226 =
                attributes != null && "ui-hotp-demo".equals(attributes.get("hotp.metadata.presetKey")) && hasDigits;

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
        if (isRfc4226) {
            builder.append(", RFC 4226");
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
