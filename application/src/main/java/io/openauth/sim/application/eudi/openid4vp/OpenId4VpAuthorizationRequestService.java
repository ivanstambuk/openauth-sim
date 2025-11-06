package io.openauth.sim.application.eudi.openid4vp;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class OpenId4VpAuthorizationRequestService {
    private final Dependencies dependencies;

    public OpenId4VpAuthorizationRequestService(Dependencies dependencies) {
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
    }

    public AuthorizationRequestResult create(CreateRequest request) {
        Objects.requireNonNull(request, "request");
        DcqlPreset preset = request.dcqlPreset()
                .map(this.dependencies.presetRepository()::load)
                .orElse(null);
        if (request.signedRequest() && preset == null) {
            throw new IllegalArgumentException("Signed requests require a DCQL preset with client metadata");
        }
        String presentationDefinition = request.dcqlOverride().orElseGet(() -> {
            if (preset != null) {
                return preset.presentationDefinition();
            }
            throw new IllegalArgumentException("DCQL preset or override required");
        });
        String clientId =
                preset != null ? preset.clientId() : OpenId4VpAuthorizationRequestService.deriveClientId(request);
        String responseMode =
                switch (request.responseMode()) {
                    case FRAGMENT -> "fragment";
                    case DIRECT_POST -> "direct_post";
                    case DIRECT_POST_JWT -> "direct_post.jwt";
                };
        String requestId = this.dependencies.seedSequence().nextRequestId();
        String nonce = this.dependencies.seedSequence().nextNonce();
        String state = this.dependencies.seedSequence().nextState();
        AuthorizationRequest authorizationRequest =
                new AuthorizationRequest(clientId, nonce, state, responseMode, presentationDefinition);
        boolean haipEnforced = request.profile() == Profile.HAIP && request.signedRequest();
        TelemetrySignal telemetry = this.dependencies
                .telemetryPublisher()
                .requestCreated(requestId, request.profile(), request.responseMode(), haipEnforced);
        return new AuthorizationRequestResult(requestId, authorizationRequest, telemetry);
    }

    private static String deriveClientId(CreateRequest request) {
        String prefix = request.profile() == Profile.HAIP ? "haip-simulator" : "baseline-simulator";
        return prefix;
    }

    public record Dependencies(
            SeedSequence seedSequence, DcqlPresetRepository presetRepository, TelemetryPublisher telemetryPublisher) {
        public Dependencies {
            Objects.requireNonNull(seedSequence, "seedSequence");
            Objects.requireNonNull(presetRepository, "presetRepository");
            Objects.requireNonNull(telemetryPublisher, "telemetryPublisher");
        }
    }

    public record CreateRequest(
            Profile profile,
            ResponseMode responseMode,
            Optional<String> dcqlPreset,
            Optional<String> dcqlOverride,
            boolean signedRequest,
            boolean includeQr,
            boolean verbose) {
        public CreateRequest {
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(responseMode, "responseMode");
            dcqlPreset = dcqlPreset == null ? Optional.empty() : dcqlPreset;
            dcqlOverride = dcqlOverride == null ? Optional.empty() : dcqlOverride;
        }
    }

    public static interface DcqlPresetRepository {
        public DcqlPreset load(String var1);
    }

    public record DcqlPreset(
            String presetId, String clientId, String presentationDefinition, List<String> trustedAuthorityPolicies) {
        public DcqlPreset {
            Objects.requireNonNull(presetId, "presetId");
            Objects.requireNonNull(clientId, "clientId");
            Objects.requireNonNull(presentationDefinition, "presentationDefinition");
            trustedAuthorityPolicies =
                    trustedAuthorityPolicies == null ? List.of() : List.copyOf(trustedAuthorityPolicies);
        }
    }

    public static enum ResponseMode {
        FRAGMENT,
        DIRECT_POST,
        DIRECT_POST_JWT;
    }

    public static interface SeedSequence {
        public String nextRequestId();

        public String nextNonce();

        public String nextState();
    }

    public record AuthorizationRequest(
            String clientId, String nonce, String state, String responseMode, String presentationDefinition) {
        public AuthorizationRequest {
            Objects.requireNonNull(clientId, "clientId");
            Objects.requireNonNull(nonce, "nonce");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(responseMode, "responseMode");
            Objects.requireNonNull(presentationDefinition, "presentationDefinition");
        }
    }

    public static enum Profile {
        HAIP,
        BASELINE;
    }

    public static interface TelemetryPublisher {
        public TelemetrySignal requestCreated(String var1, Profile var2, ResponseMode var3, boolean var4);
    }

    public static interface TelemetrySignal {
        public String event();

        public Map<String, Object> fields();
    }

    public record AuthorizationRequestResult(
            String requestId, AuthorizationRequest authorizationRequest, TelemetrySignal telemetry) {
        public AuthorizationRequestResult {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(authorizationRequest, "authorizationRequest");
            Objects.requireNonNull(telemetry, "telemetry");
        }
    }
}
