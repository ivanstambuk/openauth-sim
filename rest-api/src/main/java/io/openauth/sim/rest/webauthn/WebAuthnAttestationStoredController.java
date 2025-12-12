package io.openauth.sim.rest.webauthn;

import io.openauth.sim.application.fido2.WebAuthnAttestationStoredMetadataApplicationService;
import io.openauth.sim.application.fido2.WebAuthnAttestationStoredMetadataApplicationService.StoredAttestation;
import io.openauth.sim.application.fido2.WebAuthnMetadataCatalogue;
import io.openauth.sim.application.fido2.WebAuthnTrustAnchorResolver;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
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
import org.springframework.beans.factory.ObjectProvider;
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

    private final WebAuthnAttestationStoredMetadataApplicationService metadataService;
    private final WebAuthnTrustAnchorResolver trustAnchorResolver;
    private static final Logger LOGGER = LoggerFactory.getLogger(WebAuthnAttestationStoredController.class);
    private static final CertificateFactory CERTIFICATE_FACTORY = certificateFactory();
    private static final Map<String, String> METADATA_DESCRIPTIONS = metadataDescriptions();

    WebAuthnAttestationStoredController(
            ObjectProvider<WebAuthnAttestationStoredMetadataApplicationService> metadataServiceProvider,
            WebAuthnTrustAnchorResolver trustAnchorResolver) {
        this.metadataService = metadataServiceProvider.getIfAvailable();
        this.trustAnchorResolver = Objects.requireNonNull(trustAnchorResolver, "trustAnchorResolver");
    }

    @GetMapping("/{credentialId}")
    ResponseEntity<StoredAttestationMetadataResponse> metadata(@PathVariable("credentialId") String credentialId) {
        if (metadataService == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stored attestation not found");
        }
        return metadataService
                .detail(credentialId)
                .map(attestation -> ResponseEntity.ok(toResponse(attestation)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stored attestation not found"));
    }

    private StoredAttestationMetadataResponse toResponse(StoredAttestation attestation) {
        return new StoredAttestationMetadataResponse(
                attestation.storedCredentialId(),
                attestation.attestationId(),
                attestation.format(),
                attestation.relyingPartyId(),
                attestation.origin(),
                attestation.signingMode(),
                attestation.certificateChainPem(),
                summarizeTrustAnchors(attestation),
                attestation.challenge(),
                attestation.attestationObject(),
                attestation.clientDataJson());
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

    private List<String> summarizeTrustAnchors(StoredAttestation attestation) {
        LinkedHashSet<String> summaries = new LinkedHashSet<>();

        WebAuthnAttestationFormat format = WebAuthnAttestationFormat.fromLabel(attestation.format());
        WebAuthnTrustAnchorResolver.Resolution resolution =
                trustAnchorResolver.resolve(attestation.attestationId(), format, List.of(), List.of());
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
            appendCertificateSubjects(summaries, attestation.certificateChainPem());
            appendCertificateSubjects(summaries, attestation.customRootCertificatesPem());
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
