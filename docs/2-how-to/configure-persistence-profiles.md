# Configure MapDB Persistence Profiles

_Status: Draft_
_Last updated: 2025-09-28_

## Overview

MapDB-backed credential persistence supports multiple deployment profiles that balance cache footprint, latency, and durability. The `MapDbCredentialStore` builder exposes sensible defaults for in-memory, file-backed, and containerised deployments. This guide explains how to select a profile, adjust cache behaviour, and validate the resulting configuration using the project’s tooling.

## Prerequisites

- Java 17 runtime (verify with `java -version`).
- Access to the `core` module so you can construct `MapDbCredentialStore` instances.
- Optional: write access to a persistent filesystem path when using the `FILE` profile.

## Profiles

### In-memory (`IN_MEMORY`)

**Use when:** Running unit tests, ephemeral benchmarks, or local experiments where persistence is not required between JVM runs.

**Defaults:**
- Cache maximum size: 250,000 entries
- Expiration strategy: expire after access
- TTL: 2 minutes

**Configuration:**
```java
try (MapDbCredentialStore store = MapDbCredentialStore.inMemory().open()) {
  // interact with the credential store
}
```

**Override knobs:**
- `cacheMaximumSize(long)` to shrink the footprint for small tests.
- `cacheTtl(Duration)` to extend the active window during long-running benchmarks.
- `cacheSettings(MapDbCredentialStore.CacheSettings)` for advanced control (e.g., expire-after-write).

### File-backed (`FILE`)

**Use when:** Running the simulator on a developer workstation or single-node environment where data should persist across restarts.

**Defaults:**
- Cache maximum size: 150,000 entries
- Expiration strategy: expire after write
- TTL: 10 minutes

**Configuration:**
```java
Path databasePath = Paths.get("./build/data/credentials.db");
try (MapDbCredentialStore store = MapDbCredentialStore.file(databasePath).open()) {
  // interact with the credential store
}
```

MapDB enables memory-mapped IO (when supported) and transactional commits for durability. Ensure the target directory exists and is writable.

**Override knobs:**
- `cacheTtl(Duration)` to handle workloads with infrequent writes (increase TTL) or high churn (decrease TTL).
- `cacheExpirationStrategy(CacheSettings.ExpirationStrategy.AFTER_ACCESS)` if read-heavy workloads benefit from resetting TTL on access.
- `cacheSettings(CacheSettings.fileBackedDefaults().withMaximumSize(...))` for custom cache capacities.

### Container / volume-backed (`CONTAINER`)

**Use when:** Running the simulator inside containers (e.g., Docker, Kubernetes) where a shared volume stores MapDB files and caches must absorb bursty load.

**Defaults:**
- Cache maximum size: 500,000 entries
- Expiration strategy: expire after access
- TTL: 15 minutes

**Configuration:**
```java
Path volumePath = Paths.get(System.getenv("PERSISTENCE_VOLUME"));
MapDbCredentialStore.CacheSettings containerSettings =
    MapDbCredentialStore.CacheSettings.containerDefaults();
try (MapDbCredentialStore store =
    MapDbCredentialStore.file(volumePath).cacheSettings(containerSettings).open()) {
  // interact with the credential store
}
```

Ensure the container mounts a volume with sufficient space and grants write permissions to the application user.

**Override knobs:**
- Reduce `maximumSize` to stay within container memory limits when heap pressure is observed.
- Adjust `ttl` if telemetry shows cache churn; aim for ≥90% hit rate to meet NFR-201/NFR-202.

## Validation Checklist

1. **Smoke test the profile** by writing and reading a handful of credentials.
2. **Inspect telemetry** (`persistence.credential.lookup` and `persistence.credential.mutation`) to confirm cache hits/misses align with expectations.
3. **Run the benchmark harness** (optional) with `./gradlew :core:test --tests io.openauth.sim.core.store.MapDbCredentialStoreBaselineBenchmark -Dio.openauth.sim.benchmark=true` and record the results in the feature plan.
4. **Monitor cache metrics** by adjusting log level to `FINE` if additional insight is needed.

## Run Maintenance Operations via CLI

MapDB stores occasionally require compaction or integrity verification. The CLI module now exposes a `maintenance` command that wraps the core `MaintenanceHelper` so operators can trigger these routines without writing Java code.

1. Build the project (if necessary): `./gradlew :cli:build`
2. Run compaction against a file-backed store:

   ```bash
   ./gradlew :cli:run --args="compact --database=/path/to/credentials.db"
   ```

3. Run an integrity check instead:

   ```bash
   ./gradlew :cli:run --args="verify --database=/path/to/credentials.db"
   ```

The command prints the structured maintenance result (operation, status, duration, entries scanned/repaired, issue count). A non-zero exit code indicates the helper reported `FAIL`, in which case investigate the listed issues before continuing.

## Enable Optional AES Encryption

When running in environments that require secrets encrypted at rest, configure `MapDbCredentialStore` with the AES-GCM encryption helper added in Feature 002:

```java
byte[] key = ...; // 16/24/32-byte AES key supplied by your key-management system
PersistenceEncryption encryption =
    AesGcmPersistenceEncryption.withKeySupplier(() -> key.clone());

try (MapDbCredentialStore store =
    MapDbCredentialStore.file(Paths.get("./data/credentials.db"))
        .encryption(encryption)
        .open()) {
  // store credentials – secrets will be encrypted at rest
}
```

Key rotation guidance:

1. Use the current key to read and re-encrypt credentials with the new key in a controlled maintenance window.
2. Restart dependent facades with the new key supplier once the migration finishes.
3. Keep key material in memory only; avoid environment variables for long-term storage. Integrators can wrap the supplier to fetch keys from HSM/KMS APIs.

If the wrong key is provided during startup, credential reads will raise an `IllegalStateException` and log details—verify keys before deploying to production.

## Follow-ups

- Benchmark results after cache tuning should be captured under Feature 002 plan task T201 once rerun.
- Future work (T205+) will document maintenance hooks and optional encryption; keep this guide in sync when new configuration knobs appear.
