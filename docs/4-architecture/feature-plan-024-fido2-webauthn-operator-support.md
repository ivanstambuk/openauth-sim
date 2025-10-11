# Feature Plan 024 – FIDO2/WebAuthn Operator Support

_Linked specification:_ `docs/4-architecture/specs/feature-024-fido2-webauthn-operator-support.md`  
_Status:_ In Progress  
_Last updated:_ 2025-10-11

## Vision & Success Criteria
- Ship a parity WebAuthn assertion generation + verification experience across core, persistence, CLI, REST, and operator UI facades, mirroring HOTP/OCRA ergonomics (authenticator-style generation on Evaluate, verification on Replay).
- Validate canonical W3C WebAuthn Level 3 §16 authentication vectors end-to-end, then extend coverage to the synthetic JSONL bundle to safeguard algorithm breadth (ES256/384/512, RS256, PS256, Ed25519).
- Enable operators to preload curated stored credentials and inject inline assertion vectors via UI presets, with key material surfaced as JWKs.
- Maintain green coverage/quality gates (`spotlessApply`, SpotBugs, ArchUnit, reflectionScan, Jacoco ≥0.90, PIT ≥ baseline).

## Scope Alignment
- In scope: verification engine, persistence schema entries, telemetry integration, CLI/REST/UI wiring, W3C + JSONL fixtures, seed/preset utilities, documentation, and a WebAuthn authenticator simulator that generates assertions from private-key inputs.
- Out of scope: registration/attestation ceremonies, authenticator emulation, dependency upgrades, UI issuance flows.

## Dependencies & Interfaces
- Reuse existing MapDB schema v1 (Feature 002) via `CredentialStoreFactory`.
- Telemetry events must flow through `TelemetryContracts` (`application` module).
- Operator console tab already present from Feature 020; this plan activates the FIDO2/WebAuthn content and REST wiring.
- JSON bundle located at `docs/webauthn_assertion_vectors.json`; W3C vector conversions to be stored under `docs/webauthn_w3c_vectors/` (new).
- JSON bundle stores high-entropy payloads as 16-character base64url segments to satisfy gitleaks; ingest utilities must join segments before verification.

## Increment Breakdown (≤10 min each)
Each increment stages failing tests first, drives implementation to green, and runs `./gradlew spotlessApply check`.

1. **I1 – Fixture bootstrap (tests only)**  
   - Convert targeted W3C §16 authentication vectors to Base64url fixtures under `docs/webauthn_w3c_vectors/`.  
   - Add failing core tests asserting successful verification for ES256 baseline and expected failures (RP ID mismatch, bad signature).  
   - Analysis gate: update spec clarifications with vector references.  
   _2025-10-09 – Authored `packed-es256.properties`, added `WebAuthnAssertionVerifierTest` plus fixture loader, and confirmed red state via `./gradlew :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAssertionVerifierTest"` (throws `UnsupportedOperationException` as verifier not yet implemented)._ 

2. **I2 – Verification engine skeleton**  
   - Implement minimal parser/validator in `core/fido2` package satisfying I1 tests.  
   - Cover flag parsing, RP ID hash, `clientDataJSON` validation, signature base assembly.  
   - Add failure branch tests (UV flag requirements, type/origin mismatch).  
   _2025-10-09 – Implemented `WebAuthnAssertionVerifier` with CBOR COSE parsing, client data validation, RP hash checks, and ECDSA signature verification. `./gradlew :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAssertionVerifierTest"` now passes alongside `./gradlew spotlessApply check`._

3. **I3 – Persistence descriptors**  
   - Extend MapDB schema v1 with FIDO2 credential metadata (algorithm, credential ID, public key, flags).  
   - Add failing integration tests mixing HOTP/TOTP/OCRA/FIDO2 records; implement to green.  
   - Ensure seeds use curated subset.

4. **I4 – Application services & telemetry**  
   - Stage failing application-level tests for stored/inline evaluation + replay diagnostics.  
   - Implement services returning rich diagnostics, emitting `fido2.evaluate`/`fido2.replay` telemetry with redacted payloads.  
   - **Next action (2025-10-10):** hydrate the evaluation service to resolve stored lookups through `WebAuthnCredentialPersistenceAdapter`, reuse the verifier for inline requests, and build sanitized telemetry maps that omit challenges/signatures before wiring replay delegation.

