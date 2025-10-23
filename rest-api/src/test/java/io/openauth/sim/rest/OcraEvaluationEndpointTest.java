package io.openauth.sim.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "openauth.sim.persistence.enable-store=false")
@AutoConfigureMockMvc
final class OcraEvaluationEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SHARED_SECRET_HEX = "3132333435363738393031323334353637383930313233343536373839303132";
    private static final String SESSION_HEX_64 = "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF"
            + "112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567";
    private static final String SESSION_HEX_128 = SESSION_HEX_64 + SESSION_HEX_64;
    private static final String SESSION_HEX_256 = SESSION_HEX_128 + SESSION_HEX_128;
    private static final String SESSION_HEX_512 = SESSION_HEX_256 + SESSION_HEX_256;
    private static final String TIMESTAMP_SUITE = "OCRA-1:HOTPT30SHA256-7:QN08";
    private static final Duration TIMESTAMP_STEP = Duration.ofSeconds(30);
    private static final Instant FIXED_NOW = Instant.parse("2025-09-28T12:00:00Z");
    private static final String STORED_CREDENTIAL_ID = "rest-ocra-stored";
    private static final Logger TELEMETRY_LOGGER = Logger.getLogger("io.openauth.sim.rest.ocra.telemetry");

    @ParameterizedTest(name = "{0}")
    @MethodSource("successfulRequests")
    @DisplayName("Valid OCRA requests return expected OTP without leaking secrets")
    void evaluateReturnsOtp(String description, String requestJson, String expectedOtp, String secretSnippet)
            throws Exception {
        TestLogHandler handler = registerTelemetryHandler();
        try {
            String responseBody = mockMvc.perform(post("/api/v1/ocra/evaluate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            JsonNode response = MAPPER.readTree(responseBody);
            assertEquals(expectedOtp, response.get("otp").asText());
            assertTrue(response.get("suite").asText().startsWith("OCRA-1:HOTP-SHA256-8"));
            assertNotNull(response.get("telemetryId").asText());
            assertFalse(
                    responseBody.toLowerCase(Locale.ROOT).contains(secretSnippet.toLowerCase(Locale.ROOT)),
                    () -> "secret material leaked in response: " + responseBody);
            assertTrue(
                    handler.records().stream()
                            .anyMatch(record -> record.getMessage().contains("rest.ocra.evaluate")
                                    && record.getMessage().contains("reasonCode=success")
                                    && record.getMessage().contains("sanitized=true")),
                    "telemetry event missing required attributes");
            assertFalse(
                    handler.loggedSecrets(secretSnippet),
                    () -> "secret material leaked in telemetry: " + handler.records());
        } finally {
            deregisterTelemetryHandler(handler);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    @DisplayName("Invalid requests receive HTTP 400 without leaking secrets")
    void evaluateReturnsValidationErrors(
            String description,
            String requestJson,
            String expectedMessage,
            String expectedField,
            String expectedReasonCode,
            String secretSnippet)
            throws Exception {
        TestLogHandler handler = registerTelemetryHandler();
        try {
            String responseBody = mockMvc.perform(post("/api/v1/ocra/evaluate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            JsonNode response = MAPPER.readTree(responseBody);
            assertEquals("invalid_input", response.get("error").asText());
            assertTrue(response.get("message").asText().contains(expectedMessage));
            JsonNode details = response.get("details");
            assertNotNull(details, "details must be present");
            assertEquals(expectedField, details.get("field").asText());
            assertEquals(expectedReasonCode, details.get("reasonCode").asText());
            assertEquals("true", details.get("sanitized").asText());
            assertFalse(
                    responseBody.toLowerCase(Locale.ROOT).contains(secretSnippet.toLowerCase(Locale.ROOT)),
                    () -> "secret material leaked in validation response: " + responseBody);
            assertTelemetry(
                    handler,
                    record -> record.getLevel() == Level.WARNING
                            && record.getMessage().contains("reasonCode=" + expectedReasonCode)
                            && record.getMessage().contains("sanitized=true"));
            assertFalse(
                    handler.loggedSecrets(secretSnippet),
                    () -> "secret material leaked in telemetry: " + handler.records());
        } finally {
            deregisterTelemetryHandler(handler);
        }
    }

    private static Stream<Arguments> successfulRequests() {
        return Stream.of(
                Arguments.of(
                        "S064 session request",
                        requestJson("OCRA-1:HOTP-SHA256-8:QA08-S064", SESSION_HEX_64),
                        "17477202",
                        "313233"),
                Arguments.of(
                        "S128 session request",
                        requestJson("OCRA-1:HOTP-SHA256-8:QA08-S128", SESSION_HEX_128),
                        "18468077",
                        "313233"),
                Arguments.of(
                        "S256 session request",
                        requestJson("OCRA-1:HOTP-SHA256-8:QA08-S256", SESSION_HEX_256),
                        "77715695",
                        "313233"),
                Arguments.of(
                        "S512 session request",
                        requestJson("OCRA-1:HOTP-SHA256-8:QA08-S512", SESSION_HEX_512),
                        "05806151",
                        "313233"));
    }

    @DisplayName("Credential-backed requests return expected OTP")
    @org.junit.jupiter.api.Test
    void evaluateWithCredentialIdReturnsOtp() throws Exception {
        TestLogHandler handler = registerTelemetryHandler();
        String requestJson = """
            {
              "credentialId": "rest-ocra-stored",
              "suite": "OCRA-1:HOTP-SHA256-8:QA08-S064",
              "challenge": "SESSION01",
              "sessionHex": "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567"
            }
            """;

        try {
            String responseBody = mockMvc.perform(post("/api/v1/ocra/evaluate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            JsonNode response = MAPPER.readTree(responseBody);
            assertEquals("17477202", response.get("otp").asText());
            assertEquals("OCRA-1:HOTP-SHA256-8:QA08-S064", response.get("suite").asText());
            assertNotNull(response.get("telemetryId").asText());
            assertTrue(handler.records().stream()
                    .anyMatch(record -> record.getMessage().contains("hasCredentialReference=true")
                            && record.getMessage().contains("reasonCode=success")));
        } finally {
            deregisterTelemetryHandler(handler);
        }
    }

    @DisplayName("Stored requests return verbose trace when requested")
    @org.junit.jupiter.api.Test
    void evaluateWithCredentialIdReturnsVerboseTrace() throws Exception {
        String requestJson = """
            {
              "credentialId": "rest-ocra-stored",
              "suite": "OCRA-1:HOTP-SHA256-8:QA08-S064",
              "challenge": "SESSION01",
              "sessionHex": "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567",
              "verbose": true
            }
            """;

        String responseBody = mockMvc.perform(post("/api/v1/ocra/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = MAPPER.readTree(responseBody);
        JsonNode trace = response.get("trace");
        assertNotNull(trace, "Expected trace payload when verbose=true");
        assertEquals("ocra.evaluate.stored", trace.get("operation").asText());
        assertTrue(trace.get("steps").isArray());
        assertTrue(trace.get("steps").size() > 0, "Trace steps must not be empty");
    }

    @DisplayName("Inline requests return verbose trace when requested")
    @org.junit.jupiter.api.Test
    void evaluateInlineReturnsVerboseTrace() throws Exception {
        String requestJson = """
            {
              "suite": "OCRA-1:HOTP-SHA256-8:QA08-S064",
              "sharedSecretHex": "3132333435363738393031323334353637383930313233343536373839303132",
              "challenge": "SESSION01",
              "sessionHex": "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567",
              "verbose": true
            }
            """;

        String responseBody = mockMvc.perform(post("/api/v1/ocra/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = MAPPER.readTree(responseBody);
        JsonNode trace = response.get("trace");
        assertNotNull(trace, "Expected trace payload when verbose=true");
        assertEquals("ocra.evaluate.inline", trace.get("operation").asText());
        assertTrue(trace.get("steps").isArray());
        assertTrue(trace.get("steps").size() > 0, "Trace steps must not be empty");
    }

    @DisplayName("Missing credential reference returns descriptive error")
    @org.junit.jupiter.api.Test
    void evaluateCredentialIdNotFoundReturnsError() throws Exception {
        TestLogHandler handler = registerTelemetryHandler();
        String requestJson = """
            {
              "credentialId": "missing-credential",
              "suite": "OCRA-1:HOTP-SHA256-8:QA08-S064",
              "challenge": "SESSION01",
              "sessionHex": "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567"
            }
            """;

        try {
            String responseBody = mockMvc.perform(post("/api/v1/ocra/evaluate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            JsonNode response = MAPPER.readTree(responseBody);
            JsonNode details = response.get("details");
            assertEquals("credentialId", details.get("field").asText());
            assertEquals("credential_not_found", details.get("reasonCode").asText());
        } finally {
            deregisterTelemetryHandler(handler);
        }
    }

    @DisplayName("Requests must not supply credential reference and inline secrets together")
    @org.junit.jupiter.api.Test
    void evaluateCredentialConflictRejected() throws Exception {
        TestLogHandler handler = registerTelemetryHandler();
        String requestJson = """
            {
              "credentialId": "rest-ocra-stored",
              "suite": "OCRA-1:HOTP-SHA256-8:QA08-S064",
              "sharedSecretHex": "3132333435363738393031323334353637383930313233343536373839303132",
              "challenge": "SESSION01",
              "sessionHex": "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567"
            }
            """;

        try {
            String responseBody = mockMvc.perform(post("/api/v1/ocra/evaluate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            JsonNode response = MAPPER.readTree(responseBody);
            JsonNode details = response.get("details");
            assertEquals("request", details.get("field").asText());
            assertEquals("credential_conflict", details.get("reasonCode").asText());
        } finally {
            deregisterTelemetryHandler(handler);
        }
    }

    private static Stream<Arguments> invalidRequests() {
        Instant futureTimestamp = FIXED_NOW.plus(Duration.ofMinutes(5));
        String timestampDriftRequest = String.format(
                Locale.ROOT,
                "{"
                        + "%n  \"suite\": \"%s\","
                        + "%n  \"sharedSecretHex\": \"%s\","
                        + "%n  \"challenge\": \"12345678\","
                        + "%n  \"timestampHex\": \"%s\"%n}",
                TIMESTAMP_SUITE,
                SHARED_SECRET_HEX,
                timestampHexFor(futureTimestamp, TIMESTAMP_STEP));

        String pinHashMismatchRequest = """
            {
              \"suite\": \"OCRA-1:HOTP-SHA1-6:QA08-PSHA1\",
              \"sharedSecretHex\": \"31323334353637383930313233343536\",
              \"challenge\": \"SESSION01\",
              \"pinHashHex\": \"5e884898da28047151d0e56f8dc6292773603d\"
            }
            """;

        String missingSession = """
            {
              \"suite\": \"OCRA-1:HOTP-SHA256-8:QA08-S064\",
              \"sharedSecretHex\": \"3132333435363738393031323334353637383930313233343536373839303132\",
              \"challenge\": \"SESSION01\"
            }
            """;

        String wrongChallengeLength =
                requestJson("OCRA-1:HOTP-SHA256-8:QA08-S064", SESSION_HEX_64).replace("SESSION01", "SHORT");

        String nonHexSecret = """
            {
              \"suite\": \"OCRA-1:HOTP-SHA256-8:QA08-S064\",
              \"sharedSecretHex\": \"ZZ313233\",
              \"challenge\": \"SESSION01\",
              \"sessionHex\": \"00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567\"
            }
            """;

        String oddLengthSession = """
            {
              \"suite\": \"OCRA-1:HOTP-SHA256-8:QA08-S064\",
              \"sharedSecretHex\": \"3132333435363738393031323334353637383930313233343536373839303132\",
              \"challenge\": \"SESSION01\",
              \"sessionHex\": \"001122334455667\"
            }
            """;

        String missingCounter = """
            {
              \"suite\": \"OCRA-1:HOTP-SHA1-6:C-QN08\",
              \"sharedSecretHex\": \"31323334353637383930313233343536\",
              \"challenge\": \"12345678\"
            }
            """;

        String negativeCounter = """
            {
              \"suite\": \"OCRA-1:HOTP-SHA1-6:C-QN08\",
              \"sharedSecretHex\": \"31323334353637383930313233343536\",
              \"challenge\": \"12345678\",
              \"counter\": -1
            }
            """;

        return Stream.of(
                Arguments.of(
                        "Missing session returns validation error",
                        missingSession,
                        "sessionHex",
                        "sessionHex",
                        "session_required",
                        "SESSION01"),
                Arguments.of(
                        "Challenge length mismatch returns validation error",
                        wrongChallengeLength,
                        "challenge",
                        "challenge",
                        "challenge_length",
                        "SHORT"),
                Arguments.of(
                        "Non-hex shared secret rejected",
                        nonHexSecret,
                        "sharedSecretHex",
                        "sharedSecretHex",
                        "not_hexadecimal",
                        "ZZ313233"),
                Arguments.of(
                        "Odd-length session hex rejected",
                        oddLengthSession,
                        "sessionHex",
                        "sessionHex",
                        "invalid_hex_length",
                        "001122334455667"),
                Arguments.of(
                        "Missing counter rejected", missingCounter, "counter", "counter", "counter_required", "313233"),
                Arguments.of(
                        "Negative counter rejected",
                        negativeCounter,
                        "counter must be",
                        "counter",
                        "counter_negative",
                        "\"counter\": -1"),
                Arguments.of(
                        "Timestamp drift outside tolerance rejected",
                        timestampDriftRequest,
                        "timestamp",
                        "timestampHex",
                        "timestamp_drift_exceeded",
                        "313233"),
                Arguments.of(
                        "Pin hash mismatch rejected",
                        pinHashMismatchRequest,
                        "pinHash",
                        "pinHashHex",
                        "pin_hash_mismatch",
                        "5e8848"));
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock fixedClock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }

        @Bean
        CredentialStore inMemoryCredentialStore() {
            InMemoryCredentialStore store = new InMemoryCredentialStore();
            OcraCredentialFactory factory = new OcraCredentialFactory();
            OcraCredentialRequest request = new OcraCredentialRequest(
                    STORED_CREDENTIAL_ID,
                    "OCRA-1:HOTP-SHA256-8:QA08-S064",
                    SHARED_SECRET_HEX,
                    SecretEncoding.HEX,
                    null,
                    null,
                    null,
                    Map.of("source", "test"));
            OcraCredentialDescriptor descriptor = factory.createDescriptor(request);
            OcraCredentialPersistenceAdapter adapter = new OcraCredentialPersistenceAdapter();
            VersionedCredentialRecord record = adapter.serialize(descriptor);
            Credential credential = VersionedCredentialRecordMapper.toCredential(record);
            store.save(credential);
            return store;
        }
    }

    private static String requestJson(String suite, String sessionHex) {
        return String.format(
                Locale.ROOT,
                "{"
                        + "%n  \"suite\": \"%s\","
                        + "%n  \"sharedSecretHex\": \"%s\","
                        + "%n  \"challenge\": \"SESSION01\","
                        + "%n  \"sessionHex\": \"%s\"%n}",
                suite,
                SHARED_SECRET_HEX,
                sessionHex);
    }

    private static String timestampHexFor(Instant instant, Duration step) {
        long timeSteps = Math.floorDiv(instant.getEpochSecond(), step.getSeconds());
        String hex = Long.toHexString(timeSteps).toUpperCase(Locale.ROOT);
        return String.format(Locale.ROOT, "%16s", hex).replace(' ', '0');
    }

    private static TestLogHandler registerTelemetryHandler() {
        TestLogHandler handler = new TestLogHandler();
        TELEMETRY_LOGGER.addHandler(handler);
        return handler;
    }

    private static void deregisterTelemetryHandler(TestLogHandler handler) {
        TELEMETRY_LOGGER.removeHandler(handler);
    }

    private static final class TestLogHandler extends Handler {
        private final CopyOnWriteArrayList<LogRecord> records = new CopyOnWriteArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
            // no-op
        }

        @Override
        public void close() {
            // no-op
        }

        private boolean loggedSecrets(String secretSnippet) {
            String needle = secretSnippet.toLowerCase(Locale.ROOT);
            return records.stream()
                    .anyMatch(record ->
                            record.getMessage().toLowerCase(Locale.ROOT).contains(needle));
        }

        java.util.List<LogRecord> records() {
            return java.util.List.copyOf(records);
        }
    }

    private static final class InMemoryCredentialStore implements CredentialStore {

        private final Map<String, Credential> store = new java.util.concurrent.ConcurrentHashMap<>();

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

    private static void assertTelemetry(TestLogHandler handler, java.util.function.Predicate<LogRecord> predicate) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(250);
        while (System.nanoTime() < deadline) {
            if (handler.records().stream().anyMatch(predicate)) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("Telemetry event missing reasonCode/sanitized attributes: " + handler.records());
    }
}
