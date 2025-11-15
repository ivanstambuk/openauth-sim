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

/**
 * Native Java API seam for EUDIW OpenID4VP validation (Validate mode).
 *
 * <p>Used by Feature 006 – EUDIW OpenID4VP Simulator and Feature 014 – Native Java API Facade to
 * drive VP Token/DeviceResponse validation from Java callers without going through CLI/REST/UI.
 * Behaviour is specified in the Feature 006 spec (FR-006-01..06/11..13, FR-040-14..23) with
 * cross-cutting governance in Feature 014 (FR-014-02/04) and ADR-0007; usage examples live in
 * {@code docs/2-how-to/use-eudiw-from-java.md}. Callers construct {@link ValidateRequest}
 * instances and consume {@link ValidationResult} as façade DTOs.
 */
public final class OpenId4VpValidationService {

    private final Dependencies dependencies;

    /**
     * Creates a new validation service backed by the supplied dependencies.
     *
     * @param dependencies collaborators for stored presentations, Trusted Authorities evaluation,
     *     and telemetry
     */
    public OpenId4VpValidationService(Dependencies dependencies) {
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
    }

    /**
     * Validates a stored or inline VP Token request.
     *
     * <p>Exactly one of {@link ValidateRequest#storedPresentationId()} or
     * {@link ValidateRequest#inlineVpToken()} must be provided. On success, the method returns a
     * {@link ValidationResult} containing one or more presentations plus trace and telemetry
     * metadata; on failure, it emits a telemetry signal via the {@code responseFailed} callback and
     * throws {@link Oid4vpValidationException} with RFC&nbsp;7807 problem-details content.
     *
     * @param request validation request describing the stored or inline VP Token and Trusted
     *     Authority policies
     * @return validation result for the supplied request
     * @throws Oid4vpValidationException if validation fails (invalid request, Trusted Authorities
     *     miss, or presentation-level errors)
     */
    public ValidationResult validate(ValidateRequest request) {
        Objects.requireNonNull(request, "request");

        Submission submission = resolveSubmission(request);
        try {
            TrustedAuthorityEvaluator.Decision decision = this.dependencies
                    .trustedAuthorityEvaluator()
                    .evaluate(request.trustedAuthorityPolicy(), submission.trustedAuthorityPolicies());
            if (decision.problemDetails().isPresent()) {
                throw new Oid4vpValidationException(decision.problemDetails().orElseThrow());
            }

            VerifiedPresentation verified = verify(submission);
            TelemetrySignal telemetry = this.dependencies
                    .telemetryPublisher()
                    .responseValidated(
                            request.requestId(),
                            submission.profile(),
                            1,
                            telemetryFields(request, submission, decision.trustedAuthorityMatch()),
                            decision.trustedAuthorityMatch());

            PresentationTrace presentationTrace = new PresentationTrace(
                    submission.credentialId(),
                    submission.credentialId(),
                    submission.format(),
                    verified.holderBinding(),
                    verified.disclosureHashes(),
                    verified.vpTokenHash(),
                    verified.kbJwtHash(),
                    Optional.empty(),
                    decision.trustedAuthorityMatch());
            Trace trace = new Trace(
                    verified.vpTokenHash(),
                    verified.kbJwtHash(),
                    verified.disclosureHashes(),
                    decision.trustedAuthorityMatch(),
                    submission.walletPresetId(),
                    submission.dcqlPreview(),
                    List.of(presentationTrace));
            Presentation presentation = new Presentation(
                    submission.credentialId(),
                    submission.format(),
                    verified.holderBinding(),
                    decision.trustedAuthorityMatch(),
                    new VpToken(verified.vpToken(), verified.presentationSubmission()),
                    verified.disclosureHashes());

            return new ValidationResult(
                    request.requestId(),
                    Status.SUCCESS,
                    submission.profile(),
                    submission.responseMode(),
                    List.of(presentation),
                    trace,
                    telemetry);
        } catch (Oid4vpValidationException ex) {
            this.dependencies
                    .telemetryPublisher()
                    .responseFailed(
                            request.requestId(),
                            submission.profile(),
                            ex.problemDetails().title(),
                            failureFields(request, submission, ex.problemDetails()));
            throw ex;
        }
    }

