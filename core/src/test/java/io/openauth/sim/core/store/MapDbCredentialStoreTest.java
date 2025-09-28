package io.openauth.sim.core.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
}
