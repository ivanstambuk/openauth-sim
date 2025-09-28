# Feature 005 – CLI OCRA Operations Tasks

_Status: Draft_
_Last updated: 2025-09-28_

## Execution Notes
- Follow the specification in `docs/4-architecture/specs/feature-005-cli-ocra-operations.md`.
- Keep each task ≤10 minutes; commit after every passing build.
- Drive test-first: add CLI command tests before implementation.

## Tasks
| ID | Task | Related Requirements | Status |
|----|------|----------------------|--------|
| R017 | Draft spec/plan/tasks for CLI OCRA operations | CLI-OCRA-001–CLI-OCRA-005 | ✅ |
| R018 | Add failing CLI tests covering import/list/delete/evaluate with credential-id vs inline secret modes | CLI-OCRA-001–CLI-OCRA-004 | ☐ |
| R019 | Implement CLI command handlers, persistence integration, telemetry/logging | CLI-OCRA-001–CLI-OCRA-005 | ☐ |
| R020 | Refresh CLI docs/how-to, update telemetry snapshot if needed, rerun `./gradlew :cli:test` and `./gradlew spotlessApply check` | CLI-OCRA-001–CLI-OCRA-005 | ☐ |

Update this checklist as work progresses.
