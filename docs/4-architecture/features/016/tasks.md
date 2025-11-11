# Feature 016 Tasks – OCRA UI Replay

_Status: In Progress_  
_Last updated: 2025-11-10_

> Keep this checklist aligned with the feature plan increments. Stage tests before implementation, record verification commands beside each task, and prefer bite-sized entries (≤90 minutes).
> When referencing requirements, keep feature IDs (`F-016-xx`), non-goal IDs (`N-016-xx`), and scenario IDs (`S-016-xx`) inside the same parentheses immediately after the task title.

## Checklist
- [x] T-016-01 – Stage Replay navigation/system coverage (F-016-01, S-016-01).  
  _Intent:_ Add failing Selenium coverage for stored/inline replay navigation before UI implementation.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.OperatorConsoleReplaySeleniumTest"` (expected red)  
  _Notes:_ 2025-10-03 – Test failed as expected; documented red state in plan R1602.

- [x] T-016-02 – Extend Selenium coverage for inline replay validation errors (F-016-03, S-016-03).  
  _Intent:_ Capture failing inline success + validation paths ahead of implementation.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleReplaySeleniumTest"` (red)  
  _Notes:_ 2025-10-03 – Inline cases added and left failing prior to UI wiring.

- [x] T-016-03 – Add MockMvc/WebTestClient coverage for replay controller/service wiring (F-016-02, F-016-05, S-016-02, S-016-04).  
  _Intent:_ Ensure `/api/v1/ocra/verify` + telemetry endpoints expose `mode`, `credentialSource`, and sanitized results before UI calls them.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OcraVerificationEndpointTest"`  
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.OperatorConsoleTelemetryLoggerTest"`  
  _Notes:_ 2025-10-03 – Tests green; metadata + telemetry hooks verified.

- [x] T-016-04 – Implement Replay template/controller wiring (F-016-01–F-016-04, S-016-01/S-016-02).  
  _Intent:_ Build Thymeleaf fragments, controllers, and JS to make stored/inline flows pass system tests.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleReplaySeleniumTest"`  
  - `./gradlew --no-daemon spotlessApply check`  
  _Notes:_ 2025-10-03 – UI renders replay screen; Selenium + Gradle gate green.

- [x] T-016-05 – Add telemetry instrumentation + unit tests for replay events (F-016-05, NFR-016-03, S-016-04).  
  _Intent:_ Extend telemetry adapters with mode/outcome/fingerprint fields and cover sanitisation behaviour.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.OperatorConsoleTelemetryLoggerTest"`  
  _Notes:_ 2025-10-03 – Telemetry adapter parity verified; UI posts sanitized payloads.

- [x] T-016-06 – Update operator how-to + telemetry docs (F-016-05, S-016-04).  
  _Intent:_ Document replay workflow, telemetry fields, and troubleshooting steps.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check` (docs focus)  
  _Notes:_ 2025-10-03 – Guides now cite Replay usage + telemetry IDs.

- [x] T-016-07 – Run targeted Gradle gate + self-review (all FR/NFR).  
  _Intent:_ Execute `:rest-api:test :rest-api:systemTest spotlessApply check`, capture notes before staging/commit.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test :rest-api:systemTest spotlessApply check`  
  _Notes:_ 2025-10-03 – Build green; notes captured in plan R1606.

- [x] T-016-08 – Add Selenium assertion for inline preset dropdown behaviour (F-016-03, S-016-03).  
  _Intent:_ Ensure presets remain inline-only and hidden during stored mode.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleReplaySeleniumTest"` (red before fix)  
  _Notes:_ 2025-10-03 – Red test logged awaiting implementation.

- [x] T-016-09 – Surface inline presets on Replay view (F-016-03, S-016-03).  
  _Intent:_ Wire controller/template/JS to show presets when inline mode active and rerun quality gate.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleReplaySeleniumTest"`  
  - `./gradlew --no-daemon spotlessApply check`  
  _Notes:_ 2025-10-03 – Preset dropdown visible + functional; tests green.

