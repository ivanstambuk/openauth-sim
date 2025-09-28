package io.openauth.sim.core.store.encryption;

import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.model.SecretMaterial;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-GCM implementation of {@link PersistenceEncryption} supporting caller-supplied keys. */
public final class AesGcmPersistenceEncryption implements PersistenceEncryption {

  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int GCM_TAG_LENGTH_BITS = 128;
  private static final int NONCE_LENGTH_BYTES = 12;
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String META_PREFIX = "encryption.";
  private static final String META_ALGORITHM = META_PREFIX + "algorithm";
  private static final String META_NONCE = META_PREFIX + "nonce";
  private static final String META_KEY_ID = META_PREFIX + "keyId";
  private static final String META_ORIGINAL_ENCODING = META_PREFIX + "originalEncoding";
  private final Supplier<char[]> keyIdSupplier;
  private final Supplier<byte[]> keySupplier;

  private AesGcmPersistenceEncryption(
      Supplier<byte[]> keySupplier, Supplier<char[]> keyIdSupplier) {
    this.keySupplier = Objects.requireNonNull(keySupplier, "keySupplier");
    this.keyIdSupplier = Objects.requireNonNullElseGet(keyIdSupplier, () -> () -> new char[0]);
  }

  public static AesGcmPersistenceEncryption withKeySupplier(Supplier<byte[]> keySupplier) {
    return new AesGcmPersistenceEncryption(keySupplier, null);
  }

  public static AesGcmPersistenceEncryption withKeySupplier(
      Supplier<byte[]> keySupplier, Supplier<char[]> keyIdSupplier) {
    return new AesGcmPersistenceEncryption(keySupplier, keyIdSupplier);
  }

  @Override
  public EncryptedSecret encrypt(String credentialName, SecretMaterial secret) {
    Objects.requireNonNull(credentialName, "credentialName");
    Objects.requireNonNull(secret, "secret");
    byte[] keyBytes = copyKey();
    validateKeyLength(keyBytes);

    byte[] nonce = new byte[NONCE_LENGTH_BYTES];
    RANDOM.nextBytes(nonce);
    GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);

    byte[] ciphertext;
    try {
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, toKey(keyBytes), parameterSpec);
      cipher.updateAAD(credentialName.getBytes(StandardCharsets.UTF_8));
      ciphertext = cipher.doFinal(secret.value());
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("Unable to encrypt secret", ex);
    }

    Map<String, String> metadata = new HashMap<>();
    metadata.put(META_ALGORITHM, ALGORITHM);
    metadata.put(META_NONCE, Base64.getEncoder().encodeToString(nonce));
    metadata.put(META_KEY_ID, new String(keyIdSupplier.get()));
    metadata.put(META_ORIGINAL_ENCODING, secret.encoding().name());

    SecretMaterial encryptedMaterial = new SecretMaterial(ciphertext, SecretEncoding.RAW);
    return new EncryptedSecret(encryptedMaterial, metadata);
  }

  @Override
  public SecretMaterial decrypt(
      String credentialName, SecretMaterial encryptedSecret, Map<String, String> metadata) {
    Objects.requireNonNull(credentialName, "credentialName");
    Objects.requireNonNull(encryptedSecret, "encryptedSecret");
    Objects.requireNonNull(metadata, "metadata");

    if (!ALGORITHM.equals(metadata.get(META_ALGORITHM))) {
      throw new IllegalStateException("Unexpected encryption algorithm metadata");
    }

    byte[] nonce = Base64.getDecoder().decode(metadata.get(META_NONCE));
    byte[] keyBytes = copyKey();
    validateKeyLength(keyBytes);

    try {
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(
          Cipher.DECRYPT_MODE, toKey(keyBytes), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
      cipher.updateAAD(credentialName.getBytes(StandardCharsets.UTF_8));
      byte[] plaintext = cipher.doFinal(encryptedSecret.value());
      SecretEncoding originalEncoding =
          SecretEncoding.valueOf(
              metadata.getOrDefault(META_ORIGINAL_ENCODING, SecretEncoding.RAW.name()));
      return new SecretMaterial(plaintext, originalEncoding);
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("Unable to decrypt secret", ex);
    }
  }

  private byte[] copyKey() {
    return Objects.requireNonNull(keySupplier.get(), "encryption key must not be null").clone();
  }

  private static void validateKeyLength(byte[] key) {
    int length = key.length;
    if (length != 16 && length != 24 && length != 32) {
      throw new IllegalArgumentException("AES key must be 128, 192, or 256 bits long");
    }
  }

  private static SecretKey toKey(byte[] keyBytes) {
    return new SecretKeySpec(keyBytes, "AES");
  }
}
