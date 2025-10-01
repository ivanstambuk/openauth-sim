# Feature 012 – Maintenance CLI Coverage Buffer Tasks

_Status: Draft_
_Last updated: 2025-10-01_

## Tasks
| ID | Task | Related Requirements | Status |
|----|------|----------------------|--------|
| T1201 | Run `./gradlew jacocoAggregatedReport` and record Maintenance CLI coverage metrics (line/branch). | MCLI-COV-001 | ✅ (2025-10-01) |
| T1202 | Review Jacoco HTML for `io.openauth.sim.cli` and note low-coverage branches/functions focused on maintenance paths. | MCLI-COV-001, MCLI-COV-002 | ✅ (2025-10-01) |
| T1203 | Map each hotspot to recommended test scenarios with expected behaviours and target classes. | MCLI-COV-002, MCLI-COV-003 | ✅ (2025-10-01) |
| T1204 | Publish hotspot analysis/report in feature plan notes (or appendix) and update roadmap/open questions. | MCLI-COV-NFR-001, MCLI-COV-NFR-002, MCLI-COV-NFR-003 | ✅ (2025-10-01) |

Mark tasks complete as increments ship; keep tasks aligned with ≤10 minute increments and test-first mindset for follow-up implementation work.
| T1205 | Implement forked JVM failure-path test for `MaintenanceCli.main` ensuring System.exit branch covered. | MCLI-COV-004 | ✅ (2025-10-01) |
| T1206 | Add corrupted database maintenance test covering error catch branch. | MCLI-COV-005 | ✅ (2025-10-01) |
| T1207 | Add supplementary branch coverage tests (parent-null database path, `-h` help flag, blank required parameters). | MCLI-COV-006 | ✅ (2025-10-01) |
| T1208 | Seed legacy MapDB fixture to provoke maintenance issue listing and cover `issue=` printing. | MCLI-COV-005 | ✅ (2025-10-01) |
