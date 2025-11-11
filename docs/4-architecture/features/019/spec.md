# Feature 019 – Commit Message Hook Refactor

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/019/plan.md` |
| Linked tasks | `docs/4-architecture/features/019/tasks.md` |
| Roadmap entry | #11 – Governance & Workflow Automation |

## Overview
Move gitlint enforcement from the pre-commit hook to a dedicated `commit-msg` hook so Git always supplies the commit
message path, keep pre-commit focused on staged-content checks (Spotless, targeted tests, gitleaks), and document the
hook responsibilities plus CI coverage. The refactor also adds reliable Spotless stale-cache recovery, Gradle
configuration-cache warming, and a repository-level `.gitlint` profile enforced locally and in CI.

## Clarifications
- 2025-10-04 – Commit message linting must run inside `githooks/commit-msg` using the message-file argument that Git
  passes; pre-commit may no longer read `.git/COMMIT_EDITMSG`.
- 2025-10-04 – `.gitlint` enforces Conventional Commits, 100-character titles, and 120-character body lines.
- 2025-10-04 – When Spotless surfaces the exact stale configuration-cache message, the hook clears
  `.gradle/configuration-cache/` and retries the Gradle command once.
- 2025-10-04 – Pre-commit must warm the Gradle configuration cache via
  `./gradlew --no-daemon help --configuration-cache` (with the retry helper) before other Gradle tasks each run.
- 2025-10-04 – CI must run gitlint with the repository configuration for pushes and pull requests.

## Goals
- G-019-01 – Ensure commit message linting always runs (both locally and in CI) via the `commit-msg` hook + gitlint job.
- G-019-02 – Improve pre-commit reliability by handling Spotless stale-cache errors and warming the configuration cache.
- G-019-03 – Keep contributor/runbook documentation aligned with the hook architecture and prerequisites.

## Non-Goals
- N-019-01 – Altering existing Gradle tasks executed by the pre-commit hook beyond cache warming/retry flow.
- N-019-02 – Adding new linting rules beyond the documented `.gitlint` configuration.
- N-019-03 – Changing repository history or rewriting previous commit messages.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-019-01 | Implement `githooks/commit-msg` that runs `gitlint` against the commit message file passed by Git. | Running `githooks/commit-msg .git/COMMIT_EDITMSG` enforces `.gitlint` rules and exits 0/1 appropriately. | Manual invocation with good/bad fixtures confirms behaviour; hook logs the lint command. | Non-compliant commit messages block commits with gitlint output. | n/a | Clarifications 2025-10-04. |
| FR-019-02 | Update `githooks/pre-commit` to remove gitlint usage, focus on staged-content checks, and keep existing gitleaks/Gradle/test steps. | Pre-commit no longer touches `.git/COMMIT_EDITMSG` and still runs the expected toolchain. | Dry-run `git commit` triggers pre-commit and shows the new sequence. | Missing steps cause automation to fail or skip required checks. | n/a | Clarifications 2025-10-04. |
| FR-019-03 | Add Spotless stale-cache retry logic that clears `.gradle/configuration-cache` once per failure and logs success/failure. | Hook detects the exact stale-cache message, deletes cache, reruns command, and succeeds. | Stub Gradle wrapper to emit the stale-cache message; verify log entries. | If retry also fails, hook exits non-zero with logged failure. | n/a | Clarifications 2025-10-04. |
| FR-019-04 | Warm the Gradle configuration cache once per pre-commit run before other tasks. | Hook executes `./gradlew --no-daemon help --configuration-cache` (with retry) and logs completion. | Inspect hook output/log; rerun ensures warm step skipped/reported once. | Missing warm step flagged during hook review. | n/a | Clarifications 2025-10-04. |
| FR-019-05 | Add repository `.gitlint` file documenting Conventional Commit policy and line-length limits. | `.gitlint` exists at repo root; gitlint uses it automatically. | `gitlint --msg-filename sample` passes/fails as expected. | Absence of file or wrong config fails hook/CI. | n/a | Clarifications 2025-10-04. |
| FR-019-06 | CI workflow runs gitlint with the repository config on pushes/PRs. | GitHub workflow includes gitlint job referencing `.gitlint`. | Break CI with invalid commit to observe failure. | Missing job allows non-compliant commits to pass review. | n/a | Clarifications 2025-10-04. |
| FR-019-07 | Update documentation (AGENTS, runbooks, spec/plan/tasks) to describe hook responsibilities, prerequisites, and verification steps. | Docs mention commit-msg hook, gitlint requirement, cache-warming behaviour, and CI enforcement. | Doc lint/spotless passes; contributors can follow instructions. | Missing documentation flagged during reviews. | n/a | Clarifications 2025-10-04. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-019-01 | Tooling parity | Keep commit linting consistent between local hooks and CI. | Both commit-msg hook and CI gitlint job use `.gitlint`. | gitlint binary, CI workflow. | Clarifications 2025-10-04. |
| NFR-019-02 | Developer ergonomics | Pre-commit runtime remains ≤30s; commit-msg hook completes ≤2s. | Manual measurements/logging within hooks. | Gradle wrapper, gitleaks, gitlint. | Clarifications 2025-10-04. |

## UI / Interaction Mock-ups
_Not applicable – hook-level change only._

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-019-01 | Commit message linting runs through `commit-msg` with `.gitlint` rules. |
| S-019-02 | Pre-commit hook handles cache warming, stale-cache retry, and staged-content checks without gitlint. |
| S-019-03 | Documentation/runbooks/spec/plan/tasks explain the hook architecture and prerequisites. |
| S-019-04 | CI enforces gitlint with the repository configuration. |

## Test Strategy
- **Hooks:** Manually invoke `githooks/commit-msg` with compliant/non-compliant fixtures; run `githooks/pre-commit` on a
  staged change to validate cache warm + retry logic.
- **CI:** Trigger workflow via test branch to ensure gitlint job runs and blocks invalid messages.
- **Docs:** Run `./gradlew spotlessApply check` after doc changes to keep formatting and lint tools green.

## Interface & Contract Catalogue
### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-019-01 | `CommitMsgHookConfig` – environment contract describing the gitlint command, message path, and `.gitlint` location. | githooks |
| DO-019-02 | `PreCommitWorkflow` – ordered sequence of cache warm, Spotless retry guard, gitleaks, Gradle tasks, targeted tests. | githooks |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| — | — | Hooks run locally; no external routes apply. | |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-019-01 | `githooks/commit-msg <message-file>` | Executes gitlint using repository config, fails on invalid commit messages. |
| CLI-019-02 | `githooks/pre-commit` | Warms Gradle cache, runs Spotless with retry, executes staged-content checks. |

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|-----------|---------------------------|
| — | — | No telemetry changes; hooks log locally. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-019-01 | `/tmp/gitlint-pass.*`, `/tmp/gitlint-fail.*` (generated during validation) | Sample commit messages for hook/manual testing. |
|
### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| — | — | No UI impact. |

## Telemetry & Observability
- Hook scripts log cache warm, retry outcomes, and gitlint invocations to aid local diagnostics.
- CI workflow logs gitlint job output for failed commits.

## Documentation Deliverables
- Update `AGENTS.md`, `docs/5-operations/runbook-session-reset.md`, and related runbooks to reference commit-msg hook,
  cache warming, and gitlint prerequisites.
- Note the changes in `docs/4-architecture/knowledge-map.md` and this feature’s plan/tasks.

## Fixtures & Sample Data
No repository fixtures added; validation uses disposable temp files.

## Spec DSL
```
domain_objects:
  - id: DO-019-01
    name: CommitMsgHookConfig
    fields:
      - name: gitlintCommand
        type: string
      - name: messageFile
        type: path
      - name: gitlintConfig
        type: path
  - id: DO-019-02
    name: PreCommitWorkflow
    fields:
      - name: steps
        type: list<string>
        constraints: [warm_cache, spotless_retry, gitleaks, gradle_check, targeted_tests]
cli_commands:
  - id: CLI-019-01
    command: githooks/commit-msg
  - id: CLI-019-02
    command: githooks/pre-commit
telemetry_events: []
fixtures:
  - id: FX-019-01
    path: /tmp/gitlint-pass.*
    purpose: hook validation
ui_states: []
```

## Appendix
_None._
