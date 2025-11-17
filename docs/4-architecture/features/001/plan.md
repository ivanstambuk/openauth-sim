# Feature Plan 001 – HOTP Simulator & Tooling

_Linked specification:_ [docs/4-architecture/features/001/spec.md](docs/4-architecture/features/001/spec.md)  
_Status:_ Complete  
_Last updated:_ 2025-11-13

## Vision & Success Criteria
Deliver RFC 4226 HOTP evaluation (core → persistence → application → CLI/REST → operator UI) with telemetry parity and
shared fixtures so operators can manage HOTP credentials alongside OCRA. Success requires:
- Core domain + persistence coexistence validated via integration tests (FR-001-01/02, S-001-01).
- CLI/REST services + telemetry adapters validated via unit/integration tests (FR-001-03/04, S-001-02/03).
- Operator UI stored/inline/replay flows plus seeding controls validated via Selenium (FR-001-05, S-001-04).
- Fixture catalogue + docs updated (FR-001-06/07, S-001-05).
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
- Fixture catalogue [docs/hotp_validation_vectors.json](docs/hotp_validation_vectors.json).

## Assumptions & Risks
- **Assumptions:** Existing schema-v1 stores can load new metadata without migrations; CLI/REST telemetry infrastructure is
  ready for HOTP events.
- **Risks:** Increased persistence footprint; ensure integration tests cover OCRA + HOTP coexistence. Telemetry dashboards
  may need updates—document new event names for platform owners.

## Implementation Drift Gate

- Summary: Use this gate to ensure HOTP behaviour (core domain, persistence, application services, CLI/REST endpoints, operator UI flows, fixtures, and Native Java API) remains aligned with FR-001-01..07, Scenario S-001-01..05, and cross-cutting Feature 014 guidance.

- **Checklist for future drift-gate runs (agents):**
  - **Preconditions**
    - [ ] ``docs/4-architecture/features/001`/{spec,plan,tasks}.md` updated to the current date; all clarifications encoded in normative sections.  
    - [ ] [docs/4-architecture/open-questions.md](docs/4-architecture/open-questions.md) has no `Open` entries for Feature 001.  
    - [ ] The following commands have been run in this increment and logged in [docs/_current-session.md](docs/_current-session.md):  
      - `./gradlew --no-daemon :core:test :application:test :cli:test :rest-api:test :ui:test spotlessApply check`  
      - Any HOTP-specific OpenAPI snapshot updates when REST contracts change.  

  - **Spec ↔ code/test mapping**
    - [ ] For each HOTP FR and Scenario S-001-01..05, identify implementing classes in:  
      - `core` (HOTP descriptors, generator, hash algorithms, fixtures).  
      - `application` (HOTP evaluation/issuance/replay services, telemetry adapters).  
      - `cli` (Picocli HOTP commands).  
      - `rest-api` (HOTP endpoints, request/response models, OpenAPI snapshots).  
      - `ui` (HOTP console panels and JS tests).  
    - [ ] Ensure the Scenario Tracking table in this plan still maps Scenario IDs to increments/tasks and, where needed, add explicit code/test pointers.  

  - **Native Java API & how-to**
    - [ ] Confirm `HotpEvaluationApplicationService` and its DTOs (EvaluationCommand/EvaluationResult) behave as described in the Feature 001 spec and Feature 014 pattern.  
    - [ ] Verify Javadoc for `HotpEvaluationApplicationService` and key DTOs labels it as a Native Java API seam, references Feature 001/014 FRs and ADR‑0007, and points to [docs/2-how-to/use-hotp-from-java.md](docs/2-how-to/use-hotp-from-java.md).  
    - [ ] Ensure `use-hotp-from-java.md` uses the same types/methods, covers stored and inline evaluations, and reflects success/failure branches that appear in tests.  

  - **Fixtures & docs**
    - [ ] Check that [docs/hotp_validation_vectors.json](docs/hotp_validation_vectors.json) and any HOTP fixtures remain in sync with loader code and tests.  
    - [ ] Confirm how-to guides and README references for HOTP still point to the correct commands/endpoints and fixture usage.  
    - [ ] Verify that the HOTP protocol reference page and diagrams ([docs/3-reference/protocols/hotp.md](docs/3-reference/protocols/hotp.md) and `docs/3-reference/protocols/diagrams/hotp-*.puml`/`*.png`) accurately describe the current HOTP flows, parameters, and core/application entry points; update them in the same increment when behaviour changes.  
    - [ ] Update roadmap/knowledge map entries to reference HOTP flows if they changed since the last gate.  

  - **Drift capture & remediation**
    - [ ] Any high-/medium-impact drift (e.g., spec vs code mismatch, missing tests for documented flows, outdated fixtures) is:  
      - Logged as an `Open` entry in [docs/4-architecture/open-questions.md](docs/4-architecture/open-questions.md) for Feature 001.  
      - Captured as explicit tasks in [docs/4-architecture/features/001/tasks.md](docs/4-architecture/features/001/tasks.md).  
    - [ ] Low-impact drift (typos, minor doc misalignments, small fixture tweaks) is corrected directly, with a brief note added in this section or the plan’s verification log.  

  - **Gate output**
    - [ ] This section is updated with the latest drift gate run date, key commands executed, and a concise “matches vs gaps” summary plus remediation notes.  
    - [ ] [docs/_current-session.md](docs/_current-session.md) logs that the HOTP Implementation Drift Gate was executed (date, commands, and reference to this plan section).  