- [x] T-016-10 – Hide/disable presets when stored mode is active (F-016-02, S-016-02).  
  _Intent:_ Prevent stored flows from exposing inline preset UI.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleReplaySeleniumTest"`  
  _Notes:_ 2025-10-03 – Selenium stored-mode assertion now passes.

- [x] T-016-11 – Add expected OTP values to inline presets (F-016-03, S-016-03).  
  _Intent:_ Extend preset catalogue to include OTP strings for replay auto-fill + docs.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleReplaySeleniumTest"`  
  _Notes:_ 2025-10-03 – Presets align with `docs/ocra_validation_vectors.json`.

- [x] T-016-12 – Auto-fill OTP when preset selected (F-016-03, S-016-03).  
  _Intent:_ Update JS to populate suite/secret/context/OTP and assert via Selenium.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleReplaySeleniumTest"`  
  - `./gradlew --no-daemon spotlessApply check`  
  _Notes:_ 2025-10-03 – OTP auto-fill confirmed; replay passes without manual entry.

- [x] T-016-13 – Align Replay result card styling with Evaluate console (F-016-04, S-016-01).  
  _Intent:_ Mirror status badge, telemetry grid, and copy helpers.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleReplaySeleniumTest"`  
  _Notes:_ 2025-10-03 – UI + Selenium assertions updated.

- [x] T-016-14 – Add failing Selenium coverage for inline auto-fill action removal (F-016-03, S-016-03).  
  _Intent:_ Stage red test proving presets should auto-fill without extra button.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleReplaySeleniumTest"` (red)  
  _Notes:_ 2025-10-04 – Coverage added before UI tweak.

- [x] T-016-15 – Implement inline auto-fill control removal + preset wiring (F-016-03, S-016-03).  
  _Intent:_ Remove the temporary button, ensure selecting a preset populates immediately, rerun Gradle gate.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleReplaySeleniumTest"`  
  - `./gradlew --no-daemon spotlessApply check`  
  _Notes:_ 2025-10-04 – Auto-fill immediate; quality gate green.

- [x] T-016-16 – Update operator UI docs/telemetry guides for auto-fill removal (F-016-05, S-016-04).  
  _Intent:_ Describe preset behaviour + telemetry implications post-change.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`  
  _Notes:_ 2025-10-04 – Docs refreshed with new wording.

- [x] T-016-17 – Remove inline auto-fill button artefacts (F-016-03, S-016-03).  
  _Intent:_ Clean up templates/JS/tests after confirming presets auto-fill instantly.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleReplaySeleniumTest"`  
  _Notes:_ 2025-10-04 – Legacy button removed; tests green.

- [x] T-016-18 – Document preset removal across spec/knowledge-map (F-016-03, S-016-04).  
  _Intent:_ Sync spec, plan, tasks, and knowledge map with the new UX.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`  
  _Notes:_ 2025-10-04 – Docs + knowledge map updated.

- [x] T-016-19 – Drop Replay "Mode" metadata row per user directive (F-016-04, S-016-01).  
  _Intent:_ Simplify result card while keeping reason/outcome + telemetry.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleReplaySeleniumTest"`  
  - `./gradlew --no-daemon spotlessApply check`  
  _Notes:_ 2025-10-04 – Result card updated; Selenium asserts new layout.

- [ ] T-016-20 – Remove stored replay “Load sample data” control, auto-fill on credential selection (F-016-02, S-016-02, S-016-04).  
  _Intent:_ Finalise stored UX per Clarification 2025-10-15, refresh Selenium/MockMvc coverage, and rerun Gradle gate.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleReplaySeleniumTest"`  
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OcraVerificationEndpointTest"`  
  - `./gradlew --no-daemon spotlessApply check`  
  _Notes:_ Pending – will also update docs/migration tracker once auto-fill lands.

## Verification Log (Optional)
- 2025-10-04 – `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.OperatorConsoleReplaySeleniumTest" --info`
- 2025-10-04 – `./gradlew --no-daemon spotlessApply check`

## Notes / TODOs
- T-016-20 remains open; prioritise once current template-migration sweep completes.
- Monitor Selenium runtime; split replay suite if flakiness resurfaces when MapDB copies are slow.
