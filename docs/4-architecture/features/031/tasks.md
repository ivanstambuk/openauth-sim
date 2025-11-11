# Feature 031 Tasks – Legacy Entry-Point Removal

_Linked plan:_ `docs/4-architecture/features/031/plan.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

> Checklist mirrors the plan increments; each task recorded its verification commands when it closed.

## Checklist
- [x] T-031-01 – CLI telemetry tests (FR-031-01, S-031-01).  
  _Intent:_ Add failing tests that assert adapter-only events before removing `legacyEmit`.  
  _Verification:_ `./gradlew --no-daemon :cli:test` (2025-10-19).

- [x] T-031-02 – Remove `legacyEmit` branch (FR-031-01, S-031-01).  
  _Intent:_ Delete CLI legacy path and refresh integration expectations.  
  _Verification:_ `./gradlew --no-daemon :cli:test`, `./gradlew --no-daemon spotlessApply check` (2025-10-19).

- [x] T-031-03 – Router cleanup (FR-031-02, S-031-02).  
  _Intent:_ Remove `__openauth*` globals and legacy query-param coercion in the main console router.  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.OperatorConsoleUnificationSeleniumTest*"`, `./gradlew spotlessApply check` (2025-10-19).

- [x] T-031-04 – HOTP/TOTP router parity (FR-031-02, S-031-02).  
  _Intent:_ Apply the canonical router state to HOTP/TOTP consoles and fix Selenium navigation.  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*Hotp*" "*Totp*"` (2025-10-19).

- [x] T-031-05 – FIDO2 canonical API (FR-031-03, S-031-03).  
  _Intent:_ Remove `legacySetMode` bridge, rely on canonical ceremony helpers, update tests.  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*Fido2*"`, `./gradlew spotlessApply check` (2025-10-19).

- [x] T-031-06 – Fetch-only networking (FR-031-03, S-031-03).  
  _Intent:_ Drop XMLHttpRequest fallbacks, enable HtmlUnit fetch polyfill, rerun UI suites.  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.*"`, `./gradlew spotlessApply check` (2025-10-19).

- [x] T-031-07 – WebAuthn preset cleanup (FR-031-04, S-031-04).  
  _Intent:_ Prune legacy generator sample, align presets/docs/tests with W3C identifiers.  
  _Verification:_ `./gradlew --no-daemon :application:test`, `./gradlew --no-daemon :rest-api:test --tests "*WebAuthn*"` (2025-10-19).

- [x] T-031-08 – Documentation & analysis gate (FR-031-04, S-031-04).  
  _Intent:_ Update roadmap/knowledge map/session snapshot/how-to guides; rerun analysis gate.  
  _Verification:_ `./gradlew --no-daemon spotlessApply check` (2025-10-19).

## Verification Log
- 2025-10-19 – `./gradlew --no-daemon :cli:test`
- 2025-10-19 – `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.*"`
- 2025-10-19 – `./gradlew --no-daemon :application:test`
- 2025-10-19 – `./gradlew --no-daemon spotlessApply check`

## Notes / TODOs
- HtmlUnit fetch polyfill remains documented for Selenium harnesses; no further legacy entry points remain.
