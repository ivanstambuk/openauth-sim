# Feature 004 Tasks – FIDO2/WebAuthn Assertions & Attestations

_Status:_ Complete  
_Last updated:_ 2025-11-13

> Keep this checklist aligned with the feature plan increments. Stage tests before implementation, record verification commands beside each task, and prefer bite-sized entries (≤90 minutes).
> When referencing requirements, keep feature IDs (`F-`), non-goal IDs (`N-`), and scenario IDs (`S-<NNN>-`) inside the same parentheses immediately after the task title (omit categories that do not apply).

## Checklist
- [x] **T-004-01 – Stage core assertion evaluation & replay tests (FR-004-01, FR-004-02, S-004-01).**  
  _Intent:_ Capture W3C + synthetic assertion vectors into fixtures, stage failing `WebAuthnAssertionVerifier` and `CredentialStore` tests so evaluation/replay behaviours are guarded before wiring CLI/REST facades.  
  _Verification commands:_  
  - `./gradlew --no-daemon :core:test --tests "*WebAuthnAssertionVerifierTest"`  
  - `./gradlew --no-daemon :infra-persistence:test --tests "*CredentialStore*WebAuthn*"`  
  _Notes:_ Fixtures live under [docs/webauthn_w3c_vectors.json](docs/webauthn_w3c_vectors.json) / [docs/webauthn_assertion_vectors.json](docs/webauthn_assertion_vectors.json); coverage already exists via `WebAuthnAssertionVerifierTest` / `WebAuthnJsonVectorVerificationTest` & `WebAuthnCredentialStoreAttestationTest`.

- [x] **T-004-02 – Implement CLI/REST assertion evaluation + replay (FR-004-01, FR-004-02, S-004-02).**  
  _Intent:_ Wire `maintenance fido2 evaluate/replay`, `/api/v1/fido2/evaluate`, `/api/v1/fido2/replay`, and the credential catalogue endpoint to the verifier services and telemetry adapters.  
  _Verification commands:_  
  - `./gradlew --no-daemon :cli:test --tests "*Fido2Cli*Evaluate*"`  
  - `./gradlew --no-daemon :cli:test --tests "*Fido2Cli*Replay*"`  
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2*EndpointTest"`  
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`  
  _Notes:_ CLI commands (`Fido2Cli#Evaluate*`, `#Replay*`) and REST controllers (`WebAuthnEvaluationController`, `WebAuthnReplayController`) already emit `fido2.evaluate`/`fido2.replay` telemetry with green MockMvc + Selenium suites.

- [x] **T-004-03 – Refresh operator console evaluation/replay panels (S-004-01, S-004-05).**  
  _Intent:_ Ensure the UI toggles between Stored/Inline, reuses `secret-fields`, surfaces verification evidence, and keeps reason codes consistent with REST/CLI.  
  _Verification commands:_  
  - `node --test [rest-api/src/test/javascript/emv/console.test.js](rest-api/src/test/javascript/emv/console.test.js)`  
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.*Evaluation*"`  
  _Notes:_ Operator console fragment (`templates/ui/fido2/panel.html`) plus `static/ui/fido2/console.js` already expose the toggles; Selenium + Node suites cover evaluation/replay flows.

- [x] **T-004-04 – Stage attestation generator/replay tests (FR-004-03, S-004-03).**  
  _Intent:_ Introduce failing tests covering packed/FIDO-U2F/TPM/Android Key attestation outputs, trust-anchor parsing, and metadata coverage in the generator/service layers.  
  _Verification commands:_  
  - `./gradlew --no-daemon :core:test --tests "*WebAuthnAttestationGeneratorTest"`  
  - `./gradlew --no-daemon :application:test --tests "*WebAuthnAttestation*ServiceTest"`  
  _Notes:_ Tests (`WebAuthnAttestationGeneratorTest`, `WebAuthnAttestationGenerationStoredTest`) already execute against ``docs/webauthn_attestation`/*` fixtures.

