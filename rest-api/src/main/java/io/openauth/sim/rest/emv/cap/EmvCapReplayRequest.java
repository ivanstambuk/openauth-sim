package io.openauth.sim.rest.emv.cap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
record EmvCapReplayRequest(
        @JsonProperty("credentialId") String credentialId,
        @JsonProperty("mode") String mode,
        @JsonProperty("otp") String otp,
        @JsonProperty("driftBackward") Integer driftBackward,
        @JsonProperty("driftForward") Integer driftForward,
        @JsonProperty("includeTrace") Boolean includeTrace,
        @JsonProperty("masterKey") String masterKey,
        @JsonProperty("atc") String atc,
        @JsonProperty("branchFactor") Integer branchFactor,
        @JsonProperty("height") Integer height,
        @JsonProperty("iv") String iv,
        @JsonProperty("cdol1") String cdol1,
        @JsonProperty("issuerProprietaryBitmap") String issuerProprietaryBitmap,
        @JsonProperty("customerInputs") CustomerInputs customerInputs,
        @JsonProperty("transactionData") TransactionData transactionData,
        @JsonProperty("iccDataTemplate") String iccDataTemplate,
        @JsonProperty("issuerApplicationData") String issuerApplicationData) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CustomerInputs(
            @JsonProperty("challenge") String challenge,
            @JsonProperty("reference") String reference,
            @JsonProperty("amount") String amount) {
        // canonical payload wrapper
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TransactionData(
            @JsonProperty("terminal") String terminal,
            @JsonProperty("icc") String icc) {
        // canonical transaction payload wrapper
    }
}
