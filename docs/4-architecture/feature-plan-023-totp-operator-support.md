# Feature Plan 023 – TOTP Operator Support

_Status: Draft_
_Last updated: 2025-10-09_

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
- ☑ R2312 – Implement operator console templates/JS to satisfy R2311; rerun `./gradlew :rest-api:test`. (_2025-10-08 – Wired TOTP evaluate/replay panels, ensured telemetry surfacing, and confirmed Selenium coverage._)
- ☑ R2321 – Refresh documentation, knowledge map references, and rerun `./gradlew spotlessApply check` plus `qualityGate`. (_2025-10-08 – Docs synced and gates remained green._)
- ☑ R2322 – Add failing Selenium assertions enforcing inline-before-stored ordering for TOTP mode selectors. (_2025-10-08 – Selenium run failed pre-change, locking UI expectations._)
- ☑ R2323 – Reorder TOTP console markup to satisfy R2322 and rerun Selenium alongside `./gradlew spotlessApply check`. (_2025-10-08 – Updated radio markup; suite now green._)
- ☑ R2324 – Add failing Selenium assertions verifying inline parameter controls share a single row. (_2025-10-08 – Added guard to Selenium suite; run failed before layout update._)
- ☑ R2325 – Adjust templates/CSS to satisfy R2324 and rerun Selenium plus `./gradlew spotlessApply check`. (_2025-10-08 – Introduced `totp-inline-parameter-grid`; all checks green._)
- ☑ R2326 – Add failing Selenium assertions ensuring drift backward/forward controls align on a single row. (_2025-10-08 – Guard failed pre-change, confirming expectation._)
- ☑ R2327 – Update templates/CSS to satisfy R2326 and rerun Selenium plus `./gradlew spotlessApply check`. (_2025-10-08 – Added `totp-drift-grid`; Selenium suite now green._)
- ☐ R2328 – Stage failing application/service, REST, and UI tests for a TOTP stored-mode `Seed sample credentials` control and seeding endpoint (empty-store prompts, telemetry, visibility gating).
- ☐ R2329 – Implement the TOTP seeding application service, REST endpoint, operator console button wiring, and documentation updates; rerun `./gradlew spotlessApply check`.
- ☐ R2330 – Add failing Selenium/UI regression asserting the TOTP inline evaluate and replay panels expose a "Load a sample vector" preset control that populates shared secret, algorithm, digits, step seconds, timestamp, OTP, and drift defaults. (_Scheduled 2025-10-09 – run `./gradlew :rest-api:test --tests "io.openauth.sim.rest.ui.TotpOperatorUiSeleniumTest.totpInlineSamplePresetPopulatesForm"` and `...TotpReplaySamplePresetPopulatesForm`; expect failures until presets exist._)
- ☐ R2331 – Implement the TOTP inline preset dropdowns and sample dataset in Thymeleaf templates and `ui/totp/console.js`, ensuring telemetry metadata reflects preset usage; rerun `./gradlew :rest-api:test` and `./gradlew spotlessApply check`. (_Pending – resolves R2330 failures._)
- ☐ R2332 – Update operator documentation and knowledge map to describe the new TOTP presets; rerun `./gradlew spotlessApply check`. (_Pending – execute after implementation passes UI tests._)

## Analysis Gate
- 2025-10-08 – Completed checklist 1–6; plan/spec/tasks align, open-question log cleared, and no remediation required before development.
