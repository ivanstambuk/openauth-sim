# Feature 010 – Documentation & Knowledge Automation Plan

| Field | Value |
|-------|-------|
| Status | In migration (Batch P3) |
| Last updated | 2025-11-11 |
| Owners | Ivan (project owner) |
| Specification | `docs/4-architecture/features/010/spec.md` |
| Tasks checklist | `docs/4-architecture/features/010/tasks.md` |
| Roadmap entry | #10 – Documentation & Knowledge Automation |

## Vision
Centralise every operator-facing guide, roadmap/knowledge-map reference, and quality-automation workflow under one feature so
all documentation and the aggregated `qualityGate` evolve together. Success means the Java/CLI/REST guides, README, roadmap,
knowledge map, `_current-session.md`, session log (docs/_current-session.md), and GitHub Actions workflow share a single authoritative spec/plan/tasks
set with deterministic verification commands.

## Scope
- Merge legacy Features 007/008 (operator documentation suite + quality automation) into Feature 010’s spec/plan/tasks.
- Keep roadmap, knowledge map, architecture graph, session log (docs/_current-session.md), and session quick reference aligned with documentation and
  automation changes.
- Maintain the aggregated `qualityGate` task plus its CI workflow, report locations, skip flags, and troubleshooting guides.
- Log every documentation/automation increment inside `_current-session.md` and `docs/migration_plan.md` with command history.

_Out of scope:_ shipping runtime simulator changes, expanding the quality gate to non-OCRA modules, or introducing new
publishing tooling.

## Dependencies
| Dependency | Notes |
|------------|-------|
| Docs templates (`docs/templates/*.md`) | Govern structure for specs/plans/tasks and operator guides. |
| Operator guides (`docs/2-how-to/*.md`) + README | Must reflect current simulator behaviour and telemetry expectations. |
| Roadmap / knowledge map / session log (docs/_current-session.md) / `_current-session.md` | Need synchronized references and command logs per increment. |
| Gradle build logic (`qualityGate` task, Spotless, ArchUnit, Jacoco, PIT, SpotBugs, Checkstyle, gitleaks) | Provide the automation enforced by this feature. |
| GitHub Actions workflow (`.github/workflows/quality-gate.yml`) | Mirrors local gate execution and uploads reports. |

## Legacy Integration Tracker
| Legacy Feature(s) | Increment(s) | Status | Notes |
|-------------------|--------------|--------|-------|
| 007 | P3-I1 (spec), P3-I2 (plan/tasks) | Completed | FR-010-01/02 and NFR-010-01 now describe the operator how-to guides, snippets, and troubleshooting flows migrated from the legacy documentation feature. |
| 008 | P3-I2 | Completed | FR-010-04..09 and NFR-010-02/03/05 cover the automation charter (ArchUnit/Jacoco/PIT/qualityGate) and doc logging expectations previously maintained by Feature 008. |

## Assumptions & Risks
- Documentation remains Markdown/ASCII; deviations require template updates.
- Developers run `./gradlew --no-daemon spotlessApply check` for every doc change and `./gradlew --no-daemon qualityGate` when automations change.
- Risk: forgetting to log commands in `_current-session.md` or session log (docs/_current-session.md) reduces auditability—mitigate via checklist items.
- Risk: PIT/Jacoco runtimes can exceed NFR limits if cache hints regress—monitor timings inside tasks/plan notes.

## Increment Map (≤90 min each)
| Increment | Intent | Owner | Status | Notes |
|-----------|--------|-------|--------|-------|
| P3-I1 | Absorb the legacy operator documentation suite (Java/CLI/REST guides + README cross-links) into the consolidated spec. | Ivan | Completed | FR-010-01/02 captured from legacy Feature 007; spec now references guide paths and telemetry expectations. |
| P3-I2 | Capture the quality automation charter (ArchUnit, Jacoco, PIT, aggregated `qualityGate`, CI workflow, troubleshooting docs). | Ivan | Completed | FR-010-04..09 + NFR entries incorporated from legacy Feature 008. |
| P3-I3 | Remove `docs/4-architecture/features/010/legacy/{007,008}` after verifying the spec/plan/tasks contain the migrated content; log the deletion + command output in `_current-session.md` and `docs/migration_plan.md`. | Ivan | Completed | `rm -rf docs/4-architecture/features/010/legacy` executed and recorded; pending docs capture in session log (docs/_current-session.md)/session snapshot. |
| P3-I4 | Record the Phase 2 summary (Feature 009–013) in `docs/migration_plan.md`, rerun `./gradlew --no-daemon spotlessApply check` (doc gate), and queue the final `qualityGate` run once Features 011–013 finish their rewrites. | Ivan | Pending | Requires remaining Batch P3 features to absorb their legacy content before executing the gate. |

