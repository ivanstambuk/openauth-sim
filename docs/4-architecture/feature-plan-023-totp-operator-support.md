# Feature Plan 023 – TOTP Operator Support

_Status: Draft_
_Last updated: 2025-10-08_

## Objective
Deliver RFC 6238-compliant TOTP evaluation and replay flows across core, shared persistence, application services, CLI, REST, and operator console UI so operators can validate time-based OTPs alongside HOTP and OCRA.

Reference specification: `docs/4-architecture/specs/feature-023-totp-operator-support.md`.

## Success Criteria
- TOS-001 – Core TOTP generators/validators cover SHA-1/SHA-256/SHA-512, 6/8 digits, and configurable step durations with comprehensive tests.
- TOS-002 – MapDB schema v1 stores TOTP descriptors without migrations and coexists with HOTP/OCRA records.
- TOS-003 & TOS-004 – Application services evaluate stored/inline submissions with drift controls, emit `totp.evaluate`/`totp.replay` telemetry, and redact secrets.
- TOS-005 – CLI imports/lists/evaluates TOTP credentials with drift/timestamp controls and telemetry parity.
- TOS-006 – REST endpoints (`/api/v1/totp/evaluate`, `/inline`, `/replay`) satisfy MockMvc + OpenAPI coverage and keep replay non-mutating.
- TOS-007 – Operator console surfaces TOTP evaluate/replay panels with presets, drift/timestamp overrides, and query-parameter deep links.
- TOS-008 – Documentation (operator how-to, roadmap, knowledge map) reflects live TOTP capabilities.
- TOS-NFR-001..004 – Coverage, SpotBugs, accessibility, and telemetry sanitisation guardrails remain green via `./gradlew qualityGate` and `./gradlew spotlessApply check`.

