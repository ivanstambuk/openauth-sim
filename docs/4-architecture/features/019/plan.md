# Feature Plan 019 – Commit Message Hook Refactor

_Linked specification:_ `docs/4-architecture/features/019/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
Relocate gitlint enforcement into `githooks/commit-msg`, keep pre-commit focused on staged-content validation with cache
warming/retry protections, and ensure both local contributors and CI follow the same Conventional Commit rules.
Success signals:
- Gitlint always executes via commit-msg hook and CI job (FR-019-01/FR-019-06).
- Pre-commit runs staged-content checks, warms the Gradle configuration cache, and auto-heals Spotless stale-cache errors
  (FR-019-02/FR-019-03/FR-019-04).
- Documentation/runbooks describe the workflow and prerequisites (FR-019-07).
- `./gradlew --no-daemon spotlessApply check` remains green after hook/doc updates.

## Scope Alignment
- **In scope:** Hook script changes, `.gitlint` configuration, documentation/runbook updates, CI gitlint integration,
  cache warm/retry logic validation.
- **Out of scope:** Changing Gradle tasks executed beyond the defined sequence, adding new lint rules, rewriting history.

## Dependencies & Interfaces
- `githooks/commit-msg` and `githooks/pre-commit` scripts.
- Git tooling (gitlint, gitleaks) installed locally and available in CI.
- `.gitlint` configuration and GitHub Actions workflow.
- Documentation under `AGENTS.md`, `docs/5-operations/runbook-session-reset.md`, and related governance files.

## Assumptions & Risks
- **Assumptions:** Contributors have `gitlint` available (documented prerequisite); CI runner has gitlint installed.
- **Risks / Mitigations:**
  - Spotless retry could mask other Gradle failures → limit retry to exact stale-cache message and log outcomes.
  - Hooks may become slow → keep cache warm step lightweight (`help --configuration-cache`) and monitor runtimes.

## Implementation Drift Gate
- Map FR/NFR IDs to increments/tasks before coding (see Scenario Tracking).
- Capture manual hook invocation logs/screenshots before closing the feature.
- Rerun `./gradlew --no-daemon spotlessApply check` after documentation updates.
- Confirm CI gitlint job runs in the latest workflow execution.

## Increment Map
1. **I1 – Commit-msg hook + gitlint config (S-019-01)**
   - _Goal:_ Introduce `githooks/commit-msg` and `.gitlint`, remove gitlint from pre-commit.
   - _Preconditions:_ Clarifications approved; gitlint available locally.
   - _Steps:_
     - Create `.gitlint` with Conventional Commit rules.
     - Implement `githooks/commit-msg` invoking gitlint with provided message file.
     - Update pre-commit to drop gitlint invocation.
     - Validate hook using pass/fail fixtures.
   - _Commands:_ `githooks/commit-msg /tmp/gitlint-pass`, `githooks/commit-msg /tmp/gitlint-fail`, `./gradlew spotlessApply check`.
   - _Exit:_ Hooks executable, manual tests confirm enforcement.

2. **I2 – Pre-commit reliability upgrades (S-019-02)**
   - _Goal:_ Add cache warm and stale-cache retry logic.
   - _Steps:_
     - Add helper that runs `./gradlew --no-daemon help --configuration-cache` once per run.
     - Implement stale-cache detection clearing `.gradle/configuration-cache` and retrying once with logged outcome.
     - Verify by stubbing Gradle wrapper to emit the exact stale-cache pattern.
   - _Commands:_ `GITHOOK_SIMULATE_STALE=1 githooks/pre-commit`, `./gradlew spotlessApply check`.
   - _Exit:_ Hook logs warm step + retry success/failure.

3. **I3 – Documentation & contributor guidance (S-019-03)**
   - _Goal:_ Update AGENTS, runbooks, and governance docs to capture the new workflow.
   - _Steps:_
     - Document commit-msg hook, gitlint prerequisite, cache warm behaviour, CI enforcement.
     - Sync spec/plan/tasks references; close open questions if any.
     - Rerun doc lint via `./gradlew spotlessApply check`.
   - _Exit:_ Docs reflect new workflow; build stays green.

4. **I4 – CI enforcement (S-019-04)**
   - _Goal:_ Ensure GitHub Actions runs gitlint for pushes and PRs.
   - _Steps:_
     - Update `.github/workflows/ci.yml` with gitlint job (fetch-depth 0, dynamic commit range).
     - Trigger workflow to confirm failures on bad commit messages.
   - _Commands:_ `act` (if available) or monitored GitHub run; `./gradlew spotlessApply check` for completeness.
   - _Exit:_ CI job present and documented.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-019-01 | I1 / T-019-01, T-019-02 | Commit-msg hook + gitlint config. |
| S-019-02 | I2 / T-019-03, T-019-04 | Pre-commit cache warm + retry logic. |
| S-019-03 | I3 / T-019-05 | Documentation alignment. |
| S-019-04 | I4 / T-019-06 | CI gitlint job.

## Analysis Gate
Completed 2025-10-04 once clarifications resolved; re-run only if hook requirements change.

## Exit Criteria
- Commit-msg hook + pre-commit reliability code merged.
- `.gitlint` and CI gitlint job active.
- Documentation updated.
- `./gradlew --no-daemon spotlessApply check` green post-change.

## Follow-ups / Backlog
- Monitor hook runtimes; consider parallelising Gradle steps if future features expand checks.
