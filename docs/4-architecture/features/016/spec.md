# Feature 016 – OCRA UI Replay

| Field | Value |
|-------|-------|
| Status | In Progress |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/016/plan.md` |
| Linked tasks | `docs/4-architecture/features/016/tasks.md` |
| Roadmap entry | #16 |

## Overview
Extend the operator console inside the `rest-api` module with a replay-focused workspace so auditors can submit historical OCRA OTP payloads without leaving the UI. The replay panel consumes the production `/api/v1/ocra/verify` endpoint, supports both stored-credential and inline-secret flows, auto-fills curated presets, and mirrors the telemetry emitted by CLI/REST facades so every verification is traceable across tools.

## Clarifications
- 2025-10-04 – Inline replay presets continue to auto-fill immediately when selected; no separate button is required (user preference).
- 2025-10-03 – Replay inline mode reuses the evaluation console's inline sample presets so operators can auto-fill suite/secret/context data (user selected Option A). Load-a-sample stays inline-only to match the evaluation console.
- 2025-10-03 – Selecting a replay inline preset should populate the expected OTP alongside other context fields so auditors can submit immediately (user request).
- 2025-10-03 – Replay result card should mirror the evaluation console aesthetics: highlight status with visual emphasis and present telemetry as labeled rows for readability (user request).
- 2025-10-04 – Replay result cards may omit the mode row; reason code and outcome remain sufficient for operator context (user directive).
- 2025-10-04 – Replay Selenium coverage should fall back to programmatic credential seeding when the bundled sample MapDB cannot be copied; the test must continue rather than failing the build (owner selected Option B).
- 2025-10-03 – Operator UI telemetry posts to `/ui/console/replay/telemetry`, feeding the shared `TelemetryContracts.ocraVerificationAdapter` with `origin=ui` and replay context (mode, outcome, fingerprint).
- 2025-10-03 – Replay REST responses will surface a `mode` attribute (stored vs inline) in metadata and telemetry events so UI instrumentation can log mode-specific outcomes (user accepted).
- 2025-10-03 – Task T1602 remains focused on inline replay Selenium coverage; stored replay navigation stays with earlier increments (user selected Option B).
- 2025-10-03 – Replay scope covers both stored-credential and inline-secret verification flows in the first increment (user selected Option A).
- 2025-10-03 – The operator UI gains a dedicated replay panel rendered from `ui/ocra/replay.html` and toggled via the OCRA protocol tab instead of overloading the existing evaluation fragment (user selected Option B).
- 2025-10-07 – Inline replay preset hints must read “Selecting a preset auto-fills the inline fields with illustrative data” to keep messaging consistent with HOTP (user directive).
- 2025-10-15 – Stored replay selections auto-fill curated sample data immediately; the “Load sample data” button is removed so credential picks hydrate OTP context without extra clicks (user chose Option B).

## Goals
- Provide a Replay panel in the operator console with parity across stored and inline flows while reusing the production `/api/v1/ocra/verify` contract.
- Maintain audit parity with CLI/REST: every replay emits sanitized telemetry that captures `mode`, `outcome`, and hashed context fingerprints via `TelemetryContracts.ocraVerificationAdapter`.
- Preserve accessibility, preset guidance, and validation copy so auditors can rely on UI hints rather than consulting external docs.

## Non-Goals
- Does **not** change the behaviour of evaluation flows beyond shared components.
- Does **not** introduce replay flows for non-OCRA protocols.
- Does **not** add new credential lifecycle endpoints; persistence stays with Feature 009 and CLI tooling.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-016-01 | Provide a Replay entry point under the operator console’s OCRA tab with mode toggles, contextual copy, and CSRF-protected forms. | Selecting “Replay” reveals the replay fragment, focuses mode chips, and announces the state via ARIA labels; stored/inline sections expand accordingly. | Selenium tests assert tab focus order, disabled buttons when no credential is chosen, and CSRF tokens on every form submission. | Missing fragment, toggle wiring, or CSRF tokens trigger 404/403 responses and fail `OperatorConsoleReplaySeleniumTest`. | `rest.ui.ocra.page` frames include `panel=replay`, `activeMode`, and hashed session IDs for analytics. | Goals; Clarifications 2025-10-03/04. |
| FR-016-02 | Stored replay mode fetches credential inventory, auto-fills curated context/OTP data on selection, and POSTs to `/api/v1/ocra/verify`. | Credential dropdown renders labels sourced from `/api/v1/ocra/credentials`; selection hydrates payload preview and form inputs, then submits stored replay JSON. | Inline validation prevents submission when a credential is not selected or required context fields are cleared; Selenium verifies dropdown disabled states. | REST validation errors surface sanitized `reasonCode` strings inline; credential fetch failures display banner errors and keep prior inputs. | `ui.console.replay` events emit `mode=stored`, `credentialSource=stored`, `contextFingerprint`, `outcome`, and `telemetryIdHash`. | Clarifications 2025-10-03/04/15; Scenario S-016-02. |
| FR-016-03 | Inline replay mode accepts suite descriptor, shared secret (HEX/Base32), OTP, and context inputs, plus preset auto-fill with expected OTP values. | Preset selector lists curated policies (sourced from `docs/ocra_validation_vectors.json`); choosing a preset populates suite/secret/context/OTP fields and enables submit. | Client-side validation enforces mutually exclusive HEX/Base32 inputs, ensures OTP digits match suite, and reveals inline errors for missing challenge/session/timestamp fields. | Invalid inputs produce descriptive inline messages and keep prior values; REST rejections bubble `reasonCode` and `status=MISMATCH` cards with sanitized traces. | `ui.console.replay` events emit `mode=inline`, `presetKey`, `otpHash`, and `contextFingerprint`; verbose trace remains opt-in. | Clarifications 2025-10-03/07; Scenario S-016-03. |
| FR-016-04 | Replay results render match/mismatch/validation outcomes with console-friendly styling and telemetry identifiers. | Result card highlights status badge, shows `reasonCode`, `outcome`, `telemetryId`, and includes copy buttons identical to evaluation cards. | Selenium asserts badge colour contrast, telemetry row labels, and copy button behaviours under success, mismatch, and validation error paths. | Missing badge updates or telemetry rows fail Selenium assertions; telemetry/copy actions fall back to a warning toast with sanitized messaging. | `rest.ocra.verify` frames already include `telemetryId`, `mode`, `status`, `reasonCode`, `durationMs`; UI displays those values verbatim without secrets. | Clarifications 2025-10-03/04; Scenario S-016-01. |
| FR-016-05 | UI telemetry logging extends `TelemetryContracts.ocraVerificationAdapter` so every replay is traceable back to CLI/REST events without exposing secrets. | After each submission, the UI POSTs `/ui/console/replay/telemetry` containing `mode`, `credentialSource`, `outcome`, `contextFingerprint`, `otpHash`, and sanitised request metadata; logs include `origin=ui`. | Unit tests for `OperatorConsoleTelemetryLogger` verify hashing, reason-code normalization, and field coverage; MockMvc tests assert the endpoint rejects missing hashes. | Dropped fields, plaintext OTP/secret leaks, or adapter mismatches fail tests and are blocked by governance. | `ui.console.replay` events hash OTP/context using SHA-256 and rely on the shared adapter for redaction settings. | Clarifications 2025-10-03; Scenario S-016-04. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-016-01 | Replay submissions complete within 200 ms P95 locally, matching REST replay responsiveness. | Auditors expect UI parity with CLI/REST latencies when triaging credentials. | `OperatorConsoleReplaySeleniumTest` captures duration metrics; `rest.ocra.verify` telemetry exports `durationMs` for comparison. | `/api/v1/ocra/verify`, MapDB credential store, Selenium harness clock sync. | Goals; Clarifications 2025-10-03. |
| NFR-016-02 | Replay UI components meet WCAG 2.1 AA (contrast, focus order, keyboard-only operation). | Console accessibility parity is mandatory per constitution and Feature 006 precedents. | Axe/Selenium audits assert tab order, landmark labels, and `status` role usage on result cards. | `ui/ocra/replay.html`, shared CSS, Selenium accessibility helpers. | Clarifications 2025-10-03/04. |
| NFR-016-03 | Telemetry parity across facades (`ui.console.replay`, `rest.ocra.verify`, CLI events). | Incident response relies on correlating telemetry IDs regardless of entry point. | Unit/MockMvc tests ensure adapter fields match CLI/REST schemas; docs include updated telemetry snapshots. | `TelemetryContracts.ocraVerificationAdapter`, `OperatorConsoleTelemetryLogger`, docs/3-reference snapshots. | Knowledge map + Clarifications 2025-10-03. |
| NFR-016-04 | Test-first discipline: Selenium, MockMvc, and telemetry unit tests fail before implementation and remain in CI. | Constitution Principle 3 mandates tests precede behaviour changes. | Plan tasks require staging failing tests (`OperatorConsoleReplaySeleniumTest`, `OcraVerificationEndpointTest`) before template/JS code, then rerunning `./gradlew spotlessApply check`. | Gradle UI/system suites, MapDB fixtures, git hooks. | Plan increments R1602–R1615; Clarifications 2025-10-04. |

## UI / Interaction Mock-ups
```
+---------------------------------------------------------------+
|  OCRA › Replay                                                |
+-----------------------+-------------------------------+-------+
| Mode selector         | Stored Credential             | Trace |
|  (• Stored  ○ Inline) | [Credential ▾]  [Auto-filled] | toggle|
|                       | Context preview + OTP badge   | ctrl  |
+-----------------------+-------------------------------+-------+
| Inline parameters (shown when Inline mode active)             |
| [Preset ▾]  [Suite input]  [Secret HEX/Base32 toggle]        |
| [Challenge ] [Session ] [Counter ] [Timestamp ] [OTP ]       |
+--------------------------------------------------------------+
| Result card: Status badge | Reason code | Outcome | Telemetry|
| Copy buttons + verbose trace link (shared dock)              |
+--------------------------------------------------------------+
```

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-016-01 | Replay navigation toggles between stored and inline panels, preserves accessibility hints, and mirrors evaluation card styling. |
| S-016-02 | Stored-mode submissions auto-fill curated payloads, call `/api/v1/ocra/verify`, and display sanitized REST feedback. |
| S-016-03 | Inline-mode presets hydrate suite/secret/context/OTP fields, enforce validation, and surface errors without leaking secrets. |
| S-016-04 | Telemetry POST `/ui/console/replay/telemetry` emits sanitized frames (`mode`, `outcome`, `contextFingerprint`, `telemetryId`) and documentation/how-to entries describe the new fields. |

## Test Strategy
- **Core:** `OcraReplayVerifierTest` (core module) remains the oracle for stored/inline comparisons; replay UI work reuses these helpers without mutating core primitives.
- **Application:** `OcraVerificationApplicationService` tests ensure metadata.mode, credential source, and telemetry fields are populated before UI reads them; failures here block UI regressions.
- **REST:** `OcraVerificationEndpointTest` plus WebMvc tests for `/ui/console/replay/telemetry` validate payload wiring, hashed telemetry, and error propagation for stored vs inline flows.
- **CLI:** Existing `ocra verify` command tests serve as behavioural parity references; spec requires verifying UI behaviour against CLI fixtures during self-review.
- **UI (JS/Selenium):** `OperatorConsoleReplaySeleniumTest` covers navigation, stored auto-fill, inline presets, validation messaging, telemetry rendering, and fallback credential seeding when MapDB copies fail.
- **Docs/Contracts:** `docs/2-how-to/use-ocra-operator-ui.md` + telemetry snapshot updates describe replay logging; OpenAPI snapshots already cover `/api/v1/ocra/verify`, so this feature only appends console documentation.

## Interface & Contract Catalogue
### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-016-01 | `OcraVerificationRequest` – canonical replay payload containing OTP, credential reference, and normalized context. | rest-api, application |
| DO-016-02 | `OperatorConsoleReplayEventRequest` – UI telemetry submission (`mode`, `credentialSource`, `contextFingerprint`, `otpHash`, `outcome`). | rest-api |
| DO-016-03 | `OcraPolicyPreset` entries with suite/secret/context/OTP values used by inline replay presets. | docs, rest-api |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-016-01 | GET `/ui/console` (OCRA tab) | Renders evaluation + replay fragments with mode toggles. | Provided by `OperatorConsoleController`; Replay fragment lives in `ui/ocra/replay.html` and is hidden until the Replay chip is active. |
| API-016-02 | POST `/api/v1/ocra/verify` | Replays stored/inline payloads and returns match/mismatch metadata. | Shared with CLI/REST; UI trusts metadata.mode/outcome/telemetryId. |
| API-016-03 | GET `/api/v1/ocra/credentials` + `/api/v1/ocra/credentials/{id}/sample` | Supplies stored credential inventory and curated context/OTP presets. | Stored selections auto-fill using the sample response; failures fall back to programmatic seeding. |
| API-016-04 | POST `/ui/console/replay/telemetry` | Logs sanitized replay metadata via `OperatorConsoleTelemetryLogger`. | Accepts `OperatorConsoleReplayEventRequest`; rejects plaintext secrets. |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-016-01 | `./bin/ocra verify --stored/--inline …` | Reference implementation for replay semantics; UI must display the same outcomes, reason codes, and telemetry IDs emitted by this command. |

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|-----------|---------------------------|
| TE-016-01 | `ui.console.replay` | `telemetryId`, `origin=ui`, `mode`, `credentialSource`, `outcome`, `reasonCode`, `contextFingerprint`, `otpHash` (SHA-256, Base64URL), `durationMs`, `sanitized=true`. |
| TE-016-02 | `rest.ocra.verify` | REST controller emits `telemetryId`, `mode`, `status`, `reasonCode`, `durationMs`, and verbose-trace metadata; UI result card mirrors these values and never logs raw OTPs/secrets. |
| TE-016-03 | `rest.ui.ocra.page` | Navigation event for replay panel containing `panel=replay`, `activeMode`, and hashed session IDs to correlate tab usage. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-016-01 | `docs/ocra_validation_vectors.json` | Canonical inline preset definitions with suite/secret/context/OTP pairs reused by replay auto-fill. |
| FX-016-02 | `data/credentials.db` (MapDB) | Bundled credential store containing curated replay-ready credentials for stored mode + system tests. |
| FX-016-03 | `rest-api/src/main/resources/templates/ui/ocra/replay.html` | Thymeleaf template (plus embedded JS) driving the replay form, result card, and preset scripts referenced by Selenium tests. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| UI-016-01 | Replay mode – Stored credential | Replay chip active + stored toggle selected; dropdown + preview shown, inline panel collapsed. |
| UI-016-02 | Replay mode – Inline presets | Replay chip active + inline toggle selected; preset selector, secret toolbar, and advanced context inputs visible. |
| UI-016-03 | Validation messaging | Any missing/invalid context results in inline hints bound to the offending field without clearing existing data. |
| UI-016-04 | Result card + telemetry grid | After submission, badges, telemetry rows, and copy helpers render with sanitized IDs and hashed OTP metadata. |

## Telemetry & Observability
Replay submissions emit `ui.console.replay` frames through `TelemetryContracts.ocraVerificationAdapter`, adding `origin=ui` plus hashed OTP/context fingerprints. The REST layer simultaneously emits `rest.ocra.verify` frames; telemetry IDs link UI result cards to backend logs and CLI runs. Verbose traces remain off by default in the UI—operators rely on the shared trace dock or REST/CLI verbose options. Loggers reject plaintext OTPs and secrets; unit tests exercise both success and failure branches to prevent regressions.

## Documentation Deliverables
- Update `docs/2-how-to/use-ocra-operator-ui.md` and `docs/3-reference/rest-ocra-telemetry-snapshot.md` with Replay instructions + telemetry fields.
- Reflect the Replay panel and telemetry adapter usage in `docs/4-architecture/knowledge-map.md` and `docs/architecture-graph.json`.
- Note replay coverage in the roadmap and in future Implementation Drift Gate reports once Feature 016 closes.

## Fixtures & Sample Data
- Inline presets derive from `docs/ocra_validation_vectors.json` and remain versioned with the rest of the protocol fixtures.
- Stored-mode Selenium tests fall back to programmatic credential seeding when `data/credentials.db` is unavailable; successful runs still validate with sample MapDB content when present.
- Replay template fragments (`ui/ocra/replay.html` + embedded scripts) are treated as fixtures for Selenium since tests rely on `data-testid` selectors.

## Spec DSL
```
domain_objects:
  - id: DO-016-01
    name: OcraVerificationRequest
    modules: [rest-api, application]
  - id: DO-016-02
    name: OperatorConsoleReplayEventRequest
    modules: [rest-api]
  - id: DO-016-03
    name: OcraPolicyPreset
    modules: [docs, rest-api]
