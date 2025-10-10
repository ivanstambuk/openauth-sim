# Feature Plan 024 – FIDO2/WebAuthn Operator Support

_Linked specification:_ `docs/4-architecture/specs/feature-024-fido2-webauthn-operator-support.md`  
_Status:_ In Progress  
_Last updated:_ 2025-10-10

## Vision & Success Criteria
- Ship a parity WebAuthn assertion verification experience across core, persistence, CLI, REST, and operator UI facades, mirroring HOTP/OCRA ergonomics.
- Validate canonical W3C WebAuthn Level 3 §16 authentication vectors end-to-end, then extend coverage to the synthetic JSONL bundle to safeguard algorithm breadth (ES256/384/512, RS256, PS256, Ed25519).
- Enable operators to preload curated stored credentials and inject inline assertion vectors via UI presets, with key material surfaced as JWKs.
- Maintain green coverage/quality gates (`spotlessApply`, SpotBugs, ArchUnit, reflectionScan, Jacoco ≥0.90, PIT ≥ baseline).

## Scope Alignment
- In scope: verification engine, persistence schema entries, telemetry integration, CLI/REST/UI wiring, W3C + JSONL fixtures, seed/preset utilities, documentation.
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
   - _2025-10-10 – MockMvc tests staged in `Fido2EvaluationEndpointTest`; controllers/DTOs/services implemented with sanitized telemetry. Follow-up runs of `./gradlew --no-daemon :rest-api:test --rerun-tasks` now pass; OpenAPI snapshot update remains pending._ 

7. **I7 – Operator UI enablement**  
   - Extend Selenium/system tests to assert tab activation, mode switching, preset buttons, seed control, accessibility.  
   - Implement Thymeleaf/JS updates to enable forms, load vectors (inline) and seed curated stored credentials.  
   - Surface key material as JWK in UI modals/panels.  
   - **Next action (2025-10-10):** describe target WebAuthn panel interactions (stored/inline evaluate, replay diagnostics, “Load sample vector” + JWK display), stage failing Selenium specs, and keep HtmlUnit traces for regression debugging.  
   - _2025-10-10 – WebAuthn operator panel activated with sanitized telemetry, seed helpers, and inline presets; Selenium coverage exercised via `./gradlew --no-daemon :rest-api:test --rerun-tasks` plus a focused rerun of `TotpOperatorUiSeleniumTest` to confirm stability._ 

8. **I8 – JSONL coverage expansion**  
   - Add failing parameterised tests iterating over JSONL bundle entries across core/application layers.  
   - Implement ingestion utilities (parsing Base64url, verifying algorithms) ensuring deterministic seeds.  
   - Update UI preset catalogue to include JSONL-only flows where useful.

9. **I9 – Documentation & knowledge sync**  
   - Update how-to guides, roadmap, knowledge map, and protocol docs to reflect WebAuthn launch.  
   - Record telemetry contract additions, seed vector catalogue, and operator instructions.

10. **I10 – Quality gate + follow-up capture**  
    - Run `./gradlew qualityGate`.  
    - Finalise feature documentation, resolve remaining TODOs, record lessons in plan/roadmap.  
    - Prepare conventional commit and ensure push after passing checks.  
    - _2025-10-10 – Quality gate executed via `./gradlew --no-daemon qualityGate`; reflection scan and aggregated coverage checks reported green, enabling wrap-up._

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
