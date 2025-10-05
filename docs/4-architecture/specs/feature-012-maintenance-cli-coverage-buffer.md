# Feature 012 – Maintenance CLI Coverage Buffer

_Status: Draft_
_Last updated: 2025-10-06_

## Overview
Analyse Maintenance CLI code paths to confirm the Jacoco aggregated coverage buffer remains above 90% for both line and branch counters, and recommend targeted tests that preserve the buffer as new functionality lands. This feature first captured an evidence-backed hotspot report and now implements targeted Maintenance CLI tests to preserve the ≥0.90 Jacoco buffer. As of 2025-10-06 the enforcement gate is temporarily relaxed to ≥0.70 for both counters to accelerate HOTP delivery; the roadmap workstream added in the same update commits to restoring the 0.90 buffer once the feature set stabilises.

## Clarifications
- 2025-10-01 – Coverage buffer refers to maintaining ≥0.90 line **and** ≥0.90 branch ratios in the aggregated Jacoco verification configured in `build.gradle.kts` (see `jacocoCoverageVerification` limits set to 0.90/0.90).
- 2025-10-06 – Thresholds are temporarily reduced to ≥0.70 line and ≥0.70 branch to accelerate feature delivery; roadmap Workstream 19 tracks restoration of the 0.90/0.90 buffer once HOTP scope stabilises. The git pre-commit hook continues to run `jacocoAggregatedReport`, `jacocoCoverageVerification`, and `mutationTest` while developer workflows invoke `./gradlew check -Ppit.skip=true` to keep feature cycles fast.
- 2025-10-01 – Scope limited to Maintenance CLI commands and supporting helpers within `io.openauth.sim.cli` (e.g., `MaintenanceCli` and its nested records). Other CLI/REST/core modules are out of scope for this review.
- 2025-10-01 – Follow-up increment will implement high-priority Maintenance CLI tests derived from the hotspot analysis to protect both ≥0.90 line and branch coverage thresholds.
- 2025-10-01 – Introduced system property `openauth.sim.persistence.skip-upgrade` for test-only fixtures; defaults to `false` and allows seeding legacy records without triggering automatic upgrades.

## Functional Requirements
| ID | Requirement | Acceptance Signal |
|----|-------------|-------------------|
| MCLI-COV-001 | Capture current Jacoco coverage metrics (line and branch) for Maintenance CLI classes from the aggregated report. | Report cites latest coverage percentages for relevant packages/classes with pointers to the underlying Jacoco output. |
| MCLI-COV-002 | Identify Maintenance CLI code paths at risk of eroding the ≥0.90 coverage thresholds, including unexecuted branches such as failure paths in `MaintenanceCli.run`. | Report lists each hotspot with file/class reference, coverage details, and rationale for risk. |
| MCLI-COV-003 | Recommend specific, testable scenarios to close or protect the identified hotspots while keeping the buffer above 0.90/0.90. | Report provides actionable test recommendations (name, scope, behavioural focus) for every hotspot. |
| MCLI-COV-004 | Ensure `MaintenanceCli.main` failure path executes under Jacoco without terminating the test JVM. | Forked JVM test asserts non-zero exit code and retains aggregated coverage (System.exit branch covered). |
| MCLI-COV-005 | Cover maintenance failure path by exercising the CLI catch branch when the database is corrupted. | Test drives `maintenance verify` against an invalid file, asserting exit code 1 and the `maintenance command failed` error message. |
| MCLI-COV-006 | Exercise supplementary branches (database parent null, OCRA short help flag, blank required parameters). | Tests demonstrate parent-null success, `-h` help output, and blank suite/key validations, raising branch ratios above 0.90. |

## Non-Functional Requirements
| ID | Requirement | Target |
|----|-------------|--------|
| MCLI-COV-NFR-001 | Traceability | Store the hotspot analysis under `docs/4-architecture/feature-plan-012-maintenance-cli-coverage-buffer.md` (or referenced appendix) so future work can implement the recommended tests. |
| MCLI-COV-NFR-002 | Reproducibility | Document the commands used to collect Jacoco data, ensuring future runs can replicate the analysis. |
| MCLI-COV-NFR-003 | Scope discipline | Keep changes limited to tests/documentation (no new runtime dependencies) and log remaining coverage gaps. |

## Test Strategy
- Add or extend CLI JUnit tests ahead of implementation (forked JVM failure path, corrupted verify, supplementary branch cases).
- Re-run `./gradlew :cli:test` and `./gradlew jacocoAggregatedReport` to confirm branch ≥0.90 before finishing the increment.

## Dependencies & Risks
- Jacoco execution data must include Maintenance CLI usage; rerun `./gradlew jacocoAggregatedReport` if new tests have landed since the last report to avoid stale metrics.
- Coverage fluctuations from unrelated modules are outside this scope; note any cross-module dependencies that could affect Maintenance CLI coverage.

## Out of Scope
- Adjusting Jacoco thresholds or Gradle wiring.
- Analysing non-Maintenance CLI modules.

## Verification
- Deliverable hotspot report checked into the repository with the required metrics, hotspots, and recommendations.
- Maintenance CLI tests covering identified hotspots implemented and passing.
- Open questions resolved in this spec and documented in `docs/4-architecture/open-questions.md`.
