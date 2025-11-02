package io.openauth.sim.rest.emv.cap;

import io.openauth.sim.application.emv.cap.EmvCapSeedApplicationService;
import io.openauth.sim.application.emv.cap.EmvCapSeedApplicationService.SeedCommand;
import io.openauth.sim.application.emv.cap.EmvCapSeedApplicationService.SeedResult;
import io.openauth.sim.application.emv.cap.EmvCapSeedSamples;
import io.openauth.sim.application.emv.cap.EmvCapSeedSamples.SeedSample;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.store.CredentialStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
final class EmvCapCredentialSeedService {

    private static final Logger TELEMETRY_LOGGER = Logger.getLogger("io.openauth.sim.rest.emv.cap.telemetry");

    static {
        TELEMETRY_LOGGER.setLevel(Level.ALL);
    }

    private final CredentialStore credentialStore;
    private final EmvCapSeedApplicationService seedApplicationService;

    EmvCapCredentialSeedService(
            ObjectProvider<CredentialStore> credentialStoreProvider,
            EmvCapSeedApplicationService seedApplicationService) {
        this.credentialStore = credentialStoreProvider.getIfAvailable();
        this.seedApplicationService = Objects.requireNonNull(seedApplicationService, "seedApplicationService");
    }

    SeedOperationResult seedCanonicalCredentials() {
        List<SeedSample> definitions = EmvCapSeedSamples.samples();
        if (credentialStore == null) {
            SeedOperationResult result = SeedOperationResult.disabled(definitions.size());
            logSeed(Level.WARNING, "unavailable", result, "credential store unavailable");
            return result;
        }

        List<SeedCommand> commands =
                definitions.stream().map(SeedSample::toSeedCommand).collect(Collectors.toUnmodifiableList());

        SeedResult applicationResult = seedApplicationService.seed(commands, credentialStore);
        SeedOperationResult result =
                new SeedOperationResult(definitions.size(), applicationResult.addedCredentialIds());
        logSeed(Level.INFO, result.addedCount() == 0 ? "noop" : "seeded", result, null);
        return result;
    }

    private void logSeed(Level level, String status, SeedOperationResult result, String reason) {
        String telemetryId = "rest-emv-cap-seed-" + UUID.randomUUID();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("addedCount", result.addedCount());
        fields.put("canonicalCount", result.canonicalCount());
        fields.put("existingCount", result.existingCount());
        fields.put("trigger", "ui");
        if (!result.addedCredentialIds().isEmpty()) {
            fields.put("addedCredentialIds", result.addedCredentialIds());
        }

        TelemetryFrame frame =
                TelemetryContracts.emvCapSeedingAdapter().status(status, telemetryId, status, true, reason, fields);
        logFrame(level, frame);
    }

    private void logFrame(Level level, TelemetryFrame frame) {
        Objects.requireNonNull(frame, "frame");
        StringBuilder builder = new StringBuilder("event=rest.")
                .append(frame.event())
                .append(" status=")
                .append(frame.status());
        frame.fields()
                .forEach((key, value) ->
                        builder.append(' ').append(key).append('=').append(value));
        LogRecord record = new LogRecord(level, builder.toString());
        TELEMETRY_LOGGER.log(record);
        for (Handler handler : TELEMETRY_LOGGER.getHandlers()) {
            handler.publish(record);
            handler.flush();
        }
    }

    static final class SeedOperationResult {
        private final int canonicalCount;
        private final List<String> addedCredentialIds;

        SeedOperationResult(int canonicalCount, List<String> addedCredentialIds) {
            this.canonicalCount = canonicalCount;
            this.addedCredentialIds = List.copyOf(addedCredentialIds);
        }

        static SeedOperationResult disabled(int canonicalCount) {
            return new SeedOperationResult(canonicalCount, List.of());
        }

        int canonicalCount() {
            return canonicalCount;
        }

        List<String> addedCredentialIds() {
            return addedCredentialIds;
        }

        int addedCount() {
            return addedCredentialIds.size();
        }

        int existingCount() {
            return canonicalCount - addedCredentialIds.size();
        }
    }
}
