package io.openauth.sim.rest.webauthn;

import io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationService;
import io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationService.GeneratedAttestation;
import io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationService.GenerationResult;
import io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationService.TelemetrySignal;
import io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationService.TelemetryStatus;
import io.openauth.sim.application.fido2.WebAuthnAttestationReplayApplicationService;
import io.openauth.sim.application.fido2.WebAuthnAttestationReplayApplicationService.ReplayCommand;
import io.openauth.sim.application.fido2.WebAuthnAttestationVerificationApplicationService;
import io.openauth.sim.application.fido2.WebAuthnAttestationVerificationApplicationService.VerificationCommand;
import io.openauth.sim.application.fido2.WebAuthnTrustAnchorResolver;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator;
import io.openauth.sim.core.trace.VerboseTrace;
import io.openauth.sim.rest.VerboseTracePayload;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;

@Service
class WebAuthnAttestationService {

    private static final Logger TELEMETRY_LOGGER =
            Logger.getLogger("io.openauth.sim.rest.webauthn.attestation.telemetry");
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final String EVENT_ATTEST = "rest.fido2.attest";
    private static final String EVENT_ATTEST_REPLAY = "rest.fido2.attestReplay";

    private final WebAuthnAttestationGenerationApplicationService generationService;
    private final WebAuthnAttestationReplayApplicationService replayService;
    private final WebAuthnAttestationVerificationApplicationService verificationService;
    private final WebAuthnTrustAnchorResolver trustAnchorResolver;

    WebAuthnAttestationService(
            WebAuthnAttestationGenerationApplicationService generationService,
            WebAuthnAttestationReplayApplicationService replayService,
            WebAuthnAttestationVerificationApplicationService verificationService,
            WebAuthnTrustAnchorResolver trustAnchorResolver) {
        this.generationService = Objects.requireNonNull(generationService, "generationService");
        this.replayService = Objects.requireNonNull(replayService, "replayService");
        this.verificationService = Objects.requireNonNull(verificationService, "verificationService");
        this.trustAnchorResolver = Objects.requireNonNull(trustAnchorResolver, "trustAnchorResolver");
    }

