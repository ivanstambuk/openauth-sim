# Feature 010 – Documentation & Knowledge Automation

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-13 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/010/plan.md` |
| Linked tasks | `docs/4-architecture/features/010/tasks.md` |
| Roadmap entry | #10 – Documentation & Knowledge Automation |

## Overview
Feature 010 centralises every operator-facing guide, roadmap/knowledge-map reference, session workflow, and build-quality
automation note into a single specification so Java/CLI/REST guides, README messaging, roadmap snapshots, `_current-session.md`
logs, and Gradle quality tasks all evolve in lockstep. The feature is the authoritative source for doc structure,
knowledge-map automation, and the aggregated `qualityGate` pipeline (ArchUnit, Jacoco, PIT, Spotless, SpotBugs, Checkstyle,
gitleaks) that protects the OCRA stack and its supporting documentation.

## Clarifications
- 2025-09-30 – Operator documentation must cover Java integration (`docs/2-how-to/use-ocra-from-java.md`), CLI operations
  (`docs/2-how-to/use-ocra-cli-operations.md`), and REST workflows (`docs/2-how-to/use-ocra-rest-operations.md`) with
  runnable examples, telemetry expectations, and troubleshooting tips; README references only shipped capabilities.
- 2025-09-30 – A single Gradle entry point (`./gradlew --no-daemon qualityGate`) must run ArchUnit boundary checks,
  Jacoco aggregation (≥90% line/branch), PIT mutation tests (≥85% score), Spotless, Checkstyle, SpotBugs, and gitleaks so
  contributors replicate CI locally.
- 2025-09-30 – The GitHub Actions workflow mirrors the local `qualityGate`, uploads Jacoco/PIT/ArchUnit logs, and respects
  cache hints so runtimes stay manageable (<10 minutes on developer laptops, comparable timing in CI).
- (none currently)

## Goals
- G-010-01 – Deliver accurate, runnable operator guides for Java/CLI/REST flows plus README cross-links that reflect the
  current simulator capabilities.
- G-010-02 – Keep roadmap, knowledge map, session log (docs/_current-session.md), session quick reference, and `_current-session.md` synchronized with
  documentation migrations by logging commands and deltas for every increment.
- G-010-03 – Provide deterministic automation notes (templates, verification steps, knowledge-map refresh guidance) so doc
  updates remain auditable.
- G-010-04 – Maintain the aggregated `qualityGate` (ArchUnit + Jacoco + PIT + lint suites) with identical behaviour across
  local environments and GitHub Actions, including troubleshooting documentation and skip flags.

## Non-Goals
- Shipping new simulator runtime behaviour (doc/automation-only scope for Batch P3).
- Expanding quality automation to non-OCRA modules until future specs request it.
- Introducing new documentation formats or publishing pipelines beyond Markdown/ASCII.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-010-01 | Publish and maintain the operator doc suite (`docs/2-how-to/use-ocra-from-java.md`, `docs/2-how-to/use-ocra-cli-operations.md`, `docs/2-how-to/use-ocra-rest-operations.md`) with runnable snippets, telemetry expectations, and troubleshooting sections. | Operators follow prerequisites, copy commands, and reproduce OTP generation/replay flows without diving into source. | Spot-check snippets against the latest build; `./gradlew spotlessApply check` validates formatting; `_current-session.md` logs updates. | Outdated instructions block operators or contradict shipped behaviour. | No new telemetry; guides reference existing `core.ocra.*`, `cli.ocra.*`, `rest.ocra.*` frames. | Legacy Feature 007. |
| FR-010-02 | Keep `README.md` and how-to landing pages focused on shipped functionality, include Swagger UI links, and cross-link the operator docs/quality-gate guidance. | README lists active simulators + `http://localhost:8080/swagger-ui/index.html`, points to how-to guides, and omits future/planned placeholders. | Manual review + linting; `_current-session.md` records diff summary. | README references stale content or omits critical docs. | None. | Legacy Feature 007. |
| FR-010-03 | Synchronise roadmap, knowledge map, architecture graph, session quick reference, and `_current-session.md` whenever documentation or automation scope changes. | Doc updates mention Feature 010; `_current-session.md` logs commands (moves, deletions, verification). | `rg "Feature 010"` across docs; reviewers confirm entries after each increment. | Cross-document drift forces manual archaeology. | None. | Goals G-010-02/03. |
| FR-010-04 | Provide an aggregated Gradle task `./gradlew --no-daemon qualityGate` (with optional `-Ppit.skip=true`) that runs Spotless, Checkstyle, SpotBugs, ArchUnit, Jacoco aggregation, PIT mutation tests, and gitleaks in one command. | Running `qualityGate` locally matches CI output; reports land under `build/reports/quality/`, `build/reports/jacoco/aggregated/`, and `build/reports/pitest/`. | Observing Gradle output plus report folders; task wiring documented in plan/tasks. | Contributors must chain commands manually or miss required suites. | Build logs only; no runtime telemetry. | Legacy Feature 008. |
| FR-010-05 | Enforce ArchUnit boundary rules that block CLI/REST/UI modules from touching `core` directly (outside the application seams). | Illegal dependencies break `qualityGate` with actionable messages. | ArchUnit tests seed forbidden imports; CI artifacts capture failures. | Architecture drift ships undetected. | None. | Legacy Feature 008. |
| FR-010-06 | Maintain Jacoco aggregated coverage thresholds (≥90% line/branch) for OCRA code paths; failures block the gate until coverage is restored. | Jacoco reports meet thresholds locally and in CI; offenders listed when regressions occur. | Inspect `build/reports/jacoco/aggregated/index.html` + Gradle console when tests fail. | Coverage regressions pass unnoticed. | None. | Legacy Feature 008. |
| FR-010-07 | Maintain PIT mutation score ≥85% for OCRA packages, surfaced via `qualityGate` with HTML reports for debugging. | PIT runs during the gate and exits non-zero when the score falls below 85%; skip flag `-Ppit.skip=true` documented for local triage. | Build output references `build/reports/pitest`; docs explain thresholds and skip usage. | Mutation regressions merge unnoticed or developers cannot triage failures. | None. | Legacy Feature 008. |
| FR-010-08 | Ensure GitHub Actions runs the same `qualityGate` command (push + PR), caches Gradle/PIT/Jacoco artifacts, and uploads reports for auditing. | Workflow logs show identical command/flags; artifacts contain reports for inspection. | `.github/workflows/quality-gate.yml` (or successor) reviewed after edits; CI history tracked in `_current-session.md`. | Local and CI gates drift, causing false positives/negatives. | None. | Legacy Feature 008. |
| FR-010-09 | Document gate usage, skip flags, report locations, and remediation steps in `docs/5-operations/session-quick-reference.md`, roadmap, knowledge map, and `_current-session.md`. | Contributors find the gate runbook quickly and follow remediation playbooks for ArchUnit/Jacoco/PIT failures. | `rg "qualityGate" docs/5-operations/session-quick-reference.md` etc.; doc reviews confirm instructions. | Gate failures lack guidance, delaying fixes. | None. | Legacy Feature 008. |
| FR-010-10 | Log every documentation/automation increment in `_current-session.md`, including commands executed (`rm -rf`, `spotlessApply`, `qualityGate`) and outstanding follow-ups. | Session log shows the command list + rationale; session log (docs/_current-session.md) includes the latest Feature 010 activity. | Review `_current-session.md`, plan/tasks, and session log (docs/_current-session.md) while closing increments. | Auditors cannot trace what changed or which verification command ran. | None. | Goals G-010-02/04. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-010-01 | Documentation rebuild workflow (spec/plan/tasks + guides) must execute within 5 minutes, relying on `./gradlew --no-daemon spotlessApply check`. | Productivity | Wall-clock timing logged in plan/tasks; `_current-session.md` notes slow runs. | Spotless, doc templates. | Legacy Feature 007, Goals. |
| NFR-010-02 | `qualityGate` runtime stays ≤10 minutes on reference hardware via Gradle caching and optional PIT skip flag; CI caches mirror the local setup. | Developer ergonomics | Timing recorded in plan/tasks and CI job metadata. | Gradle caching, GitHub Actions workflow. | Legacy Feature 008. |
| NFR-010-03 | Coverage/mutation thresholds, target packages, and skip flags are parameterised through Gradle properties so tuning never requires code rewrites. | Maintainability | Thresholds stored in `gradle.properties`/`qualityGate` extension; docs explain overrides. | Gradle build scripts. | Legacy Feature 008. |
| NFR-010-04 | Templates and documentation remain ASCII/Markdown to avoid locale drift; linting keeps formatting consistent. | Consistency | `spotlessApply` + reviewer checks enforce ASCII. | Docs templates. | Clarifications 1. |
| NFR-010-05 | PIT/Jacoco/ArchUnit artifacts stay in deterministic locations (`build/reports/jacoco/aggregated/`, `build/reports/pitest/`, `build/reports/quality/`) referenced by docs for troubleshooting. | Transparency | `rg "build/reports" docs/4-architecture/features/010/spec.md` and doc deliverables cite these paths. | Gradle reporting configuration. | Legacy Feature 008. |

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-010-01 | Java/CLI/REST operator guides deliver runnable flows with telemetry/troubleshooting notes. |
| S-010-02 | README and doc landing pages link to active guides and remove stale references. |
| S-010-03 | Roadmap, knowledge map, architecture graph, session log (docs/_current-session.md), and `_current-session.md` stay synchronized with documentation moves. |
| S-010-04 | `./gradlew --no-daemon qualityGate` aggregates Spotless, Checkstyle, SpotBugs, ArchUnit, Jacoco, PIT, and gitleaks. |
| S-010-05 | ArchUnit boundary rules fire on illegal dependencies and keep gates green otherwise. |
| S-010-06 | Jacoco aggregated reports enforce ≥90% thresholds. |
| S-010-07 | PIT mutation reports enforce ≥85% thresholds (with documented skip flag). |
| S-010-08 | GitHub Actions workflow mirrors `qualityGate`, caches artifacts, and uploads reports. |
| S-010-09 | Docs/runbooks explain how to run/remediate the gate (commands, skip flags, report paths). |
| S-010-10 | `_current-session.md` + session log (docs/_current-session.md) entries log every documentation or automation move (commands, deletions, verification). |

