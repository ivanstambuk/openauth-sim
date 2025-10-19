package io.openauth.sim.infra.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.CredentialStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class CredentialStoreFactoryHotpIntegrationTest {

    @Test
    void coexistsOcraAndHotpRecordsViaSharedFactory() throws Exception {
        CredentialType hotpType = assertDoesNotThrow(() -> CredentialType.valueOf("OATH_HOTP"), "HOTP type must exist");

        Credential ocraCredential = Credential.create(
                "ocra-credential",
                CredentialType.OATH_OCRA,
                SecretMaterial.fromHex("31323334353637383930313233343536"),
                Map.of("suite", "OCRA-1:HOTP-SHA1-6:C-QN08"));

        Credential hotpCredential = Credential.create(
                "hotp-credential",
                hotpType,
                SecretMaterial.fromHex("3132333435363738393031323334353637383930"),
                Map.of("issuer", "test-lab"));

        Path tempDir = Files.createTempDirectory("hotp-coexistence");
        Path database = tempDir.resolve("credentials.db");

        try (CredentialStore store = CredentialStoreFactory.openFileStore(database)) {
            store.save(ocraCredential);
            store.save(hotpCredential);
        }

        try (CredentialStore store = CredentialStoreFactory.openFileStore(database)) {
            assertTrue(store.findByName("ocra-credential").isPresent());
            Credential hotp = store.findByName("hotp-credential").orElseThrow();
            assertEquals(hotpType, hotp.type());
            assertEquals("0", hotp.attributes().get("hotp.counter"));
        }
    }
}
