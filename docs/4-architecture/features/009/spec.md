# Feature 009 – Operator Console Infrastructure

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-12-13 |
| Owners | Ivan (project owner) |
| Linked plan | [docs/4-architecture/features/009/plan.md](docs/4-architecture/features/009/plan.md) |
| Linked tasks | [docs/4-architecture/features/009/tasks.md](docs/4-architecture/features/009/tasks.md) |
| Roadmap entry | #9 – Operator Console Infrastructure |

## Overview
Feature 009 is the single source of truth for every operator-console artefact—Thymeleaf shells, vanilla JS controllers,
verbose-trace dock wiring, Node harnesses, and protocol-specific helpers. Multi-protocol tabs, info surfaces, validation helpers,
trace diagnostics, Base32 inputs, preview windows, and the modular JS test harness all share one consolidated spec/plan/tasks bundle
before further feature work continues and are backed by ADR-0005 (Operator Console Layout and Shared UI Contracts). /ui/console
is the canonical entry point and legacy routes such as `/ui/ocra/*` either redirect to /ui/console or return `404` to prevent
drift. The tablist order is locked to HOTP → TOTP → OCRA → FIDO2/WebAuthn → EMV/CAP → EUDIW OpenID4VP → EUDIW ISO/IEC 18013-5 →
EUDIW SIOPv2, with placeholder panels maintained until each protocol is live. Protocol Info drawers, preset catalogues, validation
helpers, verbose trace toggles, trace tiers, Base32 helpers, preview tables, and modular JS controllers/harnesses described below
represent the normative behaviour for every operator-console increment.

Cross-facade conventions (Native Java/CLI/REST/UI/MCP/standalone) are centralised in
[docs/4-architecture/facade-contract-playbook.md](docs/4-architecture/facade-contract-playbook.md).

## Goals
- G-009-01 – Keep the operator console shell (tabs, query params, seed workflows) deterministic, accessible, and documented across protocols.
- G-009-02 – Surface contextual metadata (Protocol Info drawer) plus harmonised preset labels so operators rely on a single UX contract.
- G-009-03 – Document validation, trace diagnostics, Base32 inputs, preview tables, and JS harness wiring so downstream batches reuse the same builders and tests.

