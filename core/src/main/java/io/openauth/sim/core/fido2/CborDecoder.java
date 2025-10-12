package io.openauth.sim.core.fido2;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal CBOR decoder supporting the subset of types required for WebAuthn fixtures (unsigned and
 * negative integers, byte strings, UTF-8 strings, arrays, and maps).
 */
final class CborDecoder {

  private final byte[] data;
  private int index;

  private CborDecoder(byte[] data) {
    this.data = data;
  }

  static Object decode(byte[] data) throws GeneralSecurityException {
    CborDecoder decoder = new CborDecoder(data);
    Object result = decoder.readData();
    decoder.ensureFullyConsumed();
    return result;
  }

  private void ensureFullyConsumed() throws GeneralSecurityException {
    if (index != data.length) {
      throw new GeneralSecurityException("Unexpected trailing CBOR data");
    }
  }

  private Object readData() throws GeneralSecurityException {
    int initial = readUnsignedByte();
    int majorType = initial >>> 5;
    int additional = initial & 0x1F;

    return switch (majorType) {
      case 0 -> readLength(additional); // unsigned integer
      case 1 -> -1 - readLength(additional); // negative integer
      case 2 -> readByteString(additional);
      case 3 -> readTextString(additional);
      case 4 -> readArray(additional);
      case 5 -> readMap(additional);
      case 6 -> {
        // Skip tag information and decode the tagged value.
        readLength(additional);
        yield readData();
      }
      case 7 -> readSimpleValue(additional);
      default -> throw new GeneralSecurityException("Unsupported CBOR major type: " + majorType);
    };
  }

  private byte[] readByteString(int additionalInfo) throws GeneralSecurityException {
    int length = (int) readLength(additionalInfo);
    byte[] value = new byte[length];
    System.arraycopy(data, index, value, 0, length);
    index += length;
    return value;
  }

  private String readTextString(int additionalInfo) throws GeneralSecurityException {
    int length = (int) readLength(additionalInfo);
    String value = new String(data, index, length, StandardCharsets.UTF_8);
    index += length;
    return value;
  }

  private List<Object> readArray(int additionalInfo) throws GeneralSecurityException {
    int length = (int) readLength(additionalInfo);
    List<Object> list = new ArrayList<>(length);
    for (int i = 0; i < length; i++) {
      list.add(readData());
    }
    return list;
  }

  private Map<Object, Object> readMap(int additionalInfo) throws GeneralSecurityException {
    int length = (int) readLength(additionalInfo);
    Map<Object, Object> map = new LinkedHashMap<>(length);
    for (int i = 0; i < length; i++) {
      Object key = readData();
      Object value = readData();
      map.put(key, value);
    }
    return map;
  }

  private Object readSimpleValue(int additionalInfo) throws GeneralSecurityException {
    return switch (additionalInfo) {
      case 20 -> Boolean.FALSE;
      case 21 -> Boolean.TRUE;
      case 22 -> null;
      case 23 -> throw new GeneralSecurityException("Unsupported CBOR simple value: undefined");
      case 24 -> {
        int value = readUnsignedByte();
        yield (long) value;
      }
      case 25, 26, 27 ->
          throw new GeneralSecurityException("Floating-point CBOR values unsupported");
      case 31 -> throw new GeneralSecurityException("Indefinite-length items are unsupported");
      default ->
          throw new GeneralSecurityException("Unsupported CBOR simple value: " + additionalInfo);
    };
  }

  private long readLength(int additionalInfo) throws GeneralSecurityException {
    if (additionalInfo < 24) {
      return additionalInfo;
    }
    int lengthBytes =
        switch (additionalInfo) {
          case 24 -> 1;
          case 25 -> 2;
          case 26 -> 4;
          case 27 -> 8;
          default ->
              throw new GeneralSecurityException(
                  "Unsupported CBOR length additional info: " + additionalInfo);
        };
    if (index + lengthBytes > data.length) {
      throw new GeneralSecurityException("Truncated CBOR length");
    }
    long value = 0;
    for (int i = 0; i < lengthBytes; i++) {
      value = (value << 8) | (data[index++] & 0xFF);
    }
    return value;
  }

  private int readUnsignedByte() throws GeneralSecurityException {
    if (index >= data.length) {
      throw new GeneralSecurityException("Unexpected end of CBOR data");
    }
    return data[index++] & 0xFF;
  }
}
