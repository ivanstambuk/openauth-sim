package io.openauth.sim.application.fido2;

import io.openauth.sim.application.telemetry.Fido2TelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerifier;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.fido2.WebAuthnVerificationError;
import io.openauth.sim.core.trace.VerboseTrace;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Application-layer coordinator for WebAuthn attestation verification telemetry. */
public final class WebAuthnAttestationVerificationApplicationService {

    private final WebAuthnAttestationVerifier verifier;
    private final Fido2TelemetryAdapter telemetryAdapter;

    public WebAuthnAttestationVerificationApplicationService(
            WebAuthnAttestationVerifier verifier, Fido2TelemetryAdapter telemetryAdapter) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.telemetryAdapter = Objects.requireNonNull(telemetryAdapter, "telemetryAdapter");
    }

    public VerificationResult verify(VerificationCommand command) {
        return verify(command, false);
    }

    public VerificationResult verify(VerificationCommand command, boolean verbose) {
        Objects.requireNonNull(command, "command");

        VerboseTrace.Builder trace = newTrace(verbose, "fido2.attestation.verify");
        metadata(trace, "protocol", "FIDO2");
        metadata(trace, "format", command.format().label());
        metadata(trace, "attestationId", command.attestationId());

        addStep(trace, step -> step.id("parse.request")
                .summary("Prepare attestation verification request")
                .detail("VerificationCommand")
                .attribute("relyingPartyId", command.relyingPartyId())
                .attribute("origin", command.origin())
                .attribute("trustAnchors", command.trustAnchors().size())
                .attribute("trustAnchorsCached", command.trustAnchorsCached())
                .attribute("trustAnchorSource", command.trustAnchorSource().name()));

        WebAuthnAttestationServiceSupport.Outcome outcome = WebAuthnAttestationServiceSupport.process(
                verifier,
                command.format(),
                command.attestationId(),
                command.relyingPartyId(),
                command.origin(),
                command.attestationObject(),
                command.clientDataJson(),
                command.expectedChallenge(),
                command.trustAnchors(),
                command.trustAnchorSource(),
                command.trustAnchorsCached(),
                command.trustAnchorMetadataEntryId());

        TelemetrySignal telemetry = new TelemetrySignal(
                toTelemetryStatus(outcome.status()),
                outcome.reasonCode(),
                outcome.reason(),
                true,
                outcome.telemetryFields());

        Optional<AttestedCredential> attestedCredential = outcome.credential()
                .map(data -> new AttestedCredential(
                        data.relyingPartyId(),
                        data.credentialId(),
                        data.algorithm(),
                        data.userVerificationRequired(),
                        outcome.aaguid(),
                        data.signatureCounter()));

        addStep(trace, step -> {
            step.id("verify.attestation")
                    .summary("Verify WebAuthn attestation object")
                    .detail("WebAuthnAttestationServiceSupport.process")
                    .attribute("status", outcome.status().name())
                    .attribute("valid", outcome.success())
                    .attribute("anchorProvided", outcome.anchorProvided())
                    .attribute("selfAttested", outcome.selfAttestedFallback());
            outcome.error().map(Enum::name).ifPresent(err -> step.note("error", err));
        });

        addStep(trace, step -> {
            step.id("assemble.result")
                    .summary("Assemble attestation verification result")
                    .detail("VerificationResult")
                    .attribute("valid", outcome.success())
                    .attribute("anchorMode", outcome.anchorMode())
                    .attribute("attestedCredential", attestedCredential.isPresent());
        });

        return new VerificationResult(
                telemetry,
                outcome.success(),
                outcome.error(),
                attestedCredential,
                outcome.anchorProvided(),
                outcome.selfAttestedFallback(),
                outcome.anchorMode(),
                command.trustAnchorsCached(),
                command.trustAnchorWarnings(),
                buildTrace(trace));
    }

    public sealed interface VerificationCommand permits VerificationCommand.Inline {

        String attestationId();

        WebAuthnAttestationFormat format();

        String relyingPartyId();

        String origin();

        byte[] attestationObject();

        byte[] clientDataJson();

        byte[] expectedChallenge();

        List<X509Certificate> trustAnchors();

        boolean trustAnchorsCached();

        WebAuthnTrustAnchorResolver.Source trustAnchorSource();

        String trustAnchorMetadataEntryId();

        List<String> trustAnchorWarnings();

        record Inline(
                String attestationId,
                WebAuthnAttestationFormat format,
                String relyingPartyId,
                String origin,
                byte[] attestationObject,
                byte[] clientDataJson,
                byte[] expectedChallenge,
                List<X509Certificate> trustAnchors,
                boolean trustAnchorsCached,
                WebAuthnTrustAnchorResolver.Source trustAnchorSource,
                String trustAnchorMetadataEntryId,
                List<String> trustAnchorWarnings)
                implements VerificationCommand {

            public Inline {
                attestationId = sanitize(attestationId);
                format = Objects.requireNonNull(format, "format");
                relyingPartyId = sanitize(relyingPartyId);
                origin = sanitize(origin);
                attestationObject = attestationObject == null ? new byte[0] : attestationObject.clone();
                clientDataJson = clientDataJson == null ? new byte[0] : clientDataJson.clone();
                expectedChallenge = expectedChallenge == null ? new byte[0] : expectedChallenge.clone();
                trustAnchors = List.copyOf(trustAnchors == null ? List.of() : trustAnchors);
                trustAnchorSource = Objects.requireNonNull(trustAnchorSource, "trustAnchorSource");
                trustAnchorMetadataEntryId = trustAnchorMetadataEntryId == null || trustAnchorMetadataEntryId.isBlank()
                        ? null
                        : trustAnchorMetadataEntryId.trim();
                trustAnchorWarnings = List.copyOf(trustAnchorWarnings == null ? List.of() : trustAnchorWarnings);
            }

            private static String sanitize(String value) {
                Objects.requireNonNull(value, "value");
                return value.trim();
            }

            @Override
            public byte[] attestationObject() {
                return attestationObject.clone();
            }

            @Override
            public byte[] clientDataJson() {
                return clientDataJson.clone();
            }

            @Override
            public byte[] expectedChallenge() {
                return expectedChallenge.clone();
            }
        }
    }

    private static VerboseTrace.Builder newTrace(boolean verbose, String operation) {
        return verbose ? VerboseTrace.builder(operation) : null;
    }

    private static void metadata(VerboseTrace.Builder trace, String key, String value) {
        if (trace != null && value != null) {
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

    public record VerificationResult(
            TelemetrySignal telemetry,
            boolean valid,
            Optional<WebAuthnVerificationError> error,
            Optional<AttestedCredential> attestedCredential,
            boolean anchorProvided,
            boolean selfAttestedFallback,
            String anchorMode,
            boolean trustAnchorsCached,
            List<String> anchorWarnings,
            VerboseTrace trace) {

        public VerificationResult {
            telemetry = Objects.requireNonNull(telemetry, "telemetry");
            error = error == null ? Optional.empty() : error;
            attestedCredential = attestedCredential == null ? Optional.empty() : attestedCredential;
            anchorMode = anchorMode == null ? "" : anchorMode;
            anchorWarnings = List.copyOf(anchorWarnings == null ? List.of() : anchorWarnings);
        }

        public Optional<VerboseTrace> verboseTrace() {
            return Optional.ofNullable(trace);
        }
    }

    public record AttestedCredential(
            String relyingPartyId,
            String credentialId,
            WebAuthnSignatureAlgorithm algorithm,
            boolean userVerificationRequired,
            String aaguid,
            long signatureCounter) {

        public AttestedCredential {
            relyingPartyId = sanitize(relyingPartyId);
            credentialId = sanitize(credentialId);
            algorithm = Objects.requireNonNull(algorithm, "algorithm");
            aaguid = aaguid == null ? "" : aaguid.trim();
        }

        private static String sanitize(String value) {
            return value == null ? "" : value.trim();
        }
    }

    public record TelemetrySignal(
            TelemetryStatus status, String reasonCode, String reason, boolean sanitized, Map<String, Object> fields) {

        public TelemetrySignal {
            status = Objects.requireNonNull(status, "status");
            reasonCode = reasonCode == null ? "unspecified" : reasonCode;
            fields = Map.copyOf(new LinkedHashMap<>(fields == null ? Map.of() : fields));
        }

        public TelemetryFrame emit(Fido2TelemetryAdapter adapter, String telemetryId) {
            Objects.requireNonNull(adapter, "adapter");
            Objects.requireNonNull(telemetryId, "telemetryId");
            String eventStatus =
                    switch (status) {
                        case SUCCESS -> "success";
                        case INVALID -> "invalid";
                        case ERROR -> "error";
                    };
            return adapter.status(eventStatus, telemetryId, reasonCode, sanitized, reason, fields);
        }
    }

    public enum TelemetryStatus {
        SUCCESS,
        INVALID,
        ERROR
    }

    public TelemetryFrame emitTelemetry(VerificationResult result, String telemetryId) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(telemetryId, "telemetryId");
        return result.telemetry().emit(telemetryAdapter, telemetryId);
    }

    private static TelemetryStatus toTelemetryStatus(WebAuthnAttestationServiceSupport.Status status) {
        return switch (status) {
            case SUCCESS -> TelemetryStatus.SUCCESS;
            case INVALID -> TelemetryStatus.INVALID;
            case ERROR -> TelemetryStatus.ERROR;
        };
    }
}
