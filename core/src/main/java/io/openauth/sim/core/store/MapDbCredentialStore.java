package io.openauth.sim.core.store;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

/** {@link CredentialStore} implementation backed by MapDB with an in-memory caffeine cache. */
public final class MapDbCredentialStore implements CredentialStore {

  private static final String MAP_NAME = "credentials";
  private static final Logger TELEMETRY_LOGGER =
      Logger.getLogger("io.openauth.sim.core.store.persistence");

  static {
    TELEMETRY_LOGGER.setLevel(Level.FINE);
  }

  private final DB db;
  private final ConcurrentMap<String, VersionedCredentialRecord> backing;
  private final Cache<String, Credential> cache;
  private final List<VersionedCredentialRecordMigration> migrations;
  private final String storeProfile;

  private MapDbCredentialStore(
      DB db,
      ConcurrentMap<String, VersionedCredentialRecord> backing,
      Cache<String, Credential> cache,
      List<VersionedCredentialRecordMigration> migrations,
      String storeProfile) {
    this.db = db;
    this.backing = backing;
    this.cache = cache;
    this.migrations = migrations;
    this.storeProfile = storeProfile;
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
    long start = System.nanoTime();
    VersionedCredentialRecord record = VersionedCredentialRecordMapper.toRecord(credential);
    backing.put(credential.name(), record);
    db.commit();
    cache.put(credential.name(), credential);
    logMutationEvent(credential.name(), MutationOperation.SAVE, System.nanoTime() - start);
  }

