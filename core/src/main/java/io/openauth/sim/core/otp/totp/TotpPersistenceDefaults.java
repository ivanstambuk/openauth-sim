package io.openauth.sim.core.otp.totp;

import java.util.LinkedHashMap;
import java.util.Map;

/** Helper for normalising TOTP persistence attributes. */
public final class TotpPersistenceDefaults {

  public static final String ALGORITHM_ATTRIBUTE = "totp.algorithm";
  public static final String DIGITS_ATTRIBUTE = "totp.digits";
  public static final String STEP_SECONDS_ATTRIBUTE = "totp.stepSeconds";
  public static final String DRIFT_BACKWARD_ATTRIBUTE = "totp.drift.backward";
  public static final String DRIFT_FORWARD_ATTRIBUTE = "totp.drift.forward";

  private TotpPersistenceDefaults() {
    throw new AssertionError("No instances");
  }

  public static Map<String, String> ensureDefaults(Map<String, String> attributes) {
    Map<String, String> normalized = new LinkedHashMap<>(attributes);
    normalized.putIfAbsent(ALGORITHM_ATTRIBUTE, "SHA1");
    normalized.putIfAbsent(DIGITS_ATTRIBUTE, "6");
    normalized.putIfAbsent(STEP_SECONDS_ATTRIBUTE, "30");
    normalized.putIfAbsent(DRIFT_BACKWARD_ATTRIBUTE, "1");
    normalized.putIfAbsent(DRIFT_FORWARD_ATTRIBUTE, "1");
    return normalized;
  }
}
