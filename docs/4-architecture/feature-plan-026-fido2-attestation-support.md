# Feature Plan 026 – FIDO2/WebAuthn Attestation Support

_Linked specification:_ `docs/4-architecture/specs/feature-026-fido2-attestation-support.md`  
_Status:_ Proposed  
_Last updated:_ 2025-10-12

## Vision & Success Criteria
- Provide end-to-end attestation generation and verification across core, application services, CLI, REST API, and operator UI, mirroring the existing assertion workflow.
- Cover the packed, FIDO-U2F, TPM, and Android Key attestation formats with deterministic fixtures (W3C Level 3 + synthetic bundle).
- Maintain sanitized telemetry (`fido2.attest`, `fido2.attestReplay`) and redaction rules equivalent to assertion flows.
- Keep attestation support inline-only in the operator UI while preserving stored assertion generation.
- Ensure `./gradlew spotlessApply check` plus targeted suites (`:core:test`, `:application:test`, `:cli:test`, `:rest-api:test`) remain green after each increment.

## Scope Alignment
- In scope: attestation generation & replay helpers, telemetry, CLI/REST endpoints, UI toggles/forms, fixture ingestion, documentation updates.
- Out of scope: MapDB persistence of attestation payloads, attestation formats beyond the four specified types, hardware-backed certificate chain validation beyond deterministic fixtures.

## Dependencies & Interfaces
- Builds on existing FIDO2 assertion utilities (COSE parsing, JWK conversion).
- Requires fixture updates in `docs/webauthn_w3c_vectors.json` and `docs/webauthn_assertion_vectors.json` (or new companion files).
- Operator UI adjustments hinge on the current Evaluate/Replay tab layout; ensure toggle logic integrates with existing state management.
- Telemetry contracts must extend `TelemetryContracts` without breaking existing assertion events.

## Increment Breakdown (≤10 min each)
1. **I1 – Fixture + test scaffolding**  
   - Convert targeted W3C and synthetic attestation vectors into JSON fixtures.  
   - Add failing core tests for attestation generation/verification covering packed, FIDO-U2F, TPM, Android Key (happy path + invalid cases).  
   - Update spec clarifications if additional vector gaps emerge.

2. **I2 – Core attestation engine**  
   - Implement attestation generation/verification helpers in `core`, satisfying I1 tests.  
   - Ensure format-specific validation (certificate parsing, nonce/hash checks) and error enums.  
   - Add failure-branch tests for bad signatures, unsupported formats, and certificate mismatches.

3. **I3 – Application services & telemetry**  
   - Stage failing application-level tests for attestation generation/replay telemetry.  
   - Implement services emitting sanitized `fido2.attest` / `fido2.attestReplay` signals.  
   - Verify inline-only flows and error mapping.

4. **I4 – CLI façade**  
   - Add failing Picocli tests for `fido2 attest` (generate) and `fido2 attest replay` commands.  
   - Wire CLI commands to the new application services, ensuring format selection and telemetry output.

5. **I5 – REST endpoints**  
   - Add failing MockMvc + OpenAPI tests for `/api/v1/webauthn/attest` and `/api/v1/webauthn/attest/replay`.  
   - Implement controllers/DTOs with validation, format selection, and sanitized responses.  
   - Regenerate OpenAPI snapshots.

6. **I6 – Operator Evaluate toggle**  
   - Add failing Selenium tests asserting the Evaluate tab exposes an Assertion/Attestation toggle and renders attestation-specific inputs/result card.  
   - Implement toggle, attestation form, and attestation result display (copy/download).  
   - Confirm assertion workflows remain unaffected.

7. **I7 – Operator Replay toggle**  
   - Add failing Selenium tests for attestation replay verification (inline-only) including payload validation feedback.  
   - Implement UI/JS wiring for attestation verification, telemetry display, and error handling.

8. **I8 – Fixture ingestion & docs**  
   - Extend fixture loaders to ingest attestation datasets (W3C + synthetic) across core/app/CLI/REST tests.  
   - Document the new workflow in how-to guides, update knowledge map/roadmap as needed.  
   - Run `./gradlew spotlessApply check` and targeted suites to close the feature.

## Risks & Mitigations
- **Complex format coverage** – TPM/Android Key attestations involve certificate handling; rely on deterministic fixtures and synthetic certificate chains.  
- **UI complexity** – Toggle must not regress assertion flows; isolate attestation form logic to maintain readability.  
- **Fixture entropy** – Large attestation blobs may trigger gitleaks; reuse or extend the existing allowlist with documented rationale.

## Telemetry & Observability
- Add `fido2.attest` and `fido2.attestReplay` adapters capturing attestation format, RP ID, authenticator AAGUID, and validity status.  
- Ensure CLI/REST/UI outputs surface telemetry IDs without exposing raw attestation statements or certificates.
