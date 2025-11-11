# Feature 017 – Operator Console Unification

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/017/plan.md` |
| Linked tasks | `docs/4-architecture/features/017/tasks.md` |
| Roadmap entry | #9 – Operator Console Infrastructure |

## Overview
Deliver a single `/ui/console` entry point that unifies OCRA evaluation and replay workflows, introduces protocol tabs
so future facades (FIDO2/WebAuthn, EMV/CAP) inherit the same scaffolding, and preserves the existing Spring Boot +
Thymeleaf + vanilla JS stack. The console applies the requested dark theme, removes surplus whitespace, and keeps only
OCRA flows interactive while placeholders advertise upcoming facades without exposing incomplete behaviour.

## Clarifications
- 2025-10-03 – Evaluation and replay screens collapse into one console with protocol tabs so operators switch modes in
  place (owner approval).
- 2025-10-03 – FIDO2/WebAuthn and EMV/CAP tabs launch as disabled placeholders signalling upcoming support without
  exposing flows yet (Option B selected).
- 2025-10-03 – The futuristic visual design must rely on the current Thymeleaf + vanilla JS tooling; no new JS/CSS
  dependencies unless expressly approved (owner directive).
- 2025-10-03 – Legacy `/ui/ocra/evaluate` and `/ui/ocra/replay` routes are decommissioned; `/ui/console` becomes the
  sole UI surface (owner directive).
- 2025-10-03 – "Load a sample vector" in the replay tab sits immediately below the mode selector to mirror evaluation
  layout (user directive).
- 2025-10-03 – Replay result cards omit Telemetry ID, Credential Source, Context Fingerprint, and Sanitized fields to
  reduce clutter (user directive).
- 2025-10-03 – Evaluation + replay metadata render one label/value per row; multi-column grids are out (user directive).
- 2025-10-03 – Evaluation result cards drop the Suite row; only OTP + Status remain (user directive).
- 2025-10-04 – Evaluation results no longer show a Sanitized indicator; sanitisation is assumed server-side (user
  directive).
- 2025-10-04 – When no stored credentials exist, surface a manual "Seed sample credentials" control instead of auto
  seeding (Option B selected).
- 2025-10-04 – Seed MapDB with the same canonical suites as inline autofill (one credential per suite) and allow
  repeated seeding without overwriting existing entries (Option B/C selected).
- 2025-10-04 – The seeding action calls a REST endpoint, emits telemetry, and remains visible only in stored-credential
  mode to avoid inline clutter (Option A + user directive).
- 2025-10-04 – Stored credential selector renders directly beneath the replay mode chooser so operators avoid extra
  scrolling (user directive).
- 2025-10-04 – `/ui/console` encodes tab state via query parameters (e.g., `protocol=ocra&tab=replay`) and restores the
  same view on refresh or history navigation (Option A/B selected).
- 2025-10-15 – Shared console styles relocate to `/ui/console/console.css` so no single protocol owns the asset (Option B
  selected).

## Goals
- Reduce UI drift by consolidating all console layouts, JS controllers, and CSS tokens.
- Keep telemetry, docs, and fixtures aligned with the new operator experience.
- Provide guardrails (query params, redirects, seed workflows) so operators land in the right context every time.

## Non-Goals
- N-017-01 – Ship protocol-specific FIDO2/WebAuthn or EMV/CAP business logic beyond disabled previews.
- N-017-02 – Introduce third-party UI frameworks or redesign flows beyond harmonising existing patterns.
- N-017-03 – Implement export/offline tooling for console data (future feature).

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-017-01 | `/ui/console` exposes protocol tabs with OCRA active and future facades disabled by default. | Selenium tests load `/ui/console`, OCRA tab is active, FIDO2/WebAuthn + EMV/CAP tabs show disabled states/tooltips. | Invalid protocol/tab query params fall back to OCRA without errors; history navigation preserves context. | Requesting legacy `/ui/ocra/*` routes returns 404/redirect to `/ui/console`. | `ui.console.navigation` frame logs `protocol`, `tab`, and sanitized query values. | 2025-10-03 owner directive. |
| FR-017-02 | Evaluation and replay forms reside on the same page with shared panels/components. | Switching between Evaluate/Replay toggles inline/stored modes without full-page reload; REST calls succeed. | Missing inputs trigger inline validation + highlighted fields. | Downstream REST errors render as inline failure cards without console crash. | `ocra.evaluate`/`ocra.verify` events include `mode`, `credentialIdHash`, `result`. | 2025-10-03 approvals. |
| FR-017-03 | Apply the dark theme with reduced gutters and responsive layout. | UI snapshot matches dark palette, 120px gutters removed, viewport width respected. | Accessibility tooling confirms focus outlines + keyboard order. | Theme fallback displayed if CSS fails to load, flagged in console logs. | `ui.console.theming` trace logs palette + build hash (debug only). | 2025-10-03 directive. |
| FR-017-04 | Maintain telemetry parity for evaluation/replay actions under the unified console. | Telemetry assertions capture sanitized fields identical to pre-unification events. | Validation paths emit `reasonCode` + sanitized flags on error. | Telemetry adapters drop frames if required metadata is missing and raise QA alarms. | `ocra.evaluate`, `ocra.verify` adapters (TelemetryContracts). | Constitution Principle 3 + 2025-10-03 directive. |
| FR-017-05 | Update operator docs and knowledge map to describe `/ui/console`, tabs, and placeholders. | Docs/roadmap depict new layout + instructions for disabled protocols. | Broken links to legacy routes fixed. | Doc lint fails if screenshots/links stale. | `docs/` change log references spec section. | 2025-10-03 directive. |
| FR-017-06 | Decommission `/ui/ocra/evaluate` + `/ui/ocra/replay`. | Requests redirect to `/ui/console?protocol=ocra`. | Legacy bookmarks log a warning once. | If redirect fails, Selenium test alerts gating release. | `ui.console.redirect` trace includes `fromRoute`. | 2025-10-03 directive. |
| FR-017-07 | Replay tab positions "Load a sample vector" directly under the mode selector. | DOM order matches evaluate tab; spacing consistent. | Mode switching toggles control visibility instantly. | Layout regression causes Selenium diff failure. | `ui.console.layout` trace includes `panel=replay`. | 2025-10-03 directive. |
| FR-017-08 | Replay result cards hide Telemetry ID, Credential Source, Context Fingerprint, Sanitized fields. | Result cards show only status, OTP, and remaining metadata. | Validation ensures removed fields never render even via dev tools. | Snapshot tests fail if hidden fields return. | `ocra.verify` payload no longer includes removed metadata. | 2025-10-03 directive. |
| FR-017-09 | Evaluation + replay metadata render as single label/value rows. | `.result-row` groups appear per field; CSS enforces block layout. | Validation ensures responsive stacking at 1024 px width. | Layout regressions caught via Percy/Selenium diffs. | `ui.console.layout` trace marks release build. | 2025-10-03 directive. |
| FR-017-10 | Evaluation results drop Suite + Sanitized rows, keeping only Status + OTP. | UI snapshots confirm minimal summary. | Validation ensures tests fail if suite appears. | Failure path logs warning + blocks release. | `ocra.evaluate` telemetry unaffected (suite still hashed). | 2025-10-03 directive. |
| FR-017-11 | Stored-mode shows a `Seed sample credentials` button that appends missing suites via REST without overwriting existing data. | Button visible only when stored mode + registry empty; calling endpoint adds canonical suites, updates dropdown, displays success count. | Validation path logs a warning when seeding adds zero rows and renders warning styling. | REST failures show inline error card and avoid duplicate inserts. | `ocra.seed` event records `addedCount`, `canonicalCount`, sanitized IDs. | 2025-10-04 directives. |
| FR-017-12 | `/ui/console` reflects protocol + tab in query params/history, restoring state on refresh, deep-link, and back/forward navigation. | Changing tabs updates `?protocol=&tab=`; reloading reopens same state (including disabled placeholders). | Validation ensures invalid combinations snap back to defaults but log telemetry. | URL sync failure surfaces banner instructing manual reload. | `ui.console.navigation` trace includes `source=history|deeplink`. | 2025-10-04 directive. |
| FR-017-13 | Shared console stylesheet moves to `/ui/console/console.css` and all templates reference it. | Static asset served from neutral path; caching headers updated; CLI/REST/UI share tokens. | Validation ensures asset fingerprint mismatches trigger test failure. | Missing asset results in fallback theme + console warning. | `telemetry.ui.asset` debug trace logs checksum. | 2025-10-15 directive. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-017-01 | Maintain ≤200 ms P95 latency for OCRA submissions despite UI unification. | Console must remain responsive during drills. | Local + CI Selenium metrics <=200 ms for evaluation/replay POSTs. | REST controllers, application services, persistence. | Constitution Principle 3. |
| NFR-017-02 | Preserve accessibility (keyboard nav, focus outlines, aria labels). | Dark theme must remain usable. | axe-core + manual testing on evaluate/replay tabs. | UI templates, CSS tokens. | 2025-10-03 directive. |
| NFR-017-03 | Responsive layout avoids horizontal scroll at ≥1024 px. | Operators demo on large monitors/tablets. | Selenium screenshot diff + CSS audits. | CSS grid, container queries. | 2025-10-03 directive. |
| NFR-017-04 | Extend automated coverage (Selenium + REST) for unified flows. | Regression safety post unification. | `./gradlew :rest-api:test spotlessApply check` stays green with new suites. | Gradle, Selenium grid, MockMvc. | Constitution Principle 3. |

## UI / Interaction Mock-ups
```
+--------------------------------------------------------------+
| Operator Console                                             |
| [ OCRA ]  [ FIDO2/WebAuthn (disabled) ]  [ EMV/CAP (disabled) ]
+--------------------------------------------------------------+
| Tabs: [ Evaluate ] [ Replay ]                                |
|                                                              |
| Evaluate Panel (inline/stored toggle)                        |
| ----------------------------------------------------------   |
| | challenge | secret | result card (OTP + status)       |    |
| ----------------------------------------------------------   |
|                                                              |
| Replay Panel (shown when tab=Replay)                         |
| ----------------------------------------------------------   |
| | Mode toggle | "Load a sample vector" button | ...     |   |
| | Credential dropdown                                      | |
| | Seed sample credentials (visible when stored+empty)      | |
| ----------------------------------------------------------   |
| Result rows (single label/value per line)                    |
+--------------------------------------------------------------+
```

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-017-01 | `/ui/console` entry point with protocol tabs, dark theme, and disabled placeholders shipped. |
| S-017-02 | Evaluation + replay flows, result card layout, and telemetry behave inside the unified console. |
| S-017-03 | Seed sample credentials flow appears only when needed, calls REST, and surfaces status hints. |
| S-017-04 | Query-parameter routing/history plus redirects keep `/ui/console` as the sole entry point. |
| S-017-05 | Shared console assets + documentation reflect the new architecture. |

## Test Strategy
- **Core:** No direct changes; regression guarded through existing OCRA execution tests consumed by the console.
- **Application:** `OcraEvaluationApplicationService`/`OcraVerificationApplicationService` telemetry tests ensure
  adapters still sanitize metadata consumed by the UI.
- **REST:** `OcraCredentialDirectoryControllerTest`, `OcraOperatorConsoleControllerTest`, and seed endpoint tests cover
  redirects, REST payloads, and telemetry frames.
- **CLI:** No CLI scope; smoke tests ensure CLI still works after credential seeding via shared store.
- **UI (JS/Selenium):** `OperatorConsoleUnificationSeleniumTest` drives tabs, query params, seeding controls, and result
  layout diffs before implementation.
- **Docs/Contracts:** Run `OPENAPI_SNAPSHOT_WRITE=true ./gradlew :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`
  whenever UI metadata touches REST payloads; update docs/how-to per FR-017-05.

## Interface & Contract Catalogue
### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-017-01 | `OperatorConsoleState` encapsulates active protocol, tab, mode, and seeded credential availability. | rest-api (UI templates), ui JS |
| DO-017-02 | `SeedSampleCredentialsRequest`/`SeedResult` capture canonical credential IDs, added counts, and telemetry ID. | application, rest-api |
| DO-017-03 | `OperatorConsoleResultRow` model normalises label/value metadata for evaluation + replay cards. | rest-api, ui |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-017-01 | UI GET `/ui/console` | Serves unified console template + JS bundle. | Accepts `protocol` + `tab` query params. |
| API-017-02 | REST GET `/api/v1/ocra/credentials` | Lists stored OCRA credentials for dropdown population. | Filters for `CredentialType.OATH_OCRA`. |
| API-017-03 | REST POST `/api/v1/ocra/credentials/seed` | Idempotently loads canonical suites, returning counts + IDs. | Emits `ocra.seed` telemetry, only available to UI button. |
| API-017-04 | REST GET `/api/v1/ocra/credentials/{id}/sample` | Provides preset data for replay sample loader. | Enables "Load a sample vector" ordering requirement. |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-017-01 | — | No CLI additions; existing OCRA CLI consumes seeded credentials indirectly. |

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|-----------|---------------------------|
| TE-017-01 | `ocra.evaluate` | `mode`, `credentialIdHash`, `status`, `reasonCode`, `sanitized=true`. |
| TE-017-02 | `ocra.verify` | `mode`, `credentialIdHash`, `status`, `reasonCode`, `sanitized=true`. |
| TE-017-03 | `ocra.seed` | `addedCount`, `canonicalCount`, `credentialIdsHash`, `source=operator-console`. |
| TE-017-04 | `ui.console.navigation` | `protocol`, `tab`, `source (deeplink/history/manual)`, sanitized query snapshot. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-017-01 | `docs/test-vectors/ocra/operator-samples.json` | Canonical OCRA sample definitions shared by inline presets and seeding. |
| FX-017-02 | `rest-api/src/main/resources/templates/ui/console/` | Thymeleaf fragments for unified console layout. |
| FX-017-03 | `rest-api/src/main/resources/static/ui/console/console.css` | Dark theme stylesheet shared across tabs. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| UI-017-01 | OCRA Evaluate active tab | Default view; inline/stored toggle available; shows OTP results with single-row metadata. |
| UI-017-02 | OCRA Replay stored mode with seeding control | Stored mode selected + registry empty; seeding button + warning hint visible. |
| UI-017-03 | Disabled FIDO2/WebAuthn and EMV/CAP tabs | Tabs render disabled message but preserve layout + query parameters for future enablement. |

## Telemetry & Observability
- `TelemetryContracts.ocraEvaluationAdapter()` and `.ocraVerificationAdapter()` continue emitting sanitized frames; no new
  event names introduced for the UI.
- `TelemetryContracts.ocraSeedingAdapter()` now records operator UI invocations with `source=operator-console` and
  `addedCount`/`canonicalCount` fields for dashboards.
- UI navigation traces log protocol/tab transitions along with query-parameter provenance so console history regressions
  are diagnosable without leaking credential data.
- Verbose trace builder remains unchanged; evaluation/replay flows reuse existing trace IDs in result cards.

## Documentation Deliverables
- Refresh operator how-to guides (`docs/2-how-to/use-hotp-operator-ui.md`, `docs/2-how-to/use-totp-operator-ui.md`) to
  reference `/ui/console`, seeding controls, and disabled tabs.
- Update `docs/1-concepts/README.md` and `docs/4-architecture/knowledge-map.md` to describe the unified console shell.
- Note the migration in `docs/migration_plan.md` and maintain `docs/_current-session.md` for traceability.

## Fixtures & Sample Data
- `docs/test-vectors/ocra/operator-samples.json` (canonical suite definitions for inline + seeding flows).
- `rest-api/src/main/resources/static/ui/console/console.css` (dark theme stylesheet referenced by FR-017-13).
- `rest-api/src/main/resources/templates/ui/console/console.html` (shared template/partials referenced by FR-017-01).

## Spec DSL
```
domain_objects:
  - id: DO-017-01
    name: OperatorConsoleState
    fields:
      - name: protocol
        type: enum[ocra,fido2,emv]
      - name: tab
        type: enum[evaluate,replay]
      - name: mode
        type: enum[inline,stored]
  - id: DO-017-02
    name: SeedSampleCredentialsRequest
    fields:
      - name: canonicalSuites
        type: list<string>
        constraints: "canonical list from OcraOperatorSampleData"
routes:
  - id: API-017-01
    method: GET
    path: /ui/console
  - id: API-017-02
    method: GET
    path: /api/v1/ocra/credentials
  - id: API-017-03
    method: POST
    path: /api/v1/ocra/credentials/seed
  - id: API-017-04
    method: GET
    path: /api/v1/ocra/credentials/{credentialId}/sample
telemetry_events:
  - id: TE-017-01
    event: ocra.evaluate
  - id: TE-017-02
    event: ocra.verify
  - id: TE-017-03
    event: ocra.seed
  - id: TE-017-04
    event: ui.console.navigation
fixtures:
  - id: FX-017-01
    path: docs/test-vectors/ocra/operator-samples.json
  - id: FX-017-02
    path: rest-api/src/main/resources/static/ui/console/console.css
ui_states:
  - id: UI-017-01
    description: OCRA evaluate tab with inline/stored toggle and OTP result card
  - id: UI-017-02
    description: OCRA replay tab with stored mode plus seeding control
  - id: UI-017-03
    description: Disabled future protocol tabs retaining layout and query params
```

## Appendix
_Not required for this feature._
