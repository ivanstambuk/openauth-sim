# Feature 034 – Unified Validation Feedback Surfaces

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/034/plan.md` |
| Linked tasks | `docs/4-architecture/features/034/tasks.md` |
| Roadmap entry | #31 – Operator Console Simplification |

## Overview
Ensure every operator-console workflow renders service validation failures inside the visible result card so operators no
longer inspect logs/dev tools for feedback. The shared helper forces the result panel visible on invalid responses,
displays the API `message`, and becomes the default pattern for future ceremonies.

## Clarifications
- 2025-10-20 – Option 3.A approved: reuse the existing result card, reveal it automatically for invalid responses, and
  render the API `message` prominently for all ceremonies.
- 2025-10-20 – Apply the helper to OCRA, HOTP, TOTP, WebAuthn assertion, and WebAuthn attestation immediately and make it
  reusable for future flows.

## Goals
- G-034-01 – Audit current result-card toggles and capture baseline invalid-state behaviour.
- G-034-02 – Implement a reusable controller/view-model + JS helper that forces the card visible and surfaces validation
  messages.
- G-034-03 – Wire every existing ceremony to the helper so invalid responses surface feedback consistently.
- G-034-04 – Extend Selenium/docs coverage documenting the unified pattern.

## Non-Goals
- N-034-01 – Altering server-side validation copy beyond what APIs already return.
- N-034-02 – Adding per-field inline errors (future consideration).
- N-034-03 – Changing CLI/REST textual output (they already surface messages).

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-034-01 | Capture baseline invalid-state behaviour for all result cards (S-034-01). | Audit toggles/ARIA attributes; document gaps. | Manual review + screenshots stored with spec/plan. | Unknown existing regressions before helper rollout. | None. | Clarifications 2025-10-20. |
| FR-034-02 | Implement reusable helper (controller/view-model + JS hook) that reveals the card and injects API messages (S-034-02). | Helper toggles `showResultCard=true` and sets `validationMessage`. | Unit tests verify helper model + JS toggles. | Card stays hidden or message missing. | Telemetry unaffected. | G-034-02. |
| FR-034-03 | Wire OCRA, HOTP, TOTP, WebAuthn assertion/attestation to the helper (S-034-03). | Each ceremony uses helper; invalid responses display message in result card. | Selenium suites run invalid scenarios per ceremony. | Any ceremony fails to show message or card stays hidden. | Telemetry unchanged. | G-034-03. |
| FR-034-04 | Extend Selenium/docs to cover invalid flows and document the pattern (S-034-04). | Selenium tests assert message rendering; docs/how-tos describe behaviour; analysis gate rerun. | `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.*OperatorUiSeleniumTest"`, docs review. | Docs outdated or tests missing invalid coverage. | None. | G-034-04. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-034-01 | Maintain telemetry redaction (no secrets in validation messages). | Security posture. | Manual review ensures helper displays API `message` only. | REST/application modules. | Clarifications 2025-10-20. |
| NFR-034-02 | Keep Gradle gate green after helper rollout. | Constitution QA rule. | `./gradlew spotlessApply check`. | rest-api module. | Project constitution. |

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-034-01 | Baseline audit of current result-card behaviour and invalid states. |
| S-034-02 | Reusable helper forces card visible and surfaces validation message. |
| S-034-03 | All ceremonies wire to helper; invalid responses render message. |
| S-034-04 | Selenium/docs capture invalid scenarios and document the pattern. |

## Test Strategy
- Selenium: run invalid scenarios per ceremony (`:rest-api:test --tests "io.openauth.sim.rest.ui.*OperatorUiSeleniumTest"`).
- REST/UI unit tests: ensure helper toggles result card state.
- Full verification: `./gradlew spotlessApply check`.

## Interface & Contract Catalogue
### Helpers
| ID | Description | Modules |
|----|-------------|---------|
| UI-034-01 | `OperatorConsoleResultViewModel` – exposes `showResultCard` + `validationMessage`. | rest-api |
| UI-034-02 | `operatorConsoleResultHelper.js` – JS hook revealing the card for invalid responses. | static/ui |

## Documentation Deliverables
- Update `docs/2-how-to/use-ocra-operator-ui.md`, knowledge map, roadmap, `_current-session.md` with the unified validation pattern.

## Fixtures & Sample Data
- Reuse existing invalid fixtures per ceremony; no new fixtures added.

## Spec DSL
```
scenarios:
  - id: S-034-01
    focus: baseline-audit
  - id: S-034-02
    focus: helper-implementation
  - id: S-034-03
    focus: ceremony-wiring
  - id: S-034-04
    focus: tests-and-docs
requirements:
  - id: FR-034-01
    maps_to: [S-034-01]
  - id: FR-034-02
    maps_to: [S-034-02]
  - id: FR-034-03
    maps_to: [S-034-03]
  - id: FR-034-04
    maps_to: [S-034-04]
non_functional:
  - id: NFR-034-01
    maps_to: [S-034-02, S-034-03]
  - id: NFR-034-02
    maps_to: [S-034-04]
```