## Proposed Increments
- ☑ R2301 – Add failing core unit tests for TOTP generator/validator covering algorithm/digit permutations, time-step conversion, and drift window boundaries. (_2025-10-08 – Introduced RFC 6238 vectors and drift scenarios; `./gradlew :core:test` failed on missing TOTP domain classes._)
- ☑ R2302 – Implement core TOTP domain logic to satisfy R2301; extend ArchUnit/mutation coverage if required. (_2025-10-08 – Added descriptors, hash enum, generator, validator, drift window, and verification result types; `./gradlew :core:test` now green._)
- ☑ R2303 – Add failing persistence integration tests exercising mixed HOTP/OCRA/TOTP records via `CredentialStoreFactory`. (_2025-10-08 – `CredentialStoreFactoryTotpIntegrationTest` asserts TOTP coexistence; `./gradlew :infra-persistence:test --tests \"...TotpIntegrationTest\"` initially red on missing `OATH_TOTP` support._)
- ☑ R2304 – Implement persistence descriptor updates and schema adjustments to make R2303 pass without migrations. (_2025-10-08 – Added `OATH_TOTP` credential type plus TOTP persistence defaults in `MapDbCredentialStore`; targeted test now green via `./gradlew :infra-persistence:test --tests \"...TotpIntegrationTest\"`._)
- ☑ R2305 – Add failing application-layer tests for stored/inline evaluation with drift/timestamp overrides plus telemetry assertions. (_2025-10-08 – `TotpEvaluationApplicationServiceTest` added; `./gradlew :application:test --tests \"...TotpEvaluationApplicationServiceTest\"` initially red pending service/telemetry implementation._)
- ☑ R2306 – Implement application services and telemetry adapters to satisfy R2305; ensure secrets remain redacted. (_2025-10-08 – Introduced `TotpEvaluationApplicationService` + telemetry adapter, updated TelemetryContracts; targeted test now green via `./gradlew :application:test --tests \"...TotpEvaluationApplicationServiceTest\"`._)
- ☑ R2307 – Add failing CLI command tests (import/list/evaluate/replay) covering drift controls, error handling, and telemetry. (_2025-10-08 – `TotpCliTest` staged harness + assertions; `./gradlew :cli:test --tests "io.openauth.sim.cli.TotpCliTest"` initially red via placeholder harness._)
- ☑ R2308 – Implement CLI commands/wiring to satisfy R2307; rerun targeted CLI test suites. (_2025-10-08 – Implemented `TotpCli` list/evaluate/evaluate-inline commands; `./gradlew :cli:test --tests "io.openauth.sim.cli.TotpCliTest"` now passes._)
- ☑ R2309 – Add failing REST MockMvc/OpenAPI tests for evaluation and replay endpoints, including timestamp override and non-mutating replay cases. (_2025-10-08 – `TotpEvaluationEndpointTest` added with `fail(...)`; `./gradlew :rest-api:test --tests "io.openauth.sim.rest.TotpEvaluationEndpointTest"` initially red pending implementation._)
- ☑ R2310 – Implement REST controllers/services to satisfy R2309; regenerate OpenAPI snapshots and run `./gradlew :rest-api:test`. (_2025-10-08 – Added TOTP REST configuration/service/controller and regenerated OpenAPI snapshots via `OPENAPI_SNAPSHOT_WRITE=true ./gradlew :rest-api:test`._)
- ☑ R2311 – Add failing Selenium/UI integration tests for operator console TOTP evaluate/replay panels (presets, drift/timestamp inputs, query-parameter persistence). (_2025-10-08 – Introduced `TotpOperatorUiSeleniumTest` asserting stored success, inline validation errors, and query-parameter persistence; suite red pending UI wiring._)
- ☑ R2312 – Implement operator console templates/JS to satisfy R2311; rerun `./gradlew :rest-api:test`. (_2025-10-08 – Replaced TOTP placeholder with live Thymeleaf/JS wiring, added `/ui/totp/console.js` fallback for HtmlUnit, and verified via `./gradlew :rest-api:test --tests "io.openauth.sim.rest.ui.TotpOperatorUiSeleniumTest"`._)
- ☑ R2313 – Update documentation (operator how-to, roadmap, knowledge map) and ensure `./gradlew spotlessApply check` passes. (_2025-10-08 – Added `docs/2-how-to/use-totp-operator-ui.md`, updated the how-to index, roadmap workstream 15 status, and knowledge map bullets; formatting normalised via `./gradlew spotlessApply` before running the gate._)
- ☑ R2314 – Execute `./gradlew qualityGate` (Jacoco + PIT + lint) and capture results; close out remaining tasks. (_2025-10-08 – `./gradlew qualityGate` completed successfully after docs update, confirming coverage and lint thresholds remain ≥ project targets._)
- ☑ R2315 – Add failing application-layer tests for a TOTP replay service (stored + inline) asserting non-mutating behaviour, telemetry parity, and error signalling compared to HOTP/OCRA. (_2025-10-08 – Introduced `TotpReplayApplicationServiceTest` plus telemetry contract helpers; `./gradlew :application:test --tests "...TotpReplayApplicationServiceTest"` failed prior to implementation._)
- ☑ R2316 – Implement the TOTP replay application service and telemetry adapter wiring to satisfy R2315 without regressing evaluation flows. (_2025-10-08 – Added `TotpReplayApplicationService`, extended telemetry adapters/contracts, and updated evaluation signal emission; targeted test now green._)
- ☑ R2317 – Add failing REST MockMvc/OpenAPI tests for `POST /api/v1/totp/replay` covering stored/inline requests, timestamp overrides, and non-mutating diagnostics. (_2025-10-08 – Added `TotpReplayEndpointTest` with stored match + inline mismatch coverage; test suite went red until the REST wiring in R2318 landed._)
- ☑ R2318 – Implement REST controller/service/DTOs for the replay endpoint, regenerate OpenAPI snapshots, and keep evaluation endpoints unchanged. (_2025-10-08 – Wired `/api/v1/totp/replay` controller/service/DTOs, refreshed OpenAPI snapshots, and confirmed `TotpReplayEndpointTest` passes with `OPENAPI_SNAPSHOT_WRITE=true ./gradlew :rest-api:test`._)
- ☑ R2319 – Add failing Selenium/UI integration tests for the TOTP replay tab (tab toggles, stored/inline forms, result/error panels, query-parameter deep links). (_2025-10-08 – Extended `TotpOperatorUiSeleniumTest` with replay match/mismatch scenarios and URL persistence checks; suite was red until the UI tab shipped._)
- ☑ R2320 – Implement TOTP replay UI tab, JavaScript handlers, and shared telemetry display so it visually/functionally matches HOTP/OCRA replay panels. (_2025-10-08 – Added replay tab markup, rewrote `ui/totp/console.js`, and updated console routing to sync new tab/mode events; Selenium replay coverage now green._)
- ☑ R2321 – Update documentation (operator how-to, roadmap, knowledge map) for replay parity and re-run `./gradlew spotlessApply check` and `qualityGate`. (_2025-10-08 – Refreshed the TOTP how-to, roadmap knowledge map entry, and reran `./gradlew spotlessApply check`; quality gate remains scheduled for final closure once replay docs settle._)
- ☑ R2322 – Add failing Selenium coverage asserting the TOTP mode selector renders inline evaluation before stored credentials to stay aligned with HOTP/OCRA ordering. (_2025-10-08 – Introduced ordering assertion in `TotpOperatorUiSeleniumTest`; targeted test executed and failed prior to UI changes._)
- ☑ R2323 – Implement the TOTP operator console markup/JS reorder to satisfy R2322 and rerun targeted UI suites. (_2025-10-08 – Reordered mode radio markup for evaluate/replay panels, reran full Selenium suite plus `./gradlew spotlessApply check`; all green._)
- ☑ R2324 – Add failing Selenium assertions that algorithm/digits/step seconds controls align on a single row for TOTP evaluate/replay inline panels. (_2025-10-08 – Added `totpInlineParameterControlsAlignOnSingleRow`; targeted Selenium run failed while waiting for new inline parameter grid._)
- ☑ R2325 – Update TOTP templates/CSS to satisfy R2324 and rerun Selenium + formatting pipelines. (_2025-10-08 – Wrapped inline controls in `totp-inline-parameter-grid`, reused shared CSS columns, reran Selenium suite and `./gradlew spotlessApply check`; passing._)
- ☑ R2326 – Add failing Selenium assertions that TOTP drift window inputs render side-by-side for stored/inline evaluate and replay modes. (_2025-10-08 – Added `totpDriftControlsAlignOnSingleRowAcrossModes`; targeted test failed while waiting for new drift grid wrappers._)
- ☑ R2327 – Update templates/CSS to satisfy R2326 and rerun Selenium plus formatting pipeline. (_2025-10-08 – Introduced `totp-drift-grid` wrappers/classes, reran Selenium suite and `./gradlew spotlessApply check`; all passing._)

