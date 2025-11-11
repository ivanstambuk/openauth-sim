# Feature Plan 004 – REST OCRA Credential Resolution

_Linked specification:_ `docs/4-architecture/features/004/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
- Allow `/api/v1/ocra/evaluate` to evaluate OTPs using either persisted credentials (`credentialId`) or inline secrets without breaking existing clients.
- Enforce mutual exclusivity between input modes with descriptive errors/telemetry (`credential_conflict`, `credential_missing`, `credential_not_found`).
- Keep telemetry, OpenAPI snapshots, operator docs, and knowledge map aligned with the dual-mode contract.

## Scope Alignment
- **In scope:**
  - Resolver helper + persistence gateway integration for credential IDs.
  - Validation layers ensuring mutual exclusivity and reason codes.
  - Telemetry/log updates reflecting `hasCredentialReference`.
  - OpenAPI/doc updates capturing schema changes and operational guidance.
- **Out of scope:**
  - Credential CRUD via REST, CLI/UI changes, authentication/rate limiting, or new credential types.

## Dependencies & Interfaces
- Core persistence store accessed via `CredentialStoreFactory`.
- Existing `OcraResponseCalculator` from Feature 001 and REST controller from Feature 003.
- Telemetry routed through `TelemetryContracts` with redaction guardrails.
- Documentation assets under `docs/2-how-to/`, `docs/3-reference/`, knowledge map, and roadmap.

## Assumptions & Risks
- **Assumptions:** Credential descriptors already exist in persistence; inline mode remains widely used.
- **Risks:**
  - _Telemetry leakage:_ mitigated with log capture tests verifying hashed credential IDs.
  - _Snapshot drift:_ prevented by regenerating JSON/YAML artifacts + snapshot tests.
  - _Latency regression:_ minimized by reusing existing caches/persistence APIs (NFR3).

## Implementation Drift Gate
- Evidence: spec/plan/tasks, updated OpenAPI snapshots, telemetry artifacts, how-to docs.
- Gate checklist: FR/NFR coverage confirmed, telemetry/logs sanitized, docs updated, `./gradlew spotlessApply check` recorded.
- Status: PASS (2025-09-28) when feature shipped; no outstanding drift items.

## Increment Map
1. **I1 – Clarifications & failing tests (T0401–T0402)**
   - _Goal:_ Document dual-mode contract and stage failing MockMvc tests for lookup/missing/conflict cases.
   - _Preconditions:_ Feature 003 endpoint live; persistence adapter available.
   - _Steps:_ update roadmap/knowledge map, add tests covering credentialId success + error paths, keep inline regression tests.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test --tests "*OcraEvaluationEndpointCredentialTest"`
   - _Exit:_ Tests red, clarifications recorded, analysis gate ready.
2. **I2 – Resolver integration & telemetry (T0403)**
   - _Goal:_ Wire resolver to persistence, enforce mutual exclusivity, update telemetry/logs.
   - _Preconditions:_ I1 tests exist.
   - _Steps:_ implement resolver helper, integrate `CredentialStoreFactory`, add reason codes, ensure telemetry includes `hasCredentialReference` + hashed IDs.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test`
   - _Exit:_ MockMvc suite green, telemetry/log capture asserts redaction.
3. **I3 – Documentation & OpenAPI updates (T0404)**
   - _Goal:_ Refresh JSON/YAML snapshots and operator docs with `credentialId` schema/examples.
   - _Preconditions:_ I2 complete.
   - _Steps:_ regenerate snapshots, update how-to + knowledge map, document new reason codes.
   - _Commands:_ `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "*OpenApiSnapshotTest"`
   - _Exit:_ Snapshot tests pass, docs mention dual-mode usage.
4. **I4 – Final verification & wrap-up (T0405)**
   - _Goal:_ Run spotless/check, capture telemetry samples, and record final notes.
   - _Preconditions:_ I3 complete.
   - _Steps:_ run spotless/check, archive telemetry screenshots/logs, mark feature complete.
   - _Commands:_ `./gradlew --no-daemon spotlessApply check`
   - _Exit:_ Green build recorded, plan/tasks updated, roadmap reflects completion.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S04-01 | I1–I2 / T0402–T0403 | Credential lookup success path. |
| S04-02 | I1–I2 / T0402–T0403 | Inline mode regression tests. |
| S04-03 | I2 / T0403 | Mutual exclusivity + reason codes. |
| S04-04 | I2 / T0403 | Telemetry/logging with `hasCredentialReference`. |
| S04-05 | I3 / T0404 | OpenAPI + doc updates describing dual-mode API. |

## Analysis Gate
- Completed 2025-09-28 (PASS). Spec/plan/tasks aligned, tests ordered before implementation, dependencies identified, `./gradlew --no-daemon spotlessApply check` executed.

## Exit Criteria
- `./gradlew --no-daemon :rest-api:test` and `./gradlew --no-daemon spotlessApply check` green.
- Telemetry/log capture tests prove redaction + new reason codes.
- OpenAPI snapshots regenerated; docs/how-to/knowledge map updated.
- No open questions remain for credential resolution scope.

## Follow-ups / Backlog
1. Add REST credential CRUD endpoints (future feature) for managing descriptors.
2. Evaluate authentication/rate limiting requirements once REST facade opens beyond internal use.
3. Consider caching frequently used credential descriptors if lookup latency surfaces in telemetry.
