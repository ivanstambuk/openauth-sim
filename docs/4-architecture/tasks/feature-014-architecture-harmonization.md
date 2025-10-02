# Feature 014 – Architecture Harmonization Tasks

_Status: In Progress_
_Last updated: 2025-10-02_

## Tasks
| ID | Task | Related Requirements | Status |
|----|------|----------------------|--------|
| T1401 | Register Feature 014 in roadmap and knowledge map; confirm no open questions remain. | AH-001–AH-005 | ✅ (2025-10-01) |
| T1402 | Add failing ArchUnit tests asserting facades delegate to the shared application layer module. | AH-001, AH-004 | ✅ (2025-10-01) |
| T1403 | Implement shared application layer services and update facades/tests until ArchUnit delegation passes. | AH-001, AH-005 | ✅ (2025-10-02 – REST/UI delegate via shared services; ArchUnit now enforces application-layer dependency) |
| T1404 | Add failing DTO normalization tests covering inline vs stored flows across CLI/REST. | AH-005 | ✅ (2025-10-02 – Alignment tests active in `core-architecture-tests`; shared identifier helper covers stored/inline paths) |
| T1405 | Implement shared DTO/normalization library and migrate facades to green the tests. | AH-005 | ✅ (2025-10-02 – Introduced `CredentialStoreFactory` in `infra-persistence`; CLI/REST delegate creation and ArchUnit guard passes) |
| T1406 | Add failing tests around centralized `CredentialStoreFactory` (unit + integration seams). | AH-002 | ✅ (2025-10-02 – `infra-persistence` tests cover file/in-memory wiring + path resolution) |
| T1407 | Implement `CredentialStoreFactory` infrastructure and update facades/tests. | AH-002 | ✅ (2025-10-02 – CLI/REST consume factory; ArchUnit guard verifies MapDB isolation) |
| T1408 | Add failing telemetry contract tests ensuring consistent sanitized outputs across facades. | AH-003, AH-NFR-002 | ✅ (2025-10-02 – Added `OcraTelemetryContractTest` + shared `TelemetryContractTestSupport` in `application`, and `TelemetryContractArchitectureTest` enforcing adapter usage; tests currently red pending shared adapter implementation) |
| T1409 | Implement telemetry contract + adapters and update documentation/tests to pass. | AH-003, AH-NFR-002 | ✅ (2025-10-02 – Added shared `TelemetryContracts`, new contract fixtures/architecture guard, refactored CLI/REST logging to use the adapter, and refreshed REST telemetry snapshot.) |
| T1410 | Track core module split via sub-tasks (T1415–T1419) and aggregate status for AH-004. | AH-004, AH-NFR-001 | ☐ |
| T1411 | Sync documentation (AGENTS, how-to), run `spotlessApply` + `qualityGate`, record results. | AH-001–AH-005, AH-NFR-001–003 | ☐ |
| T1412 | Add failing tests covering negative branches in `OcraReplayVerifier` and related helpers to restore branch coverage headroom. | AH-001, AH-005 | ✅ (2025-10-02) |
| T1413 | Add failing REST `OcraVerificationService` tests for `handleInvalid` (validation failure, unexpected error, unexpected state) and command envelope branches. | AH-001, AH-005 | ✅ (2025-10-02) |
| T1414 | Implement the new tests, drive them green, and rerun `./gradlew :rest-api:test` + `./gradlew qualityGate`; capture metrics. | AH-NFR-001 | ✅ (2025-10-02) |
| T1415 | Add failing ArchUnit guard for `core-shared`/`core-ocra` boundaries and document package split. | AH-004 | ✅ (2025-10-02 – `CoreModuleSplitArchitectureTest` fails until facades stop importing OCRA internals; package mapping captured in plan.) |
| T1416 | Scaffold `core-shared` module (settings + Gradle) and migrate `model` + `support` packages behind red tests. | AH-004 | ✅ (2025-10-02 – Added `core-shared` module, moved model/support packages, re-exported via `core`; architecture guard still red.) |
| T1417 | Relocate serialization/encryption primitives to `core-shared`, exposing migration injection seams. | AH-004 | ✅ (2025-10-02 – Moved serialization/encryption packages into `core-shared` and added migration injection hooks to `MapDbCredentialStore.Builder`.) |
| T1418 | Create `core-ocra` module, move OCRA descriptors/registry/migrations, and update dependent module wiring. | AH-004 | ✅ (2025-10-02 – `core-ocra` module established, ocra sources/tests relocated, builder helper added, CLI/REST/UI wired.) |
| T1419 | Refresh root Gradle (settings, PIT/Jacoco) and ArchUnit rules; run `architectureTest` before `qualityGate`. | AH-004, AH-NFR-001 | ✅ (2025-10-02 – Added `core-ocra` to aggregated coverage/PIT, architecture guard green, `qualityGate` (branches 90.42 %, lines 96.91 %, PIT 91 %) passes.) |

Update this checklist after each increment, ensuring tasks remain ≤10 minutes and tests precede implementation work.

### Planning Notes – T1408/T1409 (2025-10-02)
- Create `TelemetryContractArchitectureTest` in `core-architecture-tests` that currently fails, asserting CLI/REST/UI depend only on `application.telemetry` adapters rather than bespoke loggers.
- Add red test fixtures (`TelemetryContractTestSupport`) in the application module covering success/validation/error frames with sanitized fields; reference from CLI/REST tests once adapters exist.
- Capture REST/CLI telemetry expectations in `docs/3-reference/rest-ocra-telemetry-snapshot.md` appendix update after adapters land.

### Planning Notes – T1410 (2025-10-02)
- Package allocation: move `io.openauth.sim.core.model`, `support`, and non-OCRA persistence primitives to `core-shared`; host protocol-specific descriptors, registry defaults, and migrations in `core-ocra`.
- Update `MapDbCredentialStore.Builder` to accept injected `VersionedCredentialRecordMigration` lists so OCRA migrations can ship from `core-ocra` instead of the shared module.
- New ArchUnit guard will fail until modules are split, enforcing that only `application` and `infra-persistence` reference `core-ocra`; facades keep indirect access through the application layer.
- Persistence wiring now uses `OcraStoreMigrations.apply(...)` so CLI/REST/UI and tests register legacy migrations explicitly; `core` no longer depends on OCRA classes by default.
