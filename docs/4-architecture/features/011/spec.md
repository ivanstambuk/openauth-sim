# Feature 011 – Governance & Workflow Automation

| Field | Value |
|-------|-------|
| Status | In migration (Batch P3) |
| Last updated | 2025-11-11 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/011/plan.md` |
| Linked tasks | `docs/4-architecture/features/011/tasks.md` |
| Roadmap entry | #11 – Governance & Workflow Automation |

## Overview
Feature 011 is the single source of truth for OpenAuth Simulator governance: AGENTS.md, the project constitution, session
runbooks, analysis-gate instructions, managed Git hooks, gitlint policy, formatter adoption, and verification logging all
flow through this specification. The feature absorbs the historical governance/tooling streams (legacy Features 019 and 032)
so commit-message enforcement, pre-commit workflows, and Palantir Java Format adoption are documented alongside the
runbooks they protect. No code changes ship in this migration; the goal is documentation parity and auditable workflows.

## Clarifications
- 2025-10-04 – Gitlint must run inside `githooks/commit-msg` using the message path provided by Git; the pre-commit hook
  stays focused on staged-content checks (Spotless, targeted Gradle tasks, gitleaks) while warming/clearing the Gradle
  configuration cache as needed.
- 2025-10-04 – The repository `.gitlint` profile enforces Conventional Commits, 100-character titles, and 120-character
  body lines; CI must run the same rules for pushes/PRs.
- 2025-10-04 – Pre-commit handles Spotless stale-cache retries by deleting `.gradle/configuration-cache/**` at most once per
  run and logging the retry outcome.
- 2025-10-19 – Palantir Java Format 2.78.0 is the canonical formatter across Spotless, hooks, IDE guidance, and repository
  lock files (120-character wrap, Java 17). The repository-wide reformat already landed; this spec records the policy.
- 2025-11-11 – Batch P3 moves legacy Features 019/032 into this spec, removes the `legacy/` tree once absorbed, and requires
  `_current-session.md` to log every governance change and verification command.

## Goals
- G-011-01 – Keep AGENTS.md, session quick references, runbooks, and the constitution aligned with Feature 011 so new agents
  can find governance policy instantly.
- G-011-02 – Document and verify the managed hook workflows (`githooks/pre-commit`, `githooks/commit-msg`) including
  gitlint enforcement, cache warm/retry logic, and logging expectations.
- G-011-03 – Preserve Palantir formatter adoption guidance (Spotless config, dependency locks, IDE setup, reformat history)
  so automated formatting stays deterministic.
- G-011-04 – Ensure analysis-gate execution, hook verification, and doc migrations remain auditable via
  `_current-session.md` and recorded commands.

## Non-Goals
- Editing hook scripts, Gradle configs, or formatter versions in this documentation-only increment.
- Relaxing gitlint/formatter policies or reintroducing google-java-format.
- Adding compatibility shims for legacy feature IDs; governance docs now reference Feature 011 exclusively.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-011-01 | Governance docs (AGENTS.md, constitution, session quick reference, runbook-session-reset, analysis-gate checklist) cite Feature 011 as the owner and link back to this spec/plan/tasks. | `rg "Feature 011" AGENTS.md docs/5-operations/runbook-session-reset.md docs/6-decisions/project-constitution.md` shows canonical references; `_current-session.md` logs updates. | Manual doc review + `./gradlew --no-daemon spotlessApply check`. | Agents hunt legacy IDs to find governance rules. | None (doc-only). | Goals G-011-01.
| FR-011-02 | `githooks/commit-msg` runs `gitlint` with `.gitlint` (Conventional Commits, 100/120 columns) on the message file Git provides, and CI executes the same rules on pushes/PRs. | Running `githooks/commit-msg .git/COMMIT_EDITMSG` blocks invalid commits; CI workflow lists gitlint job output. | Manual tests with compliant/non-compliant fixtures; CI log review. | Bad commit messages land or hooks fail silently. | Hook logs only; no new telemetry. | Legacy Feature 019.
| FR-011-03 | `githooks/pre-commit` warms the Gradle configuration cache via `./gradlew --no-daemon help --configuration-cache`, retries Spotless once when stale-cache errors appear, and logs retry outcomes before running staged-content checks (Spotless, targeted Gradle tasks, gitleaks). | Pre-commit output shows cache warm + retry log; staged checks run afterwards. | Manual invocation on staged files; plan/tasks capture commands. | Hooks skip cache warm or silently swallow Spotless failures. | Hook log only. | Legacy Feature 019.
| FR-011-04 | Contributors verify the hook guard (`git config core.hooksPath githooks`) before staging changes, and plan/tasks require recording the command plus results in `_current-session.md`. | `git config core.hooksPath` returns `githooks`; session log lists command output. | Checklist items reference guard; `_current-session.md` snapshot updated per session. | Hooks disabled or misconfigured without detection. | None. | Clarifications (2025-11-11).
| FR-011-05 | Analysis gate usage (`docs/5-operations/analysis-gate-checklist.md`) is documented in plan/tasks with instructions to log when the gate runs, what it covered, and resulting follow-ups. | Plan/tasks link to the checklist; `_current-session.md` records gate executions. | Doc review; session log entries. | Analysis gates skipped or untracked. | None. | Goals G-011-04.
| FR-011-06 | Spotless configuration, version catalog, and dependency locks pin Palantir Java Format 2.78.0; the spec documents repo-wide reformat expectations and IDE setup. | `build.gradle.kts` uses `palantirJavaFormat("2.78.0")`; locks reference Palantir artifacts; docs explain IDE steps. | `rg "palantir" build.gradle.kts gradle/libs.versions.toml githooks -n`; plan/tasks mention command history. | Formatter drift or conflicting instructions. | None. | Legacy Feature 032.
| FR-011-07 | Managed hooks, runbooks, and README/CONTRIBUTING references mention Palantir formatting, rebase guidance, and cite the `./gradlew --no-daemon spotlessApply check` command required after doc/code changes. | `rg "Palantir" AGENTS.md CONTRIBUTING.md githooks -n` shows updated instructions; doc lint passes. | Manual review + spotless. | Contributors follow stale google-java-format guidance. | None. | Legacy Feature 032.
| FR-011-08 | `_current-session.md` logs every governance change, including commands executed (`git config core.hooksPath`, `githooks/...`, `spotlessApply check`, repo reformat) and outstanding follow-ups/backlog items. | Session snapshot + session log (docs/_current-session.md) list each governance increment, command, and pending action. | Review logs after each increment. | Auditors cannot trace changes or verification commands. | None. | Goals G-011-04.

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-011-01 | Documentation remains ASCII/Markdown and follows project templates (AGENTS, runbooks, plans/tasks). | Consistency | `./gradlew --no-daemon spotlessApply check`; reviewer confirmation. | Docs templates, Spotless. | Clarifications 2025-11-11.
| NFR-011-02 | Pre-commit runtime ≤30 s and commit-msg gitlint runtime ≤2 s on reference hardware; failures log actionable retry output. | Developer ergonomics | Hook log timestamps captured during dry runs. | githooks, Gradle wrapper, gitlint. | Legacy Feature 019.
| NFR-011-03 | Deterministic formatting: Palantir version pin + locks guarantee identical results locally/CI. | Build reproducibility | `rg "palantirJavaFormat"` diff shows single version; lockfiles reviewed after updates. | Spotless, Gradle lockfiles. | Legacy Feature 032.
| NFR-011-04 | CI mirrors local governance checks: gitlint job, Palantir formatting, and hook-equivalent Gradle commands must run in workflows. | CI parity | GitHub workflow logs show gitlint + Spotless runs; failures block merges. | `.github/workflows/**`. | Legacy Feature 019/032.
| NFR-011-05 | Governance actions remain auditable: `_current-session.md` + session log (docs/_current-session.md) entries include command history, runtimes, and follow-ups for each increment. | Traceability | Logs updated per increment; reviewers verify before closing tasks. | `_current-session.md`. | Goals G-011-04.

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-011-01 | Governance docs reference Feature 011 and align with AGENTS/runbooks/constitution wording. |
| S-011-02 | `githooks/commit-msg` + CI gitlint enforce the `.gitlint` profile. |
| S-011-03 | `githooks/pre-commit` warms caches, retries Spotless on stale cache, and logs the sequence before staged checks. |
| S-011-04 | Hook guard (`git config core.hooksPath githooks`) is documented/run each session. |
| S-011-05 | Analysis gate checklist referenced + logged for governance increments. |
| S-011-06 | Palantir formatter pin (Spotless/Libraries/locks) documented with IDE guidance and repository reformat history. |
| S-011-07 | Hooks/docs mention Palantir usage, `spotlessApply check`, and rebase guidance. |
| S-011-08 | `_current-session.md` + session log (docs/_current-session.md) capture every governance action + verification command. |

## Test Strategy
- **Hooks:** Run `githooks/commit-msg` with compliant/non-compliant fixtures and `githooks/pre-commit` on staged changes to
  observe cache warm + retry logs.
- **Doc lint:** `./gradlew --no-daemon spotlessApply check` after governance doc updates.
- **Formatter policy:** `./gradlew --no-daemon --write-locks spotlessApply check` when updating locks; inspect diffs to verify
  Palantir artifacts only.
- **CI parity:** Observe gitlint + Spotless steps in GitHub Actions logs whenever governance docs change.
- **Logging:** Append executed commands and outcomes to `_current-session.md` per increment.

## Interface & Contract Catalogue
### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-011-01 | `GovernanceDocSet` (AGENTS.md, runbooks, constitution, analysis gate checklist, session log (docs/_current-session.md)). | docs |
| DO-011-02 | `HookWorkflow` describing `pre-commit` (cache warm, Spotless retry, staged checks) and `commit-msg` (gitlint). | githooks |
| DO-011-03 | `FormatterPolicy` enumerating Palantir version pin, lockfile entries, IDE guidance, and reformat expectations. | build scripts, docs |

### CLI / Commands
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-011-01 | `githooks/commit-msg <message-file>` | Runs gitlint with `.gitlint`; exits non-zero on violations.
| CLI-011-02 | `githooks/pre-commit` | Warms Gradle cache, retries Spotless once on stale cache, runs staged-content checks.
| CLI-011-03 | `git config core.hooksPath` | Verification guard recorded per session (must return `githooks`).
| CLI-011-04 | `./gradlew --no-daemon spotlessApply check` / `--write-locks spotlessApply check` | Doc/formatter verification gate recorded in plan/tasks.

### Fixtures & Artefacts
| ID | Path | Purpose |
|----|------|---------|
| FX-011-01 | `.gitlint` | Conventional Commit policy consumed by gitlint + CI.
| FX-011-02 | `githooks/pre-commit`, `githooks/commit-msg` | Managed hooks referenced in spec/plan/tasks.
| FX-011-03 | `gradle/libs.versions.toml`, `build.gradle.kts` | Palantir formatter pin + Spotless config.
| FX-011-04 | `build/reports/spotless/`, `.gradle/configuration-cache/` | Artefacts referenced in retry/warm documentation.

## Documentation Deliverables
- `AGENTS.md`, `docs/5-operations/runbook-session-reset.md`, `docs/5-operations/session-quick-reference.md`, and
  `docs/6-decisions/project-constitution.md` reference Feature 011 for governance.
- `docs/5-operations/analysis-gate-checklist.md` cross-links Feature 011 when recording gate usage.
- `_current-session.md` logs commands/deletions/verification runs for governance increments.
- CONTRIBUTING/README sections describe gitlint + Palantir policies and command sequences.

## Spec DSL
```
domain_objects:
  - id: DO-011-01
    name: GovernanceDocSet
    fields:
      - name: file
        type: string
      - name: owner
        type: string
  - id: DO-011-02
    name: HookWorkflow
    fields:
      - name: hook
        type: enum[pre-commit,commit-msg]
      - name: steps
        type: list<string>
      - name: verificationCommands
        type: list<string>
  - id: DO-011-03
    name: FormatterPolicy
    fields:
      - name: formatter
        type: string
      - name: version
        type: string
      - name: wrapWidth
        type: int
      - name: verificationCommands
        type: list<string>
cli_commands:
  - id: CLI-011-01
    command: githooks/commit-msg <message-file>
  - id: CLI-011-02
    command: githooks/pre-commit
  - id: CLI-011-03
    command: ./gradlew --no-daemon spotlessApply check
fixtures:
  - id: FX-011-01
    path: .gitlint
  - id: FX-011-02
    path: githooks/pre-commit
```
