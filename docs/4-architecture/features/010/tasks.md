# Feature 010 Tasks – Documentation & Knowledge Automation

_Status:_ In migration (Batch P3)  
_Last updated:_ 2025-11-11

> Keep this checklist aligned with the feature plan increments. Stage tests before implementation, record verification commands beside each task, and prefer bite-sized entries (≤90 minutes).
> When referencing requirements, keep feature IDs (`F-`), non-goal IDs (`N-`), and scenario IDs (`S-<NNN>-`) inside the same parentheses immediately after the task title (omit categories that do not apply).

## Checklist
- [x] T-010-01 – Consolidate the operator documentation + quality automation requirements into the refreshed spec (FR-010-01..10, NFR-010-01..05).
  - _Intent:_ Absorb legacy Features 007/008 into the new spec template so Feature 010 is the single source of truth.
  - _Verification commands:_ `git diff --stat docs/4-architecture/features/010/spec.md`, `rg "FR-010" docs/4-architecture/features/010/spec.md`.
  - _Notes:_ Spec now documents doc bundle paths, README expectations, and `qualityGate` automation.
- [x] T-010-02 – Refresh the plan/tasks to match the consolidated scope and scenario tracking.
  - _Intent:_ Align increments, scenario map, and checklist with the new requirements + tooling gates.
  - _Verification commands:_ `rg "P3-I" docs/4-architecture/features/010/plan.md`, `rg "T-010" docs/4-architecture/features/010/tasks.md`.
  - _Notes:_ Plan now lists P3-I1..P3-I4 and references `spotlessApply` + `qualityGate` commands.
- [x] T-010-03 – Remove `docs/4-architecture/features/010/legacy/` after verifying the migration, then log `rm -rf ...` + `ls` output inside `_current-session.md`.
  - _Intent:_ Finish the legacy absorption and keep the deletion auditable.
  - _Verification commands:_ `rm -rf docs/4-architecture/features/010/legacy`, `ls docs/4-architecture/features/010`, append log to `_current-session.md`.
  - _Notes:_ Session snapshot must reference the removal in the Batch P3 Phase 2 section.
- [x] T-010-04 – Update `_current-session.md` with the Feature 010 rewrite summary + verification commands (Batch P3 Phase 2).
  - _Intent:_ Preserve the audit trail for doc/automation changes.
  - _Verification commands:_ `rg "Batch P3" docs/_current-session.md`, `./gradlew --no-daemon spotlessApply check` (doc gate) once entries land.
  - _Notes:_ Include command list (spec rewrite, legacy deletion, spotless/quality gate status) in the session snapshot.
- [x] T-010-05 – Stage the final Phase 2 gate (pending Features 011–013) by documenting the required commands.
  - _Intent:_ Remind future agents to rerun `./gradlew --no-daemon spotlessApply check` and `./gradlew --no-daemon qualityGate` when Batch P3 rewrites conclude.
  - _Verification commands:_ Documentation updates + references in plan/tasks/session log (2025-11-11) enumerating the gate commands and runtime expectations (<5 min for spotless, <10 min for qualityGate).
  - _Notes:_ `_current-session.md` now mentions the pending gate; rerun the commands once Features 011–013 complete.

## Verification Log
- 2025-11-11 – `./gradlew --no-daemon spotlessApply check` (doc gate – Feature 010 legacy absorption, 24 s, configuration cache stored).
- 2025-11-11 – Phase 2 gate commands documented (`./gradlew --no-daemon spotlessApply check`, `./gradlew --no-daemon qualityGate`) for execution once the remaining features ship.

## Notes / TODOs
- Capture knowledge-map regeneration steps when automation scripting begins.
- Evaluate adding Markdown lint to the managed hook after Batch P3 closes.
- Legacy Coverage – T-010-L1 (Feature 007 operator guides + README cross-links). _Intent:_ Confirm FR-010-01/02 and NFR-010-01 fully describe the Java/CLI/REST guides, runnable snippets, telemetry notes, and troubleshooting flows migrated from Feature 007. _Verification commands:_ `./gradlew --no-daemon spotlessApply check`, manual validation of the `docs/2-how-to/*` procedures before publishing. _Notes:_ `_current-session.md` (2025-11-11) records the documentation sweep and spotless run that proved the migration.
- Legacy Coverage – T-010-L2 (Feature 008 quality automation charter). _Intent:_ Ensure FR-010-04..09 and NFR-010-02/03/05 capture ArchUnit/Jacoco/PIT aggregation, qualityGate triggers, troubleshooting docs, and reporting folders previously tracked in Feature 008. _Verification commands:_ `./gradlew --no-daemon qualityGate`, `./gradlew --no-daemon spotlessApply check`, inspection of `build/reports/{jacoco,pitest,quality}/`. _Notes:_ Plan increment P3-I2 plus `_current-session.md` log the command output and follow-ups.
