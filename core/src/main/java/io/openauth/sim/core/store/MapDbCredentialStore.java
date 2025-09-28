package io.openauth.sim.core.store;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

/** {@link CredentialStore} implementation backed by MapDB with an in-memory caffeine cache. */
public final class MapDbCredentialStore implements CredentialStore {

  private static final String MAP_NAME = "credentials";

  private final DB db;
  private final ConcurrentMap<String, VersionedCredentialRecord> backing;
  private final Cache<String, Credential> cache;
  private final List<VersionedCredentialRecordMigration> migrations;

  private MapDbCredentialStore(
      DB db,
      ConcurrentMap<String, VersionedCredentialRecord> backing,
      Cache<String, Credential> cache,
      List<VersionedCredentialRecordMigration> migrations) {
    this.db = db;
    this.backing = backing;
    this.cache = cache;
    this.migrations = migrations;
    upgradePersistedRecords();
  }

  public static Builder file(Path databasePath) {
    Objects.requireNonNull(databasePath, "databasePath");
    return new Builder(databasePath, false);
  }

  public static Builder inMemory() {
    return new Builder(null, true);
  }

  @Override
  public void save(Credential credential) {
    Objects.requireNonNull(credential, "credential");
    VersionedCredentialRecord record = VersionedCredentialRecordMapper.toRecord(credential);
    backing.put(credential.name(), record);
    db.commit();
    cache.put(credential.name(), credential);
  }

  @Override
  public Optional<Credential> findByName(String name) {
    Objects.requireNonNull(name, "name");
    Credential cached = cache.getIfPresent(name);
    if (cached != null) {
      return Optional.of(cached);
    }
    VersionedCredentialRecord record = backing.get(name);
    if (record != null) {
      VersionedCredentialRecord upgraded = ensureLatest(name, record);
      Credential credential = VersionedCredentialRecordMapper.toCredential(upgraded);
      cache.put(name, credential);
      return Optional.of(credential);
    }
    return Optional.empty();
  }

  @Override
  public List<Credential> findAll() {
    return backing.entrySet().stream()
        .map(
            entry ->
                VersionedCredentialRecordMapper.toCredential(
                    ensureLatest(entry.getKey(), entry.getValue())))
        .toList();
  }

  @Override
  public boolean delete(String name) {
    Objects.requireNonNull(name, "name");
    VersionedCredentialRecord removed = backing.remove(name);
    if (removed != null) {
      db.commit();
      cache.invalidate(name);
      return true;
    }
    return false;
  }

  @Override
  public void close() {
    cache.invalidateAll();
    db.close();
  }

  public static final class Builder {
    private final Path databasePath;
    private final boolean inMemory;
    private Duration cacheTtl = Duration.ofMinutes(5);
    private long cacheMaximumSize = 100_000;

    private List<VersionedCredentialRecordMigration> migrations =
        List.of(new OcraRecordSchemaV0ToV1Migration());

    private Builder(Path databasePath, boolean inMemory) {
      this.databasePath = databasePath;
      this.inMemory = inMemory;
    }

    public Builder cacheTtl(Duration ttl) {
      this.cacheTtl = Objects.requireNonNull(ttl, "ttl");
      return this;
    }

    public Builder cacheMaximumSize(long maximumSize) {
      if (maximumSize <= 0) {
        throw new IllegalArgumentException("maximumSize must be positive");
      }
      this.cacheMaximumSize = maximumSize;
      return this;
    }

    public MapDbCredentialStore open() {
      DBMaker.Maker maker =
          inMemory
              ? DBMaker.memoryDB()
              : DBMaker.fileDB(databasePath.toFile())
                  .fileMmapEnableIfSupported()
                  .fileMmapPreclearDisable();
      maker = maker.transactionEnable().closeOnJvmShutdown();

      DB db = maker.make();
      @SuppressWarnings("unchecked")
      ConcurrentMap<String, VersionedCredentialRecord> map =
          (ConcurrentMap<String, VersionedCredentialRecord>)
              db.hashMap(MAP_NAME, Serializer.STRING, Serializer.JAVA).createOrOpen();
      Cache<String, Credential> cache =
          Caffeine.newBuilder().maximumSize(cacheMaximumSize).expireAfterWrite(cacheTtl).build();
      return new MapDbCredentialStore(db, map, cache, migrations);
    }
  }

  private void upgradePersistedRecords() {
    boolean updated = false;
    for (Map.Entry<String, VersionedCredentialRecord> entry : backing.entrySet()) {
      VersionedCredentialRecord latest = ensureLatest(entry.getKey(), entry.getValue());
      if (latest != entry.getValue()) {
        backing.put(entry.getKey(), latest);
        updated = true;
      }
    }
    if (updated) {
      db.commit();
    }
  }

  private VersionedCredentialRecord ensureLatest(String name, VersionedCredentialRecord record) {
    VersionedCredentialRecord current = record;
    if (current.schemaVersion() == VersionedCredentialRecord.CURRENT_VERSION) {
      return current;
    }
    for (VersionedCredentialRecordMigration migration : migrations) {
      if (migration.supports(current.type(), current.schemaVersion())) {
        current = migration.upgrade(current);
      }
    }
    if (current.schemaVersion() != VersionedCredentialRecord.CURRENT_VERSION) {
      throw new IllegalStateException(
          "No migration path to latest schema for credential '" + name + "'");
    }
    backing.put(name, current);
    return current;
  }
}
