# Feature 033 – Operator Console Naming Alignment

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/033/plan.md` |
| Linked tasks | `docs/4-architecture/features/033/tasks.md` |
| Roadmap entry | #31 – Operator Console Simplification |

## Overview
The operator console hosted in `rest-api` evolved from an OCRA-only UI to a unified control panel for HOTP, TOTP, and
WebAuthn flows. Several Spring components, telemetry hooks, and documentation still carry legacy `Ocra*` prefixes. This
feature aligns the terminology so code, telemetry, and docs all reference the console generically as the “Operator
Console.”

## Clarifications
- 2025-10-21 – Option B approved: perform a comprehensive rename (controllers, telemetry endpoints, templates/tests) so
  only neutral `OperatorConsole*` terminology remains.

## Goals
- G-033-01 – Rename Spring MVC controllers/beans/templates from `Ocra*` to `OperatorConsole*` without altering behaviour.
- G-033-02 – Update UI telemetry endpoint/logger identifiers to the new naming while preserving payload schemas.
- G-033-03 – Refresh templates/JS assets/Selenium suites to reference the updated endpoints and remain green.
- G-033-04 – Update documentation/knowledge artefacts and rerun the analysis gate with the new terminology.

## Non-Goals
- N-033-01 – Adding new operator console functionality.
- N-033-02 – Renaming protocol-specific sample data classes that intentionally remain OCRA-only.
- N-033-03 – Changing REST API contracts beyond the UI telemetry hook (`/ui/console/replay/telemetry`).

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-033-01 | Spring MVC controllers/beans/templates adopt `OperatorConsole*` naming with no lingering `Ocra` prefixes (S-033-01). | Controller classes, beans, and Thymeleaf templates reference `OperatorConsole*`. | Unit + MockMvc tests compile and assert the new class names. | Any production component still uses the `Ocra` prefix. | Telemetry unchanged; only naming updated. | Clarifications 2025-10-21. |
| FR-033-02 | UI telemetry endpoint/logger identifiers switch to operator-console naming while keeping payload schemas (S-033-02). | Endpoint path `/ui/console/replay/telemetry`; event keys emit `ui.console.*`. | MockMvc/Selenium tests assert new endpoint/event names. | Legacy endpoint path or event key remains accessible. | TelemetryContracts adapters still emit existing payload fields. | G-033-02. |
| FR-033-03 | Templates/JS assets/Selenium suites reference the new controller/endpoint names and remain green (S-033-03). | Thymeleaf templates + JS link to `OperatorConsole*`; Selenium suite passes. | `./gradlew --no-daemon :rest-api:test` targeted suites remain green. | Tests fail due to stale identifiers. | None. | G-033-03. |
| FR-033-04 | Documentation (knowledge map, session snapshot, referenced feature plans) reflects the new naming and the analysis gate reruns green (S-033-04). | Docs mention “Operator Console” only; analysis gate run recorded. | Docs review + `spotlessApply check`. | Docs retain legacy names; gate not executed. | N/A. | G-033-04. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-033-01 | Maintain a green Gradle gate after renaming (`./gradlew --no-daemon :rest-api:test spotlessApply check`). | Constitution QA rule. | Command logs stored in plan/tasks. | rest-api module, Spotless. | Project constitution. |
| NFR-033-02 | Keep telemetry emission through `TelemetryContracts` (no bespoke loggers). | Architecture guardrail. | Code review ensures adapters remain. | application + rest-api modules. | Project constitution. |
| NFR-033-03 | Documentation/knowledge map references stay in sync with the rename. | Governance traceability. | Doc diff review + session snapshot update. | docs hierarchy. | G-033-04. |

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-033-01 | Spring MVC controllers/beans/templates adopt `OperatorConsole*` naming. |
| S-033-02 | UI telemetry endpoint/logger identifiers use operator-console naming while payload schemas stay unchanged. |
| S-033-03 | Templates/JS assets/Selenium suites reference the new names and remain green. |
| S-033-04 | Documentation/knowledge artefacts reflect the rename and the analysis gate reruns green. |

## Test Strategy
- CLI unaffected; focus on `rest-api` modules:
  - `./gradlew --no-daemon :rest-api:test` (covers MockMvc + Selenium).
  - `./gradlew --no-daemon spotlessApply check` to confirm formatting + naming updated.
- Manual verification: confirm telemetry events logged as `ui.console.*` and no `OcraOperatorUiController` references remain.

## Interface & Contract Catalogue
### Controllers/Beans
| ID | Description | Modules |
|----|-------------|---------|
| CT-033-01 | `OperatorConsoleController` – main MVC controller replacing `OcraOperatorUiController`. | rest-api |
| CT-033-02 | `OperatorConsoleTelemetryLogger` – updated telemetry endpoint handler. | rest-api |

### Templates & Assets
| ID | Path | Notes |
|----|------|-------|
| UI-033-01 | `rest-api/src/main/resources/templates/ui/console/` | Templates reference canonical controller names. |

## Documentation Deliverables
- Update knowledge map, roadmap, and `_current-session.md` entries referencing the console; note the rename in migration plan.
- Ensure feature plans/tasks citing the console adopt the new terminology.

## Fixtures & Sample Data
- None introduced; existing sample data remains OCRA-specific as appropriate.

## Spec DSL
```
scenarios:
  - id: S-033-01
    focus: controller-renames
  - id: S-033-02
    focus: telemetry-endpoint
  - id: S-033-03
    focus: templates-tests
  - id: S-033-04
    focus: documentation
requirements:
  - id: FR-033-01
    maps_to: [S-033-01]
  - id: FR-033-02
    maps_to: [S-033-02]
  - id: FR-033-03
    maps_to: [S-033-03]
  - id: FR-033-04
    maps_to: [S-033-04]
non_functional:
  - id: NFR-033-01
    maps_to: [S-033-03]
  - id: NFR-033-02
    maps_to: [S-033-02]
  - id: NFR-033-03
    maps_to: [S-033-04]
```
