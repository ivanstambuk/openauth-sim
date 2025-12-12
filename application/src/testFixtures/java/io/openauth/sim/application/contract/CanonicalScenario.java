package io.openauth.sim.application.contract;

import java.util.Objects;

public record CanonicalScenario(
        String scenarioId, Protocol protocol, Kind kind, Object command, CanonicalFacadeResult expected) {

    public CanonicalScenario {
        scenarioId = Objects.requireNonNull(scenarioId, "scenarioId");
        protocol = Objects.requireNonNull(protocol, "protocol");
        kind = Objects.requireNonNull(kind, "kind");
        expected = Objects.requireNonNull(expected, "expected");
    }

    public enum Protocol {
        HOTP,
        TOTP,
        OCRA,
        FIDO2,
        EMV_CAP,
        EUDIW_OPENID4VP
    }

    public enum Kind {
        EVALUATE_STORED,
        EVALUATE_INLINE,
        REPLAY_STORED,
        REPLAY_INLINE,
        FAILURE_STORED,
        FAILURE_INLINE
    }
}
