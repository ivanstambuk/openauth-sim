# Feature 026 – FIDO2/WebAuthn Attestation Support

| Field | Value |
|-------|-------|
| Status | In review |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/026/plan.md` |
| Linked tasks | `docs/4-architecture/features/026/tasks.md` |
| Roadmap entry | #26 |

## Overview
Extend the simulator so it can generate and verify WebAuthn authenticator attestations in addition to assertions. The feature will deliver a full-stack slice—core attestation helpers, application services, CLI flows, REST endpoints, and operator UI affordances—so operators can exercise registration-style ceremonies alongside the existing assertion evaluation tooling. This iteration also introduces stored attestation credential workflows backed by the shared MapDB store so presets can be seeded once and reused across CLI/REST/UI experiences.


## Goals
- Add fully-documented FIDO2 attestation flows (generation + replay) across core/application/REST/CLI/UI.
- Provide fixture ingestion, telemetry redaction, and OpenAPI updates for attestation payloads.

## Non-Goals
- Does not cover WebAuthn authentication flows (handled elsewhere).
- Does not introduce new crypto algorithms beyond those required for attestation.


## Clarifications
- 2025-10-12 – Deliver the capability across the entire stack (core engine, application services, CLI, REST API, operator UI) in one feature slice to keep protocol parity. (User approved Option A.)
- 2025-10-12 – Support packed, FIDO-U2F, TPM, and Android Key attestation formats on day one. (User directive.)
- 2025-10-12 – Accept authenticator private keys as either JWK or PEM/PKCS#8 inputs when generating attestations, auto-detecting the format. (User approved Option A.)
- 2025-10-12 – Fixture coverage should combine W3C WebAuthn Level 3 attestation examples with the synthetic JSON bundle, mirroring the current assertion strategy. (User approved Option B.)
- 2025-10-20 – Operator UI Evaluate flows expose Preset, Manual, and Stored radio options for attestation generation. Stored mode reuses the assertion-style credential list, while Manual preserves inline overrides. (User approved Option B.)
- 2025-10-26 – Remove the attestation identifier text input from the attestation replay UI; telemetry correlation relies on preset metadata or stored credential identifiers instead. (User directive.)
- 2025-10-27 – Attestation replay exposes Manual (editable inline payloads) and Stored (MapDB-backed) modes; Manual must include a “Load a sample vector” helper that hydrates inline fields with curated attestation fixtures for parity with assertion replay. (User directive.)
- 2025-10-27 – Stored attestation replay must surface attestationObject, clientDataJSON, and expectedChallenge as read-only fields (matching manual form layout) that auto-populate when a stored credential is selected. (User approved Option A.)
- 2025-10-27 – Operator UI stored attestation selectors must display algorithm/W3C section labels (for example, “ES256 (W3C 16.1.6)”) instead of raw vector identifiers (`w3c-packed-es256`). (User directive.)
- 2025-10-27 – Assertion and attestation seeders must converge on a single stored credential per fixture: attestation seeding enriches the existing assertion record with attestation payload attributes instead of persisting a duplicate entry. (User directive.)
- 2025-10-20 – Provide an operator “Seed stored attestation credentials” action that populates curated W3C/synthetic fixtures into the shared credential store so Stored mode is immediately useful during demos. (User approved Option B.)
- 2025-10-20 – Stored attestation credentials persist in the shared `CredentialStore` (MapDB) alongside assertion entries, making them available to CLI, REST, and UI facades. Seeding and retrieval flow through the existing MapDB-backed persistence adapters rather than ad-hoc in-memory catalogs. (User approved Option B.)
- 2025-10-28 – Introduce a synthetic packed PS256 attestation vector so stored attestation seeding hydrates PS256 credentials without manual challenge entry. (User approved Option A.)
- 2025-10-30 – Curated trust anchor selector must allow multi-selection of metadata entries so operators can combine multiple FIDO MDS roots in a single replay submission. (User selected Option B.)
- 2025-10-30 – Filter the curated trust anchor list to entries matching the currently selected attestation format; other formats remain hidden. (User selected Option A.)
- 2025-10-30 – UI/REST/CLI submit curated trust anchors by metadata entry identifier, letting the backend resolve certificates via the catalogue before merging with manual PEM bundles. (User selected Option A.)
- 2025-10-31 – Stored attestation replay surfaces a read-only trust-anchor summary (curated metadata display name or certificate subject) while keeping anchor inputs disabled. (User approved Option B.)
- 2025-11-05 – Stored credential/sample responses expose a `signingKeyHandle` (first 12 hex characters of the SHA-256 digest) plus a constant `privateKeyPlaceholder` value of `[stored-server-side]`; `privateKeyJwk`/`privateKeyPem` fields are removed so facades cannot recover private keys. Operator UI renders the placeholder text and relies on server-side signing during evaluate/replay submissions. (Owner directive.)
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
- 2025-11-05 – Stored credential/sample endpoints must never return authenticator private keys or other long-term secrets to facades. REST/CLI/UI surfaces operate on sanitized identifiers, digests, or metadata while the backend performs signing using server-side private key material (owner directive).
- 2025-10-19 – Generated assertion payloads returned by CLI/REST/UI must match the WebAuthn `PublicKeyCredential` contract (fields limited to `type`, `id`, `rawId`, `response`); retain relying-party metadata, algorithm labels, and verification hints in telemetry/metadata only.
- 2025-10-20 – Operator console attestation preset labels must follow the `<algorithm> (format, W3C <section>)` pattern used for stored assertions; if no W3C section is available, fall back to the vector’s declared origin metadata.
- 2025-10-17 – Hide the visible Attestation ID input; auto-populate a hidden `presetId` field from the selected preset. Provide an optional "Copy preset ID" affordance later if needed. (Approved.)
- 2025-10-18 – Remove `attestationId` from CLI/REST/UI attestation JSON payloads while keeping it available for internal telemetry, preset resolution, and trust-anchor lookup.
- 2025-10-18 – Update attestation private-key UI labels to match the supported formats (“JWK or PEM/PKCS#8”) and eliminate remaining Base64URL references for those inputs.
- 2025-10-17 – Add a Manual generation mode: when no preset is selected (or when overrides are applied), build the attestationObject/clientDataJSON from supplied inputs instead of reading from fixtures. Supported formats: packed, fido-u2f, tpm, android-key. (Approved.)
- 2025-10-18 – Manual credential IDs: default Manual runs generate a new random credential ID server-side (Base64URL). Provide an optional operator override accepting Base64URL; reject malformed overrides, but fall back to random generation when the field is blank/whitespace. (User approved Option A.)
- 2025-10-18 – Attestation result cards should surface certificate chains in a dedicated panel outside the generated JSON payload; remove the prior summary text (“Signature included”/“Certificate chain count”).
- 2025-10-17 – Preset with overrides: if the operator selects a preset but edits any of challenge, RP ID, origin, credential/attestation private key, or certificate serial, the generator must treat the request as Manual and favour the edited values. Telemetry should still record the original presetId as `seedPresetId` and list the `overrides` fields. (Approved in principle.)
- 2025-10-18 – Attestation private keys must accept the same formats as assertions (JWK or PEM/PKCS#8) and drop the Base64URL-only path; when seeding or returning attestation key material, expose JWK representations for parity (fixture JSON remains unchanged; decode during load). (User selected Option B.)
- 2025-10-18 – Present preset attestation private keys as pretty-printed multi-line JWK JSON to improve operator readability while keeping fixtures canonical. (User selected Option B.)
- 2025-10-19 – Stack the attestation credential and attestation private-key inputs vertically in the operator UI to match the assertion textarea layout and remove the horizontal scrollbar. (User confirmed.)
- 2025-10-20 – The REST attestation endpoint rejects unsupported `inputSource` values with `input_source_invalid` while treating a blank/null field as the PRESET default for backwards compatibility.
- 2025-10-20 – The operator UI auto-detects preset overrides, surfaces a Manual/Preset hint near Generate, and keeps manual submissions free of `attestationId` while still recording `seedPresetId`/`overrides` in telemetry.

## Functional Requirements

| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-026-01 | **Attestation generation entry points (S-026-01, S-026-03).** REST/CLI/UI generate attestation objects for preset, manual, and stored sources via `POST /api/v1/webauthn/attest` and matching CLI commands. | Requests accept `inputSource`, preset/credential IDs, optional overrides, and return deterministic `attestationObject`, `clientDataJSON`, plus metadata (format, rpId, origin, certificate fingerprints). | Mode-specific field validation (missing `credentialId`, `rpId`, `origin`, etc.) triggers UI disable states and RFC 7807 field errors exercised by MockMvc/CLI tests. | Invalid combinations (e.g., stored mode with inline overrides) return `attestation.invalid_request` RFC 7807 payloads and halt CLI with exit code 3. | `fido2.attest.generated` events include `inputSource`, `seedPresetId`, `overrides[]`, sanitized hashes, and duration metrics. | Clarifications 2025-10-12. |
| FR-026-02 | **Manual override detection & provenance (S-026-04).** Detect inline edits to preset fields, reclassify submissions as Manual, and surface provenance/override metadata. | Responses echo `seedPresetId` and `overrides[]`; UI badges and CLI/REST logs reflect Manual state without leaking secrets. | JS + Selenium tests mutate every editable field to confirm auto-switching plus telemetry snapshots for overrides. | Override mismatches block submission and emit `manualOverrideMismatch` telemetry. | `fido2.attest.generated` stores override arrays; verbose trace log references align with UI badges. | Clarifications 2025-10-20. |
| FR-026-03 | **Stored attestation workflows (S-026-05).** Seed curated credentials, expose sanitized metadata selectors, and hydrate private keys server-side for stored generation/replay. | `/api/v1/webauthn/attestations/seed` loads descriptors; selectors show sanitized metadata; stored submissions fill read-only UI fields and reuse MapDB without exposing secrets. | REST/UI/CLI tests enforce credential-ID requirements, seeding idempotency, sanitized outputs, and stored replay toggles. | Lookup failures or missing seeds return RFC 7807 payloads plus `storedLookupFailure` telemetry. | `fido2.attest.seeded` and `fido2.attest.generated` events include credential hashes, formats, and status fields only. | Clarifications 2025-10-27. |
| FR-026-04 | **Replay & verification APIs (S-026-02).** Provide `/api/v1/webauthn/attest/replay` + CLI commands that validate attestation blobs against curated/manual trust anchors. | Replay responses include verdict banners, certificate-chain summaries, trust-anchor metadata, and telemetry IDs surfaced in UI + CLI. | MockMvc + CLI tests compare verdicts for all fixture formats, covering manual PEM uploads and curated anchor selection. | Invalid payloads or trust-anchor mismatches return RFC 7807 errors with `attestation.replay.invalid_payload` reason codes. | `fido2.attest.replayed` events capture `format`, `trustAnchors`, `verdict`, `failureReason?`, `durationMs`, sanitized fingerprints. | Clarifications 2025-10-27. |
| FR-026-05 | **Telemetry, docs, and governance (S-026-02, S-026-05).** Keep telemetry snapshots, roadmap/how-to docs, and drift-gate traceability aligned with attestation flows. | Snapshot tests prove telemetry parity; roadmap/how-to entries document workflows; drift gate maps every FR/NFR to increments before acceptance. | Implementation Drift Gate cross-check plus docs review ensures coverage; failing snapshots block completion. | Missing telemetry fields or stale docs halt acceptance until remediated and logged in the migration tracker. | TelemetryContracts adapters emit sanitized payloads; verbose trace IDs link UI panels to backend logs. | Constitution Principle 4 + Clarifications 2025-10-27. |
## Non-Functional Requirements

| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-026-01 | Deterministic attestation outputs for identical inputs (including stored credentials). | Fixture reproducibility + telemetry diffs. | Core/REST snapshot tests assert byte-for-byte equality. | Core generators, fixture loaders, serialization helpers. | Clarifications 2025-10-12. |
| NFR-026-02 | Sensitive material (private keys, raw certificates) never leaves server boundaries. | Security & governance posture. | MockMvc + Selenium tests verify only hashes/fingerprints appear; telemetry redaction scan stays green. | Application services, TelemetryContracts, REST DTOs, UI templates. | Constitution Principle 5. |
| NFR-026-03 | Generation/replay round trips complete within 750 ms on developer hardware, including trust-anchor parsing. | Operator UX parity with assertions. | Local benchmark harness + Selenium timing assertions. | REST controllers, CLI commands, trust-anchor parsers. | Roadmap Workstream 26. |
| NFR-026-04 | Telemetry/logs capture trust-anchor metadata without leaking PEM bodies and integrate with monitoring dashboards. | Observability + on-call diagnostics. | Snapshot comparison + log inspection; Implementation Drift Gate checklist. | TelemetryContracts adapters, logging config, docs. | Clarifications 2025-10-27. |
## UI / Interaction Mock-ups
- Evaluate tab shows a unified fieldset with mode selector (`Preset`, `Manual`, `Stored`), preset dropdown, manual inputs (challenge, rpId, origin), stored credential selector, and inline Manual badge when overrides occur.
- Replay tab mirrors Evaluate but emphasises trust-anchor selection (curated metadata multi-select + manual PEM upload) and renders verdict banners plus certificate-chain accordions.
- A “Seed attestation credentials” control triggers `/api/v1/webauthn/attestations/seed`, displays success/already-present toasts, and refreshes stored selectors; selectors only display sanitized metadata (format, signing mode).

## Branch & Scenario Matrix

| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-026-01 | Fixture catalogues, metadata loaders, and attestation generators cover packed, FIDO-U2F, TPM, and Android Key formats with deterministic core tests. |
| S-026-02 | Application services emit sanitized telemetry for attestation generation/replay, including manual input detection and lint/quality compliance. |
| S-026-03 | CLI and REST endpoints implement attestation generation/replay (including `inputSource`, credential IDs, and response-shape changes) with refreshed OpenAPI artifacts. |
| S-026-04 | Operator UI Evaluate/Replay flows expose Preset/Manual/Stored journeys, inline sample loaders, certificate/result layout updates, and trust-anchor helpers mirroring assertion UX. |
| S-026-05 | Stored attestation credentials (MapDB schema, seeding endpoints, stored generation/replay, trust-anchor summaries, sanitisation) operate end-to-end across application/CLI/REST/UI. |

## Test Strategy
- **Core:** `AttestationGeneratorTest`, `AttestationVerifierTest`, and format-specific suites covering packed, FIDO-U2F, TPM, and Android Key fixtures.
- **Application:** Service-layer tests validating input-source routing, provenance metadata, telemetry emission, and stored credential lookups.
- **REST:** MockMvc suites for `/api/v1/webauthn/attest`, `/api/v1/webauthn/attest/replay`, and `/api/v1/webauthn/attestations/*` plus OpenAPI snapshot verification.
- **CLI:** Picocli tests covering preset/manual/stored modes, validation errors, and replay verdict output.
- **UI:** JS unit tests for preset/manual override detection, stored selector seeding, and trust-anchor widgets; Selenium suites for evaluate/replay flows.
- **Docs/Contracts:** Telemetry snapshot comparisons, how-to updates, and roadmap entries tied to the Implementation Drift Gate.

## Interface & Contract Catalogue

### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-026-01 | `AttestationPreset` metadata (format, signing mode, rpId, origin, attestationObject, clientDataJSON). | docs, core |
| DO-026-02 | `StoredAttestationCredential` (credentialId, format, signing mode, origin, certificate chain, private key, trust anchors). | application, infra |
| DO-026-03 | `AttestationResult` (attestationObject, clientDataJSON, provenance metadata, telemetry hashes). | application, rest-api, cli, ui |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-026-01 | REST `POST /api/v1/webauthn/attest` | Generates attestation objects for preset/manual/stored inputs. | Accepts `inputSource`, preset/credential IDs, overrides, trust anchors. |
| API-026-02 | REST `POST /api/v1/webauthn/attest/replay` | Validates supplied attestation payloads against curated/manual trust anchors. | Returns verdicts, certificate summaries, telemetry metadata. |
| API-026-03 | REST `POST /api/v1/webauthn/attestations/seed` | Seeds curated stored attestation descriptors, returning `{addedCount, addedCredentialIds}`. | Idempotent; used by UI “Seed” control. |
| API-026-04 | REST `GET /api/v1/webauthn/attestations/{id}` | Publishes sanitized stored attestation metadata for selector hydration. | No private keys exposed. |

### CLI Commands
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-026-01 | `fido2 attest --input-source=<preset|manual|stored>` | Generates attestation objects, enforcing mode-specific validation, accepts overrides, and prints sanitized outputs. |
| CLI-026-02 | `fido2 attest-replay --attestation-file <path>` | Validates attestation payloads with optional curated trust anchors and surfaces verdicts/metadata. |

### Telemetry Events
| ID | Event name | Fields / Redaction summary |
|----|------------|----------------------------|
| TE-026-01 | `fido2.attest.generated` | `inputSource`, `seedPresetId`, `overrides[]`, `format`, `rpId`, `origin`, `durationMs`; no raw secrets. |
| TE-026-02 | `fido2.attest.replayed` | `format`, `trustAnchors`, `verdict`, `failureReason?`, `durationMs`, sanitized certificate fingerprints. |
| TE-026-03 | `fido2.attest.seeded` | `addedCount`, `formats`, `durationMs`, `status` (success/already-present/error). |

### Fixtures & Sample Data
| ID | Path | Description |
|----|------|-------------|
| FX-026-01 | `docs/test-vectors/fido2/attestation/preset/*.json` | W3C Level 3 attestation presets (packed, FIDO-U2F, TPM, Android Key). |
| FX-026-02 | `docs/test-vectors/fido2/attestation/stored/*.json` | Stored credential descriptors consumed by the seeding pipeline. |
| FX-026-03 | `docs/test-vectors/fido2/trust-anchors/*.pem` | Curated trust anchors referenced by replay flows. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|----------------------------|
| UI-026-01 | Evaluate – Manual overrides badge | Operator edits preset fields; UI toggles to Manual, highlights overrides, and displays provenance. |
| UI-026-02 | Evaluate – Stored credential mode | Operator selects stored credential; manual fields disable, read-only metadata appears, trust anchors locked. |
| UI-026-03 | Replay – Verdict banner & chain accordion | Replay request completes; UI renders success/failure banner plus certificate-chain accordion with sanitized summaries. |

## Spec DSL
```yaml
input_sources:
  - id: PRESET
    fields: [presetId, overrides?, trustAnchors?]
  - id: MANUAL
    fields: [format, rpId, origin, challenge, credentialKey, attestationKey?, certificateChain?]
  - id: STORED
    fields: [credentialId, trustAnchors?]
trust_anchors:
  curated_catalog: docs/test-vectors/fido2/trust-anchors/catalog.json
  manual_upload: pem_bundle
stored_credentials:
  adapter: WebAuthnAttestationSeedService
  schema:
    credentialId: base64url
    format: packed|fido-u2f|tpm|android-key
    signingMode: PS256|ES256|EdDSA
    metadata:
      origin: https://simulator.local
      rpId: simulator.local
```

## Appendix

### Scope
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

### API Adjustments (Attestation Generation)
- Request: add `inputSource` with values `PRESET` (default) or `MANUAL`.
- When `inputSource=PRESET`, `attestationId` is required and current validation rules apply.
- When `inputSource=MANUAL`, `attestationId` is optional, and the service must build attestationObject/clientDataJSON from inputs. Required fields: `format`, `relyingPartyId`, `origin`, `challenge`, `credentialPrivateKey`, and either `signingMode=UNSIGNED` or both `attestationPrivateKey` and `attestationCertificateSerial` (plus optional `customRootCertificates`).
- Response: continue to return `generatedAttestation` and `metadata`, but trim the nested `response` payload to only `clientDataJSON` and `attestationObject`. Remove `attestationId` from user-facing JSON while preserving it inside telemetry fields for preset tracking, trust-anchor resolution, and replay. Telemetry adds `inputSource`, optional `seedPresetId`, and `overrides` (set of changed fields) when applicable.

### UI Adjustments
- Remove any user-facing attestation identifier fields from the attestation forms. When a preset is selected, capture its identifier through hidden metadata so telemetry still tags preset runs; replay flows rely on stored credential IDs or backend-generated identifiers.
- Add implicit auto-switch to Manual mode when edited fields diverge from the selected preset; visually indicate the mode near the Generate button.
- Ensure the attestation replay tab mirrors the Preset / Manual / Stored selector and, when stored mode is active, populate fields directly from MapDB metadata without exposing an attestation identifier input.

### Success Criteria (additions)
- Manual mode produces valid attestation payloads across the four formats without relying on fixtures.
- Preset with overrides switches to Manual automatically and honours edited inputs; telemetry records `seedPresetId` and `overrides`.

### Open Questions
None – the Manual-mode (2025-10-17/2025-10-18) and Stored-mode (2025-10-20) questions are resolved in the Clarifications list (deterministic AAGUID defaults, ≥1 certificate for CUSTOM_ROOT, CLI parity, algorithm inference from credential keys, Preset/Manual/Stored selector, MapDB-backed persistence, and curated seeding control).

### Out of Scope
- Credential export/import tooling beyond the curated “seed stored credentials” control (e.g., no file upload/download for arbitrary attestation stores yet).
- Attestation formats beyond packed, FIDO-U2F, TPM, and Android Key.
- Attestation object signing using hardware tokens—scope is limited to deterministic software fixtures.

### Success Criteria
- Core attestation helpers validate and emit attestation objects for the supported formats, with unit tests covering happy-path and failure cases (invalid signature, RP mismatch, unsupported alg).
- Application services surface sanitized telemetry (`fido2.attest` and `fido2.attestReplay`) and reuse existing redaction policies.
- CLI and REST interfaces expose generation and replay commands/endpoints with comprehensive coverage (MockMvc, Picocli, documentation snapshots); generation accepts `inputSource` values of PRESET, MANUAL, and STORED, and attestation replay supports inline payloads plus `inputSource=STORED` to resolve persisted descriptors.
- Stored attestation generation pulls credentials from the shared MapDB-backed `CredentialStore`, enforcing the same validation contracts as stored assertions and propagating telemetry for `seedPresetId`/`overrides`/`inputSource`.
- Stored credential directories and sample endpoints expose only sanitized metadata (digests, handles, descriptive labels); authenticator private keys remain server-side and are never returned to CLI/REST/operator UI clients.
- Sanitized responses include a deterministic `signingKeyHandle` (12-character hex prefix of the SHA-256 digest calculated over the raw private key bytes) so operators can confirm which key backs a credential without recovering secret material. The sample REST endpoint also returns a constant `privateKeyPlaceholder` of `[stored-server-side]`, which the operator UI renders in read-only form fields; no `privateKeyJwk` or `privateKeyPem` payloads cross the network.
- Provide a deterministic seeding workflow (application service + UI control + CLI/REST parity) that installs curated attestation credentials into the store, is safe to re-run, and surfaces success/failure telemetry.
- CLI attestation generation must honour the three input sources: default to PRESET, accept `--input-source=manual` for override flows, and support `--input-source=stored` plus selectors that mirror the assertion CLI semantics (e.g., `--credential-id`) to resolve existing entries. Manual runs enforce required fields (format, relying-party ID, origin, challenge, credential private key), infer algorithms from supplied keys, and reject `attestationId` except when echoing preset metadata for telemetry.
- Operator UI displays an attestation-specific form, toggles cleanly between assertion and attestation workflows, surfaces Preset/Manual/Stored radio options for both generation and replay, removes user-facing attestation identifier inputs, and renders result cards with copy/download affordances. Stored mode lists MapDB-backed credentials, includes a seed control to populate curated attestation presets, and exercises stored replay paths via Selenium coverage.
- Trust-anchor aware verification accepts optional anchor bundles (UI upload, CLI/REST parameter) and clearly reports when validation falls back to self-attested evaluation.
- Fixture loaders/tests ingest W3C and synthetic attestation data, ensuring multi-format coverage in both generation and replay flows.
- `./gradlew spotlessApply check` and targeted suites (`:core:test`, `:application:test`, `:cli:test`, `:rest-api:test`) remain green after each increment.

### Telemetry & Observability
- Introduce dedicated telemetry adapters (e.g., `fido2.attest`, `fido2.attestReplay`) capturing attestation format, RP ID, authenticator AAGUID, and verification outcome while redacting raw statements and keys.
- Ensure UI/CLI/REST surfaces expose telemetry IDs for operator correlation without leaking attestation statements.
- Telemetry metadata must also record the attestation input source (Preset, Manual, Stored), resolved stored credential identifiers, and any seeded preset identifiers used during the request.

### Dependencies & Considerations
- Builds on `WebAuthnAssertionGenerationApplicationService` infrastructure; cross-verify COSE/JWK utilities support the new attestation encodings.
- Stored mode reuses the existing `CredentialStore` schema; extend the persistence adapter to persist attestation metadata + private keys while ensuring only sanitized summaries (no private key material) flow back to facades, maintaining assertion compatibility and migration notes.
- Ensure TPM and Android Key attestation verification handles certificate chains appropriately (likely synthetic chain fixtures).
- Reuse existing `.gitleaks.toml` allowances or extend them if new fixture files introduce additional high-entropy payloads.

### Clarifications – Manual Mode Decisions (2025-10-17)
The following decisions were confirmed by the owner and apply to Manual generation and preset-with-overrides behavior:

1. Manual AAGUID: Default a deterministic synthetic AAGUID per selected format, with optional override (Option B).
2. Manual + CUSTOM_ROOT: Require certificate chain length ≥1 (at least one PEM certificate) (Option B).
3. UI affordance: Initial decision (Option A) added a “Copy preset ID” link; owner rescinded on 2025-10-17, so the button is removed and presets remain implicit.
4. Attestation response payloads must mirror WebAuthn assertions: the API returns `type`, `id`, and `rawId` alongside a nested `response` object containing only `clientDataJSON` and `attestationObject`. Remove `expectedChallenge`, certificate PEM payloads, and `signatureIncluded`; rely on telemetry metadata for signature and certificate counts. Update REST, CLI, and UI formatting plus OpenAPI/docs accordingly.
5. CLI parity: Support `--input-source=manual` mirroring REST (Option A).
6. Manual algorithm inference: Infer algorithm from the supplied credential key; error if undecidable (Option B). Manual CLI submissions omit `attestationId`, require the same field set as REST (format, relying-party ID, origin, challenge, credential key), and accept optional attestation keys/serials when the signing mode mandates them; telemetry captures `seedPresetId`/`overrides` when operators blend presets with overrides.
7. Operator UI ergonomics: Default the credential and attestation private-key text areas to a height that fits standard EC JWK payloads without vertical scrolling so inline editing remains frictionless.

Capture `inputSource`, optional `seedPresetId`, and `overrides` (set of edited fields) in telemetry for preset-with-overrides. REST/CLI validation must follow these decisions.

### Stored attestation workflows

- Stored attestation credentials reside in the shared MapDB-backed `CredentialStore` and reuse the `WebAuthnCredentialPersistenceAdapter` schema with attestation metadata (format, signing mode, origin, certificate chain, custom roots, private keys).
- A dedicated `WebAuthnAttestationSeedService` seeds one curated descriptor per supported attestation format when absent, storing the generated `attestationObject`, `clientDataJSON`, and `expectedChallenge` under `fido2.attestation.stored.*` attributes so replay flows can execute without recomputing payloads.
- Seeding must be idempotent: subsequent runs skip existing identifiers and return only the credentials added during the current invocation.
- When a stored assertion credential already exists for a fixture (e.g., seeded via the assertion workflow), attestation seeding updates that record in-place with attestation metadata instead of writing a second MapDB row.
- Application services emit telemetry fields for the attestation input source (`preset`, `manual`, `stored`), include `storedCredentialId` when applicable, and retain existing metadata (`generationMode`, `certificateChainCount`, `customRootCount`, `seedPresetId`, `overrides`).
- CLI:
  - `fido2 attest --input-source=stored` requires `--credential-id` and `--challenge`, reuses optional relying-party/origin overrides (validation ensures matches when provided), and relays telemetry for stored usage.
  - `fido2 attest-replay --input-source=stored` resolves MapDB-backed descriptors, reuses persisted attestationObject/clientDataJSON/expectedChallenge values, and accepts the same trust-anchor parameters as inline replay.
  - `fido2 seed-attestations` invokes the seed service against the configured credential store, printing the list of identifiers seeded or noting that all curated entries already exist.
    - Stored attestation seeds reuse the canonical assertion credential identifier; rerunning the command after assertion seeding simply enriches the existing record without altering its stored credential secret.
  - Inline replay adds a repeatable `--metadata-anchor <entryId>` option so operators can combine curated catalogue entries without hand editing PEM material; telemetry records every identifier alongside the derived anchor mode.
- REST API:
- `POST /api/v1/webauthn/attest` accepts `inputSource=STORED`, enforces `credentialId` + Base64URL `challenge`, applies optional relying-party/origin overrides, and exposes telemetry metadata (including `inputSource` and `storedCredentialId`) in the response.
- `POST /api/v1/webauthn/attest/replay` accepts inline payloads or `inputSource=STORED`; stored submissions require `credentialId`, automatically hydrate attestation artifacts from persistence, and honour trust-anchor parameters.
- Inline submissions may supply `metadataAnchorIds` (array of catalogue entry identifiers); the service resolves each entry before combining them with manual PEM bundles and persists the identifier list in telemetry and response metadata.
- Stored replay metadata responses include a `trustAnchorSummaries` array describing curated metadata display names when available, falling back to decoded certificate subjects for persisted chains so operator UIs can show anchor provenance without enabling edits.
- `POST /api/v1/webauthn/attestations/seed` seeds curated attestation descriptors and returns `{addedCount, addedCredentialIds}`.
    - When canonical assertion credentials already exist, the seed endpoint enriches the existing record in place—preserving the stored credential secret/metadata while layering attestation payload attributes—so no duplicate entries appear in dropdowns or directory listings.
    - The synthetic packed PS256 attestation fixture must be included in the curated seed set until W3C publishes an official PS256 example.
  - `GET /api/v1/webauthn/attestations/{id}` publishes stored attestation metadata (format, relying party, origin, signing mode, certificate chain) for the operator UI.
- Operator UI:
  - The attestation Evaluate form renders Preset / Manual / Stored radio buttons. Stored generation hides manual input areas, lists stored credential options, requests a Base64URL challenge when applicable, and submits directly to the REST endpoints without exposing attestation identifier inputs.
  - The attestation Replay form offers Manual / Stored radio buttons. Manual mode presents fully editable inline parameters (format, relying party metadata, attestation payloads, trust anchors); Stored mode hydrates read-only values (relying party, origin, expected challenge, attestationObject, clientDataJSON) from persistence and submits without trust-anchor inputs.
  - Stored replay now renders a read-only “Trust anchors” summary that lists curated metadata names or certificate subjects resolved from the persisted chain so operators can verify provenance without editing anchors.
  - Stored attestation read-only inputs (relying party, origin, challenge, attestationObject, clientDataJSON) must adopt the same muted styling as other non-editable fields so operators can tell they are locked.
  - Manual replay panels include a curated trust-anchor multi-select filtered to the selected attestation format. Selections post only metadata entry identifiers; the UI still renders the existing PEM textarea for custom anchors and surfaces the combined selection summary.
  - Stored attestation selectors hydrate asynchronously from the shared credential store; UI logic (and Selenium coverage) must wait for the targeted credential option to materialise before attempting selection so tests remain stable against slow persistence startups.
- Stored attestation dropdown labels mirror assertion naming (algorithm + W3C section when available) regardless of the underlying credential identifier.
- The curated seed catalogue now includes the synthetic `synthetic-packed-ps256` fixture (RSA-PSS) so PS256 credentials hydrate with a deterministic challenge; seeding logic falls back to generator presets whenever a W3C attestation vector is unavailable.
  - A “Seed attestation credentials” control calls `/api/v1/webauthn/attestations/seed`, refreshes the selector, and surfaces status messaging (success, already-present, unavailable).
  - Selenium coverage verifies stored mode visibility across evaluate and replay flows, selector population after seeding, validation when credential IDs are missing, and the absence of user-facing attestation identifier fields.
