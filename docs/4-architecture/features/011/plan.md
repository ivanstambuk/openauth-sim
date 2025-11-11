# Feature 011 – Governance & Workflow Automation Plan

| Field | Value |
|-------|-------|
| Status | In migration (Batch P3) |
| Last updated | 2025-11-11 |
| Owners | Ivan (project owner) |
| Specification | `docs/4-architecture/features/011/spec.md` |
| Tasks checklist | `docs/4-architecture/features/011/tasks.md` |
| Roadmap entry | #11 – Governance & Workflow Automation |

## Vision
Keep governance policies—AGENTS.md, runbooks, constitution, managed hooks, gitlint/formatter guidance, and analysis-gate
workflows—in sync so any contributor can reproduce the Specification-Driven Development cadence without chasing legacy
feature IDs. Feature 011 now documents the hook workflows (from legacy Feature 019) and the Palantir formatter policy
(legacy Feature 032) alongside the core governance artefacts.

## Scope
- Merge the legacy hook + formatter specs into the consolidated spec/plan/tasks (doc-only changes for Batch P3).
- Document verification commands (hook guard, gitlint, Spotless/lock refresh, analysis gate) and ensure `_current-session.md`
  logs every governance increment.
- Remove `docs/4-architecture/features/011/legacy/{019,032}` once the requirements are embedded.
- Stage remaining backlog/automation work (gitlint tuning, markdown lint) for future increments.

_Out of scope:_ Editing hook scripts, Gradle configs, or formatter versions; changing gitlint rules; adding new tooling.

## Dependencies
| Dependency | Notes |
|------------|-------|
| `AGENTS.md`, `docs/5-operations/runbook-session-reset.md`, `docs/5-operations/session-quick-reference.md` | Must point to Feature 011 and describe hook guard expectations. |
| `docs/6-decisions/project-constitution.md`, `docs/5-operations/analysis-gate-checklist.md` | Reference governance owner + gate steps. |
| `.gitlint`, `githooks/pre-commit`, `githooks/commit-msg` | Managed hook scripts/policy enforced by this feature. |
| `gradle/libs.versions.toml`, `build.gradle.kts` | Palantir formatter pin remains documented here. |
| `_current-session.md` | Receives command logs + migration summaries. |

## Legacy Integration Tracker
| Legacy Feature(s) | Increment(s) | Status | Notes |
|-------------------|--------------|--------|-------|
| 019 | P3-I1 (spec), P3-I2 (plan/tasks) | Completed | FR-011-02/03 and NFR-011-02/04 now describe hook guard logging, gitlint policy, and session-log expectations that originated in Feature 019. |
| 032 | P3-I1, P3-I2 | Completed | FR-011-06/07 and NFR-011-03/04 capture the Palantir formatter pin, spotless/qualityGate enforcement, and CI parity requirements previously maintained by Feature 032. |

## Assumptions & Risks
- Governance remains documentation-driven; contributors continue running `./gradlew --no-daemon spotlessApply check` after edits.
- Forgetting to log hook guard/analysis gate commands would reduce traceability—mitigate via explicit checklist items.
- Hook runtimes must stay within ergonomic bounds (≤30 s for pre-commit, ≤2 s for commit-msg); monitor logs if behaviour drifts.

## Increment Map (≤90 min)
| Increment | Intent | Owner | Status | Notes |
|-----------|--------|-------|--------|-------|
| P3-I1 | Absorb legacy Features 019/032 into `spec.md` (hook workflows + Palantir formatter policy). | Ivan | Completed | FR/NFR tables updated; spec references hooks, gitlint, Palantir pin. |
| P3-I2 | Refresh plan/tasks to list hook guard, gitlint, analysis gate, and `spotlessApply` verification commands. | Ivan | Completed | Plan + tasks now enumerate the governance command set, log destinations, and cross-links to `_current-session.md` / session log (docs/_current-session.md) updates. |
| P3-I3 | Remove `docs/4-architecture/features/011/legacy/` after verifying the migration; log `rm -rf` + `ls` output in `_current-session.md`. | Ivan | Pending | Execute once spec/plan/tasks reviewed. |
| P3-I4 | Sync governance artefacts (AGENTS/runbooks/constitution/analysis gate) and record commands in `_current-session.md`; prepare backlog items for post-migration automation. | Ivan | Pending | Requires doc updates + spotless run noted in logs. |

