# Feature 011 – Governance & Workflow Automation Plan

_Linked specification:_ [docs/4-architecture/features/011/spec.md](docs/4-architecture/features/011/spec.md)  
_Linked tasks:_ [docs/4-architecture/features/011/tasks.md](docs/4-architecture/features/011/tasks.md)  
_Status:_ Complete  
_Last updated:_ 2025-12-13  
_Owners:_ Ivan (project owner)  
_Roadmap entry:_ #11 – Governance & Workflow Automation

## Vision & Success Criteria
Keep governance policies—AGENTS.md, runbooks, constitution, managed hooks, gitlint/formatter guidance, and analysis-gate
workflows—in sync so any contributor can reproduce the Specification-Driven Development cadence without chasing legacy
feature IDs. Feature 011 documents the hook workflows and the Palantir formatter policy alongside the core governance artefacts.

## Scope Alignment
- Keep the managed hook + formatter policies documented inside Feature 011’s spec/plan/tasks.
- Document verification commands (hook guard, gitlint, Spotless/lock refresh, analysis gate) and ensure `_current-session.md`
  logs every governance increment.
- Stage remaining backlog/automation work (gitlint tuning, markdown lint) for future increments.

_Out of scope:_ Editing hook scripts, Gradle configs, or formatter versions; changing gitlint rules; adding substantial new tooling beyond small helper scripts that improve governance logging.

## Dependencies & Interfaces
| Dependency | Notes |
|------------|-------|
| [AGENTS.md](AGENTS.md), [docs/5-operations/runbook-session-reset.md](docs/5-operations/runbook-session-reset.md), [docs/5-operations/session-quick-reference.md](docs/5-operations/session-quick-reference.md) | Must point to Feature 011 and describe hook guard expectations. |
| [docs/6-decisions/project-constitution.md](docs/6-decisions/project-constitution.md), [docs/5-operations/analysis-gate-checklist.md](docs/5-operations/analysis-gate-checklist.md) | Reference governance owner + gate steps. |
| [.gitlint](.gitlint), [githooks/pre-commit](githooks/pre-commit), [githooks/commit-msg](githooks/commit-msg) | Managed hook scripts/policy enforced by this feature. |
| [gradle/libs.versions.toml](gradle/libs.versions.toml), [build.gradle.kts](build.gradle.kts) | Palantir formatter pin remains documented here. |
| `_current-session.md` | Receives command logs + migration summaries. |

## Assumptions & Risks
- Governance remains documentation-driven; contributors continue running `./gradlew --no-daemon spotlessApply check` after edits.
- Forgetting to log hook guard/analysis gate commands would reduce traceability—mitigate via explicit checklist items.
- Hook runtimes must stay within ergonomic bounds (≤30 s for pre-commit, ≤2 s for commit-msg); monitor logs if behaviour drifts.

## Implementation Drift Gate

- Summary: Use this gate to ensure that governance artefacts (AGENTS, runbooks, constitution, analysis-gate docs, hooks, gitlint/formatter policy) and recorded commands remain aligned with FR-011-01..10 and NFR-011-01..05, and that every governance change is logged in `_current-session.md`.

