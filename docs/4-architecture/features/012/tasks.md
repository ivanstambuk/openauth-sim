# Feature 012 Tasks – Maintenance CLI Coverage Buffer

_Status:_ Complete  
_Last updated:_ 2025-11-10_

> Checklist mirrors the Feature 012 plan increments; tasks stay checked for audit history while migrating templates. Reference FR/NFR/S-012 IDs to keep traceability clear.

## Checklist
- [x] T-012-01 – Refresh Jacoco aggregated coverage for Maintenance CLI (FR-012-01, S-012-01).  
  _Intent:_ Capture the latest line/branch metrics that justify the coverage buffer.  
  _Verification commands:_  
  - `./gradlew --no-daemon jacocoAggregatedReport`  
  _Notes:_ Metrics recorded in `docs/4-architecture/features/012/plan.md`.

- [x] T-012-02 – Review Jacoco HTML to locate hotspots (FR-012-02, S-012-02).  
  _Intent:_ Identify under-covered branches/functions for follow-up tests.  
  _Verification commands:_  
  - `python -m webbrowser build/reports/jacoco/aggregated/html/index.html`

- [x] T-012-03 – Map hotspots to recommended regression scenarios (FR-012-03, S-012-02).  
  _Intent:_ Produce actionable test ideas with file references and priorities.  
  _Verification commands:_  
  - `rg -n "hotspot" docs/4-architecture/features/012/plan.md`

- [x] T-012-04 – Publish hotspot analysis and sync roadmap/session notes (FR-012-01–FR-012-03, NFR-012-01, S-012-01, S-012-02).  
  _Intent:_ Keep documentation aligned with the coverage evidence.  
  _Verification commands:_  
  - `rg -n "Maintenance CLI" docs/4-architecture/features/012/plan.md`

- [x] T-012-05 – Implement forked JVM failure-path test covering `MaintenanceCli.main` (FR-012-04, S-012-03).  
  _Intent:_ Exercise the System.exit branch without killing the test JVM.  
  _Verification commands:_  
  - `./gradlew --no-daemon :cli:test --tests "*MaintenanceCli*"`

- [x] T-012-06 – Implement corrupted database verification test (FR-012-05, S-012-04).  
  _Intent:_ Trigger the maintenance catch block and assert exit code 1 + error message.  
  _Verification commands:_  
  - `./gradlew --no-daemon :cli:test --tests "*MaintenanceCli*"`

- [x] T-012-07 – Add supplementary branch coverage (parent-null path, short help, blank params) (FR-012-06, S-012-05).  
  _Intent:_ Guard supporting branches to preserve the ≥0.90 buffer.  
  _Verification commands:_  
  - `./gradlew --no-daemon :cli:test --tests "*MaintenanceCli*"`

- [x] T-012-08 – Cover legacy issue-listing branch via seeded MapDB fixture (FR-012-06, S-012-05).  
  _Intent:_ Ensure WARN/FAIL issue output remains exercised.  
  _Verification commands:_  
  - `./gradlew --no-daemon :cli:test --tests "*MaintenanceCli*"`

## Verification Log (Optional)
- 2025-10-01 – `./gradlew --no-daemon jacocoAggregatedReport` (PASS; line 97.56%, branch 93.30 for `io.openauth.sim.cli`).
- 2025-10-01 – `./gradlew --no-daemon :cli:test --tests "*MaintenanceCli*"` (PASS; forked JVM + corrupted DB + supplementary branch coverage). 
- 2025-10-01 – `./gradlew --no-daemon spotlessApply check` (PASS; documentation updates only).

## Notes / TODOs
- Coverage thresholds temporarily relaxed to 0.70; roadmap Workstream 19 tracks restoring 0.90 once HOTP scope stabilises. Accepted gap: defensive `parsed == null` branch remains uncovered by design.
