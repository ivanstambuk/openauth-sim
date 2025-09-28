# Feature 003 – REST OCRA Evaluation Endpoint Tasks

_Status: In progress_
_Last updated: 2025-09-28_

## Execution Notes
- Follow the specification in `docs/4-architecture/specs/feature-003-rest-ocra-evaluation-endpoint.md`.
- Keep each task ≤10 minutes and commit after every passing `./gradlew spotlessApply check`.
- Drive development test-first: add failing integration/unit coverage before wiring the endpoint.
- Maintain log/telemetry redaction; assert via tests before shipping.

## Phase 0 – Documentation & Planning
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| R001 | [x] Update/create specification, feature plan, and tasks for the REST endpoint (Option A isolation). | FR-REST-001–FR-REST-004 | No |

## Phase 1 – Test Harness
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| R002 | [x] Add REST integration test stubs covering S064/S128/S256/S512 vectors; mark TODO expecting HTTP 404/501 until endpoint exists. | FR-REST-001, NFR-REST-004 | No |
| R003 | [x] Add unit tests for request validation/telemetry redaction using controller/service-level slices. | FR-REST-002, FR-REST-003 | No |

## Phase 2 – Implementation
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| R004 | [x] Implement synchronous controller/service wiring to `OcraResponseCalculator`; ensure tests from R002/R003 pass. | FR-REST-001–FR-REST-003 | No |
| R005 | [x] Integrate SpringDoc OpenAPI, generate the spec, and refresh how-to references. | FR-REST-004 | No |

## Phase 3 – Wrap-up
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| R006 | [x] Rerun `./gradlew spotlessApply check`, capture telemetry output, and update knowledge map/roadmap with REST facade coverage. | NFR-REST-001–NFR-REST-004 | No |

## Phase 4 – Input Validation & Telemetry Hardening
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| R007 | [x] Extend unit/integration tests to cover malformed hex inputs, missing/negative counters, and verify structured `reasonCode`/`sanitized` telemetry fields. | FR-REST-005, FR-REST-003, NFR-REST-004 | No |
| R008 | [x] Implement pre-validation in `OcraEvaluationService` (or helper) to reject bad hex/counter values with field-specific errors and reason codes. | FR-REST-005 | No |
| R009 | [x] Enrich telemetry builder with `reasonCode`/`sanitized` attributes and update snapshots/docs if log output changes; rerun `./gradlew spotlessApply check`. | FR-REST-003, NFR-REST-004 | No |

2025-09-28 – R004 closed: `./gradlew :rest-api:test` (PASS, ~14s) and `./gradlew spotlessApply check` (PASS, ~26s); telemetry assertions confirmed secrets remain redacted.
2025-09-28 – R005 closed: SpringDoc 2.4.0 integrated with enforced Spring Boot BOM; generated rest-openapi.json snapshot via `OPENAPI_SNAPSHOT_WRITE=true ./gradlew :rest-api:test --tests io.openauth.sim.rest.OpenApiSnapshotTest`; verification `./gradlew :rest-api:test --tests io.openauth.sim.rest.OpenApiDocumentationTest`.

Update this checklist as tasks progress and link back to the feature plan with outcomes and build logs.
2025-09-28 – R006 closed: `./gradlew spotlessApply check` (PASS) and `./gradlew :rest-api:test --tests io.openauth.sim.rest.OcraEvaluationEndpointTest --info` captured telemetry snapshot stored in docs/3-reference/rest-ocra-telemetry-snapshot.md; confirmed no TODO markers remain.
2025-09-28 – R007 executed test-first: `./gradlew :rest-api:test` (RED – added cases) captured expected failures, then green after R008/R009.
2025-09-28 – R008 implemented input validation and telemetry reason codes; `./gradlew :rest-api:test` (PASS, ~13s).
2025-09-28 – R009 enriched telemetry/logging and reran `./gradlew spotlessApply check` (PASS, ~37s).
