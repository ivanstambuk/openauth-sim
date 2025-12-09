# Feature Plan 004 – FIDO2/WebAuthn Assertions & Attestations

_Linked specification:_ [docs/4-architecture/features/004/spec.md](docs/4-architecture/features/004/spec.md)  
_Status:_ Complete (maintenance change pending FR-004-05)  
_Last updated:_ 2025-12-09

## Vision & Success Criteria
Unify all assertion evaluation, replay, and attestation generation/verification work under Feature 004 so the CLI, REST, and UI surfaces derive their requirements from a single FR/NFR matrix, share fixtures, and emit the same sanitized telemetry events (`fido2.evaluate`, `fido2.replay`, `fido2.attest`, `fido2.attestReplay`). Success is measured by core/persistence assertion tests, attestation generator/metadata tests, Selenium coverage, and documentation/verification logs staying green after `./gradlew --no-daemon spotlessApply check`.

## Scope Alignment
- **In scope:** Core/verifier helpers for WebAuthn assertions, MapDB-based replay, attestation generation/replay services (packed, FIDO-U2F, TPM, Android Key), CLI/REST endpoints, operator UI panels, telemetry adapters, and documentation of the combined flows.
- **Out of scope:** WebAuthn registration/authenticator emulation, new persistence stores beyond MapDB, and tooling changes unrelated to the assertion/attestation flows.

## Dependencies & Interfaces
- [docs/webauthn_w3c_vectors.json](docs/webauthn_w3c_vectors.json), [docs/webauthn_assertion_vectors.json](docs/webauthn_assertion_vectors.json), ``docs/webauthn_attestation`/*` fixture bundles.
- `infra-persistence` `CredentialStoreFactory` for seeding and storing assertion/attestation metadata.
- `application.telemetry.TelemetryContracts` adapters plus CLI/REST telemetry bridges.
- Operator UI shared helpers (ui/shared/secret-fields.js) and Selenium harnesses for the evaluation/attestation panels.
- OpenAPI contract snapshots (`docs/3-reference/rest-openapi.*`) that capture the updated `/api/v1/fido2/*` and `/api/v1/webauthn/*` endpoints.

## Assumptions & Risks
- **Assumptions:** The fixture bundles already cover packed/FIDO-U2F/TPM/Android Key payloads; MapDB schema extensions accept attestation metadata beside existing assertion rows; CLI/REST infra can consume the new service signatures without dependency upgrades.
- **Risks / Mitigations:** Trust-anchor metadata growth could break persistence queries (mitigate by regression tests in `infra-persistence` and by caching via `WebAuthnMetadataCatalogue`); operator console toggles may regress (mitigate via targeted Selenium tests); OpenAPI snapshots might misalign with REST controllers (mitigate by running `OPENAPI_SNAPSHOT_WRITE=true ...` before the drift gate and logging the outputs in `_current-session.md`).

## Implementation Drift Gate

- Summary: Use this gate to ensure FIDO2/WebAuthn behaviours (assertion evaluation/replay, attestation generation/replay, trust-anchor handling, operator UI flows, telemetry/docs, and the Native Java API seam) remain aligned with Feature 004 FR/NFRs, Scenario S-004-01..05, and cross-cutting Feature 014 guidance.

