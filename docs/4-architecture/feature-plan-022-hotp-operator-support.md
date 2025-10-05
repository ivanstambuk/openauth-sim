# Feature Plan 022 – HOTP Operator Support

_Status: Draft_
_Last updated: 2025-10-05_

## Objective
Implement end-to-end HOTP flows (core domain, shared persistence, telemetry, CLI, REST) so operators can manage HOTP credentials alongside OCRA while reusing the existing schema-v1 storage baseline.

Reference specification: `docs/4-architecture/specs/feature-022-hotp-operator-support.md`.

## Success Criteria
- HOTP credential descriptors and generators comply with RFC 4226 (`HOS-001`).
- HOTP records coexist with OCRA entries in the shared MapDB store without migrations (`HOS-002`).
- CLI commands import/list/evaluate HOTP credentials with telemetry parity (`HOS-003`).
- REST endpoints expose HOTP evaluation (stored + inline) with OpenAPI updates and telemetry (`HOS-004`).
- Documentation (how-to, roadmap, knowledge map) surfaces HOTP usage and schema reuse (`HOS-005`).
- `./gradlew spotlessApply check` remains green after each increment.

## Proposed Increments
- ☑ R2201 – Draft HOTP generator/validator tests (expected failure) covering counter rollovers and digit variants.
- ☑ R2202 – Implement HOTP domain components and make tests pass; extend mutation/ArchUnit coverage.
- ☑ R2203 – Wire HOTP persistence via shared MapDB store; add integration tests mixing OCRA + HOTP records.
- ☑ R2204 – Add application-layer services + telemetry adapters for HOTP evaluation/issuance scenarios.
- ☑ R2205 – Implement CLI commands and tests for HOTP import/list/evaluate with telemetry assertions.
- ☑ R2206 – Introduce REST evaluation endpoint (stored + inline), update OpenAPI snapshots, and add MockMvc coverage.
- ☑ R2207 – Refresh documentation (how-to, roadmap highlights, knowledge map) and rerun full quality gate.
- ☑ R2208 – Add failing operator console tests covering HOTP stored credential evaluation (integration/system) and update UI plan artifacts.
- ☑ R2209 – Implement HOTP stored credential evaluation in the operator console (reuse REST API, ensure telemetry parity) and make R2208 pass.
- ☑ R2210 – Add failing operator console tests for HOTP inline evaluation (manual secret entry) and accessibility coverage.
- ☑ R2211 – Implement HOTP inline evaluation in the operator console, reuse REST telemetry, and satisfy R2210.
- ☑ R2212 – Update operator how-to docs, roadmap, knowledge map, and rerun `./gradlew spotlessApply check` after HOTP UI additions.

Each increment must complete within ≤10 minutes, lead with tests where practicable, and record tooling outcomes below.

## Checklist Before Implementation
- [x] Specification created with clarifications recorded.
- [x] Open questions resolved and captured in spec (pending session sign-off).
- [x] Tasks drafted with test-first ordering.
- [x] Analysis gate rerun once plan/tasks align.

## Tooling Readiness
- HOTP tests will extend existing JUnit/PIT suites under `core`/`application` modules.
- CLI/REST automation uses existing Picocli and MockMvc harnesses introduced for OCRA.
- Telemetry assertions rely on `TelemetryContracts` fixtures; ensure new event keys are covered.

