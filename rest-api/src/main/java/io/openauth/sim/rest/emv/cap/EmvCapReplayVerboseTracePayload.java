package io.openauth.sim.rest.emv.cap;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.openauth.sim.rest.VerboseTracePayload;

/**
 * EMV/CAP replay trace payload that exposes the hashed master key while preserving the shared verbose trace schema.
 */
record EmvCapReplayVerboseTracePayload(
        @JsonProperty("masterKeySha256") String masterKeySha256,
        @JsonUnwrapped VerboseTracePayload payload) {
    // Jackson-flattened wrapper; no additional behaviour.
}
