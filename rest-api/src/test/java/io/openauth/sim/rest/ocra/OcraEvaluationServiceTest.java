package io.openauth.sim.rest.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.ocra.OcraCredentialResolvers;
import io.openauth.sim.application.ocra.OcraEvaluationApplicationService;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OcraEvaluationServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2025-09-30T12:00:00Z"), ZoneOffset.UTC);
    private static final String DEFAULT_SECRET_HEX = "31323334353637383930313233343536";
    private static final String DEFAULT_SUITE = "OCRA-1:HOTP-SHA1-6:QN08";
    private static final String DEFAULT_CHALLENGE = "12345678";

    @Test
    @DisplayName("failure details normalize session required message")
    void failureDetailsSessionRequiredMessage() {
        OcraEvaluationService.FailureDetails details = OcraEvaluationService.FailureDetails.fromIllegalArgument(
                "sessionInformation required for suite: OCRA-1:HOTP-SHA256-8:QA08-S064");

        assertEquals("sessionHex", details.field());
        assertEquals("session_required", details.reasonCode());
        assertEquals("sessionHex is required for the requested suite", details.message());
        assertTrue(details.sanitized());
    }

    @Test
    @DisplayName("failure details normalize negative counter message")
    void failureDetailsCounterNegativeMessage() {
        OcraEvaluationService.FailureDetails details =
                OcraEvaluationService.FailureDetails.fromIllegalArgument("counterValue must be >= 0");

        assertEquals("counter", details.field());
        assertEquals("counter_negative", details.reasonCode());
        assertEquals("counter must be greater than or equal to zero", details.message());
        assertTrue(details.sanitized());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failureDetailMessages")
    @DisplayName("failure details mapping covers legacy messages")
    void failureDetailsAdditionalMappings(String description, String message, String field, String reasonCode) {
        OcraEvaluationService.FailureDetails details =
                OcraEvaluationService.FailureDetails.fromIllegalArgument(message);

        assertEquals(field, details.field());
        assertEquals(reasonCode, details.reasonCode());
        assertTrue(details.sanitized());
    }

    private static Stream<Arguments> failureDetailMessages() {
        return Stream.of(
                Arguments.of(
                        "session not permitted",
                        "sessionInformation is not permitted",
                        "sessionHex",
                        "session_not_permitted"),
                Arguments.of(
                        "timestamp not permitted",
                        "timestampHex is not permitted for the requested suite",
                        "timestampHex",
                        "timestamp_not_permitted"),
                Arguments.of(
                        "timestamp invalid", "timestampHex must be hexadecimal", "timestampHex", "timestamp_invalid"),
                Arguments.of(
                        "timestamp valid time",
                        "timestampHex must represent a valid time",
                        "timestampHex",
                        "timestamp_invalid"),
                Arguments.of("pin hash required", "pinHashHex is required", "pinHashHex", "pin_hash_required"),
                Arguments.of(
                        "pin hash not permitted",
                        "pinHashHex is not permitted",
                        "pinHashHex",
                        "pin_hash_not_permitted"),
                Arguments.of(
                        "pin hash mismatch",
                        "pinHash must use SHA1 and contain 40 hex characters",
                        "pinHashHex",
                        "pin_hash_mismatch"),
                Arguments.of("counter required", "counterValue required for suite", "counter", "counter_required"),
                Arguments.of(
                        "counter not permitted", "counterValue is not permitted", "counter", "counter_not_permitted"),
                Arguments.of(
                        "credential missing",
                        "credentialId or sharedSecretHex must be provided",
                        "credentialId",
                        "credential_missing"),
                Arguments.of(
                        "shared secret missing", "sharedSecretHex is required", "sharedSecretHex", "missing_required"));
    }

    private static OcraEvaluationService service(CredentialStore store) {
        OcraEvaluationApplicationService applicationService = new OcraEvaluationApplicationService(
                FIXED_CLOCK,
                store != null ? OcraCredentialResolvers.forStore(store) : OcraCredentialResolvers.emptyResolver());
        return new OcraEvaluationService(applicationService);
    }

    private static OcraEvaluationService service() {
        return service(null);
    }

    @Test
    @DisplayName("inline requests with invalid shared secret hex are rejected with sanitized details")
    void invalidSharedSecretHexRejected() {
        OcraEvaluationService service = service();

        OcraEvaluationRequest request = new OcraEvaluationRequest(
                null, "OCRA-1:HOTP-SHA1-6:QN08", "GHI", "12345678", null, null, null, null, null, null);

        OcraEvaluationValidationException exception =
                assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

        assertEquals("sharedSecretHex", exception.field());
        assertEquals("not_hexadecimal", exception.reasonCode());
        assertTrue(exception.sanitized());
        assertEquals("OCRA-1:HOTP-SHA1-6:QN08", exception.suite());
    }

    @Test
    @DisplayName("suite requiring session emits session_required when session payload missing")
    void sessionRequiredFailureWhenMissing() {
        OcraEvaluationService service = service();

        OcraEvaluationRequest request = new OcraEvaluationRequest(
                null,
                "OCRA-1:HOTPT30SHA256-7:QN08-SH512",
                DEFAULT_SECRET_HEX,
                "12345678",
                null,
                null,
                null,
                null,
                "00000001",
                null);

        OcraEvaluationValidationException exception =
                assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

        assertEquals("sessionHex", exception.field());
        assertEquals("session_required", exception.reasonCode());
        assertTrue(exception.sanitized());
        assertEquals("OCRA-1:HOTPT30SHA256-7:QN08-SH512", exception.suite());
    }

    @Test
    @DisplayName("invalid challenge format surfaces challenge_format reason code")
    void challengeFormatFailure() {
        OcraEvaluationService service = service();

        OcraEvaluationRequest request = new OcraEvaluationRequest(
                null, "OCRA-1:HOTP-SHA1-6:QN08", DEFAULT_SECRET_HEX, "ABCDEFGH", null, null, null, null, null, null);

        OcraEvaluationValidationException exception =
                assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

        assertEquals("challenge", exception.field());
        assertEquals("challenge_format", exception.reasonCode());
        assertTrue(exception.sanitized());
    }

    @Test
    @DisplayName("timestamp not permitted surfaces timestamp_not_permitted reason")
    void timestampNotPermittedFailure() {
        OcraEvaluationService service = service();

        OcraEvaluationRequest request = new OcraEvaluationRequest(
                null,
                "OCRA-1:HOTP-SHA1-6:QN08",
                DEFAULT_SECRET_HEX,
                "12345678",
                null,
                null,
                null,
                null,
                "00000001",
                null);

        OcraEvaluationValidationException exception =
                assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

        assertEquals("timestampHex", exception.field());
        assertEquals("timestamp_not_permitted", exception.reasonCode());
    }

    @Test
    @DisplayName("session not permitted surfaces session_not_permitted reason")
    void sessionNotPermittedFailure() {
        OcraEvaluationService service = service();

        OcraEvaluationRequest request = new OcraEvaluationRequest(
                null, DEFAULT_SUITE, DEFAULT_SECRET_HEX, DEFAULT_CHALLENGE, "ABCDEF12", null, null, null, null, null);

        OcraEvaluationValidationException exception =
                assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

        assertEquals("sessionHex", exception.field());
        assertEquals("session_not_permitted", exception.reasonCode());
    }

    @Test
    @DisplayName("timestamp drift exceeded surfaces timestamp_drift_exceeded reason")
    void timestampDriftExceededFailure() {
        OcraEvaluationService service = service();

        OcraEvaluationRequest request = new OcraEvaluationRequest(
                null,
                "OCRA-1:HOTPT30SHA256-7:QN08",
                DEFAULT_SECRET_HEX,
                DEFAULT_CHALLENGE,
                null,
                null,
                null,
                null,
                "00000000",
                null);

        OcraEvaluationValidationException exception =
                assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

        assertEquals("timestampHex", exception.field());
        assertEquals("timestamp_drift_exceeded", exception.reasonCode());
    }

    @Test
    @DisplayName("challenge shorter than required surfaces challenge_length reason")
    void challengeLengthFailure() {
        OcraEvaluationService service = service();

        OcraEvaluationRequest request = new OcraEvaluationRequest(
                null, DEFAULT_SUITE, DEFAULT_SECRET_HEX, "1234", null, null, null, null, null, null);

        OcraEvaluationValidationException exception =
                assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

        assertEquals("challenge", exception.field());
        assertEquals("challenge_length", exception.reasonCode());
    }

    @Test
    @DisplayName("credential reference evaluation succeeds and records telemetry")
    void credentialReferenceEvaluationSucceeds() {
        InMemoryCredentialStore store = new InMemoryCredentialStore();

        OcraCredentialFactory factory = new OcraCredentialFactory();
        OcraCredentialDescriptor descriptor = factory.createDescriptor(new OcraCredentialRequest(
                "stored-token",
                DEFAULT_SUITE,
                DEFAULT_SECRET_HEX,
                SecretEncoding.HEX,
                null,
                null,
                null,
                Map.of("source", "service-test")));
        VersionedCredentialRecord record = new OcraCredentialPersistenceAdapter().serialize(descriptor);
        store.save(VersionedCredentialRecordMapper.toCredential(record));

        OcraEvaluationService service = service(store);

        OcraEvaluationRequest request = new OcraEvaluationRequest(
                "stored-token", null, null, DEFAULT_CHALLENGE, null, null, null, null, null, null);

        OcraEvaluationResponse response = service.evaluate(request);

        assertEquals(DEFAULT_SUITE, response.suite());
        assertTrue(response.otp().length() > 0);
    }

    @Test
    @DisplayName("credential reference missing credential surfaces credential_not_found")
    void credentialReferenceMissingCredential() {
        InMemoryCredentialStore store = new InMemoryCredentialStore();

        OcraEvaluationService service = service(store);

        OcraEvaluationRequest request = new OcraEvaluationRequest(
                "missing-token", null, null, DEFAULT_CHALLENGE, null, null, null, null, null, null);

        OcraEvaluationValidationException exception =
                assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

        assertEquals("credentialId", exception.field());
        assertEquals("credential_not_found", exception.reasonCode());
    }

    @Test
    @DisplayName("counter-required suites surface counter_required reason")
    void counterRequiredFailure() {
        OcraEvaluationService service = service();

        OcraEvaluationRequest request = new OcraEvaluationRequest(
                null, "OCRA-1:HOTP-SHA1-6:C-QN08", DEFAULT_SECRET_HEX, "12345678", null, null, null, null, null, null);

        OcraEvaluationValidationException exception =
                assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

        assertEquals("counter", exception.field());
        assertEquals("counter_required", exception.reasonCode());
    }

    @Test
    @DisplayName("timestamp hex validation handles invalid format")
    void timestampInvalidFailure() {
        OcraEvaluationService service = service();

        OcraEvaluationRequest request = new OcraEvaluationRequest(
                null,
                "OCRA-1:HOTPT30SHA256-7:QN08",
                DEFAULT_SECRET_HEX,
                "12345678",
                null,
                null,
                null,
                null,
                "ZZZZ",
                null);

        OcraEvaluationValidationException exception =
                assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

        assertEquals("timestampHex", exception.field());
        assertEquals("not_hexadecimal", exception.reasonCode());
    }

    @Test
    @DisplayName("pin hash not permitted surfaces correct reason")
    void pinHashNotPermittedFailure() {
        OcraEvaluationService service = service();

        OcraEvaluationRequest request = new OcraEvaluationRequest(
                null,
                "OCRA-1:HOTP-SHA1-6:QN08",
                DEFAULT_SECRET_HEX,
                "12345678",
                null,
                null,
                null,
                "5e884898da28047151d0e56f8dc6292773603d0d",
                null,
                null);

        OcraEvaluationValidationException exception =
                assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

        assertEquals("pinHashHex", exception.field());
        assertEquals("pin_hash_not_permitted", exception.reasonCode());
    }

    @Test
    @DisplayName("challenge required surfaces challenge_required reason")
    void challengeRequiredFailure() {
        OcraEvaluationService service = service();

        OcraEvaluationRequest request = new OcraEvaluationRequest(
                null, DEFAULT_SUITE, DEFAULT_SECRET_HEX, null, null, null, null, null, null, null);

        OcraEvaluationValidationException exception =
                assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

        assertEquals("challenge", exception.field());
        assertEquals("challenge_required", exception.reasonCode());
    }

    @Test
    @DisplayName("counter not permitted surfaces counter_not_permitted reason")
    void counterNotPermittedFailure() {
        OcraEvaluationService service = service();

        OcraEvaluationRequest request = new OcraEvaluationRequest(
                null, DEFAULT_SUITE, DEFAULT_SECRET_HEX, "12345678", null, null, null, null, null, 5L);

        OcraEvaluationValidationException exception =
                assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

        assertEquals("counter", exception.field());
        assertEquals("counter_not_permitted", exception.reasonCode());
    }

    @Test
    @DisplayName("pin hash required surfaces pin_hash_required reason")
    void pinHashRequiredFailure() {
        OcraEvaluationService service = service();

        OcraEvaluationRequest request = new OcraEvaluationRequest(
                null,
                "OCRA-1:HOTP-SHA1-6:C-QN08-PSHA1",
                DEFAULT_SECRET_HEX,
                "12345678",
                null,
                null,
                null,
                null,
                null,
                1L);

        OcraEvaluationValidationException exception =
                assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

        assertEquals("pinHashHex", exception.field());
        assertEquals("pin_hash_required", exception.reasonCode());
    }

    @Test
    @DisplayName("credential conflict surfaces credential_conflict reason")
    void credentialConflictFailure() {
        OcraEvaluationService service = service();

        OcraEvaluationRequest request = new OcraEvaluationRequest(
                "stored-token",
                DEFAULT_SUITE,
                DEFAULT_SECRET_HEX,
                DEFAULT_CHALLENGE,
                null,
                null,
                null,
                null,
                null,
                null);

        OcraEvaluationValidationException exception =
                assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

        assertEquals("request", exception.field());
        assertEquals("credential_conflict", exception.reasonCode());
    }

    @Test
    @DisplayName("missing credential inputs surface credential_missing reason")
    void credentialMissingFailure() {
        OcraEvaluationService service = service();

        OcraEvaluationRequest request =
                new OcraEvaluationRequest("  ", null, null, DEFAULT_CHALLENGE, null, null, null, null, null, null);

        OcraEvaluationValidationException exception =
                assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

        assertEquals("request", exception.field());
        assertEquals("credential_missing", exception.reasonCode());
    }

    @Test
    @DisplayName("credential store absence surfaces credential_not_found")
    void credentialStoreMissingFailure() {
        OcraEvaluationService service = service();

        OcraEvaluationRequest request = new OcraEvaluationRequest(
                "stored-token", null, null, DEFAULT_CHALLENGE, null, null, null, null, null, null);

        OcraEvaluationValidationException exception =
                assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

        assertEquals("credentialId", exception.field());
        assertEquals("credential_not_found", exception.reasonCode());
    }

    @Test
    @DisplayName("unexpected runtime errors record telemetry and propagate")
    void unexpectedErrorRecordsTelemetry() {
        CredentialStore failingStore = new FailingCredentialStore();
        OcraEvaluationService service = service(failingStore);

        OcraEvaluationRequest request = new OcraEvaluationRequest(
                "stored-token", null, null, DEFAULT_CHALLENGE, null, null, null, null, null, null);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.evaluate(request));

        assertEquals("store offline", exception.getMessage());
    }

    @Test
    @DisplayName("inline suite omission surfaces missing_required for suite")
    void inlineSuiteMissingFailure() {
        OcraEvaluationService service = service();

        OcraEvaluationRequest request = new OcraEvaluationRequest(
                null, "  ", DEFAULT_SECRET_HEX, DEFAULT_CHALLENGE, null, null, null, null, null, null);

        OcraEvaluationValidationException exception =
                assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

        assertEquals("suite", exception.field());
        assertEquals("missing_required", exception.reasonCode());
    }

    @Test
    @DisplayName("timestamp with odd length surfaces invalid_hex_length")
    void timestampInvalidLengthFailure() {
        OcraEvaluationService service = service();

        OcraEvaluationRequest request = new OcraEvaluationRequest(
                null,
                "OCRA-1:HOTPT30SHA256-7:QN08",
                DEFAULT_SECRET_HEX,
                DEFAULT_CHALLENGE,
                null,
                null,
                null,
                null,
                "ABC",
                null);

        OcraEvaluationValidationException exception =
                assertThrows(OcraEvaluationValidationException.class, () -> service.evaluate(request));

        assertEquals("timestampHex", exception.field());
        assertEquals("invalid_hex_length", exception.reasonCode());
    }

    @Test
    @DisplayName("character format challenge is accepted for QC suites")
    void characterFormatChallengeSucceeds() {
        OcraEvaluationService service = service();

        OcraEvaluationRequest request = new OcraEvaluationRequest(
                null, "OCRA-1:HOTP-SHA1-6:QC08", DEFAULT_SECRET_HEX, "!@#$%^&*", null, null, null, null, null, null);

        OcraEvaluationResponse response = service.evaluate(request);

        assertEquals("OCRA-1:HOTP-SHA1-6:QC08", response.suite());
        assertTrue(response.otp().length() > 0);
    }

    @Test
    @DisplayName("failure details treat null message as invalid input")
    void failureDetailsNullMessage() {
        OcraEvaluationService.FailureDetails details = OcraEvaluationService.FailureDetails.fromIllegalArgument(null);

        assertEquals("request", details.field());
        assertEquals("invalid_input", details.reasonCode());
        assertTrue(details.sanitized());
    }

    @Test
    @DisplayName("failure details map credential/shared secret conflict")
    void failureDetailsCredentialConflict() {
        OcraEvaluationService.FailureDetails details =
                OcraEvaluationService.FailureDetails.fromIllegalArgument("credentialId and sharedSecretHex provided");

        assertEquals("credentialId", details.field());
        assertEquals("credential_missing", details.reasonCode());
    }

    @Test
    @DisplayName("failure details default to invalid input for unmatched message")
    void failureDetailsDefaultMessage() {
        OcraEvaluationService.FailureDetails details =
                OcraEvaluationService.FailureDetails.fromIllegalArgument("unrecognized condition");

        assertEquals("request", details.field());
        assertEquals("invalid_input", details.reasonCode());
    }

    private static final class InMemoryCredentialStore implements CredentialStore {

        private final Map<String, Credential> storage = new HashMap<>();

        @Override
        public void save(Credential credential) {
            storage.put(credential.name(), credential);
        }

        @Override
        public Optional<Credential> findByName(String name) {
            return Optional.ofNullable(storage.get(name));
        }

        @Override
        public List<Credential> findAll() {
            return List.copyOf(storage.values());
        }

        @Override
        public boolean delete(String name) {
            return storage.remove(name) != null;
        }

        @Override
        public void close() {
            storage.clear();
        }
    }

    private static final class FailingCredentialStore implements CredentialStore {

        @Override
        public void save(Credential credential) {
            throw new UnsupportedOperationException("save not supported");
        }

        @Override
        public Optional<Credential> findByName(String name) {
            throw new IllegalStateException("store offline");
        }

        @Override
        public List<Credential> findAll() {
            return List.of();
        }

        @Override
        public boolean delete(String name) {
            return false;
        }

        @Override
        public void close() {
            // no-op
        }
    }
}
