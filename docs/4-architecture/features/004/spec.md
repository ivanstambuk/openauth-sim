# Feature 004 – FIDO2/WebAuthn Assertions & Attestations

| Field | Value |
|-------|-------|
| Status | In review |
| Last updated | 2025-11-13 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/004/plan.md` |
| Linked tasks | `docs/4-architecture/features/004/tasks.md` |
| Roadmap entry | #4 – FIDO2/WebAuthn Assertions & Attestations |

## Overview
Feature 004 unifies the former WebAuthn assertion (legacy Feature 024) and attestation (legacy Feature 026) streams so every core/application/CLI/REST/UI surface, telemetry signal, fixture catalogue, and document reference points to a single canonical source. The specification now spans deterministic assertion evaluation/replay, attestation generation/replay, trust-anchor handling, and the operator guidance that ties these flows together.

## Clarifications
- 2025-11-13 – Feature 004 is the active source of truth for WebAuthn assertion/attestation flows; legacy references have been removed now that the merged spec/plan/tasks are current.

## Goals
- Deliver deterministic WebAuthn assertion evaluation and replay plus attestation generation and verification across every module (core, application, persistence, CLI, REST, and operator console UI).
- Keep fixtures, telemetry, and documentation aligned so operators and automation observe consistent evidence (W3C vectors, curated presets, sanitized telemetry frames, OpenAPI snapshots).
- Capture seeding guidance, trust-anchor handling, and verification flows so CLI/REST/JS facades reuse the same credential store semantics.

## Non-Goals
- WebAuthn registration, authenticator emulation, or other onboarding flows remain out of scope for this batch.
- No toolchain, dependency, or persistence provider changes are introduced—MapDB remains the credential store and existing adapters remain untouched.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-004-01 | Evaluate stored or inline WebAuthn assertions via CLI/REST/UI with deterministic fixtures and sanitized telemetry for every outcome. | CLI/REST/UI submit credential/assertion bundles plus a stored/inline mode flag, the verifier checks the counter/signature against W3C vectors or stored secrets, and the response includes the verification status, description, and replay hints. | Missing credential ID, assertion payload, or client data JSON returns a 400 (REST) / validation error (CLI) before the verifier runs, emphasising the absent field in the error message. | Signature/counter mismatches, replay detection, or disabled presets result in a sanitized failure reason (no raw keys), and the UI/CLI surfaces the same reason alongside the telemetry reason code. | `fido2.evaluate` (credentialIdHash, mode=[stored|inline], result=[pass|fail], reasonCode, scenarioId). | Legacy Feature 024 spec, W3C WebAuthn §5.5, migration directive 2025-11-11. |
| FR-004-02 | Replay stored WebAuthn assertions for diagnostics so operators can compare deterministic fixture results to production appliances. | The `replay` façade pulls the credential from MapDB, re-runs the verifier with stored counters/signatures, and returns a trace containing the stored assertion, submission details, and any replay hints. | Omitting the stored credential identifier or targeting a credential that was purged returns a 404/CLI error before any assertion logic executes. | Replay detects tampered signatures, counter regression, or mismatched RPs and emits a sanitized failure payload plus consistent reason codes for REST/CLI/UI. | `fido2.replay` (credentialIdHash, replayMode=[stored], result, reasonCode). | Legacy Feature 024 spec, CLI/Web UI regression notes 2025-10-26. |
| FR-004-03 | Generate attestation payloads (packed, FIDO-U2F, TPM, Android Key) through CLI/REST/UI with optional trust anchors and preset seeds. | Commands populate `attestationObject`, `clientDataJSON`, `clientDataHash`, and certificate chains via the generator, honoring the requested format and trust-anchor selection, then return the artifacts together with telemetry that records the anchor source. | Missing challenge, malformed credential parameters, or a non-PEM trust anchor payload triggers a validation error before generation. | Trust-anchor verification failures, unsupported formats, or certificate parsing errors yield sanitized failure responses that include the failure reason but no private keys. | `fido2.attest` (attestationFormat, anchorSource=[preset|manual|self-attested], trustAnchorId, result). | Legacy Feature 026 spec, attestation generator notes 2025-10-16, trust-anchor telemetry audit 2025-11-05. |
| FR-004-04 | Replay and verify attestation payloads (stored/manual/inline) with metadata-driven feedback plus trust-anchor summaries. | Replay services validate the attestation/certificate chain, surface `trustAnchorSummaries`, and provide metadata about certificate subjects, anchor status, and warnings so the UI/CLI can render them. | Missing attestation object or client data yields a 400/validation error before verification attempts. | Invalid certificate chains, missing anchors, or replay policy violations trigger sanitized failure responses with telemetry reason codes (no certificate blob leakage). | `fido2.attestReplay` (trustAnchorReference, result, warningCodes). | Legacy Feature 026 spec, trust-anchor Apollo notes 2025-10-30. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-004-01 | Deterministic fixture and seed coverage for W3C vectors plus curated presets keeps regressions detectable. | Regression prevention requires reproducible vectors. | Core/persistence tests consume `docs/webauthn_w3c_vectors.json`, `docs/webauthn_assertion_vectors.json`, and `docs/webauthn_attestation/*` fixtures. | Fixture JSON under `docs/`, MapDB credential store. | Legacy Feature 024/026 requirements, Batch P2 migration notes. |
| NFR-004-02 | Telemetry parity and sanitisation ensure processors never log raw key material while still exposing anchors and modes. | Auditability and security controls. | Unit tests assert `TelemetryContracts` emit hashed identifiers and per-mode reason codes; telemetry tables remain stable. | `application.telemetry.TelemetryContracts`, CLI/REST telemetry bridges. | Feature 013 governance logging, `TelemetryContracts` spec. |
| NFR-004-03 | Operator console UI keeps toggles and metadata summaries accessible (WCAG 2.1 AA) while reusing shared `secret-fields` helpers. | Accessibility and maintainability. | Selenium coverage for stored/inline evaluate panels, attestation generation, manual anchor modals, and replay cards. | `ui/`, Selenium wrappers, shared JS helpers. | Operator console design guidance 2025-10-20. |
| NFR-004-04 | MapDB persistence schemas accommodate attestation metadata without breaking existing assertion records. | Backward compatibility. | Integration tests (`infra-persistence`, CLI seeds, REST seeded credentials) run after schema extensions. | `infra-persistence` credential store adapters, `CredentialStoreFactory`. | Persistence migration guard 2025-10-24. |

## UI / Interaction Mock-ups
```
[Evaluation + Replay Tab]                                    [Attestation Generation Panel]
+--------------------------+                               +---------------------------------------+
| Mode: (O) Stored ( ) Inline |                             | Format: [packed | FIDO-U2F | TPM | Android]
| Credential ID: [_________]  |                             | Trust Anchor: [dropdown + Upload PEM]
| Assertion: [multi-line JSON]                              | Challenge: [auto-generate | manual]
| [Evaluate] [Replay Stored] |                             | [Generate Attestation] [Replay Attestation]
| Last result: <Success / Failure summary> |                 | Summary: trustAnchorSummaries table, warnings
+--------------------------+                               +---------------------------------------+
```

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-004-01 | Stored WebAuthn assertion evaluation (MapDB credential, W3C vector comparison, telemetry `fido2.evaluate`). |
| S-004-02 | Inline assertion evaluation where the operator pastes JSON and the CLI/REST pipeline validates it before touching persistence. |
| S-004-03 | Attestation generation requests covering packed/FIDO-U2F/TPM/Android formats with trust-anchor upload or preset selection; telemetry records `anchorSource`. |
| S-004-04 | Attestation replay produces `trustAnchorSummaries` plus certificate metadata for stored/manual credentials, surfaced in CLI/REST/UI. |
| S-004-05 | Operator console toggles between assertion vs attestation modes and between evaluation vs replay while keeping the shared `secret-fields` helper in sync. |

## Test Strategy
- **Core:** `:core:test --tests "*WebAuthnAssertionVerifierTest" --tests "*WebAuthnAttestationVerifierTest" --tests "*WebAuthnAttestationGeneratorTest"` covers deterministic vectors and attestation formats. Failures are staged before implementing core helpers.
- **Application:** `:application:test --tests "*WebAuthn*ServiceTest"` exercises persistence integrations, trust-anchor resolvers, telemetry bridging, and replay helpers.
- **REST:** `:rest-api:test --tests "io.openauth.sim.rest.Fido2*EndpointTest" --tests "io.openauth.sim.rest.OpenApiSnapshotTest" --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"` validates controllers, OpenAPI snapshots, and operator Console Selenium flows.
- **CLI:** `:cli:test --tests "*Fido2Cli*"` ensures maintenance commands (`evaluate`, `replay`, `seed`, `attest`, `attestReplay`, `seed-attestations`) trigger the correct services and telemetry.
- **UI (JS / Selenium):** `node --test rest-api/src/test/javascript/emv/console.test.js` plus UI Selenium suites cover the evaluation/attestation panels, preset selectors, and trust-anchor metadata rendering.
- **Docs / Contracts:** `./gradlew --no-daemon spotlessApply check` validates formatting, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"` refreshes contract artifacts, and `_current-session.md` captures the verification milestones.

## Interface & Contract Catalogue

### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-004-01 | `WebAuthnCredentialDescriptor` (aaguid, keyType, counter, transports for stored credentials). | core, application, infra-persistence |
| DO-004-02 | `WebAuthnAssertionVector` (authenticatorData, clientDataJSON, signature, keyMaterial). | core, rest-api, ui |
| DO-004-03 | `WebAuthnAttestationRequest` (attestationObject, clientDataJSON, format, challenge). | core, application, rest-api |
| DO-004-04 | `WebAuthnTrustAnchorMetadata` (anchorId, human-readable name, certificate fingerprint). | application, rest-api |
| DO-004-05 | `WebAuthnStoredCredential` (credentialId, formats, attestation metadata, presetId). | infra-persistence, rest-api, ui |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-004-01 | REST POST `/api/v1/fido2/evaluate` | Evaluate stored or inline assertions, returning verification status. | Supports `mode` query (stored/inline), seeded credential IDs, and reason codes. |
| API-004-02 | REST POST `/api/v1/fido2/replay` | Replay stored assertions for diagnostics and telemetry parity. | Returns verification trace, counters, and sanitized failure reasons. |
| API-004-03 | REST GET `/api/v1/fido2/credentials` | List stored assertion credentials for the UI dropdown. | Mirrors `CredentialStore` contents. |
| API-004-04 | REST POST `/api/v1/webauthn/attest` | Generate attestation objects for packed/FIDO-U2F/TPM/Android Key formats. | Accepts `challenge`, `format`, `trustAnchorPem`, `presetId`. |
| API-004-05 | REST POST `/api/v1/webauthn/attest/replay` | Replay attestation payloads and return verification metadata plus `trustAnchorSummaries`. | Uses stored/manual/inline attestation data. |
| API-004-06 | REST POST `/api/v1/webauthn/attestations/seed` | Seed stored attestation credentials from curated fixtures. | Mirrors CLI `seed-attestations`. |
| API-004-07 | REST GET `/api/v1/webauthn/attestations/{id}` | Retrieve stored attestation metadata for the UI. | Reflects `WebAuthnTrustAnchorMetadata` and certificate summary. |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-004-01 | `maintenance fido2 evaluate` | Evaluate stored/inline assertions; flags mirror REST payloads. |
| CLI-004-02 | `maintenance fido2 replay` | Replay stored assertions for diagnostics, emitting telemetry reason codes. |
| CLI-004-03 | `maintenance fido2 seed` | Load curated assertion presets into MapDB for subsequent evaluation/replay. |
| CLI-004-04 | `maintenance fido2 attest` | Generate attestation payloads in the requested format with optional trust-anchor PEM. |
| CLI-004-05 | `maintenance fido2 attest-replay` | Replay attestation payloads (stored/manual/inline) and output metadata summaries. |
| CLI-004-06 | `maintenance fido2 seed-attestations` | Seed attestation credentials from fixture bundles and document their metadata. |

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|-----------|---------------------------|
| TE-004-01 | `fido2.evaluate` | `credentialIdHash`, `mode`, `result`, `reasonCode`, `scenarioId`; no private keys. |
| TE-004-02 | `fido2.replay` | `credentialIdHash`, `replayMode`, `result`, `reasonCode`. |
| TE-004-03 | `fido2.seed` | `addedCount`, `presetIdsHash`, `storeSize`. |
| TE-004-04 | `fido2.attest` | `attestationFormat`, `anchorSource`, `trustAnchorId`, `result`. |
| TE-004-05 | `fido2.attestReplay` | `trustAnchorReference`, `result`, `warningCodes`, `scenarioId`. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-004-01 | `docs/webauthn_w3c_vectors.json` | W3C §16 assertion vectors for deterministic verification. |
| FX-004-02 | `docs/webauthn_assertion_vectors.json` | Synthetic assertion bundle for inline evaluation tests. |
| FX-004-03 | `docs/webauthn_attestation/packed.json` | Packed attestation payloads used by generator tests. |
| FX-004-04 | `docs/webauthn_attestation/fido-u2f.json` | FIDO-U2F attestation payloads. |
| FX-004-05 | `docs/webauthn_attestation/tpm.json` | TPM attestation fixtures. |
| FX-004-06 | `docs/webauthn_attestation/android-key.json` | Android Key attestation fixtures. |
| FX-004-07 | `docs/webauthn_attestation/presets.json` | Preset metadata used by CLI/REST seed helpers. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| UI-004-01 | Stored evaluate panel | Dropdown selects seeded credential, pressing Evaluate renders success card and updates `/api/v1/fido2/evaluate`. |
| UI-004-02 | Inline evaluate panel | Operator pastes assertion JSON, toggles to Inline, and the UI validates JSON before calling `/api/v1/fido2/evaluate`. |
| UI-004-03 | Attestation generation panel | Format chooser, trust-anchor dropdown/upload, and Generate button that fronts `/api/v1/webauthn/attest`. |
| UI-004-04 | Attestation replay panel | Mode switches (Stored/Manual/Inline) plus trust-anchor metadata summary from `/api/v1/webauthn/attest/replay`. |
| UI-004-05 | Trust-anchor maintenance modal | Displays `trustAnchorSummaries`, allows uploads, and links to the seed helper. |

## Telemetry & Observability
Every facade emits through `application.telemetry.TelemetryContracts`, reusing the hashed `credentialIdHash`, sanitized `trustAnchorId`, and explicit `scenarioId` so audit trails remain aligned even when CLI, REST, and UI clients revisit the same events. `fido2.evaluate`, `fido2.replay`, `fido2.attest`, and `fido2.attestReplay` all populate `mode`/`anchorSource` fields and avoid logging raw key material; instrumentation tests assert these shapes before the Increment Gate closes.

## Documentation Deliverables
- Update `docs/4-architecture/roadmap.md`, `docs/4-architecture/knowledge-map.md`, and `docs/_current-session.md` to describe the Feature 004 consolidation and the verification runs listed above.
- Refresh operator/CLI/REST how-to guides referencing WebAuthn fixtures, trust-anchor uploads, and CLI commands (`docs/2-how-to/`, `docs/3-reference/rest-openapi.*`).
- Regenerate OpenAPI snapshots via `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"` and re-run `./gradlew --no-daemon spotlessApply check` as part of the batch gate.

## Fixtures & Sample Data
Ensure the assertion/attestation fixtures stay under version control so CLI seeds, REST controllers, and Selenium suites read the same JSON bundles referenced above; update `docs/webauthn_whats-new.md` (or the relevant how-to) when new fixtures are introduced.

## Spec DSL
```
domain_objects:
  - id: DO-004-01
    name: WebAuthnCredentialDescriptor
    fields:
      - name: aaguid
        type: uuid
      - name: keyType
        type: enum[EC2,RSA,OKP]
      - name: counter
        type: long
  - id: DO-004-02
    name: WebAuthnAssertionVector
    fields:
      - name: authenticatorData
        type: base64url
      - name: clientDataJSON
        type: base64url
      - name: signature
        type: base64url
  - id: DO-004-03
    name: WebAuthnAttestationRequest
    fields:
      - name: attestationObject
        type: base64url
      - name: clientDataJSON
        type: base64url
      - name: format
        type: enum[packed,FIDO-U2F,TPM,AndroidKey]
routes:
  - id: API-004-01
    method: POST
    path: /api/v1/fido2/evaluate
  - id: API-004-04
    method: POST
    path: /api/v1/webauthn/attest
cli_commands:
  - id: CLI-004-01
    command: maintenance fido2 evaluate
  - id: CLI-004-04
    command: maintenance fido2 attest
telemetry_events:
  - id: TE-004-01
    event: fido2.evaluate
  - id: TE-004-04
    event: fido2.attest
fixtures:
  - id: FX-004-01
    path: docs/webauthn_w3c_vectors.json
  - id: FX-004-03
    path: docs/webauthn_attestation/packed.json
ui_states:
  - id: UI-004-01
    description: Stored evaluate panel shows dropdown and status card
```

## Appendix
- The UI/JS shared helper `ui/shared/secret-fields.js` keeps the stored/inline selectors and the attestation tab toggles in sync; verify it before touching the operator console controls.
- Trust-anchor ingestion via `WebAuthnMetadataCatalogue` caches offline/inlined anchors; the CLI command `maintenance fido2 attest --metadata-anchor` toggles between manual and catalog sources. Update the knowledge map if the trust-anchor catalogue location or contents change.
