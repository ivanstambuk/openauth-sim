package io.openauth.sim.rest.emv.cap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openauth.sim.application.emv.cap.EmvCapSeedApplicationService;
import io.openauth.sim.application.emv.cap.EmvCapSeedSamples;
import io.openauth.sim.application.emv.cap.EmvCapSeedSamples.SeedSample;
import io.openauth.sim.core.emv.cap.EmvCapReplayFixtures;
import io.openauth.sim.core.emv.cap.EmvCapReplayFixtures.ReplayFixture;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures.EmvCapVector;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "openauth.sim.persistence.enable-store=false")
@AutoConfigureMockMvc
final class EmvCapReplayEndpointTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CredentialStore credentialStore;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("openauth.sim.persistence.database-path", () -> "unused");
    }

    @BeforeEach
    void seedBaselineCredentials() {
        if (credentialStore instanceof InMemoryCredentialStore store) {
            store.reset();
        }
        EmvCapSeedApplicationService seeder = new EmvCapSeedApplicationService();
        List<SeedSample> samples = EmvCapSeedSamples.samples();
        seeder.seed(samples.stream().map(SeedSample::toSeedCommand).toList(), credentialStore);
    }

    @Test
    @DisplayName("Stored EMV/CAP replay returns match metadata and verbose trace")
    void storedReplayReturnsMatch() throws Exception {
        ReplayFixture fixture = EmvCapReplayFixtures.load("replay-respond-baseline");
        String responseBody = mockMvc.perform(post("/api/v1/emv/cap/replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(storedRequestBody(fixture, true)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = JSON.readTree(responseBody);
        assertEquals("match", root.get("status").asText());
        assertEquals("match", root.get("reasonCode").asText());

        JsonNode metadata = root.get("metadata");
        assertEquals("stored", metadata.get("credentialSource").asText());
        assertEquals(fixture.credentialId(), metadata.get("credentialId").asText());
        assertEquals(fixture.mode().name(), metadata.get("mode").asText());
        assertEquals(0, metadata.get("matchedDelta").asInt());
        assertEquals(
                fixture.previewWindow().backward(),
                metadata.get("driftBackward").asInt());
        assertEquals(
                fixture.previewWindow().forward(), metadata.get("driftForward").asInt());
        assertTrue(metadata.get("telemetryId").asText().startsWith("rest-emv-cap-"));

        JsonNode trace = root.get("trace");
        assertThat(trace)
                .as("Verbose trace payload should be present when includeTrace=true")
                .isNotNull();
        assertEquals("emv.cap.replay.stored", trace.get("operation").asText());
        assertEquals(
                expectedMasterKeyDigest(fixture.referencedVector()),
                trace.path("masterKeySha256").asText());
    }

    @Test
    @DisplayName("Stored replay defaults includeTrace to true when omitted")
    void storedReplayDefaultsToVerboseTrace() throws Exception {
        ReplayFixture fixture = EmvCapReplayFixtures.load("replay-respond-baseline");
        ObjectNode payload = JSON.createObjectNode();
        payload.put("credentialId", fixture.credentialId());
        payload.put("mode", fixture.mode().name());
        payload.put("otp", fixture.otpDecimal());
        payload.put("driftBackward", fixture.previewWindow().backward());
        payload.put("driftForward", fixture.previewWindow().forward());

        String responseBody = mockMvc.perform(post("/api/v1/emv/cap/replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = JSON.readTree(responseBody);
        assertEquals("match", root.get("status").asText());
        assertEquals("match", root.get("reasonCode").asText());
        assertThat(root.get("trace"))
                .as("Trace should be included when includeTrace is omitted")
                .isNotNull();
    }

    @Test
    @DisplayName("Stored replay honours includeTrace=false toggle")
    void storedReplaySuppressesTraceWhenDisabled() throws Exception {
        ReplayFixture fixture = EmvCapReplayFixtures.load("replay-respond-baseline");
        ObjectNode payload = JSON.createObjectNode();
        payload.put("credentialId", fixture.credentialId());
        payload.put("mode", fixture.mode().name());
        payload.put("otp", fixture.otpDecimal());
        payload.put("driftBackward", fixture.previewWindow().backward());
        payload.put("driftForward", fixture.previewWindow().forward());
        payload.put("includeTrace", false);

        String responseBody = mockMvc.perform(post("/api/v1/emv/cap/replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = JSON.readTree(responseBody);
        assertEquals("match", root.get("status").asText());
        assertEquals("match", root.get("reasonCode").asText());
        assertThat(root.get("trace"))
                .as("Trace should be omitted when includeTrace=false")
                .isNull();
    }

    @Test
    @DisplayName("Inline EMV/CAP replay with mismatched OTP reports mismatch without revealing secrets")
    void inlineReplayReturnsMismatch() throws Exception {
        ReplayFixture fixture = EmvCapReplayFixtures.load("replay-sign-baseline");
        EmvCapVector vector = EmvCapVectorFixtures.load(fixture.vectorId());

        String responseBody = mockMvc.perform(post("/api/v1/emv/cap/replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inlineRequestBody(
                                vector, fixture.mismatchOtpDecimal(), fixture.previewWindow(), false)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = JSON.readTree(responseBody);
        assertEquals("mismatch", root.get("status").asText());
        assertEquals("otp_mismatch", root.get("reasonCode").asText());

        JsonNode metadata = root.get("metadata");
        assertEquals("inline", metadata.get("credentialSource").asText());
        assertEquals(fixture.mode().name(), metadata.get("mode").asText());
        assertEquals(
                fixture.previewWindow().backward(),
                metadata.get("driftBackward").asInt());
        assertEquals(
                fixture.previewWindow().forward(), metadata.get("driftForward").asInt());
        assertTrue(metadata.get("telemetryId").asText().startsWith("rest-emv-cap-"));
        assertEquals(
                fixture.mismatchOtpDecimal().length(),
                metadata.get("suppliedOtpLength").asInt());

        assertThat(root.get("trace"))
                .as("Trace should be omitted when includeTrace=false")
                .isNull();
    }

    @Test
    @DisplayName("Inline EMV/CAP replay with includeTrace=true still redacts trace when no data is available")
    void inlineReplayMismatchIncludesTraceToggle() throws Exception {
        ReplayFixture fixture = EmvCapReplayFixtures.load("replay-sign-baseline");
        EmvCapVector vector = EmvCapVectorFixtures.load(fixture.vectorId());

        String responseBody = mockMvc.perform(post("/api/v1/emv/cap/replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                inlineRequestBody(vector, fixture.mismatchOtpDecimal(), fixture.previewWindow(), true)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = JSON.readTree(responseBody);
        assertEquals("mismatch", root.get("status").asText());
        assertEquals("otp_mismatch", root.get("reasonCode").asText());
        assertThat(root.has("trace")).isFalse();
    }

    @Test
    @DisplayName("Stored replay rejects negative drift window")
    void storedReplayRejectsNegativeDrift() throws Exception {
        ReplayFixture fixture = EmvCapReplayFixtures.load("replay-respond-baseline");
        ObjectNode payload = (ObjectNode) JSON.readTree(storedRequestBody(fixture, true));
        payload.put("driftBackward", -1);
        payload.put("driftForward", 0);

        String responseBody = mockMvc.perform(post("/api/v1/emv/cap/replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = JSON.readTree(responseBody);
        assertEquals("invalid_input", root.get("status").asText());
        assertEquals("invalid_window", root.get("reasonCode").asText());
        assertEquals("driftBackward must be non-negative", root.get("message").asText());
        assertEquals("driftBackward", root.get("details").get("field").asText());
    }

    @Test
    @DisplayName("Inline replay rejects non-numeric OTP")
    void inlineReplayRejectsNonNumericOtp() throws Exception {
        ReplayFixture fixture = EmvCapReplayFixtures.load("replay-sign-baseline");
        EmvCapVector vector = EmvCapVectorFixtures.load(fixture.vectorId());
        ObjectNode payload = (ObjectNode)
                JSON.readTree(inlineRequestBody(vector, fixture.mismatchOtpDecimal(), fixture.previewWindow(), true));
        payload.put("otp", "ABC123");

        String responseBody = mockMvc.perform(post("/api/v1/emv/cap/replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = JSON.readTree(responseBody);
        assertEquals("invalid_input", root.get("status").asText());
        assertEquals("invalid_otp", root.get("reasonCode").asText());
        assertEquals("OTP must contain only digits", root.get("message").asText());
        assertEquals("otp", root.get("details").get("field").asText());
    }

    @Test
    @DisplayName("Inline replay requires ICC template")
    void inlineReplayRequiresIccTemplate() throws Exception {
        ReplayFixture fixture = EmvCapReplayFixtures.load("replay-sign-baseline");
        EmvCapVector vector = EmvCapVectorFixtures.load(fixture.vectorId());
        ObjectNode payload = (ObjectNode)
                JSON.readTree(inlineRequestBody(vector, fixture.mismatchOtpDecimal(), fixture.previewWindow(), true));
        payload.remove("iccDataTemplate");

        String responseBody = mockMvc.perform(post("/api/v1/emv/cap/replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = JSON.readTree(responseBody);
        assertEquals("invalid_input", root.get("status").asText());
        assertEquals("invalid_input", root.get("reasonCode").asText());
        assertEquals("iccDataTemplate is required", root.get("message").asText());
        assertEquals("iccDataTemplate", root.get("details").get("field").asText());
    }

    @Test
    @DisplayName("Missing OTP yields a validation error with field metadata")
    void missingOtpReturnsValidationError() throws Exception {
        String responseBody = mockMvc.perform(post("/api/v1/emv/cap/replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "credentialId": "emv-cap:identify-baseline",
                                  "mode": "IDENTIFY",
                                  "driftBackward": 0,
                                  "driftForward": 0
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = JSON.readTree(responseBody);
        assertEquals("invalid_input", root.get("status").asText());
        assertEquals("invalid_input", root.get("reasonCode").asText());
        assertEquals("otp is required", root.get("message").asText());
        assertEquals("otp", root.get("details").get("field").asText());
    }

    private static String storedRequestBody(ReplayFixture fixture, boolean includeTrace) throws Exception {
        ObjectNode root = JSON.createObjectNode();
        root.put("credentialId", fixture.credentialId());
        root.put("mode", fixture.mode().name());
        root.put("otp", fixture.otpDecimal());
        root.put("driftBackward", fixture.previewWindow().backward());
        root.put("driftForward", fixture.previewWindow().forward());
        root.put("includeTrace", includeTrace);
        return JSON.writeValueAsString(root);
    }

    private static String inlineRequestBody(
            EmvCapVector vector, String otp, EmvCapReplayFixtures.PreviewWindow window, boolean includeTrace)
            throws Exception {
        ObjectNode root = JSON.createObjectNode();
        root.put("mode", vector.input().mode().name());
        root.put("masterKey", vector.input().masterKeyHex());
        root.put("atc", vector.input().atcHex());
        root.put("branchFactor", vector.input().branchFactor());
        root.put("height", vector.input().height());
        root.put("iv", vector.input().ivHex());
        root.put("cdol1", vector.input().cdol1Hex());
        root.put("issuerProprietaryBitmap", vector.input().issuerProprietaryBitmapHex());

        ObjectNode customerInputs = root.putObject("customerInputs");
        customerInputs.put("challenge", vector.input().customerInputs().challenge());
        customerInputs.put("reference", vector.input().customerInputs().reference());
        customerInputs.put("amount", vector.input().customerInputs().amount());

        if (vector.input().transactionData().terminalHexOverride().isPresent()
                || vector.input().transactionData().iccHexOverride().isPresent()) {
            ObjectNode transaction = root.putObject("transactionData");
            vector.input()
                    .transactionData()
                    .terminalHexOverride()
                    .ifPresent(value -> transaction.put("terminal", value));
            vector.input().transactionData().iccHexOverride().ifPresent(value -> transaction.put("icc", value));
        }

        root.put("iccDataTemplate", vector.input().iccDataTemplateHex());
        root.put("issuerApplicationData", vector.input().issuerApplicationDataHex());
        root.put("otp", otp);
        root.put("driftBackward", window.backward());
        root.put("driftForward", window.forward());
        if (includeTrace) {
            root.put("includeTrace", true);
        }
        return JSON.writeValueAsString(root);
    }

    private static String expectedMasterKeyDigest(EmvCapVector vector) {
        return sha256Digest(vector.input().masterKeyHex());
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

    @TestConfiguration
    static class InMemoryStoreConfiguration {

        @Bean
        CredentialStore credentialStore() {
            return new InMemoryCredentialStore();
        }
    }

    static final class InMemoryCredentialStore implements CredentialStore {

        private final CopyOnWriteArrayList<Credential> store = new CopyOnWriteArrayList<>();

        void reset() {
            store.clear();
        }

        @Override
        public void save(Credential credential) {
            store.removeIf(existing -> existing.name().equals(credential.name()));
            store.add(credential);
        }

        @Override
        public java.util.Optional<Credential> findByName(String name) {
            return store.stream().filter(cred -> cred.name().equals(name)).findFirst();
        }

        @Override
        public java.util.List<Credential> findAll() {
            return List.copyOf(store);
        }

        @Override
        public boolean delete(String name) {
            return store.removeIf(credential -> credential.name().equals(name));
        }

        @Override
        public void close() {
            store.clear();
        }
    }
}
