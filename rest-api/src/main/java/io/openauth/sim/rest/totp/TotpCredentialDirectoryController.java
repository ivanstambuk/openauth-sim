package io.openauth.sim.rest.totp;

import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.application.totp.TotpSampleApplicationService;
import io.openauth.sim.application.totp.TotpSampleApplicationService.StoredSample;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.store.CredentialStore;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/totp", produces = MediaType.APPLICATION_JSON_VALUE)
final class TotpCredentialDirectoryController {

    private static final Logger TELEMETRY_LOGGER = Logger.getLogger("io.openauth.sim.rest.totp.telemetry");

    private static final Comparator<TotpCredentialSummary> SUMMARY_COMPARATOR =
            Comparator.comparing(TotpCredentialSummary::id, String.CASE_INSENSITIVE_ORDER);

    private static final String ATTR_ALGORITHM = "totp.algorithm";
    private static final String ATTR_DIGITS = "totp.digits";
    private static final String ATTR_STEP_SECONDS = "totp.stepSeconds";
    private static final String ATTR_DRIFT_BACKWARD = "totp.drift.backward";
    private static final String ATTR_DRIFT_FORWARD = "totp.drift.forward";
    private static final String ATTR_LABEL = "totp.metadata.label";

    private final CredentialStore credentialStore;
    private final TotpSampleApplicationService sampleService;

    TotpCredentialDirectoryController(
            ObjectProvider<CredentialStore> credentialStoreProvider, TotpSampleApplicationService sampleService) {
        this.credentialStore = credentialStoreProvider.getIfAvailable();
        this.sampleService = Objects.requireNonNull(sampleService, "sampleService");
    }

    @GetMapping("/credentials")
    List<TotpCredentialSummary> listCredentials() {
        if (credentialStore == null) {
            return List.of();
        }
        return credentialStore.findAll().stream()
                .filter(credential -> credential.type() == CredentialType.OATH_TOTP)
                .map(TotpCredentialDirectoryController::toSummary)
                .sorted(SUMMARY_COMPARATOR)
                .collect(Collectors.toUnmodifiableList());
    }

    @GetMapping("/credentials/{credentialId}/sample")
    ResponseEntity<TotpStoredSampleResponse> storedSample(@PathVariable("credentialId") String credentialId) {
        if (credentialStore == null || !StringUtils.hasText(credentialId)) {
            return ResponseEntity.notFound().build();
        }

        return sampleService
                .storedSample(credentialId)
                .map(sample -> {
                    logSampleTelemetry(sample);
                    return ResponseEntity.ok(toResponse(sample));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static TotpCredentialSummary toSummary(Credential credential) {
        Map<String, String> attributes = credential.attributes();
        String algorithm = attributes.getOrDefault(ATTR_ALGORITHM, "");
        Integer digits = parseInteger(attributes.get(ATTR_DIGITS));
        Long stepSeconds = parseLong(attributes.get(ATTR_STEP_SECONDS));
        Integer driftBackward = parseInteger(attributes.get(ATTR_DRIFT_BACKWARD));
        Integer driftForward = parseInteger(attributes.get(ATTR_DRIFT_FORWARD));

        String label = Optional.ofNullable(attributes.get(ATTR_LABEL))
                .filter(value -> !value.isBlank())
                .orElseGet(() -> buildLabel(credential.name(), algorithm, digits, stepSeconds));

        return new TotpCredentialSummary(
                credential.name(), label, algorithm, digits, stepSeconds, driftBackward, driftForward);
    }

    private static String buildLabel(String id, String algorithm, Integer digits, Long stepSeconds) {
        if (id == null) {
            return "";
        }
        String trimmedId = id.trim();
        boolean hasAlgorithm = algorithm != null && !algorithm.isBlank();
        boolean hasDigits = digits != null;
        boolean hasStep = stepSeconds != null;
        if (!hasAlgorithm && !hasDigits && !hasStep) {
            return trimmedId;
        }

        StringBuilder builder = new StringBuilder(trimmedId).append(" (");
        boolean appended = false;
        if (hasAlgorithm) {
            builder.append(algorithm);
            appended = true;
        }
        if (hasDigits) {
            if (appended) {
                builder.append(", ");
            }
            builder.append(digits).append(" digits");
            appended = true;
        }
        if (hasStep) {
            if (appended) {
                builder.append(", ");
            }
            builder.append(stepSeconds).append("s step");
        }
        builder.append(')');
        return builder.toString();
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

    record TotpCredentialSummary(
            String id,
            String label,
            String algorithm,
            Integer digits,
            Long stepSeconds,
            Integer driftBackward,
            Integer driftForward) {

        TotpCredentialSummary {
            Objects.requireNonNull(id, "id");
        }
    }

    private TotpStoredSampleResponse toResponse(StoredSample sample) {
        return new TotpStoredSampleResponse(
                sample.credentialId(),
                sample.algorithm().name(),
                sample.digits(),
                sample.stepSeconds(),
                sample.driftBackwardSteps(),
                sample.driftForwardSteps(),
                sample.timestampEpochSeconds(),
                sample.otp(),
                sample.metadata());
    }

    private void logSampleTelemetry(StoredSample sample) {
        if (sample == null) {
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("credentialId", sample.credentialId());
        fields.put("algorithm", sample.algorithm().name());
        fields.put("digits", sample.digits());
        fields.put("stepSeconds", sample.stepSeconds());
        fields.put("driftBackwardSteps", sample.driftBackwardSteps());
        fields.put("driftForwardSteps", sample.driftForwardSteps());
        fields.put("timestampEpochSeconds", sample.timestampEpochSeconds());
        TelemetryFrame frame = TelemetryContracts.totpSampleAdapter()
                .status("sampled", "rest-totp-sample-" + UUID.randomUUID(), "sampled", true, null, fields);
        logTelemetryFrame(frame);
    }

    private void logTelemetryFrame(TelemetryFrame frame) {
        if (frame == null) {
            return;
        }
        StringBuilder builder = new StringBuilder("event=")
                .append(frame.event())
                .append(" status=")
                .append(frame.status());
        frame.fields()
                .forEach((key, value) ->
                        builder.append(' ').append(key).append('=').append(value));
        LogRecord record = new LogRecord(Level.FINE, builder.toString());
        TELEMETRY_LOGGER.log(record);
        for (Handler handler : TELEMETRY_LOGGER.getHandlers()) {
            handler.publish(record);
            handler.flush();
        }
    }
}
