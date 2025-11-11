# Feature 035 – Evaluate & Replay Audit Tracing

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/035/plan.md` |
| Linked tasks | `docs/4-architecture/features/035/tasks.md` |
| Roadmap entry | #31 – Operator Console Simplification |

## Overview
Provide per-request verbose traces for every credential evaluation/replay/attestation workflow (HOTP, TOTP, OCRA,
WebAuthn) so operators can inspect each cryptographic step. Verbose mode is opt-in via CLI/REST/UI toggles; traces remain
transient and include full educational detail (intermediate buffers, signatures, hashes) without altering default
behaviour.

## Clarifications
- 2025-10-22 – Scope covers all current and future credential protocols; initial delivery must support HOTP, TOTP, OCRA,
  WebAuthn evaluate/replay/attest flows.
- 2025-10-22 – CLI flag (`--verbose`), REST request field, and UI control must surface identical trace payloads. UI renders
  traces in a terminal-style panel hidden by default.
- 2025-10-22 – Traces include unredacted cryptographic data for instructional value and stay ephemeral per request; no
  global verbose switch allowed.
- 2025-10-25 – Verbose trace panel clears whenever operators change tabs/modes to keep traces scoped to the originating
  request.

## Goals
- G-035-01 – Design an immutable verbose-trace model shared across protocols.
- G-035-02 – Propagate per-request verbose toggles through application services without affecting default flows.
- G-035-03 – Surface traces across CLI/REST responses and the operator UI panel with identical content.
- G-035-04 – Extend WebAuthn traces with canonical RP IDs, signature metadata, and policy markers.
- G-035-05 – Document the verbose mode (how-to, roadmap, knowledge map) and capture analysis-gate evidence.

## Non-Goals
- N-035-01 – Persisting traces beyond the originating request.
- N-035-02 – Introducing a global verbose toggle.
- N-035-03 – Changing telemetry schemas outside the verbose-trace payload.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-035-01 | Immutable verbose-trace model emits ordered operations + steps for every supported protocol (S-035-01). | Core builders create deterministic verbose traces with `operation`, `metadata`, `steps[]`. | `:core:test` suites cover HOTP/TOTP/OCRA/WebAuthn traces. | Missing steps/order, failing tests. | No telemetry change (traces returned inline). | Clarifications 2025-10-22. |
| FR-035-02 | Application services propagate verbose toggles and populate traces for HOTP/TOTP/OCRA/WebAuthn (S-035-02). | `VerboseTrace` objects returned when toggles enabled; default behaviour unaffected. | `:application:test --tests "*VerboseTraceTest"`. | Non-verbose requests polluted or verbose traces missing. | None. | G-035-02. |
| FR-035-03 | CLI/REST/operator UI surface the same trace payload via per-request toggles (S-035-03, S-035-04). | CLI stdout, REST JSON, and UI trace panel render identical steps; UI panel collapses by default. | `:cli:test --tests "*VerboseTraceTest"`, `:rest-api:test --tests "*Verb*Trace*"`, Selenium invalid/verbose scenarios. | Facades diverge or UI fails to show traces. | None. | Clarifications 2025-10-22. |
| FR-035-04 | WebAuthn traces include canonical RP IDs, match indicators, signature metadata, flag maps, and policy notes (S-035-05). | Traces emit `rpId.canonical`, `rpIdHash.match`, `flags.bits.*`, `uv.policy.*`, signature inspectors. | WebAuthn verbose tests across core/application/CLI/REST/UI. | Missing metadata or failing assertions. | None. | Clarifications 2025-10-24/25. |
| FR-035-05 | Documentation/knowledge artefacts describe verbose tracing; analysis gate rerun with CLI/REST/UI coverage (S-035-06). | How-to guides, knowledge map, roadmap mention verbose mode; analysis gate checklist recorded. | Docs diff + `spotlessApply check` + Selenium logs. | Docs stale or gate not executed. | None. | G-035-05. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-035-01 | Preserve transient behaviour: no storage/log persistence of traces. | Security posture. | Code review + tests ensure traces only in responses. | Application, REST, CLI, UI. | Clarifications 2025-10-22. |
| NFR-035-02 | Maintain green pipeline (`./gradlew :application:test :cli:test :rest-api:test :core:test spotlessApply check`). | Constitution QA rule. | Recorded command logs. | All JVM modules. | Project constitution. |
| NFR-035-03 | Provide deterministic trace ordering for regression tests. | Stable docs/tests. | Snapshot comparisons in verbose trace tests. | Core/application modules. | G-035-01. |

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-035-01 | Trace model/builders emit ordered, immutable verbose traces with full payloads. |
| S-035-02 | Application services propagate verbose toggles across HOTP/TOTP/OCRA/WebAuthn flows. |
| S-035-03 | CLI/REST facades respect verbose flags and surface identical trace payloads. |
| S-035-04 | Operator UI renders traces in a terminal-style panel with copy/download/toggle controls. |
| S-035-05 | WebAuthn traces include canonical RP IDs, signature metadata, flag maps, and policy notes. |
| S-035-06 | Documentation/knowledge artefacts capture verbose tracing behaviour; analysis gate rerun.

## Test Strategy
- `./gradlew --no-daemon :core:test --tests "*VerboseTrace*"`
- `./gradlew --no-daemon :application:test --tests "*VerboseTraceTest"`
- `./gradlew --no-daemon :cli:test --tests "*VerboseTraceTest"`
- `./gradlew --no-daemon :rest-api:test --tests "*VerboseTrace*"`
- Selenium suites (`io.openauth.sim.rest.ui.*OperatorUiSeleniumTest`) for UI panel.
- Full pipeline `./gradlew --no-daemon spotlessApply check`.

## Interface & Contract Catalogue
### Trace Model
| ID | Description | Modules |
|----|-------------|---------|
| TM-035-01 | `VerboseTrace` (operation metadata + ordered steps). | core, application |
| TM-035-02 | `VerboseTraceStep` (name, metadata, spec anchor). | core |

### Facade Controls
| ID | Component | Notes |
|----|-----------|-------|
| CLI-035-01 | `--verbose` flag on CLI evaluate/replay commands. | cli |
| REST-035-01 | `verboseTrace=true` request field; trace payload returned in JSON under `trace`. | rest-api |
| UI-035-01 | Operator console toggle + panel (`verbose-trace-panel`). | rest-api UI |

## Documentation Deliverables
- Update CLI/REST/operator UI how-to guides, roadmap, knowledge map, `_current-session.md`, and migration plan with verbose tracing behaviour.

## Spec DSL
```
scenarios:
  - id: S-035-01
    focus: trace-model
  - id: S-035-02
    focus: application-plumbing
  - id: S-035-03
    focus: cli-rest-facades
  - id: S-035-04
    focus: operator-ui
  - id: S-035-05
    focus: webauthn-enrichment
  - id: S-035-06
    focus: documentation
requirements:
  - id: FR-035-01
    maps_to: [S-035-01]
  - id: FR-035-02
    maps_to: [S-035-02]
  - id: FR-035-03
    maps_to: [S-035-03, S-035-04]
  - id: FR-035-04
    maps_to: [S-035-05]
  - id: FR-035-05
    maps_to: [S-035-06]
non_functional:
  - id: NFR-035-01
    maps_to: [S-035-02, S-035-03]
  - id: NFR-035-02
    maps_to: [S-035-06]
  - id: NFR-035-03
    maps_to: [S-035-01]
```
