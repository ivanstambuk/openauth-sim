# Feature 028 Tasks – IDE Warning Remediation

_Linked plan:_ `docs/4-architecture/features/028/plan.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

> Checklist mirrors the new plan increments; each task recorded its verification commands and references the relevant FR/S IDs.

## Checklist
- [x] T-028-01 – Governance sync (FR-028-01, S-028-01).  
  _Intent:_ Create spec/plan/tasks, capture Option B clarification, update roadmap/knowledge map/current-session, and close the warning open question.  
  _Verification:_ `./gradlew --no-daemon spotlessApply check` (2025-10-18).

- [x] T-028-02 – Application constructors cleanup (FR-028-02, S-028-02).  
  _Intent:_ Remove redundant `evaluationInstant` assignments in TOTP evaluation/replay command records while keeping optional semantics.  
  _Verification:_ `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.totp.*"` (2025-10-18).

- [x] T-028-03 – Core/WebAuthn services assertions (FR-028-02, S-028-02).  
  _Intent:_ Strengthen `WebAuthnAttestationVerifier`, `WebAuthnReplayService`, and supporting tests; drop obsolete suppressions.  
  _Verification:_  
  - `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAttestationVerifierTest"`  
  - `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnPublicKeyDecoderTest"`  
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2ReplayEndpointTest"`

- [x] T-028-04 – CLI & REST unit tests (FR-028-03, S-028-03).  
  _Intent:_ Update `TotpCliTest` & `Fido2AttestationEndpointTest` to leverage previously unused locals.  
  _Verification:_  
  - `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.TotpCliTest"`  
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`

- [x] T-028-05 – Selenium assertions (FR-028-03, S-028-03).  
  _Intent:_ Convert unused Selenium locals/constants into explicit assertions across operator UI attestation suites.  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.*"` (2025-10-18).

- [x] T-028-06 – DTO extraction & SpotBugs annotation export (FR-028-04, S-028-04).  
  _Intent:_ Move `WebAuthnAssertionResponse` into its own file, promote `spotbugs-annotations` to `compileOnlyApi`, refresh dependency locks, and ensure downstream modules compile without warnings.  
  _Verification:_  
  - `./gradlew --no-daemon --write-locks :application:compileJava :application:compileTestJava :application:pmdTest`  
  - `./gradlew --no-daemon --write-locks :rest-api:compileJava :rest-api:compileTestJava :cli:compileJava :cli:compileTestJava :ui:compileJava :ui:compileTestJava`  
  - `./gradlew --no-daemon :application:compileJava` / `:rest-api:compileJava`  
  - `./gradlew --no-daemon --write-locks spotlessApply check` followed by a non-locking rerun (2025-10-19).

- [x] T-028-07 – REST exception serialization warnings (FR-028-03, S-028-03/S-028-04).  
  _Intent:_ Mark REST exception `details`/`metadata` maps as `transient` and verify compile/test passes.  
  _Verification:_ `./gradlew --no-daemon :rest-api:compileJava` (2025-10-19).

- [x] T-028-08 – WebAuthn assertion lossy conversion fix (FR-028-02, S-028-02).  
  _Intent:_ Update `WebAuthnAssertionVerifierTest` to avoid implicit int-to-byte conversions.  
  _Verification:_ `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAssertionVerifierTest"` (2025-10-19).

- [x] T-028-09 – CLI/REST Selenium telemetry polish (FR-028-03, S-028-03).  
  _Intent:_ Ensure Selenium fixtures assert seeded attestation metadata (PS256) and convert remaining unused locals.  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest*"` (2025-10-29).

- [x] T-028-10 – WebAuthn replay/HOTP telemetry diagnostics (FR-028-02/03, S-028-02/S-028-03).  
  _Intent:_ Drop the unused `telemetryCommand` local in replay services, guard trace helpers, and prevent HOTP matched counter auto-unboxing while asserting Selenium fixtures.  
  _Verification:_ `./gradlew --no-daemon :application:test`, Selenium suites, `./gradlew --no-daemon spotlessApply check` (2025-10-29).

- [x] T-028-11 – Regression & IDE verification (FR-028-04, S-028-04).  
  _Intent:_ Run the full Gradle gate and capture the zero-warning IDE snapshot.  
  _Verification:_ `./gradlew --no-daemon :application:test :core:test :cli:test :rest-api:test :ui:test spotlessApply check` (2025-10-30).

## Verification Log
- 2025-10-30 – `./gradlew --no-daemon :application:test :core:test :cli:test :rest-api:test :ui:test spotlessApply check`
- 2025-10-29 – `./gradlew --no-daemon spotlessApply check` (post-WebAuthn telemetry cleanup)
- 2025-10-19 – `./gradlew --no-daemon --write-locks spotlessApply check`
- 2025-10-18 – `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.totp.*"`
- 2025-11-10 – `./gradlew --no-daemon spotlessApply check` (template migration verification)

## Notes / TODOs
- IDE inspections dated 2025-10-30 confirm zero warnings across application/core/cli/rest-api/ui modules; no further action required.
