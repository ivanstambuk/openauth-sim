# Feature Plan 023 – TOTP Operator Support

_Linked specification:_ `docs/4-architecture/features/023/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
Provide RFC 6238 TOTP evaluation/replay across core → persistence → application → CLI/REST → operator UI with telemetry
parity and deterministic fixtures. Success requires:
- Core domain + persistence coexist with HOTP/OCRA (S-023-01).
- Application, CLI, REST evaluation/replay flows + telemetry pass tests/OpenAPI (S-023-02/03).
- Operator console surfaces stored/inline/replay, seeding, and timestamp helpers (S-023-04).
- Fixture catalogue + docs updated and `spotlessApply check`/`qualityGate` green (S-023-05).

## Scope Alignment
- **In scope:** Domain, persistence, telemetry adapters, CLI commands, REST endpoints, operator UI flows, fixtures/docs.
- **Out of scope:** Issuance/enrollment, schema migrations, non-RFC 6238 variants.

## Dependencies & Interfaces
- Shared MapDB schema v1, `CredentialStoreFactory`.
- Telemetry adapters under `TelemetryContracts`.
- CLI Picocli commands, REST controllers (`/api/v1/totp/...`).
- Operator console templates/JS (Feature 017 shell).
- Fixture catalogue `docs/totp_validation_vectors.json`.

## Assumptions & Risks
- **Assumptions:** Schema-v1 stores accept TOTP metadata; CLI/REST telemetry infrastructure ready. 
- **Risks:**
  - Drift/timestamp overrides need careful validation; guard with tests.
  - Replay must stay non-mutating; integration tests enforce counter integrity.

## Implementation Drift Gate
- Track FR/S scenarios against increments (see Scenario Tracking).
- Capture fixture catalogue references + doc diffs before close.
- Rerun `./gradlew --no-daemon :core:test :application:test :cli:test :rest-api:test :ui:test spotlessApply check` prior to handoff.

## Increment Map
1. **I1 – Core domain & persistence (S-023-01)**
   - Add failing tests (vectors/drift), implement TOTP descriptors/persistence.
   - Commands: `./gradlew --no-daemon :core:test :infra-persistence:test`.

2. **I2 – Application + CLI (S-023-02)**
   - Build application services, telemetry adapters, CLI commands.
   - Commands: `./gradlew --no-daemon :application:test :cli:test`.

3. **I3 – REST evaluation/replay (S-023-03)**
   - Add endpoints, OpenAPI updates, MockMvc coverage (stored/inline/replay, drift/timestamp overrides).
   - Commands: `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "*OpenApiSnapshotTest"`, then full `:rest-api:test`.

4. **I4 – Operator UI flows (S-023-04)**
   - Stored/inline/replay panels, seeding, auto-fill toggles, inline defaults, sample auto-apply.
   - Commands: `./gradlew --no-daemon :rest-api:test --tests "*TotpOperatorUi*"`, Selenium suites, `./gradlew spotlessApply check`.

5. **I5 – Fixtures & documentation (S-023-05)**
   - Publish `docs/totp_validation_vectors.json`, update loaders/tests/docs/roadmap/knowledge map.
   - Commands: `./gradlew --no-daemon spotlessApply check` and targeted module tests as needed.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-023-01 | I1 – T-023-01..T-023-04 | Core + persistence. |
| S-023-02 | I2 – T-023-05..T-023-08 | Application + CLI. |
| S-023-03 | I3 – T-023-09..T-023-18 | REST evaluation/replay. |
| S-023-04 | I4 – T-023-11..T-023-20 | Operator UI flows (evaluate/replay/seeding). |
| S-023-05 | I5 – T-023-21..T-023-23 | Fixtures + documentation.

## Analysis Gate
Completed 2025-10-08 when clarifications resolved; rerun only if scope changes.

## Exit Criteria
- Module tests + full Gradle gate green after final change.
- Telemetry events recorded for evaluation/replay.
- Operator UI parity achieved (inline default, auto-fill).
- Fixture catalogue + docs updated.

## Follow-ups / Backlog
- Future feature to deliver TOTP issuance/enrollment and possible clock-source configuration.