- [x] **T-004-05 – Implement CLI/REST attestation commands + trust-anchor metadata (FR-004-03, FR-004-04, S-004-04).**  
  _Intent:_ Wire `maintenance fido2 attest`, `attest-replay`, `seed-attestations`, `/api/v1/webauthn/attest`, `/api/v1/webauthn/attest/replay`, `/api/v1/webauthn/attestations/seed`, `/api/v1/webauthn/attestations/{id}` with the attestation services and telemetry.  
  _Verification commands:_  
  - `./gradlew --no-daemon :cli:test --tests "*Fido2CliAttestation*"`  
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`  
  - `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`  
  _Notes:_ Picocli attestation commands plus REST controllers (`WebAuthnAttestationController`, `WebAuthnAttestationStoredController`) are already emitting `fido2.attest`/`fido2.attestReplay` telemetry with metadata + OpenAPI coverage.

- [x] **T-004-06 – Update UI attestation panels & metadata cards (S-004-03, S-004-04, S-004-05).**  
  _Intent:_ Add the attestation generation panel (format selector, trust-anchor upload, challenge control), the attestation replay summary, and the `trustAnchorSummaries` rendering plus warnings.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.*Attestation*"`  
  - `node --test [rest-api/src/test/javascript/emv/console.test.js](rest-api/src/test/javascript/emv/console.test.js)`  
  _Notes:_ Attestation UI is live in `templates/ui/fido2/panel.html` + `static/ui/fido2/console.js`; Selenium tests assert metadata cards and `trustAnchorSummaries`.

- [x] **T-004-07 – Document & log the Feature 004 consolidation (I3).**  
  _Intent:_ Refresh [docs/4-architecture/knowledge-map.md](docs/4-architecture/knowledge-map.md) and `_current-session.md` with the merged scope, verification commands, and hook guard output; ensure the feature plan/spec/tasks updates match the Consolidation Goal.  
  _Verification commands:_  
- `git config core.hooksPath` (log output in `_current-session.md`)  
  - `./gradlew --no-daemon spotlessApply check`  
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`  
  _Notes:_ Hook guard + verification commands (spotless, REST WebAuthn suites, Selenium, EMV Node harness, `:ui:test`) are logged in `_current-session.md`; knowledge map and plan now call out the consolidated Feature 004 ownership.

- [x] T-004-08 – Design FIDO2/WebAuthn Native Java API seams (FR-014-01/02, S-014-02).  
  _Intent:_ Designate `io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService` (and its `EvaluationCommand` / `EvaluationResult` types) as the Native Java API seam for WebAuthn assertion evaluation, document it in the Feature 004 Interface & Contract catalogue, and link it explicitly to Feature 014 and ADR-0007.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-004-09 – Author [docs/2-how-to/use-fido2-from-java.md](docs/2-how-to/use-fido2-from-java.md) and supporting tests (FR-014-03/04, S-014-01/02).  
  _Intent:_ Add a FIDO2/WebAuthn Native Java how-to guide and tests that treat `WebAuthnEvaluationApplicationService` as the façade seam for assertion evaluation, aligning docs and behaviour with Feature 014 and ADR-0007.  
  _Verification commands:_  
  - `./gradlew --no-daemon :core:test :application:test`  
  - `./gradlew --no-daemon spotlessApply check`

## Verification Log
- 2025-11-11 – `./gradlew --no-daemon spotlessApply check` (baseline).  
- 2025-11-11 – `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.*WebAuthn*"` plus UI/Selenium suites.  
- 2025-11-11 – `node --test [rest-api/src/test/javascript/emv/console.test.js](rest-api/src/test/javascript/emv/console.test.js)`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, and `./gradlew --no-daemon :ui:test`.
- 2025-11-13 – `git config core.hooksPath` (hooks confirmed as `githooks`), `./gradlew --no-daemon spotlessApply check`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.*WebAuthn*"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`, `node --test [rest-api/src/test/javascript/emv/console.test.js](rest-api/src/test/javascript/emv/console.test.js)`, and `./gradlew --no-daemon :ui:test` to capture the Feature 004 consolidation runs.

## Notes / TODOs
- Document any remaining drift in [docs/5-operations/analysis-gate-checklist.md](docs/5-operations/analysis-gate-checklist.md).  
- Future work (trust-anchor catalog enhancements, registration flows) should reference this feature’s fixture/telemetry contracts.
