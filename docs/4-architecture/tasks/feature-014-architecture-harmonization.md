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
| T1410 | Split core modules, update Gradle + ArchUnit boundaries, and rerun quality gate. | AH-004, AH-NFR-001 | ☐ |
| T1411 | Sync documentation (AGENTS, how-to), run `spotlessApply` + `qualityGate`, record results. | AH-001–AH-005, AH-NFR-001–003 | ☐ |
| T1412 | Add failing tests covering negative branches in `OcraReplayVerifier` and related helpers to restore branch coverage headroom. | AH-001, AH-005 | ✅ (2025-10-02) |
| T1413 | Add failing REST `OcraVerificationService` tests for `handleInvalid` (validation failure, unexpected error, unexpected state) and command envelope branches. | AH-001, AH-005 | ✅ (2025-10-02) |
| T1414 | Implement the new tests, drive them green, and rerun `./gradlew :rest-api:test` + `./gradlew qualityGate`; capture metrics. | AH-NFR-001 | ✅ (2025-10-02) |

Update this checklist after each increment, ensuring tasks remain ≤10 minutes and tests precede implementation work.

### Planning Notes – T1408/T1409 (2025-10-02)
- Create `TelemetryContractArchitectureTest` in `core-architecture-tests` that currently fails, asserting CLI/REST/UI depend only on `application.telemetry` adapters rather than bespoke loggers.
- Add red test fixtures (`TelemetryContractTestSupport`) in the application module covering success/validation/error frames with sanitized fields; reference from CLI/REST tests once adapters exist.
- Capture REST/CLI telemetry expectations in `docs/3-reference/rest-ocra-telemetry-snapshot.md` appendix update after adapters land.
