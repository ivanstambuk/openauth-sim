# Feature 026 – FIDO2/WebAuthn Attestation Support

_Status: Proposed_  
_Last updated: 2025-10-12_

## Overview
Extend the simulator so it can generate and verify WebAuthn authenticator attestations in addition to assertions. The feature will deliver a full-stack slice—core attestation helpers, application services, CLI flows, REST endpoints, and operator UI affordances—so operators can exercise registration-style ceremonies alongside the existing assertion evaluation tooling.

## Clarifications
- 2025-10-12 – Deliver the capability across the entire stack (core engine, application services, CLI, REST API, operator UI) in one feature slice to keep protocol parity. (User approved Option A.)
- 2025-10-12 – Support packed, FIDO-U2F, TPM, and Android Key attestation formats on day one. (User directive.)
- 2025-10-12 – Accept authenticator private keys as either JWK or PEM/PKCS#8 inputs when generating attestations, auto-detecting the format. (User approved Option A.)
- 2025-10-12 – Attestation generation and verification remain inline-only in the operator UI; stored credential persistence continues to rely on CLI/REST/Java APIs. (User approved Option B.)
- 2025-10-12 – Fixture coverage should combine W3C WebAuthn Level 3 attestation examples with the synthetic JSON bundle, mirroring the current assertion strategy. (User approved Option B.)
- 2025-10-12 – The operator UI Evaluate tab will expose a toggle between “Assertion” and “Attestation.” Replay will gain the same toggle so operators can verify either payload type. Stored assertion mode stays available; attestation remains inline-only.
- 2025-10-16 – Attestation verification must support both self-attested flows (no trust anchors provided) and full trust-path validation. Provide optional trust anchor bundles via CLI/REST parameters and an operator UI text area upload; plan for Metadata Service (MDS) ingestion to supply default anchors. Absence of anchors should fall back to self-signed acceptance with warnings.
- 2025-10-16 – Attestation certificate chains will be supplied by fixtures or operator-provided anchors; no additional subject/issuer authoring UI is required for this feature slice.
- 2025-10-16 – Telemetry should include attestation format, RP ID, authenticator AAGUID, certificate SHA-256 fingerprint, and outcome while redacting attestation statements, raw certificates, and private keys. (User selected Option B.)
- 2025-10-16 – Store attestation fixtures under a dedicated `docs/webauthn_attestation/` directory with per-format JSON files (e.g., `packed.json`, `tpm.json`) to keep datasets modular. (User selected Option B.)

## Scope
- Implement attestation generation/verification helpers in `core` covering the targeted formats and leveraging existing COSE/JWK utilities.
- Extend application-layer services to orchestrate attestation generation and replay, emitting sanitized telemetry while redacting key material.
- Update CLI commands with attestation generation/verification options, including format selection and validation feedback.
- Add REST endpoints for attestation generation (`/api/v1/webauthn/attest`) and replay (`/api/v1/webauthn/attest/replay`), plus OpenAPI documentation and tests.
- Introduce UI affordances (toggle, inline forms, result cards) enabling operators to generate and verify attestation payloads.
- Provide optional trust anchor validation with CLI/REST inputs and an operator UI text area upload, defaulting to self-attested acceptance when no anchors are supplied.
- Lay groundwork for WebAuthn Metadata Service (MDS) ingestion to hydrate trusted roots and metadata for deterministic fixtures.
- Provide deterministic fixture data (W3C + synthetic) and update existing loader/test infrastructure to consume the new datasets.

## Out of Scope
- Persistent storage of attestation payloads via MapDB (deferred until broader credential import/export support exists for non-assertion authenticators).
- Attestation formats beyond packed, FIDO-U2F, TPM, and Android Key.
- Attestation object signing using hardware tokens—scope is limited to deterministic software fixtures.

## Success Criteria
- Core attestation helpers validate and emit attestation objects for the supported formats, with unit tests covering happy-path and failure cases (invalid signature, RP mismatch, unsupported alg).
- Application services surface sanitized telemetry (`fido2.attest` and `fido2.attestReplay`) and reuse existing redaction policies.
- CLI and REST interfaces expose generation and replay commands/endpoints with comprehensive coverage (MockMvc, Picocli, documentation snapshots).
- Operator UI displays an attestation-specific form, toggles cleanly between assertion and attestation workflows, and renders result cards with copy/download affordances.
- Trust-anchor aware verification accepts optional anchor bundles (UI upload, CLI/REST parameter) and clearly reports when validation falls back to self-attested evaluation.
- Fixture loaders/tests ingest W3C and synthetic attestation data, ensuring multi-format coverage in both generation and replay flows.
- `./gradlew spotlessApply check` and targeted suites (`:core:test`, `:application:test`, `:cli:test`, `:rest-api:test`) remain green after each increment.

## Telemetry & Observability
- Introduce dedicated telemetry adapters (e.g., `fido2.attest`, `fido2.attestReplay`) capturing attestation format, RP ID, authenticator AAGUID, and verification outcome while redacting raw statements and keys.
- Ensure UI/CLI/REST surfaces expose telemetry IDs for operator correlation without leaking attestation statements.

## Dependencies & Considerations
- Builds on `WebAuthnAssertionGenerationApplicationService` infrastructure; cross-verify COSE/JWK utilities support the new attestation encodings.
- Ensure TPM and Android Key attestation verification handles certificate chains appropriately (likely synthetic chain fixtures).
- Reuse existing `.gitleaks.toml` allowances or extend them if new fixture files introduce additional high-entropy payloads.
