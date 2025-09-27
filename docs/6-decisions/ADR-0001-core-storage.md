# ADR-0001: Core Credential Store Stack

- **Status:** Accepted
- **Date:** 2025-09-27

## Context

The emulator needs a persistence layer that supports:
- Low-latency credential lookup (hundreds to thousands of reads per second) with predictable performance for automated flows.
- Durable storage for secret material to survive process restarts while remaining local-first (no external database dependency for contributors).
- Simple bundling alongside the Java core so consumers can embed the engine without additional deployment chores.

We considered lightweight embedded databases (SQLite, H2), pure file/TOML stores, and JVM-native key/value stores.

## Decision

Adopt MapDB as the primary on-disk key/value store, wrapped behind the `CredentialStore` interface, and layer a Caffeine in-memory cache in front of it to keep hot credentials resident.

Implementation highlights:
- `MapDbCredentialStore` handles persistence, commit/close semantics, and cache invalidation.
- The API remains SPI-friendly so alternative stores (e.g., ChronicleMap, H2) can be introduced later without impacting consumers.
- MapDB files live under a caller-specified path; unit tests rely on temporary directories.

## Consequences

**Positive**
- Fast local development with no external service dependencies.
- MapDB handles durable storage with a small footprint and no JNI requirements.
- Caffeine provides flexible eviction policies and TTL control for cache tuning.

**Negative**
- MapDB serialises Java objects; schema evolution must be managed deliberately to avoid compatibility breakage.
- No built-in replication or high-availability; suitable only for dev/test scenarios.
- Requires careful scrubbing of credential material when records are deleted (future work includes secure erasure/wiping).

## Alternatives Considered

- **H2/SQLite via JPA:** richer query support but heavier dependencies and slower cold-starts; unnecessary until we expose relational queries.
- **ChronicleMap/RocksDB:** excellent performance but introduce native libraries or licensing considerations.
- **Plain TOML/JSON files:** easy to inspect but fragile at 100k credential scale and complicated to keep atomic.

## Security / Privacy Impact

- Secrets remain on-disk in MapDB files; we must document safe storage locations and encryption-at-rest options before promoting beyond lab usage.
- Cache contents reside in-memory; long-lived processes should offer cache flush controls.

## Operational Impact

- Expect to expose maintenance operations (compaction, backup/restore) via CLI/REST.
- Map files can grow with credential volume; operations docs must include disk usage guidance.

## References

- [MapDB documentation](https://github.com/jankotek/mapdb)
- [Caffeine cache documentation](https://github.com/ben-manes/caffeine/wiki)
