package io.openauth.sim.cli.eudi.openid4vp;

import io.openauth.sim.application.eudi.openid4vp.OpenId4VpAuthorizationRequestService;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpValidationService;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService;
import java.util.LinkedHashMap;
import java.util.Map;

final class Oid4vpCliMapper {
    private Oid4vpCliMapper() {}

    static Map<String, Object> authorization(OpenId4VpAuthorizationRequestService.AuthorizationRequestResult result) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("requestId", result.requestId());
        root.put("profile", result.profile().name());
        root.put("requestUri", result.requestUri());
        root.put("authorizationRequest", mapAuthorizationRequest(result.authorizationRequest()));
        result.qr().ifPresent(qr -> root.put("qr", Map.of("ascii", qr.ascii(), "uri", qr.uri())));
        result.trace().ifPresent(trace -> root.put("trace", mapAuthorizationTrace(trace)));
        root.put("telemetry", telemetry(result.telemetry()));
        return root;
    }

    static Map<String, Object> wallet(OpenId4VpWalletSimulationService.SimulationResult result, boolean includeTrace) {
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
        if (includeTrace) {
            root.put("trace", mapWalletTrace(result.trace()));
        }
        root.put("telemetry", telemetry(result.telemetry()));
        return root;
    }

    static Map<String, Object> validation(OpenId4VpValidationService.ValidationResult result, boolean includeTrace) {
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
        if (includeTrace) {
            root.put("trace", mapValidationTrace(result.trace()));
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

    private static Map<String, Object> mapAuthorizationTrace(OpenId4VpAuthorizationRequestService.Trace trace) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", trace.requestId());
        payload.put("profile", trace.profile().name());
        payload.put("dcqlHash", trace.dcqlHash());
        payload.put("trustedAuthorities", trace.trustedAuthorities());
        payload.put("nonceFull", trace.nonce());
        payload.put("stateFull", trace.state());
        payload.put("requestUri", trace.requestUri());
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

    private static Map<String, Object> mapWalletTrace(OpenId4VpWalletSimulationService.Trace trace) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vpTokenHash", trace.vpTokenHash());
        trace.kbJwtHash().ifPresent(value -> payload.put("kbJwtHash", value));
        payload.put("disclosureHashes", trace.disclosureHashes());
        payload.put(
                "trustedAuthorityMatch",
                trace.trustedAuthorityMatch().map(match -> match.policy()).orElse(null));
        return payload;
    }

    private static Map<String, Object> mapValidationTrace(OpenId4VpValidationService.Trace trace) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vpTokenHash", trace.vpTokenHash());
        trace.kbJwtHash().ifPresent(value -> payload.put("kbJwtHash", value));
        payload.put("disclosureHashes", trace.disclosureHashes());
        payload.put(
                "trustedAuthorityMatch",
                trace.trustedAuthorityMatch().map(match -> match.policy()).orElse(null));
        trace.walletPresetId().ifPresent(id -> payload.put("walletPreset", id));
        trace.dcqlPreview().ifPresent(preview -> payload.put("dcqlPreview", preview));
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
