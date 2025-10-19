# Feature Plan 031 – Legacy Entry-Point Removal

_Status: Draft_  
_Last reviewed: 2025-10-19_

## Alignment
- Specification: `docs/4-architecture/specs/feature-031-legacy-entrypoint-removal.md`
- Related roadmap entry: Workstream 31 (pending)
- Upstream dependencies: Feature 017 (operator console unification), Feature 024 (FIDO2 UI), Feature 026 (FIDO2 attestation telemetry), Feature 027 (unified credential store)
- Downstream considerations: CLI telemetry consumers, operator console automation scripts, Selenium test harness

## Intent
Retire the Java/JavaScript compatibility branches so there is a single telemetry format, router state contract, and networking pathway across all facades. This plan sequences test-first removal in ≤10 minute increments to keep the build green while we excise the legacy affordances.

## Current Status
- Specification drafted 2025-10-19. T3101–T3104 completed on 2025-10-19 delivering adapter-only CLI telemetry and unified HOTP/TOTP/FIDO2 routing without `__openauth*` shims or legacy query parameters.
- No open questions logged; REST contract tightening explicitly out of scope per product directive.
- 2025-10-19 – T3105 removed the FIDO2 `legacySetMode` bridge, rewired the console to emit canonical tab/mode events, and refreshed Selenium coverage (`OperatorConsoleUnificationSeleniumTest.fido2ConsoleProvidesCanonicalApi`). Commands executed: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.*"` and `./gradlew --no-daemon spotlessApply check`.
- 2025-10-19 – T3106 replaced HOTP/TOTP/FIDO2 operator console XHR fallbacks with Fetch API calls, enabled HtmlUnit’s built-in fetch polyfill across Selenium harnesses, and revalidated UI suites (`:rest-api:test --tests "io.openauth.sim.rest.ui.*"`) plus `spotlessApply check`.
- 2025-10-19 – T3107 aligned WebAuthn generator presets with W3C fixture identifiers (`packed-es256`, etc.), removed the legacy `generator-*` fallback, refreshed CLI/REST/operator documentation, and updated Selenium coverage. Commands executed: `./gradlew --no-daemon :application:test`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.*"`.
- 2025-10-19 – T3108 synced knowledge artefacts (roadmap, knowledge map, how-to guides, session snapshot), captured the HtmlUnit fetch polyfill requirement, and reran the analysis gate (`spotlessApply check` + targeted UI suites) to confirm a green baseline.

## Increments
1. T3101 – Update CLI telemetry tests to lock the structured adapter output; add regression for `OcraCli.emit` covering evaluate/verify cases only. _Tests first._
2. T3102 – Remove `legacyEmit` path; ensure CLI integration tests emit structured frames exclusively.
3. T3103 – In operator console router (`ui/ocra/console.js`), delete `__openauth*` shims and legacy query-param coercion; add targeted front-end unit test covering canonical params.
4. T3104 – Mirror router cleanup in HOTP/TOTP consoles; adjust Selenium tests to navigate via canonical params.
5. T3105 – Drop FIDO2 `legacySetMode` bridge and legacy mode persistence; update FIDO2 Selenium flows and HtmlUnit harness to use the canonical API.
6. T3106 – Remove XMLHttpRequest fallbacks; enable HtmlUnit’s built-in `fetch` polyfill in Selenium harnesses; rerun impacted suites.
7. T3107 – Prune WebAuthn legacy generator sample; update preset fixtures, docs, and UI tests so presets use W3C fixture identifiers (for example `packed-es256`) and sanitized synthetic keys where required.
8. T3108 – Doc sync: refresh operator how-to, CLI telemetry reference, knowledge map, roadmap workstream, and `_current-session` summary; run analysis gate checklist.

## Quality Gates
- Every increment runs `./gradlew spotlessApply check` (document pass/fail per increment).
- Touching UI routing or fetch logic triggers targeted suites: `:rest-api:test --tests "*OperatorUi*"` and `:rest-api:test --tests "*Fido2*"`.
- Before closing the feature, re-run `./gradlew --no-daemon :rest-api:test` and ensure Selenium snapshots updated.

## Risks & Mitigations
- CLI telemetry consumers parsing legacy text: coordinate release notes and provide migration guidance before landing the breaking change.
- HtmlUnit lacking native `fetch`: ensure tests inject a polyfill or swap to a driver with Fetch support.
- Bookmark breakage: update documentation and changelog so operators adjust automation scripts.

## Exit Criteria
- No remaining references to `legacyEmit`, `legacySetMode`, `__openauth*` shims, or XMLHttpRequest fallbacks in production code.
- Tests and docs reference only canonical parameters and samples.
- Knowledge map records the simplified routing/telemetry relationships.
- Feature recorded as shipped with roadmap + tasks updated.