- **Checklist for future drift-gate runs (agents):**
  - **Preconditions**
    - [ ] ``docs/4-architecture/features/004`/{spec,plan,tasks}.md` updated to the current date; all clarifications encoded in normative sections.  
    - [ ] [docs/4-architecture/open-questions.md](docs/4-architecture/open-questions.md) has no `Open` entries for Feature 004.  
    - [ ] The following commands have been run in this increment and logged in [docs/_current-session.md](docs/_current-session.md):  
      - `git config core.hooksPath` (hook guard).  
      - `./gradlew --no-daemon spotlessApply check`.  
      - Targeted WebAuthn tests from this plan (core/application/CLI/REST/UI, including Selenium).  

  - **Spec ↔ code/test mapping**
    - [ ] For each WebAuthn FR and Scenario S-004-01..05, identify implementing classes in:  
      - `core` (verifier, attestation helpers, metadata catalogue, fixtures).  
      - `infra-persistence` (WebAuthn persistence extensions).  
      - `application` (assertion/attestation services and telemetry adapters).  
      - `cli` (FIDO2 CLI commands).  
      - `rest-api` (WebAuthn endpoints, request/response models, problem-details mappings).  
      - `ui` (FIDO2 operator console panels and JS tests).  
    - [ ] Ensure Scenario Tracking still maps scenarios to increments/tasks and, where helpful, add explicit code/test pointers.  

  - **Native Java API & how-to**
    - [ ] Confirm `WebAuthnEvaluationApplicationService` and its DTOs (EvaluationCommand/EvaluationResult) behave as described in the spec and Feature 014’s Native Java pattern.  
    - [ ] Verify Javadoc for `WebAuthnEvaluationApplicationService` labels it as a Native Java API seam, references Feature 004/014 FRs and ADR‑0007, and points to [docs/2-how-to/use-fido2-from-java.md](docs/2-how-to/use-fido2-from-java.md).  
    - [ ] Ensure `use-fido2-from-java.md` uses the same types/methods and reflects both success and failure paths covered by `WebAuthnNativeJavaApiUsageTest`.  

  - **OpenAPI, fixtures, telemetry, UI**
    - [ ] Re-run OpenAPI snapshots for FIDO2/WebAuthn endpoints when contracts change and confirm `rest-openapi` output matches the spec and controllers.  
    - [ ] Verify fixtures ([docs/webauthn_w3c_vectors.json](docs/webauthn_w3c_vectors.json), [docs/webauthn_assertion_vectors.json](docs/webauthn_assertion_vectors.json), ``docs/webauthn_attestation`/*`) remain in sync with loader code and tests.  
    - [ ] Confirm telemetry events (assertion/attestation) match documented event names and field redactions.  
    - [ ] Check that the FIDO2 operator console flows (stored/inline assertions, attestation panels, trust-anchor summaries) behave as specified and are covered by Selenium/JS tests.  
    - [ ] Verify that the FIDO2/WebAuthn protocol reference page and diagrams ([docs/3-reference/protocols/fido2-webauthn.md](docs/3-reference/protocols/fido2-webauthn.md) and `docs/3-reference/protocols/diagrams/fido2-webauthn-*.puml`/`*.png`) accurately describe the current registration/authentication/attestation flows, parameters, and core/application entry points; update them in the same increment when behaviour changes.  

  - **Drift capture & remediation**
    - [ ] Any high-/medium-impact drift (spec vs code mismatch, missing tests, broken UI fixtures, outdated OpenAPI snapshots) is:  
      - Logged as an `Open` entry in [docs/4-architecture/open-questions.md](docs/4-architecture/open-questions.md) for Feature 004.  
      - Captured as explicit tasks in [docs/4-architecture/features/004/tasks.md](docs/4-architecture/features/004/tasks.md).  
    - [ ] Low-impact drift (typos, minor example updates, small fixture corrections) is corrected directly, with a short note added in this section or the plan’s verification log.  

  - **Gate output**
    - [ ] This section is updated with the latest drift gate run date, key commands executed, and a concise “matches vs gaps” summary plus remediation notes.  
    - [ ] [docs/_current-session.md](docs/_current-session.md) logs that the WebAuthn Implementation Drift Gate was executed (date, commands, and reference to this plan section).  

2025-11-13 consolidation sweep: `git config core.hooksPath`, `./gradlew --no-daemon spotlessApply check`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.*WebAuthn*"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`, `node --test [rest-api/src/test/javascript/emv/console.test.js](rest-api/src/test/javascript/emv/console.test.js)`, and `./gradlew --no-daemon :ui:test` were executed and logged in `_current-session.md`.

