package io.openauth.sim.rest.eudi.openid4vp;

import io.openauth.sim.application.eudi.openid4vp.Oid4vpValidationException;
import io.openauth.sim.application.eudi.openid4vp.Oid4vpVerboseTraceBuilder;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpAuthorizationRequestService;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpFixtureIngestionService;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpValidationService;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.Profile;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.ResponseMode;
import io.openauth.sim.application.eudi.openid4vp.TrustedAuthorityEvaluator;
import io.openauth.sim.application.eudi.openid4vp.fixtures.FixtureSeedSequence;
import io.openauth.sim.core.eudi.openid4vp.FixtureDatasets;
import io.openauth.sim.core.json.SimpleJson;
import io.openauth.sim.rest.VerboseTracePayload;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/eudiw/openid4vp")
class Oid4vpController {

    private final OpenId4VpAuthorizationRequestService authorizationRequestService;
    private final OpenId4VpWalletSimulationService walletSimulationService;
    private final OpenId4VpValidationService validationService;
    private final FixtureSeedSequence seedSequence;
    private final OpenId4VpFixtureIngestionService fixtureIngestionService;

    Oid4vpController(
            OpenId4VpAuthorizationRequestService authorizationRequestService,
            OpenId4VpWalletSimulationService walletSimulationService,
            OpenId4VpValidationService validationService,
            FixtureSeedSequence seedSequence,
            OpenId4VpFixtureIngestionService fixtureIngestionService) {
        this.authorizationRequestService = authorizationRequestService;
        this.walletSimulationService = walletSimulationService;
        this.validationService = validationService;
        this.seedSequence = seedSequence;
        this.fixtureIngestionService = fixtureIngestionService;
    }

    @PostMapping("/requests")
    AuthorizationResponse createRequest(
            @RequestBody AuthorizationRequestPayload payload,
            @RequestParam(name = "verbose", defaultValue = "false") boolean verbose) {
        OpenId4VpAuthorizationRequestService.CreateRequest request =
                new OpenId4VpAuthorizationRequestService.CreateRequest(
                        authorizationProfile(payload.profile()),
                        authorizationResponseMode(payload.responseMode()),
                        Optional.ofNullable(payload.dcqlPreset()),
                        Optional.ofNullable(payload.dcqlOverride()),
                        payload.resolvedSignedRequest(),
                        payload.resolvedIncludeQrAscii(),
                        verbose);
        var result = authorizationRequestService.create(request);
        return AuthorizationResponse.from(result, verbose);
    }

    @PostMapping("/wallet/simulate")
    WalletSimulationResponse simulateWallet(
            @RequestBody WalletSimulationPayload payload,
            @RequestParam(name = "verbose", defaultValue = "false") boolean verbose) {
        String requestId = payload.requestId() != null ? payload.requestId() : seedSequence.nextRequestId();
        var request = new OpenId4VpWalletSimulationService.SimulateRequest(
                requestId,
                profile(payload.profile()),
                responseMode(payload.responseMode()),
                Optional.ofNullable(payload.walletPreset()),
                Optional.ofNullable(payload.inlineSdJwt()).map(Oid4vpController::toInlineSdJwt),
                Optional.ofNullable(payload.trustedAuthorityPolicy()));
        var result = walletSimulationService.simulate(request);
        return WalletSimulationResponse.from(result, verbose);
    }

    @PostMapping("/validate")
    ResponseEntity<?> validate(
            @RequestBody ValidationPayload payload,
            @RequestParam(name = "verbose", defaultValue = "false") boolean verbose) {
        try {
            var request = new OpenId4VpValidationService.ValidateRequest(
                    Optional.ofNullable(payload.requestId()).orElseGet(seedSequence::nextRequestId),
                    profile(payload.profile()),
                    Optional.ofNullable(payload.responseMode()).map(Oid4vpController::responseMode),
                    Optional.ofNullable(payload.presetId()),
                    Optional.ofNullable(payload.inlineVpToken()).map(Oid4vpController::toInlineVpToken),
                    Optional.ofNullable(payload.trustedAuthorityPolicy()),
                    Optional.ofNullable(payload.inlineDcqlJson()));
            var result = validationService.validate(request);
            return ResponseEntity.ok(ValidationResponse.from(result, verbose));
        } catch (Oid4vpValidationException ex) {
            throw ex;
        }
    }

