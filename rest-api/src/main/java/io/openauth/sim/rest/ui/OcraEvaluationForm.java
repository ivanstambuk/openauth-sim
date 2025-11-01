package io.openauth.sim.rest.ui;

import io.openauth.sim.rest.EvaluationWindowRequest;
import io.openauth.sim.rest.ocra.OcraEvaluationRequest;
import java.util.Objects;

public final class OcraEvaluationForm {

    private static final String MODE_INLINE = "inline";
    private static final String MODE_CREDENTIAL = "credential";

    private String mode = MODE_INLINE;
    private String credentialId;
    private String suite;
    private String sharedSecretHex;
    private String sharedSecretBase32;
    private String challenge;
    private String sessionHex;
    private String clientChallenge;
    private String serverChallenge;
    private String pinHashHex;
    private String timestampHex;
    private Long counter;
    private String policyPreset;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        if (mode == null) {
            this.mode = MODE_INLINE;
            return;
        }
        String normalized = mode.trim().toLowerCase();
        if (MODE_CREDENTIAL.equals(normalized)) {
            this.mode = MODE_CREDENTIAL;
        } else {
            this.mode = MODE_INLINE;
        }
    }

    public String getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(String credentialId) {
        this.credentialId = trimOrNull(credentialId);
    }

    public String getSuite() {
        return suite;
    }

    public void setSuite(String suite) {
        this.suite = trimOrNull(suite);
    }

    public String getSharedSecretHex() {
        return sharedSecretHex;
    }

    public void setSharedSecretHex(String sharedSecretHex) {
        this.sharedSecretHex = trimOrNull(sharedSecretHex);
    }

    public String getSharedSecretBase32() {
        return sharedSecretBase32;
    }

    public void setSharedSecretBase32(String sharedSecretBase32) {
        this.sharedSecretBase32 = trimOrNull(sharedSecretBase32);
    }

    public String getChallenge() {
        return challenge;
    }

    public void setChallenge(String challenge) {
        this.challenge = trimOrNull(challenge);
    }

    public String getSessionHex() {
        return sessionHex;
    }

    public void setSessionHex(String sessionHex) {
        this.sessionHex = trimOrNull(sessionHex);
    }

    public String getClientChallenge() {
        return clientChallenge;
    }

    public void setClientChallenge(String clientChallenge) {
        this.clientChallenge = trimOrNull(clientChallenge);
    }

    public String getServerChallenge() {
        return serverChallenge;
    }

    public void setServerChallenge(String serverChallenge) {
        this.serverChallenge = trimOrNull(serverChallenge);
    }

    public String getPinHashHex() {
        return pinHashHex;
    }

    public void setPinHashHex(String pinHashHex) {
        this.pinHashHex = trimOrNull(pinHashHex);
    }

    public String getTimestampHex() {
        return timestampHex;
    }

    public void setTimestampHex(String timestampHex) {
        this.timestampHex = trimOrNull(timestampHex);
    }

    public Long getCounter() {
        return counter;
    }

    public void setCounter(Long counter) {
        this.counter = counter;
    }

    public String getPolicyPreset() {
        return policyPreset;
    }

    public void setPolicyPreset(String policyPreset) {
        this.policyPreset = trimOrNull(policyPreset);
    }

    public boolean isInlineMode() {
        return MODE_INLINE.equals(mode);
    }

    public boolean isCredentialMode() {
        return MODE_CREDENTIAL.equals(mode);
    }

    OcraEvaluationRequest toOcraRequest() {
        if (isInlineMode()) {
            return new OcraEvaluationRequest(
                    null,
                    suite,
                    sharedSecretHex,
                    sharedSecretBase32,
                    challenge,
                    sessionHex,
                    clientChallenge,
                    serverChallenge,
                    pinHashHex,
                    timestampHex,
                    counter,
                    new EvaluationWindowRequest(0, 0),
                    null);
        }
        String resolvedCredentialId = Objects.requireNonNull(credentialId, "credentialId");
        return new OcraEvaluationRequest(
                resolvedCredentialId,
                suite,
                null,
                null,
                challenge,
                sessionHex,
                clientChallenge,
                serverChallenge,
                pinHashHex,
                timestampHex,
                counter,
                new EvaluationWindowRequest(0, 0),
                null);
    }

    void scrubSecrets() {
        // Shared secret remains available for operator verification loops; only clear optional PIN
        // material to avoid accidental reuse.
        this.pinHashHex = null;
    }

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
