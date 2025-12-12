package io.openauth.sim.rest.eudi.openid4vp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openauth.sim.application.contract.CanonicalFacadeResult;
import io.openauth.sim.application.contract.CanonicalScenario;
import io.openauth.sim.application.contract.ScenarioEnvironment;
import io.openauth.sim.application.contract.eudi.openid4vp.OpenId4VpCanonicalScenarios;
import io.openauth.sim.application.eudi.openid4vp.Oid4vpValidationException;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpAuthorizationRequestService;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpValidationService;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService;
import io.openauth.sim.application.eudi.openid4vp.TrustedAuthorityEvaluator;
import io.openauth.sim.application.eudi.openid4vp.fixtures.FixtureDcqlPresetRepository;
import io.openauth.sim.application.eudi.openid4vp.fixtures.FixtureQrCodeEncoder;
import io.openauth.sim.application.eudi.openid4vp.fixtures.FixtureRequestUriFactory;
import io.openauth.sim.application.eudi.openid4vp.fixtures.FixtureSeedSequence;
import io.openauth.sim.application.eudi.openid4vp.fixtures.FixtureStoredPresentationRepository;
import io.openauth.sim.application.eudi.openid4vp.fixtures.FixtureWalletPresetRepository;
import io.openauth.sim.application.eudi.openid4vp.fixtures.Oid4vpTelemetryPublisher;
import io.openauth.sim.cli.eudi.openid4vp.EudiwCli;
import io.openauth.sim.core.eudi.openid4vp.TrustedAuthorityFixtures;
import io.openauth.sim.core.json.SimpleJson;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

@Tag("crossFacadeContract")
final class Oid4vpCrossFacadeContractTest {

    @Test
    void oid4vpCanonicalScenariosStayInParityAcrossFacades() {
        List<CanonicalScenario> descriptors =
                OpenId4VpCanonicalScenarios.scenarios(ScenarioEnvironment.fixedAt(Instant.EPOCH));

        for (CanonicalScenario descriptor : descriptors) {
            CanonicalFacadeResult expected = descriptor.expected();

            CanonicalFacadeResult nativeResult = executeNative(descriptor);
            assertEquals(expected, nativeResult, descriptor.scenarioId() + " native");

            CanonicalFacadeResult restResult = executeRest(descriptor);
            assertEquals(expected, restResult, descriptor.scenarioId() + " rest");

            CanonicalFacadeResult cliResult = executeCli(descriptor);
            assertEquals(expected, cliResult, descriptor.scenarioId() + " cli");
        }
    }

    private static CanonicalFacadeResult executeNative(CanonicalScenario scenario) {
        Harness harness = Harness.create();
        try {
            return harness.execute(scenario);
        } finally {
            harness.close();
        }
    }

    private static CanonicalFacadeResult executeRest(CanonicalScenario scenario) {
        Harness harness = Harness.create();
        try {
            return harness.execute(scenario);
        } finally {
            harness.close();
        }
    }

    private static CanonicalFacadeResult executeCli(CanonicalScenario scenario) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        CommandLine cmd = new CommandLine(new EudiwCli());
        cmd.setOut(new PrintWriter(stdout, true, StandardCharsets.UTF_8));
        cmd.setErr(new PrintWriter(stderr, true, StandardCharsets.UTF_8));

        String[] args =
                switch (scenario.kind()) {
                    case EVALUATE_INLINE -> {
                        OpenId4VpAuthorizationRequestService.CreateRequest request =
                                (OpenId4VpAuthorizationRequestService.CreateRequest) scenario.command();
                        java.util.List<String> parts = new java.util.ArrayList<>(List.of(
                                "request",
                                "create",
                                "--profile",
                                request.profile().name(),
                                "--response-mode",
                                request.responseMode().name(),
                                "--dcql-preset",
                                request.dcqlPreset().orElseThrow(),
                                "--include-qr",
                                "--output-json"));
                        if (request.signedRequest()) {
                            parts.add("--signed-request");
                        } else {
                            parts.add("--signed-request=false");
                        }
                        yield parts.toArray(String[]::new);
                    }
                    case EVALUATE_STORED -> {
                        OpenId4VpWalletSimulationService.SimulateRequest request =
                                (OpenId4VpWalletSimulationService.SimulateRequest) scenario.command();
                        yield new String[] {
                            "wallet",
                            "simulate",
                            "--request-id",
                            request.requestId(),
                            "--wallet-preset",
                            request.walletPresetId().orElseThrow(),
                            "--profile",
                            request.profile().name(),
                            "--response-mode",
                            request.responseMode().name(),
                            "--trusted-authority",
                            request.trustedAuthorityPolicy().orElseThrow(),
                            "--output-json"
                        };
                    }
                    case REPLAY_STORED, FAILURE_STORED -> {
                        OpenId4VpValidationService.ValidateRequest request =
                                (OpenId4VpValidationService.ValidateRequest) scenario.command();
                        yield new String[] {
                            "validate",
                            "--request-id",
                            request.requestId(),
                            "--preset",
                            request.storedPresentationId().orElseThrow(),
                            "--profile",
                            request.profile().name(),
                            "--response-mode",
                            request.responseModeOverride()
                                    .orElse(OpenId4VpWalletSimulationService.ResponseMode.DIRECT_POST_JWT)
                                    .name(),
                            "--trusted-authority",
                            request.trustedAuthorityPolicy().orElseThrow(),
                            "--output-json"
                        };
                    }
                    default -> throw new IllegalStateException("Unsupported OID4VP scenario kind " + scenario.kind());
                };