    WebAuthnAttestationResponse generate(WebAuthnAttestationGenerationRequest request) {
        Objects.requireNonNull(request, "request");

        boolean verbose = Boolean.TRUE.equals(request.verbose());
        VerboseTrace.Builder trace = newTrace(verbose, "fido2.attestation.generate");
        metadata(trace, "inputSource", request.inputSource());
        metadata(trace, "format", request.format());
        metadata(trace, "attestationId", request.attestationId());
        metadata(trace, "relyingPartyId", request.relyingPartyId());

        InputSource source = parseInputSource(request.inputSource());
        WebAuthnAttestationFormat format = parseFormat(request.format());

        metadata(trace, "source", source.name().toLowerCase(java.util.Locale.ROOT));
        metadata(trace, "formatResolved", format.label());

        GenerationResult result;
        if (source == InputSource.STORED) {
            String credentialId = requireText(request.credentialId(), "credential_id_required", "Credential ID");
            addStep(trace, step -> step.id("resolve.stored.credential")
                    .summary("Resolve stored attestation credential")
                    .detail("CredentialStore.findByName")
                    .attribute("credentialId", credentialId));
            byte[] challenge = decode(
                    "challenge_required",
                    "Invalid challenge (must be Base64URL)",
                    requireText(request.challenge(), "challenge_required", "Challenge"));

            String relyingPartyId = sanitize(request.relyingPartyId());
            String origin = sanitize(request.origin());

            var command = new WebAuthnAttestationGenerationApplicationService.GenerationCommand.Stored(
                    credentialId.trim(), format, relyingPartyId, origin, challenge);

            try {
                result = generationService.generate(command, trace != null);
                addStep(trace, step -> step.id("generate.attestation")
                        .summary("Generate attestation from stored credential")
                        .detail("WebAuthnAttestationGenerationApplicationService.generate")
                        .attribute("credentialId", credentialId));
            } catch (IllegalArgumentException ex) {
                String message = ex.getMessage() == null ? "Stored attestation generation failed" : ex.getMessage();
                String reason = mapStoredGenerationReason(message);
                addStep(trace, step -> step.id("generator.failure")
                        .summary("Stored attestation generation failed")
                        .detail("WebAuthnAttestationGenerationApplicationService.generate")
                        .note("message", message));
                throw validation(reason, message, Map.of("credentialId", credentialId), buildTrace(trace));
            } catch (Exception ex) {
                addStep(trace, step -> step.id("generator.error")
                        .summary("Unexpected error during stored attestation generation")
                        .detail(ex.getClass().getName())
                        .note("message", ex.getMessage()));
                throw unexpected(
                        "generation_failed",
                        "Attestation generation failed: " + sanitize(ex.getMessage()),
                        Map.of("credentialId", credentialId),
                        buildTrace(trace));
            }
        } else if (source == InputSource.PRESET) {
            String attestationId = requireText(request.attestationId(), "attestation_id_required", "Attestation ID");
            addStep(trace, step -> step.id("resolve.preset")
                    .summary("Resolve attestation preset")
                    .detail("WebAuthnAttestationFixtures.vectorsFor")
                    .attribute("attestationId", attestationId));
            String relyingPartyId =
                    requireText(request.relyingPartyId(), "relying_party_id_required", "Relying party ID");
            String origin = requireText(request.origin(), "origin_required", "Origin");

            byte[] challenge = decode(
                    "challenge_required",
                    "Invalid challenge (must be Base64URL)",
                    requireText(request.challenge(), "challenge_required", "Challenge"));

            WebAuthnAttestationVector vector = requireVector(attestationId, format);

            String credentialPrivateKey = requireText(
                    request.credentialPrivateKey(), "credential_private_key_required", "Credential private key");
            boolean attestationKeyRequired = vector.keyMaterial().attestationPrivateKeyJwk() != null
                    || vector.keyMaterial().attestationPrivateKeyBase64Url() != null;
            String attestationPrivateKey;
            if (attestationKeyRequired) {
                attestationPrivateKey = requireText(
                        request.attestationPrivateKey(), "attestation_private_key_required", "Attestation private key");
            } else {
                String sanitized = sanitize(request.attestationPrivateKey());
                attestationPrivateKey = sanitized.isBlank() ? null : sanitized;
            }
            String attestationSerial = requireText(
                    request.attestationCertificateSerial(),
                    "attestation_serial_required",
                    "Attestation certificate serial");
            String signingMode = requireText(request.signingMode(), "signing_mode_required", "Signing mode")
                    .trim();

            List<String> customRoots = sanitizeRoots(request.customRootCertificates());
            addStep(trace, step -> step.id("prepare.manual")
                    .summary("Prepare manual attestation command")
                    .detail("GenerationCommand.Manual")
                    .attribute("signingMode", signingMode)
                    .attribute("customRootCount", customRoots.size()));

            var command = new WebAuthnAttestationGenerationApplicationService.GenerationCommand.Inline(
                    attestationId,
                    format,
                    relyingPartyId,
                    origin,
                    challenge,
                    credentialPrivateKey,
                    attestationPrivateKey,
                    attestationSerial,
                    parseSigningMode(signingMode),
                    customRoots,
                    customRoots.isEmpty() ? "" : "inline",
                    WebAuthnAttestationGenerationApplicationService.GenerationCommand.InputSource.PRESET);

            try {
                result = generationService.generate(command, trace != null);
                addStep(trace, step -> step.id("generate.attestation")
                        .summary("Generate attestation from preset")
                        .detail("WebAuthnAttestationGenerationApplicationService.generate")
                        .attribute("attestationId", attestationId));
            } catch (IllegalArgumentException ex) {
                addStep(trace, step -> step.id("generator.failure")
                        .summary("Preset attestation generation failed")
                        .detail("WebAuthnAttestationGenerationApplicationService.generate")
                        .note("message", ex.getMessage()));
                throw validation(
                        "generation_invalid",
                        ex.getMessage() == null ? "Attestation generation failed" : ex.getMessage(),
                        Map.of("attestationId", attestationId, "format", format.label()),
                        buildTrace(trace));
            } catch (Exception ex) {
                addStep(trace, step -> step.id("generator.error")
                        .summary("Unexpected error during preset attestation generation")
                        .detail(ex.getClass().getName())
                        .note("message", ex.getMessage()));
                throw unexpected(
                        "generation_failed",
                        "Attestation generation failed: " + sanitize(ex.getMessage()),
                        Map.of("attestationId", attestationId, "format", format.label()),
                        buildTrace(trace));
            }
        } else {
            String relyingPartyId =
                    requireText(request.relyingPartyId(), "relying_party_id_required", "Relying party ID");
            String origin = requireText(request.origin(), "origin_required", "Origin");
            byte[] challenge = decode(
                    "challenge_required",
                    "Invalid challenge (must be Base64URL)",
                    requireText(request.challenge(), "challenge_required", "Challenge"));

            String credentialPrivateKey = requireText(
                    request.credentialPrivateKey(), "credential_private_key_required", "Credential private key");
            String signingMode = requireText(request.signingMode(), "signing_mode_required", "Signing mode")
                    .trim();

            List<String> customRoots = sanitizeRoots(request.customRootCertificates());

            WebAuthnAttestationGenerator.SigningMode mode = parseSigningMode(signingMode);
            String attestationPrivateKey = sanitize(request.attestationPrivateKey());
            String attestationSerial = sanitize(request.attestationCertificateSerial());
            if (mode != WebAuthnAttestationGenerator.SigningMode.UNSIGNED) {
                if (attestationPrivateKey.isBlank()) {
                    throw validation(
                            "attestation_private_key_required",
                            "Attestation private key is required for signed modes",
                            Map.of());
                }
                if (attestationSerial.isBlank()) {
                    throw validation(
                            "attestation_serial_required",
                            "Attestation certificate serial is required for signed modes",
                            Map.of());
                }
            }
            if (mode == WebAuthnAttestationGenerator.SigningMode.CUSTOM_ROOT && customRoots.isEmpty()) {
                throw validation(
                        "custom_root_required",
                        "At least one custom root certificate is required for custom-root mode",
                        Map.of());
            }

            var command = new WebAuthnAttestationGenerationApplicationService.GenerationCommand.Manual(
                    format,
                    relyingPartyId,
                    origin,
                    challenge,
                    credentialPrivateKey,
                    attestationPrivateKey.isBlank() ? null : attestationPrivateKey,
                    attestationSerial.isBlank() ? null : attestationSerial,
                    mode,
                    customRoots,
                    customRoots.isEmpty() ? "" : "inline",
                    sanitize(request.seedPresetId()),
                    request.overrides() == null ? List.of() : List.copyOf(request.overrides()));

            try {
                result = generationService.generate(command, trace != null);
                addStep(trace, step -> step.id("generate.attestation")
                        .summary("Generate manual attestation")
                        .detail("WebAuthnAttestationGenerationApplicationService.generate")
                        .attribute("signingMode", mode.name()));
            } catch (IllegalArgumentException ex) {
                addStep(trace, step -> step.id("generator.failure")
                        .summary("Manual attestation generation failed")
                        .detail("WebAuthnAttestationGenerationApplicationService.generate")
                        .note("message", ex.getMessage()));
                throw validation(
                        "generation_invalid",
                        ex.getMessage() == null ? "Attestation generation failed" : ex.getMessage(),
                        Map.of("format", format.label(), "inputSource", "MANUAL"),
                        buildTrace(trace));
            } catch (Exception ex) {
                addStep(trace, step -> step.id("generator.error")
                        .summary("Unexpected error during manual attestation generation")
                        .detail(ex.getClass().getName())
                        .note("message", ex.getMessage()));
                throw unexpected(
                        "generation_failed",
                        "Attestation generation failed: " + sanitize(ex.getMessage()),
                        Map.of("format", format.label(), "inputSource", "MANUAL"),
                        buildTrace(trace));
            }
        }

        if (trace != null) {
            result.verboseTrace().ifPresent(generatorTrace -> appendTrace(trace, generatorTrace));
        }

        TelemetrySignal telemetry = result.telemetry();
        if (telemetry.status() != TelemetryStatus.SUCCESS) {
            addStep(trace, step -> step.id("generation.invalid")
                    .summary("Attestation generation invalid")
                    .detail("Telemetry status")
                    .attribute("status", telemetry.status().name()));
            throw unexpected(
                    telemetry.reasonCode(), "Attestation generation failed", telemetry.fields(), buildTrace(trace));
        }

        String telemetryId = nextTelemetryId(EVENT_ATTEST);
        TelemetryFrame frame = telemetry.emit(TelemetryContracts.fido2AttestAdapter(), telemetryId);
        logTelemetry(frame, EVENT_ATTEST);

        GeneratedAttestation attestation = result.attestation();
        WebAuthnGeneratedAttestation.AttestationResponse responsePayload =
                new WebAuthnGeneratedAttestation.AttestationResponse(
                        attestation.response().clientDataJson(),
                        attestation.response().attestationObject());

        WebAuthnGeneratedAttestation generated = new WebAuthnGeneratedAttestation(
                attestation.type(), attestation.id(), attestation.rawId(), responsePayload);

        WebAuthnAttestationMetadata metadata = WebAuthnAttestationMetadata.forGeneration(
                telemetryId, telemetry.reasonCode(), format.label(), telemetry.fields(), result.certificateChainPem());

        VerboseTrace builtTrace = buildTrace(trace);
        VerboseTracePayload tracePayload = builtTrace == null ? null : VerboseTracePayload.from(builtTrace);

        return new WebAuthnAttestationResponse("success", generated, null, metadata, tracePayload);
    }

