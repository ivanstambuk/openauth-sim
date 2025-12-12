package io.openauth.sim.application.fido2;

import io.openauth.sim.application.telemetry.Fido2TelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.fido2.WebAuthnAttestationCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerifier;
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.fido2.WebAuthnVerificationError;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Application-level coordinator for WebAuthn attestation replay telemetry. */
public final class WebAuthnAttestationReplayApplicationService {

    private final WebAuthnAttestationVerifier verifier;
    private final Fido2TelemetryAdapter telemetryAdapter;
    private final CredentialStore credentialStore;
    private final WebAuthnCredentialPersistenceAdapter persistenceAdapter;
    private static final String ATTR_ATTESTATION_ENABLED_KEY = "fido2.attestation.enabled";

    public WebAuthnAttestationReplayApplicationService(
            WebAuthnAttestationVerifier verifier,
            Fido2TelemetryAdapter telemetryAdapter,
            CredentialStore credentialStore,
            WebAuthnCredentialPersistenceAdapter persistenceAdapter) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.telemetryAdapter = Objects.requireNonNull(telemetryAdapter, "telemetryAdapter");
        if ((credentialStore == null) != (persistenceAdapter == null)) {
            throw new IllegalArgumentException(
                    "credentialStore and persistenceAdapter must both be provided or both be null");
        }
        this.credentialStore = credentialStore;
        this.persistenceAdapter = persistenceAdapter;
    }

    public WebAuthnAttestationReplayApplicationService(
            WebAuthnAttestationVerifier verifier, Fido2TelemetryAdapter telemetryAdapter) {
        this(verifier, telemetryAdapter, null, null);
    }

    public WebAuthnAttestationReplayApplicationService() {
        this(new WebAuthnAttestationVerifier(), new Fido2TelemetryAdapter("fido2.attestReplay"), null, null);
    }

    /** Convenience factory for stored-replay facades that should not instantiate core collaborators. */
    public static WebAuthnAttestationReplayApplicationService usingDefaults(
            CredentialStore credentialStore, Fido2TelemetryAdapter telemetryAdapter) {
        return new WebAuthnAttestationReplayApplicationService(
                new WebAuthnAttestationVerifier(),
                telemetryAdapter,
                credentialStore,
                new WebAuthnCredentialPersistenceAdapter());
    }

    public ReplayResult replay(ReplayCommand command) {
        Objects.requireNonNull(command, "command");

        String telemetryInputSource = "inline";
        String telemetryStoredCredentialId = null;

        if (command instanceof ReplayCommand.Stored stored) {
            if (credentialStore == null || persistenceAdapter == null) {
                throw new IllegalStateException("Stored attestation replay requires a credential store");
            }

            Credential credential = credentialStore
                    .findByName(stored.credentialName())
                    .orElseThrow(() -> new IllegalArgumentException("Stored credential not found"));

            var record = VersionedCredentialRecordMapper.toRecord(credential);
            if (!"true".equals(record.attributes().get(ATTR_ATTESTATION_ENABLED_KEY))) {
                throw new IllegalArgumentException("Stored credential does not contain attestation metadata");
            }

            WebAuthnAttestationCredentialDescriptor descriptor = persistenceAdapter.deserializeAttestation(record);

            byte[] attestationObject =
                    decodeBase64Attribute(record.attributes(), "fido2.attestation.stored.attestationObject");
            byte[] clientDataJson =
                    decodeBase64Attribute(record.attributes(), "fido2.attestation.stored.clientDataJson");
            byte[] expectedChallenge =
                    decodeBase64Attribute(record.attributes(), "fido2.attestation.stored.expectedChallenge");

            List<X509Certificate> trustAnchors = decodeCertificates(descriptor.certificateChainPem());

            ReplayCommand.Inline inline = new ReplayCommand.Inline(
                    descriptor.attestationId(),
                    descriptor.format(),
                    descriptor.credentialDescriptor().relyingPartyId(),
                    descriptor.origin(),
                    attestationObject,
                    clientDataJson,
                    expectedChallenge,
                    trustAnchors,
                    false,
                    WebAuthnTrustAnchorResolver.Source.MANUAL,
                    List.of(),
                    List.of());

            command = inline;
            telemetryInputSource = "stored";
            telemetryStoredCredentialId = descriptor.name();
        }

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
                command.trustAnchorMetadataEntryIds());

        Map<String, Object> telemetryFields = new LinkedHashMap<>(outcome.telemetryFields());
        telemetryFields.put("inputSource", telemetryInputSource);
        if (telemetryStoredCredentialId != null) {
            telemetryFields.put("storedCredentialId", telemetryStoredCredentialId);
        }

        TelemetrySignal telemetry = new TelemetrySignal(
                toTelemetryStatus(outcome.status()), outcome.reasonCode(), outcome.reason(), true, telemetryFields);

        Optional<AttestedCredential> attestedCredential = outcome.credential()
                .map(data -> new AttestedCredential(
                        data.relyingPartyId(),
                        data.credentialId(),
                        data.algorithm(),
                        data.userVerificationRequired(),
                        outcome.aaguid(),
                        data.signatureCounter()));

        return new ReplayResult(
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

    public sealed interface ReplayCommand permits ReplayCommand.Inline, ReplayCommand.Stored {

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

        List<String> trustAnchorMetadataEntryIds();

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
                List<String> trustAnchorMetadataEntryIds,
                List<String> trustAnchorWarnings)
                implements ReplayCommand {

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
                trustAnchorMetadataEntryIds =
                        trustAnchorMetadataEntryIds == null ? List.of() : List.copyOf(trustAnchorMetadataEntryIds);
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

        record Stored(String credentialName, WebAuthnAttestationFormat format) implements ReplayCommand {

            public Stored {
                credentialName =
                        Objects.requireNonNull(credentialName, "credentialName").trim();
                if (credentialName.isEmpty()) {
                    throw new IllegalArgumentException("credentialName must not be blank");
                }
                format = Objects.requireNonNull(format, "format");
            }

            @Override
            public String attestationId() {
                return credentialName;
            }

            @Override
            public WebAuthnAttestationFormat format() {
                return format;
            }

            @Override
            public String relyingPartyId() {
                return "";
            }

            @Override
            public String origin() {
                return "";
            }

            @Override
            public byte[] attestationObject() {
                return new byte[0];
            }

            @Override
            public byte[] clientDataJson() {
                return new byte[0];
            }

            @Override
            public byte[] expectedChallenge() {
                return new byte[0];
            }

            @Override
            public List<X509Certificate> trustAnchors() {
                return List.of();
            }

            @Override
            public boolean trustAnchorsCached() {
                return false;
            }

            @Override
            public WebAuthnTrustAnchorResolver.Source trustAnchorSource() {
                return WebAuthnTrustAnchorResolver.Source.NONE;
            }

            @Override
            public List<String> trustAnchorMetadataEntryIds() {
                return List.of();
            }

            @Override
            public List<String> trustAnchorWarnings() {
                return List.of();
            }
        }
    }

    public record ReplayResult(
            TelemetrySignal telemetry,
            boolean valid,
            Optional<WebAuthnVerificationError> error,
            Optional<AttestedCredential> attestedCredential,
            boolean anchorProvided,
            boolean selfAttestedFallback,
            String anchorMode,
            boolean trustAnchorsCached,
            List<String> anchorWarnings) {

        public ReplayResult {
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

    public TelemetryFrame emitTelemetry(ReplayResult result, String telemetryId) {
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

    private static byte[] decodeBase64Attribute(Map<String, String> attributes, String key) {
        String value = attributes.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Stored attestation is missing required attribute: " + key);
        }
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Stored attestation attribute " + key + " must be Base64URL encoded", ex);
        }
    }

    private static List<X509Certificate> decodeCertificates(List<String> pemCertificates) {
        if (pemCertificates == null || pemCertificates.isEmpty()) {
            return List.of();
        }
        CertificateFactory factory;
        try {
            factory = CertificateFactory.getInstance("X.509");
        } catch (CertificateException ex) {
            throw new IllegalStateException("Unable to create certificate factory", ex);
        }
        List<X509Certificate> certificates = new ArrayList<>();
        for (String pem : pemCertificates) {
            if (pem == null || pem.trim().isEmpty()) {
                continue;
            }
            try {
                ByteArrayInputStream input = new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII));
                certificates.add((X509Certificate) factory.generateCertificate(input));
            } catch (CertificateException ex) {
                throw new IllegalArgumentException("Unable to parse stored attestation certificate chain", ex);
            }
        }
        return List.copyOf(certificates);
    }
}
