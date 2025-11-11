# Feature 003 – REST OCRA Evaluation Endpoint

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/003/plan.md` |
| Linked tasks | `docs/4-architecture/features/003/tasks.md` |
| Roadmap entry | #3 |

## Overview
Expose the existing `OcraResponseCalculator` via the `rest-api` Spring Boot facade so automated clients can compute RFC 6287-compliant OTPs with a synchronous HTTP POST. The endpoint must accept all inline inputs (suite, shared secret, optional counter/challenge/session/pin/timestamp fields), redact secrets in responses and telemetry, and keep OpenAPI snapshots aligned for downstream automation.

## Clarifications
- 2025-09-28 – Option A: maintain a dedicated REST feature (spec/plan/tasks) instead of extending Feature 001 so facade changes remain isolated.
- 2025-09-28 – Endpoint path fixed at `POST /api/v1/ocra/evaluate`; responses return a JSON payload containing the OTP and metadata. No async orchestration required for this scope.
- 2025-09-28 – Input scope limited to inline payloads; persistence-backed credential lookup deferred to future workstreams.
- 2025-09-28 – Spring Boot 3.3.4 baseline adopted with SpringDoc OpenAPI starter; dependency locks refreshed (Mockito, ByteBuddy, Caffeine, json-smart, jspecify).
- 2025-09-28 – SpringDoc Option A approved to generate `/v3/api-docs` artifacts; manual YAML editing stays a fallback if generation blocks the gate.
- 2025-09-30 – Generated OpenAPI snapshots (JSON + YAML) stored under `docs/3-reference/rest-openapi.{json,yaml}` with snapshot tests guarding drift.

## Goals
- Provide a synchronous REST endpoint that mirrors the CLI/core OCRA evaluation semantics.
- Deliver exhaustive validation + telemetry for inline inputs without leaking secret material.
- Keep OpenAPI documentation, knowledge map references, and operator how-to guides in sync with the controller contract.

## Non-Goals
- Replay/validation flows (Feature 009) and persistence-backed credential lookup.
- UI/operator-console wiring beyond referencing the REST endpoint in docs.
- Authentication, authorization, rate limiting, or async/batch interfaces (future features).

## Functional Requirements

### FR1 – Synchronous OCRA evaluation (`S03-01`)
- **Requirement:** Implement `POST /api/v1/ocra/evaluate` that accepts suite, sharedSecretHex, optional counter/challenges/session/pin/timestamp fields, and returns the computed OTP with metadata.
- **Success path:** Valid payload returns HTTP 200 with `{otp,suite,telemetryId}`; OTP matches `OcraResponseCalculator` fixtures across S064/S128/S256/S512 suites.
- **Validation path:** Missing required fields or malformed payloads route to FR2 error handling.
- **Failure path:** Calculator errors (unexpected) map to HTTP 500 with sanitized diagnostics.
- **Telemetry & traces:** `rest.ocra.evaluate` event captures `suite`, `hasSessionPayload`, `status`, `durationMillis` while redacting secrets.
- **Source:** Feature directive + clarifications dated 2025-09-28.

### FR2 – Validation & error responses (`S03-02`, `S03-05`)
- **Requirement:** Enforce suite-specific validation (hex constraints, counter non-negativity, timestamp drift, PIN hashes) and return structured errors with `reasonCode`.
- **Success path:** Inputs meeting validation proceed to FR1.
- **Validation path:** HTTP 400 with `{error,message,details{field,reason}}`; reason codes include `session_required`, `session_not_permitted`, `challenge_length`, `challenge_format`, `counter_required`, `counter_negative`, `not_hexadecimal`, `invalid_hex_length`, `timestamp_drift_exceeded`, `timestamp_invalid`, `timestamp_not_permitted`, `pin_hash_required`, `pin_hash_not_permitted`, `pin_hash_mismatch`.
- **Failure path:** Schema-level errors (malformed JSON) produce HTTP 400 with `error=invalid_json`.
- **Telemetry & traces:** Validation failures emit `rest.ocra.evaluate` events with `status=FAILED`, `reasonCode`, `sanitized=true`.
- **Source:** Clarifications + tests T0302–T0306.

### FR3 – Telemetry & logging (`S03-03`)
- **Requirement:** Emit structured telemetry/logging that adheres to constitution redaction rules for both success and failure paths.
- **Success path:** Telemetry frames include `suite`, `hasSessionPayload`, `hasClientChallenge`, `hasServerChallenge`, `hasPin`, `hasTimestamp`, `status`, `durationMillis`, `reasonCode` (optional), `sanitized=true`.
- **Validation path:** Log capture tests assert secrets never appear; attempts to log raw hex fail CI.
- **Failure path:** Missing telemetry fields block builds via contract tests.
- **Source:** Telemetry expectations + plan success criteria.

### FR4 – OpenAPI documentation (`S03-04`)
- **Requirement:** Keep JSON + YAML OpenAPI snapshots aligned with the controller and guard them via snapshot tests.
- **Success path:** `OPENAPI_SNAPSHOT_WRITE=true ./gradlew :rest-api:test --tests OpenApiSnapshotTest` regenerates artifacts, snapshot tests fail on unreviewed drift.
- **Validation path:** PRs touching REST contracts must run the snapshot test and update docs.
- **Failure path:** Snapshot mismatch breaks CI until artifacts updated intentionally.
- **Telemetry & traces:** n/a (documentation asset).
- **Source:** Clarifications 2025-09-28 to 2025-09-30.

### FR5 – Operator documentation & tooling parity (`S03-06`)
- **Requirement:** Update operator how-to guides, knowledge map, and telemetry docs to describe the REST endpoint, example payloads, and Swagger UI usage.
- **Success path:** Docs list sample cURL commands, Telemetry fields, and instructions for accessing `/swagger-ui.html`.
- **Validation path:** `rg` checks confirm doc updates referencing `rest.ocra.evaluate` after code changes.
- **Failure path:** Missing doc sync noted in plan/tasks; feature cannot close until updated.
- **Telemetry & traces:** Documented string `rest.ocra.evaluate` reused by monitoring.
- **Source:** Plan timeline entries R005–R012.

## Non-Functional Requirements

### NFR1 – Response time (`NFR-REST-001`)
- **Requirement:** P95 ≤ 50 ms under local execution; synchronous handler only.
- **Driver:** Keep REST facade responsive for operators/automation.
- **Measurement:** MockMvc + integration tests instrument latency assertions.
- **Dependencies:** `OcraResponseCalculator`, Spring Boot 3.3.4.
- **Source:** Spec table.

### NFR2 – Security (`NFR-REST-002`)
- **Requirement:** Reject invalid inputs, do not persist request payloads, and never log secrets.
- **Driver:** Constitution redaction + operator safety.
- **Measurement:** Tests capturing logs/telemetry to ensure `sanitized=true`; manual review of persistence layers (none for this feature).
- **Source:** Spec table + telemetry expectations.

### NFR3 – Compatibility (`NFR-REST-003`)
- **Requirement:** Remain Java 17/Spring Boot 3.3.x compatible; reuse existing `rest-api` module conventions.
- **Driver:** Keep workspace toolchain consistent.
- **Measurement:** `./gradlew --no-daemon :rest-api:test spotlessApply check` passes with BOM-managed deps.
- **Source:** Clarifications + plan dependency notes.

### NFR4 – Observability (`NFR-REST-004`)
- **Requirement:** Telemetry/logging adhere to redaction rules; tests confirm no secret leakage.
- **Driver:** Support monitoring/auditing.
- **Measurement:** Telemetry contract + log capture tests; knowledge map references updated.
- **Source:** Spec table + telemetry expectations.

## UI / Interaction Mock-ups
```
REST-only feature; refer to the API contract below for request/response examples.
```

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S03-01 | REST `/api/v1/ocra/evaluate` accepts inline secret payloads and returns RFC 6287 OTPs via JSON responses. |
| S03-02 | Validation layer enforces suite/hex/counter/timestamp/session requirements with structured errors. |
| S03-03 | Telemetry/logging emit `rest.ocra.evaluate` events with sanitized fields for success/failure. |
| S03-04 | OpenAPI generation (JSON + YAML) stays aligned with the controller contract and is snapshot-tested. |
| S03-05 | Integration tests cover advanced validations (timestamp drift, PIN hashes, session payloads). |
| S03-06 | Documentation/roadmap/how-to references cover configuration guidance and telemetry expectations. |

## Test Strategy
- **Unit:** DTO validation, telemetry emission, and error mapping tests.
- **Integration:** Spring Boot test slice hitting `POST /api/v1/ocra/evaluate` across RFC fixtures and validation failure cases.
- **Contract:** Snapshot tests for JSON/YAML OpenAPI files.
- **Docs:** `OPENAPI_SNAPSHOT_WRITE=true` workflow documented; how-to guide includes sample cURL commands.

## Interface & Contract Catalogue

### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-003-01 | `OcraEvaluationRequest` DTO encapsulating suite + inline inputs with validation annotations. | rest-api |
| DO-003-02 | `OcraEvaluationResponse` DTO returning `otp`, `suite`, `telemetryId`. | rest-api |
| DO-003-03 | `ValidationErrorResponse` DTO with `error`, `message`, `details{field,reason}`. | rest-api |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-003-01 | REST `POST /api/v1/ocra/evaluate` | Computes OTPs using `OcraResponseCalculator`. | Requires inline hex secrets; persistence lookup deferred.

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| — | n/a | CLI already covered by Feature 001; no new commands here. |

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|-----------|---------------------------|
| TE-003-01 | `rest.ocra.evaluate` | `suite`, `hasSessionPayload`, `hasClientChallenge`, `hasServerChallenge`, `hasPin`, `hasTimestamp`, `status`, `durationMillis`, `reasonCode`, `sanitized=true`. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-003-01 | `rest-api/src/test/resources/http/ocra-evaluate/*.http` | Sample HTTP requests for integration tests. |
| FX-003-02 | `docs/3-reference/rest-openapi.json` / `.yaml` | Snapshot of REST contracts. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| — | Not applicable | REST-only feature. |

## Telemetry & Observability
`rest.ocra.evaluate` events flow through `TelemetryContracts`, capturing sanitized booleans for each optional input. Log capture tests ensure no secrets leak. Operators correlate HTTP responses with telemetry via the returned `telemetryId`.

## Documentation Deliverables
- `docs/2-how-to/use-ocra-rest-operations.md` – sample payloads and Swagger UI instructions.
- `docs/4-architecture/knowledge-map.md` – links REST facade to core OCRA services.
- `docs/3-reference/rest-openapi.{json,yaml}` – contract artifacts with snapshot tests.

## Fixtures & Sample Data
- RFC 6287 vectors (shared with Feature 001) reused in integration tests.
- HTTP request/response samples stored alongside tests.

## Spec DSL
```
domain_objects:
  - id: DO-003-01
    name: OcraEvaluationRequest
    fields:
      - name: suite
        type: string
        constraints: "RFC 6287 suite"
      - name: sharedSecretHex
        type: string
        constraints: uppercase hex, length >= 32
  - id: DO-003-02
    name: OcraEvaluationResponse
    fields:
      - name: otp
        type: string
routes:
  - id: API-003-01
    method: POST
    path: /api/v1/ocra/evaluate
telemetry_events:
  - id: TE-003-01
    event: rest.ocra.evaluate
    fields:
      - name: status
        redaction: none
fixtures:
  - id: FX-003-02
    path: docs/3-reference/rest-openapi.json
ui_states: []
```

## Appendix
- `docs/4-architecture/features/003/plan.md`
- `docs/4-architecture/features/003/tasks.md`
- `docs/3-reference/rest-openapi.{json,yaml}`
