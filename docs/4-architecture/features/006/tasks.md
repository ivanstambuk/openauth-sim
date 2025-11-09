# Feature 006 Tasks – OCRA Operator UI

_Status:_ Complete  
_Last updated:_ 2025-11-09_

## Checklist
- [x] T0601 – Sync spec/plan/roadmap, capture templating + fetch decisions, and retire open questions (S06-01–S06-06).
  _Intent:_ Lock architecture/UX scope before building controllers/templates.
  _Verification commands:_
  - `less docs/4-architecture/features/006/spec.md`
  - `rg -n "Feature 006" docs/4-architecture/roadmap.md`

- [x] T0602 – Add failing MockMvc + JS unit tests covering inline fetch submissions, stored credential mode, and sanitized error handling (UI-OCRA-001–UI-OCRA-006, S06-01, S06-02, S06-04).
  _Intent:_ Ensure validation logic and telemetry surfaces are defined before implementation.
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test --tests "*OcraOperatorUiControllerTest"`
  - `node --test ui/static/tests/ocra-console.test.js`

- [x] T0603 – Implement controllers, services, and templates for inline + stored modes, wiring fetch submissions and telemetry logging (S06-01, S06-02, S06-04).
  _Intent:_ Drive T0602 tests green while keeping secrets redacted and CSRF/fetch plumbing stable.
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test`
  - `./gradlew --no-daemon spotlessApply`

- [x] T0604 – Expand preset catalogue, sample loader, and replay helpers with curated vectors plus stored credential hydration (S06-02, S06-03).
  _Intent:_ Guarantee curated flows stay deterministic and cover both inline and stored presets.
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test --tests "*Preset*"`
  - `./gradlew --no-daemon :rest-api:test --tests "*Replay*"`

- [x] T0605 – Add Selenium/accessibility coverage for layout, ARIA landmarks, timestamp toggles, and mode-specific CTAs (S06-03, S06-05, S06-06).
  _Intent:_ Prevent regressions in responsive behaviour and timestamp auto-fill UX.
  _Verification commands:_
  - `./gradlew --no-daemon :ui:test --tests "*OcraConsoleSeleniumTest"`
  - `./gradlew --no-daemon :rest-api:test --tests "*Accessibility*"`

- [x] T0606 – Refresh operator docs/how-to content, capture telemetry copy updates, and run `./gradlew spotlessApply check` + targeted Selenium suite (S06-01–S06-06).
  _Intent:_ Close the feature with documentation parity and a green build.
  _Verification commands:_
  - `rg -n "OCRA operator" docs/2-how-to`
  - `./gradlew --no-daemon spotlessApply check`

## Notes / TODOs
- Historic R02x/R09x task ledger (async fetch retrofit, timestamp toggle phases) remains available in git history prior to 2025-11-09 for forensic review.
