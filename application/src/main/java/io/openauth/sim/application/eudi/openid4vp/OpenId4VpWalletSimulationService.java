package io.openauth.sim.application.eudi.openid4vp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
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
        Optional<String> trustedAuthorityMatch = OpenId4VpWalletSimulationService.determineTrustedAuthorityMatch(
                request.trustedAuthorityPolicy(), trustedAuthorityPolicies);
        VpToken vpToken = new VpToken(compactSdJwt, Map.of("presentation_definition_id", credentialId));
        boolean holderBinding = keyBindingJwt.isPresent();
        Presentation presentation =
                new Presentation(credentialId, format, holderBinding, trustedAuthorityMatch, vpToken, disclosureHashes);
        Trace trace = new Trace(
                OpenId4VpWalletSimulationService.sha256Hex(compactSdJwt),
                keyBindingJwt.map(OpenId4VpWalletSimulationService::sha256Hex),
                disclosureHashes);
        TelemetrySignal telemetry =
                this.dependencies.telemetryPublisher().walletResponded(request.requestId(), request.profile(), 1);
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

    private static Optional<String> determineTrustedAuthorityMatch(
            Optional<String> requestedPolicy, List<String> availablePolicies) {
        if (requestedPolicy == null || requestedPolicy.isEmpty()) {
            return Optional.empty();
        }
        List<String> policies = availablePolicies == null ? List.of() : List.copyOf(availablePolicies);
        return requestedPolicy.filter(policies::contains);
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

    public record Dependencies(WalletPresetRepository walletPresetRepository, TelemetryPublisher telemetryPublisher) {
        public Dependencies {
            Objects.requireNonNull(walletPresetRepository, "walletPresetRepository");
            Objects.requireNonNull(telemetryPublisher, "telemetryPublisher");
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
            Optional<String> trustedAuthorityMatch,
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

    public record Trace(String vpTokenHash, Optional<String> kbJwtHash, List<String> disclosureHashes) {
        public Trace {
            Objects.requireNonNull(vpTokenHash, "vpTokenHash");
            kbJwtHash = kbJwtHash == null ? Optional.empty() : kbJwtHash;
            disclosureHashes = disclosureHashes == null ? List.of() : List.copyOf(disclosureHashes);
        }
    }

    public static interface TelemetryPublisher {
        public TelemetrySignal walletResponded(String var1, Profile var2, int var3);
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