## Test Strategy
- **Docs:** `./gradlew --no-daemon spotlessApply check` after every documentation increment; manual verification of Java/CLI/REST
  workflows before publishing changes.
- **Quality automation:** `./gradlew --no-daemon qualityGate` locally to exercise ArchUnit/Jacoco/PIT/Spotless/SpotBugs/Checkstyle/gitleaks; capture
  wall-clock time and report locations in plan/tasks.
- **CI parity:** GitHub Actions workflow executes the same `qualityGate` command on push/PR and uploads Jacoco/PIT
  artifacts for audit.
- **Logging:** `_current-session.md` records command output summaries for Feature 010.

## Interface & Contract Catalogue
### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-010-01 | `DocumentationGuide` metadata (path, prerequisites, verification command, owner) referenced from spec/plan/tasks. | docs |
| DO-010-02 | `QualityGateConfig` (thresholds, skip flags, report locations) stored in Gradle extensions/properties. | build logic |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-010-NA | n/a | Documentation-only scope; no REST/CLI services originate from this feature. | Reference existing simulator APIs as needed. |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-010-01 | `./gradlew --no-daemon spotlessApply check` | Required verification for documentation edits. |
| CLI-010-02 | `./gradlew --no-daemon qualityGate [-Ppit.skip=true]` | Runs the aggregated quality automation gate locally/CI. |