    private static List<String> sanitizeRoots(List<String> roots) {
        return roots == null
                ? List.of()
                : roots.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .toList();
    }

    WebAuthnAttestationResponse replay(WebAuthnAttestationReplayRequest request) {
        Objects.requireNonNull(request, "request");
        boolean verbose = Boolean.TRUE.equals(request.verbose());
        VerboseTrace.Builder trace = newTrace(verbose, "fido2.attestation.verify");
        metadata(trace, "attestationId", request.attestationId());
        metadata(trace, "inputSource", request.inputSource());

        InputSource source = parseInputSource(request.inputSource());
        metadata(trace, "inputSourceResolved", source.name().toLowerCase(Locale.ROOT));

        WebAuthnAttestationFormat format = parseFormat(request.format());
        metadata(trace, "format", format.label());

        WebAuthnAttestationReplayApplicationService.ReplayResult result;
        WebAuthnAttestationReplayApplicationService.ReplayCommand.Inline inlineCommand = null;
        VerboseTrace verificationTrace = null;

        if (source == InputSource.STORED) {
            String credentialId = requireText(request.credentialId(), "credential_id_required", "Credential ID");
            metadata(trace, "storedCredentialId", credentialId);

            if (request.trustAnchors() != null && !request.trustAnchors().isEmpty()) {
                addStep(trace, step -> step.id("stored.trustAnchors.unsupported")
                        .summary("Stored replay does not accept inline trust anchors")
                        .detail("trustAnchors provided for stored input")
                        .attribute("count", request.trustAnchors().size()));
                throw validation(
                        "stored_trust_anchor_unsupported",
                        "Stored attestation replay relies on persisted certificate chains; omit trust anchors.",
                        Map.of("credentialId", credentialId, "format", format.label()),
                        buildTrace(trace));
            }

            addStep(trace, step -> step.id("resolve.stored.credential")
                    .summary("Resolve stored attestation credential")
                    .detail("CredentialStore.findByName")
                    .attribute("credentialId", credentialId));

            try {
                result = replayService.replay(
                        new WebAuthnAttestationReplayApplicationService.ReplayCommand.Stored(credentialId, format));
            } catch (IllegalArgumentException ex) {
                String sanitizedMessage = sanitize(ex.getMessage());
                String message = sanitizedMessage.isBlank() ? "Stored attestation replay failed" : sanitizedMessage;
                addStep(trace, step -> step.id("replay.failure")
                        .summary("Attestation replay failed")
                        .detail("WebAuthnAttestationReplayApplicationService.replay")
                        .note("message", message));
                throw validation(
                        mapStoredReplayReason(message),
                        message,
                        Map.of("credentialId", credentialId, "format", format.label()),
                        buildTrace(trace));
            } catch (Exception ex) {
                addStep(trace, step -> step.id("replay.error")
                        .summary("Unexpected error during attestation replay")
                        .detail(ex.getClass().getName())
                        .note("message", ex.getMessage()));
                throw unexpected(
                        "replay_failed",
                        "Attestation replay failed: " + sanitize(ex.getMessage()),
                        Map.of("credentialId", credentialId, "format", format.label()),
                        buildTrace(trace));
            }
        } else {
            WebAuthnTrustAnchorResolver.Resolution anchorResolution =
                    trustAnchorResolver.resolvePemStrings(request.attestationId(), format, request.trustAnchors());
            addStep(trace, step -> step.id("resolve.trustAnchors")
                    .summary("Resolve trust anchors for attestation verification")
                    .detail("WebAuthnTrustAnchorResolver.resolvePemStrings")
                    .attribute("anchorCount", anchorResolution.anchors().size())
                    .attribute("cached", anchorResolution.cached()));
            logTrustAnchorWarnings(anchorResolution.warnings());

            inlineCommand = new WebAuthnAttestationReplayApplicationService.ReplayCommand.Inline(
                    sanitize(request.attestationId()),
                    format,
                    requireText(request.relyingPartyId(), "relying_party_id_required", "Relying party ID"),
                    requireText(request.origin(), "origin_required", "Origin"),
                    decode("attestation_object_required", "Invalid attestation object", request.attestationObject()),
                    decode("client_data_required", "Invalid clientDataJSON", request.clientDataJson()),
                    decode("expected_challenge_required", "Invalid challenge", request.expectedChallenge()),
                    anchorResolution.anchors(),
                    anchorResolution.cached(),
                    anchorResolution.source(),
                    anchorResolution.metadataEntryId(),
                    anchorResolution.warnings());

            addStep(trace, step -> step.id("prepare.replay")
                    .summary("Prepare attestation replay command")
                    .detail("ReplayCommand.Inline")
                    .attribute("anchorSource", anchorResolution.source())
                    .attribute("warnings", anchorResolution.warnings().size()));

            try {
                result = replayService.replay(inlineCommand);
                if (verbose) {
                    try {
                        VerificationCommand.Inline verificationCommand = toVerificationCommand(inlineCommand);
                        verificationTrace = verificationService
                                .verify(verificationCommand, true)
                                .verboseTrace()
                                .orElse(null);
                    } catch (Exception ex) {
                        addStep(trace, step -> step.id("verification.trace.error")
                                .summary("Unable to generate verification trace")
                                .detail(ex.getClass().getSimpleName())
                                .note("message", sanitize(ex.getMessage())));
                    }
                }
            } catch (IllegalArgumentException ex) {
                addStep(trace, step -> step.id("replay.failure")
                        .summary("Attestation replay failed")
                        .detail("WebAuthnAttestationReplayApplicationService.replay")
                        .note("message", ex.getMessage()));
                throw validation(
                        "replay_invalid",
                        ex.getMessage() == null ? "Attestation replay failed" : ex.getMessage(),
                        Map.of("attestationId", inlineCommand.attestationId(), "format", format.label()),
                        buildTrace(trace));
            } catch (Exception ex) {
                addStep(trace, step -> step.id("replay.error")
                        .summary("Unexpected error during attestation replay")
                        .detail(ex.getClass().getName())
                        .note("message", ex.getMessage()));
                throw unexpected(
                        "replay_failed",
                        "Attestation replay failed: " + sanitize(ex.getMessage()),
                        Map.of("attestationId", inlineCommand.attestationId(), "format", format.label()),
                        buildTrace(trace));
            }
        }

        appendTrace(trace, verificationTrace);
        var replayTelemetry = result.telemetry();
        Optional<WebAuthnAttestationReplayApplicationService.AttestedCredential> attestedCredential =
                result.attestedCredential();
        addStep(trace, step -> step.id("verify.attestation")
                .summary("Verify attestation")
                .detail("WebAuthnAttestationReplayApplicationService.replay")
                .attribute("status", replayTelemetry.status().name()));

        WebAuthnAttestationReplayApplicationService.TelemetrySignal telemetry = replayTelemetry;
        String telemetryId = nextTelemetryId(EVENT_ATTEST_REPLAY);
        TelemetryFrame frame = telemetry.emit(TelemetryContracts.fido2AttestReplayAdapter(), telemetryId);
        logTelemetry(frame, EVENT_ATTEST_REPLAY);
        Map<String, Object> metadataFields = Map.copyOf(frame.fields());

        if (telemetry.status() == WebAuthnAttestationReplayApplicationService.TelemetryStatus.SUCCESS) {
            WebAuthnAttestationReplayApplicationService.AttestedCredential credential =
                    attestedCredential.orElseThrow(() -> unexpected(
                            "attested_credential_missing",
                            "Attestation replay succeeded without credential metadata",
                            metadataFields,
                            buildTrace(trace)));
            WebAuthnAttestedCredential responseCredential = new WebAuthnAttestedCredential(
                    credential.relyingPartyId(),
                    credential.credentialId(),
                    credential.algorithm().label().toLowerCase(Locale.ROOT),
                    credential.userVerificationRequired(),
                    credential.signatureCounter(),
                    credential.aaguid());
            WebAuthnAttestationMetadata metadata = WebAuthnAttestationMetadata.forReplay(
                    telemetryId, telemetry.reasonCode(), format.label(), metadataFields, result.anchorWarnings());
            VerboseTrace builtTrace = buildTrace(trace);
            VerboseTracePayload tracePayload = builtTrace == null ? null : VerboseTracePayload.from(builtTrace);
            return new WebAuthnAttestationResponse("success", null, responseCredential, metadata, tracePayload);
        }

        if (telemetry.status() == WebAuthnAttestationReplayApplicationService.TelemetryStatus.INVALID) {
            String message = Optional.ofNullable(telemetry.reason())
                    .filter(str -> !str.isBlank())
                    .orElse("Attestation replay invalid");
            addStep(trace, step -> step.id("replay.invalid")
                    .summary("Attestation replay invalid")
                    .detail("Telemetry status")
                    .note("reason", message));
            throw validation(telemetry.reasonCode(), message, metadataFields, buildTrace(trace));
        }

        throw unexpected(
                telemetry.reasonCode(),
                Optional.ofNullable(telemetry.reason()).orElse("Attestation replay failed"),
                metadataFields,
                buildTrace(trace));
    }

