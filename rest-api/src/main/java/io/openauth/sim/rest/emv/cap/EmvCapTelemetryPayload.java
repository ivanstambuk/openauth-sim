package io.openauth.sim.rest.emv.cap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
final class EmvCapTelemetryPayload {

    private final TelemetryFrame frame;
    private final String reasonCode;

    EmvCapTelemetryPayload(TelemetryFrame frame, String reasonCode) {
        this.frame = Objects.requireNonNull(frame, "frame");
        this.reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
    }

    @JsonProperty("event")
    String event() {
        return frame.event();
    }

    @JsonProperty("status")
    String status() {
        return frame.status();
    }

    @JsonProperty("reasonCode")
    @Schema(
            description = "Machine-readable outcome code",
            allowableValues = {"generated", "match", "otp_mismatch", "invalid_input", "unexpected_error"})
    String reasonCode() {
        return reasonCode;
    }

    @JsonProperty("sanitized")
    boolean sanitized() {
        return frame.sanitized();
    }

    @JsonProperty("fields")
    Map<String, Object> fields() {
        return frame.fields();
    }
}
