package io.openauth.sim.core.store;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.openauth.sim.core.model.Credential;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
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
  private final ConcurrentMap<String, Credential> backing;
  private final Cache<String, Credential> cache;

  private MapDbCredentialStore(
      DB db, ConcurrentMap<String, Credential> backing, Cache<String, Credential> cache) {
    this.db = db;
    this.backing = backing;
    this.cache = cache;
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
    backing.put(credential.name(), credential);
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
    Credential resolved = backing.get(name);
    if (resolved != null) {
      cache.put(name, resolved);
      return Optional.of(resolved);
    }
    return Optional.empty();
  }

  @Override
  public List<Credential> findAll() {
    return List.copyOf(backing.values());
  }

  @Override
  public boolean delete(String name) {
    Objects.requireNonNull(name, "name");
    Credential removed = backing.remove(name);
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
      ConcurrentMap<String, Credential> map =
          (ConcurrentMap<String, Credential>)
              db.hashMap(MAP_NAME, Serializer.STRING, Serializer.JAVA).createOrOpen();
      Cache<String, Credential> cache =
          Caffeine.newBuilder().maximumSize(cacheMaximumSize).expireAfterWrite(cacheTtl).build();
      return new MapDbCredentialStore(db, map, cache);
    }
  }
}