    private Submission resolveSubmission(ValidateRequest request) {
        boolean hasStored = request.storedPresentationId().isPresent();
        boolean hasInline = request.inlineVpToken().isPresent();
        if (hasStored == hasInline) {
            throw new Oid4vpValidationException(Oid4vpProblemDetailsMapper.invalidRequest(
                    "Provide exactly one of storedPresentationId or inlineVpToken", List.of()));
        }

        if (hasStored) {
            String presentationId = request.storedPresentationId().orElseThrow();
            StoredPresentation stored;
            try {
                stored = this.dependencies.storedPresentationRepository().load(presentationId);
            } catch (IllegalArgumentException ex) {
                throw new Oid4vpValidationException(
                        Oid4vpProblemDetailsMapper.invalidRequest(ex.getMessage(), List.of()));
            }
            OpenId4VpWalletSimulationService.ResponseMode responseMode =
                    request.responseModeOverride().orElse(stored.responseMode());
            return new Submission(
                    stored.credentialId(),
                    stored.format(),
                    stored.profile(),
                    responseMode,
                    stored.vpToken(),
                    stored.keyBindingJwt(),
                    stored.disclosures(),
                    stored.trustedAuthorityPolicies(),
                    Optional.of(presentationId),
                    stored.dcqlJson());
        }

        InlineVpToken inline = request.inlineVpToken().orElseThrow();
        OpenId4VpWalletSimulationService.ResponseMode responseMode =
                request.responseModeOverride().orElse(OpenId4VpWalletSimulationService.ResponseMode.FRAGMENT);
        return new Submission(
                inline.credentialId(),
                inline.format(),
                request.profile(),
                responseMode,
                inline.vpToken(),
                inline.keyBindingJwt(),
                inline.disclosures(),
                inline.trustedAuthorityPolicies(),
                Optional.empty(),
                request.inlineDcqlJson());
    }

    private static VerifiedPresentation verify(Submission submission) {
        Map<String, Object> vpTokenPayload = submission.vpToken();
        Object vpTokenValue = vpTokenPayload.get("vp_token");
        if (!(vpTokenValue instanceof String token) || token.isBlank()) {
            throw new Oid4vpValidationException(
                    Oid4vpProblemDetailsMapper.invalidPresentation("vp_token field is required"));
        }
        Map<String, Object> presentationSubmission =
                sanitizePresentationSubmission(vpTokenPayload.get("presentation_submission"));
        List<String> disclosureHashes = computeDisclosureHashes(submission.disclosures());
        Optional<String> kbJwtHash = submission.keyBindingJwt().map(OpenId4VpValidationService::hashValue);
        boolean holderBinding = submission.keyBindingJwt().isPresent();
        return new VerifiedPresentation(
                token, presentationSubmission, holderBinding, disclosureHashes, hashValue(token), kbJwtHash);
    }

    private static Map<String, Object> telemetryFields(
            ValidateRequest request,
            Submission submission,
            Optional<TrustedAuthorityEvaluator.TrustedAuthorityVerdict> match) {
        Map<String, Object> fields = new LinkedHashMap<>();
        request.trustedAuthorityPolicy().ifPresent(policy -> fields.put("trustedAuthorityRequested", policy));
        match.ifPresent(verdict -> fields.put(
                "trustedAuthority",
                Map.of(
                        "type", verdict.type(),
                        "value", verdict.value(),
                        "label", verdict.label(),
                        "policy", verdict.policy())));
        submission.walletPresetId().ifPresent(id -> fields.put("walletPreset", id));
        submission.dcqlPreview().ifPresent(preview -> fields.put("dcqlPreview", preview));
        fields.put("presentations", 1);
        fields.put("profile", submission.profile().name());
        fields.put("responseMode", submission.responseMode().name());
        return fields;
    }