- **Checklist for future drift-gate runs (agents):**
  - **Preconditions**
    - [ ] ``docs/4-architecture/features/011`/{spec,plan,tasks}.md` updated to the current date; all clarifications are encoded in normative sections (no legacy “Clarifications” blocks).  
    - [ ] [docs/4-architecture/open-questions.md](docs/4-architecture/open-questions.md) has no `Open` entries for Feature 011.  
    - [ ] The following commands have been run in this increment and logged in [docs/_current-session.md](docs/_current-session.md):  
      - `git config core.hooksPath` (hook guard).  
      - A pre-commit dry run using a temporary index (e.g., `tmp_index=$(mktemp); GIT_INDEX_FILE=$tmp_index ./githooks/pre-commit`).  
      - `./gradlew --no-daemon spotlessApply check`.  
      - `./gradlew --no-daemon qualityGate` when governance changes affect the quality pipeline.  

  - **Spec ↔ governance docs mapping**
    - [ ] For FR-011-01..FR-011-10 and NFR-011-01..05, confirm that:  
      - [AGENTS.md](AGENTS.md), [docs/5-operations/runbook-session-reset.md](docs/5-operations/runbook-session-reset.md), and [docs/5-operations/session-quick-reference.md](docs/5-operations/session-quick-reference.md) reference Feature 011 as the governance owner and describe hook guard expectations.  
      - [docs/6-decisions/project-constitution.md](docs/6-decisions/project-constitution.md) and [docs/5-operations/analysis-gate-checklist.md](docs/5-operations/analysis-gate-checklist.md) align with Feature 011’s description of gates and logging.  
      - [.gitlint](.gitlint), [githooks/pre-commit](githooks/pre-commit), and [githooks/commit-msg](githooks/commit-msg) behaviour matches what the spec/plan/tasks state (gitlint enforcement, cache warm/retry, hook outputs).  
      - Palantir formatter version and usage are consistent across spec/plan/tasks, [gradle/libs.versions.toml](gradle/libs.versions.toml), and [build.gradle.kts](build.gradle.kts).  
      - Dependency lock refresh guidance (`--write-locks`) is present in AGENTS/runbooks and aligns with the dependency-lock expectations in FR-011-10.  

  - **Hooks & formatter behaviour**
    - [ ] Verify that [githooks/pre-commit](githooks/pre-commit) still:  
      - Guards the hooks path (`git config core.hooksPath`).  
      - Runs Spotless + targeted tests + `check` with the documented flags.  
      - Enforces size/binary guards and Gradle wrapper integrity.  
    - [ ] Verify that [githooks/commit-msg](githooks/commit-msg) still invokes gitlint with [.gitlint](.gitlint) and fails on non-compliant messages.  
    - [ ] Confirm Palantir Java Format is pinned to the documented version and used consistently by Spotless and any IDE guidance.  

  - **Audit logging & analysis gate**
    - [ ] Confirm that the analysis-gate workflow in [docs/5-operations/analysis-gate-checklist.md](docs/5-operations/analysis-gate-checklist.md) matches the Feature 011 spec and that governance-related gates are recorded in [docs/_current-session.md](docs/_current-session.md).  
    - [ ] Check that `_current-session.md` contains entries for each governance increment, including:  
      - Hook guard commands and outcomes.  
      - Pre-commit / commit-msg dry runs when hooks or policies change.  
      - `spotlessApply check` and `qualityGate` runs tied to governance updates.  

  - **Drift capture & remediation**
    - [ ] Any high-/medium-impact drift (e.g., AGENTS/runbooks contradict spec, hooks not enforcing policies as documented, missing logging) is:  
      - Logged as an `Open` entry in [docs/4-architecture/open-questions.md](docs/4-architecture/open-questions.md) for Feature 011.  
      - Captured as explicit tasks in [docs/4-architecture/features/011/tasks.md](docs/4-architecture/features/011/tasks.md).  
    - [ ] Low-impact drift (typos, minor link fixes, small doc mismatches) is corrected directly, with a brief note added to this section or the plan’s verification log.  

  - **Gate output**
    - [ ] This section is updated with the latest drift gate run date, key commands executed, and a concise “matches vs gaps” summary plus remediation notes.  
    - [ ] [docs/_current-session.md](docs/_current-session.md) logs that the Feature 011 Implementation Drift Gate was executed (date, commands, and reference to this plan section).  

### Drift Report – 2025-11-13
- **Scope review:** Spec/plan/tasks now describe steady-state governance coverage (AGENTS/runbooks/constitution, managed hooks, Palantir formatter, analysis gate logging) with no legacy references. Roadmap + knowledge map cite Feature 011 as the governance authority (FR-011-01..10, NFR-011-01..05, S-011-01..10).
- **Hook & formatter alignment:** Managed hook behaviour (gitlint enforcement, cache warm/retry, hook guard logging) and Palantir formatter pin remain documented across spec/plan/tasks plus supporting docs. Guidance ties hooks to `./gradlew spotlessApply check` and `qualityGate` when needed.
- **Audit logging:** `_current-session.md` logging expectations persist, and governance docs reference Feature 011 for hook guard + analysis gate procedures.
- **Verification commands:** `./gradlew --no-daemon spotlessApply check` (2025-11-13, 10 s, 96 tasks: 2 executed, 94 up-to-date) recorded for this drift gate; prior hook guard + gitlint dry runs remain in the verification log and `_current-session.md` entries.


## Increment Map
1. **I1 – Hook + formatter documentation** (Owner: Ivan, Status: Completed)  
   - Captured managed hook workflows (gitlint, cache warm/retry) and Palantir formatter policy directly in `spec.md`.  
2. **I2 – Governance command plan** (Owner: Ivan, Status: Completed)  
   - Refreshed plan/tasks to enumerate hook guard, gitlint, analysis gate, and `spotlessApply` commands, logging destinations (`_current-session.md`, session log).  
3. **I3 – Directory cleanup + audit log** (Owner: Ivan, Status: Completed on 2025-11-11)  
   - Confirmed governance docs were synced and captured the directory cleanup plus verification commands in `_current-session.md`.  
4. **I4 – Governance artefact sync** (Owner: Ivan, Status: Completed on 2025-11-11)  
   - Synced AGENTS/runbooks/constitution/analysis-gate docs with the consolidated scope, logged the hook guard + pre-commit dry-run + quality gate commands, and filed remaining automation backlog items.
5. **I5 – Commit message semicolon ban + assistant Git handoff protocol** (Owner: Ivan, Status: Completed on 2025-12-12)  
   - Added semicolon rejection to managed hooks, required multi-`-m` flags for multi-line commit bodies, and updated AGENTS/runbooks to always present Git commands in fenced code blocks.
