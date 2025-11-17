# Feature 007 – EUDIW ISO/IEC 18013-5 (mdoc PID) Simulator

| Field | Value |
|-------|-------|
| Status | Placeholder (awaiting implementation) |
| Last updated | 2025-11-15 |
| Owners | Ivan (project owner) |
| Linked plan | [docs/4-architecture/features/007/plan.md](docs/4-architecture/features/007/plan.md) |
| Linked tasks | [docs/4-architecture/features/007/tasks.md](docs/4-architecture/features/007/tasks.md) |
| Roadmap entry | #7 – EUDIW mdoc PID Simulator |

## Overview
Stand up a deterministic ISO/IEC 18013-5 mdoc PID simulator that mirrors the HAIP-compatible wallet experiences. The
simulator must emit DeviceResponse payloads, enforce namespace/attribute selection, and integrate with the broader EUDIW
verification stack so operators can mix-and-match mdoc and SD-JWT credentials inside OpenAuth Simulator facades. The
placeholder scope is governed by ADR-0006 (EUDIW OpenID4VP Architecture and Wallet Partitioning); while detailed
requirements still await owner approval, the simulator must stay HAIP-aligned, share fixtures/telemetry contracts with
Feature 006 (OpenID4VP), and inherit its preset catalog to avoid duplicate maintenance locations.

## Goals
- Provide deterministic mdoc PID generation and validation paths that integrate with Feature 006’s verifier.
- Support preset, stored, and manual credential modes across CLI/REST/UI facades.
- Capture telemetry, fixtures, and docs so operators can reproduce HAIP-compliant exchanges without third-party wallets.

## Non-Goals
- No DC-API or same-device exchanges (handled elsewhere).
- No new persistence schema beyond the shared credential store.
- No attempt to cover non-HAIP credential types during this placeholder stage.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-007-01 | Deterministic DeviceResponse generator yields PID payloads using curated fixtures and editable overrides. | CLI/REST/UI export identical DeviceResponse JSON for the same preset; fixtures live under `docs/eudiw/mdoc/*.json`. | Invalid overrides trigger descriptive validation errors and red telemetry. | Generator emits inconsistent payloads or leaks secrets. | `oid4vp.mdoc.generate` events carry presetId, override flags, sanitized hashes. | Spec |
| FR-007-02 | Verifier validates signatures, namespaces, device authentication, and attribute disclosure per HAIP. | REST/CLI/UI verification accept the simulator’s DeviceResponse outputs and flag tampered payloads. | Negative fixtures (bad signatures, expired timestamps) fail with `invalid_presentation`. | Missing validations allow malformed payloads. | `oid4vp.mdoc.validate` frames capture reason codes and sanitized payload hashes. | Spec |
| FR-007-03 | Operators can choose preset/stored/manual credential modes across CLI/REST/UI. | Stored mode hydrates sanitized metadata; manual mode surfaces inline editors; presets seed common PID payloads. | Selenium tests toggle modes and assert UI state + telemetry context. | Modes drift or leak secret data. | Telemetry includes `credentialMode` and preset identifiers. | Spec |
| FR-007-04 | Documentation/how-to guides explain how to generate and validate mdoc PID payloads. | Docs include prerequisites, commands, fixture references, troubleshooting steps. | `./gradlew spotlessApply check` validates formatting; manual review ensures accuracy. | Missing docs block operators. | Doc change log ties back to telemetry references. | Spec |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-007-01 | Deterministic outputs | Operators need reproducible fixtures | Integration tests compare payload hashes | Feature 006 verifier, shared fixture loaders | Spec |
| NFR-007-02 | Observability | Telemetry + verbose traces must redact PID data | Telemetry capture tests | TelemetryContracts, REST/UI logging | Spec |
| NFR-007-03 | Compatibility | Java 17 / Gradle 8.10 baseline | `./gradlew spotlessApply check` + targeted module tests | Gradle toolchain | Spec |

## UI / Interaction Mock-ups
Operator console tabs mirror the existing EUDIW layout: Evaluate drives deterministic generation, Replay validates pasted DeviceResponse payloads. ASCII sketches capture the placeholder UX so Selenium and docs can reference stable structures once implementation begins.

### Evaluate Tab (Generate PID)
```
┌──────────────────────────────────────────────┐
│ EUDIW PID – Evaluate                         │
├──────────────────────────────────────────────┤
│ Mode: (•) Inline credential  ( ) Stored preset│
│ Preset: [ pid-haip-inline ▼ ]  [ Load sample ]│
│ DeviceResponse overrides (CBOR hex textarea) │
│ Metadata: [ Profile ▼ ] [ Trusted authority ▼ ]│
│ Disclosure helpers: [ Add namespace ]        │
│ [ Generate PID presentation ]                │
│                                              │
│ Result panel (right column)                  │
│   • PID summary (name/date icons)            │
│   • VP Token JSON (read-only, scrollable)    │
│   • Trace link -> shared VerboseTraceConsole │
└──────────────────────────────────────────────┘
```

