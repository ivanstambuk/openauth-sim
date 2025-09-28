package io.openauth.sim.core.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.benmanes.caffeine.cache.Cache;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.encryption.AesGcmPersistenceEncryption;
import io.openauth.sim.core.store.encryption.PersistenceEncryption;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

class MapDbCredentialStoreTest {

  @TempDir Path tempDir;

  @Test
  void persistsAndLoadsCredentials() {
    Path dbPath = tempDir.resolve("credentials.db");
    Credential credential =
        Credential.create(
            "credential-1",
            CredentialType.GENERIC,
            SecretMaterial.fromHex("a1b2c3d4"),
            Map.of("label", "demo"));

    try (var store = MapDbCredentialStore.file(dbPath).open()) {
      store.save(credential);
    }

    Optional<Credential> loaded;
    try (var store = MapDbCredentialStore.file(dbPath).open()) {
      loaded = store.findByName("credential-1");
    }

    assertTrue(loaded.isPresent(), "credential should be reloaded from disk");
    assertEquals(credential.name(), loaded.orElseThrow().name());
    assertEquals(credential.secret(), loaded.orElseThrow().secret());
    assertEquals(
        VersionedCredentialRecord.CURRENT_VERSION,
        readRawRecord(dbPath, "credential-1").schemaVersion());
  }

  @Test
  void deletingRemovesCredentialAndCache() {
    Path dbPath = tempDir.resolve("credentials.db");
    Credential credential =
        Credential.create(
            "credential-2",
            CredentialType.OATH_OCRA,
            SecretMaterial.fromStringUtf8("otp-secret"),
            Map.of());

    try (var store = MapDbCredentialStore.file(dbPath).open()) {
      store.save(credential);
      assertTrue(store.delete("credential-2"));
      assertFalse(store.delete("credential-2"), "second delete should report false");
      assertTrue(store.findAll().isEmpty(), "store should be empty after delete");
    }
  }

  @Test
  void saveOverridesExistingCredential() {
    Path dbPath = tempDir.resolve("credentials.db");
    Credential initial =
        Credential.create(
            "credential-3", CredentialType.FIDO2, SecretMaterial.fromHex("0011"), Map.of());

    try (var store = MapDbCredentialStore.file(dbPath).open()) {
      store.save(initial);
      Credential updated = initial.withSecret(SecretMaterial.fromHex("ffee"));
      store.save(updated);
      Credential resolved = store.findByName("credential-3").orElseThrow();
      assertEquals(updated.secret(), resolved.secret());
      assertNotEquals(
          initial.updatedAt(), resolved.updatedAt(), "save should refresh update timestamp");
    }
  }

  @Test
  void inMemoryStoreSupportsTransientUsage() {
    try (var store = MapDbCredentialStore.inMemory().open()) {
      store.save(
          Credential.create(
              "mem",
              CredentialType.EMV_CA,
              SecretMaterial.fromBase64("YWJj"),
              Map.of("note", "transient")));
      List<Credential> credentials = store.findAll();
      assertEquals(1, credentials.size());
      assertEquals("mem", credentials.get(0).name());
    }
  }