## Increment Map
1. **I1 – Core domain & fixtures (S-001-01)**
   - _Goal:_ Add failing RFC 4226 tests, implement HOTP generator/validator, add fixture loader.
   - _Commands:_ `./gradlew --no-daemon :core:test`, mutation/ArchUnit suites.

2. **I2 – Persistence + application services (S-001-01, S-001-02)**
   - _Goal:_ Wire HOTP into schema-v1 store, add application services + telemetry adapters.
   - _Commands:_ `./gradlew --no-daemon :application:test`, `./gradlew --no-daemon :core:test`.

3. **I3 – CLI + REST endpoints (S-001-02, S-001-03)**
   - _Goal:_ Implement CLI import/list/evaluate/replay and REST endpoints with OpenAPI updates.
   - _Commands:_ `./gradlew --no-daemon :cli:test`, `./gradlew --no-daemon :rest-api:test`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "*OpenApiSnapshotTest"`.

4. **I4 – Operator UI flows (S-001-04)**
   - _Goal:_ Add stored/inline/replay flows + seeding controls in /ui/console.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test --tests "*HotpOperatorUi*"`, Selenium suites, `./gradlew spotlessApply check`.

5. **I5 – Fixture catalogue & documentation (S-001-05)**
   - _Goal:_ Publish [docs/hotp_validation_vectors.json](docs/hotp_validation_vectors.json), update loaders/tests/docs/roadmap/knowledge map.
   - _Commands:_ `./gradlew --no-daemon spotlessApply check`.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-001-01 | I1/I2 – T-001-01..T-001-04 | Core + persistence. |
| S-001-02 | I2/I3 – T-001-05..T-001-08 | Application + CLI coverage. |
| S-001-03 | I3 – T-001-09..T-001-18 | REST evaluation/replay. |
| S-001-04 | I4 – T-001-12..T-001-20 | Operator UI flows + seeding. |
| S-001-05 | I5 – T-001-21..T-001-23 | Fixtures + documentation.

## Analysis Gate
Completed 2025-10-04 when clarifications resolved; rerun only if scope changes.

## Exit Criteria
- HOTP flows pass all module tests plus full Gradle gate.
- Telemetry events recorded for evaluation/issuance/replay.
- Documentation/roadmap updated.
- Fixture catalogue referenced across modules.

## Follow-ups / Backlog
- Future feature to cover HOTP issuance/provisioning once prioritized.
- Align HOTP Native Java API with Feature 014 – Native Java API Facade and ADR-0007 by exposing a small, documented Java entry point (e.g., evaluation helper or application service) plus a `use-hotp-from-java.md` guide modelled on `use-ocra-from-java.md`.
