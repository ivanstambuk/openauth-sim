# Feature Plan 012 – Maintenance CLI Coverage Buffer

_Status:_ Complete  
_Last updated:_ 2025-10-31

## Objective
Produce an actionable hotspot analysis for Maintenance CLI coverage, ensuring the aggregated Jacoco buffer stays above 0.90 line/branch while identifying recommended tests for upcoming work. This increment stops short of code changes and establishes the evidence base for follow-up implementation.

Reference specification: `docs/4-architecture/features/012/spec.md`.

## Success Criteria
- Latest Jacoco aggregated report data for Maintenance CLI captured and referenced.
- Hotspots documented with coverage figures, file references, and risk summaries.
- Recommended test additions enumerated with expected behaviours and target classes, then implemented to lift coverage buffers.
- Maintenance CLI coverage tests implemented alongside documentation updates; build remains green.

## Proposed Increments
- C1201 – Refresh Jacoco aggregated coverage data and extract Maintenance CLI metrics (≥0.90 line/branch buffer confirmation).
- C1202 – Inspect Maintenance CLI coverage reports to locate untested or low-buffer branches (e.g., failure paths) and capture notes.
- C1203 – Compile hotspot analysis with prioritized test recommendations and supporting evidence.
- C1204 – Sync project docs (plan notes, roadmap action item, open questions) with analysis outcomes.
- C1205 – Implement forked-JVM test for `MaintenanceCli.main` failure exit path and confirm coverage lift.
- C1206 – Implement corrupted database verification test covering maintenance failure branches.
- C1207 – Implement supplementary branch coverage tests (parent-null path, OCRA short help, blank required parameters).

Document actual completion dates inline once each increment finishes; ensure every increment stays within a ≤30 minute window.

## Checklist Before Implementation
- [x] Specification clarifications captured (2025-10-01).
- [x] Open questions recorded (Coverage buffer initiative) pending resolution.
- [x] `docs/5-operations/analysis-gate-checklist.md` executed and recorded (2025-10-01).

## Tooling Readiness
- `./gradlew jacocoAggregatedReport` – regenerates HTML/XML coverage data.
- `open build/reports/jacoco/aggregated/html/index.html` – inspect Maintenance CLI packages (note path references instead of raw HTML in docs).
- `./gradlew :cli:test --tests "*Maintenance*"` (optional) – targeted run if coverage data needs refreshing.

## Notes
- 2025-10-01 – C1205 implemented forked-JVM failure-path coverage; `MaintenanceCliTest.mainExitsProcessWhenRunFails` now asserts exit code 1 with JaCoCo agent propagation.
- 2025-10-01 – C1206 added corrupted database verification test hitting maintenance catch branch (`verifyCommandReportsFailuresForCorruptStore`).
- 2025-10-01 – C1207 added supplementary branch tests (`compactCommandHandlesRelativeDatabasePath`, short help flag, blank suite/key validations).
- 2025-10-01 – C1208 seeded legacy OCRA fixture; issue-listing branch now covered via `verifyCommandPrintsIssuesForLegacyMigrationFailure`.
- 2025-10-01 – Introduced `openauth.sim.persistence.skip-upgrade` test hook to bypass automatic upgrades when seeding legacy fixtures.
- Capture any deviations or surprises (e.g., missing execution data) here for future implementers.
- Record final hotspot report location and summary once complete.


## Hotspot Analysis (2025-10-01)

### Coverage Snapshot
- `jacocoAggregatedReport` refreshed via `./gradlew jacocoAggregatedReport` (Java 17).
- Package `io.openauth.sim.cli` – line coverage 97.56%, branch coverage 93.30%.
- Class `io.openauth.sim.cli.MaintenanceCli` – line coverage 154/158 (97.47%), branch coverage 69/73 (94.52%), complexity coverage 42/46 (91.30%).

### Hotspots & Recommendations
| Area | Coverage Impact | Observation | Recommended Test/Scenario | Priority |
|------|------------------|-------------|---------------------------|---------|
| `MaintenanceCli.main` (`cli/src/main/java/io/openauth/sim/cli/MaintenanceCli.java:32`) | Branch: 1/2 (System.exit path untouched) | Failure exit path never executes because tests avoid `System.exit`. | Implemented via `MaintenanceCliTest.mainExitsProcessWhenRunFails` (forked JVM asserts exit code 1 with JaCoCo agent). | High |
| `MaintenanceCli.run` directory handling (`cli/src/main/java/io/openauth/sim/cli/MaintenanceCli.java:70`) | Branch: parent-null outcome untested | All fixtures pass absolute paths, so the `parent == null` branch is uncovered. | Implemented via `MaintenanceCliTest.compactCommandHandlesRelativeDatabasePath` (forked JVM working dir = temp, `store.db` relative). | Medium |
| Maintenance operations error trap (`cli/src/main/java/io/openauth/sim/cli/MaintenanceCli.java:86-105`) | Branch: catch path never triggered | Current fixtures always succeed, leaving error handling via `maintenance command failed` unreachable. | Implemented via `MaintenanceCliTest.verifyCommandReportsFailuresForCorruptStore` (writes invalid bytes, asserts exit code 1 + stderr). | High |
| OCRA failure catch-all (`cli/src/main/java/io/openauth/sim/cli/MaintenanceCli.java:173-175`) | Lines 173-175 uncovered | Only validation (`IllegalArgumentException`) paths are exercised; unexpected runtime failures remain untested. | Use temporary wrapper around `OcraResponseCalculator` via test seam (e.g., dependency injection in follow-up increment) or simulate `OcraCredentialFactory` throwing `RuntimeException` by corrupting suite metadata. | Medium |
| OCRA help short flag (`cli/src/main/java/io/openauth/sim/cli/MaintenanceCli.java:216`) | Branch: `-h` path untested | Tests cover `--help` but not `-h`. | Implemented via `MaintenanceCliTest.ocraCommandHandlesShortHelp`. | Low |
| OCRA required parameters blank (`cli/src/main/java/io/openauth/sim/cli/MaintenanceCli.java:226,230`) | Branches: `suite.isBlank()` and `key.isBlank()` outcomes missing | Only null-valued scenarios are validated; blank strings bypass coverage. | Implemented via `MaintenanceCliTest.ocraCommandRejectsBlankSuite` and `MaintenanceCliTest.ocraCommandRejectsBlankKey`. | Medium |
| Maintenance argument null guard (`cli/src/main/java/io/openauth/sim/cli/MaintenanceCli.java:55`) | Branch: `parsed == null` unreachable | `parseMaintenanceArguments` never returns `null`; condition is defensive only. | Option A: keep as-is and accept permanent branch deficit; Option B: refactor method to return `Optional<ParsedArguments>` so guard becomes meaningful. Recommend Option A (documented here) to avoid churn. | Informational |
| Issue listing (`cli/src/main/java/io/openauth/sim/cli/MaintenanceCli.java:101-102`) | Branch: issues-present path uncovered | Needed a controlled invalid record that keeps `MaintenanceResult` WARN/FAIL without extra dependencies. | Implemented via `MaintenanceCliTest.verifyCommandPrintsIssuesForLegacyMigrationFailure` using `MapDbMaintenanceFixtures`. | High |


- 2025-10-01 – Analysis gate executed: spec/plan/tasks aligned; proceeding with documentation-only analysis.
