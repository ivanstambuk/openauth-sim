# Feature Plan 026 – FIDO2/WebAuthn Attestation Support

_Linked specification:_ `docs/4-architecture/specs/feature-026-fido2-attestation-support.md`  
_Status:_ Proposed  
_Last updated:_ 2025-10-16

## Vision & Success Criteria
- Provide end-to-end attestation generation and verification across core, application services, CLI, REST API, and operator UI, mirroring the existing assertion workflow.
- Cover the packed, FIDO-U2F, TPM, and Android Key attestation formats with deterministic fixtures (W3C Level 3 + synthetic bundle).
- Maintain sanitized telemetry (`fido2.attest`, `fido2.attestReplay`) and redaction rules equivalent to assertion flows.
- Keep attestation support inline-only in the operator UI while preserving stored assertion generation.
- Allow operators to supply optional trust anchor bundles (UI text area, CLI/REST inputs) while defaulting to self-attested acceptance and laying groundwork for WebAuthn MDS ingestion.
- Ensure `./gradlew spotlessApply check` plus targeted suites (`:core:test`, `:application:test`, `:cli:test`, `:rest-api:test`) remain green after each increment.

## Scope Alignment
- In scope: attestation generation & replay helpers, telemetry, CLI/REST endpoints, UI toggles/forms, fixture ingestion, trust-anchor configuration (inline uploads), documentation updates, and initial MDS ingestion scaffolding.
- Out of scope: MapDB persistence of attestation payloads, attestation formats beyond the four specified types, hardware-backed certificate chain validation beyond deterministic fixtures.

## Dependencies & Interfaces
- Builds on existing FIDO2 assertion utilities (COSE parsing, JWK conversion).
- Stores attestation fixtures under `docs/webauthn_attestation/` with per-format JSON files while reusing shared loader patterns.
- Operator UI adjustments hinge on the current Evaluate/Replay tab layout; ensure toggle logic integrates with existing state management.
- Telemetry contracts must extend `TelemetryContracts` without breaking existing assertion events.
- Trust anchor handling must accept inline PEM bundles and prepare for optional MDS-sourced metadata without introducing external network calls.

## Increment Breakdown (≤10 min each)
1. **I1 – Fixture + test scaffolding**  
   - Convert targeted W3C and synthetic attestation vectors into per-format JSON fixtures under `docs/webauthn_attestation/` (`packed.json`, `fido-u2f.json`, `tpm.json`, `android-key.json`).  
   - Add failing core tests for attestation generation/verification covering the four formats (happy path + invalid cases) using `WebAuthnAttestationFixtures`.  
   - Update spec clarifications if additional vector gaps emerge.  
   - Commands: `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAttestationVerifierTest*"` (expected red).

2. **I2 – Core attestation engine**  
   - Implement attestation generation/verification helpers in `core`, satisfying I1 tests.  
   - Ensure format-specific validation (certificate parsing, nonce/hash checks) and error enums.  
   - Add failure-branch tests for bad signatures, unsupported formats, and certificate mismatches.

3. **I3 – Application services & telemetry**  
   - Stage failing application-level tests for attestation generation/replay telemetry, trust-anchor enforcement (anchors supplied vs absent), and self-attested warnings.  
   - Implement services emitting sanitized `fido2.attest` / `fido2.attestReplay` signals.  
   - Verify inline-only flows, trust-anchor fallback warnings, and error mapping.  
   - Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestation*"` (expected red → green).

4. **I4 – CLI façade**  
   - Add failing Picocli tests for `fido2 attest` (generate) and `fido2 attest replay` commands, including trust-anchor arguments.  
   - Wire CLI commands to the new application services, ensuring format selection, trust-anchor usage, and telemetry output.  
   - Commands: `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.fido2.Fido2CliAttestationTest"` (expected red → green).

5. **I5 – REST endpoints**  
   - Add failing MockMvc + OpenAPI tests for `/api/v1/webauthn/attest` and `/api/v1/webauthn/attest/replay`, including optional trust-anchor payloads and self-attested warnings.  
   - Implement controllers/DTOs with validation, format selection, trust-anchor handling, and sanitized responses.  
   - Regenerate OpenAPI snapshots.  
   - Commands: `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.WebAuthnAttestation*"` once implementation lands.

6. **I6 – Operator Evaluate toggle**  
   - Add failing Selenium tests asserting the Evaluate tab exposes an Assertion/Attestation toggle, renders attestation-specific inputs/result card, and surfaces an optional trust-anchor text area with validation.  
   - Implement toggle, attestation form, trust-anchor upload field (with persistence to local storage), and attestation result display (copy/download).  
   - Confirm assertion workflows remain unaffected.  
   - Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationEvaluate*"` (expected red → green).

7. **I7 – Operator Replay toggle**  
   - Add failing Selenium tests for attestation replay verification (inline-only) including payload validation feedback and trust-anchor enforcement messaging.  
   - Implement UI/JS wiring for attestation verification, telemetry display, trust-anchor reuse, and error handling.  
   - Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplay*"` (expected red → green).

8. **I8 – Fixture ingestion & docs**  
   - Extend fixture loaders to ingest attestation datasets (W3C + synthetic) across core/app/CLI/REST tests.  
   - Document the new workflow in how-to guides, update knowledge map/roadmap as needed.  
   - Run `./gradlew --no-daemon spotlessApply check` and targeted suites to close the feature.

9. **I9 – Trust anchor ingestion helpers**  
   - Add failing service/CLI/REST/UI tests ensuring PEM trust-anchor bundles are parsed, cached per session, and applied during verification (including error reporting).  
   - Implement shared parsing utilities, persistence hooks (in-memory only), and warning messages when anchors are absent or invalid.  
  - Update telemetry to flag whether anchors were provided.  
   - Commands: targeted module tests (`:application:test`, `:cli:test`, `:rest-api:test`, Selenium suites).

10. **I10 – MDS scaffolding**  
    - Stage failing tests for a metadata ingestion helper that loads offline MDS JSON blobs from `docs/webauthn_attestation/mds/`.  
    - Implement loader utilities and integrate with trust-anchor storage, ensuring deterministic fixtures.  
    - Document manual MDS refresh procedures and record limitations (no live network fetch).

## Risks & Mitigations
- **Complex format coverage** – TPM/Android Key attestations involve certificate handling; rely on deterministic fixtures and synthetic certificate chains.  
- **UI complexity** – Toggle must not regress assertion flows; isolate attestation form logic to maintain readability.  
- **Fixture entropy** – Large attestation blobs may trigger gitleaks; reuse or extend the existing allowlist with documented rationale.

## Telemetry & Observability
- Add `fido2.attest` and `fido2.attestReplay` adapters capturing attestation format, RP ID, authenticator AAGUID, certificate SHA-256 fingerprint, anchor source (self-attested vs provided), and validity status.  
- Ensure CLI/REST/UI outputs surface telemetry IDs and anchor provenance without exposing raw attestation statements, certificates, or private keys.