routes:
  - id: API-016-01
    method: GET
    path: /ui/console (panel=ocra)
    description: Renders Evaluate + Replay fragments
  - id: API-016-02
    method: POST
    path: /api/v1/ocra/verify
    description: Replay endpoint powering UI + CLI/REST
  - id: API-016-03
    method: GET
    path: /api/v1/ocra/credentials/{id}/sample
    description: Curated sample payload for stored credentials
  - id: API-016-04
    method: POST
    path: /ui/console/replay/telemetry
    description: UI telemetry logging endpoint
telemetry_events:
  - id: TE-016-01
    event: ui.console.replay
  - id: TE-016-02
    event: rest.ocra.verify
  - id: TE-016-03
    event: rest.ui.ocra.page
fixtures:
  - id: FX-016-01
    path: docs/ocra_validation_vectors.json
  - id: FX-016-02
    path: data/credentials.db
  - id: FX-016-03
    path: rest-api/src/main/resources/templates/ui/ocra/replay.html
ui_states:
  - id: UI-016-01
    description: Stored replay panel active
  - id: UI-016-02
    description: Inline replay panel with preset auto-fill
  - id: UI-016-03
    description: Validation/error banner state
  - id: UI-016-04
    description: Result card with telemetry grid
```

## Appendix (Optional)
 - Selenium smoke command: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.OperatorConsoleReplaySeleniumTest" --info`.
- Telemetry log example: `{ "event": "ui.console.replay", "mode": "inline", "outcome": "MATCH", "contextFingerprint": "Base64URL(SHA-256(...))", "telemetryId": "OCRA-UI-REPLAY-94Y" }`.
