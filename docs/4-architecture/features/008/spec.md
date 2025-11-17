# Feature 008 – EUDIW SIOPv2 Wallet Simulator

| Field | Value |
|-------|-------|
| Status | Placeholder (awaiting implementation) |
| Last updated | 2025-11-15 |
| Owners | Ivan (project owner) |
| Linked plan | [docs/4-architecture/features/008/plan.md](docs/4-architecture/features/008/plan.md) |
| Linked tasks | [docs/4-architecture/features/008/tasks.md](docs/4-architecture/features/008/tasks.md) |
| Roadmap entry | #8 – EUDIW SIOPv2 Wallet Simulator |

## Overview
Deliver a deterministic SIOPv2 wallet simulator that emits SD-JWT VC and mdoc DeviceResponse payloads for cross-device
(OpenID4VP) exchanges. This feature focuses on wallet-side request handling, consent UX, presentation composition, and
telemetry so operators can exercise verifier interoperability scenarios without external wallets. The placeholder scope
is governed by ADR-0006 (EUDIW OpenID4VP Architecture and Wallet Partitioning); final requirements remain pending owner
approval, but the wallet must stay HAIP-aligned, share fixtures/telemetry contracts with Features 006/007, and inherit
their Trusted Authority metadata to avoid duplicate maintenance locations.

## Goals
- Simulate SIOPv2 wallet journeys (authorization, consent, presentation) for HAIP-aligned OpenID4VP requests.
- Provide CLI/REST/UI entry points for wallet orchestration and diagnostics.
- Share fixtures, telemetry, and Trusted Authority metadata with Features 006/007.

## Non-Goals
- No DC-API or same-device wallet protocols.
- No production credential issuance—fixtures remain synthetic.
- No persistence beyond temp in-memory stores for wallet sessions.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-008-01 | Wallet parses SIOPv2 authorization requests (including HAIP encryption, Trusted Authorities, DCQL). | Simulator validates request, displays consent summary, and prepares the VP Token. | Missing parameters trigger `invalid_request`; encryption failures surface descriptive errors. | Wallet accepts malformed requests or leaks secrets. | `oid4vp.wallet.request` events capture decision, taMatch, encryption flags. | Spec |
| FR-008-02 | Wallet composes SD-JWT VC and/or mdoc presentations with deterministic fixtures. | CLI/REST/UI flows produce reproducible VP Tokens referencing curated disclosures/DeviceResponse payloads. | Negative fixtures (tampered disclosures) fail validation tests. | Wallet produces inconsistent payloads. | `oid4vp.wallet.present` frames log presetId, credentialModes, sanitized hashes. | Spec |
| FR-008-03 | Operators can toggle presets vs manual disclosure selection via CLI/REST/UI. | Presets hydrate common HAIP credential bundles while manual mode allows editing; telemetry tracks selection. | Selenium tests assert UI state + telemetry context. | Modes drift or fail to update telemetry. | Telemetry fields `presentationMode`, `credentialCount`. | Spec |
| FR-008-04 | Documentation/how-to guides explain wallet simulation and troubleshooting. | Docs list prerequisites, commands, expected VP Token output, telemetry hints. | `./gradlew spotlessApply check` ensures formatting; manual review ensures accuracy. | Missing docs block operators. | Doc change log references telemetry events. | Spec |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-008-01 | Deterministic behaviour across SD-JWT + mdoc payloads | Repeatable VP Tokens | Integration tests comparing payload hashes | Feature 006 verifier, Feature 007 fixtures | Spec |
| NFR-008-02 | Observability (telemetry/traces sanitize secrets) | Prevent PII leakage | Telemetry capture tests | TelemetryContracts | Spec |
| NFR-008-03 | Performance (<250 ms per simulation) | Keep demos responsive | `:application:test` benchmarks | HAIP crypto helpers | Spec |

## UI / Interaction Mock-ups
Placeholder ASCII sketches keep the template structure intact until the final UX is confirmed.

