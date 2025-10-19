package io.openauth.sim.application.fido2;

import io.openauth.sim.application.telemetry.Fido2TelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerifier;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.fido2.WebAuthnVerificationError;
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
        Objects.requireNonNull(command, "command");

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

        return new VerificationResult(
                telemetry,
                outcome.success(),
                outcome.error(),
                attestedCredential,
                outcome.anchorProvided(),
                outcome.selfAttestedFallback(),
                outcome.anchorMode(),
                command.trustAnchorsCached(),
                command.trustAnchorWarnings());
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

    public record VerificationResult(
            TelemetrySignal telemetry,
            boolean valid,
            Optional<WebAuthnVerificationError> error,
            Optional<AttestedCredential> attestedCredential,
            boolean anchorProvided,
            boolean selfAttestedFallback,
            String anchorMode,
            boolean trustAnchorsCached,
            List<String> anchorWarnings) {

        public VerificationResult {
            telemetry = Objects.requireNonNull(telemetry, "telemetry");
            error = error == null ? Optional.empty() : error;
            attestedCredential = attestedCredential == null ? Optional.empty() : attestedCredential;
            anchorMode = anchorMode == null ? "" : anchorMode;
            anchorWarnings = List.copyOf(anchorWarnings == null ? List.of() : anchorWarnings);
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