5. **I5 – CLI façade**  
   - Add failing Picocli integration tests for evaluate/replay commands (stored + inline).  
   - Implement commands, ensure help/validation parity with HOTP/OCRA.  
   - _2025-10-10 – Promoted shared WebAuthn fixtures into the core module, implemented `Fido2Cli` (stored/inline evaluate + replay), enabled optional `--user-verification-required[=<bool>]` parsing, and verified via `GRADLE_USER_HOME=$PWD/.gradle ./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliTest"`; follow-up `./gradlew --no-daemon :cli:test --rerun-tasks` confirms the suite remains green under the full-access sandbox._

6. **I6 – REST endpoints + OpenAPI**  
   - Introduce failing MockMvc tests for `/api/v1/webauthn/evaluate`, `/evaluate/inline`, `/replay`.  
   - Wire controllers, request/response DTOs, telemetry, OpenAPI updates.  
   - **Next action (2025-10-10):** capture MockMvc expectations mirroring CLI/application behaviour, stage OpenAPI snapshot adjustments, and run the refreshed suite under `./gradlew --no-daemon :rest-api:test`.  
   - _2025-10-10 – MockMvc tests staged in `Fido2EvaluationEndpointTest`; controllers/DTOs/services implemented with sanitized telemetry. Regenerated `docs/3-reference/rest-openapi.json|yaml` via `OPENAPI_SNAPSHOT_WRITE=true GRADLE_USER_HOME=$PWD/.gradle ./gradlew --no-daemon :rest-api:test --tests io.openauth.sim.rest.OpenApiSnapshotTest`, then re-ran `./gradlew --no-daemon :rest-api:test --rerun-tasks` to confirm the refreshed contract._ 

7. **I7 – Operator UI enablement**  
   - Extend Selenium/system tests to assert tab activation, mode switching, preset buttons, seed control, accessibility.  
   - Implement Thymeleaf/JS updates to enable forms, load vectors (inline) and seed curated stored credentials.  
   - Surface key material as JWK in UI modals/panels.  
   - 2025-10-10 – Confirmed with product that the JSON “Load sample vector” controls remain inside the inline forms on Evaluate and Replay tabs after we adopt the HOTP/TOTP/OCRA layout (Option A).  
   - 2025-10-10 – Realigned the FIDO2 panel with Evaluate/Replay tabs, enabled inline replay, refreshed REST wiring, and updated Selenium/OpenAPI snapshots.  
   - **Next action (2025-10-10):** describe target WebAuthn panel interactions (stored/inline evaluate, replay diagnostics, “Load sample vector” + JWK display), stage failing Selenium specs, and keep HtmlUnit traces for regression debugging.  
   - Introduce failing Selenium assertions that expect success/invalid statuses to surface after Evaluate/Replay submissions and verify sanitized payload details (no challenge/signature leakage).  
   - Implement fetch/XHR flows in `ui/fido2/console.js` that POST inline/stored evaluate and replay requests to the REST endpoints, update result panels with sanitized responses (status, reason code, telemetry ID), and surface validation errors.  
   - Add defensive error handling for network failures (display `status=error`, keep telemetry sanitized) and ensure the result cards mirror HOTP/TOTP semantics.  
  - _2025-10-10 – WebAuthn operator panel activated with sanitized telemetry, seed helpers, and inline presets; Selenium coverage exercised via `./gradlew --no-daemon :rest-api:test --rerun-tasks` plus a focused rerun of `TotpOperatorUiSeleniumTest` to confirm stability._ 
  - _2025-10-11 – Added Selenium assertions confirming inline preset dropdown option text matches the curated generator labels before re-running `:rest-api:test` and `spotlessApply check`._ 
   - 2025-10-10 – **Follow-up:** Refactor Evaluate tab into an authenticator simulator. Stage failing Selenium/UI tests asserting assertion-generation output (signed authenticator data, client data JSON, signature) and removal of verification-only telemetry from the Evaluate result card before implementing JS/HTML changes.
  - _2025-10-11 – Regression observed: “Reset to now” button fails to refresh the signature counter in any toggle state; fix implemented by snapshotting epoch seconds in `ui/fido2/console.js` and locking behaviour with Selenium coverage (Reset helper)._ 
  - _2025-10-11 – Addressed inline generator regressions by aligning telemetry to `key=value` strings, seeding generator presets for stored flows, and updating Selenium coverage; `./gradlew --no-daemon :rest-api:test` is green post-fix._
   - _2025-10-11 – Inline sample dropdowns now reuse the shared `.inline-preset` styling, matching the HOTP/TOTP/OCRA palette; Selenium helper hardened against stale selects and `./gradlew --no-daemon spotlessApply check` confirmed green._
   - _2025-10-11 – Removed inline “Credential name” inputs from Evaluate and Replay tabs; payloads now rely on server-side defaults while preserving sample metadata via telemetry._
   - _2025-10-11 – Converted the client-data-type entries to internal defaults (always `webauthn.get`), removed the UI inputs, and adjusted services to fall back automatically when callers omit the field._
   - _2025-10-11 – Dropped redundant “Load sample vector” buttons from inline Evaluate/Replay presets; dropdown change events now mirror HOTP/TOTP/OCRA behaviour._
   - _2025-10-11 – Identified regression where the host console defaulted the Evaluate tab back to stored mode; staged Selenium coverage and updated shared console scripts so inline remains the default even when protocol context restores prior state._
   - _2025-10-11 – Verified fix via `Fido2OperatorUiSeleniumTest.fido2EvaluateDefaultsToInline` after updating `ui/ocra/console.js` and `ui/fido2/console.js`; reran `./gradlew :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"` and full `spotlessApply check`._
   - _2025-10-11 – Hid the stored-only “Seed sample credentials” control whenever inline mode is active and added Selenium coverage (`Fido2OperatorUiSeleniumTest.seedControlHidesOutsideStoredMode`), exercised via `GRADLE_USER_HOME=$PWD/.gradle ./gradlew --no-daemon spotlessApply check`._
   - _2025-10-11 – Reduced inline Credential ID text areas to a single-line default height while retaining textarea controls so operators can expand as needed; Selenium coverage unchanged (visual-only tweak)._
   - **Next action (2025-10-11):** propagate the multi-algorithm generator presets into `Fido2OperatorSampleData`, update Thymeleaf/console JS preset selectors per algorithm, and stage documentation/test updates before rerunning Gradle quality gates.

