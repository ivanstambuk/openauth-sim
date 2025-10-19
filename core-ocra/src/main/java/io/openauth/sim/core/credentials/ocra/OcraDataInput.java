package io.openauth.sim.core.credentials.ocra;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

/** Parsed representation of the OCRA data input descriptor list. */
public record OcraDataInput(
        boolean counter,
        Optional<OcraChallengeQuestion> challengeQuestion,
        Optional<OcraPinSpecification> pin,
        Optional<OcraSessionSpecification> sessionInformation,
        Optional<OcraTimestampSpecification> timestamp)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public OcraDataInput {
        Objects.requireNonNull(challengeQuestion, "challengeQuestion");
        Objects.requireNonNull(pin, "pin");
        Objects.requireNonNull(sessionInformation, "sessionInformation");
        Objects.requireNonNull(timestamp, "timestamp");
    }
}
