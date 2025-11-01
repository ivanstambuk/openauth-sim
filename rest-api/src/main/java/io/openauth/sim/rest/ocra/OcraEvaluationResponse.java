package io.openauth.sim.rest.ocra;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.openauth.sim.rest.OtpPreviewResponse;
import io.openauth.sim.rest.VerboseTracePayload;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OcraEvaluationResponse(
        String suite, String otp, String telemetryId, List<OtpPreviewResponse> previews, VerboseTracePayload trace) {

    public OcraEvaluationResponse {
        suite = suite == null ? null : suite.trim();
        otp = otp == null ? null : otp.trim();
        telemetryId = telemetryId == null ? null : telemetryId.trim();
    }
}
