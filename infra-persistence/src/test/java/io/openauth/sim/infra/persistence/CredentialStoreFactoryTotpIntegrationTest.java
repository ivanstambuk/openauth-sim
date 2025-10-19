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

final class CredentialStoreFactoryTotpIntegrationTest {

    @Test
    void coexistsOcraHotpTotpRecordsViaSharedFactory() throws Exception {
        CredentialType totpType = assertDoesNotThrow(() -> CredentialType.valueOf("OATH_TOTP"), "TOTP type must exist");

        Credential ocraCredential = Credential.create(
                "ocra-credential",
                CredentialType.OATH_OCRA,
                SecretMaterial.fromHex("31323334353637383930313233343536"),
                Map.of("suite", "OCRA-1:HOTP-SHA1-6:C-QN08"));

        Credential hotpCredential = Credential.create(
                "hotp-credential",
                CredentialType.OATH_HOTP,
                SecretMaterial.fromHex("3132333435363738393031323334353637383930"),
                Map.of("issuer", "test-lab"));

        Credential totpCredential = Credential.create(
                "totp-credential",
                totpType,
                SecretMaterial.fromStringUtf8("totp-shared-secret"),
                Map.of(
                        "totp.algorithm", "SHA1",
                        "totp.digits", "6",
                        "totp.stepSeconds", "30",
                        "totp.drift.backward", "1",
                        "totp.drift.forward", "1"));

        Path tempDir = Files.createTempDirectory("totp-coexistence");
        Path database = tempDir.resolve("credentials.db");

        try (CredentialStore store = CredentialStoreFactory.openFileStore(database)) {
            store.save(ocraCredential);
            store.save(hotpCredential);
            store.save(totpCredential);
        }

        try (CredentialStore store = CredentialStoreFactory.openFileStore(database)) {
            assertTrue(store.findByName("ocra-credential").isPresent());
            assertTrue(store.findByName("hotp-credential").isPresent());
            Credential persistedTotp = store.findByName("totp-credential").orElseThrow();
            assertEquals(totpType, persistedTotp.type());
            assertEquals("SHA1", persistedTotp.attributes().get("totp.algorithm"));
            assertEquals("6", persistedTotp.attributes().get("totp.digits"));
            assertEquals("30", persistedTotp.attributes().get("totp.stepSeconds"));
            assertEquals("1", persistedTotp.attributes().get("totp.drift.backward"));
            assertEquals("1", persistedTotp.attributes().get("totp.drift.forward"));
        }
    }
}