## Scenario Tracking
| Scenario | Description | Increment |
|----------|-------------|-----------|
| S-010-01 | Operator guides remain runnable with telemetry and troubleshooting coverage. | P3-I1 |
| S-010-02 | README/doc landing pages point to shipped capabilities only. | P3-I1 |
| S-010-03 | Roadmap/knowledge map/session log (docs/_current-session.md)/session quick reference stay synchronized and log commands. | P3-I1–P3-I4 |
| S-010-04 | `qualityGate` aggregates Spotless, Checkstyle, SpotBugs, ArchUnit, Jacoco, PIT, gitleaks. | P3-I2 |
| S-010-05 | ArchUnit rules enforce module boundaries. | P3-I2 |
| S-010-06 | Jacoco aggregated thresholds remain ≥90% line/branch. | P3-I2 |
| S-010-07 | PIT mutation score stays ≥85% with documented skip flag. | P3-I2 |
| S-010-08 | GitHub Actions workflow mirrors the gate and uploads reports. | P3-I2 |
| S-010-09 | Docs/runbooks explain how to run/remediate the gate. | P3-I2 |
| S-010-10 | `_current-session.md` + session log (docs/_current-session.md) log every documentation/automation increment. | P3-I3 |

## Legacy Parity Review
- 2025-11-11 – Compared Feature 010 FR/NFR/scenario coverage with legacy Features 007/008. Documentation, roadmap automation, and quality-gate requirements are fully represented; remaining enhancements (knowledge-map regeneration scripting, markdown lint adoption) stay in the backlog.

## Quality & Tooling Gates
- `./gradlew --no-daemon spotlessApply check` – required after every documentation/spec/plan/task edit.
- `./gradlew --no-daemon qualityGate [-Ppit.skip=true]` – aggregated automation gate (ArchUnit, Jacoco, PIT, Spotless, SpotBugs, Checkstyle, gitleaks).
- Optional targeted searches: `rg "Feature 010" docs/` to confirm cross-document references.

## Analysis Gate
Run `docs/5-operations/analysis-gate-checklist.md` after P3-I3 completes (legacy tree removed + docs synced). Ensure roadmap,
knowledge map, session log (docs/_current-session.md), and `_current-session.md` match the updated spec/plan/tasks before moving to implementation work.

## Implementation Drift Gate
Once Batch P3 Phase 2 ends (Features 009–013 absorbed), cross-check FR-010-01..FR-010-10 and NFR-010-01..05 against the operator
guides, README, roadmap/knowledge map entries, `_current-session.md` logs, and `qualityGate` configuration/report folders. Log the
drift report inside this plan with links to verification commands.

## Exit Criteria
- Feature 010 spec/plan/tasks fully describe the operator doc suite and quality automation guardrails (no `legacy/` references).
- `docs/4-architecture/features/010/legacy/` removed and the deletion logged in `_current-session.md` + `docs/migration_plan.md`.
- Roadmap, knowledge map, architecture graph, session log (docs/_current-session.md), session quick reference, and `_current-session.md` cite Feature 010 for documentation and automation work.
- `./gradlew --no-daemon spotlessApply check` recorded after the migration; final `qualityGate` queued once Features 011–013 integrate.

## Follow-ups / Backlog
- Script knowledge-map regeneration to reduce manual edits (capture timing + instructions in Feature 010 tasks once designed).
- Evaluate adding Markdown lint to the managed hook after Batch P3 verification.
- Expand `qualityGate` coverage beyond the OCRA stack in a future feature once this migration stabilises.
