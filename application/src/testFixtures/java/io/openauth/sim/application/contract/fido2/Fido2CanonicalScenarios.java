package io.openauth.sim.application.contract.fido2;

import io.openauth.sim.application.contract.CanonicalFacadeResult;
import io.openauth.sim.application.contract.CanonicalScenario;
import io.openauth.sim.application.contract.ScenarioEnvironment;
import io.openauth.sim.application.fido2.WebAuthnReplayApplicationService;
import io.openauth.sim.application.fido2.WebAuthnSeedApplicationService;
import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.fido2.WebAuthnFixtures.WebAuthnFixture;
import java.util.List;
import java.util.Map;

public final class Fido2CanonicalScenarios {

    private static final String STORED_ID = "fido2-packed-es256";

    private Fido2CanonicalScenarios() {}

    public static List<CanonicalScenario> scenarios(ScenarioEnvironment env) {
        WebAuthnFixture fixture = WebAuthnFixtures.loadPackedEs256();
        seedStored(env, fixture);

        WebAuthnReplayApplicationService.ReplayCommand.Stored storedMatchCommand =
                new WebAuthnReplayApplicationService.ReplayCommand.Stored(
                        STORED_ID,
                        fixture.request().relyingPartyId(),
                        fixture.request().origin(),
                        fixture.request().expectedType(),
                        fixture.request().expectedChallenge(),
                        fixture.request().clientDataJson(),
                        fixture.request().authenticatorData(),
                        fixture.request().signature());

        CanonicalScenario storedReplayMatch = new CanonicalScenario(
                "S-004-CF-01-stored-replay-match",
                CanonicalScenario.Protocol.FIDO2,
                CanonicalScenario.Kind.REPLAY_STORED,
                storedMatchCommand,
                new CanonicalFacadeResult(true, "match", null, null, null, null, null, false, true));

        byte[] badSignature = fixture.request().signature().clone();
        badSignature[0] ^= (byte) 0xFF;

        WebAuthnReplayApplicationService.ReplayCommand.Stored storedMismatchCommand =
                new WebAuthnReplayApplicationService.ReplayCommand.Stored(
                        STORED_ID,
                        fixture.request().relyingPartyId(),
                        fixture.request().origin(),
                        fixture.request().expectedType(),
                        fixture.request().expectedChallenge(),
                        fixture.request().clientDataJson(),
                        fixture.request().authenticatorData(),
                        badSignature);

        CanonicalScenario storedReplayMismatch = new CanonicalScenario(
                "S-004-CF-02-stored-replay-mismatch",
                CanonicalScenario.Protocol.FIDO2,
                CanonicalScenario.Kind.FAILURE_STORED,
                storedMismatchCommand,
                new CanonicalFacadeResult(false, "signature_invalid", null, null, null, null, null, false, true));

        WebAuthnReplayApplicationService.ReplayCommand.Inline inlineMatchCommand =
                new WebAuthnReplayApplicationService.ReplayCommand.Inline(
                        "inline",
                        fixture.request().relyingPartyId(),
                        fixture.request().origin(),
                        fixture.request().expectedType(),
                        fixture.storedCredential().credentialId(),
                        fixture.storedCredential().publicKeyCose(),
                        fixture.storedCredential().signatureCounter(),
                        fixture.storedCredential().userVerificationRequired(),
                        fixture.algorithm(),
                        fixture.request().expectedChallenge(),
                        fixture.request().clientDataJson(),
                        fixture.request().authenticatorData(),
                        fixture.request().signature());

        CanonicalScenario inlineReplayMatch = new CanonicalScenario(
                "S-004-CF-03-inline-replay-match",
                CanonicalScenario.Protocol.FIDO2,
                CanonicalScenario.Kind.REPLAY_INLINE,
                inlineMatchCommand,
                new CanonicalFacadeResult(true, "match", null, null, null, null, null, false, true));

        return List.of(storedReplayMatch, storedReplayMismatch, inlineReplayMatch);
    }

    private static void seedStored(ScenarioEnvironment env, WebAuthnFixture fixture) {
        WebAuthnSeedApplicationService seeder = new WebAuthnSeedApplicationService();
        seeder.seed(
                List.of(new WebAuthnSeedApplicationService.SeedCommand(
                        STORED_ID,
                        fixture.storedCredential().relyingPartyId(),
                        fixture.storedCredential().credentialId(),
                        fixture.storedCredential().publicKeyCose(),
                        fixture.storedCredential().signatureCounter(),
                        fixture.storedCredential().userVerificationRequired(),
                        fixture.algorithm(),
                        fixture.credentialPrivateKeyJwk(),
                        Map.of("preset", fixture.id()))),
                env.store());
    }
}