6. **I6 – Dependency lock refresh runbook** (Owner: Ivan, Status: Completed on 2025-12-12)  
   - Documented `--write-locks` usage for dependency changes (PMD aux classpath lock drift) in AGENTS and the session quick reference.
7. **I7 – Hook guard logging helper** (Owner: Ivan, Status: Completed on 2025-12-13)  
   - Added `tools/hook-guard-log.sh` to run a verification command and append `git config core.hooksPath` + PASS/FAIL into `docs/_current-session.md`.
8. **I8 – Docs-only verification lane** (Owner: Ivan, Status: Completed on 2025-12-13)  
   - Added `tools/docs-verify.sh` (Spotless misc + markdown line-wrap + link checks) and documented it as the fast loop for doc-only increments.
9. **I9 – Agent Delivery Optimization (ADO) closeout protocol** (Owner: Ivan, Status: Completed on 2025-12-13)  
   - Documented the ADO note (max 5 bullets, action-only) in AGENTS and the session quick reference, and added a reminder to `tools/codex-commit-review.sh` so commit handoffs include it.

_Verification commands:_ `git config core.hooksPath`, `tmp_index=$(mktemp); GIT_INDEX_FILE=$tmp_index ./githooks/pre-commit`, `./gradlew --no-daemon spotlessApply check`, `./gradlew --no-daemon qualityGate`, and manual [githooks/commit-msg](githooks/commit-msg) dry runs when policies change.

## Scenario Tracking
| Scenario | Description | Increment |
|----------|-------------|-----------|
| S-011-01 | Governance docs reference Feature 011 across AGENTS/runbooks/constitution. | P3-I4 |
| S-011-02 | [githooks/commit-msg](githooks/commit-msg) + CI gitlint enforce [.gitlint](.gitlint). | P3-I2 |
| S-011-03 | [githooks/pre-commit](githooks/pre-commit) handles cache warm/retry and staged checks. | P3-I2 |
| S-011-04 | Hook guard (`git config core.hooksPath githooks`) logged per session. | P3-I2 |
| S-011-05 | Analysis gate checklist linked/logged. | P3-I2/P3-I4 |
| S-011-06 | Palantir formatter pin + lock refresh + IDE guidance captured. | P3-I1 |
| S-011-07 | Hooks/docs mention Palantir policy + `spotlessApply` command expectations. | P3-I2 |
| S-011-08 | `_current-session.md` + session log ([docs/_current-session.md](docs/_current-session.md)) capture each governance increment. | P3-I3/P3-I4 |
| S-011-09 | Commit messages forbid semicolons and assistants use multi-`-m` and fenced Git commands when handing off commits. | P3-I5 |
| S-011-10 | Dependency changes refresh Gradle lockfiles early via documented `--write-locks` workflow. | P3-I6 |
| S-011-11 | Assistants include an Agent Delivery Optimization (ADO) note in commit handoffs. | P3-I9 |

## Analysis Gate
Run [docs/5-operations/analysis-gate-checklist.md](docs/5-operations/analysis-gate-checklist.md) whenever governance artefacts change meaningfully. Record the execution in `_current-session.md` and note any follow-ups in this plan.

- 2025-11-11 – Checklist rerun after cross-doc sync (AGENTS/runbooks/constitution/analysis-gate docs now reference Feature 011).
  - Spec/plan/tasks alignment confirmed; no open questions.
  - Governance commands logged: `git config core.hooksPath`, `tmp_index=$(mktemp); GIT_INDEX_FILE=$tmp_index git read-tree --empty; GIT_INDEX_FILE=$tmp_index ./githooks/pre-commit`, `./gradlew --no-daemon spotlessApply check`, `./gradlew --no-daemon qualityGate`.
  - `_current-session.md` captures the run plus the initial malformed `spotlessApply check,workdir:` invocation that was rerun without the typo.

## Exit Criteria
- `spec.md`/`plan.md`/`tasks.md` describe hook workflows, gitlint policy, Palantir formatter pin, and verification commands.
- Governance artefacts (AGENTS, runbooks, constitution, session log ([docs/_current-session.md](docs/_current-session.md)), `_current-session.md`) reference Feature 011 and list
  hook/analysis-gate expectations.
- Spotless/quality gates rerun and logged when required by governance updates.

## Follow-ups / Backlog
- Track gitlint rule tuning, markdown lint adoption (with Feature 013 coordination), and governance automation backlog under Feature 013 as the next governance cycle begins.
- Consider documenting timing metrics for [githooks/pre-commit](githooks/pre-commit) and [githooks/commit-msg](githooks/commit-msg) runs in `_current-session.md` to
  monitor ergonomics.
- During the Phase 2 verification gate rerun, capture a fresh hook guard + managed hook runtime snapshot so we have a baseline before enabling any markdown lint or gitlint rule changes.
- Coordinate with Feature 010’s LLM/assistant-facing documentation work (FR-010-13) so [AGENTS.md](AGENTS.md) remains the canonical onboarding surface for human contributors and AI agents, reflecting module roles, Native Java entry points, and governance guardrails without diverging from this spec.

$chunk
