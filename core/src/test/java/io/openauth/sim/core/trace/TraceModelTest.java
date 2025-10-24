package io.openauth.sim.core.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openauth.sim.core.trace.VerboseTrace.AttributeType;
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
                        .attribute(AttributeType.STRING, "credentialId", "otp-001")
                        .attribute(AttributeType.INT, "counter.before", 7L)
                        .attribute(AttributeType.BOOL, "found", true)
                        .spec("rfc4226§5.1"))
                .addStep(step -> step.id("generate-otp")
                        .summary("Generate HOTP with HMAC-SHA1")
                        .detail("HotpGenerator.generate")
                        .attribute(AttributeType.INT, "counter.after", 8L)
                        .attribute(AttributeType.STRING, "otp", "123456")
                        .spec("rfc4226§5.4"))
                .build();

        assertEquals("hotp-evaluate", trace.operation());
        assertEquals("HOTP", trace.metadata().get("protocol"));
        assertEquals("stored", trace.metadata().get("mode"));
        assertEquals(VerboseTrace.Tier.EDUCATIONAL, trace.tier());
        assertEquals(2, trace.steps().size());
        assertEquals("resolve", trace.steps().get(0).id());
        assertEquals("generate-otp", trace.steps().get(1).id());
        assertEquals("rfc4226§5.1", trace.steps().get(0).specAnchor());
        assertEquals("rfc4226§5.4", trace.steps().get(1).specAnchor());
        assertEquals("otp-001", attributeValue(trace.steps().get(0), "credentialId"));

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
                        .attribute(AttributeType.STRING, "candidate", "654321")
                        .attribute(AttributeType.INT, "skew.match", 1)
                        .note("window", "[-1, +1]")
                        .note("result", "matched"))
                .build();

        TraceStep step = trace.steps().get(0);
        assertEquals("matched", step.notes().get("result"));
        assertEquals("[-1, +1]", step.notes().get("window"));
        assertEquals("654321", attributeValue(step, "candidate"));
    }

    @Test
    void builderAllowsTierOverrideAndLegacyAttributes() {
        VerboseTrace trace = VerboseTrace.builder("hotp-debug")
                .withTier(VerboseTrace.Tier.LAB_SECRETS)
                .addStep(step -> step.id("prepare")
                        .attribute("mode", "lab")
                        .attribute(AttributeType.HEX, "secret.hash", "deadbeef")
                        .spec("lab-note"))
                .build();

        assertEquals(VerboseTrace.Tier.LAB_SECRETS, trace.tier());
        TraceStep step = trace.steps().get(0);
        assertEquals("lab-note", step.specAnchor());
        assertEquals("lab", attributeValue(step, "mode"));
        assertEquals("deadbeef", attributeValue(step, "secret.hash"));
        assertEquals(AttributeType.HEX, attributeType(step, "secret.hash"));
    }

    private static Object attributeValue(TraceStep step, String name) {
        Object direct = step.attributes().get(name);
        if (direct != null) {
            return direct;
        }
        return step.typedAttributes().stream()
                .filter(attribute -> attribute.name().equals(name))
                .map(VerboseTrace.TraceAttribute::value)
                .findFirst()
                .orElse(null);
    }

    private static AttributeType attributeType(TraceStep step, String name) {
        return step.typedAttributes().stream()
                .filter(attribute -> attribute.name().equals(name))
                .map(VerboseTrace.TraceAttribute::type)
                .findFirst()
                .orElse(null);
    }
}
