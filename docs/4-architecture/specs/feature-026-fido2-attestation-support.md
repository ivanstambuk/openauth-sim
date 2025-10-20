# Feature 026 – FIDO2/WebAuthn Attestation Support

_Status: In Progress_  
_Last updated: 2025-10-20_

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
- 2025-10-16 – Treat newly committed guidance in `AGENTS.md`, `docs/5-operations/runbook-session-reset.md`, and `docs/5-operations/session-quick-reference.md` as the authoritative baseline for this feature’s workflow.
- 2025-10-17 – Curate offline metadata from the FIDO MDS v3 production release; bundle only the selected entries required for validation (no full archive).
- 2025-10-17 – Apple does not currently publish FIDO2 authenticators in the MDS v3 production feed; curated datasets therefore omit Apple-specific entries.
- 2025-10-17 – REST/CLI/operator Evaluate flows must generate new attestation payloads (attestationObject, clientDataJSON, challenge) instead of validating existing submissions; attestation verification is handled exclusively by the Replay flows.
- 2025-10-17 – Attestation generation must support three modes: (a) self-signed certificates produced locally, (b) unsigned “no signature” payload emission when operators need structural fixtures, and (c) signing with operator-provided X.509 roots supplied inline or imported from disk.
- 2025-10-17 – Resolve lingering Checkstyle/PMD violations by updating offending source/tests to satisfy the current rule sets (Option A); rule configuration changes require separate approval.
- 2025-10-17 – Attestation Evaluate result cards should omit duplicated metadata (attestation ID / format / signing mode) and redundant status rows since the header badge already conveys success/error; payload panels should focus on generated artifacts.
- 2025-10-17 – Attestation result cards should likewise drop the expected challenge excerpt and telemetry hint; operators can reference the input summary and CLI/REST outputs for those fields.
- 2025-10-17 – Generated attestation payloads must omit `expectedChallenge`, raw certificate PEM chains, and `signatureIncluded` flags; expose only `clientDataJSON` and `attestationObject` within the nested response while surfacing signature/certificate statistics through telemetry metadata. (Owner selected Option B.)
- 2025-10-18 – The attestation result panel should mirror the assertion layout by removing the intermediate “response” subheading and rendering the JSON payload directly beneath the card header.
- 2025-10-19 – Generated assertion payloads returned by CLI/REST/UI must match the WebAuthn `PublicKeyCredential` contract (fields limited to `type`, `id`, `rawId`, `response`); retain relying-party metadata, algorithm labels, and verification hints in telemetry/metadata only.
- 2025-10-20 – Operator console attestation preset labels must follow the `<algorithm> (format, W3C <section>)` pattern used for stored assertions; if no W3C section is available, fall back to the vector’s declared origin metadata.
- 2025-10-17 – Hide the visible Attestation ID input; auto-populate a hidden `presetId` field from the selected preset. Provide an optional "Copy preset ID" affordance later if needed. (Approved.)
- 2025-10-18 – Remove `attestationId` from CLI/REST/UI attestation JSON payloads while keeping it available for internal telemetry, preset resolution, and trust-anchor lookup.
- 2025-10-18 – Update attestation private-key UI labels to match the supported formats (“JWK or PEM/PKCS#8”) and eliminate remaining Base64URL references for those inputs.
- 2025-10-17 – Add a Manual generation mode: when no preset is selected (or when overrides are applied), build the attestationObject/clientDataJSON from supplied inputs instead of reading from fixtures. Supported formats: packed, fido-u2f, tpm, android-key. (Proposed; see Open Questions.)
- 2025-10-18 – Manual credential IDs: default Manual runs generate a new random credential ID server-side (Base64URL). Provide an optional operator override accepting Base64URL; reject malformed overrides, but fall back to random generation when the field is blank/whitespace. (User approved Option A.)
- 2025-10-18 – Attestation result cards should surface certificate chains in a dedicated panel outside the generated JSON payload; remove the prior summary text (“Signature included”/“Certificate chain count”).
- 2025-10-17 – Preset with overrides: if the operator selects a preset but edits any of challenge, RP ID, origin, credential/attestation private key, or certificate serial, the generator must treat the request as Manual and favour the edited values. Telemetry should still record the original presetId as `seedPresetId` and list the `overrides` fields. (Approved in principle.)
- 2025-10-18 – Attestation private keys must accept the same formats as assertions (JWK or PEM/PKCS#8) and drop the Base64URL-only path; when seeding or returning attestation key material, expose JWK representations for parity (fixture JSON remains unchanged; decode during load). (User selected Option B.)
- 2025-10-18 – Present preset attestation private keys as pretty-printed multi-line JWK JSON to improve operator readability while keeping fixtures canonical. (User selected Option B.)
- 2025-10-19 – Stack the attestation credential and attestation private-key inputs vertically in the operator UI to match the assertion textarea layout and remove the horizontal scrollbar. (User confirmed.)
- 2025-10-20 – The REST attestation endpoint rejects unsupported `inputSource` values with `input_source_invalid` while treating a blank/null field as the PRESET default for backwards compatibility.
- 2025-10-20 – The operator UI auto-detects preset overrides, surfaces a Manual/Preset hint near Generate, and keeps manual submissions free of `attestationId` while still recording `seedPresetId`/`overrides` in telemetry.

## Scope
- Implement attestation generation/verification helpers in `core` covering the targeted formats and leveraging existing COSE/JWK utilities.
- Extend generation to support two input sources:
  - PRESET: existing flow (uses fixture for attestationObject/clientData, validates inputs).
  - MANUAL: new flow (constructs attestationObject/clientData from request inputs; no preset required).
- Extend application-layer services to orchestrate attestation generation and replay, emitting sanitized telemetry while redacting key material.
- Update CLI commands with attestation generation/verification options, including format selection and validation feedback.
- Add REST endpoints for attestation generation (`/api/v1/webauthn/attest`) and replay (`/api/v1/webauthn/attest/replay`), plus OpenAPI documentation and tests.
- Introduce UI affordances (toggle, inline forms, result cards) enabling operators to generate and verify attestation payloads.
- Provide optional trust anchor validation with CLI/REST inputs and an operator UI text area upload, defaulting to self-attested acceptance when no anchors are supplied.
- Lay groundwork for WebAuthn Metadata Service (MDS) ingestion to hydrate trusted roots and metadata for deterministic fixtures.
- Provide deterministic fixture data (W3C + synthetic) and update existing loader/test infrastructure to consume the new datasets.

## API Adjustments (Attestation Generation)
- Request: add `inputSource` with values `PRESET` (default) or `MANUAL`.
- When `inputSource=PRESET`, `attestationId` is required and current validation rules apply.
- When `inputSource=MANUAL`, `attestationId` is optional, and the service must build attestationObject/clientDataJSON from inputs. Required fields: `format`, `relyingPartyId`, `origin`, `challenge`, `credentialPrivateKey`, and either `signingMode=UNSIGNED` or both `attestationPrivateKey` and `attestationCertificateSerial` (plus optional `customRootCertificates`).
- Response: continue to return `generatedAttestation` and `metadata`, but trim the nested `response` payload to only `clientDataJSON` and `attestationObject`. Remove `attestationId` from user-facing JSON while preserving it inside telemetry fields for preset tracking, trust-anchor resolution, and replay. Telemetry adds `inputSource`, optional `seedPresetId`, and `overrides` (set of changed fields) when applicable.

## UI Adjustments
- Hide the visible Attestation ID field. Keep a hidden `presetId` input that JS fills from the preset dropdown.
- Add implicit auto-switch to Manual mode when edited fields diverge from the selected preset; visually indicate the mode near the Generate button.

## Success Criteria (additions)
- Manual mode produces valid attestation payloads across the four formats without relying on fixtures.
- Preset with overrides switches to Manual automatically and honours edited inputs; telemetry records `seedPresetId` and `overrides`.

## Open Questions
None – the Manual-mode questions raised on 2025-10-17 and 2025-10-18 are resolved in the Clarifications list (deterministic AAGUID defaults, ≥1 certificate for CUSTOM_ROOT, no copy link for now, CLI parity approved, and algorithm inference from credential keys).

## Out of Scope
- Persistent storage of attestation payloads via MapDB (deferred until broader credential import/export support exists for non-assertion authenticators).
- Attestation formats beyond packed, FIDO-U2F, TPM, and Android Key.
- Attestation object signing using hardware tokens—scope is limited to deterministic software fixtures.

## Success Criteria
- Core attestation helpers validate and emit attestation objects for the supported formats, with unit tests covering happy-path and failure cases (invalid signature, RP mismatch, unsupported alg).
- Application services surface sanitized telemetry (`fido2.attest` and `fido2.attestReplay`) and reuse existing redaction policies.
- CLI and REST interfaces expose generation and replay commands/endpoints with comprehensive coverage (MockMvc, Picocli, documentation snapshots).
- CLI attestation generation must honour Manual versus Preset sources: default to Preset, accept `--input-source=manual`, enforce Manual-specific required fields (format, relying-party ID, origin, challenge, credential private key), infer algorithms from supplied keys, and reject `attestationId` unless echoing a preset reference solely for telemetry.
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

## Clarifications – Manual Mode Decisions (2025-10-17)
The following decisions were confirmed by the owner and apply to Manual generation and preset-with-overrides behavior:

1. Manual AAGUID: Default a deterministic synthetic AAGUID per selected format, with optional override (Option B).
2. Manual + CUSTOM_ROOT: Require certificate chain length ≥1 (at least one PEM certificate) (Option B).
3. UI affordance: Initial decision (Option A) added a “Copy preset ID” link; owner rescinded on 2025-10-17, so the button is removed and presets remain implicit.
4. Attestation response payloads must mirror WebAuthn assertions: the API returns `type`, `id`, and `rawId` alongside a nested `response` object containing only `clientDataJSON` and `attestationObject`. Remove `expectedChallenge`, certificate PEM payloads, and `signatureIncluded`; rely on telemetry metadata for signature and certificate counts. Update REST, CLI, and UI formatting plus OpenAPI/docs accordingly.
5. CLI parity: Support `--input-source=manual` mirroring REST (Option A).
6. Manual algorithm inference: Infer algorithm from the supplied credential key; error if undecidable (Option B). Manual CLI submissions omit `attestationId`, require the same field set as REST (format, relying-party ID, origin, challenge, credential key), and accept optional attestation keys/serials when the signing mode mandates them; telemetry captures `seedPresetId`/`overrides` when operators blend presets with overrides.
7. Operator UI ergonomics: Default the credential and attestation private-key text areas to a height that fits standard EC JWK payloads without vertical scrolling so inline editing remains frictionless.

Capture `inputSource`, optional `seedPresetId`, and `overrides` (set of edited fields) in telemetry for preset-with-overrides. REST/CLI validation must follow these decisions.