## Non-Goals
- N-009-01 – No behaviour changes that expose incomplete protocols beyond placeholders or disabled tabs.
- N-009-02 – Do not introduce telemetry adapters outside the existing `TelemetryContracts` guardrail.
- N-009-03 – Avoid launching new UI frameworks; keep the dark-theme Thymeleaf + vanilla JS stack intact.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-009-01 | /ui/console renders the ordered protocol tabs, query-parameter-based history, stored credential seed controls, and placeholder messaging while legacy `/ui/ocra/*` routes redirect to the new entry. | Selenium/DOM tests verify tab order, state restoration, seeding controls, and disabled future tabs. | Invalid/legacy tabs fallback to OCRA, history navigation uses query params, and stored credential seeding logs the action. | Legacy routes still reachable or tabs render out of order. | `ui.console.navigation` events carry `protocol`, `tab`, and `seedAction`. | Spec |
| FR-009-02 | Protocol Info drawer opens via the right-aligned trigger, persists preferences, swaps content per protocol, and exposes embeddable schema-driven assets/docs. | Drawer obeys `prefers-reduced-motion`, uses `localStorage` keys per protocol, and renders schema payloads safely. | Accessibility tests exercise focus trap + ESC/Enter shortcuts; doc review validates README/harness integration. | Drawer fails to open, persistence breaks, or schema renders unescaped HTML. | CustomEvents `ui.protocolInfo.trigger.clicked`, `ui.protocolInfo.opened/closed`, `ui.protocolInfo.protocol.changed`, `ui.protocolInfo.persistence.updated`. | Spec |
| FR-009-03 | Console preset dropdowns across HOTP/TOTP/OCRA/FIDO2 expose `<scenario – key attributes>` labels, keep seeded/stored catalogues in sync (6/8-digit permutations + RFC suffixes), and documentation references the harmonised copy. | Templates and JS render the new labels; OAuth presets include `(RFC 6287)` suffixes while drafts stay untouched. | UI snapshots + Selenium run confirm the label list and seeded credentials match; docs mention the new pattern. | Stale labels, missing seeded credentials, or docs lacking guidance. | Sanitized label strings remain in telemetry logs. | Spec |
| FR-009-04 | Validation helper ensures every invalid response reveals the appropriate result card with the API `message` for HOTP/TOTP/OCRA/WebAuthn flows. | Helper toggles `showResultCard` and `validationMessage`, and Selenium suites confirm the message renders. | Integration tests feed invalid payloads to each ceremony; docs describe the pattern. | Result card remains hidden or message missing despite invalid responses. | Telemetry unaffected. | Spec |
| FR-009-05 | Verbose trace mode provides identical payloads to CLI, REST, and UI callers while remaining opt-in per request and retaining WebAuthn metadata (RP IDs, flag maps, signature counters). | CLI `--verbose`, REST `verboseTrace=true`, and UI trace panel show the same steps; WebAuthn trace includes canonical RP IDs + policy notes. | Facade-specific verbose trace suites (`:core:test`, `:application:test`, `:cli:test`, `:rest-api:test`, Selenium) cover happy/failure scenarios. | Traces diverge, default requests leak verbose data, or WebAuthn metadata missing. | None (traces returned inline). | Spec |
| FR-009-06 | Trace tier filtering helper enforces `normal`, `educational`, and `lab-secrets` contracts across builders and facades while logging per-request tier metadata. | Helper masks secrets whose `minimumTier` exceeds the requested tier and returns deterministic payloads; CLI/REST/JS respect tier flags. | Tier fixtures/assertions exercise all protocols; telemetry events emit `telemetry.trace.filtered` and `telemetry.trace.invalid_tier`. | Unknown tiers cause errors or secrets leak at lower tiers. | `telemetry.trace.filtered`, `telemetry.trace.invalid_tier`. | Spec |
| FR-009-07 | Inline secrets accept Base32 input (mutually exclusive with hex) via the shared `SecretEncodings` helper, CLI flags, and UI toggle; docs cover conversion hints. | Helper uppercases/pads Base32, CLI commands expose `--shared-secret-base32`, REST DTOs support `sharedSecretBase32`, and the UI toggle keeps both encodings aligned. | REST/CLI/UI tests assert exclusivity, validation errors, and conversion results; OpenAPI snapshot documents the new field. | Missing validation, helpers fail, or docs lack guidance. | Telemetry events log only the sanitized hex string. | Spec |
| FR-009-08 | Evaluation flows accept preview windows (`window.backward/forward`), render Delta-ordered tables (with accent on Delta = 0), and expose CLI/REST flags while keeping replay drift inputs unchanged. | CLI output, REST JSON, and UI tables render the ordered previews; invalid offsets return informative errors and blocks the request. | CLI/REST/Selenium tests cover {0,0} defaults, multi-row windows, accent styling, and helper-text removal. | Preview tables missing, delta accent absent, or helper text unexpectedly shown. | `otp.evaluate.preview`, `otp.evaluate.preview_invalid_window`. | Spec |
| FR-009-09 | Static JS modules expose per-protocol controller factories, reuse shared helpers (SecretFieldBridge, VerboseTraceConsole), and run through the `operatorConsoleJsTest` Gradle task with filtering support. | Node harness suites (`node --test `rest-api/src/test/javascript/`...`) exercise HOTP, TOTP, OCRA, FIDO2, EMV, and shared widgets; `./gradlew operatorConsoleJsTest -PconsoleTestFilter=<protocol>` filters runs. | Gradle aggregators (`check`) fail when Node tests regress; docs describe harness onboarding. | Inline scripts remain, harness missing features, or Gradle task not wired under `check`. | Optional telemetry `build.console_js.test`. | Spec |
| FR-009-10 | Documentation, knowledge map, session log ([docs/_current-session.md](docs/_current-session.md)), roadmap, and `_current-session.md` describe the consolidated console scope (tabs, traces, debug helpers, scripted tests) and reference the new feature as the exclusive source. | Docs mention Feature 009 for console ownership | `spotlessApply check` plus manual doc review confirm the entries; `_current-session.md` cites the deletion/completion log. | Legacy features still cited or docs unsynchronised. | n/a | Spec |
| FR-009-11 | Provide a headless visual snapshot harness for `/ui/console` to capture real-browser screenshots of default and interactive UI states (tabs + Evaluate/Replay result panels for inline/stored sample flows) into a temporary, non-committed directory for visual QA of layout/CSS regressions. | Operator (or agent) runs the harness against a locally started REST server and gets PNG screenshots per protocol that include baseline tab renders plus at least one Evaluate/Replay result state per supported protocol (where the panel is live). | Harness runs headless by default, disables animations, uses stable viewport sizing, freezes time to a deterministic timestamp (opt-out), drives only sample/seeded interactions (no manual data entry), supports overriding base URL + output directory, emits local-only triage artefacts (diff ranking + montage) to reduce review time, and prunes old run directories under `build/ui-snapshots/**` (default keep: 10). | Harness launches a visible browser window by default, writes snapshots into tracked paths, blocks user interaction via persistent overlays, grows `build/ui-snapshots/**` unbounded when using the default runner, or produces non-deterministic captures without a documented reason. | n/a | Spec |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-009-01 | Documentation traceability (per Constitution Principle 4) | Keep spec/plan/tasks linked to the console GitHub fragments, knowledge map, and session log ([docs/_current-session.md](docs/_current-session.md)) so auditors can trace console changes and ownership over time. | Verified links + `_current-session.md` notes per increment. | Knowledge map, roadmap. | Spec |
| NFR-009-02 | Telemetry hygiene (per TelemetryContracts guardrail) | Secrets or Base32 strings must never appear in structured logs; trace tiers mask attributes accordingly. | ArchUnit/telemetry guardrails + code review. | `TelemetryContracts`, masked logging helpers. | Spec |
| NFR-009-03 | Quality gate parity (per Constitution Principle 3) | `./gradlew --no-daemon spotlessApply check`, all JVM `:rest-api`/`:application`/`:cli`/`:ui` suites, Node harness, and PMD/Spotless must stay green. | Command logs recorded in `_current-session.md`. | Gradle toolchain, Node environment. | Spec |
| NFR-009-04 | Accessibility (tabs, info drawer, validation, preview accents) | Tablist focus, Info drawer, validation messaging, and preview accent styling meet WCAG/keyboard expectations. | Selenium/axe audits + manual review. | Thymeleaf templates, CSS tokens. | Spec |
| NFR-009-05 | Node harness determinism | JS tests run consistently (no timers/non-deterministic APIs) and add ≤2 minutes to `./gradlew check`. | CI logs, harness README, filtering property. | Node + Gradle configuration. | Spec |
| NFR-009-06 | Cross-facade UI parity | Operator console behaviour for canonical scenarios must remain consistent with REST/CLI/Native Java outcomes and JSON payloads; cross-facade contract tests in Feature 013 (FR-013-11) provide the normative parity checks for UI-visible success/failure messaging and trace toggles. | Tagged Selenium/JS smoke parity suites green; discrepancies resolved via spec updates before UI changes. | rest-api UI controllers, Selenium/Node harness. | Spec |
| NFR-009-07 | Visual snapshot harness ergonomics | Visual QA tooling must never block the default Gradle quality gates; it must be opt-in, headless-by-default, and keep all generated artefacts under `build/` (or another ignored output directory). | Manual runs and `.gitignore` coverage confirm no snapshots are tracked; harness docs show explicit invocation separate from `check`; default runner prunes old runs so `build/ui-snapshots/**` stays bounded; captures are deterministic enough for drift review (time frozen by default); triage artefacts (diff ranking + montage) exist to keep reviews fast. | Node + Playwright tooling + `ffmpeg`. | Spec |

