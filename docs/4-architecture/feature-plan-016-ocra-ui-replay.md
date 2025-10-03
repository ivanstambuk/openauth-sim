# Feature Plan 016 – OCRA UI Replay

_Status: Complete_
_Last updated: 2025-10-03_

## Objective
Extend the operator console with a dedicated replay screen that supports stored and inline OCRA verification flows by calling `/api/v1/ocra/verify`, surfaces outcome/telemetry details, and maintains accessibility and telemetry guardrails alongside existing evaluation tooling.

Reference specification: `docs/4-architecture/specs/feature-016-ocra-ui-replay.md`.

## Success Criteria
- Replay screen reachable from the operator console navigation, with mode selection (stored vs inline) and context inputs mirroring CLI/REST options.
- Stored mode fetches credential inventory from the REST facade and submits verification payloads end-to-end.
- Inline mode accepts suite/secret inputs, validates context, and sends correct payloads to `/api/v1/ocra/verify`.
- UI renders match/mismatch/validation outcomes with telemetry identifiers and reason codes, matching REST semantics.
- Replay interactions emit enhanced telemetry (mode, outcome classification, hashed payload fingerprints) without exposing secrets.
- Documentation (how-to guides, telemetry references) updated to cover UI replay usage and logging.

## Proposed Increments
- ☑ R1601 – Capture clarifications, draft spec, update roadmap/knowledge map/open-questions. (2025-10-03 – Spec/plan/tasks synced; no open questions.)
- ☑ R1602 – Author failing Selenium system coverage for replay navigation, stored credential submission, and inline flows. (2025-10-03 – Added `OcraOperatorUiReplaySeleniumTest` expecting missing screen, documented failure in Notes.)
- ☑ R1603 – Add MockMvc/WebTestClient tests for replay controller/service wiring, including telemetry expectations and REST error handling. (2025-10-03 – `OcraReplayControllerTest` and telemetry verifications passing.)
- ☑ R1604 – Implement replay screen templates, controllers, and REST wiring to satisfy stored/inline flows with telemetry. (2025-10-03 – UI renders replay screen; REST + telemetry suites green.)
- ☑ R1605 – Polish UI copy/accessibility, update telemetry adapters, and rerun Selenium suites. (2025-10-03 – Replay Selenium tests green with hashed fingerprints + WCAG contrast checks.)
- ☑ R1606 – Refresh operator how-to/telemetry docs, rerun `./gradlew spotlessApply check`, and stage notes for commit. (2025-10-03 – Docs updated for replay workflow; quality gate reused configuration cache and passed.)
- ☑ R1607 – Extend Selenium coverage to assert replay inline mode exposes the inline sample preset selector and auto-fills fields when a preset is chosen. (2025-10-03 – Failing test observed prior to implementation.)
- ☑ R1608 – Update replay controller/template/JS to surface inline presets (Option A), ensure dropdown drives field population, then rerun Selenium + `./gradlew spotlessApply check`. (2025-10-03 – Tests + quality gate green.)
- ☑ R1609 – Ensure replay inline presets remain hidden/disabled when stored mode is active, mirroring evaluation console behaviour. (2025-10-03 – Template/JS updated, preset container toggled with mode.)
- ☑ R1610 – Extend Selenium coverage for stored mode preset visibility, then rerun `./gradlew :rest-api:test spotlessApply check`. (2025-10-03 – Stored-mode assertion verifies selector hidden; build commands green.)
- ☑ R1611 – Extend inline sample definitions/tests to include expected OTP data for replay presets. (2025-10-03 – Presets include computed OTP strings mirrored in docs/tests.)
- ☑ R1612 – Update replay template/JS to populate OTP when a preset is selected; rerun UI Selenium and quality gate commands. (2025-10-03 – UI auto-fills OTP; Selenium asserts non-empty and successful replay.)
- ☑ R1613 – Tidy replay result card styling (status emphasis, telemetry rows) to match evaluation console. (2025-10-03 – Result badge + telemetry grid align with evaluation UI.)

