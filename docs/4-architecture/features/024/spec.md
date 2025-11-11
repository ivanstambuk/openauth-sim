# Feature 024 – FIDO2/WebAuthn Operator Support

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/024/plan.md` |
| Linked tasks | `docs/4-architecture/features/024/tasks.md` |
| Roadmap entry | #4 – FIDO2/WebAuthn Assertions |

## Overview
Implement an end-to-end FIDO2/WebAuthn authentication verification path that mirrors HOTP/OCRA ergonomics: deterministic
fixtures (W3C Level 3 §16 + synthetic JSONL bundle), shared persistence descriptors, application services, CLI/REST
facades, operator UI evaluate/replay panels, seeding/preset controls, and telemetry parity. Issuance remains out of
scope; focus is on assertion verification, diagnostics, and replay.

## Clarifications
- 2025-10-09 – Feature ships core + persistence + application + CLI/REST + UI as a single slice (Option A).
- 2025-10-09 – Stored + inline evaluation plus replay diagnostics must launch together (Option A).
- 2025-10-09 – Verify W3C §16 vectors first, then immediately cover the synthetic JSONL bundle (owner directive).
- 2025-10-09 – Seed button loads a curated subset (one per algorithm/flag) and is visible only in stored mode (Option A).
- 2025-10-11 – Hide/disable seeding whenever inline-only panels are active to avoid operator confusion (bugfix directive).
- 2025-10-13 – Router must emit only shared `protocol/tab/mode` params; FIDO2 console must parse legacy params for
  backwards compatibility (routing directive).
- 2025-10-14 – Evaluation card clamps status column to 600 px and scrolls payloads horizontally (UI directive).

## Goals
- G-024-01 – Provide deterministic WebAuthn verification against both W3C and synthetic vectors.
- G-024-02 – Offer CLI/REST/operator UI evaluate/replay flows with telemetry parity and seeding/preset helpers.
- G-024-03 – Maintain fixture catalogues, documentation, and telemetry standards for future authenticator work.

## Non-Goals
- N-024-01 – Registration/attestation ceremonies or authenticator provisioning.
- N-024-02 – Hardware/CTAP emulation.
- N-024-03 – Dependency upgrades outside existing build constraints.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-024-01 | Implement RFC 8259/WebAuthn §16 assertion verification (flags, RP hash, origin/type, counter, signature) using W3C + synthetic fixtures. | Core tests pass for success/failure cases; mutation/ArchUnit stay green. | Fixture loader attempts invalid origins/flags; tests expect descriptive errors. | `:core:test` fails or verifier accepts invalid vectors. | `fido2.evaluate` frames sanitized via TelemetryContracts. | Clarifications. |
| FR-024-02 | Persist WebAuthn credentials in schema-v1 MapDB with metadata (alg, counters, UV, origins) compatible with HOTP/TOTP/OCRA. | Integration tests load mixed credential types; seeding utilities append curated entries. | CLI/REST evaluations update counters atomically; replay leaves counters unchanged. | MapDB migrations required or counters drift. | Telemetry records hashed IDs + counters. | Clarifications. |
| FR-024-03 | Application services expose stored/inline evaluate + replay flows with shared telemetry, verbose traces, and error reasons. | JUnit tests cover success, UV failure, counter regression, key mismatch, replay diagnostics. | Negative tests assert sanitized errors. | Services throw `UnsupportedOperationException` or telemetry missing. | `fido2.evaluate`, `fido2.replay`. | Clarifications. |
| FR-024-04 | CLI + REST facades provide evaluate/replay commands/endpoints, update OpenAPI specs, and honour preset/seed flows. | Picocli + MockMvc/OpenAPI tests cover stored/inline/replay, JWK/PEM/COSE inputs. | Invalid payload tests ensure proper 4xx messages. | CLI/REST fail or OpenAPI snapshot outdated. | Telemetry parity maintained. | Clarifications. |
| FR-024-05 | Operator console tab renders stored/inline evaluate + replay panels, seeding, preset loaders, router parity, status width clamp, query-parameter sync. | Selenium tests cover seeding, inline defaults, replay auto-fill, router state, accessibility. | Negative flows confirm seeding hidden inline, router preserves shared params. | UI missing flows or router diverges. | Reuses REST telemetry events. | Clarifications. |
| FR-024-06 | Maintain fixture catalogues (`docs/webauthn_w3c_vectors.json`, `docs/webauthn_assertion_vectors.json`), loaders, and documentation (how-to, roadmap, knowledge map). | Loader tests iterate W3C + synthetic datasets; docs reference presets. | Failing tests when fixture missing, doc lint ensures updates. | Missing fixtures or stale docs. | n/a | Clarifications. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-024-01 | Security | Secrets/private keys stored encrypted where supported; telemetry redacts key material. | Tests + telemetry lint; gitleaks allowances documented. | MapDB, TelemetryContracts. | Clarifications. |
| NFR-024-02 | Compatibility | Schema-v1 compatibility with HOTP/TOTP/OCRA; router default parameters stable. | Integration tests + Selenium router suite. | Persistence + UI modules. | Clarifications. |
| NFR-024-03 | Quality | `spotlessApply`, `qualityGate`, ArchUnit, reflectionScan remain green. | CI pipeline. | Gradle tooling. | Spec. |

## UI / Interaction Mock-ups
```
[FIDO2/WebAuthn tab] [ Info ⓘ ]
Evaluate (Stored | Inline)
+ Stored form with credential dropdown, curated seed button (stored mode only), auto-filled metadata, verify button.
+ Inline form with JSON assertion textarea, key material tabs (COSE/JWK/PEM), RP/origin fields.
Replay panel mirrors Evaluate but never mutates counters.
Status card clamps width to 600 px and scrolls long payloads.
```

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-024-01 | Core verification engine + fixtures. |
| S-024-02 | Persistence + application services + telemetry. |
| S-024-03 | CLI/REST evaluate & replay flows (OpenAPI updated). |
| S-024-04 | Operator console stored/inline/replay UI + router/seeding tweaks. |
| S-024-05 | Fixture catalogues + documentation/roadmap updates.

## Test Strategy
- **Core:** JUnit + mutation tests for verification engine using W3C and synthetic datasets.
- **Persistence:** MapDB integration tests mixing credential types; seeding tests verifying curated entries.
- **Application:** Service tests for evaluation/replay/telemetry, key-format normalization.
- **CLI:** Picocli integration tests covering stored/inline/replay, seed/preset commands.
- **REST:** MockMvc + OpenAPI snapshots for evaluate/replay + sample endpoints.
- **UI:** Selenium/system tests for stored/inline/replay flows, router parity, seeding visibility, status clamp, accessibility.
- **Docs:** `./gradlew spotlessApply check` + doc lint after updates.

## Interface & Contract Catalogue
### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-024-01 | `WebAuthnCredentialDescriptor` (AAGUID, key type, counters, UV flag, origins). | core, application |
| DO-024-02 | `WebAuthnAssertionVector` (authenticator data, client data JSON, signature, key material). | docs, core, REST |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-024-01 | REST POST `/api/v1/fido2/evaluate` | Stored + inline evaluation. |
| API-024-02 | REST POST `/api/v1/fido2/replay` | Non-mutating replay diagnostics. |
| API-024-03 | REST GET `/api/v1/fido2/credentials` | Stored credential catalogue for UI. |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-024-01 | `maintenance fido2 evaluate` | Evaluates stored/inline assertions (COSE/JWK/PEM). |
| CLI-024-02 | `maintenance fido2 replay` | Non-mutating replay diagnostics. |
| CLI-024-03 | `maintenance fido2 seed` | Loads curated stored credentials. |

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|-----------|---------------------------|
| TE-024-01 | `fido2.evaluate` | `credentialIdHash`, `mode`, `result`, sanitized reason codes. |
| TE-024-02 | `fido2.replay` | `credentialIdHash`, `mode`, `result`. |
| TE-024-03 | `fido2.seed` | `addedCount`, `presetIdsHash`. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-024-01 | `docs/webauthn_w3c_vectors.json` | Converted W3C §16 vectors. |
| FX-024-02 | `docs/webauthn_assertion_vectors.json` | Synthetic JSONL vector bundle. |
| FX-024-03 | `rest-api/src/test/resources/fido2/*.json` | REST/Selenium request payloads. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| UI-024-01 | Stored evaluate panel | Dropdown/seed visible; verifies assertion, increments counter. |
| UI-024-02 | Inline evaluate panel | Inline assertion+key inputs; defaults active on load. |
| UI-024-03 | Replay panel | Stored/inline replay with auto-filled sample data; non-mutating. |

## Telemetry & Observability
- Telemetry routed through `TelemetryContracts`; no raw key material logged.
- Operator console reuses REST telemetry; verbose traces reference fixture IDs for troubleshooting.

## Documentation Deliverables
- Update operator/CLI/REST how-to guides, roadmap, knowledge map, and OpenAPI docs with WebAuthn workflows and fixture references.

## Fixtures & Sample Data
- FX-024-01..03 plus curated seed presets per algorithm/flag combination.

## Spec DSL
```
domain_objects:
  - id: DO-024-01
    name: WebAuthnCredentialDescriptor
    fields:
      - name: aaguid
        type: uuid
      - name: keyType
        type: enum[EC2,RSA,OKP]
      - name: counter
        type: long
  - id: DO-024-02
    name: WebAuthnAssertionVector
    fields:
      - name: authenticatorData
        type: base64url
      - name: clientDataJSON
        type: base64url
      - name: signature
        type: base64url
telemetry_events:
  - id: TE-024-01
    event: fido2.evaluate
fixtures:
  - id: FX-024-01
    path: docs/webauthn_w3c_vectors.json
ui_states:
  - id: UI-024-01
    description: Stored evaluate panel with seeding
```

## Appendix
_None._