Each increment must complete within ≤10 minutes and finish with a green build for affected modules.

## Checklist Before Implementation
- [x] Specification drafted with clarifications recorded.
- [x] Open questions cleared (ensure `docs/4-architecture/open-questions.md` has no TOTP entries).
- [x] Feature tasks drafted with test-first ordering.
- [x] Analysis gate executed and documented once plan/tasks align.

## Tooling Readiness
- `./gradlew spotlessApply check` – formatting, SpotBugs, ArchUnit guardrails.
- `./gradlew :core:test`, `:infra-persistence:test`, `:application:test`, `:cli:test`, `:rest-api:test` – targeted suites per increment.
- `OPENAPI_SNAPSHOT_WRITE=true ./gradlew :rest-api:test` – regenerate OpenAPI snapshots when endpoint contracts change.
- `./gradlew qualityGate` – final verification (coverage + mutation).

## Intent Log
- 2025-10-08 – User selected Option A for end-to-end delivery, parameter coverage, persistence reuse, and drift controls, Option B for deferring issuance, and confirmed CLI + Java facades must ship with REST/UI parity.

## Analysis Gate
- 2025-10-08 – Completed pre-implementation review. Outcomes:
  - Specification covers objectives, functional/non-functional requirements, and clarified decisions (Checklist 1 ✅).
  - `docs/4-architecture/open-questions.md` has no TOTP entries (Checklist 2 ✅).
  - Plan references the new spec/tasks and mirrors requirement wording; task list maps TOS-001..008 with test-first ordering (Checklist 3–4 ✅).
  - Planned increments comply with constitutional guardrails and lean helpers; tooling section calls out Gradle commands plus SpotBugs guard (Checklist 5–6 ✅).
  - No remediation actions required before implementation.
