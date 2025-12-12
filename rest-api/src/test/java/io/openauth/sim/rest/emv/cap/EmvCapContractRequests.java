package io.openauth.sim.rest.emv.cap;

import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService;
import io.openauth.sim.application.emv.cap.EmvCapReplayApplicationService;

final class EmvCapContractRequests {

    private EmvCapContractRequests() {
        throw new AssertionError("No instances");
    }

    static EmvCapEvaluationRequest evaluateInline(EmvCapEvaluationApplicationService.EvaluationRequest request) {
        return new EmvCapEvaluationRequest(
                null,
                request.mode().name(),
                request.masterKeyHex(),
                request.atcHex(),
                request.branchFactor(),
                request.height(),
                request.ivHex(),
                request.cdol1Hex(),
                request.issuerProprietaryBitmapHex(),
                null,
                new EmvCapEvaluationRequest.CustomerInputs(
                        request.customerInputs().challenge(),
                        request.customerInputs().reference(),
                        request.customerInputs().amount()),
                new EmvCapEvaluationRequest.TransactionData(
                        request.transactionData().terminalHexOverride().orElse(null),
                        request.transactionData().iccHexOverride().orElse(null)),
                request.iccDataTemplateHex(),
                request.issuerApplicationDataHex(),
                false);
    }

    static EmvCapReplayRequest replayStored(EmvCapReplayApplicationService.ReplayCommand.Stored stored) {
        return new EmvCapReplayRequest(
                stored.credentialId(),
                stored.mode().name(),
                stored.otp(),
                stored.driftBackward(),
                stored.driftForward(),
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
