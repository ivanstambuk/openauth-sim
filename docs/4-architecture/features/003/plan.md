# Feature Plan 003 – OCRA Simulator & Replay

_Linked specification:_ `docs/4-architecture/features/003/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-13

## Vision & Success Criteria
Deliver an end-to-end OCRA simulator stack that keeps the core credential domain, persistence envelopes, CLI/REST
facades, replay workflows, and operator-console panels aligned after renumbering. Success requires:
- RFC 6287 descriptors, calculation helpers, and fixtures continue to serve as the canonical source for all facades.
- REST `/api/v1/ocra/evaluate` + `/api/v1/ocra/verify` mirror CLI semantics (inline/stored, replay) with sanitized
  telemetry and up-to-date OpenAPI snapshots.
- CLI + operator-console flows expose the same validation paths, hashed telemetry, timestamp toggles, and verbose trace
  wiring as REST.
- Schema-v0 migrations remain retired, ensuring every persistence touchpoint enforces schema-v1 invariants only.
- Documentation (roadmap, knowledge map, how-to guides) points to the new Feature 003 artefacts while preserving the
  legacy specs under `docs/4-architecture/features/003/legacy/` for historical reference.

## Scope Alignment
- **In scope:** Core credential domain, persistence envelopes, calculation helpers, REST controllers, CLI commands,
  replay/verification flows, operator-console panels, fixtures, documentation/telemetry updates, migration cleanup.
- **Out of scope:** HOTP/TOTP simulators (Features 001/002), wallet simulators, issuance/provisioning workflows,
  asynchronous/batch APIs, tolerance windows, persistence receipts.

## Dependencies & Interfaces
- `core` module (OcraCredentialDescriptor, SecretMaterial, OcraResponseCalculator, OcraReplayVerifier).
- `application` services for credential lookup + telemetry bridging.
- `rest-api` controllers + SpringDoc/OpenAPI snapshots.
- `cli` Picocli commands for evaluate/replay.
- Operator console templates/JS + verbose trace dock in `rest-api`.
- `CredentialStoreFactory` and schema-v1 MapDB stores.
- TelemetryContracts adapters for every facade.

## Assumptions & Risks
- Schema-v1 remains the only supported persistence format; legacy schema-v0 fixtures appear only in tests guarded by
  `openauth.sim.persistence.skip-upgrade`.
- Telemetry pipelines accept the new event names/fields without further approvals.
- Risk: renumbering may miss a reference; mitigate by running `rg features/00[1-3] docs/` after moves and updating the
  roadmap/knowledge map/session snapshot.

## Implementation Drift Gate
- Map FR IDs to increments/tasks (see Scenario Tracking).
- Keep `legacy/` subdirectories intact for auditability while ensuring the new spec/plan/tasks own the authoritative
  requirements.
- Run `./gradlew --no-daemon spotlessApply check :core:test :application:test :cli:test :rest-api:test :ui:test`
  whenever OCRA modules change; capture command history in `_current-session.md`.

## Increment Map
1. **I1 – Core domain & fixtures (S-003-01)**
   - _Goal:_ Maintain RFC 6287 descriptor/canonicalisation logic plus `OcraResponseCalculator` fixtures.
   - _Commands:_ `./gradlew --no-daemon :core:test`, ArchUnit + mutation suites.
2. **I2 – REST inline evaluation (S-003-02)**
   - _Goal:_ Keep `/api/v1/ocra/evaluate` inline payload handling, validation, telemetry, and OpenAPI snapshots green.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "*OpenApiSnapshotTest"`.
3. **I3 – Stored credential resolution (S-003-02)**
   - _Goal:_ Enforce mutual exclusivity between `credentialId` and `sharedSecretHex`, reuse persistence lookups, document
     reason codes, and keep CLI/REST parity.
   - _Commands:_ `./gradlew --no-daemon :application:test`, targeted `:rest-api:test --tests "*OcraStored*"`.
4. **I4 – Replay & verification (S-003-03)**
   - _Goal:_ Maintain CLI + REST replay commands/endpoints, hashed telemetry, and benchmark artefacts.
   - _Commands:_ `./gradlew --no-daemon :cli:test`, `./gradlew --no-daemon :rest-api:test --tests "*OcraVerify*"`,
     benchmark helper (`IO_OPENAUTH_SIM_BENCHMARK=true ./gradlew :core:test --tests "*OcraReplayBenchmark*"`).
5. **I5 – Operator console flows (S-003-04)**
   - _Goal:_ Keep stored/inline panels, replay workspace, timestamp toggles, preset helpers, verbose trace wiring, and
     Selenium coverage aligned with REST contracts.
   - _Commands:_ `./gradlew --no-daemon :ui:test`, Selenium suites invoked via `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=… ./gradlew --no-daemon :rest-api:test --tests "*OperatorUi*"`.
6. **I6 – Migration & documentation sync (S-003-05)**
   - _Goal:_ Ensure schema-v0 code stays retired, documentation/roadmap/knowledge-map references point to the new
     Feature 003, and the session log (docs/_current-session.md) captures the renumbering.
   - _Commands:_ `./gradlew --no-daemon spotlessApply check`, doc linting as needed.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-003-01 | I1 – T-003-01..T-003-05 | Core descriptors, canonicalisation, calculator fixtures. |
| S-003-02 | I2/I3 – T-003-06..T-003-15 | REST inline + stored evaluation handling. |
| S-003-03 | I4 – T-003-16..T-003-24 | CLI/REST replay + telemetry/benchmark coverage. |
| S-003-04 | I5 – T-003-25..T-003-32 | Operator console evaluation/replay UX + verbose trace integration. |
| S-003-05 | I6 – T-003-33..T-003-36 | Migration retirement + documentation sync. |

## Analysis Gate
Completed initially on 2025-10-05 (when legacy features cleared the gate); reconfirmed during the 2025-11-11 renumbering
because the consolidated spec/plan/tasks now govern the same scope.

## Exit Criteria
- All FR/NFR entries covered by passing tests (core/application/CLI/REST/UI) plus spotless.
- Telemetry + documentation updated to reference Feature 003 instead of the retired Feature 001/003/009 IDs.
- The session log (docs/_current-session.md) records the Batch P1 execution, commands, and verification results.

## Follow-ups / Backlog
- None for OCRA after renumbering; future work moves to the roadmap once new requirements emerge.