## UI / Interaction Mock-ups

```
+----------------------------------------------------------------------------------+
| HOTP | TOTP | OCRA | EMV | FIDO2 | EUDI-OpenID4VP | EUDI-ISO18013-5 | EUDI-SIOPv2 | [Protocol Info] |
+----------------------------------------------------------------------------------+
| Preset selector with `<scenario – key attributes>` hint        | Inline secret entry (Base32 ⇄ Hex toggle, SecretEncodings helper) |
| Validation result card (message + copy/download)              | Preview window (Delta=0 accent, CLI/REST offset labels)         |
| Verbose trace dock (tier filter, `normal/educational/lab-secrets` toggles)             |
| Protocol Info drawer (schema accordion, persisted prefs, CustomEvents, copy links)     |
| Node harness tip (`operatorConsoleJsTest`, `consoleTestFilter` prompt)                 |
```

The mock-up emphasises the consolidated tab list, inline secrets, validation/preview stack, verbose trace dock, and the right-rail Protocol Info drawer that surfaces schema + persistence content for every protocol.

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-009-01 | /ui/console renders ordered tabs (hotp, totp, ocra, emv, fido2, eudi-openid4vp, eudi-iso18013-5, eudi-siopv2), preserves query params, and keeps seeding/placeholder controls intact. |
| S-009-02 | Protocol Info trigger/drawer opens, swaps schema content, persists preferences, and exposes embeddable docs. |
| S-009-03 | Preset dropdowns across HOTP/TOTP/OCRA/FIDO2 show `<scenario – key attributes>` labels with RFC suffixes where required.
| S-009-04 | Validation helper reveals result cards/messages for invalid responses across HOTP/TOTP/OCRA/WebAuthn.
| S-009-05 | Verbose trace mode returns identical payloads (CLI/REST/UI) with WebAuthn metadata when toggled per request.
| S-009-06 | Trace tiers filter attributes while CLI/REST/UI contemplates `normal`, `educational`, `lab-secrets` toggles; invalid tier requests emit telemetry.
| S-009-07 | Inline secret entry accepts Base32 or hex (mutually exclusive), CLI flags route through the helper, and UI toggles keep values aligned.
| S-009-08 | Evaluation preview windows render ordered tables with Delta=0 accent, CLI/REST flags pick offsets, and helper text is concise.
| S-009-09 | Console JS modules run inside the `operatorConsoleJsTest` harness with filtering, covering HOTP/TOTP/OCRA/FIDO2/EMV/shared widgets.
| S-009-10 | Knowledge map, session log ([docs/_current-session.md](docs/_current-session.md)), roadmap, and `_current-session.md` describe the console feature as the authoritative place for all related content.
| S-009-11 | Visual snapshot harness captures headless real-browser screenshots for each protocol tab plus representative Evaluate/Replay result states (inline/stored via presets/seeding) into `build/ui-snapshots/**` for CSS/layout regression review.