  @Override
  public Optional<Credential> findByName(String name) {
    Objects.requireNonNull(name, "name");
    Credential cached = cache.getIfPresent(name);
    if (cached != null) {
      logLookupEvent(name, true, LookupSource.CACHE, 0L);
      return Optional.of(cached);
    }
    long start = System.nanoTime();
    VersionedCredentialRecord record = backing.get(name);
    if (record != null) {
      VersionedCredentialRecord upgraded = ensureLatest(name, record);
      Credential credential = VersionedCredentialRecordMapper.toCredential(upgraded);
      cache.put(name, credential);
      logLookupEvent(name, false, LookupSource.MAPDB, System.nanoTime() - start);
      return Optional.of(credential);
    }
    logLookupEvent(name, false, LookupSource.MAPDB_MISS, System.nanoTime() - start);
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
    long start = System.nanoTime();
    VersionedCredentialRecord removed = backing.remove(name);
    if (removed != null) {
      db.commit();
      cache.invalidate(name);
      logMutationEvent(name, MutationOperation.DELETE, System.nanoTime() - start);
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
    private CacheSettings cacheSettings;

    private List<VersionedCredentialRecordMigration> migrations =
        List.of(new OcraRecordSchemaV0ToV1Migration());

    private Builder(Path databasePath, boolean inMemory) {
      this.databasePath = databasePath;
      this.inMemory = inMemory;
      this.cacheSettings =
          inMemory ? CacheSettings.inMemoryDefaults() : CacheSettings.fileBackedDefaults();
    }

    public Builder cacheTtl(Duration ttl) {
      this.cacheSettings = cacheSettings.withTtl(Objects.requireNonNull(ttl, "ttl"));
      return this;
    }

    public Builder cacheMaximumSize(long maximumSize) {
      this.cacheSettings = cacheSettings.withMaximumSize(maximumSize);
      return this;
    }

    public Builder cacheExpirationStrategy(CacheSettings.ExpirationStrategy strategy) {
      this.cacheSettings = cacheSettings.withStrategy(Objects.requireNonNull(strategy, "strategy"));
      return this;
    }

    public Builder cacheSettings(CacheSettings cacheSettings) {
      this.cacheSettings = Objects.requireNonNull(cacheSettings, "cacheSettings");
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
      Cache<String, Credential> cache = buildCache();
      String profile = inMemory ? "IN_MEMORY" : "FILE";
      return new MapDbCredentialStore(db, map, cache, migrations, profile);
    }

    private Cache<String, Credential> buildCache() {
      Caffeine<Object, Object> builder =
          Caffeine.newBuilder().maximumSize(cacheSettings.maximumSize());
      if (cacheSettings.expirationStrategy() == CacheSettings.ExpirationStrategy.AFTER_ACCESS) {
        builder = builder.expireAfterAccess(cacheSettings.ttl());
      } else {
        builder = builder.expireAfterWrite(cacheSettings.ttl());
      }
      return builder.build();
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

  private void logLookupEvent(
      String credentialName, boolean cacheHit, LookupSource source, long latencyNanos) {
    if (!TELEMETRY_LOGGER.isLoggable(Level.FINE)) {
      return;
    }
    Map<String, String> payload = new LinkedHashMap<>();
    payload.put("storeProfile", storeProfile);
    payload.put("credentialName", credentialName);
    payload.put("cacheHit", Boolean.toString(cacheHit));
    payload.put("source", source.name());
    if (source != LookupSource.CACHE) {
      payload.put(
          "latencyMicros",
          Long.toString(TimeUnit.NANOSECONDS.toMicros(Math.max(latencyNanos, 0L))));
    }
    payload.put("redacted", Boolean.TRUE.toString());
    TELEMETRY_LOGGER.log(Level.FINE, "persistence.credential.lookup", new Object[] {payload});
  }

  private void logMutationEvent(
      String credentialName, MutationOperation operation, long latencyNanos) {
    if (!TELEMETRY_LOGGER.isLoggable(Level.FINE)) {
      return;
    }
    Map<String, String> payload = new LinkedHashMap<>();
    payload.put("storeProfile", storeProfile);
    payload.put("credentialName", credentialName);
    payload.put("operation", operation.name());
    payload.put(
        "latencyMicros", Long.toString(TimeUnit.NANOSECONDS.toMicros(Math.max(latencyNanos, 0L))));
    payload.put("redacted", Boolean.TRUE.toString());
    TELEMETRY_LOGGER.log(Level.FINE, "persistence.credential.mutation", new Object[] {payload});
  }

  private enum LookupSource {
    CACHE,
    MAPDB,
    MAPDB_MISS
  }

  private enum MutationOperation {
    SAVE,
    DELETE
  }

  public static final record CacheSettings(
      Duration ttl, long maximumSize, ExpirationStrategy expirationStrategy) {

    public CacheSettings {
      Objects.requireNonNull(ttl, "ttl");
      Objects.requireNonNull(expirationStrategy, "expirationStrategy");
      if (ttl.isZero() || ttl.isNegative()) {
        throw new IllegalArgumentException("ttl must be positive");
      }
      if (maximumSize <= 0) {
        throw new IllegalArgumentException("maximumSize must be positive");
      }
    }

    public CacheSettings withTtl(Duration ttl) {
      return new CacheSettings(ttl, maximumSize, expirationStrategy);
    }

    public CacheSettings withMaximumSize(long maximumSize) {
      return new CacheSettings(ttl, maximumSize, expirationStrategy);
    }

    public CacheSettings withStrategy(ExpirationStrategy strategy) {
      return new CacheSettings(ttl, maximumSize, strategy);
    }

    public static CacheSettings inMemoryDefaults() {
      return new CacheSettings(Duration.ofMinutes(2), 250_000, ExpirationStrategy.AFTER_ACCESS);
    }

    public static CacheSettings fileBackedDefaults() {
      return new CacheSettings(Duration.ofMinutes(10), 150_000, ExpirationStrategy.AFTER_WRITE);
    }

    public static CacheSettings containerDefaults() {
      return new CacheSettings(Duration.ofMinutes(15), 500_000, ExpirationStrategy.AFTER_ACCESS);
    }

    public enum ExpirationStrategy {
      AFTER_ACCESS,
      AFTER_WRITE
    }
  }
}
