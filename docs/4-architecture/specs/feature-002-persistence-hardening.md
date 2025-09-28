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

## References
- `docs/4-architecture/feature-plan-002-persistence-hardening.md`
- `docs/4-architecture/tasks/feature-002-persistence-hardening.md`
- `core/src/main/java/io/openauth/sim/core/store/MapDbCredentialStore.java`
