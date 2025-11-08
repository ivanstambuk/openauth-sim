# Feature Plan 041 – Operator Console JavaScript Modularization & Test Harness

_Linked specification:_ `docs/4-architecture/specs/feature-041-operator-console-js-test-harness.md`  
_Status:_ In planning  
_Last updated:_ 2025-11-07

## Vision & Success Criteria
- Consolidate all operator-console scripts into standalone modules with clear contracts so future protocols avoid inline Thymeleaf logic.
- Establish a reusable Node-based DOM harness that lets us unit-test console behaviour without Selenium.
- Bring HOTP, TOTP, OCRA, FIDO2, and EMV flows under the shared harness and expose a Gradle task that runs every suite during `./gradlew check`.
- Keep the specification in Draft so it can be amended whenever console protocols change or new ones are introduced.

## Scope Alignment
- **In scope:** JS asset extraction, harness utilities, per-protocol Node tests, Gradle task updates, contributor documentation describing the workflow, and roadmap/session updates.
- **Out of scope:** Replacing Selenium entirely, introducing a JS bundler/TypeScript toolchain, or redesigning the operator UI layouts.

## Dependencies & Interfaces
- Reuses existing assets in `rest-api/src/main/resources/static/ui/**`.  
- Depends on Node 20+ availability in CI (already required for EMV tests).  
- Touches Gradle build logic (`rest-api/build.gradle.kts`) and contributor docs/README.  
- Interfaces with SecretFieldBridge, VerboseTraceConsole, and any new shared helper extracted during the workstream.

## Increment Breakdown (≤30 minutes each)
1. **I1 – Asset inventory & extraction blueprint**  
   - Catalogue every inline console script (OCRA evaluate/replay, potential legacy snippets) and describe extraction seams.  
   - Produce a short appendix (added to the spec) that maps each DOM dependency to a planned helper.  
   - Commands: documentation-only edits; no build required.  
   - _Status:_ Pending.

2. **I2 – Shared DOM/test harness scaffolding**  
   - Introduce `rest-api/src/test/javascript/support/` with DOM stubs, event helpers, fetch spies, and SecretFieldBridge mocks.  
   - Port the existing EMV tests onto the harness to validate design decisions.  
   - Commands: `node --test rest-api/src/test/javascript/emv/console.test.js`.  
   - _Status:_ Pending.

3. **I3 – Protocol adoption wave #1 (HOTP/TOTP/FIDO2)**  
   - Extract any lingering inline logic, add unit suites for evaluate/replay toggles, preset hydration, seeding, preview windows, and verbose traces.  
   - Update Selenium tests only where assertions overlap; primary verification runs via Node.  
   - Commands: `node --test rest-api/src/test/javascript/{hotp,totp,fido2}/console.test.js`, targeted Gradle check.  
   - _Status:_ Pending.

4. **I4 – OCRA controller extraction & coverage**  
   - Move the OCRA evaluate/replay scripts out of Thymeleaf fragments, expose initialiser functions, and add Node suites for mode toggles, credential directory fetch, preset hydration, and inline validation.  
   - Retire any duplicated logic once tests are green.  
   - Commands: `./gradlew --no-daemon :rest-api:test --tests "*Ocra*"`, `node --test rest-api/src/test/javascript/ocra/*.test.js`.  
   - _Status:_ Pending.

5. **I5 – Gradle integration + contributor docs**  
   - Replace `emvConsoleJsTest` with `operatorConsoleJsTest` (or equivalent aggregate task), wire it under `check`, and document usage in README/CONTRIBUTING.  
   - Add task filtering knobs for local runs and capture the new pipeline in `docs/_current-session.md` + roadmap notes.  
   - Commands: `./gradlew --no-daemon operatorConsoleJsTest check`.  
   - _Status:_ Pending.

6. **I6 – Governance & future-proofing**  
   - Add harness onboarding notes to `docs/4-architecture/tasks/feature-041-...` so every new protocol includes the same steps.  
   - Record Implementation Drift Gate expectations and prepare outstanding follow-ups (e.g., coverage thresholds) before closing the workstream.  
   - Commands: documentation + quality gate rerun.  
   - _Status:_ Pending.

## Risks & Mitigations
- **DOM parity gaps:** Mitigate by building exhaustive fixture coverage per protocol before ripping out inline scripts.  
- **Test flakiness in Node harness:** Keep helpers deterministic, avoid timers, and rely on synchronous stubs.  
- **Gradle task duration:** Allow per-protocol filtering and consider parallel `node --test` invocation if runtime exceeds CI budgets.

## Exit Criteria
- Spec + plan + tasks reflect the final harness design and per-protocol coverage.  
- All console protocols run under Node tests, Gradle `check` depends on the aggregate task, and docs direct contributors to the new workflow.  
- Roadmap entry marked “Complete” only after governance confirms ongoing maintenance expectations (including future protocol hooks) are documented.
