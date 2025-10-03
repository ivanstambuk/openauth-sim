# Feature Plan 016 – OCRA UI Replay

_Status: Planning_
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
- R1601 – Capture clarifications, draft spec, update roadmap/knowledge map/open-questions. ✅ 2025-10-03
- R1602 – Author failing Selenium/system tests covering navigation to the replay screen, stored credential submission, and inline submission (success + failure). ☐
- R1603 – Add MockMvc/WebTestClient tests for replay controller/service wiring, including telemetry expectations and REST error handling. ☐
- R1604 – Implement replay screen templates, controllers, and REST wiring to satisfy stored/inline flows; ensure telemetry emitted per spec. ☐
- R1605 – Polish UI copy/accessibility, update telemetry and operator docs, and rerun Selenium tests. ☐
- R1606 – Run `./gradlew spotlessApply check` (plus targeted UI/REST test suites), review changes, and prepare conventional commit. ☐

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
- 2025-10-03 – Added Selenium replay suite (`OcraOperatorUiReplaySeleniumTest`) covering stored and inline flows; `./gradlew :rest-api:test --tests "io.openauth.sim.rest.ui.OcraOperatorUiReplaySeleniumTest"` currently fails (no replay screen yet) with timeouts/illegal argument at waitForReplayBootstrap (line 256).

## Analysis Gate (2025-10-03)
- Specification completeness – PASS: Feature 016 spec defines objectives, functional/non-functional requirements, and captured clarifications.
- Open questions review – PASS: `docs/4-architecture/open-questions.md` has no entries for this feature.
- Plan alignment – PASS: Plan references the spec/tasks; success criteria mirror OUR-001–OUR-005.
- Tasks coverage – PASS: Tasks T1601–T1607 map to replay flows, telemetry, documentation, and quality commands with tests preceding implementation.
- Constitution compliance – PASS: Work remains within existing modules, reuses REST replay endpoint, and adds telemetry per guardrails (no dependency changes planned).
- Tooling readiness – PASS: Plan lists Selenium/MockMvc suites and `./gradlew spotlessApply check`; SpotBugs/PMD guardrails remain active.
