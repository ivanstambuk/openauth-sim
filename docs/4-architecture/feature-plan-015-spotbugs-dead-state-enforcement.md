# Feature Plan 015 – SpotBugs Dead-State Enforcement

_Status: Complete_
_Last updated: 2025-10-03_

## Objective
Activate SpotBugs dead-state detectors (unread/unwritten/unused fields) across all JVM modules so latent state fails the quality gate. Work includes wiring a shared include filter, remediation of current violations, and updating runbooks to reflect the stricter guardrail.

Reference specification: `docs/4-architecture/specs/feature-015-spotbugs-dead-state-enforcement.md`.

## Success Criteria
- SpotBugs include filter covers the approved detector list and is consumed by every `spotbugs*` task (SB-001).
- Baseline violations (e.g., unused `Clock` field) are cleaned or justified, allowing `./gradlew spotlessApply check` to pass with the filter active (SB-002).
- Quality documentation (analysis gate + tooling guide) explains the new detectors and suppression etiquette (SB-003).

## Clarifications
- 2025-10-03 – Detectors limited to `URF_UNREAD_FIELD`, `URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD`, `UUF_UNUSED_FIELD`, `UWF_UNWRITTEN_FIELD`, and `NP_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD`; additional patterns deferred.
- 2025-10-03 – Suppressions require `@SuppressFBWarnings` with justification and must be logged in this plan’s Notes section when added.

## Proposed Increments
- R1501 – Register Feature 015 across roadmap/tasks; confirm no open questions. ☑ (2025-10-03 – Added roadmap row 13, knowledge map link, tasks/spec synced, open questions verified empty)
- R1502 – Add shared SpotBugs include filter with agreed bug patterns; wire Gradle tasks to consume it and verify failing run captures current violations. ☑ (2025-10-03 – Introduced `config/spotbugs/dead-state-include.xml`, configured SpotBugs tasks, command failed on `URF_UNREAD_FIELD` as expected)
- R1503 – Remediate surfaced findings (unused/unwritten fields) or justify suppressions; rerun `spotlessApply check`. ☑ (2025-10-03 – Removed unused `Clock` dependency from verification service + call sites; SpotBugs and `spotlessApply check` green)
- R1504 – Update documentation (analysis gate checklist + tooling guide) to include the new detectors and remediation guidance; sync knowledge map if necessary. ☑ (2025-10-03 – Documented detector list in analysis gate checklist and quality gate guide; reran `spotlessApply check` after edits)
- R1505 – Record final command outputs and note detector adoption in plan Notes; ensure feature artefacts (spec/plan/tasks) reflect completion. ☑ (2025-10-03 – Notes capture failing/passing SpotBugs + check runs; spec/plan/tasks aligned)
- R1506 – Add PMD `UnusedPrivateField`, remediate violations (e.g., `PIN_SUITE`), and rerun `pmdMain`/`pmdTest`. ☑ (2025-10-03 – Added rule to PMD ruleset, removed unused `PIN_SUITE`, `:rest-api:pmdTest` passes)
- R1507 – Enable PMD `UnusedPrivateMethod`, clean up unused helpers (e.g., `inlineCredential()`), rerun `pmdMain`/`pmdTest`. ☑ (2025-10-03 – Rule active, no violations after cleanup; reran `:rest-api:pmdTest` and root `check`.)

Each increment should take ≤10 minutes with tests preceding implementation where applicable.

## Checklist Before Implementation
- [x] Specification drafted with clarifications.
- [x] Open questions recorded/resolved (`docs/4-architecture/open-questions.md`).
- [x] Tasks list created with tests-first ordering.
- [x] Analysis gate rerun once plan/tasks synced.

### Tooling Readiness
- `./gradlew :application:spotbugsMain --rerun-tasks` – verify detectors trigger and later confirm green status.
- `./gradlew spotlessApply check` – assure aggregate quality gate remains green post-remediation.

## Notes
Use this section to log detector findings, suppressions (with rationale), and runtime observations (e.g., delta in SpotBugs execution time) as work proceeds.
- 2025-10-03 – `./gradlew :application:spotbugsMain --rerun-tasks` failed with `URF_UNREAD_FIELD` on `OcraVerificationApplicationService.clock`, confirming detector activation; rerun passed after removing the unused field.
- 2025-10-03 – Full `./gradlew spotlessApply check` succeeded (≈3m16s) with no additional dead-state findings; SpotBugs warnings limited to existing serialVersionUID notices.
- 2025-10-03 – Added PMD `UnusedPrivateField` (bestpractices); initial run flagged `PIN_SUITE` in REST test, removed constant, reran `:rest-api:pmdTest` (pass).
- 2025-10-03 – Verified PMD `UnusedPrivateMethod` is active; `./gradlew :rest-api:pmdTest` and `./gradlew check` both green with rule enforced.

## Analysis Gate (2025-10-03)
- Specification completeness – PASS: Feature 015 spec defines objectives, requirements, clarifications (2025-10-03).
- Open questions review – PASS: `docs/4-architecture/open-questions.md` has no entries for this feature.
- Plan alignment – PASS: Plan references Feature 015 spec/tasks; success criteria map to SB-001–SB-003.
- Tasks coverage – PASS: Tasks T1501–T1505 align with requirements and sequence validation commands ahead of implementation steps.
- Constitution compliance – PASS: Work remains within existing tooling; no dependency changes planned.
- Tooling readiness – PASS: Plan documents SpotBugs and `spotlessApply check` commands for validation.
