# Feature Plan 022 – HOTP Operator Support

_Linked specification:_ `docs/4-architecture/features/022/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
Deliver RFC 4226 HOTP evaluation (core → persistence → application → CLI/REST → operator UI) with telemetry parity and
shared fixtures so operators can manage HOTP credentials alongside OCRA. Success requires:
- Core domain + persistence coexistence validated via integration tests (FR-022-01/02, S-022-01).
- CLI/REST services + telemetry adapters validated via unit/integration tests (FR-022-03/04, S-022-02/03).
- Operator UI stored/inline/replay flows plus seeding controls validated via Selenium (FR-022-05, S-022-04).
- Fixture catalogue + docs updated (FR-022-06/07, S-022-05).
- `./gradlew spotlessApply check` green after each increment.

## Scope Alignment
- **In scope:** HOTP domain, persistence wiring, telemetry adapters, CLI/REST endpoints, operator UI evaluation/replay,
  fixture catalogue, documentation.
- **Out of scope:** HOTP issuance/provisioning, schema migrations, other OTP variants.

## Dependencies & Interfaces
- Shared MapDB schema-v1 credential store (`CredentialStoreFactory`).
- Telemetry adapters under `TelemetryContracts`.
- CLI Picocli commands and REST controllers under `rest-api`.
- Operator console templates/JS (Feature 017 shell).
- Fixture catalogue `docs/hotp_validation_vectors.json`.

## Assumptions & Risks
- **Assumptions:** Existing schema-v1 stores can load new metadata without migrations; CLI/REST telemetry infrastructure is
  ready for HOTP events.
- **Risks:** Increased persistence footprint; ensure integration tests cover OCRA + HOTP coexistence. Telemetry dashboards
  may need updates—document new event names for platform owners.

## Implementation Drift Gate
- Map FR IDs to increments/tasks (see Scenario Tracking).
- Capture fixture catalogue + loader references in docs.
- Rerun `./gradlew --no-daemon :core:test :application:test :cli:test :rest-api:test :ui:test spotlessApply check` before
  closing the feature.
- Update roadmap/knowledge map entries to reference HOTP flows.

## Increment Map
1. **I1 – Core domain & fixtures (S-022-01)**
   - _Goal:_ Add failing RFC 4226 tests, implement HOTP generator/validator, add fixture loader.
   - _Commands:_ `./gradlew --no-daemon :core:test`, mutation/ArchUnit suites.

2. **I2 – Persistence + application services (S-022-01, S-022-02)**
   - _Goal:_ Wire HOTP into schema-v1 store, add application services + telemetry adapters.
   - _Commands:_ `./gradlew --no-daemon :application:test`, `./gradlew --no-daemon :core:test`.

3. **I3 – CLI + REST endpoints (S-022-02, S-022-03)**
   - _Goal:_ Implement CLI import/list/evaluate/replay and REST endpoints with OpenAPI updates.
   - _Commands:_ `./gradlew --no-daemon :cli:test`, `./gradlew --no-daemon :rest-api:test`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "*OpenApiSnapshotTest"`.

4. **I4 – Operator UI flows (S-022-04)**
   - _Goal:_ Add stored/inline/replay flows + seeding controls in `/ui/console`.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test --tests "*HotpOperatorUi*"`, Selenium suites, `./gradlew spotlessApply check`.

5. **I5 – Fixture catalogue & documentation (S-022-05)**
   - _Goal:_ Publish `docs/hotp_validation_vectors.json`, update loaders/tests/docs/roadmap/knowledge map.
   - _Commands:_ `./gradlew --no-daemon spotlessApply check`.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-022-01 | I1/I2 – T-022-01..T-022-04 | Core + persistence. |
| S-022-02 | I2/I3 – T-022-05..T-022-08 | Application + CLI coverage. |
| S-022-03 | I3 – T-022-09..T-022-18 | REST evaluation/replay. |
| S-022-04 | I4 – T-022-12..T-022-20 | Operator UI flows + seeding. |
| S-022-05 | I5 – T-022-21..T-022-23 | Fixtures + documentation.

## Analysis Gate
Completed 2025-10-04 when clarifications resolved; rerun only if scope changes.

## Exit Criteria
- HOTP flows pass all module tests plus full Gradle gate.
- Telemetry events recorded for evaluation/issuance/replay.
- Documentation/roadmap updated.
- Fixture catalogue referenced across modules.

## Follow-ups / Backlog
- Future feature to cover HOTP issuance/provisioning once prioritized.
