package io.openauth.sim.application.eudi.openid4vp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
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
        String presentationDefinition = request.dcqlOverride().orElseGet(() -> {
            if (preset != null) {
                return preset.presentationDefinition();
            }
            throw new IllegalArgumentException("DCQL preset or override required");
        });
        String clientId = preset != null ? preset.clientId() : deriveClientId(request);
        String responseMode = responseModeLabel(request.responseMode());

        SeedSequence seeds = this.dependencies.seedSequence();
        String requestId = seeds.nextRequestId();
        String nonce = seeds.nextNonce();
        String state = seeds.nextState();

        List<String> trustedAuthorities = preset == null ? List.of() : preset.trustedAuthorityPolicies();

        AuthorizationRequest authorizationRequest =
                new AuthorizationRequest(clientId, nonce, state, responseMode, presentationDefinition);

        boolean haipEnforced = request.profile() == Profile.HAIP && request.signedRequest();
        String requestUri = this.dependencies.requestUriFactory().create(requestId);
        String deepLinkUri = "openid-vp://?request_uri=" + requestUri;

        QrCode qr = null;
        if (request.includeQr()) {
            String ascii = this.dependencies.qrCodeEncoder().encode(deepLinkUri);
            qr = new QrCode(ascii, deepLinkUri);
        }

        Trace trace = request.verbose()
                ? new Trace(
                        requestId,
                        request.profile(),
                        dcqlHash(presentationDefinition),
                        trustedAuthorities,
                        nonce,
                        state,
                        requestUri)
                : null;

        Map<String, Object> telemetryFields = new LinkedHashMap<>();
        telemetryFields.put("requestId", requestId);
        telemetryFields.put("profile", request.profile().name());
        telemetryFields.put("responseMode", request.responseMode().name());
        telemetryFields.put("haipMode", haipEnforced);
        telemetryFields.put("requestUri", requestUri);
        telemetryFields.put("trustedAuthorities", trustedAuthorities);
        telemetryFields.put("nonceMasked", maskValue(nonce));
        telemetryFields.put("stateMasked", maskValue(state));
        if (request.verbose()) {
            telemetryFields.put("nonceFull", nonce);
            telemetryFields.put("stateFull", state);
        }
        if (qr != null) {
            telemetryFields.put("qrAscii", qr.ascii());
            telemetryFields.put("qrUri", qr.uri());
        }

        TelemetrySignal telemetry = this.dependencies
                .telemetryPublisher()
                .requestCreated(requestId, request.profile(), request.responseMode(), haipEnforced, telemetryFields);

        return new AuthorizationRequestResult(
                requestId,
                request.profile(),
                requestUri,
                authorizationRequest,
                Optional.ofNullable(qr),
                Optional.ofNullable(trace),
                telemetry);
    }

    private static String deriveClientId(CreateRequest request) {
        return request.profile() == Profile.HAIP ? "haip-simulator" : "baseline-simulator";
    }

    private static String responseModeLabel(ResponseMode responseMode) {
        return switch (responseMode) {
            case FRAGMENT -> "fragment";
            case DIRECT_POST -> "direct_post";
            case DIRECT_POST_JWT -> "direct_post.jwt";
        };
    }

    private static String maskValue(String value) {
        String suffix = value.length() <= 6 ? value : value.substring(value.length() - 6);
        return "******" + suffix;
    }

    private static String dcqlHash(String presentationDefinition) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(presentationDefinition.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    public record Dependencies(
            SeedSequence seedSequence,
            DcqlPresetRepository presetRepository,
            RequestUriFactory requestUriFactory,
            QrCodeEncoder qrCodeEncoder,
            TelemetryPublisher telemetryPublisher) {
        public Dependencies {
            Objects.requireNonNull(seedSequence, "seedSequence");
            Objects.requireNonNull(presetRepository, "presetRepository");
            Objects.requireNonNull(requestUriFactory, "requestUriFactory");
            Objects.requireNonNull(qrCodeEncoder, "qrCodeEncoder");
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

    public interface RequestUriFactory {
        String create(String requestId);
    }

    public interface QrCodeEncoder {
        String encode(String payload);
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

    public record QrCode(String ascii, String uri) {
        public QrCode {
            Objects.requireNonNull(ascii, "ascii");
            Objects.requireNonNull(uri, "uri");
        }
    }

    public record Trace(
            String requestId,
            Profile profile,
            String dcqlHash,
            List<String> trustedAuthorities,
            String nonce,
            String state,
            String requestUri) {
        public Trace {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(dcqlHash, "dcqlHash");
            Objects.requireNonNull(trustedAuthorities, "trustedAuthorities");
            Objects.requireNonNull(nonce, "nonce");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(requestUri, "requestUri");
            trustedAuthorities = List.copyOf(trustedAuthorities);
        }
    }

    public static enum Profile {
        HAIP,
        BASELINE;
    }

    public interface TelemetryPublisher {
        TelemetrySignal requestCreated(
                String requestId,
                Profile profile,
                ResponseMode responseMode,
                boolean haipMode,
                Map<String, Object> fields);
    }

    public static interface TelemetrySignal {
        public String event();

        public Map<String, Object> fields();
    }

    public record AuthorizationRequestResult(
            String requestId,
            Profile profile,
            String requestUri,
            AuthorizationRequest authorizationRequest,
            Optional<QrCode> qr,
            Optional<Trace> trace,
            TelemetrySignal telemetry) {
        public AuthorizationRequestResult {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(requestUri, "requestUri");
            Objects.requireNonNull(authorizationRequest, "authorizationRequest");
            qr = qr == null ? Optional.empty() : qr;
            trace = trace == null ? Optional.empty() : trace;
            Objects.requireNonNull(telemetry, "telemetry");
        }
    }
}