## Increment Map
1. **I1 – Assertion evaluation & replay coverage**  
   - _Goal:_ Align core, persistence, CLI, REST, and UI surfaces with FR-004-01/FR-004-02 so stored and inline assertions behave deterministically.  
   - _Preconditions:_ Fixtures load ([docs/webauthn_w3c_vectors.json](docs/webauthn_w3c_vectors.json), [docs/webauthn_assertion_vectors.json](docs/webauthn_assertion_vectors.json)) and `CredentialStore` seeding works.  
   - _Steps:_ Stage failing core/persistence tests (WebAuthn verifier, MapDB integration), implement CLI `maintenance fido2 evaluate/replay`, update REST endpoints (`/api/v1/fido2/evaluate`, `/api/v1/fido2/replay`, `/api/v1/fido2/credentials`), refresh operator console evaluate/replay panels, and ensure telemetry events emit hashed identifiers.  
   - _Commands:_  
     - `./gradlew --no-daemon :core:test --tests "*WebAuthnAssertionVerifierTest"`  
     - `./gradlew --no-daemon :infra-persistence:test --tests "*CredentialStore*WebAuthn*"`  
     - `./gradlew --no-daemon :cli:test --tests "*Fido2CliEvaluateTest"`  
     - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2*EndpointTest"`  
     - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`  
   - _Exit:_ Core/persistence assertion tests and CLI/REST/Selenium checks pass with telemetry events recorded.
2. **I2 – Attestation generation & replay services**  
   - _Goal:_ Deliver FR-004-03/FR-004-04 across generator services, trust-anchor resolvers, CLI/REST commands, and UI panels with `trustAnchorSummaries`.  
   - _Preconditions:_ Attestation fixtures (``docs/webauthn_attestation`/*`) exist and CLI/REST parse anchors.  
   - _Steps:_ Stage attestation generator/replay tests (packed/FIDO-U2F/TPM/Android Key), extend CLI `maintenance fido2 attest`, `attest-replay`, and `seed-attestations`, wire `/api/v1/webauthn/attest`, `/api/v1/webauthn/attest/replay`, `/api/v1/webauthn/attestations/{id}`, regenerate OpenAPI snapshots, and refresh UI attestation forms + metadata cards.  
   - _Commands:_  
     - `./gradlew --no-daemon :core:test --tests "*WebAuthnAttestationGeneratorTest"`  
     - `./gradlew --no-daemon :application:test --tests "*WebAuthnAttestation*ServiceTest"`  
     - `./gradlew --no-daemon :cli:test --tests "*Fido2CliAttestationTest"`  
     - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`  
     - `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`  
     - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`  
   - _Exit:_ Attestation generation/replay commands emit `fido2.attest`/`fido2.attestReplay` telemetry with trust-anchor metadata and UI cards render `trustAnchorSummaries`.
3. **I3 – Documentation, telemetry, and governance sync**  
   - _Goal:_ Ensure knowledge artefacts document the merged workflow and all verification commands (hook guard, spotless, targeted suites) appear in `_current-session.md`.  
   - _Preconditions:_ I1/I2 tests are green; knowledge map snapshots available for update.  
   - _Steps:_ Update the Feature 004 spec/plan/tasks per this migration, refresh [docs/4-architecture/knowledge-map.md](docs/4-architecture/knowledge-map.md) to reflect the new ownership, and capture the hook guard/verification output inside `_current-session.md`.  
   - _Commands:_ `./gradlew --no-daemon spotlessApply check`, `./gradlew --no-daemon qualityGate`, and the targeted REST/UI commands listed earlier (document each in `_current-session.md`).  
   - _Exit:_ Docs, session log ([docs/_current-session.md](docs/_current-session.md)), and session snapshot describe the merged feature and recorded verification commands.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|----------------------------|-------|
| S-004-01 | I1 / T-004-01..T-004-03 | Stored assertion evaluation and telemetry coverage. |
| S-004-02 | I1 / T-004-04 | Inline assertion evaluation before persistence is touched. |
| S-004-03 | I2 / T-004-05..T-004-06 | Attestation generation with trust-anchor selection. |
| S-004-04 | I2 / T-004-07 | Attestation replay with metadata summaries. |
| S-004-05 | I1+I2 / T-004-08 | UI toggles and shared `secret-fields` helpers mid-flow. |

## Analysis Gate
Log the gate results in this plan after the first clean `./gradlew --no-daemon spotlessApply check`. Confirm each scenario matches a test path, mention any failing commands in `_current-session.md`, and attach the executed [docs/5-operations/analysis-gate-checklist.md](docs/5-operations/analysis-gate-checklist.md) snapshot. Ensure the gate reiterates the requirement to rerun `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"` and the CLI tests before closing the plan.

## Exit Criteria
- `./gradlew --no-daemon spotlessApply check` (batch baseline).  
- `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`.  
- `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"` and `node --test [rest-api/src/test/javascript/emv/console.test.js](rest-api/src/test/javascript/emv/console.test.js)`.  
- CLI and application attestation tests (`:cli:test --tests "*Fido2CliAttestationTest"`, `:application:test --tests "*WebAuthnAttestation*ServiceTest"`) succeed.  
- `_current-session.md` notes the hook guard and these verification commands with timestamps.

## Follow-ups / Backlog
- Future Features 007/008 will cover the mdoc/SIOPv2 simulators; ensure their specs reference Feature 004’s telemetry/fixture contracts when they reuse WebAuthn assets.  
- Monitor [docs/4-architecture/knowledge-map.md](docs/4-architecture/knowledge-map.md) for new dependencies introduced by future trust-anchor catalog enhancements and add entries referencing `WebAuthnMetadataCatalogue`.  
- Schedule a drift gate once Feature 004 wraps to verify no regression tests were skipped and to capture lessons in the plan appendix.
- Align FIDO2/WebAuthn Native Java API seams with Feature 014 – Native Java API Facade and ADR-0007 by identifying a small set of public Java entry points (assertion/attestation helpers or application services), documenting them, and adding a `use-fido2-from-java.md` how-to when scheduled.
- Implement FR-004-05 (counter optional with Unix-seconds default) across CLI/REST/Native Java, update fixtures/OpenAPI/how-tos, and add tests + telemetry fields for derived counters.