### Replay Tab (Validate PID)
```
┌──────────────────────────────────────────────┐
│ EUDIW PID – Replay                           │
├──────────────────────────────────────────────┤
│ Mode: (•) Inline VP Token  ( ) Stored preset │
│ Paste DeviceResponse JSON                    │
│ Trusted authority policy [ EU PID default ▼ ]│
│ Response mode override [ fragment ▼ ]        │
│ [ Validate PID presentation ]                │
│                                              │
│ Result panel                                 │
│   • Status badge (VALID / INVALID)           │
│   • Reason / telemetry ID                    │
│   • Collapsible claim sections per namespace │
│   • Trace link -> shared VerboseTraceConsole │
└──────────────────────────────────────────────┘
```

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-007-01 | Generate deterministic DeviceResponse payloads from presets and overrides. |
| S-007-02 | Validate DeviceResponse submissions (success + failure paths). |
| S-007-03 | Operator console + REST + CLI parity for stored/preset/manual modes. |
| S-007-04 | Documentation/how-to coverage and telemetry wiring. |

## Test Strategy
- **Core/application:** JUnit coverage for DeviceResponse generation, signature validation, namespace enforcement.
- **REST/CLI:** Contract tests covering preset/stored/manual submissions plus telemetry assertions.
- **UI:** Selenium coverage for Evaluate/Replay panels, preset hydration, and verbose trace toggles.
- **Docs:** `./gradlew spotlessApply check` plus manual verification of command snippets.
## Interface & Contract Catalogue

### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-007-01 | `MdocPidDescriptor` placeholder capturing namespace/claims + issuer metadata. | core, application |
| DO-007-02 | `MdocPidPresentation` placeholder with DeviceResponse bytes, holder binding, Trusted Authority verdict. | application, REST, CLI, UI |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-007-01 | REST `POST /api/v1/eudiw/mdoc/generate` | Generate deterministic PID DeviceResponse payloads. | Placeholder – mirrors Feature 006 contracts. |
| API-007-02 | REST `POST /api/v1/eudiw/mdoc/validate` | Validate inline/stored PID presentations. | Placeholder – telemetry + verbose trace parity with Feature 006. |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-007-01 | `eudiw mdoc generate` | Generates PID DeviceResponse fixtures (inline/preset). |
| CLI-007-02 | `eudiw mdoc validate` | Validates PID DeviceResponse payloads. |

### Telemetry Events
| ID | Event name | Fields / Notes |
|----|-----------|----------------|
| TE-007-01 | `oid4vp.mdoc.generate` | Placeholder fields: `presetId`, `credentialMode`, `status`. |
| TE-007-02 | `oid4vp.mdoc.validate` | Placeholder fields: `status`, `reasonCode`, `trustedAuthorityMatch`. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-007-01 | docs/test-vectors/eudiw/mdoc/pid-inline.json | Inline PID sample claims + namespaces. |
| FX-007-02 | docs/test-vectors/eudiw/mdoc/pid-stored.json | Stored preset metadata for MapDB seeding. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| UI-007-01 | Evaluate – inline | Inline mode keeps disclosure fields editable, result panel shows VP Token. |
| UI-007-02 | Evaluate – stored | Stored preset hides DeviceResponse secrets, exposes digest placeholders. |
| UI-007-03 | Replay – inline | Paste VP Token, see validation badge + trace link. |

## Telemetry & Observability
- Reuse `oid4vp.*` event family with `protocol=mdoc` annotations.
- Verbose traces capture sanitized payload previews and validation diagnostics.

## Documentation Deliverables
- Update OpenID4VP how-to guides with mdoc sections.
- Add fixture catalogue references under `docs/3-reference/eudiw/`.

## Fixtures & Sample Data
- Seed curated PID fixtures with deterministic salts.
- Document fixture IDs and provenance metadata.


## Spec DSL
```yaml
feature: F-007
protocol: eudiw-mdoc
modes:
  - preset
  - stored
  - manual
telemetry:
  - event: oid4vp.mdoc.generate
    fields: [presetId, credentialMode, status, reasonCode]
  - event: oid4vp.mdoc.validate
    fields: [status, reasonCode, credentialMode]
```

## Appendix
- Pending owner approval for detailed requirements.
- Legacy documentation for the prior Feature 007 lives under `docs/4-architecture/features/new-010/legacy/007/`.
