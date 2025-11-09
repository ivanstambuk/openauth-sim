package io.openauth.sim.rest.emv.cap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

@JsonInclude(JsonInclude.Include.NON_NULL)
record EmvCapReplayVerboseTracePayload(@JsonUnwrapped EmvCapTracePayload trace) {

    static EmvCapReplayVerboseTracePayload from(EmvCapTracePayload payload) {
        return payload == null ? null : new EmvCapReplayVerboseTracePayload(payload);
    }
}
