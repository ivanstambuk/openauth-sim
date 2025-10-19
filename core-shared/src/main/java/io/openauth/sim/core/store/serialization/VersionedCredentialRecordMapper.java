package io.openauth.sim.core.store.serialization;

import io.openauth.sim.core.model.Credential;
import java.util.Objects;

/** Utility for converting between {@link Credential} aggregates and persistence records. */
public final class VersionedCredentialRecordMapper {

    private VersionedCredentialRecordMapper() {
        // Utility class
    }

    public static VersionedCredentialRecord toRecord(Credential credential) {
        Objects.requireNonNull(credential, "credential");
        return new VersionedCredentialRecord(
                VersionedCredentialRecord.CURRENT_VERSION,
                credential.name(),
                credential.type(),
                credential.secret(),
                credential.createdAt(),
                credential.updatedAt(),
                credential.attributes());
    }

    public static Credential toCredential(VersionedCredentialRecord record) {
        Objects.requireNonNull(record, "record");
        return new Credential(
                record.name(),
                record.type(),
                record.secret(),
                record.attributes(),
                record.createdAt(),
                record.updatedAt());
    }
}
