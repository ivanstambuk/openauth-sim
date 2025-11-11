# Feature 019 Tasks – Commit Message Hook Refactor

_Status: Complete_  
_Last updated: 2025-11-10_

> Each task remained ≤30 minutes and staged validation before implementation. All verification commands were executed
> during the original work; rerun them if future edits touch the hooks.

## Checklist
- [x] T-019-01 – Stage failing gitlint scenarios with temp message fixtures (FR-019-01, S-019-01).  
  _Intent:_ Create `/tmp/gitlint-pass.*` and `/tmp/gitlint-fail.*` to drive commit-msg hook validation before implementation.  
  _Verification commands:_  
  - `printf "feat: pass" > /tmp/gitlint-pass.test`  
  - `printf "Fix stuff" > /tmp/gitlint-fail.test`

- [x] T-019-02 – Implement `githooks/commit-msg`, remove gitlint from pre-commit, and validate with fixtures (FR-019-01, FR-019-02, S-019-01).  
  _Intent:_ Ensure gitlint always runs via commit-msg and pre-commit focuses on staged checks.  
  _Verification commands:_  
  - `githooks/commit-msg /tmp/gitlint-pass.test`  
  - `githooks/commit-msg /tmp/gitlint-fail.test`  
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-019-03 – Add Spotless stale-cache retry helper and validate via simulated failure (FR-019-03, S-019-02).  
  _Intent:_ Automatically clear `.gradle/configuration-cache` and rerun once on the exact stale-cache message.  
  _Verification commands:_  
  - `SIMULATE_STALE_CACHE=1 githooks/pre-commit`

- [x] T-019-04 – Warm the Gradle configuration cache at hook start and ensure the step runs once per invocation (FR-019-04, S-019-02).  
  _Intent:_ Reduce first Gradle task latency during pre-commit.  
  _Verification commands:_  
  - `githooks/pre-commit` (observe warm-cache log entry)

- [x] T-019-05 – Update documentation (AGENTS, runbook, spec/plan/tasks) with hook duties and gitlint prerequisites; rerun spotless (FR-019-07, S-019-03).  
  _Intent:_ Keep governance docs synchronized with hook behaviour.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-019-06 – Add `.gitlint` + CI gitlint job; confirm failures on bad commit messages (FR-019-05, FR-019-06, S-019-04).  
  _Intent:_ Enforce Conventional Commits locally and in CI.  
  _Verification commands:_  
  - `gitlint --msg-filename /tmp/gitlint-pass.test`  
  - `gitlint --msg-filename /tmp/gitlint-fail.test`  
  - Trigger GitHub Actions workflow (bad commit message expected to fail gitlint job)

## Verification Log
- 2025-10-04 – `githooks/pre-commit`, `githooks/commit-msg`, `gitlint --msg-filename …`, `./gradlew --no-daemon spotlessApply check`
- 2025-11-10 – `./gradlew --no-daemon spotlessApply check` (template migration verification)

## Notes / TODOs
- Monitor hook runtimes; if Gradle workload grows, consider parallelising steps or adding a summary timer.
