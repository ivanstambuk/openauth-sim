# Feature 002 – Persistence & Caching Hardening Specification

_Status: Draft_
_Last updated: 2025-09-28_

## Overview
Elevate the persistence layer backing the `CredentialStore` so it reliably supports ultra-low latency and high-throughput workloads (≥10,000 RPS) across local, embedded, and containerised deployments. The work focuses on tuning MapDB + Caffeine, adding targeted observability, and enforcing secret redaction while retaining the existing `CredentialStore` abstraction.

## Objectives & Success Criteria
- Prioritise resilience improvements in the following order: (1) instrumentation/metrics, (2) cache eviction & sizing strategy, (3) storage hygiene (compaction, integrity checks), (4) optional at-rest protections (encryption hooks, key management guidance).
- Support multiple deployment profiles (ephemeral tests, local disk, container volume) with documented configuration knobs.
- Sustain ≥10,000 RPS read-heavy workload with P99 latency under 5 ms for cache hits and 15 ms for MapDB hits in representative benchmarks.
- Maintain `CredentialStore` as the sole public persistence interface; downstream modules require no API changes.
- Redact secrets in logs/metrics and respect existing log-level driven auditing expectations.

## Functional Requirements
| ID | Requirement | Acceptance Signal |
|----|-------------|-------------------|
| FR-201 | Provide configuration profiles for in-memory, file-backed, and containerised deployments, including defaults for cache sizing and MapDB options. | Profiles documented and covered by smoke tests that load and query credentials without manual tuning. |
| FR-202 | Surface metrics/diagnostics for persistence operations (cache hits/misses, load latency, compaction cycles) without exposing secret material. | Metrics exposed via SLF4J/structured logging and validated by tests. |
| FR-203 | Implement maintenance hooks (compaction, integrity verification) runnable without downtime. | Scheduled/admin-triggered operations complete safely in tests and leave store consistent. |
| FR-204 | Offer optional at-rest protection hooks (encryption provider interface, key rotation guidance) while keeping default behaviour unchanged. | Encryption interface documented and default implementation remains no-op; tests confirm secret redaction. |

## Non-Functional Requirements
| ID | Requirement | Target |
|----|-------------|--------|
| NFR-201 | Throughput | Sustain ≥10,000 requests per second in synthetic benchmarks with ≥90% cache hit-rate. |
| NFR-202 | Latency | P99 latency ≤5 ms for cache hits, ≤15 ms for MapDB lookups under benchmark load. |
| NFR-203 | Observability | Emit structured logs/metrics suitable for aggregation, referencing credential names only. |
| NFR-204 | Security | Ensure all logs and metrics redact secrets; detect attempts to log raw secret material in tests. |

## Clarifications
- 2025-09-28 – Priority focus: Recommend tackling instrumentation/metrics first, then cache tuning, storage hygiene, and optional encryption to unlock iterative performance validation.
- 2025-09-28 – Deployment scope: Design configuration for local disk, embedded tests, and containerised runtimes; document assumptions for each profile.
- 2025-09-28 – Performance goal: Target ultra-low latency/ultra-high throughput with capability to handle ≥10,000 RPS.
- 2025-09-28 – API surface: Stay within the existing `CredentialStore` abstraction; no new public repositories.
- 2025-09-28 – Auditing & redaction: Enforce secret redaction; auditing remains governed by log levels.
- 2025-09-28 – Telemetry contract (T202): Emit Level.FINE events `persistence.credential.lookup` and `persistence.credential.mutation` with payload fields limited to `storeProfile` (`IN_MEMORY` or `FILE`), `credentialName`, cache outcome (`cacheHit` boolean, `source` of data), optional `latencyMicros` when MapDB is consulted, `operation` (`SAVE`/`DELETE`), and `redacted` flag; payloads must never include raw secret material.
- 2025-09-28 – Cache tuning (T203): Default Caffeine profiles – in-memory (max 250k entries, expire-after-access 2 minutes), file-backed (max 150k entries, expire-after-write 10 minutes), container (max 500k entries, expire-after-access 15 minutes) – with overrides available via builder hooks.
- 2025-09-28 – Maintenance API scope (T205): Expose MapDB maintenance operations through a dedicated helper produced by `MapDbCredentialStore.Builder` so `CredentialStore` stays unchanged and callers opt in explicitly.
- 2025-09-28 – Maintenance execution (T205): Helper provides synchronous compaction/integrity methods that block until completion; asynchronous scheduling can be layered externally if needed.
- 2025-09-28 – Maintenance diagnostics (T205): Maintenance helper returns a `MaintenanceResult` record including `operation` (`COMPACTION` or `INTEGRITY_CHECK`), `duration`, `entriesScanned`, `entriesRepaired`, `issues` (list of warnings/errors), and `status` (`SUCCESS`, `WARN`, `FAIL`); the same data is emitted via Level.FINE telemetry.
- 2025-09-28 – Maintenance triggers (T206): Initial operator surface delivered via CLI command(s) that invoke the maintenance helper; REST/UI hooks can layer on later once facades stabilize.
- 2025-09-28 – Encryption scope (T207): Provide AES-GCM based at-rest protection with project-managed key callbacks so callers can supply in-memory keys; default implementation remains no-op until configured.

## Deployment Profiles

| Profile | Target Scenario | Default Cache Settings | Storage Notes | Override Guidance |
|---------|-----------------|------------------------|---------------|-------------------|
| `IN_MEMORY` | Embedded tests, ephemeral benchmarks | Max 250k entries, expire after access 2 minutes | MapDB runs fully in-memory; disk persistence disabled. | Use `MapDbCredentialStore.inMemory().cacheSettings(...)` to shrink footprint for unit tests or increase TTL during long benchmarks. |
| `FILE` | Local disk deployments, developer machines | Max 150k entries, expire after write 10 minutes | File DB with mmap (when supported) and transactional commits. | Override TTL if the write frequency is low; prefer `cacheExpirationStrategy(AFTER_WRITE)` for durability alignment. |
| `CONTAINER` | Shared container/volume-backed services | Max 500k entries, expire after access 15 minutes | Intended for future builder helpers; use `cacheSettings(CacheSettings.containerDefaults())` and provide volume mounts. | Adjust maximum size based on container memory limits, keeping ≥15 minutes TTL only when hit ratios stay above 90%. |

All profiles redact secrets in telemetry and obey the `CredentialStore` abstraction. Additional deployment instructions live in `docs/2-how-to/configure-persistence-profiles.md` and must stay consistent with this table.

## References
- `docs/4-architecture/feature-plan-002-persistence-hardening.md`
- `docs/4-architecture/tasks/feature-002-persistence-hardening.md`
- `core/src/main/java/io/openauth/sim/core/store/MapDbCredentialStore.java`
