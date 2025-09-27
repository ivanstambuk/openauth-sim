package io.openauth.sim.core.model;

import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/** Immutable wrapper for credential secret material. */
public record SecretMaterial(byte[] value, SecretEncoding encoding) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  public SecretMaterial {
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(encoding, "encoding");
    value = value.clone();
  }

  public static SecretMaterial fromHex(String hex) {
    Objects.requireNonNull(hex, "hex");
    return new SecretMaterial(HexFormat.of().parseHex(hex.replace(" ", "")), SecretEncoding.HEX);
  }

  public static SecretMaterial fromBase64(String base64) {
    Objects.requireNonNull(base64, "base64");
    return new SecretMaterial(Base64.getDecoder().decode(base64), SecretEncoding.BASE64);
  }

  public static SecretMaterial fromStringUtf8(String value) {
    Objects.requireNonNull(value, "value");
    return new SecretMaterial(value.getBytes(StandardCharsets.UTF_8), SecretEncoding.RAW);
  }

  public static SecretMaterial fromBytes(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes");
    return new SecretMaterial(bytes.clone(), SecretEncoding.RAW);
  }

  @Override
  public byte[] value() {
    return value.clone();
  }

  public String asHex() {
    return HexFormat.of().formatHex(value);
  }

  public String asBase64() {
    return Base64.getEncoder().withoutPadding().encodeToString(value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SecretMaterial that)) {
      return false;
    }
    return encoding == that.encoding && Arrays.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return 31 * encoding.hashCode() + Arrays.hashCode(value);
  }
}
