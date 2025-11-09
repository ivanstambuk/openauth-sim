# Feature 004 Tasks – REST OCRA Credential Resolution

_Status:_ Complete  
_Last updated:_ 2025-11-09_

## Checklist
- [x] T0401 – Refresh spec/plan/tasks to capture dual-mode requirements and roadmap notes (F-REST-010–F-REST-013, S04-01–S04-05).
  _Intent:_ Document the `credentialId` vs inline mode contract before implementation.
  _Verification commands:_
  - `less docs/4-architecture/features/004/spec.md`
  - `rg -n "Feature 004" docs/4-architecture/roadmap.md`

- [x] T0402 – Add failing MockMvc tests for credential lookup success, missing credential, conflict, and legacy inline paths (F-REST-010, F-REST-011, F-REST-012, S04-01–S04-03).
  _Intent:_ Capture behavioural expectations for both input modes and error codes.
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test --tests "*OcraEvaluationEndpointCredentialTest"`

- [x] T0403 – Implement resolver integration, mutual exclusivity enforcement, and telemetry flag updates (F-REST-010–F-REST-013, S04-01–S04-04).
  _Intent:_ Load descriptors from persistence, keep inline mode intact, and emit sanitized metadata.
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test`
  - `rg -n "hasCredentialReference" rest-api/src`

- [x] T0404 – Regenerate OpenAPI snapshots, operator docs, and telemetry references to reflect the dual-mode API (F-REST-013, S04-05).
  _Intent:_ Ensure documentation stays aligned with the updated contract.
  _Verification commands:_
  - `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "*OpenApiSnapshotTest"`
  - `rg -n "credentialId" docs/2-how-to/use-ocra-rest-operations.md`

- [x] T0405 – Run `./gradlew spotlessApply check`, capture final plan notes, and mark feature complete (S04-01–S04-05).
  _Intent:_ Wrap up with a green build and audit trail.
  _Verification commands:_
  - `./gradlew --no-daemon spotlessApply check`

## Notes / TODOs
- Earlier R013–R016 tables remain available in git history if finer-grained traceability is needed.