## Test Strategy
- JVM suites: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.*OperatorUiSeleniumTest"`, `./gradlew --no-daemon :cli:test --tests "*VerboseTrace*"`, `./gradlew --no-daemon :application:test --tests "*VerboseTrace*"`, and `./gradlew --no-daemon :ui:test` to cover console flows, trace builders, and validation helpers.
- Node/JS: `node --test [rest-api/src/test/javascript/emv/console.test.js](rest-api/src/test/javascript/emv/console.test.js)`, `./gradlew --no-daemon operatorConsoleJsTest -PconsoleTestFilter=<protocol>` plus the aggregated `check` target to run Node harness suites.
- Visual QA (opt-in):
  - Capture: run `bash tools/ui-visual/run-operator-console-snapshots.sh` to capture baseline + Evaluate/Replay screenshots into `build/ui-snapshots/**` (bounded by `UI_VISUAL_MAX_RUNS`).
  - Review (triage-first): start with `build/ui-snapshots/<run-id>/triage/current-interactive.png` to scan high-signal “result panel” states; when a baseline exists, use `triage/top-changes.png` + `triage/triage.json` to focus on the biggest diffs (baseline left, current right); open the underlying full-size PNGs only for the 1–3 screens that look off, using `manifest.json` as the table of contents.
  - Backlog: log each issue as a Feature 009 task (or the owning UI feature) with screenshot references and acceptance criteria.
  - Validate: re-run the harness after fixes and compare against the prior run directory to confirm the issue is resolved.
- Documentation: `./gradlew --no-daemon spotlessApply check` to keep specs/roadmap/knowledge-map/formatted; manual doc review ensures session log ([docs/_current-session.md](docs/_current-session.md))/_current-session updates.

