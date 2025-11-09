# Feature 041 – Operator Console JavaScript Modularization & Test Harness

_Status: Draft_
_Last updated: 2025-11-07_

## Overview
Operator-console behaviour currently lives in sprawling per-protocol JavaScript files plus inline Thymeleaf scripts. Only the EMV console logic has Node-based unit tests, leaving HOTP, TOTP, OCRA, FIDO2, and shared widgets (secret fields, verbose trace console, protocol switcher) dependent on slower Selenium coverage. This workstream modularises the console assets, establishes a shared DOM/test harness, and wires a consolidated Gradle task so every protocol script can be validated inside `./gradlew check`. The specification intentionally remains open so it can be amended whenever new protocols ship or when existing controllers gain functionality.

## Clarifications
- 2025-11-07 – This workstream stays in "Draft" until all existing console protocols adopt the shared harness; each new protocol or major console change must trigger a spec refresh (owner directive).
- 2025-11-07 – Node-based tests must run through Gradle (e.g., `operatorConsoleJsTest`) so CI surfaces failures alongside JVM suites; the command may fan out to per-protocol test files (owner directive).
- 2025-11-07 – Inline scripts embedded inside Thymeleaf templates (currently OCRA evaluate/replay fragments) need to move into standalone assets before we add tests; no new inline scripts may be introduced once extraction completes (owner directive).
- 2025-11-07 – Shared helpers (SecretFieldBridge, VerboseTraceConsole, protocol tabs) should expose small, documented entry points so tests can stub dependencies without a browser (owner directive).

## Goals & Success Criteria
1. All operator-console JavaScript loads from standalone files with clearly defined module seams and no duplicated protocol scaffolding.
2. A reusable DOM harness (fixtures + helper APIs) enables Node unit tests to simulate user interactions without Selenium.
3. Gradle `check` fails when any console unit test fails; the console test command surfaces per-protocol results for quick triage.
4. Coverage extends to shared widgets plus HOTP, TOTP, OCRA, FIDO2, and EMV flows (Evaluate + Replay toggles, presets, seeding, verbose trace wiring, secret fields) before the workstream can close.
5. Future protocols inherit the same harness and add tests as part of their feature gates; documentation explains how to plug into the pipeline.

## Requirements
### R1 – JavaScript modularization & contracts
1. Extract remaining inline scripts (notably OCRA evaluate/replay fragments) into `rest-api/src/main/resources/static/ui/<protocol>/console.js` following the existing module pattern.
2. Split oversized files into composable helpers (e.g., preset hydration, credential directory fetchers, seed orchestration) when logic exceeds 500 LOC, exposing pure functions wherever possible.
3. Document the exported functions/initializers at the top of each file so future tests know which entry points to invoke.
4. Ensure each protocol module guards against missing DOM nodes (enabling deterministic Node tests) and exports factory methods where meaningful (e.g., `createHotpConsoleController`).

### R2 – Shared Node test harness
1. Create `rest-api/src/test/javascript/support/` with utilities for DOM stubs, event dispatch, fetch/CSRF simulators, and SecretFieldBridge mocks.
2. Provide fixtures for preset payloads, credential directory responses, and verbose trace toggles so per-protocol suites only declare scenario-specific data.
3. Offer helper assertions (e.g., `expectFieldHidden`, `collectFetchPayloads`) to keep suites concise.
4. Document harness usage inside `docs/4-architecture/features/041/spec.md` and mirror the guidance in the feature plan/tasks so downstream contributors follow the same patterns.

### R3 – Protocol test coverage rollout
1. Port existing EMV Node tests to the shared harness and keep them as the reference implementation.
2. Add HOTP, TOTP, and FIDO2 suites that prove:
   - Mode persistence when switching between stored/inline tabs.
   - Preset hydration + seed workflows (including disabled states).
   - Secret-field encoding toggles and validation messaging.
   - Verbose trace toggle wiring per protocol.