8. **I8 – JSONL coverage expansion**  
   - Add failing parameterised tests iterating over JSONL bundle entries across core/application layers.  
   - Implement ingestion utilities (parsing Base64url, verifying algorithms) ensuring deterministic seeds.  
   - Update UI preset catalogue to include JSONL-only flows where useful.
   - _2025-10-10 – Added `WebAuthnJsonVectorVerificationTest` (core) and `WebAuthnJsonVectorEvaluationApplicationServiceTest` (application); extended `WebAuthnAssertionVerifier` to support ES384/ES512/RS256/PS256/EdDSA so both suites pass across all 42 JSON bundle vectors. CLI presets (`--vector-id`/`vectors`) and REST sample responses expose the full catalog for reproducibility, while the operator UI inline dropdown presents a curated subset to avoid clutter._ 

9. **I9 – Documentation & knowledge sync**  
   - Update how-to guides, roadmap, knowledge map, and protocol docs to reflect WebAuthn launch.  
   - Record telemetry contract additions, seed vector catalogue, and operator instructions.
   - _2025-10-10 – Authored new how-to guides for CLI, REST, and operator UI workflows (docs/2-how-to/use-fido2-*.md) and documented JWK/vector handling guidance._
   - _2025-10-11 – Refreshed REST/operator UI guides with per-algorithm generator preset tables, updated inline workflow descriptions, and synced the knowledge map to capture the multi-algorithm preset curation._

10. **I10 – Quality gate + follow-up capture**  
    - Run `./gradlew qualityGate`.  
    - Finalise feature documentation, resolve remaining TODOs, record lessons in plan/roadmap.  
    - Prepare conventional commit and ensure push after passing checks.  
    - _2025-10-10 – Quality gate executed via `./gradlew --no-daemon qualityGate`; reflection scan and aggregated coverage checks reported green, enabling wrap-up._