### Consent / Presentation Composition
```
┌─────────────────────────────────────┐
│ SIOPv2 Wallet – Consent             │
├─────────────────────────────────────┤
│ Request summary                     │
│   DCQL preset: pid-haip             │
│   Trusted authority: EU PID (aki)   │
│                                     │
│ Credential mode                     │
│   (•) Stored preset   ( ) Inline    │
│   Preset: [ pid-sdjwt-01 ▼ ]        │
│ Inline disclosures (JSON)           │
│ [ Add disclosure ] [ Remove ]       │
│                                     │
│ Response mode                        │
│   [ fragment ▼ ]                    │
│                                     │
│ [ Compose VP Token ]                │
└─────────────────────────────────────┘
```

### VP Token Preview / Replay
```
┌─────────────────────────────────────┐
│ SIOPv2 Wallet – Result              │
├─────────────────────────────────────┤
│ Status: READY                       │
│ vp_token (read-only textarea)       │
│ presentation_submission JSON        │
│ Trusted authority decision: MATCH   │
│ Trace ID: ABCD-1234 (link)          │
│ [ Download JSON ] [ Copy vp_token ] │
└─────────────────────────────────────┘
```

## Branch & Scenario Matrix
| Scenario ID | Description |
|-------------|-------------|
| S-008-01 | Authorization request parsing & consent UI |
| S-008-02 | Presentation composition (SD-JWT + mdoc) |
| S-008-03 | CLI/REST/UI preset/manual parity |
| S-008-04 | Documentation + telemetry coverage |

## Test Strategy
- **Core/application:** Unit tests for authorization parsing, wallet state machines, deterministic presentation builders.
- **REST/CLI:** Contract tests ensuring CLI commands and REST endpoints wrap the wallet orchestrator correctly.
- **UI:** Selenium flows for consent dialogs, preset selection, verbose traces.
- **Docs:** `./gradlew spotlessApply check` with manual verification of commands.
## Interface & Contract Catalogue

### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-008-01 | `SioPv2WalletRequest` placeholder capturing consent state. | application, REST |
| DO-008-02 | `SioPv2WalletPresentation` placeholder for VP Token / DeviceResponse output. | application, REST, CLI, UI |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-008-01 | REST `POST /api/v1/eudiw/wallet/request` | Create consent summaries for a DCQL request. | Placeholder mirrors Feature 006. |
| API-008-02 | REST `POST /api/v1/eudiw/wallet/present` | Compose VP Tokens (SD-JWT / mdoc). | Placeholder. |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-008-01 | `eudiw wallet request` | Shows consent summary for a request. |
| CLI-008-02 | `eudiw wallet present` | Emits VP Tokens for selected presets. |

### Telemetry Events
| ID | Event name | Notes |
|----|-----------|-------|
| TE-008-01 | `oid4vp.wallet.request` | Placeholder fields: decision, taMatch. |
| TE-008-02 | `oid4vp.wallet.present` | Placeholder fields: presetId, credentialMode. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-008-01 | docs/test-vectors/eudiw/sio/consent.json | Sample request metadata. |
| FX-008-02 | docs/test-vectors/eudiw/sio/presentations/pid-sdjwt.json | VP Token seed. |

### UI States
| ID | State | Trigger |
|----|-------|---------|
| UI-008-01 | Consent modal open | Operator views request summary + toggles presets. |
| UI-008-02 | Result preview | VP Token ready; copy/download controls enabled. |

## Telemetry & Observability
- Extend `oid4vp.wallet.*` event family with sanitized context (taMatch, credentialCount, includeTrace flag).

## Documentation Deliverables
- Update OpenID4VP how-to + session log ([docs/_current-session.md](docs/_current-session.md)) with wallet simulator instructions.

## Fixtures & Sample Data
- Share fixture catalogue with Feature 006 and Feature 007; annotate provenance metadata.


## Spec DSL
```yaml
feature: F-008
protocol: eudiw-wallet
flows:
  - authorization
  - presentation
fixtures:
  - id: haip-wallet-sdjwt
    type: sd-jwt
  - id: haip-wallet-mdoc
    type: mdoc
```

## Appendix
- Detailed requirements pending owner approval; this placeholder captures the intended wallet scope until the feature is implemented.
