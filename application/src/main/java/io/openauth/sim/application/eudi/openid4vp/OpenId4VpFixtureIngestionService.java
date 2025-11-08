package io.openauth.sim.application.eudi.openid4vp;

import io.openauth.sim.application.eudi.openid4vp.fixtures.OpenId4VpStoredPresentationFixtures;
import io.openauth.sim.application.eudi.openid4vp.fixtures.OpenId4VpStoredPresentationFixtures.StoredPresentationFixture;
import io.openauth.sim.core.eudi.openid4vp.FixtureDatasets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Coordinates fixture ingestion toggles between synthetic and conformance datasets. */
public final class OpenId4VpFixtureIngestionService {
    private final Dependencies dependencies;

    public OpenId4VpFixtureIngestionService(Dependencies dependencies) {
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
    }

    public IngestionResult ingest(IngestionRequest request) {
        Objects.requireNonNull(request, "request");
        FixtureDatasets.FixtureDataset dataset = FixtureDatasets.load(request.source());
        List<StoredPresentationFixture> fixtures =
                dependencies.storedPresentations().loadAll();
        List<PresentationSummary> summaries = fixtures.stream()
                .filter(fixture -> fixture.source().equals(request.source()))
                .filter(fixture -> request.presentationIds().isEmpty()
                        || request.presentationIds().contains(fixture.presentationId()))
                .map(OpenId4VpFixtureIngestionService::toSummary)
                .collect(Collectors.toUnmodifiableList());
        ensureRequestedIdsPresent(request.presentationIds(), summaries);

        Map<String, Object> telemetryFields = new LinkedHashMap<>();
        telemetryFields.put("source", request.source().directoryName());
        telemetryFields.put("ingestedCount", summaries.size());
        telemetryFields.put("provenanceVersion", dataset.provenance().version());
        telemetryFields.put("provenanceHash", dataset.provenance().sha256());

        TelemetrySignal telemetry = dependencies
                .telemetryPublisher()
                .fixturesIngested(request.source(), telemetryFields, request.presentationIds());
        return new IngestionResult(request.source(), dataset.provenance(), summaries, telemetry);
    }

    private static PresentationSummary toSummary(StoredPresentationFixture fixture) {
        return new PresentationSummary(
                fixture.presentationId(), fixture.credentialId(), fixture.format(), fixture.trustedAuthorityPolicies());
    }

    private static void ensureRequestedIdsPresent(List<String> requestedIds, List<PresentationSummary> summaries) {
        if (requestedIds.isEmpty()) {
            return;
        }
        Set<String> missing = new LinkedHashSet<>(requestedIds);
        summaries.stream().map(PresentationSummary::presentationId).forEach(missing::remove);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Unknown stored presentation ids: " + missing);
        }
    }

    public record Dependencies(
            OpenId4VpStoredPresentationFixtures storedPresentations, TelemetryPublisher telemetryPublisher) {
        public Dependencies {
            Objects.requireNonNull(storedPresentations, "storedPresentations");
            Objects.requireNonNull(telemetryPublisher, "telemetryPublisher");
        }
    }

    public record IngestionRequest(FixtureDatasets.Source source, List<String> presentationIds) {
        public IngestionRequest {
            Objects.requireNonNull(source, "source");
            presentationIds = presentationIds == null ? List.of() : List.copyOf(presentationIds);
        }
    }

    public record IngestionResult(
            FixtureDatasets.Source source,
            FixtureDatasets.Provenance provenance,
            List<PresentationSummary> presentations,
            TelemetrySignal telemetry) {
        public IngestionResult {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(provenance, "provenance");
            presentations = presentations == null ? List.of() : List.copyOf(presentations);
            Objects.requireNonNull(telemetry, "telemetry");
        }
    }

    public record PresentationSummary(
            String presentationId, String credentialId, String format, List<String> trustedAuthorityPolicies) {
        public PresentationSummary {
            Objects.requireNonNull(presentationId, "presentationId");
            Objects.requireNonNull(credentialId, "credentialId");
            Objects.requireNonNull(format, "format");
            trustedAuthorityPolicies =
                    trustedAuthorityPolicies == null ? List.of() : List.copyOf(trustedAuthorityPolicies);
        }
    }

    public interface TelemetryPublisher {
        TelemetrySignal fixturesIngested(
                FixtureDatasets.Source source, Map<String, Object> fields, List<String> requestedPresentationIds);
    }

    public interface TelemetrySignal {
        String event();

        Map<String, Object> fields();
    }
}