## Interface & Contract Catalogue
### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-009-01 | `OperatorProtocolTab` enumerates the supported tabs (hotp/totp/ocra/emv/fido2/eudi-*). | rest-api, ui JS |
| DO-009-02 | `ProtocolInfoSchema` describes sections/accordions/hooks rendered in the info drawer. | rest-api UI |
| DO-009-03 | `ProtocolInfoState` tracks last protocol, drawer visibility, and accordion scope persisted to `localStorage`. | ui JS |
| DO-009-04 | `SecretEncodings` helper converts Base32 to uppercase hex, strips whitespace, and provides masking hints. | core, application, ui |
| DO-009-05 | `InlineSecretInput` holds mutually exclusive `sharedSecretHex` and `sharedSecretBase32` fields. | rest-api, application, cli, ui |
| DO-009-06 | `VerboseTrace`, `VerboseTraceStep`, and `TraceAttribute` model operations, metadata, and tier filters. | core, application |
| DO-009-07 | `TraceTier` enum (normal/educational/lab-secrets) plus `TraceTierFilter` helper masks attributes per tier. | core |
| DO-009-08 | `PreviewWindow` captures `backward`/`forward` offsets; `PreviewRow` records delta, context, and accent metadata. | rest-api, application, cli, ui |
| DO-009-09 | `ConsoleControllerFactory` (per protocol) receives `DomHarnessContext` from the shared JS harness. | rest-api JS |

### API Routes / Services
| ID | Method | Path | Description |
|----|--------|------|-------------|
| API-009-01 | GET | /ui/console | Renders the console shell (tabs, headers, seed controls). |
| API-009-02 | POST | /api/v1/{protocol}/inline/evaluate | Accepts `sharedSecretHex` or `sharedSecretBase32`, `window`, and toggles trace/preview behaviour. |
| API-009-03 | POST | /api/v1/{protocol}/trace | Returns verbose trace payloads filtered by tier if requested. |
| API-009-04 | GET | /api/v1/ocra/credentials/seed | Seeds canonical credential suites (without overwriting existing ones). |
| API-009-05 | POST | /api/v1/{protocol}/validate | Supports preview windows and validation helper messaging. |

### CLI Commands
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-009-01 | `hotp evaluate --verbose --verbose-tier=<tier> --shared-secret-base32` | Routes Base32/hex through helper, emits verbose traces filtered by tier, and prints ordered preview tables when `--window-*` flags used. |
| CLI-009-02 | `node --test [rest-api/src/test/javascript/emv/console.test.js](rest-api/src/test/javascript/emv/console.test.js)` | Runs the console JS harness for EMV flows. |
| CLI-009-03 | `./gradlew --no-daemon operatorConsoleJsTest` | Aggregates console Node suites, supports `-PconsoleTestFilter=<protocol>`, and runs as part of `check`. |
| CLI-009-04 | `totp inline-evaluate --shared-secret-base32` / `ocra evaluate --shared-secret-base32` etc | Mirror HOTP options per protocol through the shared helper. |

### Telemetry Events
| ID | Event | Fields / Notes |
|----|-------|----------------|
| TE-009-01 | `ui.console.navigation` | `protocol`, `tab`, `seedAction`. |
| TE-009-02 | `ui.protocolInfo.trigger.clicked` / `ui.protocolInfo.opened/closed` / `ui.protocolInfo.protocol.changed` / `ui.protocolInfo.persistence.updated` | `protocol`, `entryPoint`, `prefs`. |
| TE-009-03 | `telemetry.trace.filtered` | `protocol`, `tier`, `attributeCount`, `maskedCount`, `source`. |
| TE-009-04 | `telemetry.trace.invalid_tier` | `protocol`, `tier`, `source`, `reason`. |
| TE-009-05 | `otp.evaluate.preview` / `otp.evaluate.preview_invalid_window` | `protocol`, `windowBackward`, `windowForward`, `previewCount`, `result`. |
| TE-009-06 | `build.console_js.test` (optional) | Reports JS harness status/duration per protocol. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-009-01 | docs/test-vectors/ocra/operator-samples.json | Seeded credential catalogue for seeding controls. |
| FX-009-02 | [rest-api/src/main/resources/static/ui/console/console.css](rest-api/src/main/resources/static/ui/console/console.css) | Shared console styles. |
| FX-009-03 | [rest-api/src/main/java/io/openauth/sim/rest/ui/HotpOperatorSampleData.java](rest-api/src/main/java/io/openauth/sim/rest/ui/HotpOperatorSampleData.java) (and Totp/Ocra/Fido2 variants) | Preset label definitions referred by requirement FR-009-03. |
| FX-009-04 | `docs/test-vectors/trace-tiers/hotp/*.json`, `docs/test-vectors/trace-tiers/fido2/*.json` | Canonical tier payloads. |
| FX-009-05 | `docs/test-vectors/trace-preview/*.json` | Ordered preview tables for evaluation windows. |
| FX-009-06 | `rest-api/src/test/javascript/support/fixtures/*.json` | DOM harness fixtures for Node suites. |

