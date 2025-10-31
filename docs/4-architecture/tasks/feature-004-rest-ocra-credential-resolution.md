# Feature 004 – REST OCRA Credential Resolution Tasks

_Status:_ Complete  
_Last updated:_ 2025-10-31

## Execution Notes
- Follow the specification in `docs/4-architecture/specs/feature-004-rest-ocra-credential-resolution.md`.
- Keep each task ≤30 minutes; commit after every passing `./gradlew spotlessApply check`.
- Drive development test-first for new behaviours.

## Tasks
| ID | Task | Related Requirements | Status |
|----|------|----------------------|--------|
| R013 | Draft spec/plan/tasks (Option A dual-mode support) | FR-REST-010–FR-REST-013 | ✅ |
| R014 | Add failing tests covering credential lookup success, missing credential, conflict, and legacy inline mode regression | FR-REST-010–FR-REST-012 | ✅ |
| R015 | Implement credential resolver integration, mutual exclusivity validation, and telemetry updates | FR-REST-010–FR-REST-013 | ✅ |
| R016 | Refresh OpenAPI/docs/telemetry snapshot; rerun `./gradlew :rest-api:test` and `./gradlew spotlessApply check` | FR-REST-010–FR-REST-013 | ✅ |

## Completion Notes
- 2025-09-28 – Feature 004 tasks complete; final builds: `./gradlew :rest-api:test`, `./gradlew spotlessApply check`.

Update this checklist as work progresses.

2025-09-28 – R014 executed test-first: added credential lookup/conflict MockMvc cases; initial `./gradlew :rest-api:test --tests io.openauth.sim.rest.OcraEvaluationEndpointTest.evaluateWithCredentialIdReturnsOtp` (RED – prior to implementation).
2025-09-28 – R015 implemented credential resolver, input-mode validation, telemetry flag; `./gradlew :rest-api:test --tests io.openauth.sim.rest.OcraEvaluationEndpointTest` (PASS, ~13s).
2025-09-28 – R016 refreshed OpenAPI snapshot/docs/telemetry (`OPENAPI_SNAPSHOT_WRITE=true ./gradlew :rest-api:test --tests io.openauth.sim.rest.OpenApiSnapshotTest`, `./gradlew :rest-api:test --tests io.openauth.sim.rest.OcraEvaluationEndpointTest --info --rerun-tasks`, `./gradlew spotlessApply check`).
