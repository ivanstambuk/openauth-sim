# Feature 012 Tasks – Maintenance CLI Coverage Buffer

_Status:_ Complete  
_Last updated:_ 2025-10-31_

## Checklist
- [x] T1201 – Run `jacocoAggregatedReport` and record baseline Maintenance CLI coverage (S12-01).
  _Intent:_ Capture the starting line/branch metrics that justify the coverage buffer.
  _Verification commands:_
  - `./gradlew --no-daemon jacocoAggregatedReport`

- [x] T1202 – Review Jacoco HTML for Maintenance CLI hotspots (S12-02).
  _Intent:_ Identify under-covered branches/functions and annotate them in the plan.
  _Verification commands:_
  - `python -m webbrowser build/reports/jacoco/jacocoAggregatedReport/html/index.html`

- [x] T1203 – Map each hotspot to recommended test scenarios with expected behaviours (S12-02).
  _Intent:_ Produce actionable test ideas that will later translate into scenario IDs and tasks.
  _Verification commands:_
  - `rg -n "MaintenanceCli" docs/4-architecture/features/012/plan.md`

- [x] T1204 – Publish hotspot analysis/report in the feature plan and sync roadmap/open questions (S12-01, S12-02).
  _Intent:_ Keep documentation, roadmap, and questions aligned with the coverage work.
  _Verification commands:_
  - `rg -n "hotspot" docs/4-architecture/features/012/plan.md`

- [x] T1205 – Implement forked JVM failure-path test covering `MaintenanceCli.main` exit codes (S12-03).
  _Intent:_ Ensure the System.exit branch is exercised without destabilising the test JVM.
  _Verification commands:_
  - `./gradlew --no-daemon :cli:test --tests "*MaintenanceCliFailurePathTest"`

- [x] T1206 – Add corrupted database test covering the error catch branch (S12-04).
  _Intent:_ Simulate invalid persistence state and verify exit code 1 plus the expected error message.
  _Verification commands:_
  - `./gradlew --no-daemon :cli:test --tests "*MaintenanceCliCorruptedDatabaseTest"`

- [x] T1207 – Add supplementary branch coverage (parent-null DB path, `-h` flag, blank params) (S12-05).
  _Intent:_ Guard helper branches that protect the ≥0.90 buffer once HOTP scope resumes.
  _Verification commands:_
  - `./gradlew --no-daemon :cli:test --tests "*MaintenanceCli*"`

- [x] T1208 – Seed legacy MapDB fixture to cover issue listing output (S12-04, S12-05).
  _Intent:_ Capture diagnostics produced when legacy records surface so operators keep context.
  _Verification commands:_
  - `./gradlew --no-daemon :cli:test --tests "*LegacyMaintenanceCliTest"`

## Notes / TODOs
- Coverage buffer temporarily relaxed to 0.70; roadmap workstream tracks restoring 0.90 once HOTP scope stabilises.
