# Feature Plan 002 – TOTP Simulator & Tooling

_Linked specification:_ `docs/4-architecture/features/002/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-13

## Vision & Success Criteria
Provide RFC 6238 TOTP evaluation/replay across core → persistence → application → CLI/REST → operator UI with telemetry
parity and deterministic fixtures. Success requires:
- Core domain + persistence coexist with HOTP/OCRA (S-002-01).
- Application, CLI, REST evaluation/replay flows + telemetry pass tests/OpenAPI (S-002-02/03).
- Operator console surfaces stored/inline/replay, seeding, and timestamp helpers (S-002-04).
- Fixture catalogue + docs updated and `spotlessApply check`/`qualityGate` green (S-002-05).

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

- Summary: Use this gate to ensure TOTP behaviour (core domain, persistence, application services, CLI/REST endpoints, operator UI flows, fixtures, and Native Java API) remains aligned with FR-002-01..07, Scenario S-002-01..05, and cross-cutting Feature 014 guidance.

- **Checklist for future drift-gate runs (agents):**
  - **Preconditions**
    - [ ] `docs/4-architecture/features/002/{spec,plan,tasks}.md` updated to the current date; all clarifications encoded in normative sections.  
    - [ ] `docs/4-architecture/open-questions.md` has no `Open` entries for Feature 002.  
    - [ ] The following commands have been run in this increment and logged in `docs/_current-session.md`:  
      - `./gradlew --no-daemon :core:test :application:test :cli:test :rest-api:test :ui:test spotlessApply check`  
      - Any TOTP-specific OpenAPI snapshot updates when REST contracts change.  

  - **Spec ↔ code/test mapping**
    - [ ] For each TOTP FR and Scenario S-002-01..05, identify implementing classes in:  
      - `core` (TOTP descriptors, generator, clock/window logic, fixtures).  
      - `application` (TOTP evaluation/replay/seed services, telemetry adapters).  
      - `cli` (Picocli TOTP commands).  
      - `rest-api` (TOTP endpoints, request/response models, OpenAPI snapshots).  
      - `ui` (TOTP console panels and JS tests).  
    - [ ] Ensure the Scenario Tracking table still maps Scenario IDs to increments/tasks, with code/test pointers where helpful.  

  - **Native Java API & how-to**
    - [ ] Confirm `TotpEvaluationApplicationService` and its DTOs (EvaluationCommand/EvaluationResult) behave as described in the Feature 002 spec and Feature 014 pattern.  
    - [ ] Verify Javadoc for `TotpEvaluationApplicationService` and key DTOs labels it as a Native Java API seam, references Feature 002/014 FRs and ADR‑0007, and points to `docs/2-how-to/use-totp-from-java.md`.  
    - [ ] Ensure `use-totp-from-java.md` uses the same types/methods, covers stored and inline flows (including out-of-window failures), and reflects the behaviour tested in `TotpNativeJavaApiUsageTest`.  

  - **Fixtures & docs**
    - [ ] Check that `docs/totp_validation_vectors.json` and any TOTP fixtures remain in sync with loader code and tests.  
    - [ ] Confirm how-to guides and README references for TOTP still point to the correct commands/endpoints and fixture usage.  
    - [ ] Update roadmap/knowledge map entries to reference TOTP flows if they changed since the last gate.  

  - **Drift capture & remediation**
    - [ ] Any high-/medium-impact drift (spec vs code mismatch, missing tests for documented flows, outdated fixtures) is:  
      - Logged as an `Open` entry in `docs/4-architecture/open-questions.md` for Feature 002.  
      - Captured as explicit tasks in `docs/4-architecture/features/002/tasks.md`.  
    - [ ] Low-impact drift (typos, minor doc misalignments, small fixture tweaks) is corrected directly, with a brief note added in this section or the plan’s verification log.  

  - **Gate output**
    - [ ] This section is updated with the latest drift gate run date, key commands executed, and a concise “matches vs gaps” summary plus remediation notes.  
    - [ ] `docs/_current-session.md` logs that the TOTP Implementation Drift Gate was executed (date, commands, and reference to this plan section).  

## Increment Map
1. **I1 – Core domain & persistence (S-002-01)**
   - Add failing tests (vectors/drift), implement TOTP descriptors/persistence.
   - Commands: `./gradlew --no-daemon :core:test :infra-persistence:test`.

2. **I2 – Application + CLI (S-002-02)**
   - Build application services, telemetry adapters, CLI commands.
   - Commands: `./gradlew --no-daemon :application:test :cli:test`.

3. **I3 – REST evaluation/replay (S-002-03)**
   - Add endpoints, OpenAPI updates, MockMvc coverage (stored/inline/replay, drift/timestamp overrides).
   - Commands: `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "*OpenApiSnapshotTest"`, then full `:rest-api:test`.

4. **I4 – Operator UI flows (S-002-04)**
   - Stored/inline/replay panels, seeding, auto-fill toggles, inline defaults, sample auto-apply.
   - Commands: `./gradlew --no-daemon :rest-api:test --tests "*TotpOperatorUi*"`, Selenium suites, `./gradlew spotlessApply check`.

5. **I5 – Fixtures & documentation (S-002-05)**
   - Publish `docs/totp_validation_vectors.json`, update loaders/tests/docs/roadmap/knowledge map.
   - Commands: `./gradlew --no-daemon spotlessApply check` and targeted module tests as needed.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-002-01 | I1 – T-002-01..T-002-04 | Core + persistence. |
| S-002-02 | I2 – T-002-05..T-002-08 | Application + CLI. |
| S-002-03 | I3 – T-002-09..T-002-18 | REST evaluation/replay. |
| S-002-04 | I4 – T-002-11..T-002-20 | Operator UI flows (evaluate/replay/seeding). |
| S-002-05 | I5 – T-002-21..T-002-23 | Fixtures + documentation.

## Analysis Gate
Completed 2025-10-08 when clarifications resolved; rerun only if scope changes.

## Exit Criteria
- Module tests + full Gradle gate green after final change.
- Telemetry events recorded for evaluation/replay.
- Operator UI parity achieved (inline default, auto-fill).
- Fixture catalogue + docs updated.

## Follow-ups / Backlog
- Future feature to deliver TOTP issuance/enrollment and possible clock-source configuration.
- Align TOTP Native Java API with Feature 014 – Native Java API Facade and ADR-0007 by exposing a documented Java entry point for TOTP evaluation (mirroring OCRA’s pattern) and adding a `use-totp-from-java.md` how-to once prioritised.
