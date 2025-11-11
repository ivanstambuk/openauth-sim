# Feature Plan 012 – Maintenance CLI Coverage Buffer

_Linked specification:_ `docs/4-architecture/features/012/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
- Maintain ≥0.90 line/branch Jacoco coverage for Maintenance CLI once the temporary 0.70 relaxation ends.
- Capture hotspot analysis evidence (metrics, risks, recommendations) inside the feature plan for future reference.
- Implement the high-priority regression tests (forked JVM failure path, corrupted DB catch branch, supplementary branches) without touching runtime behaviour.
- Document all reproduction commands so contributors can regenerate the analysis quickly.
- Keep `./gradlew spotlessApply check`, `jacocoAggregatedReport`, and `jacocoCoverageVerification` green throughout the increment.

## Scope Alignment
- **In scope:** Jacoco report refresh, hotspot analysis, regression test implementation for Maintenance CLI, documentation updates (plan, roadmap/session snapshot, migration tracker).
- **Out of scope:** Changes to Maintenance CLI runtime commands/flags, REST/UI modules, Jacoco threshold policy beyond the temporary relaxation, new dependencies.

## Dependencies & Interfaces
- Gradle build tasks (`jacocoAggregatedReport`, `jacocoCoverageVerification`, `:cli:test`).
- Maintenance CLI tests/fixtures under `cli/src/test/java` and `cli/src/test/resources`.
- Documentation artefacts (`docs/4-architecture/features/012/plan.md`, roadmap, session snapshot, migration tracker).
- System property `openauth.sim.persistence.skip-upgrade` for seeding legacy fixtures.

## Assumptions & Risks
- **Assumptions:** Jacoco aggregated report includes the latest CLI changes; forked JVM harness can reuse the JaCoCo agent; temporary 0.70 coverage threshold will be restored to 0.90 via roadmap Workstream 19.
- **Risks / Mitigations:**
  - _Stale coverage data:_ Always rerun `jacocoAggregatedReport` before updating the hotspot report.
  - _Forked JVM flakiness:_ Reuse the JaCoCo agent + deterministic fixtures; document commands for reruns.
  - _Scope creep:_ Keep work limited to tests/docs and note any accepted coverage gaps (e.g., defensive null guard).

## Implementation Drift Gate
- **Trigger:** After T-012-01–T-012-08 completed (2025-10-01).
- **Evidence:** Hotspot table (metrics + recommendations), updated CLI tests covering the failure branches, Jacoco HTML snapshot references, command log in tasks file.
- **Outcome:** Gate confirmed each FR-012 requirement mapped to tests/docs; `./gradlew jacocoAggregatedReport`, `:cli:test`, and `spotlessApply check` all green.

## Increment Map
1. **I1 – Coverage snapshot & analysis setup** _(T-012-01–T-012-03)_  
   - _Goal:_ Refresh Jacoco data, extract Maintenance CLI metrics, and log hotspots.  
   - _Preconditions:_ Spec clarifications complete; roadmap entry active.  
   - _Steps:_ Run coverage, review HTML, annotate plan with metrics and hotspots.  
   - _Commands:_ `./gradlew --no-daemon jacocoAggregatedReport`, browser open for HTML.  
   - _Exit:_ Plan records baseline coverage + hotspot list (S-012-01, S-012-02).
2. **I2 – Publish hotspot report & doc sync** _(T-012-04)_  
   - _Goal:_ Finalise hotspot table and sync roadmap/session/open questions.  
   - _Steps:_ Document findings, update roadmap and open questions (if any).  
   - _Commands:_ `rg -n "hotspot" docs/4-architecture/features/012/plan.md`.  
   - _Exit:_ Report committed; governance logs updated.
3. **I3 – Forked JVM failure-path test** _(T-012-05)_  
   - _Goal:_ Cover `MaintenanceCli.main` failure exit via spawned JVM.  
   - _Steps:_ Write test harness, forward JaCoCo agent, assert exit code/message.  
   - _Commands:_ `./gradlew --no-daemon :cli:test --tests "*MaintenanceCli*"`.  
   - _Exit:_ Scenario S-012-03 satisfied; coverage report reflects branch coverage.
4. **I4 – Corrupted database catch-path test** _(T-012-06)_  
   - _Goal:_ Trigger maintenance catch branch via invalid store.  
   - _Steps:_ Create corrupt fixture, assert exit code 1 + error messaging.  
   - _Commands:_ `./gradlew --no-daemon :cli:test --tests "*MaintenanceCli*"`.  
   - _Exit:_ Scenario S-012-04 satisfied.
5. **I5 – Supplementary branch coverage & legacy issues** _(T-012-07–T-012-08)_  
   - _Goal:_ Cover parent-null, `-h`, blank params, and legacy issue listing.  
   - _Steps:_ Add targeted tests + fixtures, note accepted gaps.  
   - _Commands:_ `./gradlew --no-daemon :cli:test --tests "*MaintenanceCli*"`.  
   - _Exit:_ Scenario S-012-05 satisfied; plan documents any remaining defensive guard.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-012-01 | I1 / T-012-01 | Coverage snapshot recorded in plan. |
| S-012-02 | I1–I2 / T-012-02–T-012-04 | Hotspot analysis + recommendations. |
| S-012-03 | I3 / T-012-05 | Forked JVM failure-path coverage. |
| S-012-04 | I4 / T-012-06 | Corrupted DB catch-path coverage. |
| S-012-05 | I5 / T-012-07–T-012-08 | Supplementary branch coverage + legacy issue listing. |

## Analysis Gate (2025-10-01)
- ✅ Specification finalised with goals, requirements, clarifications.
- ✅ Open questions cleared (temporary coverage relaxation captured in roadmap).
- ✅ Plan/tasks enumerated ≤30 min increments with tests-first ordering.
- ✅ Tooling readiness documented (`jacocoAggregatedReport`, `:cli:test`, `spotlessApply check`).
- ✅ Governance artefacts (roadmap, session log) noted for updates.

## Exit Criteria
- Plan stores up-to-date coverage metrics and hotspot recommendations.
- CLI regression tests covering failure and supplementary branches pass via `./gradlew :cli:test`.
- `./gradlew jacocoAggregatedReport` and `jacocoCoverageVerification` remain green.
- Documentation (plan, session log, migration tracker) reflects the analysis and implemented tests.
- `./gradlew spotlessApply check` succeeds after documentation updates.

## Follow-ups / Backlog
- Restore aggregated thresholds to ≥0.90 line/branch per roadmap Workstream 19 once HOTP scope settles.
- Monitor defensive `parsed == null` guard; consider refactoring to `Optional` if future increments justify it.
- Re-run hotspot analysis after major CLI feature additions to ensure coverage buffer remains healthy.
