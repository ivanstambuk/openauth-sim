# Feature Plan 019 – Commit Message Hook Refactor

_Status: Complete_
_Last updated: 2025-10-04_

## Objective
Relocate commit message linting from the pre-commit hook to a dedicated commit-msg hook so Git provides the message file path, preventing failures when `.git/COMMIT_EDITMSG` is unavailable while preserving existing pre-commit quality gates.

Reference specification: `docs/4-architecture/specs/feature-019-commit-msg-hook-refactor.md`.

## Success Criteria
- `githooks/commit-msg` invokes `gitlint` using the message file argument Git supplies and fails on lint violations (CMH-001).
- `githooks/pre-commit` no longer reads `.git/COMMIT_EDITMSG` yet continues running gitleaks, Gradle formatting, targeted tests, and `check` (CMH-002).
- Contributor documentation clarifies hook responsibilities and installation expectations (CMH-003).
- `./gradlew spotlessApply check` passes after each increment.

## Proposed Increments
- ☑ R1901 – Implement `githooks/commit-msg`, adjust `githooks/pre-commit` to remove gitlint usage, and ensure scripts are executable.
- ☑ R1902 – Update contributor documentation (runbook + relevant references), exercise the hooks manually, and run `./gradlew spotlessApply check`.

Each increment must stay within ≤10 minutes, lead with tests where possible (manual hook invocations), and log outcomes below.

## Checklist Before Implementation
- [x] Specification drafted with clarifications logged.
- [x] Open question resolved and captured in spec clarifications.
- [x] Tasks document created and aligned with proposed increments.
- [x] Analysis gate rerun once plan/tasks align (record below).

## Tooling Readiness
- Ensure `JAVA_HOME` points to a Java 17 JDK before running Gradle or hooks.
- Confirm `gitlint` and `gitleaks` are installed locally; document prerequisites if missing.

## Notes

- 2025-10-04 – Tasks checklist published; verified gitlint/gitleaks presence and JAVA_HOME configuration before implementation.
- 2025-10-04 – R1901 delivered commit-msg hook + pre-commit update; manual fixtures prepared for validation.
- 2025-10-04 – R1902 ran commit-msg/pre-commit hooks, cleared `.gradle/configuration-cache`, updated runbook + AGENTS, and reran `./gradlew --no-daemon spotlessApply check`.

## Analysis Gate
_Re-run after tasks checklist is published and increments scoped._

- [x] Specification completeness verified.
- [x] Open questions reviewed.
- [x] Plan aligns with specification requirements.
- [x] Tasks cover proposed increments with test-first ordering.
- [x] Tooling readiness checked.
- [x] Constitution compliance confirmed (hooks + documentation only, no dependency changes).
