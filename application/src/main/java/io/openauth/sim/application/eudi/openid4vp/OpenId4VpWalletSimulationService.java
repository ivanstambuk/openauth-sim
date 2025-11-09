package io.openauth.sim.application.eudi.openid4vp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class OpenId4VpWalletSimulationService {
    private final Dependencies dependencies;

    public OpenId4VpWalletSimulationService(Dependencies dependencies) {
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
    }

    public SimulationResult simulate(SimulateRequest request) {
        Objects.requireNonNull(request, "request");
        WalletPreset preset = request.walletPresetId()
                .map(this.dependencies.walletPresetRepository()::load)
                .orElse(null);
        InlineSdJwt inlineSdJwt = request.inlineSdJwt().orElse(null);
        if (preset == null && inlineSdJwt == null) {
            throw new IllegalArgumentException("walletPresetId or inlineSdJwt required for SD-JWT simulations");
        }

        String credentialId = preset != null
                ? preset.credentialId()
                : requireInlineMetadata(inlineSdJwt.credentialId(), "inlineSdJwt.credentialId");
        String format =
                preset != null ? preset.format() : requireInlineMetadata(inlineSdJwt.format(), "inlineSdJwt.format");

        List<String> trustedAuthorityPolicies =
                preset != null ? preset.trustedAuthorityPolicies() : inlineSdJwt.trustedAuthorityPolicies();

        String compactSdJwt = inlineSdJwt != null ? inlineSdJwt.compactSdJwt() : preset.compactSdJwt();
        List<String> disclosures = inlineSdJwt != null ? inlineSdJwt.disclosures() : preset.disclosures();
        Optional<String> keyBindingJwt = inlineSdJwt != null ? inlineSdJwt.keyBindingJwt() : preset.keyBindingJwt();

        List<String> disclosureHashes = OpenId4VpWalletSimulationService.computeDisclosureHashes(disclosures);
        TrustedAuthorityEvaluator.Decision trustedAuthorityDecision = this.dependencies
                .trustedAuthorityEvaluator()
                .evaluate(request.trustedAuthorityPolicy(), trustedAuthorityPolicies);
        if (trustedAuthorityDecision.problemDetails().isPresent()) {
            throw new Oid4vpValidationException(
                    trustedAuthorityDecision.problemDetails().orElseThrow());
        }
        Optional<TrustedAuthorityEvaluator.TrustedAuthorityVerdict> trustedAuthorityMatch =
                trustedAuthorityDecision.trustedAuthorityMatch();
        VpToken vpToken = new VpToken(compactSdJwt, Map.of("presentation_definition_id", credentialId));
        boolean holderBinding = keyBindingJwt.isPresent();
        Presentation presentation =
                new Presentation(credentialId, format, holderBinding, trustedAuthorityMatch, vpToken, disclosureHashes);
        String vpTokenHash = OpenId4VpWalletSimulationService.sha256Hex(compactSdJwt);
        Optional<String> kbJwtHash = keyBindingJwt.map(OpenId4VpWalletSimulationService::sha256Hex);
        PresentationTrace presentationTrace = new PresentationTrace(
                credentialId,
                credentialId,
                format,
                holderBinding,
                disclosureHashes,
                vpTokenHash,
                kbJwtHash,
                Optional.empty(),
                trustedAuthorityMatch);
        Trace trace =
                new Trace(vpTokenHash, kbJwtHash, disclosureHashes, trustedAuthorityMatch, List.of(presentationTrace));
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

    private static List<String> computeDisclosureHashes(List<String> disclosures) {
        List<String> resolvedDisclosures = disclosures == null ? List.of() : List.copyOf(disclosures);
        List<String> computed = new ArrayList<>(resolvedDisclosures.size());
        for (String disclosure : resolvedDisclosures) {
            computed.add("sha-256:" + OpenId4VpWalletSimulationService.sha256Hex(disclosure));
        }
        return List.copyOf(computed);
    }

    private static String requireInlineMetadata(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " required when walletPresetId is not provided");
        }
        return value;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return OpenId4VpWalletSimulationService.toHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
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

    public record Dependencies(
            WalletPresetRepository walletPresetRepository,
            TelemetryPublisher telemetryPublisher,
            TrustedAuthorityEvaluator trustedAuthorityEvaluator) {
        public Dependencies {
            Objects.requireNonNull(walletPresetRepository, "walletPresetRepository");
            Objects.requireNonNull(telemetryPublisher, "telemetryPublisher");
            Objects.requireNonNull(trustedAuthorityEvaluator, "trustedAuthorityEvaluator");
        }
    }

    public record SimulateRequest(
            String requestId,
            Profile profile,
            ResponseMode responseMode,
            Optional<String> walletPresetId,
            Optional<InlineSdJwt> inlineSdJwt,
            Optional<String> trustedAuthorityPolicy) {
        public SimulateRequest {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(responseMode, "responseMode");
            walletPresetId = walletPresetId == null ? Optional.empty() : walletPresetId;
            inlineSdJwt = inlineSdJwt == null ? Optional.empty() : inlineSdJwt;
            trustedAuthorityPolicy = trustedAuthorityPolicy == null ? Optional.empty() : trustedAuthorityPolicy;
        }
    }

    public static interface WalletPresetRepository {
        public WalletPreset load(String var1);
    }

    public record WalletPreset(
            String presetId,
            String credentialId,
            String format,
            String compactSdJwt,
            List<String> disclosures,
            List<String> disclosureHashes,
            Optional<String> keyBindingJwt,
            List<String> trustedAuthorityPolicies) {
        public WalletPreset {
            Objects.requireNonNull(presetId, "presetId");
            Objects.requireNonNull(credentialId, "credentialId");
            Objects.requireNonNull(format, "format");
            Objects.requireNonNull(compactSdJwt, "compactSdJwt");
            disclosures = disclosures == null ? List.of() : List.copyOf(disclosures);
            disclosureHashes = disclosureHashes == null ? List.of() : List.copyOf(disclosureHashes);
            keyBindingJwt = keyBindingJwt == null ? Optional.empty() : keyBindingJwt;
            trustedAuthorityPolicies =
                    trustedAuthorityPolicies == null ? List.of() : List.copyOf(trustedAuthorityPolicies);
        }
    }

    public record InlineSdJwt(
            String credentialId,
            String format,
            String compactSdJwt,
            List<String> disclosures,
            Optional<String> keyBindingJwt,
            List<String> trustedAuthorityPolicies) {
        public InlineSdJwt {
            Objects.requireNonNull(credentialId, "credentialId");
            Objects.requireNonNull(format, "format");
            Objects.requireNonNull(compactSdJwt, "compactSdJwt");
            disclosures = disclosures == null ? List.of() : List.copyOf(disclosures);
            keyBindingJwt = keyBindingJwt == null ? Optional.empty() : keyBindingJwt;
            trustedAuthorityPolicies =
                    trustedAuthorityPolicies == null ? List.of() : List.copyOf(trustedAuthorityPolicies);
        }
    }

    public record VpToken(String vpToken, Map<String, Object> presentationSubmission) {
        public VpToken {
            Objects.requireNonNull(vpToken, "vpToken");
            presentationSubmission = presentationSubmission == null ? Map.of() : Map.copyOf(presentationSubmission);
        }
    }

    public record Presentation(
            String credentialId,
            String format,
            boolean holderBinding,
            Optional<TrustedAuthorityEvaluator.TrustedAuthorityVerdict> trustedAuthorityMatch,
            VpToken vpToken,
            List<String> disclosureHashes) {
        public Presentation {
            Objects.requireNonNull(credentialId, "credentialId");
            Objects.requireNonNull(format, "format");
            trustedAuthorityMatch = trustedAuthorityMatch == null ? Optional.empty() : trustedAuthorityMatch;
            Objects.requireNonNull(vpToken, "vpToken");
            disclosureHashes = disclosureHashes == null ? List.of() : List.copyOf(disclosureHashes);
        }
    }

    public record Trace(
            String vpTokenHash,
            Optional<String> kbJwtHash,
            List<String> disclosureHashes,
            Optional<TrustedAuthorityEvaluator.TrustedAuthorityVerdict> trustedAuthorityMatch,
            List<PresentationTrace> presentations) {
        public Trace {
            Objects.requireNonNull(vpTokenHash, "vpTokenHash");
            kbJwtHash = kbJwtHash == null ? Optional.empty() : kbJwtHash;
            disclosureHashes = disclosureHashes == null ? List.of() : List.copyOf(disclosureHashes);
            trustedAuthorityMatch = trustedAuthorityMatch == null ? Optional.empty() : trustedAuthorityMatch;
            presentations = presentations == null ? List.of() : List.copyOf(presentations);
        }
    }

    public record PresentationTrace(
            String presentationId,
            String credentialId,
            String format,
            boolean holderBinding,
            List<String> disclosureHashes,
            String vpTokenHash,
            Optional<String> kbJwtHash,
            Optional<String> deviceResponseHash,
            Optional<TrustedAuthorityEvaluator.TrustedAuthorityVerdict> trustedAuthorityMatch) {
        public PresentationTrace {
            Objects.requireNonNull(presentationId, "presentationId");
            Objects.requireNonNull(credentialId, "credentialId");
            Objects.requireNonNull(format, "format");
            disclosureHashes = disclosureHashes == null ? List.of() : List.copyOf(disclosureHashes);
            Objects.requireNonNull(vpTokenHash, "vpTokenHash");
            kbJwtHash = kbJwtHash == null ? Optional.empty() : kbJwtHash;
            deviceResponseHash = deviceResponseHash == null ? Optional.empty() : deviceResponseHash;
            trustedAuthorityMatch = trustedAuthorityMatch == null ? Optional.empty() : trustedAuthorityMatch;
        }
    }

    public static interface TelemetryPublisher {
        public TelemetrySignal walletResponded(
                String requestId, Profile profile, int presentations, Map<String, Object> fields);
    }

    public static enum Profile {
        HAIP,
        BASELINE;
    }

    public static interface TelemetrySignal {
        public String event();

        public Map<String, Object> fields();
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

    public static enum Status {
        SUCCESS,
        FAILED;
    }

    public static enum ResponseMode {
        FRAGMENT,
        DIRECT_POST,
        DIRECT_POST_JWT;
    }
}
