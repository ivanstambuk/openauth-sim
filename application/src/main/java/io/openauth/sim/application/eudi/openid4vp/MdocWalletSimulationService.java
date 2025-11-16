package io.openauth.sim.application.eudi.openid4vp;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Native Java helper for ISO/IEC 18013-5 (mdoc) presentations used by the EUDIW wallet simulator.
 *
 * <p>The service hydrates inline payloads or stored presets, evaluates Trusted Authorities
 * policies, and assembles presentation diagnostics shared by the wallet simulation facade.
 */
public final class MdocWalletSimulationService {

    private final Dependencies dependencies;

    public MdocWalletSimulationService(Dependencies dependencies) {
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
    }

    public SimulationResult simulate(SimulateRequest request) {
        Objects.requireNonNull(request, "request");

        boolean encryptionRequired =
                request.profile() == Profile.HAIP && request.responseMode() == ResponseMode.DIRECT_POST_JWT;
        if (encryptionRequired) {
            this.dependencies
                    .haipEncryptionHook()
                    .ensureEncryption(request.profile(), request.responseMode(), request.requestId());
        }

        ResolvedMdocInput input = resolveInput(request);
        TrustedAuthorityEvaluator.Decision trustedAuthorityDecision = this.dependencies
                .trustedAuthorityEvaluator()
                .evaluate(request.trustedAuthorityPolicy(), input.trustedAuthorityPolicies());
        if (trustedAuthorityDecision.problemDetails().isPresent()) {
            throw new Oid4vpValidationException(
                    trustedAuthorityDecision.problemDetails().orElseThrow());
        }
        Optional<TrustedAuthorityEvaluator.TrustedAuthorityVerdict> trustedAuthorityMatch =
                trustedAuthorityDecision.trustedAuthorityMatch();

        Map<String, Boolean> claimsSatisfied = evaluateClaims(input.claimsPathPointers());
        String deviceResponseHash = sha256Hex(decodeBase64(input.deviceResponseBase64()));

        VpToken vpToken = new VpToken(input.deviceResponseBase64(), Map.of("descriptor_id", input.credentialId()));
        Presentation presentation =
                new Presentation(input.credentialId(), input.docType(), false, trustedAuthorityMatch, vpToken);

        PresentationDiagnostics diagnostics = new PresentationDiagnostics(
                input.credentialId(),
                trustedAuthorityMatch,
                claimsSatisfied,
                deviceResponseHash,
                encryptionRequired ? Optional.of(Boolean.TRUE) : Optional.empty());
        Trace trace = new Trace(List.of(diagnostics), Optional.empty());

        TelemetrySignal telemetry = this.dependencies
                .telemetryPublisher()
                .walletResponded(
                        request.requestId(),
                        request.profile(),
                        1,
                        telemetryFields(trustedAuthorityMatch, request.trustedAuthorityPolicy()));

        return new SimulationResult(
                request.requestId(),
                Status.SUCCESS,
                request.profile(),
                request.responseMode(),
                List.of(presentation),
                trace,
                telemetry);
    }

    private ResolvedMdocInput resolveInput(SimulateRequest request) {
        if (request.inlineMdoc().isPresent()) {
            InlineMdoc inline = request.inlineMdoc().get();
            return new ResolvedMdocInput(
                    inline.credentialId(),
                    inline.docType(),
                    inline.deviceResponseBase64(),
                    inline.claimsPathPointers(),
                    inline.trustedAuthorityPolicies());
        }

        String presetId = request.walletPresetId()
                .orElseThrow(() -> new IllegalArgumentException("walletPresetId required when inlineMdoc absent"));
        DeviceResponsePreset preset = this.dependencies.presetRepository().load(presetId);
        return new ResolvedMdocInput(
                preset.credentialId(),
                preset.docType(),
                preset.deviceResponseBase64(),
                preset.claimsPathPointers(),
                preset.trustedAuthorityPolicies());
    }

    private static Map<String, Boolean> evaluateClaims(Map<String, String> claims) {
        Map<String, Boolean> evaluation = new LinkedHashMap<>();
        claims.forEach((pointer, value) -> {
            if (pointer != null && pointer.endsWith(".doctype")) {
                return;
            }
            evaluation.put(pointer, value != null && !value.isBlank());
        });
        return Map.copyOf(evaluation);
    }

    private static byte[] decodeBase64(String value) {
        return Base64.getDecoder().decode(value);
    }

