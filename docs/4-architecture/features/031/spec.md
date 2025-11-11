# Feature 031 – Legacy Entry-Point Removal

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/031/plan.md` |
| Linked tasks | `docs/4-architecture/features/031/tasks.md` |
| Roadmap entry | #31 – Operator Console Simplification |

## Overview
Legacy CLI/JS compatibility shims kept older tooling and bookmarks working but now block simplifying the simulator. This
feature retires the Java and JavaScript entry points so only the canonical telemetry adapters, router state keys, and
modern Fetch-based APIs remain. Operators and automated tests must rely on the interfaces captured in Features 017, 024,
026, and 027.

## Clarifications
- 2025-10-19 – REST controller contracts stay as-is; optional JSON fields are not deprecated.
- 2025-10-19 – Completion: T3107/T3108 aligned WebAuthn presets with W3C identifiers, refreshed documentation, and
  re-ran the analysis gate to confirm a green baseline after the removals.

## Goals
- G-031-01 – Remove CLI telemetry fallbacks (`legacyEmit`) and emit exclusively through `TelemetryContracts` adapters.
- G-031-02 – Delete operator-console routing/query-state shims (`__openauth*`, legacy query params) so navigation relies
  on canonical `protocol/tab/mode` keys.
- G-031-03 – Drop XMLHttpRequest/fetch polyfills and FIDO2 `legacySetMode`, relying on the standard Fetch helpers and
  canonical router API.
- G-031-04 – Prune WebAuthn legacy presets, refresh docs/tests/knowledge artefacts, and log the change.

## Non-Goals
- N-031-01 – Tightening REST or CLI payload validation beyond the legacy entry points listed here.
- N-031-02 – Removing other test-only flags (for example `openauth.sim.persistence.skip-upgrade`).
- N-031-03 – Introducing new telemetry schemas or additional protocol routes.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-031-01 | CLI flows emit telemetry exclusively through `TelemetryContracts` adapters; `legacyEmit` is removed (S-031-01). | CLI commands call adapters-only emission; tests assert structured frames. | CLI unit/integration tests cover evaluate/replay flows with adapter events only. | Any CLI path still calls `legacyEmit` or logs text frames. | Telemetry events unchanged; only adapters are invoked. | Clarifications 2025-10-19. |
| FR-031-02 | Operator console routing/query-state shims (`__openauth*`, legacy params) are deleted (S-031-02). | UI router parses canonical `protocol/tab/mode`, Selenium tests navigate using those keys. | Front-end unit tests + Selenium suites assert canonical navigation. | Legacy aliases still work or canonical keys regress. | Browser history events still emit canonical router states. | G-031-02. |
| FR-031-03 | Console networking/ceremony helpers rely solely on Fetch + canonical APIs (S-031-03). | FIDO2 console uses `setMode` only; HtmlUnit harness enables fetch polyfill; XMLHttpRequest fallbacks removed. | Selenium + JS unit tests verify fetch usage and canonical ceremony helpers. | Legacy `legacySetMode` or XHR fallbacks remain. | Telemetry unaffected; operator console logs canonical events only. | G-031-03. |
| FR-031-04 | WebAuthn legacy presets removed, docs/how-tos updated, analysis gate re-run (S-031-04). | Generator presets match W3C fixture IDs (`packed-es256` etc.); docs/knowledge map updated; gate executed. | CLI/REST/UI tests reference canonical preset IDs; docs diff reviewed. | Legacy presets accessible or documentation references them. | Telemetry unchanged; samples now match W3C naming. | Clarifications 2025-10-19.

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-031-01 | Maintain Fetch-only networking support in operator console automated tests. | Ensure test harness mirrors production browsers. | Selenium + HtmlUnit harnesses confirm fetch polyfill is enabled. | UI harnesses, HtmlUnit, Selenium suites. | G-031-03. |
| NFR-031-02 | Keep roadmap/knowledge map/how-to docs aligned with the removal timeline. | Governance traceability. | Docs review after feature completes. | docs hierarchy. | G-031-04. |
| NFR-031-03 | Preserve green Gradle gate (`:rest-api:test` + `spotlessApply check`). | Constitution test-first rule. | Recorded command log. | Gradle, REST/UI modules. | Project constitution. |

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-031-01 | CLI telemetry emits only structured adapter events; legacy `legacyEmit` removed. |
| S-031-02 | Operator console routing/query-state shims removed; canonical query params only. |
| S-031-03 | Console networking + FIDO2 ceremony helpers rely on Fetch + canonical APIs, no XHR/legacy bridges. |
| S-031-04 | WebAuthn legacy presets removed; docs/knowledge artefacts updated and analysis gate re-run. |

## Test Strategy
- CLI: update telemetry tests to assert adapter-only events; rerun CLI suites.
- UI/Selenium: navigate using canonical params, ensure Fetch polyfill available, rerun `:rest-api:test --tests "*OperatorUi*"`.
- REST/Application: ensure WebAuthn generator fixtures + telemetry tests reference canonical presets.
- Full verification: `./gradlew --no-daemon :rest-api:test` (targeted suites) and `./gradlew spotlessApply check`.

## Interface & Contract Catalogue
### CLI Commands
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-031-01 | `ocra evaluate/verify` | Emits telemetry via adapters only; legacy text branch removed. |

### UI Modules
| ID | Module | Notes |
|----|--------|-------|
| UI-031-01 | `rest-api/src/main/resources/static/ui/**/console.js` | Router parses canonical `protocol/tab/mode`; no `__openauth*` globals. |
| UI-031-02 | `rest-api/src/main/resources/static/ui/fido2/console.js` | Exposes only canonical `setMode` API. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-031-01 | `application/src/main/resources/webauthn/generator-samples.json` | Presets named after W3C fixture IDs (legacy entries removed). |

## Documentation Deliverables
- Update CLI telemetry reference, operator how-to guides, roadmap, knowledge map, and `_current-session.md` to describe the unified flow.
- Note fetch polyfill expectations in Selenium harness docs.

## Fixtures & Sample Data
- See FX-031-01; no additional fixtures.

## Spec DSL
```
scenarios:
  - id: S-031-01
    focus: cli-telemetry
  - id: S-031-02
    focus: router-state
  - id: S-031-03
    focus: networking-helpers
  - id: S-031-04
    focus: docs-presets
requirements:
  - id: FR-031-01
    maps_to: [S-031-01]
  - id: FR-031-02
    maps_to: [S-031-02]
  - id: FR-031-03
    maps_to: [S-031-03]
  - id: FR-031-04
    maps_to: [S-031-04]
non_functional:
  - id: NFR-031-01
    maps_to: [S-031-03]
  - id: NFR-031-02
    maps_to: [S-031-04]
  - id: NFR-031-03
    maps_to: [S-031-01, S-031-03]
```
