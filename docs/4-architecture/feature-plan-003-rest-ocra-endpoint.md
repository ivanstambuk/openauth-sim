# Feature Plan 003 – REST OCRA Evaluation Endpoint

_Status: In progress_
_Last updated: 2025-09-28_

## Objective
Expose a synchronous REST endpoint under `/api/v1/ocra/evaluate` that reuses the core `OcraResponseCalculator` to compute session-aware OTPs for RFC 6287 suites. The feature delivers automated coverage for the S064/S128/S256/S512 fixtures already validated through the CLI, ensuring REST clients receive identical responses with secrets redacted.

Reference specification: `docs/4-architecture/specs/feature-003-rest-ocra-evaluation-endpoint.md`.

## Success Criteria
- Endpoint accepts all supported runtime inputs (suite, shared secret hex, optional counter/challenges/session/pin/timestamp) and produces deterministic OTPs.
- Integration tests exercise the endpoint against the known RFC vector fixtures, keeping responses in sync with the core module.
- Telemetry verifies redaction rules and captures execution metadata (`rest.ocra.evaluate`).
- OpenAPI documentation (via SpringDoc `/v3/api-docs`) and operator how-to references describe the endpoint contract and sample payloads.

## Task Tracker
- Detailed increments live in `docs/4-architecture/tasks/feature-003-rest-ocra-endpoint.md`. Keep each task ≤10 minutes and commit after every green build.
- Record `./gradlew spotlessApply check` outputs and analysis gate evaluations in this plan as work progresses.
- Initial scope covers synchronous evaluation only; follow-up tickets will address persistence-backed credential lookup and authentication.

### Timeline & Notes
- 2025-09-28 – Owner approved adopting SpringDoc OpenAPI starter for R005 to auto-generate REST docs; dependency addition will be recorded alongside Gradle lock refresh.
- 2025-09-28 – R005 implementation: integrated SpringDoc 2.4.0 with enforced Spring Boot BOM, added OpenAPI documentation tests, and generated the initial snapshot under docs/3-reference/rest-openapi.json.
- 2025-09-28 – Option A confirmed: REST facade receives dedicated spec/plan/tasks separate from Feature 001.
- 2025-09-28 – Current focus on Task R001 (documentation uplift) before implementing the endpoint (R002–R004).
- 2025-09-28 – R001 complete: spec/plan/tasks created; queued next steps R002/R003 (tests first).
- 2025-09-28 – R002 complete: Spring Boot dependencies added, integration test asserts current 404 response (TODO to flip on R004); dependency locks updated after aligning transitive versions; `./gradlew spotlessApply check` (PASS, 2025-09-28, ~99s).
- 2025-09-28 – R003 clarification: controller validation tests will keep 404 expectations with TODOs to flip when R004 lands.
- 2025-09-28 – `./gradlew spotlessApply check` (PASS, config cache reused) after documentation updates.
- 2025-09-28 – R004 delivered: wired `POST /api/v1/ocra/evaluate` controller/service to `OcraResponseCalculator`, flipped MockMvc tests to assert 200/400 with telemetry captures, and added error DTOs; `./gradlew :rest-api:test` (PASS, ≈14s) and `./gradlew spotlessApply check` (PASS, ≈26s) recorded.
- 2025-09-28 – R006 wrap-up: reran `./gradlew spotlessApply check` (PASS), captured telemetry output via `./gradlew :rest-api:test --tests io.openauth.sim.rest.OcraEvaluationEndpointTest --info`, archived sample logs in `docs/3-reference/rest-ocra-telemetry-snapshot.md`, and verified roadmap/knowledge map consistency.
- 2025-09-28 – Authentication decision finalized: no authentication/authorization layer will be added for this endpoint; treat it as an internal operator surface and avoid revisiting the topic.
- 2025-09-28 – Operational safeguards decision recorded: no rate limiting or throttling will be introduced for this endpoint; downstream environments will manage access externally.
- 2025-09-28 – Input hardening direction set: validate all hex fields, enforce non-negative counters, and enrich telemetry with `reasonCode`/`sanitized` attributes to support alerting while preserving redaction guarantees.
- 2025-09-28 – R007–R009 complete: expanded MockMvc coverage for malformed hex/counter flows, added pre-validation and telemetry reason codes, and captured green runs (`./gradlew :rest-api:test` PASS, `./gradlew spotlessApply check` PASS).
- 2025-09-28 – Timestamp validation will reuse `OcraCredentialFactory.validateTimestamp` drift rules (reason code `timestamp_drift_exceeded`); runtime PIN mismatches will emit `pin_hash_mismatch` and be pre-validated before calculator execution.
- 2025-09-28 – R010/R011 delivered: timestamp drift + PIN mismatch MockMvc coverage now green; REST service resolves timestampHex with suite metadata, injects `Clock` for deterministic tests, and maps reason codes (`timestamp_drift_exceeded`, `pin_hash_mismatch`, etc.).
- 2025-09-28 – R012 complete: operator how-to and telemetry snapshot updated with new reason codes; captured fresh logs via `./gradlew :rest-api:test --tests io.openauth.sim.rest.OcraEvaluationEndpointTest --info --rerun-tasks`.

## Dependencies
- Relies on the existing OCRA core package; ensure no modifications are required in `core/` for this feature.
- Spring Boot application scaffolding must be ready in the `rest-api` module (confirm configuration before implementation).
- Telemetry logging must align with constitution redaction rules already in place for CLI/core helpers.

## Self-Review & Analysis Gate
- 2025-09-28 (analysis gate) – Checklist completed prior to R004 implementation: specification and tasks remain in sync, no open questions, tests sequenced ahead of code, dependencies unchanged, and Gradle command readiness confirmed. No remediation items identified.
- 2025-09-28 – Self-review complete for R004: verified telemetry logging redacts secrets, confirmed new request/response DTO constructors normalize inputs, and captured Gradle command outputs noted above.
- 2025-09-28 – Self-review for R005: validated SpringDoc 2.4.0 alignment with Spring Boot 3.3.4 (enforced BOM), confirmed new OpenAPI tests guard `/v3/api-docs`, regenerated `docs/3-reference/rest-openapi.json`, and documented operator workflow in `docs/2-how-to/use-ocra-evaluation-endpoint.md`.
- Self-review notes, Gradle command outputs, and telemetry assertions should be appended as tasks close.

Update this plan as each task completes, mirroring task checklist status and documenting build/analysis results.
