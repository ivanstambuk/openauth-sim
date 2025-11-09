package io.openauth.sim.rest.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openauth.sim.application.fido2.WebAuthnAttestationSamples;
import io.openauth.sim.application.fido2.WebAuthnMetadataCatalogue;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/ui")
final class OperatorConsoleController {

    private static final String REST_EVALUATION_PATH = "/api/v1/ocra/evaluate";
    private static final String CSRF_ATTRIBUTE = "operator-console-csrf-token";
    private static final String REST_VERIFICATION_PATH = "/api/v1/ocra/verify";
    private static final Set<String> SUPPORTED_PROTOCOLS =
            Set.of("ocra", "hotp", "totp", "fido2", "emv", "eudi-openid4vp", "eudi-iso-18013-5", "eudi-siopv2");
    private static final Map<String, String> PROTOCOL_ALIASES = Map.of("eudiw", "eudi-openid4vp");

    private static final Base64.Encoder BASE64_URL_ENCODER =
            Base64.getUrlEncoder().withoutPadding();

    private final ObjectMapper objectMapper;
    private final OperatorConsoleTelemetryLogger telemetry;

    OperatorConsoleController(ObjectMapper objectMapper, OperatorConsoleTelemetryLogger telemetry) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
    }

    @ModelAttribute("form")
    OcraEvaluationForm formModel() {
        return new OcraEvaluationForm();
    }

    @GetMapping("/ocra")
    String landingPage() {
        return "redirect:/ui/console";
    }

    @GetMapping("/console")
    String unifiedConsole(@ModelAttribute("form") OcraEvaluationForm form, HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(true);
        EudiwOperatorConsoleData.Snapshot eudiwData = EudiwOperatorConsoleData.snapshot();
        model.addAttribute("csrfToken", ensureCsrfToken(session));
        model.addAttribute("activeProtocol", determineProtocol(request.getParameter("protocol")));
        model.addAttribute("initialEudiwTab", request.getParameter("tab"));
        model.addAttribute("initialEudiwMode", request.getParameter("mode"));
        model.addAttribute("evaluationEndpoint", REST_EVALUATION_PATH);
        model.addAttribute("verificationEndpoint", REST_VERIFICATION_PATH);
        model.addAttribute("credentialsEndpoint", "/api/v1/ocra/credentials");
        model.addAttribute("credentialSampleEndpoint", "/api/v1/ocra/credentials/{credentialId}/sample");
        model.addAttribute("seedEndpoint", "/api/v1/ocra/credentials/seed");
        model.addAttribute("hotpStoredEvaluateEndpoint", "/api/v1/hotp/evaluate");
        model.addAttribute("hotpInlineEvaluateEndpoint", "/api/v1/hotp/evaluate/inline");
        model.addAttribute("hotpCredentialsEndpoint", "/api/v1/hotp/credentials");
        model.addAttribute("hotpCredentialSampleEndpoint", "/api/v1/hotp/credentials");
        model.addAttribute("hotpSeedEndpoint", "/api/v1/hotp/credentials/seed");
        model.addAttribute("hotpSeedDefinitionsJson", serializeHotpSeedDefinitions());
        model.addAttribute("hotpInlinePresetsJson", serializeHotpInlinePresets());
        model.addAttribute("hotpReplayEndpoint", "/api/v1/hotp/replay");
        model.addAttribute("totpStoredEvaluateEndpoint", "/api/v1/totp/evaluate");
        model.addAttribute("totpInlineEvaluateEndpoint", "/api/v1/totp/evaluate/inline");
        model.addAttribute("totpCredentialsEndpoint", "/api/v1/totp/credentials");
        model.addAttribute("totpSeedEndpoint", "/api/v1/totp/credentials/seed");
        model.addAttribute("totpSeedDefinitionsJson", serializeTotpSeedDefinitions());
        model.addAttribute("totpCredentialSampleEndpoint", "/api/v1/totp/credentials");
        model.addAttribute("totpInlinePresetsJson", serializeTotpInlinePresets());
        model.addAttribute("totpReplayEndpoint", "/api/v1/totp/replay");
        model.addAttribute("eudiwRequestEndpoint", "/api/v1/eudiw/openid4vp/requests");
        model.addAttribute("eudiwWalletEndpoint", "/api/v1/eudiw/openid4vp/wallet/simulate");
        model.addAttribute("eudiwValidateEndpoint", "/api/v1/eudiw/openid4vp/validate");
        model.addAttribute("eudiwSeedEndpoint", "/api/v1/eudiw/openid4vp/presentations/seed");
        model.addAttribute("eudiwConsoleData", eudiwData);
        model.addAttribute("eudiwConsoleDataJson", serializeEudiwConsoleData(eudiwData));
        model.addAttribute("emvEvaluateEndpoint", "/api/v1/emv/cap/evaluate");
        model.addAttribute("emvStoredEvaluateEndpoint", "/api/v1/emv/cap/evaluate");
        model.addAttribute("emvReplayEndpoint", "/api/v1/emv/cap/replay");
        model.addAttribute("emvCredentialsEndpoint", "/api/v1/emv/cap/credentials");
        model.addAttribute("emvSeedEndpoint", "/api/v1/emv/cap/credentials/seed");
        model.addAttribute("emvSeedDefinitionsJson", serializeEmvSeedDefinitions());
        model.addAttribute("fido2StoredEvaluateEndpoint", "/api/v1/webauthn/evaluate");
        model.addAttribute("fido2InlineEvaluateEndpoint", "/api/v1/webauthn/evaluate/inline");
        model.addAttribute("fido2ReplayEndpoint", "/api/v1/webauthn/replay");
        model.addAttribute("fido2CredentialsEndpoint", "/api/v1/webauthn/credentials");
        model.addAttribute("fido2StoredSampleEndpoint", "/api/v1/webauthn/credentials/{credentialId}/sample");
        model.addAttribute("fido2SeedEndpoint", "/api/v1/webauthn/credentials/seed");
        model.addAttribute("fido2SeedDefinitionsJson", serializeFido2SeedDefinitions());
        model.addAttribute("fido2InlineVectorsJson", serializeFido2InlineVectors());
        model.addAttribute("fido2InlineVectors", Fido2OperatorSampleData.inlineVectors());
        model.addAttribute("fido2AttestationVectorsJson", serializeFido2AttestationVectors());
        model.addAttribute("fido2AttestationMetadataEntriesJson", serializeFido2MetadataEntries());
        model.addAttribute("fido2AttestationEndpoint", "/api/v1/webauthn/attest");
        model.addAttribute("fido2AttestationReplayEndpoint", "/api/v1/webauthn/attest/replay");
        model.addAttribute("fido2AttestationSeedEndpoint", "/api/v1/webauthn/attestations/seed");
        model.addAttribute("fido2AttestationMetadataEndpoint", "/api/v1/webauthn/attestations/{credentialId}");
        model.addAttribute("telemetryEndpoint", "/ui/console/replay/telemetry");
        populatePolicyPresets(model);
        return "ui/console/index";
    }

    private String determineProtocol(String requested) {
        if (!StringUtils.hasText(requested)) {
            return "ocra";
        }
        String normalised = requested.trim().toLowerCase();
        if (PROTOCOL_ALIASES.containsKey(normalised)) {
            return PROTOCOL_ALIASES.get(normalised);
        }
        return SUPPORTED_PROTOCOLS.contains(normalised) ? normalised : "ocra";
    }

    private String serializeHotpSeedDefinitions() {
        try {
            return objectMapper.writeValueAsString(HotpOperatorSampleData.seedDefinitions());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to render HOTP seed definitions", ex);
        }
    }

    private String serializeHotpInlinePresets() {
        try {
            return objectMapper.writeValueAsString(HotpOperatorSampleData.inlinePresets());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to render HOTP inline presets", ex);
        }
    }

    private String serializeTotpSeedDefinitions() {
        try {
            return objectMapper.writeValueAsString(TotpOperatorSampleData.seedDefinitions());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to render TOTP seed definitions", ex);
        }
    }

    private String serializeTotpInlinePresets() {
        try {
            return objectMapper.writeValueAsString(TotpOperatorSampleData.inlinePresets());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to render TOTP inline presets", ex);
        }
    }

    private String serializeEudiwConsoleData(EudiwOperatorConsoleData.Snapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to render EUDIW console data", ex);
        }
    }

    private String serializeEmvSeedDefinitions() {
        try {
            return objectMapper.writeValueAsString(EmvCapOperatorSampleData.seedDefinitions());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to render EMV/CAP seed definitions", ex);
        }
    }

    private String serializeFido2SeedDefinitions() {
        try {
            return objectMapper.writeValueAsString(Fido2OperatorSampleData.seedDefinitions());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to render FIDO2 seed definitions", ex);
        }
    }

    private String serializeFido2InlineVectors() {
        try {
            return objectMapper.writeValueAsString(Fido2OperatorSampleData.inlineVectors());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to render FIDO2 inline vectors", ex);
        }
    }

    private String serializeFido2AttestationVectors() {
        try {
            List<Map<String, Object>> payload = WebAuthnAttestationSamples.vectors().stream()
                    .map(OperatorConsoleController::describeAttestationVector)
                    .toList();
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to render FIDO2 attestation vectors", ex);
        }
    }

    private String serializeFido2MetadataEntries() {
        try {
            List<Map<String, Object>> payload = WebAuthnMetadataCatalogue.entries().stream()
                    .map(OperatorConsoleController::describeMetadataEntry)
                    .toList();
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to render FIDO2 metadata entries", ex);
        }
    }

    private static Map<String, Object> describeAttestationVector(WebAuthnAttestationVector vector) {
        Map<String, Object> descriptor = new java.util.LinkedHashMap<>();
        descriptor.put("vectorId", vector.vectorId());
        descriptor.put("format", vector.format().label());
        descriptor.put("algorithm", vector.algorithm().label());
        descriptor.put("w3cSection", vector.w3cSection());
        descriptor.put("title", vector.title());
        descriptor.put("relyingPartyId", vector.relyingPartyId());
        descriptor.put("origin", vector.origin());
        descriptor.put("authenticationAvailable", vector.authentication().isPresent());
        descriptor.put("label", buildAttestationLabel(vector));
        descriptor.put(
                "challengeBase64Url", encodeBase64Url(vector.registration().challenge()));
        descriptor.put("clientDataJson", encodeBase64Url(vector.registration().clientDataJson()));
        descriptor.put(
                "attestationObject", encodeBase64Url(vector.registration().attestationObject()));
        descriptor.put("credentialPrivateKey", vector.keyMaterial().credentialPrivateKeyJwk());
        descriptor.put("attestationPrivateKey", vector.keyMaterial().attestationPrivateKeyJwk());
        descriptor.put("attestationCertificateSerial", vector.keyMaterial().attestationCertificateSerialBase64Url());
        return descriptor;
    }

    private static String buildAttestationLabel(WebAuthnAttestationVector vector) {
        String algorithmLabel =
                vector.algorithm() == null ? "" : safeTrim(vector.algorithm().label());
        String formatLabel =
                vector.format() == null ? "" : safeTrim(vector.format().label());
        String w3cSection = safeTrim(vector.w3cSection());
        String origin = safeTrim(vector.origin());
        String fallbackTitle = safeTrim(vector.title());
        if (fallbackTitle.isBlank()) {
            fallbackTitle = safeTrim(vector.vectorId());
        }

        String prefix = algorithmLabel.isBlank() ? fallbackTitle : algorithmLabel;
        if (prefix.isBlank()) {
            prefix = "attestation";
        }

        StringBuilder suffix = new StringBuilder();
        suffix.append(formatLabel.isBlank() ? "unknown-format" : formatLabel);
        String descriptor = null;
        if (!w3cSection.isBlank()) {
            descriptor = "W3C " + w3cSection;
        } else if (!origin.isBlank()) {
            descriptor = origin;
        }
        if (descriptor != null && !descriptor.isBlank()) {
            suffix.append(", ").append(descriptor);
        }
        return prefix + " (" + suffix + ")";
    }

    private static Map<String, Object> describeMetadataEntry(WebAuthnMetadataCatalogue.WebAuthnMetadataEntry entry) {
        Map<String, Object> descriptor = new java.util.LinkedHashMap<>();
        descriptor.put("entryId", entry.entryId());
        descriptor.put("format", entry.attestationFormat().label());
        descriptor.put("label", buildMetadataEntryLabel(entry));
        descriptor.put("description", entry.description());
        descriptor.put("aaguid", entry.aaguid().toString());
        descriptor.put(
                "vectorIds",
                entry.sources().stream()
                        .map(WebAuthnMetadataCatalogue.MetadataSource::vectorId)
                        .filter(StringUtils::hasText)
                        .distinct()
                        .toList());
        return descriptor;
    }

    private static String buildMetadataEntryLabel(WebAuthnMetadataCatalogue.WebAuthnMetadataEntry entry) {
        String base = safeTrim(entry.description());
        if (base.isBlank()) {
            base = entry.entryId();
        }
        String format = safeTrim(entry.attestationFormat().label());
        return base + " (" + (format.isBlank() ? "unknown-format" : format) + ")";
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String encodeBase64Url(byte[] value) {
        if (value == null || value.length == 0) {
            return "";
        }
        return BASE64_URL_ENCODER.encodeToString(value);
    }

    @PostMapping(value = "/console/replay/telemetry", consumes = "application/json")
    org.springframework.http.ResponseEntity<Void> replayTelemetry(
            @RequestBody OperatorConsoleReplayEventRequest request) {
        telemetry.record(request);
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    private void populatePolicyPresets(Model model) {
        List<PolicyPreset> presets = OcraOperatorSampleData.policyPresets();
        model.addAttribute("policyPresets", presets);
        try {
            List<Map<String, Object>> payload = presets.stream()
                    .map(preset -> {
                        Map<String, Object> sampleMap = new java.util.LinkedHashMap<>();
                        InlineSample sample = preset.getSample();
                        putIfNotNull(sampleMap, "suite", sample.getSuite());
                        putIfNotNull(sampleMap, "sharedSecretHex", sample.getSharedSecretHex());
                        putIfNotNull(sampleMap, "challenge", sample.getChallenge());
                        putIfNotNull(sampleMap, "sessionHex", sample.getSessionHex());
                        putIfNotNull(sampleMap, "clientChallenge", sample.getClientChallenge());
                        putIfNotNull(sampleMap, "serverChallenge", sample.getServerChallenge());
                        putIfNotNull(sampleMap, "pinHashHex", sample.getPinHashHex());
                        putIfNotNull(sampleMap, "timestampHex", sample.getTimestampHex());
                        putIfNotNull(sampleMap, "counter", sample.getCounter());
                        putIfNotNull(sampleMap, "expectedOtp", sample.getExpectedOtp());
                        return Map.of("key", preset.getKey(), "label", preset.getLabel(), "sample", sampleMap);
                    })
                    .toList();
            model.addAttribute("policyPresetJson", objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to render policy presets", ex);
        }
    }

    private static String ensureCsrfToken(HttpSession session) {
        Object existing = session.getAttribute(CSRF_ATTRIBUTE);
        if (existing instanceof String token && StringUtils.hasText(token)) {
            return token;
        }
        String generated = UUID.randomUUID().toString();
        session.setAttribute(CSRF_ATTRIBUTE, generated);
        return generated;
    }

    public static final class PolicyPreset {
        private final String key;
        private final String label;
        private final InlineSample sample;

        PolicyPreset(String key, String label, InlineSample sample) {
            this.key = key;
            this.label = label;
            this.sample = sample;
        }

        public String getKey() {
            return key;
        }

        public String getLabel() {
            return label;
        }

        public InlineSample getSample() {
            return sample;
        }
    }

    public static final class InlineSample {
        private final String suite;
        private final String sharedSecretHex;
        private final String challenge;
        private final String sessionHex;
        private final String clientChallenge;
        private final String serverChallenge;
        private final String pinHashHex;
        private final String timestampHex;
        private final Long counter;
        private final String expectedOtp;

        InlineSample(
                String suite,
                String sharedSecretHex,
                String challenge,
                String sessionHex,
                String clientChallenge,
                String serverChallenge,
                String pinHashHex,
                String timestampHex,
                Long counter,
                String expectedOtp) {
            this.suite = suite;
            this.sharedSecretHex = sharedSecretHex;
            this.challenge = challenge;
            this.sessionHex = sessionHex;
            this.clientChallenge = clientChallenge;
            this.serverChallenge = serverChallenge;
            this.pinHashHex = pinHashHex;
            this.timestampHex = timestampHex;
            this.counter = counter;
            this.expectedOtp = expectedOtp;
        }

        public String getSuite() {
            return suite;
        }

        public String getSharedSecretHex() {
            return sharedSecretHex;
        }

        public String getChallenge() {
            return challenge;
        }

        public String getSessionHex() {
            return sessionHex;
        }

        public String getClientChallenge() {
            return clientChallenge;
        }

        public String getServerChallenge() {
            return serverChallenge;
        }

        public String getPinHashHex() {
            return pinHashHex;
        }

        public String getTimestampHex() {
            return timestampHex;
        }

        public Long getCounter() {
            return counter;
        }

        public String getExpectedOtp() {
            return expectedOtp;
        }
    }

    private static void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
