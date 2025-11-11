# Feature 012 – Maintenance CLI Coverage Buffer

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/012/plan.md` |
| Linked tasks | `docs/4-architecture/features/012/tasks.md` |
| Roadmap entry | #12 |

## Overview
Analyse the Maintenance CLI coverage profile, document hotspots that threaten the Jacoco aggregated buffer, and land targeted regression tests so the CLI keeps ≥0.90 line/branch coverage once the temporary 0.70 relaxation is lifted. The work delivers a repeatable hotspot report plus forked-JVM and corrupted-database tests that exercise the remaining failure branches without touching runtime behaviour.

## Clarifications
- 2025-10-01 – Coverage buffer refers to maintaining ≥0.90 line **and** ≥0.90 branch ratios in the aggregated Jacoco verification configured in `build.gradle.kts` (`jacocoCoverageVerification` limits 0.90/0.90). (Option A)
- 2025-10-06 – Thresholds are temporarily reduced to ≥0.70/≥0.70 to accelerate HOTP delivery; roadmap Workstream 19 tracks restoring the 0.90 buffer once scope stabilises. The pre-commit hook still runs `jacocoAggregatedReport`, `jacocoCoverageVerification`, and `mutationTest`; developers use `./gradlew check -Ppit.skip=true` during feature work. (Option A)
- 2025-10-01 – Scope is limited to Maintenance CLI commands and helpers (`io.openauth.sim.cli.MaintenanceCli` and nested records). Other CLI/REST/core modules stay out of scope. (Option A)
- 2025-10-01 – Introduced system property `openauth.sim.persistence.skip-upgrade` for test-only fixtures; defaults to `false` and lets tests seed legacy records without triggering automatic upgrades. (Option A)

## Goals
- Capture accurate Maintenance CLI coverage metrics and hotspot analysis so future work has a baseline.
- Recommend and implement targeted regression tests (forked JVM failure path, corrupted DB, supplementary branches) that protect the Jacoco buffer.
- Document reproduction commands, thresholds, and hotspot findings in the plan/tasks so coverage remains auditable.

## Non-Goals
- Adding new CLI functionality or changing Maintenance CLI runtime behaviour.
- Modifying REST/UI modules or Jacoco thresholds beyond the temporary relaxation already documented.
- Introducing new dependencies beyond existing CLI test fixtures.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-012-01 | Capture aggregated Jacoco line/branch metrics for Maintenance CLI classes and store them in the hotspot report. | `./gradlew jacocoAggregatedReport` runs and the plan lists up-to-date percentages plus file references. | Reviewers can trace metrics in `docs/4-architecture/features/012/plan.md` to the Jacoco HTML. | Missing or stale metrics leave the plan undocumented. | Build output only; no runtime telemetry. | Clarifications 1–2. |
| FR-012-02 | Identify Maintenance CLI hotspots (untested branches, failure paths) with coverage data and risk context. | Plan documents each hotspot with file path, coverage numbers, and risk rationale. | Hotspot table references Jacoco data and aligns with Scenario S-012-02. | Analysis lacks evidence or omits known hotspots. | None. | Goals. |
| FR-012-03 | Recommend concrete regression scenarios for every hotspot, including commands, expected outcomes, and priority. | Plan lists recommended tests that map to task IDs and scenarios. | `docs/4-architecture/features/012/plan.md` links each hotspot to a test recommendation; tasks reference them. | Missing recommendations prevent implementation planning. | None. | Goals. |
| FR-012-04 | Implement forked-JVM test covering `MaintenanceCli.main` failure exit path without terminating the test JVM. | `MaintenanceCliTest` (or equivalent) spawns a JVM, asserts exit code 1, and keeps Jacoco coverage intact. | `./gradlew :cli:test --tests "*MaintenanceCli*"` covers the System.exit branch; coverage report confirms branch is green. | Forked JVM test fails or leaves branch uncovered. | None. | Clarifications 2; Goals. |
| FR-012-05 | Implement corrupted-database verification test that exercises the maintenance catch block and asserts exit code 1 plus the failure message. | CLI test feeds invalid data store, expecting exit code 1 and `maintenance command failed`. | `./gradlew :cli:test --tests "*MaintenanceCli*"` captures stderr assertion; coverage report marks catch path covered. | Test fails or bypasses the catch branch. | None. | Goals. |
| FR-012-06 | Add supplementary branch coverage (parent-null path, `-h` flag, blank suite/key, legacy issue listing) to preserve ≥0.90 buffer when thresholds restore. | CLI tests cover each branch and plan documents any remaining gaps (e.g., defensive null guard). | Branch counters for the affected lines show coverage ≥0.90; plan notes accepted gaps. | Branch coverage regresses or gaps lack documentation. | None. | Goals. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-012-01 | Traceability – hotspot report plus tests must live in version control. | Future work needs evidence to justify coverage buffers. | `docs/4-architecture/features/012/plan.md` stores metrics & recommendations, tasks reference them. | Plan/docs, Jacoco report. | Clarification 1. |
| NFR-012-02 | Reproducibility – coverage commands remain documented. | Contributors can rerun analysis quickly. | Plan/tasks list `./gradlew jacocoAggregatedReport`, `./gradlew :cli:test`, and browser commands. | Gradle build, Jacoco plugin. | Goals. |
| NFR-012-03 | Scope discipline – no runtime dependency drift. | Keep changes limited to tests/docs and note unresolved coverage gaps. | Git history shows only test/doc edits; plan records accepted gaps (e.g., `parsed == null`). | CLI module tests/docs. | Clarifications 3–4. |

## UI / Interaction Mock-ups
_Not applicable – Maintenance CLI coverage has no UI footprint._

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-012-01 | Aggregated Jacoco run captures current Maintenance CLI line/branch metrics and stores them in the hotspot report. |
| S-012-02 | Hotspot analysis documents every low-coverage branch with recommended tests and priorities. |
| S-012-03 | Forked JVM test exercises `MaintenanceCli.main` failure branch while preserving Jacoco coverage. |
| S-012-04 | Corrupted database verification test triggers the maintenance catch block, asserting exit code 1 + error message. |
| S-012-05 | Supplementary branch tests (parent-null path, `-h` flag, blank parameters, legacy issue listing) keep the coverage buffer intact when thresholds return to 0.90. |

## Test Strategy
- **CLI:** Add/extend JUnit tests (`MaintenanceCliTest` variants) for forked JVM exit, corrupted DB, parent-null, short help, blank parameter validations, and legacy fixtures.
- **Build tooling:** Run `./gradlew jacocoAggregatedReport` to regenerate coverage data after tests land; verify aggregated HTML matches plan notes.
- **Quality gate:** `./gradlew --no-daemon spotlessApply check` and `jacocoCoverageVerification` remain part of the standard pipeline; enforcement temporarily accepts ≥0.70 while roadmap Workstream 19 restores ≥0.90.

## Interface & Contract Catalogue
### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-012-NA | No new runtime domain objects; tests rely on existing Maintenance CLI records. | — |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-012-NA | — | No REST/service changes. | — |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-012-01 | `./gradlew --no-daemon jacocoAggregatedReport` | Regenerates coverage data used in the hotspot report. |
| CLI-012-02 | `./gradlew --no-daemon :cli:test --tests "*MaintenanceCli*"` | Runs the Maintenance CLI test suite including forked JVM and corrupted DB cases. |
| CLI-012-03 | `./gradlew --no-daemon check -Ppit.skip=true` | Developer workflow executing quality gates while skipping PIT as clarified. |

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|-----------|---------------------------|
| TE-012-NA | — | Build-only coverage enforcement; no runtime telemetry. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-012-01 | `docs/4-architecture/features/012/plan.md` | Stores hotspot metrics/recommendations. |
| FX-012-02 | `build/reports/jacoco/aggregated/html/index.html` | Coverage evidence referenced by the plan. |
| FX-012-03 | `cli/src/test/java/io/openauth/sim/cli/MaintenanceCliTest.java` (and helpers) | Houses forked JVM/corrupted DB/supplementary branch tests. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| UI-012-NA | — | Not applicable. |

## Telemetry & Observability
Observability relies on Jacoco HTML/XML reports plus Gradle task output. When coverage drops below thresholds, `jacocoCoverageVerification` fails the gate. The plan records metrics so trends remain auditable; no runtime telemetry is emitted.

## Documentation Deliverables
- `docs/4-architecture/features/012/plan.md` – Hotspot analysis, coverage metrics, and recommendations.
- `docs/_current-session.md` – Session log entry for Feature 012 template migration.
- `docs/migration_plan.md` – Progress update to unblock directory renumbering once legacy features align with the template.

## Fixtures & Sample Data
- Maintain Jacoco HTML output under `build/reports/jacoco/aggregated/` for audit.
- Keep CLI test fixtures (corrupted DB blobs, legacy MapDB snapshots) under `cli/src/test/resources/maintenance/` so regression tests remain deterministic.

## Spec DSL
```
cli_commands:
  - id: CLI-012-01
    command: ./gradlew --no-daemon jacocoAggregatedReport
    description: Regenerates Maintenance CLI coverage data
  - id: CLI-012-02
    command: ./gradlew --no-daemon :cli:test --tests "*MaintenanceCli*"
    description: Runs Maintenance CLI regression tests covering hotspot scenarios
  - id: CLI-012-03
    command: ./gradlew --no-daemon check -Ppit.skip=true
    description: Developer workflow while PIT skips are permitted
fixtures:
  - id: FX-012-01
    path: docs/4-architecture/features/012/plan.md
    purpose: Stores hotspot metrics & recommendations
  - id: FX-012-02
    path: build/reports/jacoco/aggregated/html/index.html
    purpose: Coverage evidence for Maintenance CLI
  - id: FX-012-03
    path: cli/src/test/java/io/openauth/sim/cli/MaintenanceCliTest.java
    purpose: Forked JVM/corrupted DB/supplementary branch tests
scenarios:
  - id: S-012-01
    description: Coverage snapshot stored in plan
  - id: S-012-02
    description: Hotspot analysis with actionable recommendations
  - id: S-012-03
    description: Forked JVM failure-path test covers System.exit branch
  - id: S-012-04
    description: Corrupted database test drives maintenance catch path
  - id: S-012-05
    description: Supplementary branch coverage preserves ≥0.90 buffer
```

## Appendix (Optional)
- Coverage snapshot (2025-10-01): `io.openauth.sim.cli` line 97.56%, branch 93.30%. `MaintenanceCli` line 97.47%, branch 94.52%, complexity 91.30%.
- Accepted gap: defensive `parsed == null` guard remains uncovered; documented to avoid unnecessary API churn.
