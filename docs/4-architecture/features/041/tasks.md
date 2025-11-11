# Feature 041 Tasks – Operator Console JavaScript Modularization & Test Harness

| Field | Value |
|-------|-------|
| Status | Not started |
| Last updated | 2025-11-10 |
| Linked plan | `docs/4-architecture/features/041/plan.md` |

> Align tasks with the Increment Map. Include `_Intent`, `_Verification commands`, and `_Notes` for every entry; record executed commands under the verification log.

## Checklist
- [ ] T4101 – Console asset inventory & extraction notes (S41-01).  
  _Intent:_ Catalogue every inline script fragment (OCRA evaluate/replay, preset loaders) and capture extraction seams + DOM dependencies inside the spec appendix; update roadmap + `_current-session.md`.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check` (docs only)  
  _Notes:_ Include table mapping templates to extracted assets.

- [ ] T4102 – Shared Node harness scaffolding (S41-02, S41-06).  
  _Intent:_ Create `rest-api/src/test/javascript/support/` utilities (DOM stubs, SecretFieldBridge/VerboseTrace mocks, fetch spies), port EMV tests to the harness, and add contributing notes for running suites locally.  
  _Verification commands:_  
  - `node --test rest-api/src/test/javascript/emv/console.test.js`  
  - `./gradlew --no-daemon operatorConsoleJsTest` (once wired)  
  _Notes:_ Document harness API surface in `support/README.md`.

- [ ] T4103 – HOTP/TOTP/FIDO2 coverage wave (S41-03, S41-04).  
  _Intent:_ Extract leftover inline helpers, add Node suites for evaluate/replay toggles, preset hydration, seeding, preview windows, verbose trace wiring, and secret-field messaging across HOTP/TOTP/FIDO2; refresh Selenium assertions only where expectations change.  
  _Verification commands:_  
  - `node --test rest-api/src/test/javascript/hotp/*.test.js`  
  - `node --test rest-api/src/test/javascript/totp/*.test.js`  
  - `node --test rest-api/src/test/javascript/fido2/*.test.js`  
  - `./gradlew --no-daemon operatorConsoleJsTest -PconsoleTestFilter=hotp,totp,fido2`  
  _Notes:_ Capture fixture IDs inside spec DSL.

- [ ] T4104 – OCRA controller extraction + tests (S41-05).  
  _Intent:_ Move OCRA evaluate/replay scripts into standalone assets, expose controller factories, and add Node tests covering credential directory fetch flows, preset hydration, inline validation, and replay toggles.  
  _Verification commands:_  
  - `node --test rest-api/src/test/javascript/ocra/*.test.js`  
  - `./gradlew --no-daemon operatorConsoleJsTest -PconsoleTestFilter=ocra`  
  _Notes:_ Remove Thymeleaf inline snippets; reference new asset paths in spec.

- [ ] T4105 – Gradle integration & documentation (S41-07).  
  _Intent:_ Replace `emvConsoleJsTest` with consolidated `operatorConsoleJsTest`, wire it under `check`, document usage in README/CONTRIBUTING, and update roadmap/knowledge-map entries.  
  _Verification commands:_  
  - `./gradlew --no-daemon operatorConsoleJsTest check`  
  - `./gradlew --no-daemon spotlessApply check`  
  _Notes:_ Ensure property `-PconsoleTestFilter` documented with examples.

- [ ] T4106 – Governance & future-protocol checklist (S41-08).  
  _Intent:_ Extend this tasks file with onboarding steps for future protocols, record Implementation Drift Gate expectations, and keep the spec in Draft until governance confirms compliance.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`  
  _Notes:_ Append future-protocol template snippet to spec appendix.

## Verification log
- 2025-11-10 – `./gradlew --no-daemon spotlessApply check` (doc/template migration sweep)
- _(pending)_ – `./gradlew --no-daemon operatorConsoleJsTest`

## Notes / TODOs
- Coordinate with Feature 040 before touching shared verbose trace console scripts to avoid merge conflicts.
