# Feature 019 – Commit Message Hook Refactor Tasks

_Status: Complete_
_Last updated: 2025-10-04_

## Tasks
| ID | Task | Related Requirements | Status |
|----|------|----------------------|--------|
| T1901 | Prepare sample commit message fixtures (e.g., via `mktemp`) to drive manual hook validation before implementation. | CMH-001 | ✅ (2025-10-04 – Created `/tmp/gitlint-pass.XSp1tL` and `/tmp/gitlint-fail.UaXqSh` for manual testing.) |
| T1902 | Implement `githooks/commit-msg`, remove gitlint call from `githooks/pre-commit`, ensure scripts stay executable. | CMH-001, CMH-002 | ✅ (2025-10-04 – Added `githooks/commit-msg`, updated pre-commit header + scanners, set executable bits.) |
| T1903 | Run manual hook checks (`githooks/commit-msg` with pass/fail fixtures, `githooks/pre-commit` on staged sample) and execute `./gradlew spotlessApply check`. | CMH-001, CMH-002 | ✅ (2025-10-04 – `githooks/commit-msg /tmp/gitlint-pass.XSp1tL` passed; `/tmp/gitlint-fail.UaXqSh` failed as expected; `githooks/pre-commit` and `./gradlew --no-daemon spotlessApply check` both succeeded after clearing `.gradle/configuration-cache`.) |
| T1904 | Update documentation (runbook/contributor guidance), sync spec/plan/tasks, and clear resolved open question. | CMH-003 | ✅ (2025-10-04 – Updated runbook + AGENTS hook guard, synced roadmap/spec/plan/tasks, open questions log cleared.) |
| T1905 | Implement Spotless stale-cache auto-recovery in `githooks/pre-commit` and validate with a simulated failure. | CMH-004 | ✅ (2025-10-04 – Added retry helper, staged `githooks/pre-commit`, stubbed `gradlew` to emit the stale-cache error once, confirmed the hook cleared `.gradle/configuration-cache` and reran Gradle successfully, then restored wrapper.) |

Update the status column as tasks complete, keeping each increment ≤10 minutes and sequencing validation commands before code whenever feasible.

