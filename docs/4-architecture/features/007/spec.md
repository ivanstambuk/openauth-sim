# Feature 007 – EUDIW ISO/IEC 18013-5 (mdoc PID) Simulator

| Field | Value |
|-------|-------|
| Status | Placeholder (awaiting implementation) |
| Last updated | 2025-11-11 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/007/plan.md` |
| Linked tasks | `docs/4-architecture/features/007/tasks.md` |
| Roadmap entry | #7 – EUDIW mdoc PID Simulator |

## Overview
Stand up a deterministic ISO/IEC 18013-5 mdoc PID simulator that mirrors the HAIP-compatible wallet experiences. The
simulator must emit DeviceResponse payloads, enforce namespace/attribute selection, and integrate with the broader EUDIW
verification stack so operators can mix-and-match mdoc and SD-JWT credentials inside OpenAuth Simulator facades.

## Clarifications
- 2025-11-11 – Placeholder specification added during Batch P2 renumbering; the authoritative requirements remain open
  for owner approval, but the simulator must align with the HAIP profile and share fixtures with Feature 006 (OpenID4VP).
- 2025-11-11 – All references to the former documentation-centric Feature 007 now reside under
  `docs/4-architecture/features/new-010/legacy/007/` per the session log (docs/_current-session.md).

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
| FR-007-01 | Deterministic DeviceResponse generator yields PID payloads using curated fixtures and editable overrides. | CLI/REST/UI export identical DeviceResponse JSON for the same preset; fixtures live under `docs/eudiw/mdoc/*.json`. | Invalid overrides trigger descriptive validation errors and red telemetry. | Generator emits inconsistent payloads or leaks secrets. | `oid4vp.mdoc.generate` events carry presetId, override flags, sanitized hashes. | Placeholder – to be expanded with final spec. |
| FR-007-02 | Verifier validates signatures, namespaces, device authentication, and attribute disclosure per HAIP. | REST/CLI/UI verification accept the simulator’s DeviceResponse outputs and flag tampered payloads. | Negative fixtures (bad signatures, expired timestamps) fail with `invalid_presentation`. | Missing validations allow malformed payloads. | `oid4vp.mdoc.validate` frames capture reason codes and sanitized payload hashes. | Placeholder. |
| FR-007-03 | Operators can choose preset/stored/manual credential modes across CLI/REST/UI. | Stored mode hydrates sanitized metadata; manual mode surfaces inline editors; presets seed common PID payloads. | Selenium tests toggle modes and assert UI state + telemetry context. | Modes drift or leak secret data. | Telemetry includes `credentialMode` and preset identifiers. | Placeholder. |
| FR-007-04 | Documentation/how-to guides explain how to generate and validate mdoc PID payloads. | Docs include prerequisites, commands, fixture references, troubleshooting steps. | `./gradlew spotlessApply check` validates formatting; manual review ensures accuracy. | Missing docs block operators. | Doc change log ties back to telemetry references. | Placeholder. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-007-01 | Deterministic outputs | Operators need reproducible fixtures | Integration tests compare payload hashes | Feature 006 verifier, shared fixture loaders | Placeholder |
| NFR-007-02 | Observability | Telemetry + verbose traces must redact PID data | Telemetry capture tests | TelemetryContracts, REST/UI logging | Placeholder |
| NFR-007-03 | Compatibility | Java 17 / Gradle 8.10 baseline | `./gradlew spotlessApply check` + targeted module tests | Gradle toolchain | Placeholder |

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

## Telemetry & Observability
- Reuse `oid4vp.*` event family with `protocol=mdoc` annotations.
- Verbose traces capture sanitized payload previews and validation diagnostics.

## Documentation Deliverables
- Update OpenID4VP how-to guides with mdoc sections.
- Add fixture catalogue references under `docs/3-reference/eudiw/`.

## Fixtures & Sample Data
- Seed curated PID fixtures with deterministic salts.
- Document fixture IDs and provenance metadata.

## Interface & Contract Catalogue
- **REST:** `POST /api/v1/eudiw/mdoc/generate`, `POST /api/v1/eudiw/mdoc/validate` (placeholder).
- **CLI:** `eudiw mdoc generate`, `eudiw mdoc validate` (placeholder).
- **UI:** Operator console EUDIW tab (mdoc mode).
- **Telemetry:** `oid4vp.mdoc.generate`, `oid4vp.mdoc.validate` events.

## Spec DSL (placeholder)
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
