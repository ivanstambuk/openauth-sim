# Feature 004 – REST OCRA Credential Resolution Tasks

_Status: Draft_
_Last updated: 2025-09-28_

## Execution Notes
- Follow the specification in `docs/4-architecture/specs/feature-004-rest-ocra-credential-resolution.md`.
- Keep each task ≤10 minutes; commit after every passing `./gradlew spotlessApply check`.
- Drive development test-first for new behaviours.

## Tasks
| ID | Task | Related Requirements | Status |
|----|------|----------------------|--------|
| R013 | Draft spec/plan/tasks (Option A dual-mode support) | FR-REST-010–FR-REST-013 | ✅ |
| R014 | Add failing tests covering credential lookup success, missing credential, conflict, and legacy inline mode regression | FR-REST-010–FR-REST-012 | ☐ |
| R015 | Implement credential resolver integration, mutual exclusivity validation, and telemetry updates | FR-REST-010–FR-REST-013 | ☐ |
| R016 | Refresh OpenAPI/docs/telemetry snapshot; rerun `./gradlew :rest-api:test` and `./gradlew spotlessApply check` | FR-REST-010–FR-REST-013 | ☐ |

Update this checklist as work progresses.
