# Feature Plan 028 – IDE Warning Remediation

_Linked specification:_ `docs/4-architecture/features/028/spec.md`  
_Linked tasks:_ `docs/4-architecture/features/028/tasks.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
Eliminate the 2025-10-18 IDE diagnostics without altering runtime behaviour by converting unused locals into assertions,
removing redundant assignments, and refreshing documentation/telemetry notes. Success requires:
- FR-028-01 – Governance artefacts highlight the remediation scope and decisions.
- FR-028-02 – Application/core modules (TOTP, WebAuthn, HOTP) run warning-free with stronger assertions.
- FR-028-03 – CLI/REST/Selenium suites assert the values previously held in unused locals.
- FR-028-04 – Toolchain updates (SpotBugs annotations, DTO extraction, transient fields, spotless) run to green with no
  IDE warnings remaining.

## Scope Alignment
- **In scope:** Application/core/REST/CLI/Selenium tests touched by the IDE report, documentation/roadmap updates,
  SpotBugs annotation export, DTO extraction, transient REST exception fields, regression gate reruns.
- **Out of scope:** New behaviour, schema migrations, lint/tool upgrades beyond the annotation export, or unrelated UI/CLI
  refactors.

## Dependencies & Interfaces
- `CredentialStoreFactory`, WebAuthn replay/generation services, TOTP evaluation/replay commands, Totp CLI commands.
- Selenium suites for operator UI; tests rely on deterministic selectors.
- Gradle quality gate + dependency lockfiles used when exporting SpotBugs annotations.

## Assumptions & Risks
- **Assumptions:** Existing tests cover all branches touched by new assertions; operators can still rely on unchanged
  telemetry outputs.
- **Risks & Mitigations:**
  - Over-constraining Selenium/UI assertions → prefer visibility/attribute checks over brittle text equality.
  - Nullability regressions when removing redundant assignments → re-run targeted module tests immediately after edits.
  - Dependency lock churn from `spotbugs-annotations` export → stage narrow `--write-locks` commands per module.

## Implementation Drift Gate
- Evidence captured 2025-10-30:
  - IDE inspection screenshots indicating zero warnings across application/core/cli/rest-api/ui modules.
  - Gradle log: `./gradlew --no-daemon :application:test :core:test :cli:test :rest-api:test :ui:test spotlessApply check`.
  - Documentation diffs for roadmap, knowledge map, and `docs/_current-session.md`.
  - Notes on DTO extraction + transient REST exception fields.
- Gate remains satisfied; rerun if future warnings emerge.

## Increment Map
1. **I1 – Governance & documentation sync (S-028-01)**
   - Create spec/plan/tasks entries, update roadmap/current-session, resolve open question with Option B.
   - Commands: docs edits + `./gradlew --no-daemon spotlessApply check`.
   - Status: Completed 2025-10-18.
2. **I2 – Application TOTP constructors (S-028-02)**
   - Remove redundant `evaluationInstant` assignments; rerun TOTP tests.
   - Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.totp.*"`.
   - Status: Completed 2025-10-18.
3. **I3 – Core/WebAuthn services assertions (S-028-02)**
   - Assert decoded client data, consume metadata locals, drop suppressions.
   - Commands: `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAttestationVerifierTest"`,
     `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnPublicKeyDecoderTest"`,
     `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2ReplayEndpointTest"`.
   - Status: Completed 2025-10-18.
4. **I4 – CLI & REST unit tests (S-028-03)**
   - Update `TotpCliTest`, `Fido2AttestationEndpointTest` to leverage unused locals.
   - Commands: `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.TotpCliTest"`,
     `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`.
   - Status: Completed 2025-10-18.
5. **I5 – Selenium assertions (S-028-03)**
   - Convert unused variables in attestation Selenium suites into visibility/assertion checks.
   - Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.*"`.
   - Status: Completed 2025-10-18.
6. **I6 – DTO extraction & SpotBugs annotation export (S-028-04)**
   - Move `WebAuthnAssertionResponse` to its own file; promote `spotbugs-annotations` to `compileOnlyApi`; refresh
     dependency locks; rerun compile tasks.
   - Commands: targeted `./gradlew --no-daemon --write-locks …` invocations listed in tasks plus
     `./gradlew --no-daemon :application:compileJava` / `:rest-api:compileJava`.
   - Status: Completed 2025-10-19.
7. **I7 – REST exception serialization warnings (S-028-03/S-028-04)**
   - Mark `details`/`metadata` maps as `transient`; rerun compile/tests.
   - Commands: `./gradlew --no-daemon :rest-api:compileJava` (+ optional `:rest-api:test`).
   - Status: Completed 2025-10-19.
8. **I8 – WebAuthn assertion lossy conversion fix (S-028-02)**
   - Update `WebAuthnAssertionVerifierTest` to avoid implicit int-to-byte conversions.
   - Commands: `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAssertionVerifierTest"`.
   - Status: Completed 2025-10-19.
9. **I9 – WebAuthn replay/HOTP telemetry diagnostics (S-028-02/S-028-03)**
   - Remove unused telemetry locals, guard trace helpers, add HOTP counter fallbacks, assert PS256 Selenium fixture.
   - Commands: `./gradlew --no-daemon :application:test`, Selenium suites, `./gradlew --no-daemon spotlessApply check`.
   - Status: Completed 2025-10-29.
10. **I10 – Regression/IDE verification (S-028-04)**
    - Run the full Gradle gate and re-check IDE inspections for zero warnings.
    - Commands: `./gradlew --no-daemon :application:test :core:test :cli:test :rest-api:test :ui:test spotlessApply check`.
    - Status: Completed 2025-10-30.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-028-01 | I1 / T-028-01 | Governance + documentation alignment. |
| S-028-02 | I2, I3, I8, I9 / T-028-02/03/07/10/11 | Application/core WebAuthn & TOTP updates. |
| S-028-03 | I4, I5, I7, I9 / T-028-04/05/09/11 | CLI/REST/Selenium assertions + transient exceptions. |
| S-028-04 | I6, I10 / T-028-06/08 | Toolchain, SpotBugs export, regression gate. |

## Analysis Gate
- Completed 2025-10-18 when Option B remediation approach was approved and the warning inventory documented.
- No re-run needed unless new IDE diagnostics appear.

## Exit Criteria
- FR-028-01…FR-028-04 satisfied with test/documentation evidence.
- IDE inspection snapshot shows zero outstanding warnings.
- Final Gradle gate (`:application:test :core:test :cli:test :rest-api:test :ui:test spotlessApply check`) recorded in
  tasks/plan log.
- docs/_current-session.md and roadmap reference the completed remediation.

## Follow-ups / Backlog
- None; future IDE warning sweeps should be tracked under new features once diagnostics surface.
