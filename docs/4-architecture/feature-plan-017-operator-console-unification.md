# Feature Plan 017 – Operator Console Unification

_Status: Draft_
_Last updated: 2025-10-04_

## Objective
Unify the operator UI into a single dark-themed console with protocol tabs, retain OCRA evaluation and replay functionality, and stay within the existing Thymeleaf + vanilla JS stack.

Reference specification: `docs/4-architecture/specs/feature-017-operator-console-unification.md`.

## Success Criteria
- Unified console at `/ui/console` replaces separate evaluation/replay routes while keeping OCRA workflows functional and legacy paths removed (OCU-001/OCU-002/OCU-006).
- Dark theme and reduced margins deliver the requested terminal aesthetic without breaking accessibility (OCU-003).
- Telemetry and docs remain aligned with the unified console layout (OCU-004/OCU-005).
- Console exposes a manual `Seed sample credentials` flow backed by a REST endpoint that appends canonical suites when missing and logs telemetry, while leaving existing credentials intact (OCU-011).
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
- ☑ R1712 – Add failing Selenium/Web layer coverage for the `Seed sample credentials` control, REST endpoint contract, and telemetry expectations when the store is empty.
- ☑ R1713 – Implement the seeding REST endpoint, domain wiring, and UI button (append-only) with telemetry + persistence updates, then rerun `./gradlew :rest-api:test`.
- ☑ R1714 – Refresh documentation/knowledge map for the seeding workflow, ensure repeated seeding behaviour is described, and rerun `./gradlew spotlessApply check`.
- ☑ R1715 – Add failing Selenium/Web tests asserting query-parameter deep links and history behaviour for `/ui/console` tabs; run `./gradlew :rest-api:test` to confirm red.
- ☑ R1716 – Implement query-parameter state management and history handling in the console controller/JS, ensure disabled protocols render placeholders, and rerun `./gradlew :rest-api:test`.
- ☑ R1717 – Update documentation/knowledge map for stateful URLs, capture telemetry implications, and rerun `./gradlew spotlessApply check`.
- ☑ R1718 – Add failing Selenium/UI assertion that the evaluation result card no longer renders a Sanitized row. (2025-10-04 – Selenium assertion added before UI update.)
- ☑ R1719 – Remove the Sanitized row from evaluation results, update UI scripts/tests, and rerun `./gradlew :rest-api:test spotlessApply check`. (2025-10-04 – Evaluation template/JS updated; tests and quality gate green.)
- ☑ R1720 – Extend Selenium coverage so the seeding status surfaces beneath the seed control while the original hint remains unchanged. (2025-10-04 – Selenium test now asserts hint text + hidden status element before implementation.)
- ☑ R1721 – Adjust the console template/scripts to render seeding feedback under the seed control with danger styling for failures, then rerun `./gradlew :rest-api:test`. (2025-10-04 – Template adds dedicated seed status element with red-error handling; REST UI tests + `./gradlew spotlessApply check` verified.)
- ☑ R1722 – Extend Selenium coverage so zero-added seeding outcomes assert the warning styling distinct from neutral hints. (2025-10-04 – Updated seeding Selenium test to require `credential-status--warning` after duplicate seeding.)
- ☑ R1723 – Update UI scripts/styles to flag zero-added results with warning styling while keeping failures red, then rerun `./gradlew :rest-api:test`. (2025-10-04 – Added warning color token + severity handling; `./gradlew :rest-api:test spotlessApply check` passed.)
- ☑ R1724 – Relocate the shared operator console stylesheet to `/ui/console/console.css`, adjust Thymeleaf templates, JS fetchers, Selenium helpers, and rerun `./gradlew spotlessApply check`. (2025-10-15 – Stylesheet moved, references updated, quality gate rerun.)

Each increment must stay within ≤30 minutes, lead with tests, and capture notes/telemetry adjustments in this plan as work proceeds.

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
- 2025-10-04 – New request (R1718/R1719) to drop the Sanitized indicator from evaluation results; pending test-first removal.
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
- 2025-10-03 – Completed R1711: removed Suite row from evaluation result, tightened Selenium expectations, and reran `./gradlew :rest-api:test spotlessApply check` successfully.
- 2025-10-04 – Initiated R1712 with Selenium + endpoint tests covering the seeding control, REST contract, and telemetry expectations.
- 2025-10-04 – Completed R1712: seeding UI/REST telemetry tests now cover empty-store behaviour; red tests drove the implementation.
- 2025-10-04 – Completed R1713: introduced an application-layer seeding service, delegated REST logic through it, wired the UI button, and reran `./gradlew :rest-api:test`.
- 2025-10-04 – Completed R1714: refreshed documentation, knowledge map, and snapshots for the seeding workflow and reran `./gradlew spotlessApply check`.
- 2025-10-04 – Updated operator docs and UI logic so the seeding control only renders when stored credential mode is active, keeping inline mode uncluttered.
- 2025-10-04 – Adjusted replay layout so the stored credential selector sits directly beneath the mode chooser per operator feedback.
- 2025-10-04 – Captured directive to encode console state via query parameters and preserve tab navigation/history (OCU-012); pending R1715–R1717.
- 2025-10-04 – Initiated R1715 by adding failing Selenium coverage for query-parameter deep links and history navigation; `OperatorConsoleUnificationSeleniumTest` now red pending implementation.
- 2025-10-04 – Completed R1716: console script now normalises query parameters, pushes/pops history state, and updates URLs during tab/mode changes; `./gradlew :rest-api:test` rerun shows new Selenium tests green alongside pre-existing OpenAPI/replay failures.
- 2025-10-04 – Completed R1717: refreshed the operator UI how-to and knowledge map to document stateful URLs and confirm telemetry remains client-only for tab transitions; reran `./gradlew spotlessApply check` (fails on existing OpenAPI + replay suites).
- 2025-10-04 – Regenerated OpenAPI JSON/YAML snapshots via `OPENAPI_SNAPSHOT_WRITE=true ./gradlew :rest-api:test --tests io.openauth.sim.rest.OpenApiSnapshotTest` after console state wiring added new metadata, then reran `./gradlew spotlessApply check` to confirm green.
Use this section to log telemetry schema additions, performance observations, and accessibility adjustments.

## Analysis Gate (2025-10-03)
- [x] Specification completeness – Feature 017 spec documents objectives, functional/non-functional requirements, and captures the latest clarifications (2025-10-03 approvals).
- [x] Open questions review – `docs/4-architecture/open-questions.md` has no entries for Feature 017.
- [x] Plan alignment – Plan references the Feature 017 spec and tasks checklist; success criteria mirror OCU-001…OCU-005.
- [x] Tasks coverage – Tasks T1701–T1706 map to requirements, with tests preceding implementation and ≤30 min scope.
- [x] Constitution compliance – Work keeps to existing modules, honors spec-first/test-first, and does not introduce new dependencies.
- [x] Tooling readiness – Plan cites `./gradlew :rest-api:test spotlessApply check`; SpotBugs dead-state enforcement stays active via root `check` invocation.
