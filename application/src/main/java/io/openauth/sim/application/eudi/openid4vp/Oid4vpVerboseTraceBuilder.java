package io.openauth.sim.application.eudi.openid4vp;

import io.openauth.sim.application.eudi.openid4vp.OpenId4VpAuthorizationRequestService.AuthorizationRequestResult;
import io.openauth.sim.core.trace.VerboseTrace;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds canonical VerboseTrace instances for REST/CLI surfaces so all facades emit the same payload contract.
 */
public final class Oid4vpVerboseTraceBuilder {

    private static final String OPERATION_REQUEST = "eudiw.request.create";
    private static final String OPERATION_WALLET = "eudiw.wallet.simulate";
    private static final String OPERATION_VALIDATE = "eudiw.wallet.validate";

    private Oid4vpVerboseTraceBuilder() {}

    public static Optional<VerboseTrace> authorization(AuthorizationRequestResult result) {
        return result.trace().map(trace -> buildAuthorizationTrace(result, trace));
    }

    public static VerboseTrace wallet(OpenId4VpWalletSimulationService.SimulationResult result) {
        return buildWalletTrace(result);
    }

    public static VerboseTrace validation(OpenId4VpValidationService.ValidationResult result) {
        return buildValidationTrace(result);
    }

    private static VerboseTrace buildAuthorizationTrace(
            AuthorizationRequestResult result, OpenId4VpAuthorizationRequestService.Trace trace) {
        VerboseTrace.Builder builder = VerboseTrace.builder(OPERATION_REQUEST)
                .withTier(VerboseTrace.Tier.EDUCATIONAL)
                .withMetadata("request_id", result.requestId())
                .withMetadata("profile", result.profile().name())
                .withMetadata("response_mode", result.authorizationRequest().responseMode());

        builder.addStep(step -> {
            step.id("authorization.request")
                    .summary("Authorization request created")
                    .attribute("dcql_hash", trace.dcqlHash())
                    .attribute("nonce_full", trace.nonce())
                    .attribute("state_full", trace.state())
                    .attribute("request_uri", trace.requestUri());
            if (!trace.trustedAuthorities().isEmpty()) {
                step.attribute("trusted_authorities", join(trace.trustedAuthorities()));
            }
        });

        return builder.build();
    }

    private static VerboseTrace buildWalletTrace(OpenId4VpWalletSimulationService.SimulationResult result) {
        VerboseTrace.Builder builder = VerboseTrace.builder(OPERATION_WALLET)
                .withTier(VerboseTrace.Tier.EDUCATIONAL)
                .withMetadata("request_id", result.requestId())
                .withMetadata("profile", result.profile().name())
                .withMetadata("response_mode", result.responseMode().name());

        appendWalletTraceSummary(builder, result.trace());
        appendPresentationSteps(builder, "wallet.presentation", result.trace().presentations());
        return builder.build();
    }

    private static VerboseTrace buildValidationTrace(OpenId4VpValidationService.ValidationResult result) {
        VerboseTrace.Builder builder = VerboseTrace.builder(OPERATION_VALIDATE)
                .withTier(VerboseTrace.Tier.EDUCATIONAL)
                .withMetadata("request_id", result.requestId())
                .withMetadata("profile", result.profile().name())
                .withMetadata("response_mode", result.responseMode().name());

        appendValidationTraceSummary(builder, result.trace());
        appendValidationPresentationSteps(builder, result.trace().presentations());
        return builder.build();
    }

    private static void appendWalletTraceSummary(
            VerboseTrace.Builder builder, OpenId4VpWalletSimulationService.Trace trace) {
        builder.addStep(step -> {
            step.id("trace.summary").summary("Trace hashes").attribute("vp_token_hash", trace.vpTokenHash());
            trace.kbJwtHash().ifPresent(value -> step.attribute("kb_jwt_hash", value));
            String disclosures = join(trace.disclosureHashes());
            if (disclosures != null) {
                step.attribute("disclosure_hashes", disclosures);
            }
            trace.trustedAuthorityMatch().ifPresent(verdict -> step.attribute("trusted_authority", verdict.policy()));
        });
    }

    private static void appendValidationTraceSummary(
            VerboseTrace.Builder builder, OpenId4VpValidationService.Trace trace) {
        builder.addStep(step -> {
            step.id("trace.summary").summary("Trace hashes").attribute("vp_token_hash", trace.vpTokenHash());
            trace.kbJwtHash().ifPresent(value -> step.attribute("kb_jwt_hash", value));
            String disclosures = join(trace.disclosureHashes());
            if (disclosures != null) {
                step.attribute("disclosure_hashes", disclosures);
            }
            trace.trustedAuthorityMatch().ifPresent(verdict -> step.attribute("trusted_authority", verdict.policy()));
            trace.walletPresetId().ifPresent(value -> step.attribute("wallet_preset", value));
            trace.dcqlPreview().ifPresent(value -> step.attribute("dcql_preview", value));
        });
    }

    private static void appendPresentationSteps(
            VerboseTrace.Builder builder,
            String prefix,
            List<OpenId4VpWalletSimulationService.PresentationTrace> traces) {
        AtomicInteger counter = new AtomicInteger(1);
        traces.forEach(entry -> builder.addStep(step -> {
            step.id(prefix + "." + counter.getAndIncrement())
                    .summary("Presentation " + entry.credentialId())
                    .attribute("credential_id", entry.credentialId())
                    .attribute("format", entry.format())
                    .attribute("holder_binding", entry.holderBinding())
                    .attribute("vp_token_hash", entry.vpTokenHash());
            entry.kbJwtHash().ifPresent(value -> step.attribute("kb_jwt_hash", value));
            entry.deviceResponseHash().ifPresent(value -> step.attribute("device_response_hash", value));
            String disclosures = join(entry.disclosureHashes());
            if (disclosures != null) {
                step.attribute("disclosure_hashes", disclosures);
            }
            entry.trustedAuthorityMatch().ifPresent(match -> step.attribute("trusted_authority", match.policy()));
        }));
    }

    private static void appendValidationPresentationSteps(
            VerboseTrace.Builder builder, List<OpenId4VpValidationService.PresentationTrace> traces) {
        AtomicInteger counter = new AtomicInteger(1);
        traces.forEach(entry -> builder.addStep(step -> {
            step.id("validation.presentation." + counter.getAndIncrement())
                    .summary("Validated presentation " + entry.credentialId())
                    .attribute("credential_id", entry.credentialId())
                    .attribute("format", entry.format())
                    .attribute("holder_binding", entry.holderBinding())
                    .attribute("vp_token_hash", entry.vpTokenHash());
            entry.kbJwtHash().ifPresent(value -> step.attribute("kb_jwt_hash", value));
            entry.deviceResponseHash().ifPresent(value -> step.attribute("device_response_hash", value));
            String disclosures = join(entry.disclosureHashes());
            if (disclosures != null) {
                step.attribute("disclosure_hashes", disclosures);
            }
            entry.trustedAuthorityMatch().ifPresent(match -> step.attribute("trusted_authority", match.policy()));
        }));
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.joining(", "));
    }
}
