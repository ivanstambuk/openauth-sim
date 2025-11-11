# Feature 008 – OCRA Quality Automation

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/008/plan.md` |
| Linked tasks | `docs/4-architecture/features/008/tasks.md` |
| Roadmap entry | #8 |

## Overview
Establish an automated quality gate for the OpenAuth Simulator’s OCRA stack so module boundaries remain enforced and behavioural regressions surface early. The gate combines ArchUnit boundary checks, Jacoco coverage thresholds, PIT mutation testing, and existing lint/formatting tasks into a single Gradle entry point that runs locally and in GitHub Actions. Documentation teaches contributors how to execute the gate, interpret reports, and remediate failures without digging through build scripts.

## Clarifications
- 2025-09-30 – The gate covers architecture boundary enforcement plus mutation and coverage thresholds. CI badge wiring and broader QA tooling remain out of scope for now.
- 2025-09-30 – Local Gradle workflows and GitHub Actions must run the same quality gate to keep developer and CI feedback aligned.
- 2025-09-30 – Reuse the existing tool stack (ArchUnit, PIT, Jacoco, gitleaks) before introducing new dependencies.
- 2025-09-30 – Coverage thresholds target ≥90% line and branch for core OCRA packages; raise coverage via new tests rather than relaxing the thresholds.

## Goals
- Guard the architecture boundary between core/application/persistence and facade modules via deterministic ArchUnit rules.
- Enforce ≥90% coverage and ≥85% mutation-score targets for OCRA code paths.
- Provide a single Gradle entry point (`qualityGate`) plus a CI workflow that runs it on every push/PR.
- Document quality-gate usage, skip flags, report locations, and remediation playbooks.

## Non-Goals
- Introducing new simulator features or runtime telemetry.
- Applying the automation to non-OCRA modules (tracked separately).
- Publishing status badges or adding unrelated security scanners—these belong to future workstreams.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-008-01 | Define ArchUnit (or equivalent) rules that prevent CLI/REST/UI from bypassing the application layer or touching core internals directly. | `./gradlew qualityGate` fails fast when a forbidden dependency appears and passes when boundaries hold. | Dedicated ArchUnit tests seed illegal dependencies to prove the rule fires; CI run captures failure output. | Missing or misconfigured rules allow architecture drift without detection. | Quality logs document the failing rule; no runtime telemetry is affected. | Clarifications 2025-09-30; Goals. |
| FR-008-02 | Integrate PIT mutation testing for OCRA packages with a minimum surviving-mutation threshold (target ≥85%). | PIT executes as part of the gate and reports ≥85% score. | Build scripts expose toggles (e.g., `-Ppit.skip=true`) for local triage while CI enforces the full run. | Mutation score drops below threshold and the build fails, blocking merges until tests improve. | Build output references PIT HTML reports under `build/reports/pitest`. | Clarifications 2025-09-30; Goals. |
| FR-008-03 | Enforce Jacoco aggregated coverage thresholds (≥90% line and branch) across core/application/facade OCRA code paths. | Coverage reports show ≥90% and the gate passes. | CI and local runs surface threshold failures with pointers to offending classes. | Build finishes with non-zero exit status when coverage regresses. | Jacoco HTML/CSV reports stored under `build/reports/jacoco/aggregated/`; referenced in docs. | Goals; Non-goals (no relaxations). |
| FR-008-04 | Provide a single Gradle task (`qualityGate`) that aggregates ArchUnit, PIT, Jacoco, Spotless, Checkstyle, SpotBugs, and other lint suites. | Contributors run one command locally to execute every guard. | Task wiring documented in plan/tasks; integration tests ensure dependencies run in the right order. | Missing aggregation forces ad-hoc commands and increases drift between contributors/CI. | Gradle console output plus `build/reports/quality/` capture results; no runtime telemetry needed. | Goals. |
| FR-008-05 | Run the same `qualityGate` task inside GitHub Actions (push + PR) with caching so runtime stays manageable. | Workflow completes successfully when gate passes and blocks merges when it fails. | Workflow definition references identical Gradle command and publishes artifacts/logs. | Divergent CI/local behaviour causes false positives or untested paths. | GitHub Actions logs and uploaded reports provide traceability. | Clarifications 2025-09-30. |
| FR-008-06 | Document how to run, interpret, and troubleshoot the quality gate (docs/how-to, roadmap/knowledge map). | Docs list prerequisites, commands, skip flags, report paths, and remediation steps. | `./gradlew spotlessApply check` validates Markdown formatting; doc reviews ensure accuracy. | Missing docs leave contributors guessing about failures and next steps. | Documentation references report locations; no new telemetry. | Goals. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-008-01 | Keep local `qualityGate` runtime ≤10 minutes on a developer laptop by scoping PIT/Jacoco targets and enabling caching. | Productivity – long-running gates discourage usage. | Wall-clock timing recorded in plan/tasks; CI job duration tracked in workflow logs. | Gradle caching, PIT filters, GitHub Actions cache. | Clarifications 2025-09-30. |
| NFR-008-02 | Parameterise thresholds and package lists so future tuning does not require code rewrites. | Maintainability – future features may adjust targets. | Thresholds defined via Gradle properties/extension; documented in plan. | Gradle conventions plugins, `qualityGate` task. | Goals. |
| NFR-008-03 | Store PIT/Jacoco artifacts under `build/reports/quality/` (or linked folders) and reference them in docs for troubleshooting. | Transparency – contributors need deterministic report locations. | Build output lists report directories; docs link to them. | Gradle reporting configuration. | Goals. |

## UI / Interaction Mock-ups
_Not applicable – this feature is build automation only._

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-008-01 | ArchUnit boundary rules detect illegal module dependencies. |
| S-008-02 | Jacoco aggregation enforces ≥90% thresholds and fails when coverage regresses. |
| S-008-03 | PIT mutation testing enforces ≥85% threshold with actionable reports. |
| S-008-04 | `./gradlew qualityGate` aggregates all quality tasks into one command. |
| S-008-05 | GitHub Actions workflow runs the same gate per push/PR and publishes logs/reports. |
| S-008-06 | Documentation (how-to, roadmap, knowledge map) explains gate usage, skip flags, and remediation steps.

## Test Strategy
- **Architecture tests:** ArchUnit suites enforce and regression-test module boundaries.
- **Coverage/mutation verification:** Targeted Jacoco aggregation and PIT runs fail intentionally when thresholds dip during development.
- **Build task tests:** Gradle integration tests (or smoke runs) ensure `qualityGate` wires every sub-task with the correct dependencies.
- **CI workflow dry-run:** GitHub Actions workflow validated in a feature branch before being marked required; artifacts stored for audit.
- **Documentation linting:** `./gradlew spotlessApply check` plus optional `markdownlint` keep how-to updates compliant.

## Interface & Contract Catalogue
### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-008-NA | No new runtime domain objects introduced; automation operates within Gradle build scripts. | — |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-008-NA | — | No runtime API changes. | — |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-008-01 | `./gradlew --no-daemon qualityGate [-Ppit.skip=true]` | Executes ArchUnit, PIT, Jacoco, Spotless, SpotBugs, Checkstyle, and related tasks; optional flag skips PIT locally. |

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|-----------|---------------------------|
| TE-008-NA | — | Build automation only; no runtime telemetry changes. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-008-01 | `build/reports/jacoco/aggregated/` | Coverage HTML/CSV reports referenced in docs. |
| FX-008-02 | `build/reports/pitest/` | Mutation testing reports referenced during triage. |
| FX-008-03 | `.github/workflows/quality-gate.yml` (or equivalent) | Workflow definition storing cache configuration and Gradle command. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| UI-008-NA | — | Not applicable. |

## Telemetry & Observability
Quality-gate output appears in Gradle logs plus PIT/Jacoco HTML reports. GitHub Actions artifacts capture the same data for CI runs. No new production telemetry is introduced; instead, documentation references existing report directories for troubleshooting.

## Documentation Deliverables
- Update operator/developer guides (e.g., `docs/5-operations/quality-gate.md`, roadmap, knowledge map) with commands, thresholds, and skip flags.
- Note the gate in `docs/_current-session.md` and migration trackers when template or governance changes occur.

## Fixtures & Sample Data
- Retain deterministic coverage/mutation fixture data under `docs/test-vectors/ocra/`; automation references these tests indirectly.
- Store PIT/Jacoco HTML artifacts under `build/reports/quality/` (or the directories listed above) for audit.

## Spec DSL
```
domain_objects: []
cli_commands:
  - id: CLI-008-01
    command: ./gradlew --no-daemon qualityGate [-Ppit.skip=true]
    description: Runs the aggregated quality gate locally or in CI
telemetry_events: []
fixtures:
  - id: FX-008-01
    path: build/reports/jacoco/aggregated/
  - id: FX-008-02
    path: build/reports/pitest/
scenarios:
  - id: S-008-01
    description: ArchUnit rules enforce module boundaries
  - id: S-008-02
    description: Jacoco thresholds block regressions
  - id: S-008-03
    description: PIT thresholds block regressions
  - id: S-008-04
    description: qualityGate aggregates tasks into one command
  - id: S-008-05
    description: GitHub Actions workflow mirrors the local gate
  - id: S-008-06
    description: Documentation teaches operators how to run the gate
```

## Appendix (Optional)
- None.
