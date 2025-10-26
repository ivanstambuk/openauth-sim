package io.openauth.sim.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openauth.sim.core.fido2.CborDecoder;
import io.openauth.sim.core.fido2.SignatureInspector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationRequest;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerification;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerifier;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "openauth.sim.persistence.enable-store=false")
@AutoConfigureMockMvc
class Fido2AttestationManualEndpointTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("openauth.sim.persistence.database-path", () -> "unused");
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class CredentialStoreConfiguration {

        @org.springframework.context.annotation.Bean
        io.openauth.sim.core.store.CredentialStore credentialStore() {
            return new InMemoryCredentialStore();
        }
    }

    private static final class InMemoryCredentialStore implements io.openauth.sim.core.store.CredentialStore {
        private final java.util.Map<String, io.openauth.sim.core.model.Credential> backing =
                new java.util.LinkedHashMap<>();

        @Override
        public void save(io.openauth.sim.core.model.Credential credential) {
            backing.put(credential.name(), credential);
        }

        @Override
        public java.util.Optional<io.openauth.sim.core.model.Credential> findByName(String name) {
            return java.util.Optional.ofNullable(backing.get(name));
        }

        @Override
        public java.util.List<io.openauth.sim.core.model.Credential> findAll() {
            return java.util.List.copyOf(backing.values());
        }

        @Override
        public boolean delete(String name) {
            return backing.remove(name) != null;
        }

        @Override
        public void close() {
            backing.clear();
        }
    }

    @Test
    @DisplayName("Manual attestation generation (unsigned) succeeds and emits metadata")
    void manualUnsignedAttestationWorks() throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("inputSource", "MANUAL");
        WebAuthnAttestationVector vector =
                WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
                        .findFirst()
                        .orElseThrow();
        root.put("format", vector.format().label());
        root.put("relyingPartyId", "example.org");
        root.put("origin", "https://example.org");
        root.put("challenge", "dGVzdC1tYW51YWwtY2hhbGxlbmdl");
        root.put("credentialPrivateKey", vector.keyMaterial().credentialPrivateKeyJwk());
        root.put("signingMode", "UNSIGNED");

        String response = mockMvc.perform(post("/api/v1/webauthn/attest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(root)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode json = MAPPER.readTree(response);
        assertThat(json.get("status").asText()).isEqualTo("success");
        JsonNode attestation = json.path("generatedAttestation");
        assertThat(attestation.path("type").asText()).isEqualTo("public-key");
        assertThat(attestation.path("response").path("attestationObject").asText())
                .isNotBlank();
        assertThat(json.path("metadata").path("attestationFormat").asText()).isEqualTo("packed");
        assertThat(json.path("metadata").path("generationMode").asText()).isEqualTo("unsigned");
        assertThat(json.path("metadata").path("inputSource").asText()).isEqualTo("manual");
    }

    @Test
    @DisplayName("Manual attestation generation (custom-root) includes chain and telemetry")
    void manualCustomRootAttestationWorks() throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("inputSource", "MANUAL");
        WebAuthnAttestationVector vector =
                WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.FIDO_U2F).stream()
                        .findFirst()
                        .orElseThrow();
        root.put("format", vector.format().label());
        root.put("relyingPartyId", "example.org");
        root.put("origin", "https://example.org");
        root.put("challenge", "dGVzdC1tYW51YWwtY2hhbGxlbmdl");
        root.put("credentialPrivateKey", vector.keyMaterial().credentialPrivateKeyJwk());
        root.put("attestationPrivateKey", vector.keyMaterial().attestationPrivateKeyJwk());
        root.put("attestationCertificateSerial", vector.keyMaterial().attestationCertificateSerialBase64Url());
        root.put("signingMode", "CUSTOM_ROOT");
        List<String> chain = certificateChainPem(vector);
        root.putArray("customRootCertificates").add(chain.get(chain.size() - 1));

        String response = mockMvc.perform(post("/api/v1/webauthn/attest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(root)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode json = MAPPER.readTree(response);
        assertThat(json.get("status").asText()).isEqualTo("success");
        assertThat(json.path("metadata").path("generationMode").asText()).isEqualTo("custom_root");
        assertThat(json.path("metadata").path("customRootCount").asInt()).isEqualTo(1);
        assertThat(json.path("metadata").path("inputSource").asText()).isEqualTo("manual");
    }

    @Test
    @DisplayName("Manual attestation generation returns verbose trace when requested")
    void manualAttestationReturnsVerboseTrace() throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("inputSource", "MANUAL");
        WebAuthnAttestationVector vector =
                WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
                        .findFirst()
                        .orElseThrow();
        root.put("format", vector.format().label());
        root.put("relyingPartyId", "example.org");
        root.put("origin", "https://example.org");
        root.put("challenge", "dGVzdC1tYW51YWwtY2hhbGxlbmdl");
        root.put("credentialPrivateKey", vector.keyMaterial().credentialPrivateKeyJwk());
        root.put("signingMode", "UNSIGNED");
        root.put("verbose", true);

        String response = mockMvc.perform(post("/api/v1/webauthn/attest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(root)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode json = MAPPER.readTree(response);
        JsonNode trace = json.get("trace");
        assertThat(trace).isNotNull();
        assertThat(trace.get("operation").asText()).isEqualTo("fido2.attestation.generate");

        JsonNode metadata = trace.get("metadata");
        assertThat(metadata.get("format").asText()).isEqualTo("packed");
        assertThat(metadata.get("tier").asText()).isEqualTo("educational");

        Map<String, String> generateAttributes = orderedAttributes(step(trace, "generate.attestation"));
        assertThat(generateAttributes).containsEntry("signingMode", "UNSIGNED");
    }

    @Test
    @DisplayName("Attestation replay returns verbose trace when requested")
    void attestationReplayReturnsVerboseTrace() throws Exception {
        WebAuthnAttestationVector vector =
                WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
                        .findFirst()
                        .orElseThrow();

        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("attestationId", vector.vectorId());
        payload.put("format", vector.format().label());
        payload.put("relyingPartyId", vector.relyingPartyId());
        payload.put("origin", vector.origin());
        payload.put(
                "attestationObject",
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(vector.registration().attestationObject()));
        payload.put(
                "clientDataJson",
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(vector.registration().clientDataJson()));
        payload.put(
                "expectedChallenge",
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(vector.registration().challenge()));
        List<String> anchors = certificateChainPem(vector);
        if (!anchors.isEmpty()) {
            var array = payload.putArray("trustAnchors");
            anchors.forEach(array::add);
        }
        payload.put("verbose", true);

        String response = mockMvc.perform(post("/api/v1/webauthn/attest/replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode json = MAPPER.readTree(response);
        JsonNode trace = json.get("trace");
        assertThat(trace).isNotNull();
        assertThat(trace.get("operation").asText()).isEqualTo("fido2.attestation.verify");

        JsonNode metadata = trace.get("metadata");
        assertThat(metadata.get("format").asText()).isEqualTo("packed");
        assertThat(metadata.get("tier").asText()).isEqualTo("educational");

        Map<String, String> clientData = orderedAttributes(step(trace, "parse.clientData"));
        assertThat(clientData)
                .containsKey("expected.type")
                .containsKey("type.match")
                .containsKey("origin.expected")
                .containsKey("origin.match")
                .containsKey("challenge.b64u")
                .containsEntry("tokenBinding.status", "not_present")
                .containsEntry("tokenBinding.id", "");

        Map<String, String> authenticator = orderedAttributes(step(trace, "parse.authenticatorData"));
        assertThat(authenticator)
                .containsKey("rpIdHash.hex")
                .containsKey("rpIdHash.expected")
                .containsKey("rpIdHash.match")
                .containsKey("flags.bits.UP")
                .containsKey("flags.bits.ED");

        Map<String, String> anchorAttributes = orderedAttributes(step(trace, "resolve.trustAnchors"));
        assertThat(anchorAttributes).containsEntry("anchorCount", "1");

        Map<String, String> prepareAttributes = orderedAttributes(step(trace, "prepare.replay"));
        assertThat(prepareAttributes).containsEntry("anchorSource", "COMBINED");

        Map<String, String> signatureBase = orderedAttributes(step(trace, "build.signatureBase"));
        assertThat(signatureBase).containsKey("signedBytes.preview");

        Map<String, String> verifyAttributes = orderedAttributes(step(trace, "verify.attestation"));
        assertThat(verifyAttributes).containsEntry("status", "SUCCESS");

        Map<String, String> verifySignature = orderedAttributes(step(trace, "verify.signature"));
        byte[] attestationSignature =
                extractAttestationSignature(vector.registration().attestationObject());
        WebAuthnSignatureAlgorithm algorithm = vector.algorithm();
        SignatureInspector.SignatureDetails signatureDetails =
                SignatureInspector.inspect(algorithm, attestationSignature);
        String signaturePrefix = signatureDetails.ecdsa().isPresent() ? "sig.der" : "sig.raw";

        assertThat(verifySignature)
                .containsEntry("alg", algorithm.name())
                .containsEntry("cose.alg", String.valueOf(algorithm.coseIdentifier()))
                .containsEntry("cose.alg.name", algorithm.name())
                .containsEntry("valid", "true")
                .containsEntry("verify.ok", "true")
                .containsEntry("policy.lowS.enforced", "false")
                .containsEntry(signaturePrefix + ".b64u", signatureDetails.base64Url())
                .containsEntry(signaturePrefix + ".len", String.valueOf(signatureDetails.length()));

        signatureDetails.ecdsa().ifPresent(ecdsa -> assertThat(verifySignature)
                .containsEntry("ecdsa.r.hex", ecdsa.rHex())
                .containsEntry("ecdsa.s.hex", ecdsa.sHex())
                .containsEntry("ecdsa.lowS", String.valueOf(ecdsa.lowS())));
    }

    private static JsonNode step(JsonNode trace, String id) {
        for (JsonNode step : trace.withArray("steps")) {
            if (id.equals(step.get("id").asText())) {
                return step;
            }
        }
        throw new AssertionError("Missing trace step: " + id);
    }

    private static Map<String, String> orderedAttributes(JsonNode step) {
        Map<String, String> attributes = new LinkedHashMap<>();
        for (JsonNode attribute : step.withArray("orderedAttributes")) {
            attributes.put(
                    attribute.get("name").asText(), attribute.get("value").asText());
        }
        return attributes;
    }

    private static byte[] extractAttestationSignature(byte[] attestationObject) {
        try {
            Object decoded = CborDecoder.decode(attestationObject);
            if (!(decoded instanceof Map<?, ?> root)) {
                throw new IllegalStateException("Attestation object must be a CBOR map");
            }
            Object attStmtObject = root.get("attStmt");
            if (!(attStmtObject instanceof Map<?, ?> attStmt)) {
                throw new IllegalStateException("Attestation statement missing");
            }
            Object signature = attStmt.get("sig");
            if (signature instanceof byte[] bytes) {
                return bytes;
            }
            throw new IllegalStateException("Attestation statement missing signature");
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to decode attestation object", ex);
        }
    }

    private static List<String> certificateChainPem(WebAuthnAttestationVector vector) {
        WebAuthnAttestationVerifier verifier = new WebAuthnAttestationVerifier();
        WebAuthnAttestationVerification verification = verifier.verify(new WebAuthnAttestationRequest(
                vector.format(),
                vector.registration().attestationObject(),
                vector.registration().clientDataJson(),
                vector.registration().challenge(),
                vector.relyingPartyId(),
                vector.origin()));
        if (!verification.result().success()) {
            throw new IllegalStateException("Attestation verification failed for vector "
                    + vector.vectorId()
                    + ": "
                    + verification.result().message());
        }
        return verification.certificateChain().stream()
                .map(certificate -> {
                    try {
                        return toPem(certificate);
                    } catch (CertificateEncodingException ex) {
                        throw new IllegalStateException("Unable to encode certificate", ex);
                    }
                })
                .toList();
    }

    private static String toPem(X509Certificate certificate) throws CertificateEncodingException {
        String encoded = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(certificate.getEncoded());
        return "-----BEGIN CERTIFICATE-----\n" + encoded + "\n-----END CERTIFICATE-----\n";
    }
}