### UI States
| ID | Description | Trigger |
|----|-------------|--------|
| UI-009-01 | Tablist with HOTP → EUDI tabs, placeholder panels, and query-param persistence. | /ui/console navigation. |
| UI-009-02 | Protocol Info drawer (trigger + panel) with schema-driven content and persistence. | `Protocol info` icon. |
| UI-009-03 | Validation result card forced visible with API message when responses are invalid. | Invalid REST responses. |
| UI-009-04 | Verbose trace dock (terminal-style) showing trace steps filtered by tier. | `Show verbose trace` control. |
| UI-009-05 | Base32 toggle/text area syncing between hex/base32, inline hints, and validation states. | Shared secret section. |
| UI-009-06 | Preview window offsets control and Delta-ordered table with accent for Delta = 0. | Evaluation forms. |
| UI-009-07 | Node harness documentation/hint showing `createXConsoleController({ dom, harness })`. | JS tests referencing rest-api/src/test/javascript/support. |

## Telemetry & Observability
Continue emitting `operator.console.*` frames through `TelemetryContracts` adapters. Verbose trace tiers emit `telemetry.trace.filtered`/`invalid_tier`, and preview telemetry uses `otp.evaluate.preview*`. The `build.console_js.test` event remains optional but available for monitoring the JS harness.

## Documentation Deliverables
- Update [docs/4-architecture/roadmap.md](docs/4-architecture/roadmap.md) and [docs/4-architecture/knowledge-map.md](docs/4-architecture/knowledge-map.md) to describe the consolidated console scope (tabs, info drawer, trace tiers, Base32, preview windows, and JS harness).
- Align operator how-to guides (``docs/2-how-to`/*.md`) and runbooks with the new spec, including Base32 instructions, trace usage, and console testing guidance.

## Fixtures & Sample Data
Reuse the existing preset helpers (HotpOperatorSampleData, TotpOperatorSampleData, OcraOperatorSampleData, Fido2OperatorSampleData), trace tier fixture sets, and preview table resources; keep the DOM harness fixtures under `rest-api/src/test/javascript/support/fixtures/` up to date with each protocol.

## Spec DSL
```
domain_objects:
  - id: DO-009-01
    name: OperatorProtocolTab
    values: [hotp, totp, ocra, emv, fido2, eudi-openid4vp, eudi-iso18013-5, eudi-siopv2]
  - id: DO-009-04
    name: SecretEncodings
    operations:
      - name: toHex
        params: [base32Value]
        returns: string
  - id: DO-009-05
    name: InlineSecretInput
    constraints:
      - exactly_one_of: [sharedSecretHex, sharedSecretBase32]
routes:
  - id: API-009-01
    method: GET
    path: /ui/console
  - id: API-009-02
    method: POST
    path: /api/v1/{protocol}/inline/evaluate
    query:
      - name: verboseTrace
        type: boolean
      - name: verboseTier
        type: enum[normal, educational, lab_secrets]
  - id: API-009-03
    method: POST
    path: /api/v1/{protocol}/trace
  - id: API-009-04
    method: POST
    path: /api/v1/{protocol}/preview
cli_commands:
  - id: CLI-009-01
    command: hotp evaluate --verbose --verbose-tier=<tier> --shared-secret-base32=<value>
  - id: CLI-009-02
    command: ./gradlew operatorConsoleJsTest
telemetry_events:
  - id: TE-009-01
    event: ui.console.navigation
  - id: TE-009-02
    event: ui.protocolInfo.trigger.clicked
  - id: TE-009-03
    event: telemetry.trace.filtered
fixtures:
  - id: FX-009-03
    path: rest-api/src/main/java/io/openauth/sim/rest/ui/HotpOperatorSampleData.java
ui_states:
  - id: UI-009-02
    description: Protocol Info drawer with schema-driven content
```