        cmd.execute(args);
        Map<String, Object> envelope = castMap(SimpleJson.parse(stdout.toString(StandardCharsets.UTF_8)));
        return toCanonicalCli(envelope);
    }

    private static CanonicalFacadeResult toCanonicalCli(Map<String, Object> envelope) {
        String status = String.valueOf(envelope.get("status"));
        String reasonRaw = String.valueOf(envelope.get("reasonCode"));
        String reasonCode = canonicalReasonCode(reasonRaw);
        boolean success = "success".equals(status);
        boolean telemetryPresent = envelope.get("telemetryId") != null;
        Map<String, Object> data = castMap(envelope.get("data"));
        boolean includeTrace = data != null && data.get("trace") != null;
        return new CanonicalFacadeResult(
                success, reasonCode, null, null, null, null, null, includeTrace, telemetryPresent);
    }

    private static String canonicalReasonCode(String raw) {
        if (raw == null) {
            return "unknown";
        }
        String text = raw.toLowerCase();
        if (text.contains("invalid_scope")) {
            return "invalid_scope";
        }
        if (text.contains("invalid_request")) {
            return "invalid_request";
        }
        if (text.contains("success")) {
            return "success";
        }
        return raw;
    }

    private static final class Harness implements AutoCloseable {
        private final FixtureSeedSequence seedSequence = new FixtureSeedSequence();
        private final FixtureDcqlPresetRepository dcqlPresetRepository = new FixtureDcqlPresetRepository();
        private final FixtureWalletPresetRepository walletPresetRepository = new FixtureWalletPresetRepository();
        private final FixtureStoredPresentationRepository storedPresentationRepository =
                new FixtureStoredPresentationRepository();
        private final FixtureRequestUriFactory requestUriFactory = new FixtureRequestUriFactory();
        private final FixtureQrCodeEncoder qrCodeEncoder = new FixtureQrCodeEncoder();
        private final Oid4vpTelemetryPublisher telemetryPublisher = new Oid4vpTelemetryPublisher();
        private final TrustedAuthorityEvaluator trustedAuthorityEvaluator =
                TrustedAuthorityEvaluator.fromSnapshot(TrustedAuthorityFixtures.loadSnapshot("haip-baseline"));

        private final OpenId4VpAuthorizationRequestService authorizationService =
                new OpenId4VpAuthorizationRequestService(new OpenId4VpAuthorizationRequestService.Dependencies(
                        seedSequence,
                        dcqlPresetRepository,
                        requestUriFactory,
                        qrCodeEncoder,
                        telemetryPublisher,
                        trustedAuthorityEvaluator));
        private final OpenId4VpWalletSimulationService walletService =
                new OpenId4VpWalletSimulationService(new OpenId4VpWalletSimulationService.Dependencies(
                        walletPresetRepository, telemetryPublisher, trustedAuthorityEvaluator));
        private final OpenId4VpValidationService validationService =
                new OpenId4VpValidationService(new OpenId4VpValidationService.Dependencies(
                        storedPresentationRepository, trustedAuthorityEvaluator, telemetryPublisher));

        static Harness create() {
            return new Harness();
        }

        CanonicalFacadeResult execute(CanonicalScenario scenario) {
            return switch (scenario.kind()) {
                case EVALUATE_INLINE ->
                    executeCreateRequest((OpenId4VpAuthorizationRequestService.CreateRequest) scenario.command());
                case EVALUATE_STORED ->
                    executeWalletSimulate((OpenId4VpWalletSimulationService.SimulateRequest) scenario.command());
                case REPLAY_STORED ->
                    executeValidateSuccess((OpenId4VpValidationService.ValidateRequest) scenario.command());
                case FAILURE_STORED ->
                    executeValidateFailure((OpenId4VpValidationService.ValidateRequest) scenario.command());
                default -> throw new IllegalStateException("Unsupported OID4VP scenario kind " + scenario.kind());
            };
        }

        private CanonicalFacadeResult executeCreateRequest(OpenId4VpAuthorizationRequestService.CreateRequest request) {
            var result = authorizationService.create(request);
            boolean includeTrace = result.trace().isPresent();
            return new CanonicalFacadeResult(true, "success", null, null, null, null, null, includeTrace, true);
        }

        private CanonicalFacadeResult executeWalletSimulate(OpenId4VpWalletSimulationService.SimulateRequest request) {
            var result = walletService.simulate(request);
            boolean success = result.status() == OpenId4VpWalletSimulationService.Status.SUCCESS;
            return new CanonicalFacadeResult(success, "success", null, null, null, null, null, false, true);
        }

        private CanonicalFacadeResult executeValidateSuccess(OpenId4VpValidationService.ValidateRequest request) {
            var result = validationService.validate(request);
            boolean success = result.status() == OpenId4VpValidationService.Status.SUCCESS;
            return new CanonicalFacadeResult(success, "success", null, null, null, null, null, false, true);
        }

        private CanonicalFacadeResult executeValidateFailure(OpenId4VpValidationService.ValidateRequest request) {
            try {
                validationService.validate(request);
                return new CanonicalFacadeResult(true, "success", null, null, null, null, null, false, true);
            } catch (Oid4vpValidationException ex) {
                String reason = canonicalReasonCode(ex.problemDetails().type());
                return new CanonicalFacadeResult(false, reason, null, null, null, null, null, false, true);
            }
        }

        @Override
        public void close() {
            // no-op
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        return (Map<String, Object>) value;
    }
}
