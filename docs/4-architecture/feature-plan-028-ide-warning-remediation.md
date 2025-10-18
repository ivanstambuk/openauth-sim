# Feature Plan 028 – IDE Warning Remediation

_Linked specification:_ `docs/4-architecture/specs/feature-028-ide-warning-remediation.md`  
_Status:_ In Progress  
_Last updated:_ 2025-10-18

## Objective
Eliminate the IDE diagnostics reported on 2025-10-18 by strengthening assertions/usages where variables were previously unused and by removing redundant assignments, while keeping module behaviour unchanged.

## Current Context
- Application TOTP evaluation/replay command records contain redundant `evaluationInstant` assignments that trigger warnings without providing additional validation.
- Several tests across CLI, REST, and Selenium scenarios retain locals or constants intended for future assertions; warnings highlight that they are currently unused.
- Core FIDO2 verification and WebAuthn replay services decode metadata that should be leveraged for assertions/coverage but currently is not.
- The IDE snapshot targets specific files; broader static analysis has not been requested for this increment.

## Increment Breakdown (≤10 minutes each)
1. **I1 – Governance sync and documentation updates**  
   - Create spec/plan/tasks entries for Feature 028.  
   - Update roadmap and current-session snapshot; capture the accepted clarification in the spec.  
   - Log and resolve the corresponding open question.  
   - _2025-10-18 – Completed: spec/plan/tasks created, roadmap/current-session updated, open question resolved with Option B, tasks checklist promoted to In Progress._

2. **I2 – Application TOTP command constructors**  
   - Remove redundant `evaluationInstant` assignments from `TotpEvaluationApplicationService` and `TotpReplayApplicationService`.  
   - Ensure optional semantics remain intact; run targeted application tests.  
   - _2025-10-18 – Completed: redundant assignments removed, optional timestamp handling untouched, and `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.totp.*"` passed._

3. **I3 – Core/WebAuthn services**  
   - Assert decoded `clientData` in `WebAuthnAttestationVerifier`.  
   - Utilize `metadata` locals in `WebAuthnReplayService` for validation/telemetry assertions.  
   - Drop obsolete suppressions in `WebAuthnPublicKeyDecoderTest`.  
   - Execute core/application/REST unit tests.  
   - _2025-10-18 – Completed: WebAuthn attestation verifier now validates client data fields post-parse, replay service folds metadata into error details, suppression removed by leveraging typed parsing, and targeted tests (`:core:test --tests "io.openauth.sim.core.fido2.WebAuthnAttestationVerifierTest"`, `:application:test --tests "io.openauth.sim.application.fido2.WebAuthnPublicKeyDecoderTest"`, `:rest-api:test --tests "io.openauth.sim.rest.Fido2ReplayEndpointTest"`) all pass._

4. **I4 – CLI and REST tests**  
   - Update `TotpCliTest` and `Fido2AttestationEndpointTest` to assert on previously unused locals.  
   - Run CLI and targeted REST tests.  
   - _2025-10-18 – Completed: CLI now asserts the generated OTP with `TotpGenerator`, REST attestation tests validate credential IDs and payload content, and targeted runs (`:cli:test --tests "io.openauth.sim.cli.TotpCliTest"`, `:rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`) passed._

5. **I5 – Selenium suites**  
   - Convert unused UI element handles (`inlineSection`, `evaluateSelect`, `replaySelect`, `resultPanel`) and constants to explicit assertions in Selenium tests.  
   - Execute the affected Selenium suites (`rest-api:test` with focused class filters).  
   - _2025-10-18 – Completed: Selenium tests now assert inline sections hide/show, preset dropdown content, TOTP result panels, and HOTP preset labels; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.*"` finished green._

6. **I6 – Quality gate**  
   - Run `./gradlew spotlessApply check` to confirm a clean build.  
   - Update knowledge artefacts if additional follow-ups emerge.  
   - _2025-10-18 – Completed: full `spotlessApply check` now passes after topping up TOTP REST coverage (`:core:test`, `:application:test`, `:cli:test`, `:rest-api:test`, and the aggregated jacoco verification all green`)._

## Dependencies
- Application, core, CLI, and REST modules share telemetry and verification helpers; assertions must respect existing contracts.
- Selenium tests rely on deterministic UI IDs; ensure selectors remain stable when adding assertions.
- Gradle quality gate must pass to conclude the feature.

## Risks & Mitigations
- **Risk:** Adding assertions in tests could over-constrain behaviour if UI text changes.  
  **Mitigation:** Prefer presence/visibility checks or telemetry verification rather than brittle text equality when possible.
- **Risk:** Removing redundant assignments might inadvertently change null-handling semantics.  
  **Mitigation:** Re-run targeted tests and rely on existing `defaultInstant` helpers to validate optional behaviour.

## Validation
- Run targeted Gradle commands per increment and finish with the full `spotlessApply check`.
- Confirm IDE inspection no longer reports the original warnings.