    private static WebAuthnAttestationFormat parseFormat(String format) {
        try {
            return WebAuthnAttestationFormat.fromLabel(
                    requireText(format, "attestation_format_required", "Attestation format"));
        } catch (IllegalArgumentException ex) {
            throw validation(
                    "attestation_format_invalid",
                    "Unsupported attestation format: " + format,
                    Map.of("format", format));
        }
    }

    private static VerboseTrace.Builder newTrace(boolean verbose, String operation) {
        return verbose ? VerboseTrace.builder(operation) : null;
    }

    private static void metadata(VerboseTrace.Builder trace, String key, String value) {
        if (trace != null && value != null && !value.isBlank()) {
            trace.withMetadata(key, value);
        }
    }

    private static void addStep(
            VerboseTrace.Builder trace, java.util.function.Consumer<VerboseTrace.TraceStep.Builder> configurer) {
        if (trace != null) {
            trace.addStep(configurer);
        }
    }

    private static VerboseTrace buildTrace(VerboseTrace.Builder trace) {
        return trace == null ? null : trace.build();
    }

    private static VerificationCommand.Inline toVerificationCommand(ReplayCommand.Inline command) {
        return new VerificationCommand.Inline(
                command.attestationId(),
                command.format(),
                command.relyingPartyId(),
                command.origin(),
                command.attestationObject(),
                command.clientDataJson(),
                command.expectedChallenge(),
                command.trustAnchors(),
                command.trustAnchorsCached(),
                command.trustAnchorSource(),
                command.trustAnchorMetadataEntryId(),
                command.trustAnchorWarnings());
    }