## Notes
- Capture intent logs and command sequences for each increment in this plan.
- HOTP UI work is now in scope for evaluation flows (stored + inline) within the existing operator console; issuance remains out of scope per 2025-10-05 directive and is tracked on the roadmap.
- Operator UI remains evaluation-only; issuance flows stay deferred per 2025-10-05 directive and are tracked on the roadmap for future consideration.
- HOTP operator UI reuses REST telemetry (`hotp.evaluate`) without adding UI-specific events and requires operator documentation updates once implemented.
- 2025-10-04 – R2201 added HOTP generator/validator tests (failing) covering RFC 4226 vectors, 8-digit support, secret bounds, validation success/failure, and counter overflow guard; `./gradlew :core:test --tests io.openauth.sim.core.otp.hotp.*` fails as expected with `UnsupportedOperationException` placeholders.
- 2025-10-04 – R2202 implemented HOTP generator/validator, enforcing secret length, digit bounds, counter overflow guard, and RFC 4226 dynamic truncation; `./gradlew :core:test --tests io.openauth.sim.core.otp.hotp.*` passes and full `./gradlew --no-configuration-cache spotlessApply check` validates the suite (mutation tests included).
- 2025-10-04 – R2203 added integration coverage exercising `CredentialStoreFactory` with mixed OCRA/HOTP records; initial run (`./gradlew :infra-persistence:test --tests io.openauth.sim.infra.persistence.CredentialStoreFactoryHotpIntegrationTest`) failed while HOTP type/metadata were absent, then T2204 normalised persisted attributes (defaulting `hotp.counter`) so the test and full `./gradlew --no-configuration-cache spotlessApply check` now pass.
- 2025-10-04 – Application layer confirmed to own HOTP counter advancement and telemetry shaping (Option A); CLI/REST increments will call shared services rather than duplicating logic.
- 2025-10-05 – R2204 added HOTP evaluation + issuance application services, telemetry adapters, and contract coverage. `./gradlew :application:test --tests io.openauth.sim.application.hotp.*` failed before implementation, then passed after wiring services. Final validation via `./gradlew --no-configuration-cache spotlessApply check` succeeded.
- 2025-10-05 – R2205 delivered HOTP CLI import/list/evaluate flows backed by the new application services; coverage now exercises success, validation, and error telemetry (`./gradlew :cli:test --tests io.openauth.sim.cli.HotpCliTest`) and aggregate quality gates remain ≥0.90 after `./gradlew --no-configuration-cache spotlessApply check`.
- 2025-10-05 – R2206 introduced REST-facing MockMvc + endpoint tests (stored + inline) and OpenAPI expectations for HOTP evaluation. After wiring the controller/service, `./gradlew :rest-api:test --tests "io.openauth.sim.rest.hotp.HotpEvaluationControllerTest"` and `./gradlew :rest-api:test --tests "io.openauth.sim.rest.HotpEvaluationEndpointTest"` pass, and OpenAPI snapshots were refreshed via `OPENAPI_SNAPSHOT_WRITE=true ./gradlew :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`.
- 2025-10-05 – R2207 synced docs (spec, roadmap, knowledge map) with the HOTP REST slice and tightened HOTP REST coverage. `./gradlew --no-configuration-cache spotlessApply check` now succeeds with Jacoco branch coverage ≈0.9002 and line coverage ≈0.9706.
- 2025-10-05 – R2208 drafted failing Selenium coverage for HOTP stored evaluation in the operator console (`./gradlew :rest-api:test --tests "io.openauth.sim.rest.ui.HotpOperatorUiSeleniumTest.storedCredentialEvaluationSucceeds"` currently fails with a TimeoutException because the HOTP panel is a placeholder).
- 2025-10-05 – R2210 drafted failing Selenium coverage for HOTP inline evaluation with accessibility assertions; targeted run (`./gradlew :rest-api:test --tests "io.openauth.sim.rest.ui.HotpOperatorUiSeleniumTest.inlineHotpEvaluationSucceeds"`) is expected to fail until the UI wiring lands.
- 2025-10-05 – R2209 implemented HOTP stored evaluation UI, wired credential directory fetches, and refreshed Selenium coverage; targeted suite now passes (`./gradlew :rest-api:test --tests "io.openauth.sim.rest.ui.HotpOperatorUiSeleniumTest"`).
- 2025-10-05 – R2211 wired inline HOTP evaluation UI (identifier/secret/algorithm/digits/counter inputs) and shared result rendering, keeping telemetry metadata intact; Selenium suite above exercises both flows and confirms accessibility attributes.
- 2025-10-05 – Added HOTP credential directory endpoint + unit tests to supply the UI with stored credential metadata (`./gradlew :rest-api:test --tests "io.openauth.sim.rest.hotp.HotpCredentialDirectoryControllerTest"`).
- 2025-10-05 – R2212 refreshed operator documentation (new HOTP UI how-to, roadmap/knowledge-map updates) and re-ran the quality gate (`./gradlew spotlessApply check`), restoring aggregated branch coverage ≥0.90 after adding validation edge-case tests.

## Analysis Gate (2025-10-04)
- [x] Specification completeness – HOS requirements and clarifications recorded (telemetry parity, shared schema, CLI/REST scope).
- [x] Open questions review – No open entries for Feature 022 in `open-questions.md`.
- [x] Plan alignment – Feature plan references spec/tasks and mirrors success criteria.
- [x] Tasks coverage – T2201–T2211 cover test-first increments for each requirement.
- [x] Constitution compliance – Work honours spec-first, test-first, dependency guardrails.
- [x] Tooling readiness – Plan documents `./gradlew spotlessApply check`, reuses SpotBugs/quality gate context.
