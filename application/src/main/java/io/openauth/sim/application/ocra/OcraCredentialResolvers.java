package io.openauth.sim.application.ocra;

import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.MapDbCredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.util.Objects;
import java.util.Optional;

/** Utility factory for shared OCRA credential resolvers. */
public final class OcraCredentialResolvers {

  private OcraCredentialResolvers() {
    throw new AssertionError("No instances");
  }

  public static OcraEvaluationApplicationService.CredentialResolver forStore(
      CredentialStore credentialStore) {
    Objects.requireNonNull(credentialStore, "credentialStore");
    OcraCredentialPersistenceAdapter persistenceAdapter = new OcraCredentialPersistenceAdapter();
    return credentialId ->
        credentialStore
            .findByName(credentialId)
            .filter(credential -> credential.type() == CredentialType.OATH_OCRA)
            .map(VersionedCredentialRecordMapper::toRecord)
            .map(
                record ->
                    new OcraEvaluationApplicationService.ResolvedCredential(
                        persistenceAdapter.deserialize(record)));
  }

  public static OcraEvaluationApplicationService.CredentialResolver emptyResolver() {
    return credentialId -> Optional.empty();
  }

  public static OcraVerificationApplicationService.CredentialResolver forVerificationStore(
      MapDbCredentialStore credentialStore) {
    return forVerificationStore((CredentialStore) credentialStore);
  }

  public static OcraVerificationApplicationService.CredentialResolver emptyVerificationResolver() {
    return credentialId -> Optional.empty();
  }

  public static OcraVerificationApplicationService.CredentialResolver forVerificationStore(
      CredentialStore credentialStore) {
    Objects.requireNonNull(credentialStore, "credentialStore");
    OcraCredentialPersistenceAdapter persistenceAdapter = new OcraCredentialPersistenceAdapter();
    return credentialId ->
        credentialStore
            .findByName(credentialId)
            .filter(credential -> credential.type() == CredentialType.OATH_OCRA)
            .map(VersionedCredentialRecordMapper::toRecord)
            .map(persistenceAdapter::deserialize);
  }
}
