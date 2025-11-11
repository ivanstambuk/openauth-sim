# Feature 041 – Operator Console JavaScript Modularization & Test Harness

| Field | Value |
|-------|-------|
| Status | Draft |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/041/plan.md` |
| Linked tasks | `docs/4-architecture/features/041/tasks.md` |
| Roadmap entry | #41 |

## Overview
Operator-console behaviour currently lives in large per-protocol JavaScript files plus inline Thymeleaf snippets. Only the EMV console logic has Node-based unit tests, leaving HOTP, TOTP, OCRA, FIDO2, and shared widgets (secret fields, verbose trace console, protocol switcher) dependent on slower Selenium coverage. This feature modularises the console assets, establishes a shared DOM/test harness, and wires a consolidated Gradle task so every protocol script runs inside `./gradlew check`. The spec remains in Draft until governance confirms all protocols conform to the harness.

## Clarifications
- 2025-11-07 – Workstream remains Draft until every existing protocol adopts the harness; new protocols must update this spec before implementation (owner directive).
- 2025-11-07 – Node-based tests must run through Gradle (`operatorConsoleJsTest`) so CI surfaces failures alongside JVM suites (owner directive).
- 2025-11-07 – Inline Thymeleaf scripts (notably OCRA evaluate/replay fragments) must move into standalone assets before receiving tests; no new inline scripts allowed after extraction (owner directive).
- 2025-11-07 – Shared helpers (SecretFieldBridge, VerboseTraceConsole, protocol tabs) must expose documented entry points so tests can stub dependencies without Selenium (owner directive).

## Goals
- Extract all operator-console JavaScript into standalone modules with composable helpers.
- Provide a reusable DOM harness (fixtures + helper APIs) so Node unit tests simulate interactions deterministically.
- Create a Gradle task that aggregates all console Node tests under `check`, with per-protocol filtering for local runs.
- Expand coverage to HOTP, TOTP, OCRA, FIDO2, EMV, and shared widgets before closing the feature.
- Document onboarding steps so future protocols hook into the harness.

## Non-Goals
- Introducing TypeScript, bundlers, or runtime module loaders.
- Replacing Selenium entirely; browser tests remain for end-to-end assertions.
- Building a full UI component library beyond the minimal helpers required for testing.

## Functional Requirements

### FR41-01 – JavaScript modularization
- **Requirement:** Extract remaining inline scripts (especially OCRA) into standalone files under `rest-api/src/main/resources/static/ui/<protocol>/`.
- **Success path:** Each protocol exports factories/helpers (`createHotpConsoleController`, etc.) and guards against missing DOM nodes.
- **Validation path:** Node harness throws descriptive errors when DOM hooks are absent; plan logs any residual inline snippets.
- **Failure path:** Build fails if inline fragments remain or modules exceed documented boundaries (>500 LOC) without helper extraction.

### FR41-02 – Shared Node harness
- **Requirement:** Create `rest-api/src/test/javascript/support/` utilities (DOM stubs, fetch spies, SecretFieldBridge/VerboseTrace mocks) and port EMV tests to prove the design.
- **Success path:** Harness API documented; Node suites run deterministically without browser dependencies.
- **Validation path:** EMV tests execute via `node --test` and through Gradle aggregator; harness README included.
- **Failure path:** Harness missing features forces suites back to Selenium; feature cannot progress.

### FR41-03 – Protocol coverage rollout
- **Requirement:** Add Node suites for HOTP, TOTP, OCRA, and FIDO2 covering evaluate/replay toggles, preset hydration, verbose trace routing, secret-field messaging, and credential directory fetches.
- **Success path:** Each suite simulates DOM interactions and asserts shared widget behaviour; fixtures live alongside tests.
- **Validation path:** Gradle task exposes per-protocol filtering; task log shows executed suites.
- **Failure path:** Missing coverage blocks drift gate; spec remains Draft.

### FR41-04 – Gradle integration
- **Requirement:** Replace `emvConsoleJsTest` with `operatorConsoleJsTest`, wire it under `check`, and support filtering (`-PconsoleTestFilter=fido2`).
- **Success path:** `./gradlew operatorConsoleJsTest check` runs Node suites; CI fails fast on JS regressions.
- **Validation path:** Contributor docs describe how to run the task locally; plan logs command output.
- **Failure path:** Task missing from `check` or lacking filtering prevents exit.

### FR41-05 – Governance & lifecycle
- **Requirement:** Keep this spec in Draft until governance verifies all protocols comply; document onboarding steps, drift gate expectations, and follow-ups.
- **Success path:** Tasks file lists future-protocol checklist; knowledge map references harness relationships.
- **Validation path:** Analysis gate confirms docs/how-tos updated; open questions logged/resolved.
- **Failure path:** Missing governance artefacts block closure.

## Non-Functional Requirements
- **NFR41-01 – Determinism:** Node harness must avoid timers/non-deterministic DOM APIs; tests run identically on CI and local machines.
- **NFR41-02 – Performance:** Console JS task should add ≤2 minutes to `./gradlew check`; support filtering for local focus.
- **NFR41-03 – Maintainability:** Shared helpers documented with entry points + JSDoc-style comments so future protocols integrate quickly.

## UI / Interaction Mock-ups
- Console page retains existing layout but scripts now initialise via module factories. Documented pseudo-code:
  ```
  import { createHotpConsoleController } from './hotp/console.js';
  createHotpConsoleController({ dom: getHotpDom(), harnessApi });
  ```
- Node harness mimics DOM tree using JSDOM; sample snippet lives in `support/harness.md`.

## Branch & Scenario Matrix

| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S41-01 | Existing EMV console Node suite runs (and fails fast) under the shared harness/Gradle aggregator. |
| S41-02 | Shared DOM/test harness (SecretFieldBridge/VerboseTrace mocks) enables protocol scripts to run without Selenium. |
| S41-03 | HOTP & TOTP controllers expose pure helpers and gain Node suites covering evaluate/replay flows, presets, verbose traces, secret-field messaging. |
| S41-04 | FIDO2 console scripts run inside the harness validating preset seeding, credential directory fetches, verbose trace wiring. |
| S41-05 | OCRA controllers extracted from Thymeleaf, tested for credential directory retries, preset hydration, inline validation, replay toggles. |
| S41-06 | Shared widgets (SecretFieldBridge, VerboseTraceConsole, protocol tab switcher) ship dedicated suites verifying DOM mutations and messaging. |
| S41-07 | Gradle `operatorConsoleJsTest` replaces `emvConsoleJsTest`, wires into `check`, supports filtering, and fails CI on any console unit failure. |
| S41-08 | Governance checklist enforces that every new protocol registers harness coverage before merging. |

## Test Strategy
- Node unit tests per protocol + shared helper suites executed via `./gradlew operatorConsoleJsTest`.
- Selenium regression tests remain but may shrink once harness stabilises.
- JVM module tests unaffected but may stub harness integration points.
- Spotless/ESLint (once adopted) ensure JS style consistency.

## Interface & Contract Catalogue

### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-041-01 | `ConsoleControllerFactory` (per protocol entry point returning event handlers). | rest-api |
| DO-041-02 | `DomHarnessContext` (fixtures + stubs passed to controller factories during Node tests). | rest-api tests |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-041-01 | Static assets under `/ui/<protocol>/console.js` | Standalone JS modules initialising protocol consoles. | Loaded via existing Thymeleaf templates. |

### CLI Commands
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-041-01 | `./gradlew --no-daemon operatorConsoleJsTest` | Runs all Node-based console suites; supports filtering via `-PconsoleTestFilter=<protocol>`. |

### Telemetry Events
| ID | Event name | Fields / Redaction summary |
|----|------------|----------------------------|
| TE-041-01 | `build.console_js.test` | `protocol`, `durationMs`, `status`; emitted via Gradle logging or optional telemetry hook to monitor harness health. |

### Fixtures & Sample Data
| ID | Path | Description |
|----|------|-------------|
| FX-041-01 | `rest-api/src/test/javascript/support/fixtures/*.json` | Preset payloads, credential directory responses, verbose trace toggles for Node tests. |
| FX-041-02 | `rest-api/src/test/javascript/<protocol>/*.test.js` | Canonical test suites per protocol. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|----------------------------|
| UI-041-01 | Console Evaluate mode initialised via module factory. | DOM harness ensures controllers render evaluate view without Selenium. |
| UI-041-02 | Console Replay mode toggled in Node tests. | Harness verifies preset hydration + verbose trace wiring. |

## Spec DSL
```yaml
harness:
  task: operatorConsoleJsTest
  protocols:
    - hotp
    - totp
    - ocra
    - fido2
    - emv
  shared_helpers:
    - SecretFieldBridge
    - VerboseTraceConsole
filters:
  property: consoleTestFilter
  example: "-PconsoleTestFilter=fido2"
assets:
  ocra:
    inline_scripts: false
    entry_point: rest-api/src/main/resources/static/ui/ocra/console.js
```

## Appendix
- DOM harness checklist (JSDOM version, fetch mock strategy) documented under `rest-api/src/test/javascript/support/README.md`.
- Future protocol onboarding steps must append to `docs/4-architecture/features/041/tasks.md` before implementation.