## Scenario Tracking
| Scenario | Description | Increment |
|----------|-------------|-----------|
| S-011-01 | Governance docs reference Feature 011 across AGENTS/runbooks/constitution. | P3-I4 |
| S-011-02 | `githooks/commit-msg` + CI gitlint enforce `.gitlint`. | P3-I2 |
| S-011-03 | `githooks/pre-commit` handles cache warm/retry and staged checks. | P3-I2 |
| S-011-04 | Hook guard (`git config core.hooksPath githooks`) logged per session. | P3-I2 |
| S-011-05 | Analysis gate checklist linked/logged. | P3-I2/P3-I4 |
| S-011-06 | Palantir formatter pin + lock refresh + IDE guidance captured. | P3-I1 |
| S-011-07 | Hooks/docs mention Palantir policy + `spotlessApply` command expectations. | P3-I2 |
| S-011-08 | `_current-session.md` + session log (docs/_current-session.md) capture each governance increment. | P3-I3/P3-I4 |

## Legacy Parity Review
- 2025-11-11 – Cross-checked Feature 011 FR/NFR sets against legacy Features 019/032. All governance, hook, gitlint, and formatter requirements are represented in the consolidated spec/plan/tasks; remaining automation ideas (gitlint tuning, markdown lint adoption) remain in the backlog.

## Quality & Tooling Gates
- `./gradlew --no-daemon spotlessApply check` after governance doc updates.
- `git config core.hooksPath` guard recorded before staging.
- Manual hook dry-runs (`githooks/pre-commit`, `githooks/commit-msg <file>`) when instructions change.
- (Future) `./gradlew --no-daemon qualityGate` when governance changes require verifying formatter impact.

## Analysis Gate
Run `docs/5-operations/analysis-gate-checklist.md` once P3-I4 finishes (docs synced + hooks logged). Record the execution in
`_current-session.md` and note any follow-ups in this plan.

- 2025-11-11 – Checklist rerun after cross-doc sync (AGENTS/runbooks/constitution/analysis-gate docs now reference Feature 011).
  - Spec/plan/tasks alignment confirmed; no open questions.
  - Governance commands logged: `git config core.hooksPath`, `tmp_index=$(mktemp); GIT_INDEX_FILE=$tmp_index git read-tree --empty; GIT_INDEX_FILE=$tmp_index ./githooks/pre-commit`, `./gradlew --no-daemon spotlessApply check`, `./gradlew --no-daemon qualityGate`.
  - `_current-session.md` captures the run plus the initial malformed `spotlessApply check,workdir:` invocation that was rerun without the typo.

## Implementation Drift Gate
After Batch P3 completes, ensure each FR/NFR from the spec maps to runbook entries, hook instructions, and recorded commands.
Document drift findings (if any) in this plan’s appendix with links to remediation tasks.

## Exit Criteria
- `spec.md`/`plan.md`/`tasks.md` describe hook workflows, gitlint policy, Palantir formatter pin, and verification commands.
- `docs/4-architecture/features/011/legacy/` removed; migration/session logs capture the deletion + spotless run.
- Governance artefacts (AGENTS, runbooks, constitution, session log (docs/_current-session.md), `_current-session.md`) reference Feature 011 and list
  hook/analysis-gate expectations.
- Spotless/quality gates rerun and logged when required by governance updates.

## Follow-ups / Backlog
- Track gitlint rule tuning, markdown lint adoption, and governance automation backlog under Feature 013 once Batch P3 closes.
- Consider documenting timing metrics for `githooks/pre-commit` and `githooks/commit-msg` runs in `_current-session.md` to
  monitor ergonomics.