3. Add OCRA suites that validate evaluate/replay controllers after extraction, covering credential directory fetch retries, preset hydration, and inline validation messaging.
4. Track coverage for any new protocol introduced after this spec by updating the checklist in `docs/4-architecture/features/041/tasks.md` before merging.

### R4 – Gradle integration & CI guardrails
1. Replace the single `emvConsoleJsTest` task with a generalized `operatorConsoleJsTest` that enumerates all Node suites (or depends on target-specific Exec tasks) and wire it under `check`.
2. Ensure the task supports parallelism or per-suite filtering (e.g., `-PconsoleTestFilter=fido2`) for local development without slowing CI.
3. Update contributor docs (`README`, `CONTRIBUTING`, or relevant how-to guides) so running console tests becomes part of the documented workflow.
4. Record intentional follow-ups (e.g., coverage thresholds, mutation testing for JS) in the feature plan before closing the workstream.

### R5 – Lifecycle & governance
1. Keep this specification in Draft until a governance review confirms all current and future protocols conform to the harness; add a "Rebuild required" note whenever new protocols are proposed.
2. When new protocols land, append their coverage expectations to this spec before implementation begins; if the harness needs changes, describe them here first.
3. Capture deviations (temporary skips, flaky suites) in `docs/4-architecture/tasks/feature-041-...` with owners and remediation dates.

## Branch & Scenario Matrix

| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S41-01 | Existing EMV console Node suite continues to run (and fail fast) under the shared harness/Gradle aggregator so evaluate/replay flows remain deterministic. |
| S41-02 | Shared DOM/test harness (SecretFieldBridge/VerboseTraceConsole mocks, fetch spies) enables protocol scripts to run without Selenium. |
| S41-03 | HOTP & TOTP controllers expose pure helpers and gain Node suites covering mode toggles, preset hydration, preview windows, verbose trace routing, and secret-field messaging. |
| S41-04 | FIDO2 console scripts (attestation/assertion evaluate/replay) run inside the harness, validating preset seeding, credential directory fetches, and verbose trace wiring. |
| S41-05 | OCRA evaluate/replay controllers are extracted from Thymeleaf, load as modules, and gain Node coverage for credential directory retries, preset hydration, inline validation, and replay toggles. |
| S41-06 | Shared widgets (SecretFieldBridge, VerboseTraceConsole, protocol tab switcher) have dedicated unit suites verifying DOM mutations and messaging consistency. |
| S41-07 | Gradle `operatorConsoleJsTest` replaces `emvConsoleJsTest`, wires into `check`, supports filtering, and fails CI on any console unit failure. |
| S41-08 | Governance checklist enforces that every new protocol or console change registers harness coverage before merging, keeping this spec in Draft until compliance is confirmed. |

## Non-Goals
- Introducing TypeScript, bundlers, or runtime module loaders.
- Replacing Selenium coverage; browser tests remain for end-to-end assertions.
- Building a shared UI component library beyond the minimal helpers required for testing.

## Dependencies
- Existing console assets under `rest-api/src/main/resources/static/ui/`.
- SecretFieldBridge and VerboseTraceConsole scripts (shared helpers) which must remain CommonJS-friendly for Node tests.
- Gradle Node availability (CI environments already install Node 20+ for EMV tests).

## Test Strategy
- Node unit tests per protocol + shared helper suites, executed via the new Gradle task.
- Targeted Selenium tests remain for regressions but may shrink once confidence increases.
- Linting/formatting (Spotless Palantir Java format) unchanged; JS style continues to follow repository eslint/prettier defaults when introduced.

## Open Questions
_None at this time; add entries to `docs/4-architecture/open-questions.md` as soon as uncertainties surface._

## Exit Criteria
- All requirements above satisfied and documented in the feature plan/tasks.
- Coverage summary (which protocols + shared helpers are green) recorded in the Implementation Drift Gate when the workstream eventually closes.
- Roadmap entry updated to "Complete" only after governance confirms the harness applies to every protocol and the spec no longer requires draft status.
