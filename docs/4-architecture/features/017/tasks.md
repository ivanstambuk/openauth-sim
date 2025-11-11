# Feature 017 Tasks – Operator Console Unification

_Status: Complete_  
_Last updated: 2025-11-10_

> Keep this checklist aligned with the feature plan increments. All tasks below completed with tests staged before
> implementation and ≤30-minute slices.

## Checklist
- [x] T-017-01 – Add red Selenium journey for the consolidated `/ui/console` entry (FR-017-01, FR-017-03, S-017-01).  
  _Intent:_ Prove `/ui/console` must exist, render OCRA tabs, and apply dark theme tokens before implementation.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleUnificationSeleniumTest"`  
  _Notes:_ Test failed prior to I1 implementation, forcing the unified route.

- [x] T-017-02 – Update legacy Selenium suites to target `/ui/console` while preserving regression coverage (FR-017-01, S-017-01).  
  _Intent:_ Keep historic OCRA flows green after route rename.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OcraOperatorUi*"`

- [x] T-017-03 – Implement unified Thymeleaf template, tab shell, and neutral stylesheet path (FR-017-01, FR-017-03, FR-017-13, S-017-01).  
  _Intent:_ Bring `/ui/console` live with shared layout + `/ui/console/console.css`.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test`  
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-017-04 – Introduce responsive dark theme tokens and accessibility checks (FR-017-03, NFR-017-02, S-017-01).  
  _Intent:_ Validate theme meets WCAG + responsive constraints.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleThemeSeleniumTest"`

- [x] T-017-05 – Stage failing tests for inline/stored toggles and telemetry payloads (FR-017-02, FR-017-04, S-017-02).  
  _Intent:_ Ensure evaluation/replay logic moves into unified console with telemetry parity.  
  _Verification commands:_  
  - `./gradlew --no-daemon :application:test --tests "*OcraTelemetryContractTest"`  
  - `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleEvaluationRedTest"`

- [x] T-017-06 – Rewire evaluation + replay forms, ensuring REST + telemetry tests pass (FR-017-02, FR-017-04, S-017-02).  
  _Intent:_ Move both flows into `/ui/console` and stop invoking legacy templates.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test`  
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-017-07 – Remove legacy `/ui/ocra/evaluate` and `/ui/ocra/replay` routes; add redirect coverage (FR-017-06, S-017-04).  
  _Intent:_ Ensure `/ui/console` is sole entry point.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*LegacyRouteRedirectTest"`

- [x] T-017-08 – Verify "Load a sample vector" placement via Selenium diff (FR-017-07, S-017-04).  
  _Intent:_ Enforce replay control order relative to mode selector.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*ReplaySamplePlacementTest"`

- [x] T-017-09 – Remove Telemetry ID / Credential Source / Context Fingerprint / Sanitized fields from replay results (FR-017-08, S-017-02).  
  _Intent:_ Trim replay metadata per spec while keeping remaining info intact.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleReplayMetadataTest"`

- [x] T-017-10 – Stack evaluation + replay metadata into single-row entries and drop Suite/Sanitized rows (FR-017-09, FR-017-10, S-017-02).  
  _Intent:_ Finalise result card layout.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleResultLayoutTest"`

- [x] T-017-11 – Add failing tests for `Seed sample credentials` visibility, append-only enforcement, and warning state (FR-017-11, S-017-03).  
  _Intent:_ Lock expected behaviour before wiring backend.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OcraOperatorUiSeedingSeleniumTest"`

- [x] T-017-12 – Implement seeding endpoint + application service integration with telemetry (FR-017-11, S-017-03).  
  _Intent:_ Append canonical suites via REST, emit `ocra.seed`.  
  _Verification commands:_  
  - `./gradlew --no-daemon :application:test --tests "*OcraSeedApplicationServiceTest"`  
  - `./gradlew --no-daemon :rest-api:test --tests "*OcraCredentialSeedEndpointTest"`

- [x] T-017-13 – Refresh docs/knowledge map for seeding workflow and rerun spotless/check (FR-017-05, S-017-05).  
  _Intent:_ Document UI control, telemetry, and fixtures.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-017-14 – Add failing tests for query-parameter deep links + history navigation (FR-017-12, S-017-04).  
  _Intent:_ Force implementation of router/state sync.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleHistorySeleniumTest"`

- [x] T-017-15 – Implement query-parameter router + history handling (FR-017-12, S-017-04).  
  _Intent:_ Keep protocol/tab in sync with URL + history stack.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleHistorySeleniumTest"`

- [x] T-017-16 – Document stateful URLs + telemetry intent; rerun spotless/check (FR-017-05, S-017-05).  
  _Intent:_ Capture navigation guidance for future operators.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-017-17 – Add failing tests ensuring evaluation result no longer renders Sanitized row (FR-017-10, S-017-02).  
  _Intent:_ Guard removal of Sanitized indicator.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleSanitizedRowTest"`

- [x] T-017-18 – Remove Sanitized row from evaluation results and update Selenium expectations (FR-017-10, S-017-02).  
  _Intent:_ Finalise result card minimalist view.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test`

- [x] T-017-19 – Add Selenium coverage for seeding status placement beneath the button (FR-017-11, S-017-03).  
  _Intent:_ Lock layout when registry empties.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OcraOperatorUiSeedingSeleniumTest"`

- [x] T-017-20 – Implement seeding status layout, dropdown refresh, and warning styling (FR-017-11, S-017-03).  
  _Intent:_ Provide user feedback after seeding attempts.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test`  
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-017-21 – Add coverage asserting zero-added seeding responses show warning styling (FR-017-11, S-017-03).  
  _Intent:_ Prevent UX regressions when nothing new is seeded.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OcraOperatorUiSeedingSeleniumTest"`

- [x] T-017-22 – Implement warning styling + telemetry flag for zero-added runs (FR-017-11, S-017-03).  
  _Intent:_ Distinguish noop seeding attempts.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test`  
  - `./gradlew --no-daemon :application:test --tests "*OcraSeedTelemetryTest"`

- [x] T-017-23 – Relocate and fingerprint the shared console stylesheet (`/ui/console/console.css`) (FR-017-13, S-017-05).  
  _Intent:_ Decouple theme asset from OCRA-specific paths.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*ConsoleStylesheetTest"`

- [x] T-017-24 – Update roadmap/migration tracker/current session entries for the unified console (FR-017-05, S-017-05).  
  _Intent:_ Keep governance artefacts aligned.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`

## Verification Log
- 2025-10-31 – `./gradlew --no-daemon :rest-api:test :application:test spotlessApply check`
- 2025-11-10 – `./gradlew --no-daemon spotlessApply check` (doc-only verification sweep)

## Notes / TODOs
- Future work (Feature 041) will modularise the JS router/console assets; keep references to `UI-017-03` ready for that
  extraction.
