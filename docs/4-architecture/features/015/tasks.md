# Feature 015 Tasks – SpotBugs Dead-State Enforcement

_Status:_ Complete  
_Last updated:_ 2025-11-10_

> Checklist mirrors the Feature 015 plan increments; boxes remain checked for audit history while migrating templates.

## Checklist
- [x] T-015-01 – Register Feature 015 in roadmap/knowledge map, confirm `open-questions.md` stays empty (FR-015-01, FR-015-03, S-015-01, S-015-03).  
  _Intent:_ Anchor the scope before wiring detectors.  
  _Verification commands:_  
  - `rg -n "Feature 015" docs/4-architecture/roadmap.md`  
  - `rg -n "Feature 015" docs/4-architecture/knowledge-map.md`

- [x] T-015-02 – Add shared SpotBugs include filter + Gradle wiring; capture the expected failing run (FR-015-01, FR-015-02, S-015-01).  
  _Intent:_ Enable dead-state detectors across all JVM modules and observe the initial failure.  
  _Verification commands:_  
  - `./gradlew --no-daemon :application:spotbugsMain --rerun-tasks`

- [x] T-015-03 – Remediate dead-state findings and rerun SpotBugs + `spotlessApply check` (FR-015-02, S-015-02).  
  _Intent:_ Remove or justify unread/unwritten fields so detectors pass.  
  _Verification commands:_  
  - `./gradlew --no-daemon :application:spotbugsMain --rerun-tasks`  
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-015-04 – Update documentation (analysis gate checklist + tooling guide) describing the detectors/suppression policy (FR-015-03, S-015-03).  
  _Intent:_ Keep governance artefacts aligned with the new enforcement.  
  _Verification commands:_  
  - `rg -n "dead-state" docs/5-operations/analysis-gate-checklist.md`  
  - `rg -n "dead-state" docs/3-reference/developer-tooling.md`

- [x] T-015-05 – Log runtime impact and suppression policy in the feature plan, sync spec/plan/tasks (FR-015-03, S-015-03).  
  _Intent:_ Preserve evidence for future auditors.  
  _Verification commands:_  
  - `rg -n "SpotBugs" docs/4-architecture/features/015/plan.md`

- [x] T-015-06 – Enable PMD `UnusedPrivateField`, clean violations, rerun PMD (`pmdMain`/`pmdTest`) (FR-015-04, S-015-04).  
  _Intent:_ Extend the guardrail to private fields across JVM modules.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:pmdTest`  
  - `./gradlew --no-daemon check`

- [x] T-015-07 – Enable PMD `UnusedPrivateMethod`, remove dead helpers, rerun PMD + check (FR-015-04, S-015-04).  
  _Intent:_ Ensure unused private methods also fail the gate.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:pmdTest`  
  - `./gradlew --no-daemon check`

## Verification Log (Optional)
- 2025-10-03 – `./gradlew --no-daemon :application:spotbugsMain --rerun-tasks` (FAIL → PASS after remediation).
- 2025-10-03 – `./gradlew --no-daemon spotlessApply check` (PASS with detectors enabled).
- 2025-10-03 – `./gradlew --no-daemon :rest-api:pmdTest` / `./gradlew --no-daemon check` (PASS with new PMD rules).

## Notes / TODOs
- SpotBugs runtime delta (+4%) recorded in the plan; suppressions must cite justification plus plan reference. Optional detector families remain future scope.