    @PostMapping("/presentations/seed")
    ResponseEntity<SeedResponse> seedPresentations(@RequestBody SeedRequest payload) {
        FixtureDatasets.Source source = resolveSource(payload.source());
        List<String> requested = payload.presentations() == null ? List.of() : List.copyOf(payload.presentations());
        var result = fixtureIngestionService.ingest(
                new OpenId4VpFixtureIngestionService.IngestionRequest(source, requested));
        SeedResponse response = SeedResponse.from(result, requested.size());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    private static OpenId4VpWalletSimulationService.InlineSdJwt toInlineSdJwt(
            WalletSimulationPayload.InlineSdJwtPayload payload) {
        return new OpenId4VpWalletSimulationService.InlineSdJwt(
                payload.credentialId(),
                payload.format(),
                payload.compactSdJwt(),
                payload.disclosures() == null ? List.of() : payload.disclosures(),
                Optional.ofNullable(payload.keyBindingJwt()),
                payload.trustedAuthorityPolicies() == null ? List.of() : payload.trustedAuthorityPolicies());
    }

    private static OpenId4VpValidationService.InlineVpToken toInlineVpToken(
            ValidationPayload.InlineVpTokenPayload payload) {
        return new OpenId4VpValidationService.InlineVpToken(
                payload.credentialId(),
                payload.format(),
                payload.vpToken(),
                Optional.ofNullable(payload.keyBindingJwt()),
                payload.disclosures() == null ? List.of() : payload.disclosures(),
                payload.trustedAuthorityPolicies() == null ? List.of() : payload.trustedAuthorityPolicies());
    }

    private static Profile profile(String value) {
        if (value == null || value.isBlank()) {
            return Profile.HAIP;
        }
        return Profile.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private static OpenId4VpAuthorizationRequestService.Profile authorizationProfile(String value) {
        if (value == null || value.isBlank()) {
            return OpenId4VpAuthorizationRequestService.Profile.HAIP;
        }
        return OpenId4VpAuthorizationRequestService.Profile.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private static OpenId4VpAuthorizationRequestService.ResponseMode authorizationResponseMode(String value) {
        if (value == null || value.isBlank()) {
            return OpenId4VpAuthorizationRequestService.ResponseMode.DIRECT_POST_JWT;
        }
        return OpenId4VpAuthorizationRequestService.ResponseMode.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private static ResponseMode responseMode(String value) {
        if (value == null || value.isBlank()) {
            return ResponseMode.DIRECT_POST_JWT;
        }
        return ResponseMode.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private record AuthorizationRequestPayload(
            String profile,
            String responseMode,
            String dcqlPreset,
            String dcqlOverride,
            Boolean signedRequest,
            Boolean includeQrAscii) {
        boolean resolvedSignedRequest() {
            return signedRequest == null || signedRequest;
        }

        boolean resolvedIncludeQrAscii() {
            return includeQrAscii != null && includeQrAscii;
        }
    }

    private record WalletSimulationPayload(
            String requestId,
            String walletPreset,
            String profile,
            String responseMode,
            InlineSdJwtPayload inlineSdJwt,
            String trustedAuthorityPolicy) {
        record InlineSdJwtPayload(
                String credentialId,
                String format,
                String compactSdJwt,
                List<String> disclosures,
                String keyBindingJwt,
                List<String> trustedAuthorityPolicies) {}
    }

    private record ValidationPayload(
            String requestId,
            String presetId,
            InlineVpTokenPayload inlineVpToken,
            String trustedAuthorityPolicy,
            String profile,
            String responseMode,
            String inlineDcqlJson) {
        record InlineVpTokenPayload(
                String credentialId,
                String format,
                Map<String, Object> vpToken,
                String keyBindingJwt,
                List<String> disclosures,
                List<String> trustedAuthorityPolicies) {}
    }

    private static FixtureDatasets.Source resolveSource(String value) {
        if (value == null || value.isBlank()) {
            return FixtureDatasets.Source.SYNTHETIC;
        }
        return FixtureDatasets.Source.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private record SeedRequest(String source, List<String> presentations, Map<String, Object> metadata) {}

    private record SeedResponse(
            String source,
            int requestedCount,
            int ingestedCount,
            Map<String, Object> provenance,
            List<Map<String, Object>> presentations,
            Map<String, Object> telemetry) {
        static SeedResponse from(OpenId4VpFixtureIngestionService.IngestionResult result, int requestedCount) {
            Map<String, Object> provenance = new LinkedHashMap<>();
            provenance.put("source", result.provenance().source());
            provenance.put("version", result.provenance().version());
            provenance.put("sha256", result.provenance().sha256());
            if (!result.provenance().metadata().isEmpty()) {
                provenance.putAll(result.provenance().metadata());
            }
            List<Map<String, Object>> presentations = result.presentations().stream()
                    .map(SeedResponse::mapPresentation)
                    .toList();
            Map<String, Object> telemetry = Map.of(
                    "event",
                    result.telemetry().event(),
                    "fields",
                    result.telemetry().fields());
            return new SeedResponse(
                    result.source().directoryName(),
                    requestedCount,
                    presentations.size(),
                    Map.copyOf(provenance),
                    presentations,
                    telemetry);
        }

        private static Map<String, Object> mapPresentation(
                OpenId4VpFixtureIngestionService.PresentationSummary summary) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("presentationId", summary.presentationId());
            map.put("credentialId", summary.credentialId());
            map.put("format", summary.format());
            map.put("trustedAuthorities", summary.trustedAuthorityPolicies());
            return map;
        }
    }

    private record AuthorizationResponse(
            String requestId,
            String profile,
            String requestUri,
            Map<String, Object> authorizationRequest,
            Map<String, Object> qr,
            VerboseTracePayload trace,
            Map<String, Object> telemetry) {
        static AuthorizationResponse from(
                OpenId4VpAuthorizationRequestService.AuthorizationRequestResult result, boolean verbose) {
            Map<String, Object> requestMap = new LinkedHashMap<>();
            requestMap.put("clientId", result.authorizationRequest().clientId());
            requestMap.put("nonce", result.authorizationRequest().nonce());
            requestMap.put("state", result.authorizationRequest().state());
            requestMap.put("responseMode", result.authorizationRequest().responseMode());
            requestMap.put(
                    "presentationDefinition",
                    parseJson(result.authorizationRequest().presentationDefinition()));
            Map<String, Object> qrMap = result.qr()
                    .map(qr -> {
                        Map<String, Object> qrPayload = new LinkedHashMap<>();
                        qrPayload.put("ascii", qr.ascii());
                        qrPayload.put("uri", qr.uri());
                        return qrPayload;
                    })
                    .orElse(null);
            VerboseTracePayload tracePayload = verbose
                    ? Oid4vpVerboseTraceBuilder.authorization(result)
                            .map(VerboseTracePayload::from)
                            .orElse(null)
                    : null;
            return new AuthorizationResponse(
                    result.requestId(),
                    result.profile().name(),
                    result.requestUri(),
                    requestMap,
                    qrMap,
                    tracePayload,
                    Oid4vpController.telemetry(result.telemetry()));
        }
    }

    private record WalletSimulationResponse(
            String requestId,
            String status,
            String profile,
            String responseMode,
            List<Map<String, Object>> presentations,
            VerboseTracePayload trace,
            Map<String, Object> telemetry) {
        static WalletSimulationResponse from(
                OpenId4VpWalletSimulationService.SimulationResult result, boolean verbose) {
            List<Map<String, Object>> presentations = result.presentations().stream()
                    .map(Oid4vpController::mapPresentation)
                    .toList();
            VerboseTracePayload tracePayload =
                    verbose ? VerboseTracePayload.from(Oid4vpVerboseTraceBuilder.wallet(result)) : null;
            return new WalletSimulationResponse(
                    result.requestId(),
                    result.status().name(),
                    result.profile().name(),
                    result.responseMode().name(),
                    presentations,
                    tracePayload,
                    Oid4vpController.telemetry(result.telemetry()));
        }
    }

    private record ValidationResponse(
            String requestId,
            String status,
            String profile,
            String responseMode,
            List<Map<String, Object>> presentations,
            VerboseTracePayload trace,
            Map<String, Object> telemetry) {
        static ValidationResponse from(OpenId4VpValidationService.ValidationResult result, boolean verbose) {
            List<Map<String, Object>> presentations = result.presentations().stream()
                    .map(Oid4vpController::mapPresentation)
                    .toList();
            VerboseTracePayload tracePayload =
                    verbose ? VerboseTracePayload.from(Oid4vpVerboseTraceBuilder.validation(result)) : null;
            return new ValidationResponse(
                    result.requestId(),
                    result.status().name(),
                    result.profile().name(),
                    result.responseMode().name(),
                    presentations,
                    tracePayload,
                    Oid4vpController.telemetry(result.telemetry()));
        }
    }

    private static Map<String, Object> mapPresentation(OpenId4VpWalletSimulationService.Presentation presentation) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("credentialId", presentation.credentialId());
        map.put("format", presentation.format());
        map.put("holderBinding", presentation.holderBinding());
        map.put(
                "trustedAuthorityMatch",
                presentation
                        .trustedAuthorityMatch()
                        .map(TrustedAuthorityEvaluator.TrustedAuthorityVerdict::policy)
                        .orElse(null));
        map.put("vpToken", presentation.vpToken());
        map.put("disclosureHashes", presentation.disclosureHashes());
        return map;
    }

    private static Map<String, Object> mapPresentation(OpenId4VpValidationService.Presentation presentation) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("credentialId", presentation.credentialId());
        map.put("format", presentation.format());
        map.put("holderBinding", presentation.holderBinding());
        map.put(
                "trustedAuthorityMatch",
                presentation
                        .trustedAuthorityMatch()
                        .map(TrustedAuthorityEvaluator.TrustedAuthorityVerdict::policy)
                        .orElse(null));
        map.put("vpToken", presentation.vpToken());
        map.put("disclosureHashes", presentation.disclosureHashes());
        return map;
    }

    private static Map<String, Object> telemetry(OpenId4VpAuthorizationRequestService.TelemetrySignal telemetry) {
        return Map.of("event", telemetry.event(), "fields", telemetry.fields());
    }

    private static Map<String, Object> telemetry(OpenId4VpWalletSimulationService.TelemetrySignal telemetry) {
        return Map.of("event", telemetry.event(), "fields", telemetry.fields());
    }

    private static Map<String, Object> telemetry(OpenId4VpValidationService.TelemetrySignal telemetry) {
        return Map.of("event", telemetry.event(), "fields", telemetry.fields());
    }

    private static Object parseJson(String json) {
        try {
            Object parsed = SimpleJson.parse(json);
            return parsed;
        } catch (Exception ex) {
            return json;
        }
    }
}
