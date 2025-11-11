# Feature 004 – REST OCRA Credential Resolution

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/004/plan.md` |
| Linked tasks | `docs/4-architecture/features/004/tasks.md` |
| Roadmap entry | #4 |

## Overview
Extend `POST /api/v1/ocra/evaluate` so callers can either reference a persisted OCRA credential by identifier or continue supplying inline secret material. The controller must resolve descriptors from the existing persistence layer, keep inline mode behaviour unchanged, enforce mutual exclusivity between modes, and update telemetry/OpenAPI/docs accordingly.

## Clarifications
- 2025-09-28 – Dual-mode payloads: request supplies either `credentialId` (resolved via persistence) or inline `sharedSecretHex` inputs; providing both or neither is invalid.
- 2025-09-28 – Endpoint remains internal-only; no authentication/rate limiting requirements are added in this feature.
- 2025-09-28 – Persistence integration is read-only: use existing descriptor store (`CredentialStoreFactory`) without adding create/update/delete flows.
- 2025-09-28 – New validation reason codes: `credential_not_found`, `credential_conflict`, `credential_missing`.

## Goals
- Support credential resolution by ID while preserving inline secret submissions.
- Ensure mutually exclusive payload semantics with descriptive error responses and telemetry reason codes.
- Document the dual-mode contract in OpenAPI, operator guides, and telemetry references.

## Non-Goals
- Introducing new credential types or modifying CLI/UI credential flows.
- Adding persistence write/update APIs, authentication, or rate limiting.

## Functional Requirements

### FR1 – Credential resolution mode (FR-REST-010, S04-01)
- **Requirement:** Accept `credentialId` in the request body, load the descriptor from persistence, and compute OTPs without inline secrets.
- **Success path:** Valid IDs return HTTP 200 responses with OTPs matching `OcraResponseCalculator` fixtures while redacting secrets.
- **Validation path:** Invalid IDs flow through FR2 error handling.
- **Failure path:** Persistence failures return HTTP 500 with sanitized diagnostics.
- **Telemetry & traces:** `rest.ocra.evaluate` events include `hasCredentialReference=true` and `credentialIdHash` (hashed) when resolution succeeds.
- **Source:** Clarifications + original specification table.

### FR2 – Inline mode parity (FR-REST-011, S04-02)
- **Requirement:** Preserve existing inline secret behaviour when `sharedSecretHex` is provided and `credentialId` omitted.
- **Success path:** Legacy MockMvc fixtures continue to pass; telemetry fields remain unchanged (`hasCredentialReference=false`).
- **Validation path:** Inline validations reuse Feature 003 rules (hex length, timestamp drift, PIN hashes).
- **Failure path:** Inline validation errors surface via structured HTTP 400 responses.
- **Telemetry & traces:** Existing telemetry fields remain, plus new `hasCredentialReference` boolean.
- **Source:** Feature 003 baseline + this feature’s objectives.

### FR3 – Mutual exclusivity & validation (FR-REST-012, S04-03)
- **Requirement:** Enforce that exactly one mode is selected; emit descriptive errors for conflicts/missing credentials.
- **Success path:** Requests providing valid inline or credential reference inputs proceed without ambiguity.
- **Validation path:** Supplying both modes yields HTTP 400 with `reasonCode=credential_conflict`; providing neither yields `reasonCode=credential_missing`.
- **Failure path:** Missing descriptors return `reasonCode=credential_not_found` and HTTP 404/400 (per spec).
- **Telemetry & traces:** Failure events include `reasonCode` and `hasCredentialReference` context; logs stay sanitized.
- **Source:** Clarifications + FR table.

### FR4 – Telemetry & documentation updates (FR-REST-013, S04-04/05)
- **Requirement:** Capture credential-resolution metadata in telemetry/logs and update OpenAPI/docs to reflect dual-mode payloads.
- **Success path:** Telemetry events include `hasCredentialReference`, and OpenAPI snapshots/documentation describe `credentialId` vs `sharedSecretHex` usage.
- **Validation path:** Snapshot tests ensure JSON/YAML artifacts match controller schema; docs mention new reason codes.
- **Failure path:** Snapshot drift or missing doc updates block merges.
- **Telemetry & traces:** `rest.ocra.evaluate` frames store hashed credential IDs and reason codes for lookup failures.
- **Source:** Spec objectives + documentation deliverables.

## Non-Functional Requirements

### NFR1 – Compatibility (NFR-REST-010)
- **Requirement:** Stay on Java 17/Spring Boot stack without new dependencies.
- **Driver:** Maintain consistent toolchain for REST facade.
- **Measurement:** `./gradlew --no-daemon :rest-api:test spotlessApply check` remains green.
- **Source:** Existing spec table.

### NFR2 – Observability (NFR-REST-011)
- **Requirement:** Telemetry/logging reflect dual-mode flags and remain sanitized.
- **Driver:** Operators need visibility into credential lookup vs inline usage.
- **Measurement:** Log capture tests assert `hasCredentialReference` and redaction; knowledge map updated.
- **Source:** Telemetry expectations.

### NFR3 – Performance (NFR-REST-012)
- **Requirement:** Maintain ≤50 ms P95 response times by reusing cached descriptors and persistence APIs.
- **Driver:** Avoid degrading REST OCRA latency when lookup mode is used.
- **Measurement:** MockMvc/integration tests plus benchmark notes from Feature 002 persistence tuning.
- **Source:** Original spec.

## UI / Interaction Mock-ups
```
REST-only feature; refer to the request/response schemas below.
```

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S04-01 | Requests with `credentialId` evaluate OTPs via persistence without inline secrets. |
| S04-02 | Inline secret submissions continue to succeed when `credentialId` is omitted. |
| S04-03 | Validation enforces mutually exclusive modes and surfaces descriptive reason codes. |
| S04-04 | Telemetry/logging capture `hasCredentialReference` and lookup failures without leaking secrets. |
| S04-05 | OpenAPI/docs/telemetry snapshots document the dual-mode API. |

## Test Strategy
- **Unit:** Resolver helper enforcing exclusivity, persistence gateway mocking, telemetry serialization.
- **Integration (MockMvc):** Credential lookup success, missing credential, conflict, inline regression, and telemetry/log capture tests.
- **Contract:** OpenAPI JSON/YAML snapshot tests include `credentialId` field and new error schemas.

## Interface & Contract Catalogue

### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-004-01 | `OcraEvaluationRequest` (extended) with optional `credentialId` plus inline fields. | rest-api |
| DO-004-02 | `CredentialResolutionError` DTO capturing `error`, `message`, `reasonCode`. | rest-api |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-004-01 | REST `POST /api/v1/ocra/evaluate` | Adds optional `credentialId`; inline mode remains. | Persistence read-only; conflict/missing rules enforced.

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| — | n/a | CLI unchanged; credential management handled elsewhere. |

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|-----------|---------------------------|
| TE-004-01 | `rest.ocra.evaluate` | `hasCredentialReference`, `credentialIdHash`, `reasonCode`, `status`, `durationMillis`, `sanitized=true`. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-004-01 | `rest-api/src/test/resources/http/ocra-credential-resolution/*.http` | Sample requests covering lookup/conflict/inline modes. |
| FX-004-02 | `docs/3-reference/rest-openapi.{json,yaml}` | Snapshot artifacts containing `credentialId` schema. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| — | Not applicable | REST-only change. |

## Telemetry & Observability
`rest.ocra.evaluate` events now include `hasCredentialReference` and hashed credential IDs. Lookup failures emit `reasonCode=credential_not_found` with sanitized messages. Logging adheres to constitution guardrails; knowledge map references persistence relationships.

## Documentation Deliverables
- OpenAPI snapshots (`docs/3-reference/rest-openapi.{json,yaml}`) updated with `credentialId` schema/examples.
- Operator how-to (`docs/2-how-to/use-ocra-rest-operations.md`) describing credential mode usage and sample curl commands.
- Knowledge map + roadmap entries referencing credential resolution flow.

## Fixtures & Sample Data
- HTTP request fixtures for lookup/conflict cases stored with tests.
- Telemetry samples captured during verification to document new reason codes.

## Spec DSL
```
domain_objects:
  - id: DO-004-01
    name: OcraEvaluationRequest
    fields:
      - name: credentialId
        type: string
        constraints: optional, mutually exclusive with sharedSecretHex
      - name: sharedSecretHex
        type: string
        constraints: uppercase hex, required when credentialId absent
  - id: DO-004-02
    name: CredentialResolutionError
    fields:
      - name: reasonCode
        type: enum(credential_not_found, credential_conflict, credential_missing)
routes:
  - id: API-004-01
    method: POST
    path: /api/v1/ocra/evaluate
telemetry_events:
  - id: TE-004-01
    event: rest.ocra.evaluate
    fields:
      - name: hasCredentialReference
        redaction: none (boolean)
      - name: credentialIdHash
        redaction: hash
fixtures:
  - id: FX-004-02
    path: docs/3-reference/rest-openapi.json
ui_states: []
```

## Appendix
- `docs/4-architecture/features/004/plan.md`
- `docs/4-architecture/features/004/tasks.md`
- `docs/3-reference/rest-openapi.{json,yaml}`
