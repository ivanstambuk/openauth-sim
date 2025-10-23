package io.openauth.sim.application.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class OcraEvaluationApplicationServiceVerboseTraceTest {

    private static final String SUITE_VALUE = "OCRA-1:HOTP-SHA1-6:QN08";
    private static final String CHALLENGE = "12345678";
    private static final String SHARED_SECRET = "3132333435363738393031323334353637383930";

    private OcraCredentialDescriptor storedDescriptor;
    private MockCredentialResolver credentialResolver;
    private OcraEvaluationApplicationService service;

    @BeforeEach
    void setUp() {
        OcraCredentialFactory factory = new OcraCredentialFactory();
        storedDescriptor = factory.createDescriptor(new OcraCredentialRequest(
                "stored-ocra",
                SUITE_VALUE,
                SHARED_SECRET,
                io.openauth.sim.core.model.SecretEncoding.HEX,
                null,
                null,
                null,
                Map.of("source", "stored")));
        credentialResolver = new MockCredentialResolver(storedDescriptor);
        service = new OcraEvaluationApplicationService(
                Clock.fixed(Instant.parse("2025-10-22T12:00:00Z"), ZoneOffset.UTC), credentialResolver);
    }

    @Test
    void storedEvaluationWithVerboseCapturesTrace() {
        OcraEvaluationApplicationService.EvaluationCommand.Stored command =
                new OcraEvaluationApplicationService.EvaluationCommand.Stored(
                        "stored-ocra", CHALLENGE, "", "", "", "", "", null);

        OcraEvaluationApplicationService.EvaluationResult result = service.evaluate(command, true);

        assertTrue(result.verboseTrace().isPresent());
        var trace = result.verboseTrace().orElseThrow();

        assertEquals("ocra.evaluate.stored", trace.operation());
        assertEquals("OCRA", trace.metadata().get("protocol"));
        assertEquals("stored", trace.metadata().get("mode"));
        assertEquals("stored-ocra", trace.metadata().get("credentialId"));
        assertEquals(SUITE_VALUE, trace.metadata().get("suite"));

        assertTrue(trace.steps().stream().anyMatch(step -> "normalize.request".equals(step.id())));
        assertTrue(trace.steps().stream().anyMatch(step -> "resolve.credential".equals(step.id())));
        assertTrue(trace.steps().stream().anyMatch(step -> "validate.inputs".equals(step.id())));
        assertTrue(trace.steps().stream().anyMatch(step -> "generate.otp".equals(step.id())));
    }

    @Test
    void inlineEvaluationWithVerboseCapturesTrace() {
        OcraEvaluationApplicationService.EvaluationCommand.Inline command =
                new OcraEvaluationApplicationService.EvaluationCommand.Inline(
                        "inline-ocra", SUITE_VALUE, SHARED_SECRET, CHALLENGE, "", "", "", "", "", null, null);

        OcraEvaluationApplicationService.EvaluationResult result = service.evaluate(command, true);

        assertTrue(result.verboseTrace().isPresent());
        var trace = result.verboseTrace().orElseThrow();

        assertEquals("ocra.evaluate.inline", trace.operation());
        assertEquals("inline", trace.metadata().get("mode"));
        assertTrue(trace.steps().stream().anyMatch(step -> "create.descriptor".equals(step.id())));
        assertTrue(trace.steps().stream().anyMatch(step -> "generate.otp".equals(step.id())));
    }

    @Test
    void verboseDisabledLeavesTraceEmpty() {
        OcraEvaluationApplicationService.EvaluationCommand.Stored command =
                new OcraEvaluationApplicationService.EvaluationCommand.Stored(
                        "stored-ocra", CHALLENGE, "", "", "", "", "", null);

        OcraEvaluationApplicationService.EvaluationResult result = service.evaluate(command);

        assertTrue(result.verboseTrace().isEmpty());
    }

    private static final class MockCredentialResolver implements OcraEvaluationApplicationService.CredentialResolver {

        private final OcraEvaluationApplicationService.ResolvedCredential resolvedCredential;

        private MockCredentialResolver(OcraCredentialDescriptor descriptor) {
            this.resolvedCredential = new OcraEvaluationApplicationService.ResolvedCredential(descriptor);
        }

        @Override
        public Optional<OcraEvaluationApplicationService.ResolvedCredential> findById(String credentialId) {
            if ("stored-ocra".equals(credentialId)) {
                return Optional.of(resolvedCredential);
            }
            return Optional.empty();
        }
    }
}