11. **I11 – Assertion generation UX**  
    - Update Evaluate tab request/response contract to accept relying party inputs, authenticator private key (auto-detect JWK or PEM/PKCS#8), and optional authenticator flags; generate signed assertions (authenticator data, client data JSON, signature) without invoking the verification pipeline.  
    - Add failing REST controller/application tests for assertion generation responses, including deterministic sample vectors and error cases for malformed JWK/PEM payloads.  
    - Update operator console JS/HTML to display generated payload blobs (with copy/download actions) and hide telemetry-style status text; ensure parsing errors surface inline.  
    - Adjust documentation/how-to guides to explain the new generation workflow, private-key formats, and updated Replay usage for verification.  
    - Initial implementation may emit ES256 assertions, but structure services/utilities to extend across RS256/PS256/ES384/ES512/EdDSA without UI rewrites.  
    - 2025-10-11 – Adopt the “Use current Unix seconds” default for the inline signature counter field, rendering the input read-only while the toggle remains checked and exposing a “Reset to now” helper to refresh the snapshot.
    - 2025-10-11 – Compact the inline and stored generator request forms by placing the relying party ID and origin inputs on a shared row with responsive wrapping for narrow viewports.
    - _2025-10-11 – Patched the REST generator flow so stored dropdowns enable correctly after seed refresh, mapped generator parse failures to `private_key_invalid`, and added `WebAuthnEvaluationServiceTest` to lock the behaviour. Selenium suite (`Fido2OperatorUiSeleniumTest`) passes alongside `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`.  `./gradlew --no-daemon spotlessApply check` now returns green._
    - 2025-10-10 – Confirmed: CLI `fido2 evaluate` commands will emit generated assertion payloads (no new `generate` verb), Evaluate result card presents a structured `PublicKeyCredential` JSON object with copy/download helpers, and operator presets provide ES256 JWK private keys only.  
    - 2025-10-11 – JSON vector bundle now carries `keyPairJwk` entries for every algorithm; expand `WebAuthnGeneratorSamples`/operator presets to surface curated RS256/PS256/ES384/ES512/Ed25519 keys alongside ES256 so multi-algorithm generators stay in sync.
    - 2025-10-11 – Added `.gitleaks.toml` allowlist so the higher-entropy JWK fixtures in `docs/webauthn_assertion_vectors.json` remain committed without tripping secret scans.

12. **I12 – Fixture hygiene & gitleaks alignment**  
    - Add `.gitleaks.toml` allowlisting the synthetic WebAuthn assertion bundles, documenting why the exemption is safe.  
    - Normalize JSON vector payloads to single-line base64 strings and update ingestion helpers to treat them as plain strings.  
    - Rename `publicKeyJwk` to `keyPairJwk` and enrich each entry with private-key parameters (`d`, RSA factors) so generator flows expose full JWK material without touching PEM files.  
    - Re-run `./gradlew spotlessApply check` to confirm formatting and tests remain green after the fixture change.  
    - _Status: Complete – 2025-10-11 – Allowlist added with documented rationale, fixtures flattened to single-line base64 strings, loader tightened to reject segmented arrays, full key-pair JWKs emitted, and `./gradlew --no-daemon spotlessApply check` verified the build stays green._

## Risks & Mitigations
- **Large vector set increases test time** → run JSONL suite in targeted tests (I8) with caching helpers; consider tagging for selective execution.  
- **Signature algorithm variance** → rely on deterministic fixtures; add explicit assertions for DER decoding vs raw Ed25519.  
- **UI seed overload** → curated subset for seed button; document advanced usage for manual imports.

## Telemetry & Observability
- Add `TelemetryContracts` entries: `fido2.evaluate` and `fido2.replay`, capturing algorithm, RP ID, UV flag, outcome.  
- Ensure CLI/REST/UI call the shared adapters; include unit tests verifying redaction.

## Intent & Tooling Notes
- Session decisions (2025-10-09) captured in spec clarifications and open-questions log (resolved).  
- Gradle wrapper configured with `GRADLE_USER_HOME=$PWD/.gradle` to avoid home-directory locks; CLI/REST/Application suites re-executed locally via `./gradlew --no-daemon :cli:test --rerun-tasks`, `:rest-api:test --rerun-tasks`, `:application:test --tests "io.openauth.sim.application.fido2.*"`, and `spotlessApply check`.  
- JSONL + W3C fixtures will be version-controlled to avoid external fetches.
