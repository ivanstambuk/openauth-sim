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
- OpenAPI documentation and operator how-to references describe the endpoint contract and sample payloads.

## Task Tracker
- Detailed increments live in `docs/4-architecture/tasks/feature-003-rest-ocra-endpoint.md`. Keep each task ≤10 minutes and commit after every green build.
- Record `./gradlew spotlessApply check` outputs and analysis gate evaluations in this plan as work progresses.
- Initial scope covers synchronous evaluation only; follow-up tickets will address persistence-backed credential lookup and authentication.

### Timeline & Notes
- 2025-09-28 – Option A confirmed: REST facade receives dedicated spec/plan/tasks separate from Feature 001.
- 2025-09-28 – Current focus on Task R001 (documentation uplift) before implementing the endpoint (R002–R004).
- 2025-09-28 – R001 complete: spec/plan/tasks created; queued next steps R002/R003 (tests first).
- 2025-09-28 – R002 complete: Spring Boot dependencies added, integration test asserts current 404 response (TODO to flip on R004); dependency locks updated after aligning transitive versions; `./gradlew spotlessApply check` (PASS, 2025-09-28, ~99s).
- 2025-09-28 – `./gradlew spotlessApply check` (PASS, config cache reused) after documentation updates.

## Dependencies
- Relies on the existing OCRA core package; ensure no modifications are required in `core/` for this feature.
- Spring Boot application scaffolding must be ready in the `rest-api` module (confirm configuration before implementation).
- Telemetry logging must align with constitution redaction rules already in place for CLI/core helpers.

## Self-Review & Analysis Gate
- Run `docs/5-operations/analysis-gate-checklist.md` once tasks R001–R004 are defined; record findings here.
- Self-review notes, Gradle command outputs, and telemetry assertions should be appended as tasks close.

Update this plan as each task completes, mirroring task checklist status and documenting build/analysis results.