    private static void appendTrace(VerboseTrace.Builder target, VerboseTrace source) {
        if (target == null || source == null) {
            return;
        }
        source.metadata().forEach(target::withMetadata);
        source.steps()
                .forEach(step -> target.addStep(builder -> {
                    builder.id(step.id());
                    if (hasText(step.summary())) {
                        builder.summary(step.summary());
                    }
                    if (hasText(step.detail())) {
                        builder.detail(step.detail());
                    }
                    if (hasText(step.specAnchor())) {
                        builder.spec(step.specAnchor());
                    }
                    step.typedAttributes()
                            .forEach(attribute ->
                                    builder.attribute(attribute.type(), attribute.name(), attribute.value()));
                    step.notes().forEach(builder::note);
                }));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static WebAuthnAttestationGenerator.SigningMode parseSigningMode(String signingMode) {
        String normalized = signingMode.trim().toLowerCase(Locale.ROOT);
        try {
            return switch (normalized) {
                case "self-signed", "self_signed" -> WebAuthnAttestationGenerator.SigningMode.SELF_SIGNED;
                case "unsigned" -> WebAuthnAttestationGenerator.SigningMode.UNSIGNED;
                case "custom-root", "custom_root" -> WebAuthnAttestationGenerator.SigningMode.CUSTOM_ROOT;
                default ->
                    throw new IllegalArgumentException("Unsupported signing mode: "
                            + signingMode
                            + " (expected self-signed, unsigned, or custom-root)");
            };
        } catch (IllegalArgumentException ex) {
            throw validation("signing_mode_invalid", ex.getMessage(), Map.of("signingMode", signingMode));
        }
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private static String mapStoredGenerationReason(String message) {
        String normalized = sanitize(message).toLowerCase(Locale.ROOT);
        if (normalized.contains("not found")) {
            return "stored_credential_not_found";
        }
        if (normalized.contains("metadata")) {
            return "stored_attestation_required";
        }
        if (normalized.contains("relying party")) {
            return "stored_relying_party_mismatch";
        }
        return "generation_failed";
    }

    private static String mapStoredReplayReason(String message) {
        String normalized = sanitize(message).toLowerCase(Locale.ROOT);
        if (normalized.contains("not found")) {
            return "stored_credential_not_found";
        }
        if (normalized.contains("attestation metadata")) {
            return "stored_attestation_required";
        }
        if (normalized.contains("missing required attribute")) {
            return "stored_attestation_missing_attribute";
        }
        if (normalized.contains("base64")) {
            return "stored_attestation_invalid";
        }
        return "replay_invalid";
    }

    private enum InputSource {
        PRESET,
        MANUAL,
        STORED
    }

    private static InputSource parseInputSource(String input) {
        if (input == null || input.isBlank()) {
            return InputSource.PRESET;
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "manual" -> InputSource.MANUAL;
            case "preset" -> InputSource.PRESET;
            case "stored" -> InputSource.STORED;
            default ->
                throw validation(
                        "input_source_invalid",
                        "Unsupported input source: " + input,
                        Map.of("inputSource", input),
                        Map.of(),
                        null);
        };
    }

    private static String requireText(String value, String reasonCode, String fieldDescription) {
        if (value == null || value.isBlank()) {
            throw validation(reasonCode, fieldDescription + " is required", Map.of());
        }
        return value.trim();
    }

    private static byte[] decode(String reasonCode, String message, String value) {
        try {
            return URL_DECODER.decode(requireText(value, reasonCode, message));
        } catch (IllegalArgumentException ex) {
            throw validation(reasonCode, message, Map.of());
        }
    }

    private static String nextTelemetryId(String event) {
        return event + "-" + UUID.randomUUID();
    }

    private void logTelemetry(TelemetryFrame frame, String event) {
        if (TELEMETRY_LOGGER.isLoggable(Level.INFO)) {
            TELEMETRY_LOGGER.log(Level.INFO, () -> event + " " + frame.fields());
        }
    }

    private void logTrustAnchorWarnings(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return;
        }
        if (TELEMETRY_LOGGER.isLoggable(Level.WARNING)) {
            warnings.stream()
                    .filter(message -> message != null && !message.isBlank())
                    .forEach(message -> TELEMETRY_LOGGER.log(Level.WARNING, message));
        }
    }

    private static WebAuthnAttestationValidationException validation(
            String reasonCode, String message, Map<String, Object> metadata) {
        return validation(reasonCode, message, Map.of(), metadata, null);
    }

    private static WebAuthnAttestationValidationException validation(
            String reasonCode, String message, Map<String, Object> details, VerboseTrace trace) {
        return validation(reasonCode, message, details, Map.of(), trace);
    }

    private static WebAuthnAttestationValidationException validation(
            String reasonCode,
            String message,
            Map<String, Object> details,
            Map<String, Object> metadata,
            VerboseTrace trace) {
        return new WebAuthnAttestationValidationException(
                sanitize(reasonCode), sanitize(message), details, metadata, trace);
    }

    private static WebAuthnAttestationUnexpectedException unexpected(
            String reasonCode, String message, Map<String, Object> metadata, VerboseTrace trace) {
        return new WebAuthnAttestationUnexpectedException(sanitize(reasonCode), sanitize(message), metadata, trace);
    }

    private static WebAuthnAttestationVector requireVector(String attestationId, WebAuthnAttestationFormat format) {
        return WebAuthnAttestationFixtures.findById(attestationId)
                .filter(vector -> vector.format() == format)
                .orElseThrow(() -> validation(
                        "unknown_attestation",
                        "Unknown attestation preset: " + attestationId,
                        Map.of("attestationId", attestationId, "format", format.label())));
    }
}
