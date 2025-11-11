# Feature Plan 002 – Persistence & Caching Hardening

_Linked specification:_ `docs/4-architecture/features/002/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
- Ship hardened MapDB + Caffeine cache profiles that boot cleanly across in-memory, file, and container deployments without manual tuning.
- Provide structured telemetry/metrics plus maintenance helpers so operators can diagnose issues and trigger compaction/integrity checks safely.
- Demonstrate ≥10k RPS throughput with benchmark evidence and document the configuration/operational guidance in roadmap + how-to artifacts.
- Keep the `CredentialStore` abstraction stable for all facades while introducing optional AES-GCM encryption hooks with redaction guarantees.

## Scope Alignment
- **In scope:**
  - Benchmark harness + instrumentation updates (T0201–T0203).
  - Cache profile defaults + override hooks and documentation (T0204).
  - Maintenance helper + CLI entry points (T0205–T0206).
  - Optional encryption interface, docs, and key-management guidance (T0206).
  - Benchmark reruns, roadmap/knowledge-map/how-to updates, and final quality gates (T0207–T0209).
- **Out of scope:**
  - Replacing MapDB with a different storage backend.
  - Adding REST/UI maintenance endpoints (future feature once persistence stabilises).
  - Multi-tenant persistence sharding or HA/replication strategies.

## Dependencies & Interfaces
- `core` MapDB implementation + Caffeine cache settings.
- `CredentialStoreFactory` (application module) that provisions stores for CLI/REST/UI consumers.
- CLI maintenance command wiring plus telemetry adapters defined under `TelemetryContracts`.
- `docs/2-how-to/configure-persistence-profiles.md`, roadmap, and knowledge map for documentation sync.
- Benchmark harness `MapDbCredentialStoreBaselineBenchmark` executed via Gradle with the benchmark flag/environment variable.

## Assumptions & Risks
- **Assumptions:** MapDB remains the persistence engine; benchmark hardware roughly matches local dev machines; optional encryption stays off by default.
- **Risks:**
  - Benchmark regressions if cache defaults change (mitigation: rerun harness after each tuning change).
  - Maintenance helper misuse causing downtime (mitigation: synchronous helper with explicit CLI confirmation text).
  - Encryption key mishandling (mitigation: document key rotation + add telemetry warnings for mismatches).

## Implementation Drift Gate
- Evidence lives in the feature plan/task history plus telemetry snapshots.
- Gate checklist: map FR/NFR/S02 scenarios to increments, ensure benchmark + telemetry artefacts exist, and confirm documentation updates.
- Record drift findings here; none outstanding as of 2025-09-28 (PASS).

## Increment Map
1. **I1 – Clarifications, instrumentation, and baseline benchmarks (T0201–T0203)**
   - _Goal:_ Lock priorities, emit structured telemetry, and capture pre-change benchmarks.
   - _Preconditions:_ Spec + clarifications drafted; analysis gate ready.
   - _Steps:_ finalize clarifications/runbook entries; implement telemetry events; build + run benchmark harness; document results.
   - _Commands:_
     - `./gradlew --no-daemon :core:test --tests "*MapDbCredentialStoreBaselineBenchmark" -Dio.openauth.sim.benchmark=true`
     - `./gradlew --no-daemon :core:test --tests "*CredentialStoreTelemetryTest"`
   - _Exit:_ Telemetry redaction tests pass, baseline metrics recorded in plan/tasks.

2. **I2 – Cache profiles + maintenance helper (T0204–T0205)**
   - _Goal:_ Deliver profile defaults and expose maintenance helper APIs + CLI wiring.
   - _Preconditions:_ I1 complete; telemetry events available for verification.
   - _Steps:_ implement profile defaults + overrides; add maintenance helper + result record; wire CLI commands; expand docs/how-to.
   - _Commands:_
     - `./gradlew --no-daemon :core:test --tests "*CacheProfileTest"`
     - `./gradlew --no-daemon :core:test --tests "*MaintenanceHelperTest"`
     - `./gradlew --no-daemon :cli:test --tests "*MaintenanceCli*"`
   - _Exit:_ Profiles smoke-tested across configurations; maintenance CLI produces structured output.

3. **I3 – Encryption hooks + documentation sync (T0206–T0207)**
   - _Goal:_ Introduce AES-GCM encryption interface, tests, and operator guidance (key rotation + maintenance docs).
   - _Preconditions:_ I2 complete; CLI wiring available for doc references.
   - _Steps:_ implement `PersistenceEncryption`, add positive/negative tests, refresh how-to + knowledge map + roadmap.
   - _Commands:_
     - `./gradlew --no-daemon :core:test --tests "*PersistenceEncryptionTest"`
     - `rg -n "MapDB" docs/4-architecture/knowledge-map.md`
   - _Exit:_ Encryption optional path documented; telemetry events cover failures.

4. **I4 – Benchmark rerun + quality gate (T0208–T0209)**
   - _Goal:_ Re-run benchmarks after tuning, archive results, and close with spotless/check + quality gate.
   - _Preconditions:_ I3 complete; docs updated.
   - _Steps:_ rerun benchmark harness with final configuration; log deltas in plan; execute spotless/check + `qualityGate` (with PIT skip flag); capture self-review notes.
   - _Commands:_
     - `IO_OPENAUTH_SIM_BENCHMARK=true ./gradlew --no-daemon :core:test --tests "*MapDbCredentialStoreBaselineBenchmark"`
     - `./gradlew --no-daemon spotlessApply check`
     - `./gradlew --no-daemon qualityGate -Ppit.skip=true`
   - _Exit:_ Benchmark + Gradle gates green; documentation + tasks updated with closure notes.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S02-01 | I2 / T0204 | Cache profiles + overrides validated via smoke tests. |
| S02-02 | I1 / T0203 | Telemetry/metrics instrumentation with redaction checks. |
| S02-03 | I2 / T0205–T0206 | Maintenance helper + CLI command outputs. |
| S02-04 | I3 / T0206 | AES-GCM encryption optional path + failure coverage. |
| S02-05 | I1 & I4 / T0202, T0208 | Benchmark harness before/after tuning. |
| S02-06 | I3 / T0207 | Documentation updates (how-to, roadmap, knowledge map). |

## Analysis Gate
- 2025-09-28 – PASS. Spec FR/NFR tables populated, no open questions, tasks staged with tests-first ordering, telemetry + persistence dependencies identified, and `./gradlew --no-daemon spotlessApply check` executed.

## Exit Criteria
- All FR/NFR scenarios proven via tests + benchmarks; documentation updated (how-to, roadmap, knowledge map).
- `./gradlew --no-daemon spotlessApply check` and `./gradlew --no-daemon qualityGate -Ppit.skip=true` pass post-migration.
- Telemetry events verified for success/failure paths with redaction.
- Benchmark reruns logged with configuration context and stored in plan/tasks.

## Follow-ups / Backlog
1. Evaluate REST/UI endpoints for maintenance operations once persistence stabilises (defer to new feature).
2. Investigate background/async maintenance scheduling hooks to avoid manual CLI invocations in long-running environments.
3. Explore MapDB sharding or multi-tenant support if simulator workloads outgrow single-store patterns.
