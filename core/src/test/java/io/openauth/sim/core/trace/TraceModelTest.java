package io.openauth.sim.core.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openauth.sim.core.trace.VerboseTrace.TraceStep;
import org.junit.jupiter.api.Test;

class TraceModelTest {

    @Test
    void builderProducesImmutableTrace() {
        VerboseTrace trace = VerboseTrace.builder("hotp-evaluate")
                .withMetadata("protocol", "HOTP")
                .withMetadata("mode", "stored")
                .addStep(step -> step.id("resolve")
                        .summary("Resolve stored credential")
                        .detail("CredentialStore.findByName -> HotpDescriptor")
                        .attribute("credentialId", "otp-001")
                        .attribute("counter.before", 7L)
                        .attribute("found", true))
                .addStep(step -> step.id("generate-otp")
                        .summary("Generate HOTP with HMAC-SHA1")
                        .detail("HotpGenerator.generate")
                        .attribute("counter.after", 8L)
                        .attribute("otp", "123456"))
                .build();

        assertEquals("hotp-evaluate", trace.operation());
        assertEquals("HOTP", trace.metadata().get("protocol"));
        assertEquals("stored", trace.metadata().get("mode"));
        assertEquals(2, trace.steps().size());
        assertEquals("resolve", trace.steps().get(0).id());
        assertEquals("generate-otp", trace.steps().get(1).id());
        assertEquals("otp-001", trace.steps().get(0).attributes().get("credentialId"));

        assertThrows(UnsupportedOperationException.class, () -> trace.steps().add(null));
        assertThrows(
                UnsupportedOperationException.class,
                () -> trace.steps().get(0).attributes().put("new", "value"));
    }

    @Test
    void stepBuilderSupportsNotes() {
        VerboseTrace trace = VerboseTrace.builder("totp-replay")
                .addStep(step -> step.id("verify-otp")
                        .summary("Verify candidate OTP")
                        .detail("TotpValidator.verify")
                        .attribute("candidate", "654321")
                        .attribute("skew.match", 1)
                        .note("window", "[-1, +1]")
                        .note("result", "matched"))
                .build();

        TraceStep step = trace.steps().get(0);
        assertEquals("matched", step.notes().get("result"));
        assertEquals("[-1, +1]", step.notes().get("window"));
        assertEquals("654321", step.attributes().get("candidate"));
    }
}
