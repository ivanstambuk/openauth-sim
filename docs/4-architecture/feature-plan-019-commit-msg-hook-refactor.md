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
- Pre-commit hook auto-heals Spotless stale cache failures without manual intervention (CMH-004).
- Repository `.gitlint` enforces Conventional Commit rules (CMH-005).
- CI runs gitlint checks using the repository configuration (CMH-006).
- `./gradlew spotlessApply check` passes after each increment.

## Proposed Increments
- ☑ R1901 – Implement `githooks/commit-msg`, adjust `githooks/pre-commit` to remove gitlint usage, and ensure scripts are executable.
- ☑ R1902 – Update contributor documentation (runbook + relevant references), exercise the hooks manually, and run `./gradlew spotlessApply check`.
- ☑ R1903 – Teach the pre-commit hook to clear `.gradle/configuration-cache` once when the Spotless stale-cache error appears and rerun the failing Gradle command.
- ☑ R1906 – Tighten Spotless retry to exact message match and log success/failure.
- ☑ R1904 – Add repository `.gitlint`, update documentation, and validate gitlint enforcement against allowed/disallowed commit messages.
- ☑ R1905 – Wire gitlint into CI so pushes/PRs fail on non-compliant commit messages.

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
- 2025-10-04 – R1903 added Spotless stale-cache auto-retry to the pre-commit hook and verified via stubbed wrapper simulation.
- 2025-10-04 – R1904 added `.gitlint` config, updated docs, and validated gitlint pass/fail scenarios.
- 2025-10-04 – R1905 added CI gitlint job and documented server-side enforcement.
- 2025-10-04 – R1906 tightened Spotless retry matching/logging with success/failure logs.

## Analysis Gate
_Re-run after tasks checklist is published and increments scoped._

- [x] Specification completeness verified.
- [x] Open questions reviewed.
- [x] Plan aligns with specification requirements.
- [x] Tasks cover proposed increments with test-first ordering.
- [x] Tooling readiness checked.
- [x] Constitution compliance confirmed (hooks + documentation only, no dependency changes).
