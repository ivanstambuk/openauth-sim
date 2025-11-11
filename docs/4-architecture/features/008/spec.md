# Feature 008 – EUDIW SIOPv2 Wallet Simulator

| Field | Value |
|-------|-------|
| Status | Placeholder (awaiting implementation) |
| Last updated | 2025-11-11 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/008/plan.md` |
| Linked tasks | `docs/4-architecture/features/008/tasks.md` |
| Roadmap entry | #8 – EUDIW SIOPv2 Wallet Simulator |

## Overview
Deliver a deterministic SIOPv2 wallet simulator that emits SD-JWT VC and mdoc DeviceResponse payloads for cross-device
(OpenID4VP) exchanges. This feature focuses on wallet-side request handling, consent UX, presentation composition, and
telemetry so operators can exercise verifier interoperability scenarios without external wallets.

## Clarifications
- 2025-11-11 – Placeholder spec created during Batch P2 to free the numbering for mdoc/SIOPv2; the final requirements will
  be captured once owner priorities are confirmed.
- 2025-11-11 – Previous Feature 008 (OCRA quality automation) content lives under
  `docs/4-architecture/features/new-010/legacy/008/`.

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
| FR-008-01 | Wallet parses SIOPv2 authorization requests (including HAIP encryption, Trusted Authorities, DCQL). | Simulator validates request, displays consent summary, and prepares the VP Token. | Missing parameters trigger `invalid_request`; encryption failures surface descriptive errors. | Wallet accepts malformed requests or leaks secrets. | `oid4vp.wallet.request` events capture decision, taMatch, encryption flags. | Placeholder |
| FR-008-02 | Wallet composes SD-JWT VC and/or mdoc presentations with deterministic fixtures. | CLI/REST/UI flows produce reproducible VP Tokens referencing curated disclosures/DeviceResponse payloads. | Negative fixtures (tampered disclosures) fail validation tests. | Wallet produces inconsistent payloads. | `oid4vp.wallet.present` frames log presetId, credentialModes, sanitized hashes. | Placeholder |
| FR-008-03 | Operators can toggle presets vs manual disclosure selection via CLI/REST/UI. | Presets hydrate common HAIP credential bundles while manual mode allows editing; telemetry tracks selection. | Selenium tests assert UI state + telemetry context. | Modes drift or fail to update telemetry. | Telemetry fields `presentationMode`, `credentialCount`. | Placeholder |
| FR-008-04 | Documentation/how-to guides explain wallet simulation and troubleshooting. | Docs list prerequisites, commands, expected VP Token output, telemetry hints. | `./gradlew spotlessApply check` ensures formatting; manual review ensures accuracy. | Missing docs block operators. | Doc change log references telemetry events. | Placeholder |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-008-01 | Deterministic behaviour | Repeatable VP Tokens | Integration tests comparing payload hashes | Feature 006 verifier, Feature 007 fixtures | Placeholder |
| NFR-008-02 | Observability | Telemetry/traces sanitize secrets | Telemetry capture tests | TelemetryContracts | Placeholder |
| NFR-008-03 | Performance | Wallet simulation completes <250 ms per request | :application:test benchmarks | HAIP crypto helpers | Placeholder |

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

## Telemetry & Observability
- Extend `oid4vp.wallet.*` event family with sanitized context (taMatch, credentialCount, includeTrace flag).

## Documentation Deliverables
- Update OpenID4VP how-to + session log (docs/_current-session.md) with wallet simulator instructions.

## Fixtures & Sample Data
- Share fixture catalogue with Feature 006 and Feature 007; annotate provenance metadata.

## Interface & Contract Catalogue
- **REST:** `POST /api/v1/eudiw/wallet/request`, `/wallet/present` (placeholder).
- **CLI:** `eudiw wallet request`, `eudiw wallet present` commands.
- **UI:** Operator console wallet consent panel.
- **Telemetry:** `oid4vp.wallet.request`, `oid4vp.wallet.present`.

## Spec DSL (placeholder)
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
- Detailed requirements pending owner approval; use this placeholder to anchor renumbering work.
- Legacy quality automation artifacts preserved under `docs/4-architecture/features/new-010/legacy/008/`.
