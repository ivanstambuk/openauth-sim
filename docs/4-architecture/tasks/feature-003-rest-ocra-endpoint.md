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
| R003 | Add unit tests for request validation/telemetry redaction using controller/service-level slices. | FR-REST-002, FR-REST-003 | No |

## Phase 2 – Implementation
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| R004 | Implement synchronous controller/service wiring to `OcraResponseCalculator`; ensure tests from R002/R003 pass. | FR-REST-001–FR-REST-003 | No |
| R005 | Generate/update OpenAPI documentation and how-to references. | FR-REST-004 | No |

## Phase 3 – Wrap-up
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| R006 | Rerun `./gradlew spotlessApply check`, capture telemetry output, and update knowledge map/roadmap with REST facade coverage. | NFR-REST-001–NFR-REST-004 | No |

Update this checklist as tasks progress and link back to the feature plan with outcomes and build logs.