### Telemetry Events
| ID | Event name | Notes |
|----|-----------|-------|
| TE-010-NA | n/a | Documentation-only scope; relies on existing `core.ocra.*`, `cli.ocra.*`, and `rest.ocra.*` telemetry families. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-010-01 | `docs/2-how-to/use-ocra-from-java.md`, `docs/2-how-to/use-ocra-cli-operations.md`, `docs/2-how-to/use-ocra-rest-operations.md` | Operator-facing documentation bundle governed by this feature. |
| FX-010-02 | `docs/test-vectors/ocra/` | Deterministic OTP vectors referenced in docs. |
| FX-010-03 | `build/reports/jacoco/aggregated/`, `build/reports/pitest/`, `build/reports/quality/` | Report directories linked from the gate documentation. |

### UI States
_Not applicable._

## Telemetry & Observability
No runtime telemetry changes. Observability relies on deterministic documentation, `_current-session.md` logs, Gradle console
output, and the stored Jacoco/PIT/quality reports referenced above.

## Documentation Deliverables
- `docs/2-how-to/use-ocra-from-java.md`, `docs/2-how-to/use-ocra-cli-operations.md`, `docs/2-how-to/use-ocra-rest-operations.md` – operator
  guides kept current by this feature.
- `README.md` – refreshed to reference only shipped functionality and entry points.
- `docs/4-architecture/roadmap.md`, `docs/4-architecture/knowledge-map.md`, `docs/_current-session.md`, and
  `docs/5-operations/session-quick-reference.md` – synchronized references + logging expectations for documentation and automation work.
- `.github/workflows/quality-gate.yml` (or successor) – ensures CI parity for the quality gate.

## Fixtures & Sample Data
- Continue using `docs/test-vectors/ocra/` for deterministic OTP examples across all guides.
- Store Jacoco/PIT/quality reports in the documented `build/reports/**` directories for troubleshooting and CI artifact uploads.

## Spec DSL
```
domain_objects:
  - id: DO-010-01
    name: DocumentationGuide
    fields:
      - name: path
        type: string
      - name: verificationCommand
        type: string
  - id: DO-010-02
    name: QualityGateConfig
    fields:
      - name: jacocoLineThreshold
        type: int
        default: 90
      - name: jacocoBranchThreshold
        type: int
        default: 90
      - name: pitMutationScore
        type: int
        default: 85
cli_commands:
  - id: CLI-010-01
    command: ./gradlew --no-daemon spotlessApply check
  - id: CLI-010-02
    command: ./gradlew --no-daemon qualityGate [-Ppit.skip=true]
fixtures:
  - id: FX-010-01
    path: docs/2-how-to/use-ocra-rest-operations.md
  - id: FX-010-02
    path: build/reports/jacoco/aggregated/
scenarios:
  - id: S-010-01
    description: Operator guides stay runnable with telemetry/troubleshooting coverage.
  - id: S-010-04
    description: Aggregated qualityGate runs locally/CI with ArchUnit/Jacoco/PIT + lint suites.
  - id: S-010-08
    description: CI workflow mirrors local gate and uploads reports.
```
