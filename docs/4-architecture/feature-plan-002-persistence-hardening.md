# Feature Plan 002 – Persistence & Caching Hardening

_Status: In planning_
_Last updated: 2025-09-28_

## Objective
Enhance the persistence layer so MapDB + Caffeine can sustain ≥10,000 RPS while providing robust observability, maintenance hooks, and optional at-rest protections—all without changing the `CredentialStore` interface.

Reference specification: `docs/4-architecture/specs/feature-002-persistence-hardening.md`.

## Success Criteria
- Benchmark harness demonstrates ≥10,000 RPS with latency targets from the specification and documents configuration used.
- Cache and store emit metrics/logs (hit/miss ratios, load latencies, compaction events) without leaking secrets.
- Maintenance operations (compaction, integrity checks) can be triggered via APIs/tests and complete without data loss.
- Optional encryption hooks exist with documentation while default behaviour remains plaintext for tests.
- Documentation (concepts, knowledge map, roadmap) reflects new persistence capabilities and configuration profiles.

## Task Tracker
- Detailed execution steps reside in `docs/4-architecture/tasks/feature-002-persistence-hardening.md`.
- Map benchmark and profiling results back to NFR-201/NFR-202 as tasks close.
- Record outcomes of `./gradlew spotlessApply check` and benchmarks in this plan to maintain traceability.
- 2025-09-28 – T201 baseline benchmark (`MapDbCredentialStoreBaselineBenchmark`) captured in-memory metrics: writes ≈1.7k ops/s (20k dataset, 11.6 s), reads ≈567k ops/s with P50≈0.0006 ms, P90≈0.0012 ms, P99≈0.0043 ms. Rerun via `./gradlew :core:test --tests io.openauth.sim.core.store.MapDbCredentialStoreBaselineBenchmark -Dio.openauth.sim.benchmark=true` or set `IO_OPENAUTH_SIM_BENCHMARK=true` in the environment.
- 2025-09-28 – T202 structured telemetry: MapDB store now emits `persistence.credential.lookup` (cache hit/miss, latency) and `persistence.credential.mutation` (save/delete latency) Level.FINE events with redacted payloads. Validation covered by `MapDbCredentialStoreTest.structuredTelemetryEmittedForPersistenceOperations`.
- 2025-09-28 – Gradle verification: `./gradlew :core:test` (pass) and `./gradlew spotlessApply check` (pass, includes SpotBugs note about missing `org.opentest4j.MultipleFailuresError`, non-fatal) recorded cache/persistence telemetry behaviour.

## Upcoming Increments
1. **T201 – Baseline Metrics & Benchmark Harness**: introduce synthetic load tests and logging scaffolding to capture current performance.
2. **T202 – Structured Metrics & Logging**: add Level.FINE telemetry events for cache hits/misses and persistence latencies with redaction guarantees, capturing payload contract from the specification.
3. **T203 – MapDB Maintenance Hooks**: add compaction/integrity routines and ensure safe invocation.
4. **T204 – Optional Encryption Hooks**: define interface and default implementation; document usage.
5. **T205 – Documentation & Self-Review**: propagate findings to concepts/knowledge map/roadmap and capture lessons learned.

## Dependencies & Considerations
- Benchmarks may require additional tooling (JMH or custom harness) – ensure they run without network access.
- Observe any platform constraints (CI resources) when defining RPS scenarios.
- Coordinate with future Workstreams (CLI/REST) so their persistence assumptions match new configuration profiles.

## Analysis Gate
- [x] Specification reviewed and aligned with clarifications.
- [x] Open questions resolved (clarifications recorded on 2025-09-28; none outstanding).
- [x] Tasks ordered with benchmarks/metrics preceding behavioural changes.

Update this plan as tasks progress: check off completed items, add new tasks, and note blockers.