  @Test
  void upgradesLegacyOcraRecord() {
    Path dbPath = tempDir.resolve("legacy.db");

    try (DB db = DBMaker.fileDB(dbPath.toFile()).transactionEnable().closeOnJvmShutdown().make()) {
      @SuppressWarnings("unchecked")
      var map =
          (org.mapdb.HTreeMap<String, VersionedCredentialRecord>)
              db.hashMap("credentials", Serializer.STRING, Serializer.JAVA).createOrOpen();
      map.put(
          "legacy-token",
          new VersionedCredentialRecord(
              0,
              "legacy-token",
              CredentialType.OATH_OCRA,
              SecretMaterial.fromHex("3132333435363738393031323334353637383930"),
              java.time.Instant.parse("2025-09-20T10:15:30Z"),
              java.time.Instant.parse("2025-09-21T11:16:31Z"),
              Map.of(
                  "suite", "OCRA-1:HOTP-SHA1-6:C-QN08",
                  "counter", "5",
                  "metadata.environment", "production")));
      db.commit();
    }

    try (var store = MapDbCredentialStore.file(dbPath).open()) {
      Credential credential = store.findByName("legacy-token").orElseThrow();
      assertEquals(CredentialType.OATH_OCRA, credential.type());
      assertEquals("legacy-token", credential.name());
      assertEquals("production", credential.attributes().get("ocra.metadata.environment"));
    }

    VersionedCredentialRecord migrated = readRawRecord(dbPath, "legacy-token");
    assertEquals(VersionedCredentialRecord.CURRENT_VERSION, migrated.schemaVersion());
    assertEquals("OCRA-1:HOTP-SHA1-6:C-QN08", migrated.attributes().get("ocra.suite"));
    assertEquals("5", migrated.attributes().get("ocra.counter"));
  }

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "LG_LOST_LOGGER_DUE_TO_WEAK_REFERENCE",
      justification = "Test attaches a temporary handler to verify telemetry and restores state")
  @Test
  void structuredTelemetryEmittedForPersistenceOperations() {
    Logger telemetryLogger = Logger.getLogger("io.openauth.sim.core.store.persistence");
    TestLogHandler handler = new TestLogHandler();
    telemetryLogger.addHandler(handler);
    telemetryLogger.setLevel(Level.FINE);

    Credential credential =
        Credential.create(
            "telemetry-test",
            CredentialType.GENERIC,
            SecretMaterial.fromStringUtf8("super-secret-value"),
            Map.of());

    Path telemetryDb = tempDir.resolve("telemetry.db");

    try (var store = MapDbCredentialStore.file(telemetryDb).open()) {
      store.save(credential);
    }

    try (var store = MapDbCredentialStore.file(telemetryDb).open()) {
      store.findByName(credential.name()).orElseThrow();
      store.findByName(credential.name()).orElseThrow();
      assertTrue(store.findByName("missing").isEmpty());
    } finally {
      telemetryLogger.removeHandler(handler);
    }

    List<LogRecord> records = handler.records();
    List<LogRecord> mutationEvents =
        records.stream()
            .filter(record -> "persistence.credential.mutation".equals(record.getMessage()))
            .toList();
    List<LogRecord> lookupEvents =
        records.stream()
            .filter(record -> "persistence.credential.lookup".equals(record.getMessage()))
            .toList();

    assertEquals(1, mutationEvents.size(), "One mutation event expected");
    LogRecord mutation = mutationEvents.get(0);
    assertEquals(Level.FINE, mutation.getLevel());
    Map<String, String> mutationPayload = extractPayload(mutation);
    assertEquals(
        Set.of("storeProfile", "credentialName", "operation", "latencyMicros", "redacted"),
        mutationPayload.keySet());
    assertEquals("FILE", mutationPayload.get("storeProfile"));
    assertEquals("telemetry-test", mutationPayload.get("credentialName"));
    assertEquals("SAVE", mutationPayload.get("operation"));
    assertEquals("true", mutationPayload.get("redacted"));
    long mutationLatency = Long.parseLong(mutationPayload.get("latencyMicros"));
    assertTrue(mutationLatency >= 0, "latencyMicros should be non-negative");

    assertEquals(3, lookupEvents.size(), "Expected cache hit, persistence hit, and miss events");
    lookupEvents.forEach(record -> assertEquals(Level.FINE, record.getLevel()));

    Map<String, Map<String, String>> payloadBySource = new HashMap<>();
    for (LogRecord record : lookupEvents) {
      Map<String, String> payload = extractPayload(record);
      payloadBySource.put(payload.get("source"), payload);
    }

    assertEquals(
        Set.of("MAPDB", "CACHE", "MAPDB_MISS"),
        payloadBySource.keySet(),
        "Unexpected lookup sources");

    Map<String, String> mapDbHitPayload = payloadBySource.get("MAPDB");
    assertNotNull(mapDbHitPayload, "MAPDB lookup event missing");
    assertEquals(
        Set.of("storeProfile", "credentialName", "cacheHit", "source", "latencyMicros", "redacted"),
        mapDbHitPayload.keySet());
    assertEquals("false", mapDbHitPayload.get("cacheHit"));
    assertEquals("telemetry-test", mapDbHitPayload.get("credentialName"));
    assertTrue(Long.parseLong(mapDbHitPayload.get("latencyMicros")) >= 0);

    Map<String, String> cacheHitPayload = payloadBySource.get("CACHE");
    assertNotNull(cacheHitPayload, "CACHE lookup event missing");
    assertEquals(
        Set.of("storeProfile", "credentialName", "cacheHit", "source", "redacted"),
        cacheHitPayload.keySet());
    assertEquals("true", cacheHitPayload.get("cacheHit"));
    assertEquals("telemetry-test", cacheHitPayload.get("credentialName"));

    Map<String, String> missPayload = payloadBySource.get("MAPDB_MISS");
    assertNotNull(missPayload, "MAPDB_MISS lookup event missing");
    assertEquals(
        Set.of("storeProfile", "credentialName", "cacheHit", "source", "latencyMicros", "redacted"),
        missPayload.keySet());
    assertEquals("false", missPayload.get("cacheHit"));
    assertEquals("missing", missPayload.get("credentialName"));
    assertTrue(Long.parseLong(missPayload.get("latencyMicros")) >= 0);

    payloadBySource.values().forEach(payload -> assertEquals("true", payload.get("redacted")));
    payloadBySource
        .values()
        .forEach(
            payload ->
                assertFalse(
                    payload.containsKey("secret"), "Payload must not expose secret fields"));
    payloadBySource.values().forEach(payload -> assertEquals("FILE", payload.get("storeProfile")));
  }

  @Test
  void encryptionEncryptsSecretsAtRestAndDecryptsOnRead() throws Exception {
    byte[] key = new byte[32];
    Arrays.fill(key, (byte) 0x5A);
    PersistenceEncryption encryption =
        AesGcmPersistenceEncryption.withKeySupplier(() -> key.clone());

    Path dbPath = tempDir.resolve("encrypted.db");
    Credential original =
        Credential.create(
            "secure",
            CredentialType.GENERIC,
            SecretMaterial.fromStringUtf8("super-secret"),
            Map.of("purpose", "test"));

    try (var store = MapDbCredentialStore.file(dbPath).encryption(encryption).open()) {
      store.save(original);
    }

    VersionedCredentialRecord rawRecord = readRawRecord(dbPath, "secure");
    assertNotEquals(
        original.secret().asBase64(),
        SecretMaterial.fromBytes(rawRecord.secret().value()).asBase64(),
        "secret should be encrypted at rest");
    assertTrue(rawRecord.attributes().containsKey("encryption.algorithm"));

    try (var store = MapDbCredentialStore.file(dbPath).encryption(encryption).open()) {
      Credential loaded = store.findByName("secure").orElseThrow();
      assertEquals(original.secret(), loaded.secret());
      assertEquals("test", loaded.attributes().get("purpose"));
      assertFalse(loaded.attributes().containsKey("encryption.algorithm"));
      assertFalse(loaded.attributes().containsKey("encryption.nonce"));
    }
  }

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "LG_LOST_LOGGER_DUE_TO_WEAK_REFERENCE",
      justification = "Test attaches a temporary handler to verify maintenance telemetry")
  @Test
  void maintenanceHelperEmitsResultsAndTelemetry() {
    Logger telemetryLogger = Logger.getLogger("io.openauth.sim.core.store.persistence");
    TestLogHandler handler = new TestLogHandler();
    telemetryLogger.addHandler(handler);
    telemetryLogger.setLevel(Level.FINE);

    Path dbPath = tempDir.resolve("maintenance.db");

    MapDbCredentialStore.MaintenanceBundle opened =
        MapDbCredentialStore.file(dbPath).openWithMaintenance();

    try (var store = opened.store()) {
      store.save(
          Credential.create(
              "maint", CredentialType.GENERIC, SecretMaterial.fromHex("feedface"), Map.of()));

      MapDbCredentialStore.MaintenanceHelper maintenance = opened.maintenance();

      MapDbCredentialStore.MaintenanceResult compactionResult = maintenance.compact();
      assertEquals(
          MapDbCredentialStore.MaintenanceOperation.COMPACTION, compactionResult.operation());
      assertTrue(compactionResult.duration().toNanos() >= 0L);
      assertEquals(1L, compactionResult.entriesScanned());
      assertEquals(0L, compactionResult.entriesRepaired());
      assertEquals(MapDbCredentialStore.MaintenanceStatus.SUCCESS, compactionResult.status());
      assertTrue(compactionResult.issues().isEmpty());

      MapDbCredentialStore.MaintenanceResult integrityResult = maintenance.verifyIntegrity();
      assertEquals(
          MapDbCredentialStore.MaintenanceOperation.INTEGRITY_CHECK, integrityResult.operation());
      assertTrue(integrityResult.duration().toNanos() >= 0L);
      assertEquals(1L, integrityResult.entriesScanned());
      assertEquals(0L, integrityResult.entriesRepaired());
      assertEquals(MapDbCredentialStore.MaintenanceStatus.SUCCESS, integrityResult.status());
      assertTrue(integrityResult.issues().isEmpty());
    } finally {
      telemetryLogger.removeHandler(handler);
    }

    List<LogRecord> maintenanceEvents =
        handler.records().stream()
            .filter(record -> "persistence.credential.maintenance".equals(record.getMessage()))
            .toList();

    assertEquals(2, maintenanceEvents.size(), "Expected maintenance telemetry for each operation");

    maintenanceEvents.forEach(record -> assertEquals(Level.FINE, record.getLevel()));

    Map<String, Map<String, String>> payloadsByOperation = new HashMap<>();
    for (LogRecord record : maintenanceEvents) {
      Map<String, String> payload = extractPayload(record);
      payloadsByOperation.put(payload.get("operation"), payload);
      assertEquals("true", payload.get("redacted"));
      assertEquals("FILE", payload.get("storeProfile"));
      assertTrue(Long.parseLong(payload.get("durationMicros")) >= 0L);
    }

    Map<String, String> compactionPayload = payloadsByOperation.get("COMPACTION");
    assertNotNull(compactionPayload);
    assertEquals("SUCCESS", compactionPayload.get("status"));
    assertEquals("1", compactionPayload.get("entriesScanned"));
    assertEquals("0", compactionPayload.get("entriesRepaired"));
    assertEquals("0", compactionPayload.get("issues"));

    Map<String, String> integrityPayload = payloadsByOperation.get("INTEGRITY_CHECK");
    assertNotNull(integrityPayload);
    assertEquals("SUCCESS", integrityPayload.get("status"));
    assertEquals("1", integrityPayload.get("entriesScanned"));
    assertEquals("0", integrityPayload.get("entriesRepaired"));
    assertEquals("0", integrityPayload.get("issues"));
  }

  @Test
  void inMemoryCacheDefaultsUseExpireAfterAccess() throws Exception {
    try (var store = MapDbCredentialStore.inMemory().open()) {
      Cache<String, Credential> cache = extractCache(store);
      Duration expectedTtl = MapDbCredentialStore.CacheSettings.inMemoryDefaults().ttl();
      long actualTtlNanos =
          cache.policy().expireAfterAccess().orElseThrow().getExpiresAfter(TimeUnit.NANOSECONDS);
      assertEquals(expectedTtl, Duration.ofNanos(actualTtlNanos));
      assertTrue(cache.policy().expireAfterWrite().isEmpty());
      long maximum = cache.policy().eviction().orElseThrow().getMaximum();
      assertEquals(MapDbCredentialStore.CacheSettings.inMemoryDefaults().maximumSize(), maximum);
    }
  }

  @Test
  void fileBackedCacheDefaultsUseExpireAfterWrite() throws Exception {
    Path dbPath = tempDir.resolve("file-defaults.db");
    try (var store = MapDbCredentialStore.file(dbPath).open()) {
      Cache<String, Credential> cache = extractCache(store);
      Duration expectedTtl = MapDbCredentialStore.CacheSettings.fileBackedDefaults().ttl();
      long actualTtlNanos =
          cache.policy().expireAfterWrite().orElseThrow().getExpiresAfter(TimeUnit.NANOSECONDS);
      assertEquals(expectedTtl, Duration.ofNanos(actualTtlNanos));
      assertTrue(cache.policy().expireAfterAccess().isEmpty());
      long maximum = cache.policy().eviction().orElseThrow().getMaximum();
      assertEquals(MapDbCredentialStore.CacheSettings.fileBackedDefaults().maximumSize(), maximum);
    }
  }

  @Test
  void customCacheSettingsOverrideDefaults() throws Exception {
    MapDbCredentialStore.CacheSettings customSettings =
        new MapDbCredentialStore.CacheSettings(
            Duration.ofSeconds(45),
            32_000,
            MapDbCredentialStore.CacheSettings.ExpirationStrategy.AFTER_WRITE);

    try (var store = MapDbCredentialStore.inMemory().cacheSettings(customSettings).open()) {
      Cache<String, Credential> cache = extractCache(store);
      long ttlNanos =
          cache.policy().expireAfterWrite().orElseThrow().getExpiresAfter(TimeUnit.NANOSECONDS);
      assertEquals(customSettings.ttl(), Duration.ofNanos(ttlNanos));
      long maximum = cache.policy().eviction().orElseThrow().getMaximum();
      assertEquals(customSettings.maximumSize(), maximum);
    }
  }

  @Test
  void legacyBuilderMutatorsAdjustCacheSettings() throws Exception {
    try (var store =
        MapDbCredentialStore.inMemory()
            .cacheMaximumSize(12_345)
            .cacheTtl(Duration.ofSeconds(30))
            .cacheExpirationStrategy(
                MapDbCredentialStore.CacheSettings.ExpirationStrategy.AFTER_WRITE)
            .open()) {
      Cache<String, Credential> cache = extractCache(store);
      long ttlNanos =
          cache.policy().expireAfterWrite().orElseThrow().getExpiresAfter(TimeUnit.NANOSECONDS);
      assertEquals(Duration.ofSeconds(30), Duration.ofNanos(ttlNanos));
      long maximum = cache.policy().eviction().orElseThrow().getMaximum();
      assertEquals(12_345, maximum);
    }
  }

  @Test
  void containerProfileDefaultsAvailable() {
    MapDbCredentialStore.CacheSettings container =
        MapDbCredentialStore.CacheSettings.containerDefaults();
    assertEquals(Duration.ofMinutes(15), container.ttl());
    assertEquals(500_000, container.maximumSize());
    assertEquals(
        MapDbCredentialStore.CacheSettings.ExpirationStrategy.AFTER_ACCESS,
        container.expirationStrategy());
  }

  private static Map<String, String> extractPayload(LogRecord record) {
    Object[] parameters = record.getParameters();
    assertNotNull(parameters, "Expected structured payload parameters");
    assertTrue(parameters.length >= 1, "Telemetry payload parameter missing");
    assertTrue(parameters[0] instanceof Map, "Telemetry payload should be a map");
    @SuppressWarnings("unchecked")
    Map<String, String> payload = (Map<String, String>) parameters[0];
    return payload;
  }

  private static final class TestLogHandler extends Handler {

    private final List<LogRecord> records = new java.util.ArrayList<>();

    private TestLogHandler() {
      setLevel(Level.ALL);
    }

    @Override
    public void publish(LogRecord record) {
      records.add(record);
    }

    @Override
    public void flush() {
      // no-op
    }

    @Override
    public void close() {
      records.clear();
    }

    List<LogRecord> records() {
      return List.copyOf(records);
    }
  }

  private VersionedCredentialRecord readRawRecord(Path dbPath, String name) {
    try (DB db = DBMaker.fileDB(dbPath.toFile()).transactionEnable().closeOnJvmShutdown().make()) {
      @SuppressWarnings("unchecked")
      var map =
          (org.mapdb.HTreeMap<String, VersionedCredentialRecord>)
              db.hashMap("credentials", Serializer.STRING, Serializer.JAVA).createOrOpen();
      return map.get(name);
    }
  }

  @SuppressWarnings("unchecked")
  private static Cache<String, Credential> extractCache(MapDbCredentialStore store)
      throws Exception {
    Field field = MapDbCredentialStore.class.getDeclaredField("cache");
    field.setAccessible(true);
    return (Cache<String, Credential>) field.get(store);
  }
}