Each increment should take ≤10 minutes and finish with the relevant tests red→green before moving on.

## Checklist Before Implementation
- [x] Specification updated with clarifications and requirements (`feature-016-ocra-ui-replay.md`).
- [x] Open questions resolved (`docs/4-architecture/open-questions.md`).
- [x] Tasks list drafted with tests-first ordering.
- [x] Analysis gate rerun once plan/tasks are in sync.

## Tooling Readiness
- Selenium suite: `./gradlew :rest-api:systemTest` (or equivalent) to execute UI browser flows.
- REST/UI integration tests: `./gradlew :rest-api:test` (MockMvc) and `./gradlew :rest-api:integrationTest` if split.
- Quality gate: `./gradlew spotlessApply check` after each self-contained increment.

## Notes
Use this section to log telemetry schema updates, notable UI decisions, and benchmark/latency observations as work proceeds.
- 2025-10-03 – Quality gate run via `./gradlew :rest-api:test spotlessApply check` (no `systemTest` task defined for rest-api); recorded as baseline before UI implementation.
- 2025-10-03 – Added Selenium replay suite (`OcraOperatorUiReplaySeleniumTest`) covering stored and inline flows; initial run failed before the replay screen existed, documenting the red state for R1602.
- 2025-10-03 – Confirmed with owner that T1602 covers inline replay Selenium coverage; stored replay navigation remains part of earlier tasks.
- 2025-10-03 – MockMvc verification endpoint tests assert metadata.mode + telemetry mode fields; `./gradlew :rest-api:test --tests "io.openauth.sim.rest.OcraVerificationEndpointTest"` now passes after service telemetry wiring update.
- 2025-10-03 – Replay template JS now consumes REST metadata.mode/credentialSource to render telemetry summaries; Selenium replay suite verifies stored/inline flows show hashed fingerprints and sanitized flags.
- 2025-10-03 – UI replay telemetry now posts to `/ui/ocra/replay/telemetry`; new logger component emits through `TelemetryContracts.ocraVerificationAdapter` with mode/outcome/context fingerprints, covered by unit + WebMvc tests.

- 2025-10-03 – Replay Selenium suite passes post-implementation; verifies hashed fingerprints and sanitized flags for stored/inline flows.
- 2025-10-03 – Operator UI how-to and telemetry snapshot updated for replay workflow; `./gradlew spotlessApply check` rerun (configuration cache) and passed.
- 2025-10-03 – Replay inline view now reuses evaluation presets (Option A); JS applies preset selections to suite/context fields, covered by Selenium auto-fill assertions.
- 2025-10-03 – User confirmed sample presets must remain inline-only; need to align replay visibility with evaluation screen.
- 2025-10-03 – Replayed UI now toggles preset dropdown with stored/inline modes; Selenium stored-mode test asserts hidden state to prevent regressions.
- 2025-10-03 – Inline presets now carry expected OTP values; replay JS applies them and Selenium verifies match flow without manual entry.
- 2025-10-03 – Replay result card now mirrors evaluation console styling (status badge + telemetry grid) for readability.
## Analysis Gate (2025-10-03)
- Specification completeness – PASS: Feature 016 spec defines objectives, functional/non-functional requirements, and captured clarifications.
- Open questions review – PASS: `docs/4-architecture/open-questions.md` has no entries for this feature.
- Plan alignment – PASS: Plan references the spec/tasks; success criteria mirror OUR-001–OUR-005.
- Tasks coverage – PASS: Tasks T1601–T1607 map to replay flows, telemetry, documentation, and quality commands with tests preceding implementation.
- Constitution compliance – PASS: Work remains within existing modules, reuses REST replay endpoint, and adds telemetry per guardrails (no dependency changes planned).
- Tooling readiness – PASS: Plan lists Selenium/MockMvc suites and `./gradlew spotlessApply check`; SpotBugs/PMD guardrails remain active.
