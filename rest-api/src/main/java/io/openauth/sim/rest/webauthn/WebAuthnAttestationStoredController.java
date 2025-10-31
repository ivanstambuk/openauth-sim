package io.openauth.sim.rest.webauthn;

import io.openauth.sim.application.fido2.WebAuthnMetadataCatalogue;
import io.openauth.sim.application.fido2.WebAuthnTrustAnchorResolver;
import io.openauth.sim.core.fido2.WebAuthnAttestationCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/webauthn/attestations")
final class WebAuthnAttestationStoredController {

    private static final String ATTR_ATTESTATION_OBJECT = "fido2.attestation.stored.attestationObject";
    private static final String ATTR_CLIENT_DATA_JSON = "fido2.attestation.stored.clientDataJson";
    private static final String ATTR_EXPECTED_CHALLENGE = "fido2.attestation.stored.expectedChallenge";

    private final CredentialStore credentialStore;
    private final WebAuthnCredentialPersistenceAdapter persistenceAdapter;
    private final WebAuthnTrustAnchorResolver trustAnchorResolver;
    private static final Logger LOGGER = LoggerFactory.getLogger(WebAuthnAttestationStoredController.class);
    private static final CertificateFactory CERTIFICATE_FACTORY = certificateFactory();
    private static final Map<String, String> METADATA_DESCRIPTIONS = metadataDescriptions();

    WebAuthnAttestationStoredController(
            CredentialStore credentialStore,
            WebAuthnCredentialPersistenceAdapter persistenceAdapter,
            WebAuthnTrustAnchorResolver trustAnchorResolver) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
        this.persistenceAdapter = Objects.requireNonNull(persistenceAdapter, "persistenceAdapter");
        this.trustAnchorResolver = Objects.requireNonNull(trustAnchorResolver, "trustAnchorResolver");
    }

    @GetMapping("/{credentialId}")
    ResponseEntity<StoredAttestationMetadataResponse> metadata(@PathVariable("credentialId") String credentialId) {
        return credentialStore
                .findByName(credentialId)
                .map(credential -> VersionedCredentialRecordMapper.toRecord(credential))
                .map(record -> ResponseEntity.ok(toResponse(record, persistenceAdapter.deserializeAttestation(record))))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stored attestation not found"));
    }

    private StoredAttestationMetadataResponse toResponse(
            io.openauth.sim.core.store.serialization.VersionedCredentialRecord record,
            WebAuthnAttestationCredentialDescriptor descriptor) {
        Map<String, String> attributes = record.attributes();
        return new StoredAttestationMetadataResponse(
                descriptor.name(),
                descriptor.attestationId(),
                descriptor.format().label(),
                descriptor.credentialDescriptor().relyingPartyId(),
                descriptor.origin(),
                descriptor.signingMode().name().toLowerCase(),
                descriptor.certificateChainPem(),
                summarizeTrustAnchors(descriptor),
                attributes.getOrDefault(ATTR_EXPECTED_CHALLENGE, ""),
                attributes.getOrDefault(ATTR_ATTESTATION_OBJECT, ""),
                attributes.getOrDefault(ATTR_CLIENT_DATA_JSON, ""));
    }

    record StoredAttestationMetadataResponse(
            String storedCredentialId,
            String attestationId,
            String format,
            String relyingPartyId,
            String origin,
            String signingMode,
            List<String> certificateChainPem,
            List<String> trustAnchorSummaries,
            String challenge,
            String attestationObject,
            String clientDataJson) {
        // DTO marker
    }

    private List<String> summarizeTrustAnchors(WebAuthnAttestationCredentialDescriptor descriptor) {
        LinkedHashSet<String> summaries = new LinkedHashSet<>();

        WebAuthnTrustAnchorResolver.Resolution resolution =
                trustAnchorResolver.resolve(descriptor.attestationId(), descriptor.format(), List.of(), List.of());
        for (String metadataId : resolution.metadataEntryIds()) {
            String summary = metadataDescription(metadataId)
                    .map(String::trim)
                    .filter(text -> !text.isEmpty())
                    .orElse(metadataId);
            if (!summary.isBlank()) {
                summaries.add(summary);
            }
        }

        if (summaries.isEmpty()) {
            appendCertificateSubjects(summaries, descriptor.certificateChainPem());
            appendCertificateSubjects(summaries, descriptor.customRootCertificatesPem());
        }

        return List.copyOf(summaries);
    }

    private static void appendCertificateSubjects(LinkedHashSet<String> summaries, List<String> pemBlocks) {
        for (String pem : pemBlocks) {
            if (pem == null || pem.isBlank()) {
                continue;
            }
            ByteArrayInputStream inputStream = new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8));
            try {
                Collection<? extends java.security.cert.Certificate> certificates =
                        CERTIFICATE_FACTORY.generateCertificates(inputStream);
                for (java.security.cert.Certificate certificate : certificates) {
                    if (certificate instanceof X509Certificate x509) {
                        summaries.add(extractDisplayName(x509));
                    }
                }
            } catch (CertificateException ex) {
                LOGGER.debug("Unable to parse stored trust anchor certificate: {}", ex.getMessage());
            }
        }
    }

    private static String extractDisplayName(X509Certificate certificate) {
        String subject = certificate.getSubjectX500Principal().getName();
        String commonName = CertificateSubjectFormatter.commonName(subject);
        return commonName != null && !commonName.isBlank() ? commonName : subject;
    }

    private static Map<String, String> metadataDescriptions() {
        Map<String, String> descriptions = new LinkedHashMap<>();
        for (WebAuthnMetadataCatalogue.WebAuthnMetadataEntry entry : WebAuthnMetadataCatalogue.entries()) {
            String key = entry.entryId().toLowerCase(Locale.ROOT);
            descriptions.putIfAbsent(
                    key, Optional.ofNullable(entry.description()).orElse(entry.entryId()));
        }
        return Map.copyOf(descriptions);
    }

    private static Optional<String> metadataDescription(String metadataId) {
        if (metadataId == null || metadataId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(METADATA_DESCRIPTIONS.get(metadataId.toLowerCase(Locale.ROOT)));
    }

    private static CertificateFactory certificateFactory() {
        try {
            return CertificateFactory.getInstance("X.509");
        } catch (CertificateException ex) {
            throw new IllegalStateException("Unable to create certificate factory", ex);
        }
    }

    private static final class CertificateSubjectFormatter {

        private CertificateSubjectFormatter() {
            throw new AssertionError("Utility");
        }

        private static String commonName(String subject) {
            if (subject == null || subject.isBlank()) {
                return null;
            }
            try {
                javax.naming.ldap.LdapName ldapName = new javax.naming.ldap.LdapName(subject);
                for (javax.naming.ldap.Rdn rdn : ldapName.getRdns()) {
                    if ("CN".equalsIgnoreCase(rdn.getType())) {
                        Object value = rdn.getValue();
                        return value == null ? null : value.toString();
                    }
                }
            } catch (javax.naming.InvalidNameException ignored) {
                // Fall back to the full subject string.
            }
            return null;
        }
    }
}
