package io.openauth.sim.rest.emv.cap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.EvaluationWindowRequest;

@JsonInclude(JsonInclude.Include.NON_NULL)
record EmvCapEvaluationRequest(
        @JsonProperty("credentialId") String credentialId,
        @JsonProperty("mode") String mode,
        @JsonProperty("masterKey") String masterKey,
        @JsonProperty("atc") String atc,
        @JsonProperty("branchFactor") Integer branchFactor,
        @JsonProperty("height") Integer height,
        @JsonProperty("iv") String iv,
        @JsonProperty("cdol1") String cdol1,
        @JsonProperty("issuerProprietaryBitmap") String issuerProprietaryBitmap,
        @JsonProperty("previewWindow") EvaluationWindowRequest previewWindow,
        @JsonProperty("customerInputs") CustomerInputs customerInputs,
        @JsonProperty("transactionData") TransactionData transactionData,
        @JsonProperty("iccDataTemplate") String iccDataTemplate,
        @JsonProperty("issuerApplicationData") String issuerApplicationData,
        @JsonProperty("includeTrace") Boolean includeTrace) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CustomerInputs(
            @JsonProperty("challenge") String challenge,
            @JsonProperty("reference") String reference,
            @JsonProperty("amount") String amount) {
        // no members
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TransactionData(
            @JsonProperty("terminal") String terminal,
            @JsonProperty("icc") String icc) {
        // no members
    }
}
