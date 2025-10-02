# Feature 014 – Architecture Harmonization Tasks

_Status: In Progress_
_Last updated: 2025-10-02_

## Tasks
| ID | Task | Related Requirements | Status |
|----|------|----------------------|--------|
| T1401 | Register Feature 014 in roadmap and knowledge map; confirm no open questions remain. | AH-001–AH-005 | ✅ (2025-10-01) |
| T1402 | Add failing ArchUnit tests asserting facades delegate to the shared application layer module. | AH-001, AH-004 | ✅ (2025-10-01) |
| T1403 | Implement shared application layer services and update facades/tests until ArchUnit delegation passes. | AH-001, AH-005 | ☐ |
| T1404 | Add failing DTO normalization tests covering inline vs stored flows across CLI/REST. | AH-005 | ☐ |
| T1405 | Implement shared DTO/normalization library and migrate facades to green the tests. | AH-005 | ☐ |
| T1406 | Add failing tests around centralized `CredentialStoreFactory` (unit + integration seams). | AH-002 | ☐ |
| T1407 | Implement `CredentialStoreFactory` infrastructure and update facades/tests. | AH-002 | ☐ |
| T1408 | Add failing telemetry contract tests ensuring consistent sanitized outputs across facades. | AH-003, AH-NFR-002 | ☐ |
| T1409 | Implement telemetry contract + adapters and update documentation/tests to pass. | AH-003, AH-NFR-002 | ☐ |
| T1410 | Split core modules, update Gradle + ArchUnit boundaries, and rerun quality gate. | AH-004, AH-NFR-001 | ☐ |
| T1411 | Sync documentation (AGENTS, how-to), run `spotlessApply` + `qualityGate`, record results. | AH-001–AH-005, AH-NFR-001–003 | ☐ |
| T1412 | Add failing tests covering negative branches in `OcraReplayVerifier` and related helpers to restore branch coverage headroom. | AH-001, AH-005 | ✅ (2025-10-02) |
| T1413 | Add failing REST `OcraVerificationService` tests for `handleInvalid` (validation failure, unexpected error, unexpected state) and command envelope branches. | AH-001, AH-005 | ✅ (2025-10-02) |
| T1414 | Implement the new tests, drive them green, and rerun `./gradlew :rest-api:test` + `./gradlew qualityGate`; capture metrics. | AH-NFR-001 | ✅ (2025-10-02) |

Update this checklist after each increment, ensuring tasks remain ≤10 minutes and tests precede implementation work.
