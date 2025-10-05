package io.openauth.sim.core.otp.hotp;

import java.util.LinkedHashMap;
import java.util.Map;

/** Helper for normalising HOTP persistence attributes. */
public final class HotpPersistenceDefaults {

  static final String COUNTER_ATTRIBUTE = "hotp.counter";

  private HotpPersistenceDefaults() {
    throw new AssertionError("No instances");
  }

  public static Map<String, String> ensureDefaults(Map<String, String> attributes) {
    Map<String, String> normalized = new LinkedHashMap<>(attributes);
    normalized.putIfAbsent(COUNTER_ATTRIBUTE, "0");
    return normalized;
  }
}
