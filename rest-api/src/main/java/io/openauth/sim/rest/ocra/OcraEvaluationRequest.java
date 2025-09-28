package io.openauth.sim.rest.ocra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record OcraEvaluationRequest(
    String credentialId,
    String suite,
    String sharedSecretHex,
    String challenge,
    String sessionHex,
    String clientChallenge,
    String serverChallenge,
    String pinHashHex,
    String timestampHex,
    Long counter) {

  public OcraEvaluationRequest {
    credentialId = trimOrNull(credentialId);
    suite = trimOrNull(suite);
    sharedSecretHex = trimOrNull(sharedSecretHex);
    challenge = trimOrNull(challenge);
    sessionHex = trimOrNull(sessionHex);
    clientChallenge = trimOrNull(clientChallenge);
    serverChallenge = trimOrNull(serverChallenge);
    pinHashHex = trimOrNull(pinHashHex);
    timestampHex = trimOrNull(timestampHex);
  }

  private static String trimOrNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
