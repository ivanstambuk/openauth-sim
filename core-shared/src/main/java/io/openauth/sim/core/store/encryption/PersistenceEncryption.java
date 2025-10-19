package io.openauth.sim.core.store.encryption;

import io.openauth.sim.core.model.SecretMaterial;
import java.util.Map;

/** Strategy for optionally encrypting credential secrets before persisting them. */
public interface PersistenceEncryption {

    /**
     * Encrypt the provided secret for the given credential name.
     *
     * @param credentialName unique credential identifier, used for associated data if supported.
     * @param secret plaintext secret material.
     * @return encrypted secret along with metadata that must be stored to enable future decryption.
     */
    EncryptedSecret encrypt(String credentialName, SecretMaterial secret);

    /**
     * Decrypt the previously encrypted secret using the stored metadata.
     *
     * @param credentialName unique credential identifier (matches the name passed to {@link
     *     #encrypt}).
     * @param encryptedSecret encrypted secret material returned from {@link #encrypt}.
     * @param metadata metadata map captured during encryption.
     * @return decrypted secret material.
     */
    SecretMaterial decrypt(String credentialName, SecretMaterial encryptedSecret, Map<String, String> metadata);

    /** Immutable view of an encrypted secret with metadata required for decryption. */
    record EncryptedSecret(SecretMaterial secret, Map<String, String> metadata) {

        public EncryptedSecret {
            secret = java.util.Objects.requireNonNull(secret, "secret");
            metadata = Map.copyOf(java.util.Objects.requireNonNull(metadata, "metadata"));
        }
    }
}