    private static Map<String, Object> failureFields(
            ValidateRequest request, Submission submission, Oid4vpProblemDetails problemDetails) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("reason", problemDetails.title());
        fields.put("status", problemDetails.status());
        submission.walletPresetId().ifPresent(id -> fields.put("walletPreset", id));
        request.trustedAuthorityPolicy().ifPresent(policy -> fields.put("trustedAuthorityRequested", policy));
        fields.put("profile", submission.profile().name());
        fields.put("responseMode", submission.responseMode().name());
        return fields;
    }

    private static Map<String, Object> sanitizePresentationSubmission(Object value) {
        if (!(value instanceof Map<?, ?> submission)) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        submission.forEach((key, entry) -> copy.put(String.valueOf(key), entry));
        return Map.copyOf(copy);
    }

    private static List<String> computeDisclosureHashes(List<String> disclosures) {
        if (disclosures == null || disclosures.isEmpty()) {
            return List.of();
        }
        List<String> hashes = new ArrayList<>(disclosures.size());
        for (String disclosure : disclosures) {
            hashes.add(hashValue(disclosure));
        }
        return List.copyOf(hashes);
    }

    private static String hashValue(String value) {
        return "sha-256:" + sha256Hex(value);
    }

    private static String sha256Hex(String value) {
        Objects.requireNonNull(value, "value");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    public record Dependencies(
            StoredPresentationRepository storedPresentationRepository,
            TrustedAuthorityEvaluator trustedAuthorityEvaluator,
            TelemetryPublisher telemetryPublisher) {
        public Dependencies {
            Objects.requireNonNull(storedPresentationRepository, "storedPresentationRepository");
            Objects.requireNonNull(trustedAuthorityEvaluator, "trustedAuthorityEvaluator");
            Objects.requireNonNull(telemetryPublisher, "telemetryPublisher");
        }
    }

    /**
     * Validation request describing a stored or inline VP Token plus associated Trusted Authority
     * policies and optional DCQL preview.
     */
    public record ValidateRequest(
            String requestId,
            OpenId4VpWalletSimulationService.Profile profile,
            Optional<OpenId4VpWalletSimulationService.ResponseMode> responseModeOverride,
            Optional<String> storedPresentationId,
            Optional<InlineVpToken> inlineVpToken,
            Optional<String> trustedAuthorityPolicy,
            Optional<String> inlineDcqlJson) {
        public ValidateRequest {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(profile, "profile");
            responseModeOverride = responseModeOverride == null ? Optional.empty() : responseModeOverride;
            storedPresentationId = storedPresentationId == null ? Optional.empty() : storedPresentationId;
            inlineVpToken = inlineVpToken == null ? Optional.empty() : inlineVpToken;
            trustedAuthorityPolicy =
                    trustedAuthorityPolicy == null ? Optional.empty() : trustedAuthorityPolicy.map(String::trim);
            inlineDcqlJson = inlineDcqlJson == null ? Optional.empty() : inlineDcqlJson;
        }
    }

    /**
     * Inline VP Token payload used when validating ad-hoc OpenID4VP presentations rather than
     * stored fixtures.
     */
    public record InlineVpToken(
            String credentialId,
            String format,
            Map<String, Object> vpToken,
            Optional<String> keyBindingJwt,
            List<String> disclosures,
            List<String> trustedAuthorityPolicies) {
        public InlineVpToken {
            Objects.requireNonNull(credentialId, "credentialId");
            Objects.requireNonNull(format, "format");
            vpToken = vpToken == null ? Map.of() : Map.copyOf(vpToken);
            keyBindingJwt = keyBindingJwt == null ? Optional.empty() : keyBindingJwt;
            disclosures = disclosures == null ? List.of() : List.copyOf(disclosures);
            trustedAuthorityPolicies =
                    trustedAuthorityPolicies == null ? List.of() : List.copyOf(trustedAuthorityPolicies);
        }
    }

    /**
     * Stored presentation snapshot loaded from the repository and used as validation input when
     * {@link ValidateRequest#storedPresentationId()} is supplied.
     */
    public record StoredPresentation(
            String presentationId,
            String credentialId,
            String format,
            OpenId4VpWalletSimulationService.Profile profile,
            OpenId4VpWalletSimulationService.ResponseMode responseMode,
            Map<String, Object> vpToken,
            Optional<String> keyBindingJwt,
            List<String> disclosures,
            List<String> trustedAuthorityPolicies,
            Optional<String> dcqlJson) {
        public StoredPresentation {
            Objects.requireNonNull(presentationId, "presentationId");
            Objects.requireNonNull(credentialId, "credentialId");
            Objects.requireNonNull(format, "format");
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(responseMode, "responseMode");
            vpToken = vpToken == null ? Map.of() : Map.copyOf(vpToken);
            keyBindingJwt = keyBindingJwt == null ? Optional.empty() : keyBindingJwt;
            disclosures = disclosures == null ? List.of() : List.copyOf(disclosures);
            trustedAuthorityPolicies =
                    trustedAuthorityPolicies == null ? List.of() : List.copyOf(trustedAuthorityPolicies);
            dcqlJson = dcqlJson == null ? Optional.empty() : dcqlJson;
        }
    }

    public interface StoredPresentationRepository {
        StoredPresentation load(String presentationId);
    }

    public record ValidationResult(
            String requestId,
            Status status,
            OpenId4VpWalletSimulationService.Profile profile,
            OpenId4VpWalletSimulationService.ResponseMode responseMode,
            List<Presentation> presentations,
            Trace trace,
            TelemetrySignal telemetry) {
        public ValidationResult {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(responseMode, "responseMode");
            presentations = presentations == null ? List.of() : List.copyOf(presentations);
            Objects.requireNonNull(trace, "trace");
            Objects.requireNonNull(telemetry, "telemetry");
        }
    }

    public enum Status {
        SUCCESS,
        FAILED
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

    public record VpToken(String vpToken, Map<String, Object> presentationSubmission) {
        public VpToken {
            Objects.requireNonNull(vpToken, "vpToken");
            presentationSubmission = presentationSubmission == null ? Map.of() : Map.copyOf(presentationSubmission);
        }
    }

    public record Trace(
            String vpTokenHash,
            Optional<String> kbJwtHash,
            List<String> disclosureHashes,
            Optional<TrustedAuthorityEvaluator.TrustedAuthorityVerdict> trustedAuthorityMatch,
            Optional<String> walletPresetId,
            Optional<String> dcqlPreview,
            List<PresentationTrace> presentations) {
        public Trace {
            Objects.requireNonNull(vpTokenHash, "vpTokenHash");
            kbJwtHash = kbJwtHash == null ? Optional.empty() : kbJwtHash;
            disclosureHashes = disclosureHashes == null ? List.of() : List.copyOf(disclosureHashes);
            trustedAuthorityMatch = trustedAuthorityMatch == null ? Optional.empty() : trustedAuthorityMatch;
            walletPresetId = walletPresetId == null ? Optional.empty() : walletPresetId;
            dcqlPreview = dcqlPreview == null ? Optional.empty() : dcqlPreview;
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

    public interface TelemetryPublisher {
        TelemetrySignal responseValidated(
                String requestId,
                OpenId4VpWalletSimulationService.Profile profile,
                int presentations,
                Map<String, Object> fields,
                Optional<TrustedAuthorityEvaluator.TrustedAuthorityVerdict> trustedAuthorityMatch);

        TelemetrySignal responseFailed(
                String requestId,
                OpenId4VpWalletSimulationService.Profile profile,
                String reason,
                Map<String, Object> fields);
    }

    public interface TelemetrySignal {
        String event();

        Map<String, Object> fields();
    }

    private record Submission(
            String credentialId,
            String format,
            OpenId4VpWalletSimulationService.Profile profile,
            OpenId4VpWalletSimulationService.ResponseMode responseMode,
            Map<String, Object> vpToken,
            Optional<String> keyBindingJwt,
            List<String> disclosures,
            List<String> trustedAuthorityPolicies,
            Optional<String> walletPresetId,
            Optional<String> dcqlPreview) {
        Submission {
            Objects.requireNonNull(credentialId, "credentialId");
            Objects.requireNonNull(format, "format");
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(responseMode, "responseMode");
            vpToken = vpToken == null ? Map.of() : Map.copyOf(vpToken);
            keyBindingJwt = keyBindingJwt == null ? Optional.empty() : keyBindingJwt;
            disclosures = disclosures == null ? List.of() : List.copyOf(disclosures);
            trustedAuthorityPolicies =
                    trustedAuthorityPolicies == null ? List.of() : List.copyOf(trustedAuthorityPolicies);
            walletPresetId = walletPresetId == null ? Optional.empty() : walletPresetId;
            dcqlPreview = dcqlPreview == null ? Optional.empty() : dcqlPreview;
        }
    }

    private record VerifiedPresentation(
            String vpToken,
            Map<String, Object> presentationSubmission,
            boolean holderBinding,
            List<String> disclosureHashes,
            String vpTokenHash,
            Optional<String> kbJwtHash) {
        VerifiedPresentation {
            Objects.requireNonNull(vpToken, "vpToken");
            presentationSubmission = presentationSubmission == null ? Map.of() : Map.copyOf(presentationSubmission);
            disclosureHashes = disclosureHashes == null ? List.of() : List.copyOf(disclosureHashes);
            Objects.requireNonNull(vpTokenHash, "vpTokenHash");
            kbJwtHash = kbJwtHash == null ? Optional.empty() : kbJwtHash;
        }
    }
}
