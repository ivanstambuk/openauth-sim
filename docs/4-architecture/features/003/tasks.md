# Feature 003 Tasks – REST OCRA Evaluation Endpoint

_Status:_ Complete  
_Last updated:_ 2025-11-09_

## Checklist
- [x] T0301 – Align spec/plan/tasks, record Spring Boot + SpringDoc decisions, and clear open questions (F-REST-001–F-REST-005, S03-01–S03-05).
  _Intent:_ Lock facade scope and tooling choices before implementation.
  _Verification commands:_
  - `less docs/4-architecture/features/003/spec.md`
  - `rg -n "Feature 003" docs/4-architecture/roadmap.md`

- [x] T0302 – Add failing MockMvc + unit tests for inline success, validation errors, and telemetry redaction (F-REST-001, F-REST-002, S03-01, S03-02, S03-03).
  _Intent:_ Define controller/service expectations and error shapes up front.
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test --tests "*OcraEvaluationEndpointTest"`

- [x] T0303 – Implement controller/service wiring to `OcraResponseCalculator`, satisfying inline success tests (F-REST-001, S03-01).
  _Intent:_ Provide the synchronous JSON endpoint described in the spec.
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test`

- [x] T0304 – Enforce validation + telemetry reason codes (hex checks, counters, sanitization) and rerun targeted suites (F-REST-002, F-REST-003, S03-02, S03-03).
  _Intent:_ Guarantee detailed error reporting without leaking secrets.
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test --tests "*Validation*"`
  - `./gradlew --no-daemon :rest-api:test --tests "*Telemetry*"`

- [x] T0305 – Generate OpenAPI JSON/YAML snapshots, add snapshot tests, and document Swagger UI usage (F-REST-004, S03-04).
  _Intent:_ Keep contract artifacts in sync with the controller and guard drift.
  _Verification commands:_
  - `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "*OpenApiSnapshotTest"`
  - `rg -n "Swagger" docs/2-how-to/use-ocra-rest-operations.md`

- [x] T0306 – Extend validation for timestamp drift + PIN hashes, with dedicated tests for new reason codes (F-REST-002, S03-05).
  _Intent:_ Cover advanced scenarios highlighted in the spec and prevent regressions.
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test --tests "*Timestamp*"`

- [x] T0307 – Run `./gradlew spotlessApply check`, record telemetry samples, and update knowledge map/operator docs (S03-01–S03-05).
  _Intent:_ Finish the feature with documentation parity and a green build.
  _Verification commands:_
  - `./gradlew --no-daemon spotlessApply check`
  - `rg -n "rest.ocra.evaluate" docs/3-reference`

## Notes / TODOs
- Original R00x/R01x task tables remain in git history before 2025-11-09 for auditors who need the incremental breakdown.
