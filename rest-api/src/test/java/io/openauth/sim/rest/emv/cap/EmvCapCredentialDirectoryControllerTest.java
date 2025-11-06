package io.openauth.sim.rest.emv.cap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openauth.sim.application.emv.cap.EmvCapSeedApplicationService;
import io.openauth.sim.application.emv.cap.EmvCapSeedSamples;
import io.openauth.sim.core.emv.cap.EmvCapCredentialPersistenceAdapter;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.CredentialStore;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
final class EmvCapCredentialDirectoryControllerTest {

    @TempDir
    static Path tempDir;

    private static Path databasePath;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        databasePath = tempDir.resolve("credentials.db");
        registry.add(
                "openauth.sim.persistence.database-path",
                () -> databasePath.toAbsolutePath().toString());
        registry.add("openauth.sim.persistence.enable-store", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CredentialStore credentialStore;

    @Autowired
    private ObjectMapper objectMapper;

    private final EmvCapSeedApplicationService seedService = new EmvCapSeedApplicationService();

    @BeforeEach
    void setUp() {
        credentialStore.findAll().forEach(credential -> credentialStore.delete(credential.name()));
        seedService.seed(
                EmvCapSeedSamples.samples().stream()
                        .map(sample -> sample.toSeedCommand())
                        .toList(),
                credentialStore);
    }

    @Test
    @DisplayName("Directory endpoint skips credentials missing required attributes")
    void directorySkipsIncompleteCredentials() throws Exception {
        Credential missingCdol1 = Credential.create(
                "emv-cap-incomplete",
                CredentialType.EMV_CA,
                SecretMaterial.fromHex("00112233445566778899AABBCCDDEEFF"),
                Map.ofEntries(
                        Map.entry(EmvCapCredentialPersistenceAdapter.ATTR_MODE, "IDENTIFY"),
                        Map.entry(EmvCapCredentialPersistenceAdapter.ATTR_DEFAULT_ATC, "00B4"),
                        Map.entry(EmvCapCredentialPersistenceAdapter.ATTR_BRANCH_FACTOR, "4"),
                        Map.entry(EmvCapCredentialPersistenceAdapter.ATTR_HEIGHT, "8"),
                        Map.entry(EmvCapCredentialPersistenceAdapter.ATTR_IV, "0000000000000000"),
                        Map.entry(EmvCapCredentialPersistenceAdapter.ATTR_IPB, "F0F0"),
                        Map.entry(EmvCapCredentialPersistenceAdapter.ATTR_ICC_TEMPLATE, "DEADBEEF"),
                        Map.entry(EmvCapCredentialPersistenceAdapter.ATTR_ISSUER_APPLICATION_DATA, "CAFEBABE"),
                        Map.entry(EmvCapCredentialPersistenceAdapter.ATTR_CDOL1, "")));
        credentialStore.save(missingCdol1);

        MvcResult result = mockMvc.perform(get("/api/v1/emv/cap/credentials").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(findById(root, "emv-cap-incomplete"))
                .as("Incomplete credential should be omitted from the directory listing")
                .isNull();
    }

    @Test
    @DisplayName("Directory endpoint skips credentials with invalid branch factor metadata")
    void directorySkipsCredentialWithInvalidBranchFactor() throws Exception {
        Credential invalidBranchFactor = Credential.create(
                "emv-cap-invalid-branch",
                CredentialType.EMV_CA,
                SecretMaterial.fromHex("F00112233445566778899AABBCCDDEE0"),
                Map.ofEntries(
                        Map.entry(EmvCapCredentialPersistenceAdapter.ATTR_MODE, "IDENTIFY"),
                        Map.entry(EmvCapCredentialPersistenceAdapter.ATTR_DEFAULT_ATC, "00B4"),
                        Map.entry(EmvCapCredentialPersistenceAdapter.ATTR_BRANCH_FACTOR, "not-a-number"),
                        Map.entry(EmvCapCredentialPersistenceAdapter.ATTR_HEIGHT, "8"),
                        Map.entry(EmvCapCredentialPersistenceAdapter.ATTR_IV, "0000000000000000"),
                        Map.entry(EmvCapCredentialPersistenceAdapter.ATTR_IPB, "0F0F"),
                        Map.entry(EmvCapCredentialPersistenceAdapter.ATTR_ICC_TEMPLATE, "FEEDFACE"),
                        Map.entry(EmvCapCredentialPersistenceAdapter.ATTR_ISSUER_APPLICATION_DATA, "CAFED00D"),
                        Map.entry(EmvCapCredentialPersistenceAdapter.ATTR_CDOL1, "01")));
        credentialStore.save(invalidBranchFactor);

        MvcResult result = mockMvc.perform(get("/api/v1/emv/cap/credentials").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(findById(root, "emv-cap-invalid-branch"))
                .as("Credential with non-numeric branch factor should be omitted")
                .isNull();
    }

    @Test
    @DisplayName("Directory endpoint lists seeded EMV/CAP credentials with metadata")
    void directoryListsSeededCredentials() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/emv/cap/credentials").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(root.isArray()).isTrue();
        assertThat(root).hasSizeGreaterThanOrEqualTo(3);

        JsonNode identifyNode = findById(root, "emv-cap-identify-baseline");
        assertThat(identifyNode).isNotNull();
        assertThat(identifyNode.path("mode").asText()).isEqualTo("IDENTIFY");
        assertThat(identifyNode.path("label").asText()).isEqualTo("CAP Identify baseline");
        Credential identifyCredential =
                credentialStore.findByName("emv-cap-identify-baseline").orElseThrow();
        String masterKey = identifyCredential.secret().asHex().trim();
        String cdol1 = attribute(identifyCredential, EmvCapCredentialPersistenceAdapter.ATTR_CDOL1);
        String issuerBitmap = attribute(identifyCredential, EmvCapCredentialPersistenceAdapter.ATTR_IPB);
        String iccTemplate = attribute(identifyCredential, EmvCapCredentialPersistenceAdapter.ATTR_ICC_TEMPLATE);
        String issuerApplicationData =
                attribute(identifyCredential, EmvCapCredentialPersistenceAdapter.ATTR_ISSUER_APPLICATION_DATA);

        assertThat(identifyNode.has("masterKey"))
                .as("Master key should be omitted from directory response")
                .isFalse();
        assertThat(identifyNode.has("cdol1"))
                .as("CDOL1 payload should be omitted from directory response")
                .isFalse();
        assertThat(identifyNode.has("issuerProprietaryBitmap"))
                .as("IPB should be omitted from directory response")
                .isFalse();
        assertThat(identifyNode.has("iccDataTemplate"))
                .as("ICC template should be omitted from directory response")
                .isFalse();
        assertThat(identifyNode.has("issuerApplicationData"))
                .as("Issuer application data should be omitted from directory response")
                .isFalse();

        assertThat(identifyNode.path("masterKeySha256").asText()).isEqualTo(sha256Digest(masterKey));
        assertThat(identifyNode.path("masterKeyHexLength").asInt()).isEqualTo(masterKey.length());
        assertThat(identifyNode.path("cdol1HexLength").asInt()).isEqualTo(cdol1.length());
        assertThat(identifyNode.path("issuerProprietaryBitmapHexLength").asInt())
                .isEqualTo(issuerBitmap.length());
        assertThat(identifyNode.path("iccDataTemplateHexLength").asInt()).isEqualTo(iccTemplate.length());
        assertThat(identifyNode.path("issuerApplicationDataHexLength").asInt())
                .isEqualTo(issuerApplicationData.length());
        assertThat(identifyNode.path("defaultAtc").asText()).isEqualTo("00B4");
        assertThat(identifyNode.path("defaults").path("challenge").asText()).isEmpty();
        assertThat(identifyNode.path("transaction").path("iccResolved").asText())
                .isNotEmpty();

        JsonNode metadata = identifyNode.path("metadata");
        assertThat(metadata.path("presetKey").asText()).isEqualTo("emv-cap.identify.baseline");
        assertThat(metadata.path("vectorId").asText()).isEqualTo("identify-baseline");
    }

    @Test
    @DisplayName("Credential detail endpoint exposes inline defaults for presets")
    void credentialDetailExposesInlineDefaults() throws Exception {
        EmvCapSeedSamples.SeedSample signSample = EmvCapSeedSamples.samples().stream()
                .filter(sample -> "sign-baseline".equals(sample.vectorId()))
                .findFirst()
                .orElseThrow();

        MvcResult result = mockMvc.perform(get("/api/v1/emv/cap/credentials/{credentialId}", signSample.credentialId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        var vector = signSample.vector();

        assertThat(payload.path("id").asText()).isEqualTo(signSample.credentialId());
        assertThat(payload.path("mode").asText()).isEqualTo(signSample.mode().name());
        assertThat(payload.path("masterKey").asText()).isEqualTo(vector.input().masterKeyHex());
        assertThat(payload.path("cdol1").asText()).isEqualTo(vector.input().cdol1Hex());
        assertThat(payload.path("issuerProprietaryBitmap").asText())
                .isEqualTo(vector.input().issuerProprietaryBitmapHex());
        assertThat(payload.path("iccDataTemplate").asText())
                .isEqualTo(vector.input().iccDataTemplateHex());
        assertThat(payload.path("issuerApplicationData").asText())
                .isEqualTo(vector.input().issuerApplicationDataHex());

        JsonNode defaults = payload.path("defaults");
        assertThat(defaults.path("challenge").asText())
                .isEqualTo(vector.input().customerInputs().challenge());
        assertThat(defaults.path("reference").asText())
                .isEqualTo(vector.input().customerInputs().reference());
        assertThat(defaults.path("amount").asText())
                .isEqualTo(vector.input().customerInputs().amount());
    }

    @Test
    @DisplayName("Credential detail endpoint returns 404 when preset is missing")
    void credentialDetailReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/emv/cap/credentials/does-not-exist").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    private static JsonNode findById(JsonNode root, String id) {
        for (JsonNode node : root) {
            if (id.equals(node.path("id").asText())) {
                return node;
            }
        }
        return null;
    }

    private static String attribute(Credential credential, String key) {
        return credential.attributes().getOrDefault(key, "").trim();
    }

    private static String sha256Digest(String hex) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = hexToBytes(hex);
            byte[] hashed = digest.digest(bytes);
            return "sha256:" + toHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static byte[] hexToBytes(String hex) {
        String normalized = hex.trim().toUpperCase(Locale.ROOT);
        if ((normalized.length() & 1) == 1) {
            throw new IllegalArgumentException("Hex input must contain an even number of characters");
        }
        byte[] data = new byte[normalized.length() / 2];
        for (int index = 0; index < normalized.length(); index += 2) {
            data[index / 2] = (byte) Integer.parseInt(normalized.substring(index, index + 2), 16);
        }
        return data;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(Locale.ROOT, "%02X", value));
        }
        return builder.toString();
    }
}
