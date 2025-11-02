package io.openauth.sim.rest.emv.cap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openauth.sim.core.emv.cap.EmvCapInput;
import io.openauth.sim.core.emv.cap.EmvCapMode;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures.EmvCapVector;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import java.util.Optional;
import java.util.stream.Stream;
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
class EmvCapEvaluationEndpointTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("openauth.sim.persistence.database-path", () -> "unused");
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "REST evaluate {0} aligns with fixture")
    @org.junit.jupiter.params.provider.MethodSource("emvCapVectorIds")
    void restEvaluateMatchesFixture(String vectorId) throws Exception {
        EmvCapVector vector = EmvCapVectorFixtures.load(vectorId);
        String responseBody = mockMvc.perform(post("/api/v1/emv/cap/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(vector, Optional.of(true))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = MAPPER.readTree(responseBody);
        assertEquals(vector.outputs().otpDecimal(), root.get("otp").asText());
        assertEquals(expectedMaskLength(vector), root.get("maskLength").asInt());

        JsonNode trace = root.get("trace");
        assertNotNull(trace);
        assertEquals(vector.outputs().sessionKeyHex(), trace.get("sessionKey").asText());
        assertEquals(
                vector.outputs().generateAcResultHex(),
                trace.get("generateAcResult").asText());
        assertEquals(vector.outputs().bitmaskOverlay(), trace.get("bitmask").asText());
        assertEquals(
                vector.outputs().maskedDigitsOverlay(),
                trace.get("maskedDigitsOverlay").asText());
        assertEquals(
                vector.outputs().generateAcInputTerminalHex(),
                trace.get("generateAcInput").get("terminal").asText());
        assertEquals(
                vector.outputs().generateAcInputIccHex(),
                trace.get("generateAcInput").get("icc").asText());

        JsonNode telemetry = root.get("telemetry");
        assertNotNull(telemetry);
        assertEquals(
                expectedEventName(vector.input().mode()), telemetry.get("event").asText());
        assertEquals("success", telemetry.get("status").asText());
        assertEquals(vector.input().atcHex(), telemetry.get("fields").get("atc").asText());
        assertEquals(
                expectedMaskLength(vector),
                telemetry.get("fields").get("maskedDigitsCount").asInt());
    }

    @Test
    @DisplayName("Identify mode returns OTP, trace, and telemetry by default")
    void identifyModeReturnsOtpAndTrace() throws Exception {
        EmvCapVector vector = EmvCapVectorFixtures.load("identify-baseline");
        String responseBody = mockMvc.perform(post("/api/v1/emv/cap/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(vector, Optional.empty())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = MAPPER.readTree(responseBody);
        assertEquals(vector.outputs().otpDecimal(), root.get("otp").asText());
        assertEquals(expectedMaskLength(vector), root.get("maskLength").asInt());

        JsonNode trace = root.get("trace");
        assertNotNull(trace);
        assertEquals(vector.outputs().sessionKeyHex(), trace.get("sessionKey").asText());
        assertEquals(
                vector.outputs().generateAcResultHex(),
                trace.get("generateAcResult").asText());
        assertEquals(vector.outputs().bitmaskOverlay(), trace.get("bitmask").asText());
        assertEquals(
                vector.outputs().maskedDigitsOverlay(),
                trace.get("maskedDigitsOverlay").asText());
        assertEquals(
                vector.outputs().generateAcInputTerminalHex(),
                trace.get("generateAcInput").get("terminal").asText());
        assertEquals(
                vector.outputs().generateAcInputIccHex(),
                trace.get("generateAcInput").get("icc").asText());
        assertEquals(
                vector.input().iccDataTemplateHex(),
                trace.get("iccPayloadTemplate").asText());
        assertEquals(
                vector.outputs().generateAcInputIccHex(),
                trace.get("iccPayloadResolved").asText());
        assertEquals(
                vector.input().issuerApplicationDataHex(),
                trace.get("issuerApplicationData").asText());

        JsonNode telemetry = root.get("telemetry");
        assertNotNull(telemetry);
        assertEquals("emv.cap.identify", telemetry.get("event").asText());
        assertEquals("success", telemetry.get("status").asText());
        assertEquals("generated", telemetry.get("reasonCode").asText());
        assertTrue(telemetry.get("sanitized").asBoolean());

        JsonNode fields = telemetry.get("fields");
        assertTrue(fields.get("telemetryId").asText().startsWith("rest-emv-cap-"));
        assertEquals(vector.input().mode().name(), fields.get("mode").asText());
        assertEquals(vector.input().atcHex(), fields.get("atc").asText());
        assertEquals(expectedMaskLength(vector), fields.get("maskedDigitsCount").asInt());
        assertEquals(
                vector.input().issuerProprietaryBitmapHex().length() / 2,
                fields.get("ipbMaskLength").asInt());
        assertEquals(vector.input().branchFactor(), fields.get("branchFactor").asInt());
        assertEquals(vector.input().height(), fields.get("height").asInt());
    }

    @Test
    @DisplayName("Respond mode evaluates challenge and produces OTP")
    void respondModeProducesOtp() throws Exception {
        EmvCapVector vector = EmvCapVectorFixtures.load("respond-baseline");
        String responseBody = mockMvc.perform(post("/api/v1/emv/cap/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(vector, Optional.of(true))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = MAPPER.readTree(responseBody);
        assertEquals(vector.outputs().otpDecimal(), root.get("otp").asText());
        assertEquals(expectedMaskLength(vector), root.get("maskLength").asInt());

        JsonNode telemetry = root.get("telemetry");
        assertEquals("emv.cap.respond", telemetry.get("event").asText());
        assertEquals("success", telemetry.get("status").asText());
    }

    @Test
    @DisplayName("Sign mode honours includeTrace=false toggle")
    void signModeWithoutTrace() throws Exception {
        EmvCapVector vector = EmvCapVectorFixtures.load("sign-baseline");
        String responseBody = mockMvc.perform(post("/api/v1/emv/cap/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(vector, Optional.of(false))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = MAPPER.readTree(responseBody);
        assertEquals(vector.outputs().otpDecimal(), root.get("otp").asText());
        assertEquals(expectedMaskLength(vector), root.get("maskLength").asInt());
        assertNull(root.get("trace"));

        JsonNode telemetry = root.get("telemetry");
        assertEquals("emv.cap.sign", telemetry.get("event").asText());
        assertEquals("success", telemetry.get("status").asText());
    }

    @Test
    @DisplayName("Missing master key produces validation error")
    void missingMasterKeyReturnsValidationError() throws Exception {
        String responseBody = mockMvc.perform(post("/api/v1/emv/cap/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "mode": "IDENTIFY",
                          "atc": "00B4",
                          "branchFactor": 4,
                          "height": 8,
                          "iv": "00000000000000000000000000000000",
                          "cdol1": "9F02069F03069F1A0295055F2A029A039C019F3704",
                          "issuerProprietaryBitmap": "00001F00000000000FFFFF00000000008000",
                          "iccDataTemplate": "1000xxxxA50006040000",
                          "issuerApplicationData": "06770A03A48000"
                        }
                        """))
                .andExpect(status().isUnprocessableEntity())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = MAPPER.readTree(responseBody);
        assertEquals("invalid_input", root.get("status").asText());
        assertEquals("missing_field", root.get("reasonCode").asText());
        assertEquals("masterKey is required", root.get("message").asText());
        assertEquals("masterKey", root.get("details").get("field").asText());
    }

    @Test
    @DisplayName("Invalid mode produces readable validation error")
    void invalidModeReturnsValidationError() throws Exception {
        EmvCapVector vector = EmvCapVectorFixtures.load("identify-baseline");
        ObjectNode payload = (ObjectNode) MAPPER.readTree(requestBody(vector, Optional.of(true)));
        payload.put("mode", "INVALID");

        String responseBody = mockMvc.perform(post("/api/v1/emv/cap/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = MAPPER.readTree(responseBody);
        assertEquals("invalid_input", root.get("status").asText());
        assertEquals("invalid_mode", root.get("reasonCode").asText());
        assertEquals(
                "Mode must be IDENTIFY, RESPOND, or SIGN", root.get("message").asText());
        assertEquals("mode", root.get("details").get("field").asText());
    }

    @Test
    @DisplayName("Odd-length ICC template triggers invalid_template_length")
    void iccTemplateWithOddLengthReturnsValidationError() throws Exception {
        EmvCapVector vector = EmvCapVectorFixtures.load("identify-baseline");
        ObjectNode payload = (ObjectNode) MAPPER.readTree(requestBody(vector, Optional.of(true)));
        payload.put("iccDataTemplate", "ABC");

        String responseBody = mockMvc.perform(post("/api/v1/emv/cap/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = MAPPER.readTree(responseBody);
        assertEquals("invalid_input", root.get("status").asText());
        assertEquals("invalid_template_length", root.get("reasonCode").asText());
        assertEquals(
                "iccDataTemplate must contain an even number of characters",
                root.get("message").asText());
        assertEquals("iccDataTemplate", root.get("details").get("field").asText());
    }

    @Test
    @DisplayName("Optional terminal hex rejects odd-length payloads")
    void transactionTerminalOddLengthReturnsValidationError() throws Exception {
        EmvCapVector vector = EmvCapVectorFixtures.load("identify-baseline");
        ObjectNode payload = (ObjectNode) MAPPER.readTree(requestBody(vector, Optional.of(true)));
        ObjectNode transaction = payload.putObject("transactionData");
        transaction.put("terminal", "123");
        transaction.put("icc", vector.input().iccDataTemplateHex());

        String responseBody = mockMvc.perform(post("/api/v1/emv/cap/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = MAPPER.readTree(responseBody);
        assertEquals("invalid_input", root.get("status").asText());
        assertEquals("invalid_hex_length", root.get("reasonCode").asText());
        assertEquals(
                "transactionData.terminal must contain an even number of hex characters",
                root.get("message").asText());
        assertEquals(
                "transactionData.terminal", root.get("details").get("field").asText());
    }

    @Test
    @DisplayName("Branch factor must be positive")
    void branchFactorMustBePositive() throws Exception {
        EmvCapVector vector = EmvCapVectorFixtures.load("identify-baseline");
        ObjectNode payload = (ObjectNode) MAPPER.readTree(requestBody(vector, Optional.of(true)));
        payload.put("branchFactor", 0);

        String responseBody = mockMvc.perform(post("/api/v1/emv/cap/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = MAPPER.readTree(responseBody);
        assertEquals("invalid_input", root.get("status").asText());
        assertEquals("invalid_number", root.get("reasonCode").asText());
        assertEquals("branchFactor must be positive", root.get("message").asText());
        assertEquals("branchFactor", root.get("details").get("field").asText());
    }

    @Test
    @DisplayName("Issuer proprietary bitmap must be hexadecimal")
    void issuerBitmapMustBeHexadecimal() throws Exception {
        EmvCapVector vector = EmvCapVectorFixtures.load("identify-baseline");
        ObjectNode payload = (ObjectNode) MAPPER.readTree(requestBody(vector, Optional.of(true)));
        payload.put("issuerProprietaryBitmap", "ZZ");

        String responseBody = mockMvc.perform(post("/api/v1/emv/cap/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = MAPPER.readTree(responseBody);
        assertEquals("invalid_input", root.get("status").asText());
        assertEquals("invalid_hex", root.get("reasonCode").asText());
        assertEquals(
                "issuerProprietaryBitmap must be hexadecimal",
                root.get("message").asText());
        assertEquals("issuerProprietaryBitmap", root.get("details").get("field").asText());
    }

    @Test
    @DisplayName("IV must contain an even number of hex characters")
    void ivRequiresEvenLength() throws Exception {
        EmvCapVector vector = EmvCapVectorFixtures.load("identify-baseline");
        ObjectNode payload = (ObjectNode) MAPPER.readTree(requestBody(vector, Optional.of(true)));
        payload.put("iv", "000");

        String responseBody = mockMvc.perform(post("/api/v1/emv/cap/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = MAPPER.readTree(responseBody);
        assertEquals("invalid_input", root.get("status").asText());
        assertEquals("invalid_hex_length", root.get("reasonCode").asText());
        assertEquals(
                "iv must contain an even number of hex characters",
                root.get("message").asText());
        assertEquals("iv", root.get("details").get("field").asText());
    }

    @Test
    @DisplayName("Blank transactionData fields are ignored")
    void transactionDataBlankValuesAreIgnored() throws Exception {
        EmvCapVector vector = EmvCapVectorFixtures.load("identify-baseline");
        ObjectNode payload = (ObjectNode) MAPPER.readTree(requestBody(vector, Optional.of(true)));
        ObjectNode transaction = payload.putObject("transactionData");
        transaction.put("terminal", "  ");
        transaction.put("icc", "");

        String responseBody = mockMvc.perform(post("/api/v1/emv/cap/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = MAPPER.readTree(responseBody);
        assertEquals(vector.outputs().otpDecimal(), root.get("otp").asText());
        assertEquals(expectedMaskLength(vector), root.get("maskLength").asInt());
    }

    @Test
    @DisplayName("Invalid transactionData ICC hex produces validation error")
    void transactionIccInvalidHexReturnsValidationError() throws Exception {
        EmvCapVector vector = EmvCapVectorFixtures.load("identify-baseline");
        ObjectNode payload = (ObjectNode) MAPPER.readTree(requestBody(vector, Optional.of(true)));
        ObjectNode transaction = payload.putObject("transactionData");
        transaction.put("terminal", vector.input().ivHex());
        transaction.put("icc", "ZZ");

        String responseBody = mockMvc.perform(post("/api/v1/emv/cap/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn()
                .getResponse()
                .getContentAsString();

        System.out.println("transaction icc response=" + responseBody);
        JsonNode root = MAPPER.readTree(responseBody);
        assertEquals("invalid_input", root.get("status").asText());
        assertEquals("invalid_hex", root.get("reasonCode").asText());
        assertEquals(
                "transactionData.icc must be hexadecimal", root.get("message").asText());
        assertEquals("transactionData.icc", root.get("details").get("field").asText());
    }

    @Test
    @DisplayName("Issuer application data must be hexadecimal")
    void issuerApplicationDataMustBeHex() throws Exception {
        EmvCapVector vector = EmvCapVectorFixtures.load("identify-baseline");
        ObjectNode payload = (ObjectNode) MAPPER.readTree(requestBody(vector, Optional.of(true)));
        payload.put("issuerApplicationData", "GHIJKL");

        String responseBody = mockMvc.perform(post("/api/v1/emv/cap/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = MAPPER.readTree(responseBody);
        assertEquals("invalid_input", root.get("status").asText());
        assertEquals("invalid_hex", root.get("reasonCode").asText());
        assertEquals(
                "issuerApplicationData must be hexadecimal", root.get("message").asText());
        assertEquals("issuerApplicationData", root.get("details").get("field").asText());
    }

    private static String requestBody(EmvCapVector vector, Optional<Boolean> includeTrace)
            throws JsonProcessingException {
        ObjectNode root = MAPPER.createObjectNode();
        EmvCapInput input = vector.input();

        root.put("mode", input.mode().name());
        root.put("masterKey", input.masterKeyHex());
        root.put("atc", input.atcHex());
        root.put("branchFactor", input.branchFactor());
        root.put("height", input.height());
        root.put("iv", input.ivHex());
        root.put("cdol1", input.cdol1Hex());
        root.put("issuerProprietaryBitmap", input.issuerProprietaryBitmapHex());

        ObjectNode customerInputs = root.putObject("customerInputs");
        customerInputs.put("challenge", input.customerInputs().challenge());
        customerInputs.put("reference", input.customerInputs().reference());
        customerInputs.put("amount", input.customerInputs().amount());

        if (input.transactionData().terminalHexOverride().isPresent()
                || input.transactionData().iccHexOverride().isPresent()) {
            ObjectNode transactionData = root.putObject("transactionData");
            input.transactionData().terminalHexOverride().ifPresent(value -> transactionData.put("terminal", value));
            input.transactionData().iccHexOverride().ifPresent(value -> transactionData.put("icc", value));
        }

        root.put("iccDataTemplate", input.iccDataTemplateHex());
        root.put("issuerApplicationData", input.issuerApplicationDataHex());
        includeTrace.ifPresent(value -> root.put("includeTrace", value));
        return MAPPER.writeValueAsString(root);
    }

    private static int expectedMaskLength(EmvCapVector vector) {
        String overlay = vector.outputs().maskedDigitsOverlay();
        int count = 0;
        for (int index = 0; index < overlay.length(); index++) {
            if (overlay.charAt(index) != '.') {
                count++;
            }
        }
        return count;
    }

    private static Stream<String> emvCapVectorIds() {
        return Stream.of(
                "identify-baseline",
                "identify-b2-h6",
                "identify-b6-h10",
                "respond-baseline",
                "respond-challenge4",
                "respond-challenge8",
                "sign-baseline",
                "sign-amount-0845",
                "sign-amount-50375");
    }

    private static String expectedEventName(EmvCapMode mode) {
        return switch (mode) {
            case IDENTIFY -> "emv.cap.identify";
            case RESPOND -> "emv.cap.respond";
            case SIGN -> "emv.cap.sign";
        };
    }

    @TestConfiguration
    static class InMemoryStoreConfiguration {

        @Bean
        CredentialStore credentialStore() {
            return new InMemoryCredentialStore();
        }
    }

    static final class InMemoryCredentialStore implements CredentialStore {

        private final java.util.LinkedHashMap<String, Credential> store = new java.util.LinkedHashMap<>();

        @Override
        public void save(Credential credential) {
            store.put(credential.name(), credential);
        }

        @Override
        public java.util.Optional<Credential> findByName(String name) {
            return java.util.Optional.ofNullable(store.get(name));
        }

        @Override
        public java.util.List<Credential> findAll() {
            return java.util.List.copyOf(store.values());
        }

        @Override
        public boolean delete(String name) {
            return store.remove(name) != null;
        }

        @Override
        public void close() {
            store.clear();
        }
    }
}
