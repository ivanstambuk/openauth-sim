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

- **Drift Gate – Template for OCRA Simulator & Replay**

  - Summary: When (re)validating Feature 003, use this gate to confirm that the OCRA spec, plan, tasks, code/tests, and documentation remain aligned across core, application, CLI, REST, UI, and the Native Java API; ensure no undocumented flows or untested branches have crept in since the last review.

  - **Preconditions**
    - [ ] `docs/4-architecture/features/003/{spec,plan,tasks}.md` updated to the current date with all clarifications folded into normative sections (no legacy “Clarifications” appendices).  
    - [ ] `docs/4-architecture/open-questions.md` has no `Open` entries for Feature 003.  
    - [ ] The following commands have been run in this increment and logged in `docs/_current-session.md`:  
      - `./gradlew --no-daemon :core:test :application:test :cli:test :rest-api:test :ui:test spotlessApply check`  
      - Any OCRA-focused quality checks captured in Feature 013 (e.g., Jacoco/SpotBugs for OCRA packages) when relevant.  

  - **FR ↔ increment ↔ code/test mapping**
    - [ ] For each OCRA FR and NFR in the Feature 003 spec:  
      - Identify the implementing classes in `core` (descriptors, calculators, replay/verifier helpers).  
      - Identify the application/CLI/REST/UI entry points that consume those helpers.  
      - Identify tests that cover success, validation, and failure paths (core calculators, REST endpoints, CLI commands, UI flows).  
    - [ ] Ensure the Scenario Tracking table in this plan still maps FR IDs to increments/tasks and, where necessary, augment it with explicit code/test pointers.  

  - **Seam inventory & contract check (facades + Native Java)**
    - [ ] Confirm the following OCRA surfaces behave as specified and stay consistent with each other:  
      - Native Java API (as documented in `docs/2-how-to/use-ocra-from-java.md` and Feature 014).  
      - CLI (`ocra` commands for evaluate/verify/replay).  
      - REST endpoints (`/api/v1/ocra/evaluate`, `/api/v1/ocra/verify`).  
      - Operator console OCRA panels (stored/inline/replay).  
    - [ ] For each surface, verify:  
      - Supported request shapes (stored vs inline, replay payloads).  
      - Validation semantics (mutually exclusive fields, missing fields, malformed inputs).  
      - Telemetry behaviour (hashed OTPs, reason codes, trace IDs).  

  - **Legacy `legacy/` artefacts**
    - [ ] Confirm that `docs/4-architecture/features/003/legacy/` remains present for historical reference but does not contain authoritative requirements; the live spec/plan/tasks outside `legacy/` must govern behaviour.  
    - [ ] Ensure references in roadmap/knowledge map and how-to docs point to Feature 003 (not legacy IDs) for current behaviour.  

  - **How-to guides, telemetry, and OpenAPI**
    - [ ] Check that OCRA how-to guides (CLI/REST/UI/Native Java) reference the correct commands/endpoints and use request/response examples that match current DTOs and JSON payloads.  
    - [ ] Verify telemetry documentation (e.g., OCRA telemetry snapshots) matches actual `ocra.*` events emitted by the application and facades (hashed fields, status/reason codes).  
    - [ ] Re-run and validate OpenAPI snapshots for OCRA endpoints when behaviour changes (`OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "*OpenApiSnapshotTest"`), and confirm the spec still matches the generated contract.  

  - **UI & replay behaviour**
    - [ ] Confirm operator console OCRA flows (stored/inline/replay) match REST/CLI semantics for:  
      - Input fields and validation messages.  
      - Replay workflows and mismatch diagnostics.  
      - Verbose trace dock wiring (trace IDs, payload snippets).  
    - [ ] Verify UI tests (including any Selenium/JS harnesses) still cover the key scenarios listed in the Scenario Tracking table.  

  - **Drift capture & remediation**
    - [ ] Any high-/medium-impact drift (e.g., spec vs code mismatch, missing tests for a documented flow, telemetry discrepancies) is:  
      - Logged as an `Open` row in `docs/4-architecture/open-questions.md` for Feature 003.  
      - Captured as explicit tasks in `docs/4-architecture/features/003/tasks.md` (and, if cross-cutting, in related cross-feature plans).  
    - [ ] Low-impact drift (typos, minor doc tweaks, small test naming mismatches) is corrected directly in spec/plan/tasks/docs, with a short note added to this section or the plan’s verification log.  

  - **Gate output**
    - [ ] This section is updated with the date of the latest drift gate run, the key commands executed, and a short summary of “matches vs gaps” plus remediation notes.  
    - [ ] `docs/_current-session.md` logs that the OCRA Implementation Drift Gate was executed (with date, commands, and reference to this plan section).  

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
- Native Java API reference note – OCRA’s existing Native Java usage (see `docs/2-how-to/use-ocra-from-java.md`) acts as
  the reference pattern for Feature 014 – Native Java API Facade and ADR-0007; no additional OCRA-specific backlog is
  required unless future features extend the Native Java surface.
