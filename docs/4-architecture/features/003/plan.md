# Feature Plan 003 – REST OCRA Evaluation Endpoint

_Linked specification:_ `docs/4-architecture/features/003/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
- Deliver a synchronous REST endpoint (`POST /api/v1/ocra/evaluate`) that mirrors CLI/core OCRA behaviour with deterministic OTP outputs.
- Keep validation, telemetry, and documentation (OpenAPI snapshots, how-to guides, knowledge map) aligned so downstream clients rely on a stable contract.
- Maintain telemetry redaction guarantees and provide tests for all success/failure paths, including advanced validations (timestamps, PIN hashes).

## Scope Alignment
- **In scope:**
  - Request/response DTOs, controller/service wiring, MockMvc + unit tests.
  - Validation, telemetry, reason-code mapping, and log redaction checks.
  - OpenAPI JSON/YAML generation with snapshot tests.
  - Documentation updates (operator how-to, knowledge map, roadmap references).
- **Out of scope:**
  - Persistence-backed credential lookup, authentication/authorization, replay flows, UI wiring, async/batch endpoints.

## Dependencies & Interfaces
- Core `OcraResponseCalculator` and fixtures from Feature 001.
- `rest-api` Spring Boot stack (Java 17, Boot 3.3.4) plus SpringDoc OpenAPI starter.
- `TelemetryContracts` for redaction-compliant logging.
- Documentation assets under `docs/2-how-to/`, `docs/3-reference/`, and knowledge map entries.

## Assumptions & Risks
- **Assumptions:** Inline payloads remain the only supported mode; operators rely on HTTP auth handled outside this feature.
- **Risks & mitigations:**
  - _Telemetry leakage:_ mitigated via log capture tests enforcing `sanitized=true`.
  - _OpenAPI drift:_ snapshot tests plus `OPENAPI_SNAPSHOT_WRITE` workflow.
  - _Dependency skew:_ SpringDoc/Spring Boot versions recorded here and pinned via Gradle locks.

## Implementation Drift Gate
- Evidence captured via spec/plan/tasks plus telemetry + OpenAPI snapshots.
- Gate checklist: FR/NFR mapping confirmed, OpenAPI/telemetry assets updated, docs synced, and `./gradlew spotlessApply check` recorded.
- Status: PASS (2025-09-28) prior to final implementation; no outstanding drift items.

## Increment Map
1. **I1 – Clarifications + failing tests (T0301–T0302)**
   - _Goal:_ Lock scope, record tooling decisions, and stage failing MockMvc/telemetry tests.
   - _Preconditions:_ Spec skeleton + clarifications drafted.
   - _Steps:_ update roadmap/knowledge map entries; add failing tests for success + validation + telemetry; document `reasonCode` list.
   - _Commands:_
     - `./gradlew --no-daemon :rest-api:test --tests "*OcraEvaluationEndpointTest"`
   - _Exit:_ Tests fail as expected, clarifications logged, analysis gate satisfied.
2. **I2 – Controller + validation implementation (T0303–T0304)**
   - _Goal:_ Wire controller/service to `OcraResponseCalculator`, satisfy inline success cases, and enforce validation + telemetry semantics.
   - _Preconditions:_ I1 tests exist.
   - _Steps:_ implement controller, request/response DTOs, validation annotations, telemetry hooking; rerun targeted suites.
   - _Commands:_
     - `./gradlew --no-daemon :rest-api:test`
   - _Exit:_ MockMvc suites green; telemetry/log capture asserts redaction.
3. **I3 – OpenAPI + advanced validations (T0305–T0306)**
   - _Goal:_ Generate JSON/YAML snapshots, add snapshot tests, and extend coverage for timestamp/pin/session validations.
   - _Preconditions:_ I2 green.
   - _Steps:_ run snapshot writer, commit artifacts, add negative tests for timestamp/pin, update dependency locks if needed.
   - _Commands:_
     - `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "*OpenApiSnapshotTest"`
     - `./gradlew --no-daemon :rest-api:test --tests "*Timestamp*"`
   - _Exit:_ Snapshots updated, advanced validations covered, docs reference Swagger UI.
4. **I4 – Documentation sync + final verification (T0307)**
   - _Goal:_ Update how-to/knowledge map references, capture telemetry samples, and run spotless/check for closure.
   - _Preconditions:_ I3 complete.
   - _Steps:_ refresh docs referencing `rest.ocra.evaluate`, update roadmap + knowledge map, run full Gradle gate.
   - _Commands:_
     - `./gradlew --no-daemon spotlessApply check`
   - _Exit:_ Docs in sync, knowledge map updated, final build recorded.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S03-01 | I1–I2 / T0302–T0303 | Endpoint success path + OTP parity. |
| S03-02 | I2 / T0304 | Validation + structured errors. |
| S03-03 | I2 / T0304 | Telemetry redaction + reason codes. |
| S03-04 | I3 / T0305 | OpenAPI generation + snapshot tests. |
| S03-05 | I3 / T0306 | Advanced validation coverage (timestamp/pin). |
| S03-06 | I4 / T0307 | Documentation + telemetry references updated. |

## Analysis Gate
- Completed 2025-09-28 (PASS). Spec and plan aligned with clarifications; tasks ordered tests-first; dependencies documented; `./gradlew --no-daemon spotlessApply check` executed.

## Exit Criteria
- `./gradlew --no-daemon :rest-api:test` and `./gradlew --no-daemon spotlessApply check` green with telemetry/log capture assertions.
- OpenAPI snapshots updated + guarded by tests.
- Operator docs, knowledge map, and roadmap entries reflect the endpoint + telemetry reason codes.
- No open questions remain for REST OCRA evaluation scope.

## Follow-ups / Backlog
1. Add persistence-backed credential lookup + authentication for REST OCRA (future feature).
2. Extend maintenance endpoints/UI wiring once persistence templates finish migrating.
3. Evaluate throttling/rate-limiting needs for high-volume clients.
