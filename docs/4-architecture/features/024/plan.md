# Feature Plan 024 – FIDO2/WebAuthn Operator Support

_Linked specification:_ `docs/4-architecture/features/024/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
Provide deterministic WebAuthn assertion verification across core → persistence → application → CLI/REST → operator UI
with telemetry parity, W3C + synthetic fixtures, and curated seeding/presets. Success indicators align with S-024-01..05:
- Core verification engine validates W3C + synthetic vectors (S-024-01).
- Persistence/application/telemetry handle stored/inline/replay flows (S-024-02).
- CLI/REST facades expose evaluate/replay endpoints + OpenAPI updates (S-024-03).
- Operator UI delivers stored/inline/replay panels, seeding controls, router parity, layout fixes (S-024-04).
- Fixture catalogues + documentation updated, Gradle gate green (S-024-05).

## Scope Alignment
- **In scope:** Core verifier, fixtures, persistence descriptors, telemetry adapters, CLI/REST endpoints, operator UI,
  seed/preset utilities, documentation.
- **Out of scope:** WebAuthn registration/attestation, authenticator emulation, dependency upgrades.

## Dependencies & Interfaces
- MapDB schema v1 + `CredentialStoreFactory`.
- Telemetry contracts for `fido2.evaluate`, `fido2.replay`, `fido2.seed`.
- CLI Picocli commands, REST controllers (`/api/v1/fido2/...`).
- Operator console tab (Feature 020) for FIDO2 UI.
- Fixture files `docs/webauthn_w3c_vectors.json`, `docs/webauthn_assertion_vectors.json`.

## Assumptions & Risks
- **Assumptions:** W3C vectors converted locally; JSON bundle sanitized for gitleaks.
- **Risks:** Router regressions due to new parameters; mitigate with Selenium tests. Large fixture set increases test time;
  keep dataset curated and document bundling process.

## Implementation Drift Gate
- Map FR/S scenarios to increments (see Scenario Tracking).
- Capture fixture conversion steps + doc updates before close.
- Rerun `./gradlew --no-daemon :core:test :application:test :cli:test :rest-api:test :ui:test spotlessApply check`
  before handoff; ensure OpenAPI snapshots + telemetry docs updated.

## Increment Map
1. **I1 – Fixture bootstrap + core verifier (S-024-01)**
   - Convert W3C vectors, stage failing core tests, implement verifier.
   - Commands: `./gradlew --no-daemon :core:test`.

2. **I2 – Persistence + application services (S-024-02)**
   - Add MapDB descriptors/integration tests, implement evaluation/replay services + telemetry.
   - Commands: `./gradlew --no-daemon :core:test :application:test`.

3. **I3 – CLI/REST facades (S-024-03)**
   - Stage + implement CLI commands, REST endpoints, OpenAPI snapshots, sample endpoints.
   - Commands: `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "*OpenApiSnapshotTest"`,
     `./gradlew --no-daemon :cli:test :rest-api:test`.

4. **I4 – Operator UI + router parity (S-024-04)**
   - Stored/inline/replay panels, seeding controls, router/shared query params, layout clamp.
   - Commands: `./gradlew --no-daemon :rest-api:test --tests "*Fido2OperatorUi*"`, Selenium suites,
     `./gradlew --no-daemon spotlessApply check`.

5. **I5 – Fixtures/documentation/telemetry polish (S-024-05)**
   - Finalize JSONL catalogues, JWK ordering, doc updates, router docs, knowledge map.
   - Commands: `./gradlew --no-daemon spotlessApply check`.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-024-01 | I1 / T-024-01..T-024-04 | Fixture + verifier. |
| S-024-02 | I2 / T-024-05..T-024-10 | Persistence/app services/telemetry. |
| S-024-03 | I3 / T-024-11..T-024-15 | CLI + REST evaluate/replay. |
| S-024-04 | I4 / T-024-16..T-024-21 | Operator UI + router + layout. |
| S-024-05 | I5 / T-024-22..T-024-24 | Fixture catalogues + documentation.

## Analysis Gate
Completed 2025-10-09; clarifications resolved, open-questions log empty. Re-run only if scope expands beyond evaluation/replay.

## Exit Criteria
- All module tests + `spotlessApply check` + `qualityGate` green.
- Telemetry events recorded for evaluate/replay/seed.
- Operator console parity achieved (inline defaults, seeding visibility, router sync).
- Fixture catalogues + documentation updated with provenance labels.

## Follow-ups / Backlog
- Future feature to cover WebAuthn registration/attestation and authenticator emulation once prioritized.
