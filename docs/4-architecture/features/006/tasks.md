
# Feature 006 Tasks – OCRA Operator UI

_Status:_ Complete  
_Last updated:_ 2025-11-10_

## Checklist
- [x] T-006-01 – Sync spec/plan/roadmap, capture templating + fetch decisions, and retire open questions (FR-006-01–FR-006-11, S-006-01–S-006-06).
  _Intent:_ Lock architecture/UX scope before building controllers/templates.
  _Verification commands:_
  - `less docs/4-architecture/features/006/spec.md`
  - `rg -n "Feature 006" docs/4-architecture/roadmap.md`

- [x] T-006-02 – Add failing MockMvc + JS unit tests covering inline fetch submissions, stored credential mode, and sanitized error handling (FR-006-02, FR-006-03, FR-006-04, S-006-01, S-006-02, S-006-04).
  _Intent:_ Ensure validation logic and telemetry surfaces are defined before implementation.
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test --tests "*OcraOperatorUiControllerTest"`
  - `node --test ui/static/tests/ocra-console.test.js`

- [x] T-006-03 – Implement controllers, services, and templates for inline + stored modes, wiring fetch submissions and telemetry logging (FR-006-02, FR-006-03, FR-006-04, FR-006-05, FR-006-06, S-006-01, S-006-02, S-006-04).
  _Intent:_ Drive the red tests from T-006-02 green while keeping secrets redacted and CSRF/fetch plumbing stable.
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test`
  - `./gradlew --no-daemon spotlessApply`

- [x] T-006-04 – Expand preset catalogue, sample loader, and replay helpers with curated vectors plus stored credential hydration (FR-006-07, FR-006-08, FR-006-10, S-006-02, S-006-03).
  _Intent:_ Guarantee curated flows stay deterministic and cover both inline and stored presets.
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test --tests "*Preset*"`
  - `./gradlew --no-daemon :rest-api:test --tests "*Replay*"`

- [x] T-006-05 – Add Selenium/accessibility coverage for layout, ARIA landmarks, timestamp toggles, and mode-specific CTAs (FR-006-05, FR-006-06, FR-006-11, S-006-05, S-006-06).
  _Intent:_ Prevent regressions in responsive behaviour and timestamp auto-fill UX.
  _Verification commands:_
  - `./gradlew --no-daemon :ui:test --tests "*OcraConsoleSeleniumTest"`
  - `./gradlew --no-daemon :rest-api:test --tests "*Accessibility*"`

- [x] T-006-06 – Refresh operator docs/how-to content, capture telemetry copy updates, and run `./gradlew spotlessApply check` + targeted Selenium suite (FR-006-01–FR-006-11, S-006-01–S-006-06).
  _Intent:_ Close the feature with documentation parity and a green build.
  _Verification commands:_
  - `rg -n "OCRA operator" docs/2-how-to`
  - `./gradlew --no-daemon spotlessApply check`

## Verification Log
- 2025-11-09 – `./gradlew --no-daemon spotlessApply check`
- 2025-11-10 – `./gradlew --no-daemon spotlessApply check`

## Notes / TODOs
- Historic R02x/R09x task ledger (async fetch retrofit, timestamp toggle phases) remains available in git history prior to 2025-11-09 for forensic review.
