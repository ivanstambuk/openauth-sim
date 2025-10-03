# Feature Plan 017 – Operator Console Unification

_Status: Draft_
_Last updated: 2025-10-03_

## Objective
Unify the operator UI into a single dark-themed console with protocol tabs, retain OCRA evaluation and replay functionality, and stay within the existing Thymeleaf + vanilla JS stack.

Reference specification: `docs/4-architecture/specs/feature-017-operator-console-unification.md`.

## Success Criteria
- Unified console at `/ui/console` replaces separate evaluation/replay routes while keeping OCRA workflows functional and legacy paths removed (OCU-001/OCU-002/OCU-006).
- Dark theme and reduced margins deliver the requested terminal aesthetic without breaking accessibility (OCU-003).
- Telemetry and docs remain aligned with the unified console layout (OCU-004/OCU-005).
- `./gradlew :rest-api:test spotlessApply check` passes after each increment and final changes include documentation updates.

## Proposed Increments
- ☑ R1701 – Add failing Selenium coverage for the consolidated `/ui/console` route with OCRA active and placeholder protocol tabs disabled.
- ☑ R1702 – Update existing Selenium suites to target the unified console while maintaining regression coverage for OCRA workflows.
- ☑ R1703 – Implemented consolidated Thymeleaf template/fragment structure, routing updates, and basic tab skeleton (OCRA active, others disabled); reran affected tests.
- ☑ R1704 – Apply dark theme token updates, responsive layout, and remove perimeter gutters; extend accessibility checks.
- ☑ R1705 – Rewire evaluation/replay functionality within the unified console (form wiring, REST calls, telemetry), rerun Selenium and unit suites.
- ☑ R1706 – Update operator documentation and screenshots, refresh knowledge map if new components are introduced, rerun `./gradlew :rest-api:test spotlessApply check`.
- ☑ R1707 – Decommission legacy `/ui/ocra/evaluate` and `/ui/ocra/replay` routes (redirect or remove), update coverage, and rerun `./gradlew :rest-api:test spotlessApply check`.
- ☑ R1708 – Align replay tab controls so "Load a sample vector" renders directly under the replay mode selector; update Selenium coverage and rerun `./gradlew :rest-api:test spotlessApply check`.
- ☑ R1709 – Remove replay result fields (Telemetry ID, Credential Source, Context Fingerprint, Sanitized), adjust scripts/tests, and rerun `./gradlew :rest-api:test spotlessApply check`.
- ☑ R1710 – Stack evaluation and replay metadata so each label/value appears on its own row; update styles/tests and rerun `./gradlew :rest-api:test spotlessApply check`.
- ☑ R1711 – Remove Suite from evaluation results, adjust scripts/tests, and rerun `./gradlew :rest-api:test spotlessApply check`.

Each increment must stay within ≤10 minutes, lead with tests, and capture notes/telemetry adjustments in this plan as work proceeds.

## Checklist Before Implementation
- [x] Specification created with clarifications logged.
- [x] Open questions resolved and captured in spec/plan.
- [x] Task checklist drafted with tests-first ordering.
- [x] Analysis gate rerun once plan/tasks are in sync.

## Tooling Readiness
- Selenium/system tests: `./gradlew :rest-api:test` currently houses UI Selenium suites (no dedicated `systemTest` task); expand as needed.
- MockMvc/WebTestClient: reuse existing OCRA controller tests for unified endpoints.
- Lint/format: `./gradlew spotlessApply check` mandatory after each increment.

## Notes

- 2025-10-03 – R1702 updated evaluation/replay Selenium suites to launch via `/ui/console` links before exercising existing flows, keeping regression coverage intact while touching the new layout.
- 2025-10-03 – R1704 swapped the operator palette to the new dark theme, widened layouts, and re-styled console tabs/placeholders while keeping forms accessible.
- 2025-10-03 – R1703 merged evaluation/replay entry points under `/ui/console` with placeholder content and links back to legacy pages; Selenium suite now passes.
- 2025-10-03 – Added Selenium coverage for unified console/tab expectations (R1701); tests passed once the `/ui/console` scaffold landed in R1703.
- 2025-10-03 – Initiated R1705 by drafting Selenium expectations for inline evaluation/replay forms directly on `/ui/console`, forcing red coverage until the forms and telemetry wiring landed.
- 2025-10-03 – Completed R1705: embedded evaluation/replay forms within `/ui/console`, added mode toggle script, and reran `./gradlew :rest-api:test` to confirm Selenium coverage.
- 2025-10-03 – Completed R1706: refreshed operator documentation + knowledge map for the inline console and reran `./gradlew spotlessApply check` as the verification gate.
- 2025-10-03 – Follow-up styling refinements: darkened inline field backgrounds, moved protocol tabs into the console header, and removed the legacy summary copy per UI feedback.
- 2025-10-03 – Completed R1707: removed `/ui/ocra/evaluate` + `/ui/ocra/replay` routes, redirected `/ui/ocra` to `/ui/console`, updated tests to assert 404s for legacy paths, and reused temp databases in OpenAPI suites to avoid MapDB lock contention.
- 2025-10-03 – Completed R1708: moved preset loader immediately below replay mode selector, updated Selenium assertions for control order, and reran `./gradlew :rest-api:test` plus `spotlessApply check`.
- 2025-10-03 – New directive captured as R1709: remove Telemetry ID, Credential Source, Context Fingerprint, and Sanitized values from replay results while keeping remaining metadata visible.
- 2025-10-03 – Completed R1709: replay fragment now renders only mode/reason/outcome, Selenium asserts removed fields stay absent, and `./gradlew :rest-api:test spotlessApply check` passed.
- 2025-10-03 – New directive captured as R1710: update evaluation and replay result cards to render one metadata item per row instead of auto-fit grid columns.
- 2025-10-03 – Completed R1710: templates now wrap each label/value in `.result-row`, CSS enforces stacked layout, Selenium verifies row counts, and build passed via `./gradlew :rest-api:test spotlessApply check`.
- 2025-10-03 – New directive captured as R1711: evaluation results should only display Status and Sanitized metadata alongside the OTP value.
- 2025-10-03 – Completed R1711: removed Suite row from evaluation result, cleaned templating/scripts, tightened Selenium expectations, and reran `./gradlew :rest-api:test spotlessApply check` successfully.
Use this section to log telemetry schema additions, performance observations, and accessibility adjustments.

## Analysis Gate (2025-10-03)
- [x] Specification completeness – Feature 017 spec documents objectives, functional/non-functional requirements, and captures the latest clarifications (2025-10-03 approvals).
- [x] Open questions review – `docs/4-architecture/open-questions.md` has no entries for Feature 017.
- [x] Plan alignment – Plan references the Feature 017 spec and tasks checklist; success criteria mirror OCU-001…OCU-005.
- [x] Tasks coverage – Tasks T1701–T1706 map to requirements, with tests preceding implementation and ≤10 min scope.
- [x] Constitution compliance – Work keeps to existing modules, honors spec-first/test-first, and does not introduce new dependencies.
- [x] Tooling readiness – Plan cites `./gradlew :rest-api:test spotlessApply check`; SpotBugs dead-state enforcement stays active via root `check` invocation.
