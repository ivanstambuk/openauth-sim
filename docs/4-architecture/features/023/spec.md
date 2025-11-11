# Feature 023 – TOTP Operator Support

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/023/plan.md` |
| Linked tasks | `docs/4-architecture/features/023/tasks.md` |
| Roadmap entry | #3 – HOTP/TOTP Simulator & Tooling |

## Overview
Add RFC 6238 TOTP support across the simulator so operators can evaluate stored and inline TOTP credentials (plus replay)
alongside existing HOTP/OCRA flows. Scope includes core domain, persistence descriptors, application services, CLI/REST
facades, operator UI panels, shared fixtures, telemetry parity, and documentation. Issuance remains out of scope.

## Clarifications
- 2025-10-08 – Launch delivers an end-to-end slice (core → application → CLI/REST/UI) in a single feature (Option A).
- 2025-10-08 – Support SHA-1, SHA-256, SHA-512, 6/8 digits, and at least 30 s/60 s steps at launch (Option A).
- 2025-10-08 – Reuse schema-v1 MapDB store; no new schema (Option A).
- 2025-10-08 – Evaluation exposes ± step drift windows + timestamp overrides; replay uses `/api/v1/totp/replay` with
  non-mutating diagnostics (Option A).
- 2025-10-08 – CLI + Java facades ship alongside REST/UI surfaces.
- 2025-10-08 – Telemetry emits via `totp.evaluate`/`totp.replay` adapters.
- 2025-10-11 – Operator console Evaluate tab defaults to inline mode for TOTP; replay removes “Load sample data” button
  in favour of auto-applied samples (user directives).
- 2025-10-28 – TOTP panels include “Use current Unix seconds” toggles and “Reset to now” helpers quantised to the step size.

## Goals
- G-023-01 – Provide deterministic TOTP evaluation/replay with telemetry parity across core/application/CLI/REST.
- G-023-02 – Extend operator console with TOTP stored/inline/replay flows, presets, seeding, auto-fill helpers.
- G-023-03 – Publish TOTP fixture catalogue + documentation updates.

## Non-Goals
- N-023-01 – Issuance/enrollment flows.
- N-023-02 – New schema or data migrations.
- N-023-03 – Non-RFC 6238 OTP variants.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-023-01 | Implement RFC 6238 TOTP generator/validator across SHA-1/256/512, 6/8 digits, and configurable steps with drift windows. | Core tests with fixture catalogue pass; mutation/ArchUnit green. | Drift boundary tests ensure rejection when outside windows. | `:core:test` fails or drift logic incorrect. | `totp.evaluate` events sanitized via TelemetryContracts. | Clarifications. |
| FR-023-02 | Persist TOTP credentials within schema-v1 MapDB with defaults (`SHA1`, `6`, `30s`, ±1 steps). | Integration tests mix HOTP/OCRA/TOTP descriptors. | CLI/REST evaluation updates counters/timestamps as expected. | Schema diff or migration required. | Telemetry includes credential hashes only. | Clarifications. |
| FR-023-03 | Application services handle stored/inline evaluation, timestamp overrides, drift windows, replay (non-mutating), telemetry. | JUnit tests cover success/error paths, replay non-mutating. | Negative tests reject invalid inputs or drift. | Replay mutates counters or telemetry missing. | `totp.evaluate`/`totp.replay`. | Clarifications. |
| FR-023-04 | CLI commands import/list/evaluate/replay TOTP credentials with drift/timestamp options. | Picocli tests assert outputs + telemetry, covering stored/inline/replay. | Invalid input tests produce descriptive errors. | CLI command fails or telemetry absent. | Telemetry parallels HOTP/OCRA. | Clarifications. |
| FR-023-05 | REST endpoints (evaluate inline/stored, replay) expose schema + OpenAPI updates, accept drift/timestamp overrides, return OTP payloads. | MockMvc + OpenAPI tests pass; replay non-mutating. | Negative tests cover invalid drift, timestamp overrides. | REST tests fail or counters mutate. | `totp.evaluate`/`totp.replay`. | Clarifications. |
| FR-023-06 | Operator console adds TOTP stored/inline/replay panels with presets, seeding, auto-fill toggles, inline-default Evaluate tab. | Selenium tests cover stored/inline/replay, seeding, timestamp controls, auto-applied samples. | Accessibility checks (aria roles, keyboard order). | UI missing flows or telemetry inconsistent. | Reuses REST telemetry. | Clarifications. |
| FR-023-07 | Publish `docs/totp_validation_vectors.json` and shared loader; update CLI/REST/UI docs/how-to. | Fixture loader feeds tests/presets; docs reference vector IDs. | Missing vector causes tests/docs failures. | n/a | Clarifications. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-023-01 | Security | Secrets remain encrypted at rest; telemetry redacts OTP/secrets. | Tests + telemetry linting. | MapDB, TelemetryContracts. | Clarifications. |
| NFR-023-02 | Compatibility | schema-v1 remains backward compatible; no migrations. | Legacy stores load successfully. | Persistence module. | Clarifications. |
| NFR-023-03 | Quality | `./gradlew qualityGate` + `spotlessApply` stay green. | CI pipeline. | Gradle tooling. | Spec. |

## UI / Interaction Mock-ups
```
TOTP Evaluate (Inline default)
-----------------------------
[ Inline form | Stored form ]
Inline fields: Secret, Algorithm, Digits, Step, Drift ±, Timestamp (with “Use current Unix seconds” toggle and “Reset to now”).
Stored fields: Credential dropdown, auto-filled counter/timestamp preview.
Replay panel mirrors Evaluate but never mutates counters.
```

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-023-01 | Core domain + persistence descriptors ready for TOTP. |
| S-023-02 | Application services + telemetry + CLI flows implemented. |
| S-023-03 | REST evaluation/replay endpoints with OpenAPI parity. |
| S-023-04 | Operator console stored/inline/replay UX parity (seeding, auto-fill). |
| S-023-05 | Fixture catalogue + documentation updates complete.

## Test Strategy
- **Core:** RFC 6238 vector tests + mutation/ArchUnit.
- **Persistence:** Integration tests mixing HOTP/OCRA/TOTP descriptors.
- **Application:** JUnit coverage for stored/inline/replay flows + telemetry.
- **CLI:** Picocli integration tests for evaluation/replay commands.
- **REST:** MockMvc + OpenAPI snapshot tests for evaluate/replay endpoints.
- **UI:** Selenium tests for stored/inline/replay flows, seeding, timestamp toggles, accessibility/regressions.
- **Docs:** `./gradlew spotlessApply check` after doc updates.

## Interface & Contract Catalogue
### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-023-01 | `TotpCredentialDescriptor` (algorithm, digits, stepSeconds, drift). | core, application |
| DO-023-02 | `TotpEvaluationRequest`/`ReplayRequest` (stored/inline payload). | REST, CLI, application |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-023-01 | REST POST `/api/v1/totp/evaluate` | Evaluates stored/inline credentials with drift/timestamp overrides. |
| API-023-02 | REST POST `/api/v1/totp/replay` | Non-mutating replay diagnostics. |
| API-023-03 | REST GET `/api/v1/totp/credentials` | Stored credential listing for UI. |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-023-01 | `maintenance totp evaluate` | Evaluates stored/inline credentials (drift/timestamp). |
| CLI-023-02 | `maintenance totp replay` | Non-mutating replay. |

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|-----------|---------------------------|
| TE-023-01 | `totp.evaluate` | `credentialIdHash`, `mode`, `result`, sanitized metadata. |
| TE-023-02 | `totp.replay` | `credentialIdHash`, `mode`, `result`, sanitized metadata. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-023-01 | `docs/totp_validation_vectors.json` | RFC 6238 vectors shared across modules. |
| FX-023-02 | `rest-api/src/test/resources/totp/sample-requests/*.json` | REST/Selenium test payloads. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| UI-023-01 | Inline Evaluate default | Evaluate tab loads inline form active. |
| UI-023-02 | Stored Evaluate | Dropdown selection auto-fills preview and increments counters on success. |
| UI-023-03 | Replay | Stored/inline replay actions never mutate counters; auto-fill sample data. |

## Telemetry & Observability
- All evaluation/replay events routed through `TelemetryContracts` to maintain parity with HOTP/OCRA.
- Operator console leverages REST telemetry, no additional UI event names.

## Documentation Deliverables
- Update TOTP operator how-to, CLI/REST guides, roadmap, knowledge map, OpenAPI docs.

## Fixtures & Sample Data
- `docs/totp_validation_vectors.json` plus derived test fixtures.

## Spec DSL
```
domain_objects:
  - id: DO-023-01
    name: TotpCredentialDescriptor
    fields:
      - name: algorithm
        type: enum[SHA1,SHA256,SHA512]
      - name: digits
        type: enum[6,8]
      - name: stepSeconds
        type: integer
  - id: DO-023-02
    name: TotpEvaluationRequest
    fields:
      - name: credentialId
        type: string
      - name: mode
        type: enum[stored,inline]
telemetry_events:
  - id: TE-023-01
    event: totp.evaluate
fixtures:
  - id: FX-023-01
    path: docs/totp_validation_vectors.json
ui_states:
  - id: UI-023-01
    description: TOTP inline evaluate default view
```

## Appendix
_None._
