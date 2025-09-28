# Feature 003 – REST OCRA Evaluation Endpoint Specification

_Status: Draft_
_Last updated: 2025-09-28_

## Overview
Expose the existing `OcraResponseCalculator` via the Spring Boot REST facade so automated clients can compute RFC 6287-compliant one-time passcodes using synchronous HTTP requests. The endpoint must remain session-aware, redacting all sensitive material while emitting structured telemetry for operations teams.

## Clarifications
- 2025-09-28 – Adopt Option A: author dedicated REST-facing specification/plan/tasks instead of extending Feature 001 documents, keeping facade concerns isolated from the core credential domain plan.
- 2025-09-28 – Endpoint will be implemented as a synchronous `POST` under `/api/v1/ocra/evaluate`, returning the computed OTP in the response payload. No long-polling or async job orchestration is required for this feature.
- 2025-09-28 – The endpoint accepts hex-encoded secret material supplied per-request; persistence-backed credential lookup remains out of scope until a future task.
- 2025-09-28 – Spring Boot 3.3.4 (`spring-boot-starter-web` and `spring-boot-starter-test`) introduced to the `rest-api` module with dependency locks refreshed to align shared tooling (Mockito, ByteBuddy, Caffeine, json-smart, jspecify).
- 2025-09-28 – Adopt SpringDoc OpenAPI (Option A) to generate `/v3/api-docs` for REST surfaces; manual YAML authoring stays as a contingency plan if generation fails the analysis gate.
- 2025-09-28 – Controller validation tests (R003) will mirror current 404 behavior with TODOs referencing R004, keeping the build green while signalling future expectations.

## Objectives & Success Criteria
- Provide a deterministic REST endpoint that accepts all runtime inputs required by session-enabled OCRA suites (challenge, client/server data, session payloads, timestamp, counter, PIN hash).
- Guarantee responses redact shared secrets and echo only non-sensitive metadata (suite identifier, OTP, execution telemetry references).
- Publish OpenAPI documentation and supporting markdown references so clients understand request/response formats and error semantics.

## Functional Requirements
| ID | Requirement | Acceptance Signal |
|----|-------------|-------------------|
| FR-REST-001 | Expose `POST /api/v1/ocra/evaluate` accepting suite, shared secret, and optional runtime parameters. | Valid requests using S064/S128/S256/S512 fixtures return HTTP 200 with the expected OTP values. |
| FR-REST-002 | Validate inputs using the existing `OcraCredentialFactory` and reuse `OcraResponseCalculator`. | Invalid suites or malformed secrets produce HTTP 400 with descriptive, redacted error messages. |
| FR-REST-003 | Emit structured telemetry events (`rest.ocra.evaluate`) capturing status, suite, input flags, and duration without logging secrets. | Telemetry hook verified by unit/integration tests asserting redaction. |
| FR-REST-004 | Document the endpoint in OpenAPI generation and `docs/2-how-to` so operators can exercise it. | `rest-api` module exposes updated OpenAPI spec and docs reference the new route. |

## Non-Functional Requirements
| ID | Requirement | Target |
|----|-------------|--------|
| NFR-REST-001 | Response time | ≤50 ms at p95 under local execution; synchronous handler only. |
| NFR-REST-002 | Security | Reject missing/invalid inputs; do not persist request payloads or secrets. |
| NFR-REST-003 | Compatibility | Java 17, Spring Boot stack already available in `rest-api` module. |
| NFR-REST-004 | Observability | Telemetry/logging adheres to constitution redaction rules; tests confirm no secret leakage. |

## API Contract
- **Request (application/json)**
  ```json
  {
    "suite": "OCRA-1:HOTP-SHA256-8:QA08-S064",
    "sharedSecretHex": "3132333435363738393031323334353637383930313233343536373839303132",
    "challenge": "SESSION01",
    "sessionHex": "001122...",
    "clientChallenge": "optional",
    "serverChallenge": "optional",
    "pinHashHex": "optional",
    "timestampHex": "optional",
    "counter": 0
  }
  ```
- **Response (HTTP 200)**
  ```json
  {
    "otp": "17477202",
    "suite": "OCRA-1:HOTP-SHA256-8:QA08-S064",
    "telemetryId": "rest-ocra-<uuid>"
  }
  ```
- **Error (HTTP 400)**
  ```json
  {
    "error": "invalid_input",
    "message": "challenge must be 8 characters for QA08",
    "details": {
      "field": "challenge",
      "reason": "length_mismatch"
    }
  }
  ```

## Telemetry Expectations
- Emit a structured event with keys: `event=rest.ocra.evaluate`, `suite`, `hasSessionPayload`, `hasClientChallenge`, `hasServerChallenge`, `hasPin`, `hasTimestamp`, `status`, `durationMillis`.
- Secrets (`sharedSecretHex`, full session payload, challenges) must never appear in logs/telemetry; tests enforce this via log capture.

## Test Strategy
- **Unit tests**: Validate request DTO parsing, rejection of malformed inputs, and telemetry emission using mocked logger/appender.
- **Integration tests**: Spin up the Spring Boot test slice, issue HTTP requests covering S064/S128/S256/S512 vectors, and assert OTP parity with core fixtures.
- **Contract tests**: Ensure OpenAPI generation includes schema and examples; compare against saved snapshot when feasible.

## Dependencies & Out of Scope
- Reuses core OCRA factories/calculator; no changes to core module for this feature.
- Adds `org.springdoc:springdoc-openapi-starter-webmvc-ui` to the REST facade so `/v3/api-docs` and Swagger UI stay aligned with implementation; rationale and approval recorded in Feature Plan 003 (R005).
- Credential persistence lookup, authentication/authorization, and rate limiting remain out of scope.
- Future async/batch evaluation endpoints will require separate specifications.

Update this document as new clarifications or constraints arise during implementation.