package io.openauth.sim.cli.eudi.openid4vp;

import io.openauth.sim.application.eudi.openid4vp.OpenId4VpAuthorizationRequestService;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpValidationService;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService;
import io.openauth.sim.core.trace.VerboseTrace;
import java.util.LinkedHashMap;
import java.util.Map;

final class Oid4vpCliMapper {
    private Oid4vpCliMapper() {}

    static Map<String, Object> authorization(
            OpenId4VpAuthorizationRequestService.AuthorizationRequestResult result, Map<String, Object> tracePayload) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("requestId", result.requestId());
        root.put("profile", result.profile().name());
        root.put("requestUri", result.requestUri());
        root.put("authorizationRequest", mapAuthorizationRequest(result.authorizationRequest()));
        result.qr().ifPresent(qr -> root.put("qr", Map.of("ascii", qr.ascii(), "uri", qr.uri())));
        if (tracePayload != null) {
            root.put("trace", tracePayload);
        }
        root.put("telemetry", telemetry(result.telemetry()));
        return root;
    }

    static Map<String, Object> wallet(
            OpenId4VpWalletSimulationService.SimulationResult result, Map<String, Object> tracePayload) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("requestId", result.requestId());
        root.put("status", result.status().name());
        root.put("profile", result.profile().name());
        root.put("responseMode", result.responseMode().name());
        root.put(
                "presentations",
                result.presentations().stream()
                        .map(Oid4vpCliMapper::mapPresentation)
                        .toList());
        if (tracePayload != null) {
            root.put("trace", tracePayload);
        }
        root.put("telemetry", telemetry(result.telemetry()));
        return root;
    }

    static Map<String, Object> validation(
            OpenId4VpValidationService.ValidationResult result, Map<String, Object> tracePayload) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("requestId", result.requestId());
        root.put("status", result.status().name());
        root.put("profile", result.profile().name());
        root.put("responseMode", result.responseMode().name());
        root.put(
                "presentations",
                result.presentations().stream()
                        .map(Oid4vpCliMapper::mapPresentation)
                        .toList());
        if (tracePayload != null) {
            root.put("trace", tracePayload);
        }
        root.put("telemetry", telemetry(result.telemetry()));
        return root;
    }

    private static Map<String, Object> mapAuthorizationRequest(
            OpenId4VpAuthorizationRequestService.AuthorizationRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientId", request.clientId());
        payload.put("nonce", request.nonce());
        payload.put("state", request.state());
        payload.put("responseMode", request.responseMode());
        payload.put("presentationDefinition", request.presentationDefinition());
        return payload;
    }

    private static Map<String, Object> mapPresentation(OpenId4VpWalletSimulationService.Presentation presentation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("credentialId", presentation.credentialId());
        payload.put("format", presentation.format());
        payload.put("holderBinding", presentation.holderBinding());
        payload.put(
                "trustedAuthorityMatch",
                presentation
                        .trustedAuthorityMatch()
                        .map(match -> match.policy())
                        .orElse(null));
        payload.put("vpToken", mapToken(presentation.vpToken()));
        payload.put("disclosureHashes", presentation.disclosureHashes());
        return payload;
    }

    private static Map<String, Object> mapPresentation(OpenId4VpValidationService.Presentation presentation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("credentialId", presentation.credentialId());
        payload.put("format", presentation.format());
        payload.put("holderBinding", presentation.holderBinding());
        payload.put(
                "trustedAuthorityMatch",
                presentation
                        .trustedAuthorityMatch()
                        .map(match -> match.policy())
                        .orElse(null));
        payload.put("vpToken", mapToken(presentation.vpToken()));
        payload.put("disclosureHashes", presentation.disclosureHashes());
        return payload;
    }

    private static Map<String, Object> mapToken(OpenId4VpWalletSimulationService.VpToken token) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vp_token", token.vpToken());
        payload.put("presentation_submission", token.presentationSubmission());
        return payload;
    }

    private static Map<String, Object> mapToken(OpenId4VpValidationService.VpToken token) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vp_token", token.vpToken());
        payload.put("presentation_submission", token.presentationSubmission());
        return payload;
    }

    static Map<String, Object> traceToMap(VerboseTrace trace) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operation", trace.operation());
        payload.put("metadata", trace.metadata());
        payload.put(
                "steps",
                trace.steps().stream().map(Oid4vpCliMapper::mapTraceStep).toList());
        return payload;
    }

    private static Map<String, Object> mapTraceStep(VerboseTrace.TraceStep step) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", step.id());
        payload.put("summary", step.summary());
        if (step.detail() != null && !step.detail().isBlank()) {
            payload.put("detail", step.detail());
        }
        if (step.specAnchor() != null && !step.specAnchor().isBlank()) {
            payload.put("spec", step.specAnchor());
        }
        payload.put("attributes", step.attributes());
        payload.put(
                "orderedAttributes",
                step.typedAttributes().stream()
                        .map(attribute -> Map.of(
                                "type", attribute.type().label(),
                                "name", attribute.name(),
                                "value", String.valueOf(attribute.value())))
                        .toList());
        payload.put("notes", step.notes());
        return payload;
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
}
