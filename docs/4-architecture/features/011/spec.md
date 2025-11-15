# Feature 011 – Governance & Workflow Automation

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-15 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/011/plan.md` |
| Linked tasks | `docs/4-architecture/features/011/tasks.md` |
| Roadmap entry | #11 – Governance & Workflow Automation |

## Overview
Feature 011 is the single source of truth for OpenAuth Simulator governance: AGENTS.md, the project constitution, session
runbooks, analysis-gate instructions, managed Git hooks, gitlint policy, formatter adoption, and verification logging all
flow through this specification so commit-message enforcement, pre-commit workflows, Checkstyle guardrails, and Palantir
Java Format 2.78.0 adoption are documented alongside the runbooks they protect. It operationalises the constitution’s
Clarification Gate and Implementation Drift Gate for governance artefacts and is backed by ADR-0002 (formatter/managed
hooks) and ADR-0003 (governance workflow and drift gates). Gitlint runs inside `githooks/commit-msg` with the repository
`.gitlint` profile (Conventional Commits, 100-character titles, 120-character body lines) and CI mirrors the same rules.
`githooks/pre-commit` warms the Gradle configuration cache via `./gradlew --no-daemon help --configuration-cache`,
retries Spotless once on stale-cache failures by deleting `.gradle/configuration-cache/**`, logs the retry outcome, and then
executes Spotless, targeted Gradle tasks, and gitleaks. Palantir Java Format 2.78.0 (120-character wrap, Java 17) remains
the canonical formatter across Spotless, hooks, IDE guidance, and lock files; Checkstyle tuning (for example, `WhitespaceAround`)
must be recorded here and applied via shared configuration rather than per-feature specs. Every governance increment logs
hook verdicts, `git config core.hooksPath githooks`, and verification commands in `_current-session.md` to keep these policies
auditable.

## Goals
- G-011-01 – Keep AGENTS.md, session quick references, runbooks, and the constitution aligned with Feature 011 so new agents
  can find governance policy instantly.
- G-011-02 – Document and verify the managed hook workflows (`githooks/pre-commit`, `githooks/commit-msg`) including
  gitlint enforcement, cache warm/retry logic, and logging expectations.
- G-011-03 – Preserve Palantir formatter adoption guidance (Spotless config, dependency locks, IDE setup, reformat history)
  so automated formatting stays deterministic.
- G-011-04 – Ensure analysis-gate execution, hook verification, and documentation updates remain auditable via
  `_current-session.md` and recorded commands.

## Non-Goals
- Editing hook scripts, Gradle configs, or formatter versions in this documentation-only increment.
- Relaxing gitlint/formatter policies or reintroducing google-java-format.
- Adding compatibility shims for legacy feature IDs; governance docs now reference Feature 011 exclusively.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-011-01 | Governance docs (AGENTS.md, constitution, session quick reference, runbook-session-reset, analysis-gate checklist) cite Feature 011 as the owner and link back to this spec/plan/tasks. | `rg "Feature 011" AGENTS.md docs/5-operations/runbook-session-reset.md docs/6-decisions/project-constitution.md` shows canonical references; `_current-session.md` logs updates. | Manual doc review + `./gradlew --no-daemon spotlessApply check`. | Agents hunt legacy IDs to find governance rules. | None (doc-only). | Spec
| FR-011-02 | `githooks/commit-msg` runs `gitlint` with `.gitlint` (Conventional Commits, 100/120 columns) on the message file Git provides, and CI executes the same rules on pushes/PRs. | Running `githooks/commit-msg .git/COMMIT_EDITMSG` blocks invalid commits; CI workflow lists gitlint job output. | Manual tests with compliant/non-compliant fixtures; CI log review. | Bad commit messages land or hooks fail silently. | Hook logs only; no new telemetry. | Spec
| FR-011-03 | `githooks/pre-commit` warms the Gradle configuration cache via `./gradlew --no-daemon help --configuration-cache`, retries Spotless once when stale-cache errors appear, and logs retry outcomes before running staged-content checks (Spotless, targeted Gradle tasks, gitleaks). | Pre-commit output shows cache warm + retry log; staged checks run afterwards. | Manual invocation on staged files; plan/tasks capture commands. | Hooks skip cache warm or silently swallow Spotless failures. | Hook log only. | Spec
| FR-011-04 | Contributors verify the hook guard (`git config core.hooksPath githooks`) before staging changes, and plan/tasks require recording the command plus results in `_current-session.md`. | `git config core.hooksPath` returns `githooks`; session log lists command output. | Checklist items reference guard; `_current-session.md` snapshot updated per session. | Hooks disabled or misconfigured without detection. | None. | Spec
| FR-011-05 | Analysis gate usage (`docs/5-operations/analysis-gate-checklist.md`) is documented in plan/tasks with instructions to log when the gate runs, what it covered, and resulting follow-ups. | Plan/tasks link to the checklist; `_current-session.md` records gate executions. | Doc review; session log entries. | Analysis gates skipped or untracked. | None. | Spec
| FR-011-06 | Spotless configuration, version catalog, and dependency locks pin Palantir Java Format 2.78.0; the spec documents repo-wide reformat expectations and IDE setup. | `build.gradle.kts` uses `palantirJavaFormat("2.78.0")`; locks reference Palantir artifacts; docs explain IDE steps. | `rg "palantir" build.gradle.kts gradle/libs.versions.toml githooks -n`; plan/tasks mention command history. | Formatter drift or conflicting instructions. | None. | Spec
| FR-011-07 | Managed hooks, runbooks, and README/CONTRIBUTING references mention Palantir formatting, rebase guidance, and cite the `./gradlew --no-daemon spotlessApply check` command required after doc/code changes. | `rg "Palantir" AGENTS.md CONTRIBUTING.md githooks -n` shows updated instructions; doc lint passes. | Manual review + spotless. | Contributors follow stale google-java-format guidance. | None. | Spec
| FR-011-08 | `_current-session.md` logs every governance change, including commands executed (`git config core.hooksPath`, `githooks/...`, `spotlessApply check`, repo reformat) and outstanding follow-ups/backlog items. | Session snapshot + session log (docs/_current-session.md) list each governance increment, command, and pending action. | Review logs after each increment. | Auditors cannot trace changes or verification commands. | None. | Spec

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-011-01 | Documentation remains ASCII/Markdown and follows project templates (AGENTS, runbooks, plans/tasks). | Consistency | `./gradlew --no-daemon spotlessApply check`; reviewer confirmation. | Docs templates, Spotless. | Spec |
| NFR-011-02 | Pre-commit runtime ≤30 s and commit-msg gitlint runtime ≤2 s on reference hardware; failures log actionable retry output. | Developer ergonomics | Hook log timestamps captured during dry runs. | githooks, Gradle wrapper, gitlint. | Spec |
| NFR-011-03 | Deterministic formatting: Palantir version pin + locks guarantee identical results locally/CI. | Build reproducibility | `rg "palantirJavaFormat"` diff shows single version; lockfiles reviewed after updates. | Spotless, Gradle lockfiles. | Spec |
| NFR-011-04 | CI mirrors local governance checks: gitlint job, Palantir formatting, and hook-equivalent Gradle commands must run in workflows. | CI parity | GitHub workflow logs show gitlint + Spotless runs; failures block merges. | `.github/workflows/**`. | Spec |
| NFR-011-05 | Governance actions remain auditable: `_current-session.md` + session log (docs/_current-session.md) entries include command history, runtimes, and follow-ups for each increment. | Traceability | Logs updated per increment; reviewers verify before closing tasks. | `_current-session.md`. | Spec |

## UI / Interaction Mock-ups

### Governance Runbook Panel (UI-011-01)
```
┌──────────────────────── Governance Runbook ───────────────────────┐
│ Session Reset Checklist                                          │
│  1. Read AGENTS.md                                               │
│  2. Run hook guard (git config core.hooksPath)                   │
│  3. Update docs/_current-session.md                              │
│                                                                  │
│ Commands                                                         │
│  ┌────────────────────────────┐  ┌────────────────────────────┐  │
│  │ git config core.hooksPath  │  │ githooks/pre-commit        │  │
│  ├────────────────────────────┤  ├────────────────────────────┤  │
│  │ githooks/commit-msg <msg>  │  │ ./gradlew spotlessApply…   │  │
│  └────────────────────────────┘  └────────────────────────────┘  │
│                                                                  │
│ Output Log (read-only)                                          │
│  • 12:01 hook guard → githooks                                  │
│  • 12:04 spotlessApply check ✔                                  │
│                                                                  │
│ Footer actions: [Copy log] [Append to _current-session.md]      │
└──────────────────────────────────────────────────────────────────┘
```

### Analysis Gate Checklist (UI-011-02)
```
┌──────────────────────── Analysis Gate ────────────────────────────┐
│ Status badge: [ IN PROGRESS ]                                    │
│ Tasks                                                            │
│  [ ] Hook guard recorded                                         │
│  [ ] Spotless/Gradle run logged                                  │
│  [ ] Docs synced (roadmap/knowledge-map/session quick ref)       │
│                                                                  │
│ Left column                              Right column            │
│  Checklist items                          Command log            │
│   - git config core.hooksPath             12:10 hook guard OK    │
│   - githooks/pre-commit                   12:13 spotlessApply OK │
│   - Update docs/_current-session.md       12:16 roadmap updated  │
│                                                                  │
│ Action bar: [Validate gate] [Copy summary] [Export to log]       │
└──────────────────────────────────────────────────────────────────┘
```

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

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-011-01 | Documentation | AGENTS.md + runbooks detail the governance workflow (hook guard, gitlint, Spotless) so operators know the sequence to run commands. | Linked from `docs/5-operations/runbook-session-reset.md`.
| API-011-02 | Documentation | The analysis gate checklist documents how governance commands (hooks, spotless) come together before each increment. | Shares `docs/5-operations/analysis-gate-checklist.md`.

### CLI / Commands
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-011-01 | `githooks/commit-msg <message-file>` | Runs gitlint with `.gitlint`; exits non-zero on violations.
| CLI-011-02 | `githooks/pre-commit` | Warms Gradle cache, retries Spotless once on stale cache, runs staged-content checks.
| CLI-011-03 | `git config core.hooksPath` | Verification guard recorded per session (must return `githooks`).
| CLI-011-04 | `./gradlew --no-daemon spotlessApply check` / `--write-locks spotlessApply check` | Doc/formatter verification gate recorded in plan/tasks.

### Telemetry Events
| ID | Event | Fields / Notes |
|----|-------|----------------|
| TE-011-01 | `telemetry.governance.hook.guard` | `hook`, `result`, `timestamp`; emitted when `git config core.hooksPath` confirms `githooks`.
| TE-011-02 | `telemetry.governance.hook.retry` | `hook`, `stage`, `outcome`; emitted when `githooks/pre-commit` retries Spotless due to stale cache.
| TE-011-03 | `telemetry.governance.formatter.version` | `formatter`, `version`, `wrapWidth`, `diffs` (summary); reported when `./gradlew --write-locks spotlessApply check` updates lock files.

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-011-01 | `.gitlint` | Conventional Commit policy consumed by gitlint + CI.
| FX-011-02 | `githooks/pre-commit`, `githooks/commit-msg` | Managed hooks referenced in spec/plan/tasks.
| FX-011-03 | `gradle/libs.versions.toml`, `build.gradle.kts` | Palantir formatter pin + Spotless config.
| FX-011-04 | `build/reports/spotless/`, `.gradle/configuration-cache/` | Artefacts referenced in retry/warm documentation.

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| UI-011-01 | Governance runbook panel open | Opening AGENTS/runbook documents exposes required commands and expectation for logging; triggered by governance sprint reviews.
| UI-011-02 | Analysis gate checklist view | Accessed when `docs/5-operations/analysis-gate-checklist.md` runs; shows the hook/Spotless checklist and requires `_current-session.md` updates.

## Telemetry & Observability

Governance-related frames stream through `TelemetryContracts` adapters to keep CLI/REST/UI logs sanitised and auditable:
- `telemetry.governance.hook.guard` records `hook`, `result`, and `timestamp` whenever `git config core.hooksPath` confirms `githooks`; failures surface as telemetry warnings so reviewers spot hook-guard drift without reading hook output.
- `telemetry.governance.hook.retry` tracks `hook`, `stage` (cacheWarm/spotlessRetry), and `outcome`; emitted when `githooks/pre-commit` detects a stale configuration cache, retries Spotless, and documents the retry result.
- `telemetry.governance.formatter.version` reports `formatter`, `version`, `wrapWidth`, and `lockChanges` whenever `./gradlew --write-locks spotlessApply check` refreshes the Palantir formatter lockfiles so automation can correlate formatter updates with verification commands.

## Documentation Deliverables
- `AGENTS.md`, `docs/5-operations/runbook-session-reset.md`, `docs/5-operations/session-quick-reference.md`, and
  `docs/6-decisions/project-constitution.md` reference Feature 011 for governance.
- `docs/5-operations/analysis-gate-checklist.md` cross-links Feature 011 when recording gate usage.
- `_current-session.md` logs commands/deletions/verification runs for governance increments.
- CONTRIBUTING/README sections describe gitlint + Palantir policies and command sequences.

## Fixtures & Sample Data
- `.gitlint` defines the Conventional Commit policy that `githooks/commit-msg` and CI run.
- `githooks/pre-commit` and `githooks/commit-msg` embody the managed hook sequences described in the governance docs.
- `gradle/libs.versions.toml` and `build.gradle.kts` capture the Palantir formatter version pin and Spotless configuration referenced in this spec.
- `build/reports/spotless/` and `.gradle/configuration-cache/` hold the verification outputs and cache warm artefacts that governance documentation cites.

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
  - id: CLI-011-04
    command: ./gradlew --no-daemon --write-locks spotlessApply check
fixtures:
  - id: FX-011-01
    path: .gitlint
  - id: FX-011-02
    path: githooks/pre-commit
  - id: FX-011-03
    path: githooks/commit-msg
  - id: FX-011-04
    path: gradle/libs.versions.toml
  - id: FX-011-05
    path: build/reports/spotless/
routes:
  - id: API-011-01
    transport: documentation
    description: AGENTS.md + runbooks detail the governance workflow (hook guard, gitlint, Spotless).
  - id: API-011-02
    transport: documentation
    description: Analysis gate checklist documents hook/Spotless commands and follow-ups.
telemetry_events:
  - id: TE-011-01
    event: telemetry.governance.hook.guard
  - id: TE-011-02
    event: telemetry.governance.hook.retry
  - id: TE-011-03
    event: telemetry.governance.formatter.version
ui_states:
  - id: UI-011-01
    description: Governance runbook panel open.
  - id: UI-011-02
    description: Analysis gate checklist view.
```
