# Feature 009 Tasks – Operator Console Infrastructure

_Status:_ In migration (Batch P3)
_Last updated:_ 2025-11-11

## Checklist (Batch P3 Phase 2)
- [x] T-009-01 – Consolidate the functional/non-functional/verification content from legacy Features 017/020/021/025/033–038/041 into the refreshed spec template.
  - _Intent:_ Make Feature 009 the authoritative console spec and capture every interface/domain requirement from the migrated features.
  - _Verification commands:_ Review `spec.md` diff for new sections (FRs, scenarios, DSL, interface catalogue). Auto-format docs with `spotless` if needed.
  - _Notes:_ `spec.md` now lists FR-009-01..FR-009-10, updated scenarios, and interface tables covering tabs, info drawer, trace tiers, Base32, preview windows, and JS harness.
- [x] T-009-02 – Update the plan/tasks/checklist to describe the new scope, scenario tracking, tooling gates, and docs/log requirements.
  - _Intent:_ Align planning artefacts with the consolidated console scope so reviewers see the same story across plan/tasks.
  - _Verification commands:_ `git diff --stat docs/4-architecture/features/009/{plan,tasks}.md` (2025-11-11) to confirm FR/NFR, scenario, and quality sections match the consolidated spec.
  - _Notes:_ Plan/tasks now document the Node harness commands, verbose trace gates, and documentation/logging expectations; legacy directory references were removed during the review.
- [x] T-009-03 – Delete the now-empty staging folders from the operator-console migration and log the commands.
  - _Intent:_ Remove redundant directories while keeping the migration auditable.
  - _Verification commands:_ `rm -rf docs/4-architecture/features/operator-console docs/4-architecture/features/docs-and-quality docs/4-architecture/features/platform-foundations` (2025-11-11), followed by `./gradlew --no-daemon spotlessApply check`.
  - _Notes:_ Commands/output recorded in `_current-session.md` and `docs/migration_plan.md` (Batch P3 Phase 2 legacy cleanup entry).
- [x] T-009-04 – Capture the Phase 2 gate reminder (Feature 010–013 rewrites + `spotlessApply check`/`qualityGate`) in the plan, tasks, and migration log.
  - _Intent:_ Keep the session log (docs/_current-session.md)/`_current-session.md` aware that the final Gradle gate awaits the remaining feature rewrites.
  - _Verification commands:_ `rg "Phase 2" docs/4-architecture/features/009/plan.md` (2025-11-11) confirms the reminder plus command list.
  - _Notes:_ Batch P3 gate commands are documented in the plan and migration log; rerun them once the remaining features finish.

## Verification Log
- 2025-11-11 – `./gradlew --no-daemon spotlessApply check` (doc migration gate)
- 2025-11-11 – `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test` (console-focused suites)
- 2025-11-11 – `node --test rest-api/src/test/javascript/emv/console.test.js`
- 2025-11-11 – `./gradlew --no-daemon pmdMain pmdTest`

### Legacy Coverage Checklist
- [x] T-009-L1 – Features 017/020/021/025/033 (tabs, presets, Base32 helpers).
  - _Intent:_ Confirm FR-009-01..03 and NFR-009-04 cover the unified tab shell, history routing, preset labels, and Base32 toggles inherited from these features.
  - _Verification commands:_ `./gradlew --no-daemon :ui:test`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.*OperatorUiSeleniumTest"`, `./gradlew --no-daemon :application:test --tests "*VerboseTrace*"`.
  - _Notes:_ Scenario S-009-01/S-009-03 plus `_current-session.md` (2025-11-11) record the verification output.
- [x] T-009-L2 – Features 034/035/036/037/038 (info drawer, verbose traces, validation helpers).
  - _Intent:_ Keep FR-009-04..08 and NFR-009-02 synchronized with the instrumentation/spec DSL migrated from these features.
  - _Verification commands:_ `./gradlew --no-daemon :application:test --tests "*TraceDock*"`, `./gradlew --no-daemon :cli:test --tests "*VerboseTrace*"`, `node --test rest-api/src/test/javascript/emv/console.test.js`.
  - _Notes:_ Plan increments P3-I1/P3-I2 reference these IDs; `_current-session.md` stores the command log.
- [x] T-009-L3 – Feature 041 (operator console JS harness + preview windows).
  - _Intent:_ Ensure FR-009-09 and NFR-009-05 encapsulate the harness, preview panes, and doc-sync requirements so the old scripts stay retired.
  - _Verification commands:_ `node --test rest-api/src/test/javascript/emv/console.test.js`, `./gradlew --no-daemon pmdMain pmdTest`, `./gradlew --no-daemon :rest-api:test --tests "*OperatorUiSeleniumTest"`.
  - _Notes:_ Harness deletion + verification output logged with the Batch P3 Phase 2 migration summary on 2025-11-11.

## Notes / TODOs
- Document Phase 2 status (Feature 009 spec rewrite complete but verification gate pending) inside `docs/migration_plan.md` and `_current-session.md` before moving to Features 010–013.
- Keep the knowledge map/roadmap aligned with Feature 009 ownership; update references when upstream features change scope.
