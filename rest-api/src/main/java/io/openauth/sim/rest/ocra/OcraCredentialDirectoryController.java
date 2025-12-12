package io.openauth.sim.rest.ocra;

import io.openauth.sim.application.ocra.OcraCredentialManagementApplicationService;
import io.openauth.sim.application.ocra.OcraCredentialManagementApplicationService.Summary;
import io.openauth.sim.rest.ui.OcraOperatorSampleData;
import io.openauth.sim.rest.ui.OcraOperatorSampleData.SampleDefinition;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/ocra", produces = MediaType.APPLICATION_JSON_VALUE)
final class OcraCredentialDirectoryController {

    private static final Comparator<OcraCredentialSummary> SUMMARY_COMPARATOR =
            Comparator.comparing(OcraCredentialSummary::getId, String.CASE_INSENSITIVE_ORDER);

    private final OcraCredentialManagementApplicationService credentialManagement;
    private final OcraCredentialSeedService seedService;

    OcraCredentialDirectoryController(
            ObjectProvider<OcraCredentialManagementApplicationService> credentialManagementProvider,
            OcraCredentialSeedService seedService) {
        this.credentialManagement = credentialManagementProvider.getIfAvailable();
        this.seedService = Objects.requireNonNull(seedService, "seedService");
    }

    @GetMapping("/credentials")
    List<OcraCredentialSummary> listCredentials() {
        if (credentialManagement == null) {
            return List.of();
        }
        return credentialManagement.list().stream()
                .map(OcraCredentialDirectoryController::toSummary)
                .sorted(SUMMARY_COMPARATOR)
                .toList();
    }

    @PostMapping(value = "/credentials/seed", produces = MediaType.APPLICATION_JSON_VALUE)
    SeedResponse seedCredentials() {
        OcraCredentialSeedService.SeedResult result = seedService.seedCanonicalCredentials();
        return new SeedResponse(result.addedCount(), result.canonicalCount(), result.addedCredentialIds());
    }

    @GetMapping("/credentials/{credentialId}/sample")
    ResponseEntity<OcraCredentialSampleResponse> fetchSample(@PathVariable("credentialId") String credentialId) {
        if (credentialManagement == null || !StringUtils.hasText(credentialId)) {
            return ResponseEntity.notFound().build();
        }

        return credentialManagement
                .find(credentialId)
                .flatMap(OcraOperatorSampleData::findByDescriptor)
                .map(definition -> buildSampleResponse(credentialId, definition))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/credentials/{credentialId}")
    ResponseEntity<Map<String, String>> delete(@PathVariable("credentialId") String credentialId) {
        if (credentialManagement == null || !StringUtils.hasText(credentialId)) {
            return ResponseEntity.notFound().build();
        }
        boolean removed = credentialManagement.delete(credentialId.trim());
        if (!removed) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("credentialId", credentialId.trim(), "status", "deleted"));
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

    private static OcraCredentialSummary toSummary(Summary summary) {
        String label = buildLabel(summary.credentialId(), summary.suite());
        return new OcraCredentialSummary(summary.credentialId(), label, summary.suite());
    }

    private static OcraCredentialSampleResponse buildSampleResponse(String credentialId, SampleDefinition definition) {
        String presetKey = definition.metadata().get("presetKey");
        OcraCredentialSampleResponse.Context context = new OcraCredentialSampleResponse.Context(
                definition.challenge(),
                definition.sessionHex(),
                definition.clientChallenge(),
                definition.serverChallenge(),
                definition.pinHashHex(),
                definition.timestampHex(),
                definition.counter());
        return new OcraCredentialSampleResponse(
                credentialId, presetKey, definition.suite(), definition.expectedOtp(), context);
    }

    private static String buildLabel(String identifier, String suite) {
        if (!StringUtils.hasText(identifier)) {
            return identifier;
        }
        if (!StringUtils.hasText(suite)) {
            return identifier;
        }
        StringBuilder builder = new StringBuilder(identifier).append(" (").append(suite);
        if (isRfc6287Preset(identifier, suite)) {
            builder.append(", RFC 6287");
        }
        return builder.append(')').toString();
    }

    private static boolean isRfc6287Preset(String identifier, String suite) {
        Optional<SampleDefinition> fromCredential = StringUtils.hasText(identifier)
                ? OcraOperatorSampleData.findByCredentialName(identifier)
                : Optional.empty();
        Optional<SampleDefinition> fromSuite =
                StringUtils.hasText(suite) ? OcraOperatorSampleData.findBySuite(suite) : Optional.empty();
        return fromCredential
                .or(() -> fromSuite)
                .map(SampleDefinition::label)
                .map(label -> label.contains("(RFC 6287)"))
                .orElse(false);
    }
}
