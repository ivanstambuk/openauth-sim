# Feature 001 – HOTP Simulator & Tooling

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-13 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/001/plan.md` |
| Linked tasks | `docs/4-architecture/features/001/tasks.md` |
| Roadmap entry | #1 – HOTP Simulator & Tooling |

## Overview
Add RFC 4226 HOTP capability across the simulator so operators can evaluate stored and inline HOTP credentials in
parallel with OCRA. Scope includes core domain/fixtures, shared persistence, application/telemetry services, CLI + REST
facades, operator UI flows, replay tooling, and documentation. This feature ships a single end-to-end slice spanning
core → application → CLI/REST → operator console; issuance remains out of scope.

## Goals
- G-001-01 – Provide deterministic HOTP evaluation flows in core/application/CLI/REST with telemetry parity.
- G-001-02 – Extend operator console with HOTP stored/inline evaluation + replay and deterministic seeding.
- G-001-03 – Publish fixtures/catalogues/documentation so HOTP vectors remain shareable across modules.

## Non-Goals
- N-001-01 – HOTP issuance/provisioning remains deferred; the operator console remains evaluation-only (no issuance UI).
- N-001-02 – Schema migrations/new stores.
- N-001-03 – Adding non-RFC 4226 OTP variants (e.g., TOTP).

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-001-01 | Implement RFC 4226 HOTP generator/validator with counter rollover, digit variants, and shared fixtures. | Core tests pass for counters 0–9 (6/8 digits) using catalogued vectors. | Mutation/ArchUnit verification ensures correct domain boundaries. | `:core:test` fails or fixtures mismatched. | `hotp.evaluate` events carry sanitized fields. | Spec. |
| FR-001-02 | Persist HOTP credentials alongside OCRA in schema-v1 MapDB without migrations. | Integration tests load OCRA + HOTP records via `CredentialStoreFactory`. | CLI/REST evaluation tests confirm counters update persistently. | Persistence mismatch or migrations required. | `hotp.issue`/`hotp.evaluate` telemetry share metadata. | Spec. |
| FR-001-03 | Add CLI commands for HOTP import/list/evaluate with telemetry parity. | Picocli tests assert sanitized output, telemetry frames, exit codes. | Invalid inputs raise descriptive errors. | CLI command fails or telemetry missing. | `hotp.evaluate` CLI path. | Spec. |
| FR-001-04 | Expose REST endpoints for HOTP evaluation (stored + inline) and replay, updating OpenAPI snapshots. | MockMvc/OpenAPI tests cover POST `/api/v1/hotp/evaluate` + `/replay`. | Stored replay leaves counters unchanged. | REST tests fail or counters mutate. | `hotp.evaluate`/`hotp.replay`. | Spec. |
| FR-001-05 | Add operator UI HOTP stored/inline evaluation plus replay flows with telemetry parity. | Selenium tests run stored + inline flows, seeding control, replay UI. | Accessibility checks ensure keyboard focus + aria semantics. | UI missing flows or telemetry inconsistent. | relies on REST telemetry events. | Spec. |
| FR-001-06 | Provide deterministic HOTP fixture catalogue (`docs/hotp_validation_vectors.json`) and shared loader consumed by core/CLI/REST/UI. | Loader feeds tests/presets; docs reference vector IDs. | Missing vector results cause failing tests/docs warnings. | CLI/REST/ UI fixtures inconsistent. | n/a | Spec. |
| FR-001-07 | Update documentation (how-to, roadmap, knowledge map) to reflect HOTP availability, seeding, replay, and telemetry. | Docs mention HOTP flows, seeding controls, fixture catalogue. | Spotless/doc lint passes. | Documentation gaps flagged during review. | n/a | Spec. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-001-01 | Security | Secrets encrypted at rest when MapDB encryption enabled; telemetry redacts OTP/secret fields. | Unit/integration tests confirm redaction + encryption guardrails. | MapDB, TelemetryContracts. | Spec. |
| NFR-001-02 | Compatibility | Schema-v1 metadata remains backward compatible; no migrations triggered. | Regression tests run with legacy stores. | Core/application persistence. | Spec. |
| NFR-001-03 | Quality | `./gradlew spotlessApply check` + quality gate stay green; ArchUnit/PMD/SpotBugs cover new modules. | CI build history. | Gradle tooling. | Spec. |

## UI / Interaction Mock-ups
```
HOTP Evaluate Panel (Stored)
-------------------------------------------
| Stored credential: [ dropdown ] [Seed]  |
| Counter (read-only)   OTP result card   |
-------------------------------------------
Inline Panel mirrors OCRA layout (secret, counter input, OTP output). Replay panel reuses sample data + telemetry hints.
HOTP stored + inline evaluations live inside the unified operator console tab and reuse existing REST telemetry events.
```

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-001-01 | Core HOTP domain + shared persistence fixtures. |
| S-001-02 | Application services + CLI/REST telemetry parity. |
| S-001-03 | REST endpoints + OpenAPI snapshots for evaluation/replay. |
| S-001-04 | Operator UI stored/inline/replay flows + seeding. |
| S-001-05 | Fixture catalogue + documentation updates.

## Test Strategy
- **Core:** RFC 4226 vector tests + mutation guard.
- **Application:** Service + telemetry adapters verified via JUnit.
- **CLI:** Picocli tests covering import/list/evaluate/replay.
- **REST:** MockMvc + OpenAPI snapshot tests for evaluation/replay.
- **UI:** Selenium/system tests for HOTP stored/inline/replay flows, seeding control, accessibility.
- **Docs:** Spotless/doc lint after updates.

## Interface & Contract Catalogue
### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-001-01 | `HotpCredentialDescriptor` (suite metadata, counters, digits). | core, application |
| DO-001-02 | `HotpEvaluationRequest` (inline/stored payload). | REST, CLI, application |
| DO-001-03 | `HotpReplayRequest` (non-mutating evaluation). | REST, application |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-001-01 | REST POST `/api/v1/hotp/evaluate` | Stored + inline evaluations. | OpenAPI snapshot updated. |
| API-001-02 | REST POST `/api/v1/hotp/replay` | Non-mutating replay. | Counters unchanged. |
| API-001-03 | REST GET `/api/v1/hotp/credentials` | Stored credential listing for UI. | Shared with seeding. |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-001-01 | `maintenance hotp import` | Loads HOTP credentials. |
| CLI-001-02 | `maintenance hotp evaluate` | Inline/stored evaluation. |
| CLI-001-03 | `maintenance hotp replay` | Non-mutating evaluation. |

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|-----------|---------------------------|
| TE-001-01 | `hotp.evaluate` | `credentialIdHash`, `mode`, `result`, sanitized inputs. |
| TE-001-02 | `hotp.issue` | `credentialIdHash`, `result`. |
| TE-001-03 | `hotp.replay` | `credentialIdHash`, `mode`, `result`. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-001-01 | `docs/hotp_validation_vectors.json` | Canonical vectors for tests/presets. |
| FX-001-02 | `core/src/test/resources/hotp/*.json` | Loader inputs for unit tests. |
| FX-001-03 | `rest-api/src/test/resources/hotp/sample-requests/*.json` | REST test payloads. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| UI-001-01 | Stored evaluation | Select stored credential, generate OTP, counter increments. |
| UI-001-02 | Inline evaluation | Enter shared secret/counter; OTP displayed without persistence. |
| UI-001-03 | Replay | Evaluate stored/inline without counter increment. |

## Telemetry & Observability
- Telemetry emits via `TelemetryContracts` with sanitized hashes, matching OCRA parity.
- Application layer owns HOTP counter persistence and telemetry metadata; stored evaluations increment the moving factor
  immediately after successful evaluation.
- Replay path logs non-mutating evaluations for audit.

## Documentation Deliverables
- Update operator/CLI/REST how-to guides with HOTP usage, seeding, replay.
- Refresh roadmap/knowledge map.

## Fixtures & Sample Data
See FX-001-01..03; no extra fixtures required beyond the shared catalogue.

## Spec DSL
```
domain_objects:
  - id: DO-001-01
    name: HotpCredentialDescriptor
    fields:
      - name: digits
        type: enum[6,8]
      - name: counter
        type: long
  - id: DO-001-02
    name: HotpEvaluationRequest
    fields:
      - name: credentialId
        type: string
      - name: mode
        type: enum[stored,inline]
cli_commands:
  - id: CLI-001-01
    command: maintenance hotp evaluate
telemetry_events:
  - id: TE-001-01
    event: hotp.evaluate
fixtures:
  - id: FX-001-01
    path: docs/hotp_validation_vectors.json
ui_states:
  - id: UI-001-01
    description: HOTP stored evaluation panel
```

## Appendix
_None._
