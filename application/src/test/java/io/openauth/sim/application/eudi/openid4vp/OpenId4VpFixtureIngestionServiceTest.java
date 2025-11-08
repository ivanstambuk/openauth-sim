package io.openauth.sim.application.eudi.openid4vp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.eudi.openid4vp.OpenId4VpFixtureIngestionService.IngestionRequest;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpFixtureIngestionService.IngestionResult;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpFixtureIngestionService.PresentationSummary;
import io.openauth.sim.application.eudi.openid4vp.fixtures.OpenId4VpStoredPresentationFixtures;
import io.openauth.sim.core.eudi.openid4vp.FixtureDatasets;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class OpenId4VpFixtureIngestionServiceTest {

    @TempDir
    Path tempDir;

    private Path storedPresentationsDir;

    @BeforeEach
    void setUp() throws IOException {
        storedPresentationsDir = tempDir.resolve("stored/presentations");
        Files.createDirectories(storedPresentationsDir);
        writePresentation(
                storedPresentationsDir.resolve("pid-synthetic.json"),
                "pid-synthetic",
                "fixtures/synthetic/sdjwt-vc/pid-synthetic/",
                "pid-synthetic",
                List.of("aki:synthetic"));
        writePresentation(
                storedPresentationsDir.resolve("pid-conformance.json"),
                "pid-conformance",
                "fixtures/conformance/sdjwt-vc/pid-conformance/",
                "pid-conformance",
                List.of("aki:conformance", "etsi_tl:EU-TL"));
    }

    @Test
    void syntheticIngestionIncludesTrustedAuthorityPolicies() {
        OpenId4VpStoredPresentationFixtures fixtures = new OpenId4VpStoredPresentationFixtures(storedPresentationsDir);
        RecordingTelemetry telemetry = new RecordingTelemetry();
        OpenId4VpFixtureIngestionService service = new OpenId4VpFixtureIngestionService(
                new OpenId4VpFixtureIngestionService.Dependencies(fixtures, telemetry));

        IngestionResult result = service.ingest(new IngestionRequest(FixtureDatasets.Source.SYNTHETIC, List.of()));

        assertEquals(FixtureDatasets.Source.SYNTHETIC, result.source());
        assertEquals("2025-11-01", result.provenance().version());
        assertEquals("sha256:synthetic-openid4vp-v1", result.provenance().sha256());
        assertEquals(1, result.presentations().size());
        PresentationSummary summary = result.presentations().get(0);
        assertEquals("pid-synthetic", summary.presentationId());
        assertEquals(List.of("aki:synthetic"), summary.trustedAuthorityPolicies());
        assertEquals("oid4vp.fixtures.ingested", telemetry.lastEvent);
        assertEquals("synthetic", telemetry.lastFields.get("source"));
        assertEquals(1, telemetry.lastFields.get("ingestedCount"));
    }

    @Test
    void conformanceIngestionFiltersPresentationIds() {
        OpenId4VpStoredPresentationFixtures fixtures = new OpenId4VpStoredPresentationFixtures(storedPresentationsDir);
        RecordingTelemetry telemetry = new RecordingTelemetry();
        OpenId4VpFixtureIngestionService service = new OpenId4VpFixtureIngestionService(
                new OpenId4VpFixtureIngestionService.Dependencies(fixtures, telemetry));

        IngestionResult result =
                service.ingest(new IngestionRequest(FixtureDatasets.Source.CONFORMANCE, List.of("pid-conformance")));

        assertEquals(FixtureDatasets.Source.CONFORMANCE, result.source());
        assertEquals("2025-10-15", result.provenance().version());
        assertEquals(1, result.presentations().size());
        PresentationSummary summary = result.presentations().get(0);
        assertEquals("pid-conformance", summary.presentationId());
        assertTrue(summary.trustedAuthorityPolicies().contains("etsi_tl:EU-TL"));
        assertEquals("oid4vp.fixtures.ingested", telemetry.lastEvent);
        assertEquals("conformance", telemetry.lastFields.get("source"));
        assertEquals(1, telemetry.lastFields.get("ingestedCount"));
        assertEquals(List.of("pid-conformance"), telemetry.lastRequestedIds);
    }

    private static void writePresentation(
            Path path, String id, String source, String credentialId, List<String> policies) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"credentialId\": \"").append(credentialId).append("\",\n");
        builder.append("  \"profile\": \"HAIP\",\n");
        builder.append("  \"format\": \"dc+sd-jwt\",\n");
        builder.append("  \"source\": \"").append(source).append("\",\n");
        builder.append("  \"trustedAuthorities\": [");
        for (int i = 0; i < policies.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append("\"").append(policies.get(i)).append("\"");
        }
        builder.append("]\n");
        builder.append("}\n");
        Files.writeString(path, builder.toString(), StandardCharsets.UTF_8);
    }

    private static final class RecordingTelemetry implements OpenId4VpFixtureIngestionService.TelemetryPublisher {
        private String lastEvent;
        private Map<String, Object> lastFields;
        private List<String> lastRequestedIds = new ArrayList<>();

        @Override
        public OpenId4VpFixtureIngestionService.TelemetrySignal fixturesIngested(
                FixtureDatasets.Source source, Map<String, Object> fields, List<String> requestedIds) {
            this.lastEvent = "oid4vp.fixtures.ingested";
            this.lastFields = Map.copyOf(fields);
            this.lastRequestedIds =
                    List.copyOf(Optional.ofNullable(requestedIds).orElse(List.of()));
            return new OpenId4VpFixtureIngestionService.TelemetrySignal() {
                @Override
                public String event() {
                    return lastEvent;
                }

                @Override
                public Map<String, Object> fields() {
                    return lastFields;
                }
            };
        }
    }
}
