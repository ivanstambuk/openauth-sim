package io.openauth.sim.core.store.testing;

import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

/** Utility helpers for seeding MapDB maintenance fixtures in tests. */
public final class MapDbMaintenanceFixtures {

    private static final String MAP_NAME = "credentials";

    private MapDbMaintenanceFixtures() {
        // no instances
    }

    /**
     * Writes a legacy OCRA record (schema version 0) missing the required suite attribute so that the
     * maintenance verifier emits an issue entry during migration.
     */
    public static void writeLegacyOcraRecordMissingSuite(
            Path databasePath, String credentialName, SecretMaterial secret) throws IOException {
        Instant createdAt = Instant.now().minusSeconds(120);
        Instant updatedAt = createdAt.plusSeconds(60);

        VersionedCredentialRecord record = new VersionedCredentialRecord(
                0,
                credentialName,
                CredentialType.OATH_OCRA,
                secret,
                createdAt,
                updatedAt,
                Map.of("metadata.placeholder", "value"));

        Path parent = databasePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (DB db = DBMaker.fileDB(databasePath.toFile())
                .fileMmapEnableIfSupported()
                .fileMmapPreclearDisable()
                .transactionEnable()
                .closeOnJvmShutdown()
                .make()) {
            @SuppressWarnings("unchecked")
            HTreeMap<String, VersionedCredentialRecord> map = (HTreeMap<String, VersionedCredentialRecord>)
                    db.hashMap(MAP_NAME, Serializer.STRING, Serializer.JAVA).createOrOpen();
            map.put(record.name(), record);
            db.commit();
        }
    }
}