    private static String sha256Hex(byte[] payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static Map<String, Object> telemetryFields(
            Optional<TrustedAuthorityEvaluator.TrustedAuthorityVerdict> match, Optional<String> requestedPolicy) {
        Map<String, Object> fields = new LinkedHashMap<>();
        requestedPolicy.ifPresent(policy -> fields.put("trustedAuthorityRequested", policy));
        match.ifPresent(verdict -> fields.put(
                "trustedAuthority",
                Map.of(
                        "type", verdict.type(),
                        "value", verdict.value(),
                        "label", verdict.label(),
                        "policy", verdict.policy())));
        fields.put("presentations", 1);
        return fields;
    }

    private record ResolvedMdocInput(
            String credentialId,
            String docType,
            String deviceResponseBase64,
            Map<String, String> claimsPathPointers,
            List<String> trustedAuthorityPolicies) {
        // Marker record for resolved preset/inline inputs.
    }

    public record Dependencies(
            DeviceResponsePresetRepository presetRepository,
            TelemetryPublisher telemetryPublisher,
            HaipEncryptionHook haipEncryptionHook,
            TrustedAuthorityEvaluator trustedAuthorityEvaluator) {
        public Dependencies {
            Objects.requireNonNull(presetRepository, "presetRepository");
            Objects.requireNonNull(telemetryPublisher, "telemetryPublisher");
            Objects.requireNonNull(haipEncryptionHook, "haipEncryptionHook");
            Objects.requireNonNull(trustedAuthorityEvaluator, "trustedAuthorityEvaluator");
        }
    }

    public interface DeviceResponsePresetRepository {
        DeviceResponsePreset load(String presetId);
    }

    public record DeviceResponsePreset(
            String presetId,
            String credentialId,
            String docType,
            String deviceResponseBase64,
            Map<String, String> claimsPathPointers,
            List<String> trustedAuthorityPolicies) {
        public DeviceResponsePreset {
            Objects.requireNonNull(presetId, "presetId");
            Objects.requireNonNull(credentialId, "credentialId");
            Objects.requireNonNull(docType, "docType");
            Objects.requireNonNull(deviceResponseBase64, "deviceResponseBase64");
            claimsPathPointers = claimsPathPointers == null ? Map.of() : Map.copyOf(claimsPathPointers);
            trustedAuthorityPolicies =
                    trustedAuthorityPolicies == null ? List.of() : List.copyOf(trustedAuthorityPolicies);
        }
    }

    public record InlineMdoc(
            String credentialId,
            String docType,
            String deviceResponseBase64,
            Map<String, String> claimsPathPointers,
            List<String> trustedAuthorityPolicies) {
        public InlineMdoc {
            Objects.requireNonNull(credentialId, "credentialId");
            Objects.requireNonNull(docType, "docType");
            Objects.requireNonNull(deviceResponseBase64, "deviceResponseBase64");
            claimsPathPointers = claimsPathPointers == null ? Map.of() : Map.copyOf(claimsPathPointers);
            trustedAuthorityPolicies =
                    trustedAuthorityPolicies == null ? List.of() : List.copyOf(trustedAuthorityPolicies);
        }
    }

    public interface TelemetryPublisher {
        TelemetrySignal walletResponded(
                String requestId, Profile profile, int presentations, Map<String, Object> fields);
    }

    public interface TelemetrySignal {
        String event();

        Map<String, Object> fields();
    }

    public interface HaipEncryptionHook {
        void ensureEncryption(Profile profile, ResponseMode responseMode, String requestId);
    }

    public record SimulateRequest(
            String requestId,
            Profile profile,
            ResponseMode responseMode,
            Optional<String> walletPresetId,
            Optional<InlineMdoc> inlineMdoc,
            Optional<String> trustedAuthorityPolicy) {
        public SimulateRequest {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(responseMode, "responseMode");
            walletPresetId = walletPresetId == null ? Optional.empty() : walletPresetId;
            inlineMdoc = inlineMdoc == null ? Optional.empty() : inlineMdoc;
            trustedAuthorityPolicy = trustedAuthorityPolicy == null ? Optional.empty() : trustedAuthorityPolicy;
        }
    }

    public record SimulationResult(
            String requestId,
            Status status,
            Profile profile,
            ResponseMode responseMode,
            List<Presentation> presentations,
            Trace trace,
            TelemetrySignal telemetry) {
        public SimulationResult {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(responseMode, "responseMode");
            presentations = presentations == null ? List.of() : List.copyOf(presentations);
            Objects.requireNonNull(trace, "trace");
            Objects.requireNonNull(telemetry, "telemetry");
        }
    }

    public record Presentation(
            String credentialId,
            String format,
            boolean holderBinding,
            Optional<TrustedAuthorityEvaluator.TrustedAuthorityVerdict> trustedAuthorityMatch,
            VpToken vpToken) {
        public Presentation {
            Objects.requireNonNull(credentialId, "credentialId");
            Objects.requireNonNull(format, "format");
            trustedAuthorityMatch = trustedAuthorityMatch == null ? Optional.empty() : trustedAuthorityMatch;
            Objects.requireNonNull(vpToken, "vpToken");
        }
    }

    public record VpToken(String vpToken, Map<String, Object> presentationSubmission) {
        public VpToken {
            Objects.requireNonNull(vpToken, "vpToken");
            presentationSubmission = presentationSubmission == null ? Map.of() : Map.copyOf(presentationSubmission);
        }
    }

    public record Trace(List<PresentationDiagnostics> presentations, Optional<Long> latencyMs) {
        public Trace {
            presentations = presentations == null ? List.of() : List.copyOf(presentations);
            latencyMs = latencyMs == null ? Optional.empty() : latencyMs;
        }
    }

    public record PresentationDiagnostics(
            String credentialId,
            Optional<TrustedAuthorityEvaluator.TrustedAuthorityVerdict> trustedAuthorityMatch,
            Map<String, Boolean> claimsSatisfied,
            String deviceResponseHash,
            Optional<Boolean> encryptionApplied) {
        public PresentationDiagnostics {
            Objects.requireNonNull(credentialId, "credentialId");
            trustedAuthorityMatch = trustedAuthorityMatch == null ? Optional.empty() : trustedAuthorityMatch;
            claimsSatisfied = claimsSatisfied == null ? Map.of() : Map.copyOf(claimsSatisfied);
            Objects.requireNonNull(deviceResponseHash, "deviceResponseHash");
            encryptionApplied = encryptionApplied == null ? Optional.empty() : encryptionApplied;
        }
    }

    public enum Status {
        SUCCESS,
        FAILED;
    }

    public enum Profile {
        HAIP,
        BASELINE;
    }

    public enum ResponseMode {
        FRAGMENT,
        DIRECT_POST,
        DIRECT_POST_JWT;
    }
}
