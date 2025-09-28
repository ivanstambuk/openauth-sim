# Feature 002 – Persistence & Caching Hardening Tasks

_Status: In planning_
_Last updated: 2025-09-28_

## Execution Notes
- Follow the specification in `docs/4-architecture/specs/feature-002-persistence-hardening.md`.
- Work remains within the `CredentialStore` abstraction; new public APIs require explicit approval.
- Maintain secret redaction in all logs/metrics and ensure benchmarks do not persist sensitive data.
- Keep increments ≤10 minutes, run `./gradlew spotlessApply check`, and record benchmark outputs in the feature plan.

## Phase 0 – Foundations
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| T201 | Establish benchmark harness and baseline metrics for MapDB + Caffeine. | FR-202, NFR-201, NFR-202 | No |
| T202 | Add structured metrics/logging (hit/miss, latency) with secret redaction checks. | FR-202, NFR-203, NFR-204 | No |

### T201 – Benchmark Harness Checklist
- [x] Introduced `MapDbCredentialStoreBaselineBenchmark` with opt-in execution flag/environment variable.
- [x] Captured baseline in-memory metrics (writes ≈1.7k ops/s; reads ≈567k ops/s; P99 ≈0.0043 ms) on 2025-09-28.
- [x] Documented rerun instructions (`./gradlew :core:test --tests io.openauth.sim.core.store.MapDbCredentialStoreBaselineBenchmark -Dio.openauth.sim.benchmark=true` or `IO_OPENAUTH_SIM_BENCHMARK=true ./gradlew :core:test --tests ...`) and recorded sample output in the feature plan.
- [x] Reran benchmark after cache tuning on 2025-09-28 (`--rerun-tasks --info`); observed writes ≈2.86k ops/s, reads ≈351k ops/s, P50≈0.00093 ms, P90≈0.00249 ms, P99≈0.0208 ms.

### T202 – Structured Metrics & Logging Checklist
- [x] Document telemetry contract and payload fields in spec/plan (2025-09-28).
- [x] Emit `persistence.credential.lookup` and `persistence.credential.mutation` events with redacted payloads.
- [x] Add tests covering cache hit/miss logging and redaction expectations.
- [x] Record Gradle `:core:test` and `spotlessApply check` outcomes in plan/tasks once passing (2025-09-28).

## Phase 1 – Cache Strategy
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| T203 | Tune Caffeine configuration (maximum size, TTL, eviction strategy) guided by benchmarks. | FR-201, NFR-201 | No |
| T204 | Document deployment profiles (embedded, local disk, container) with default settings. | FR-201 | Yes |

### T203 – Cache Strategy Tuning Checklist
- [x] Define per-profile cache defaults and builder override strategy in spec/plan (2025-09-28).
- [x] Implement Caffeine profile defaults (in-memory/file/container) in `MapDbCredentialStore.Builder`.
- [x] Add regression tests verifying expiration strategy, TTL, and maximum size per profile and override paths.
- [x] Record benchmark follow-up needs and Gradle command outcomes once passing (2025-09-28).

### T204 – Deployment Profiles Checklist
- [x] Align spec/plan with deployment documentation scope (2025-09-28).
- [x] Author operator-facing guide covering in-memory, file-backed, and container profiles with defaults and overrides (2025-09-28).
- [x] Cross-reference guide from tasks/spec/knowledge map and capture follow-up benchmark action (2025-09-28).
- [x] Record Gradle command status (spotlessApply check, 2025-09-28 – pass, SpotBugs warns missing `org.opentest4j.MultipleFailuresError`).

## Phase 2 – Storage Maintenance
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| T205 | Implement MapDB compaction and integrity verification hooks with tests. | FR-203 | No |
| T206 | Surface CLI commands/tests to trigger maintenance helper operations safely. | FR-203 | No |

### T205 – Maintenance Hooks Checklist
- [x] Builder returns dedicated maintenance helper exposing compaction/integrity operations (decision recorded 2025-09-28).
- [x] Helper methods execute synchronously on the caller thread; document assumption in spec/plan (2025-09-28).
- [x] Define structured maintenance result (operation, duration, entries scanned/repaired, issues, status) and Level.FINE telemetry payload before implementing tests (2025-09-28).
- [x] Write failing tests covering compaction/integrity flows via maintenance helper (2025-09-28).

### T206 – Maintenance CLI Checklist
- [x] Confirm operator surface via CLI command(s) invoking `MaintenanceHelper` (2025-09-28).
- [x] Add failing-first CLI tests covering `compact` and `verify` commands with MapDB file stores (2025-09-28).
- [x] Implement CLI command runner emitting `MaintenanceResult` fields to STDOUT (2025-09-28).
- [x] Document maintenance CLI usage and integrate into operator how-to guide (2025-09-28).

## Phase 3 – Optional Protections
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| T207 | Define encryption provider interface and default AES-GCM implementation with callback-supplied keys. | FR-204, NFR-204 | No |
| T208 | Add documentation/tests covering encryption hook behaviour and key rotation guidance. | FR-204 | No |

### T207 – Encryption Hooks Checklist
- [x] Confirm AES-GCM with in-memory key callbacks as initial encryption approach (2025-09-28).
- [x] Introduce `PersistenceEncryption` interface and AES-GCM implementation with unit coverage (2025-09-28).
- [x] Integrate encryption into `MapDbCredentialStore` and ensure attributes returned to callers exclude metadata (2025-09-28).
- [x] Provide operator/key rotation documentation updates (2025-09-28) – see `docs/2-how-to/configure-persistence-profiles.md`.

## Phase 4 – Wrap-up
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| T209 | Update concepts/knowledge map/roadmap with persistence findings and telemetry references. | FR-201–FR-204 | Yes |
| T210 | Record final benchmarks/self-review outcomes in the feature plan and close open questions. | NFR-201–NFR-204 | No |

### T209 – Wrap-up Checklist
- [x] Knowledge map captures maintenance helper and AES-GCM encryption relationships (2025-09-28).
- [x] Roadmap includes replay/verification + simulator UI workstreams informed by persistence work (2025-09-28).
- [x] Concepts documentation updated with maintenance helper and AES-GCM definitions (2025-09-28).

## Open Follow-ups
- Populate benchmark results and decisions as tasks complete.
- Track any approval needed for additional tooling or dependencies before implementation.
