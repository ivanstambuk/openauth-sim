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
- 2025-09-28 – Authentication hardening deferred: endpoint remains internal-only with no additional auth layer; future security posture will rely on organizational controls rather than request-level checks.
- 2025-09-28 – All hex-encoded fields (`sharedSecretHex`, `sessionHex`, `pinHashHex`, `timestampHex`) will be pre-validated for hexadecimal content and even length; failures return field-specific 400 responses with structured reason codes.
- 2025-09-28 – Suites requiring counters must receive non-negative `counter` values; missing or invalid counters trigger explicit 400 responses rather than defaulting to zero.
- 2025-09-28 – Telemetry hardening: structured log events gain `reasonCode` and `sanitized=true|false` attributes and continue to rely on WARN/ERROR levels for downstream alerting.
- 2025-09-28 – Timestamp validation will reuse the descriptor-configured drift window from `OcraCredentialFactory.validateTimestamp` and surface `timestamp_drift_exceeded` when outside tolerance.
- 2025-09-28 – Runtime PIN hash mismatches emit a dedicated `pin_hash_mismatch` reason code; REST pre-validation compares the supplied hash against descriptor expectations before invoking the calculator.
- 2025-09-28 – Authentication/authorization is not required for this endpoint; treat the route as an internal operator surface with no additional auth layer.

## Objectives & Success Criteria
- Provide a deterministic REST endpoint that accepts all runtime inputs required by session-enabled OCRA suites (challenge, client/server data, session payloads, timestamp, counter, PIN hash).
- Guarantee responses redact shared secrets and echo only non-sensitive metadata (suite identifier, OTP, execution telemetry references).
- Publish OpenAPI documentation and supporting markdown references so clients understand request/response formats and error semantics.

## Functional Requirements
| ID | Requirement | Acceptance Signal |
|----|-------------|-------------------|
| FR-REST-001 | Expose `POST /api/v1/ocra/evaluate` accepting suite, shared secret, and optional runtime parameters. | Valid requests using S064/S128/S256/S512 fixtures return HTTP 200 with the expected OTP values. |
| FR-REST-002 | Validate inputs using the existing `OcraCredentialFactory` and reuse `OcraResponseCalculator`. | Invalid suites or malformed secrets produce HTTP 400 with descriptive, redacted error messages. |
| FR-REST-005 | Reject malformed hex inputs, invalid counters, timestamp drift violations, and PIN hash mismatches before invoking the core calculator. | Requests with non-hex characters, odd-length hex, missing/negative counters, out-of-window timestamps, or PIN hash mismatches return HTTP 400 with field-specific `reasonCode` values (`timestamp_drift_exceeded`, `pin_hash_mismatch`, etc.). |
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
- Include additional attributes `reasonCode` (when applicable) and `sanitized` (boolean) so alerting rules can track redaction state.
- Secrets (`sharedSecretHex`, full session payload, challenges) must never appear in logs/telemetry; tests enforce this via log capture.
- Expected validation reason codes now include `session_required`, `session_not_permitted`, `challenge_length`, `challenge_format`, `counter_required`, `counter_negative`, `not_hexadecimal`, `invalid_hex_length`, `timestamp_drift_exceeded`, `timestamp_not_permitted`, `timestamp_invalid`, `pin_hash_mismatch`, `pin_hash_required`, and `pin_hash_not_permitted` (extend tests as new cases emerge).

## Test Strategy
- **Unit tests**: Validate request DTO parsing, rejection of malformed inputs, and telemetry emission using mocked logger/appender.
- **Integration tests**: Spin up the Spring Boot test slice, issue HTTP requests covering S064/S128/S256/S512 vectors, and assert OTP parity with core fixtures plus field-specific validation errors and reason codes.
- **Contract tests**: Ensure OpenAPI generation includes schema and examples; compare against saved snapshot when feasible.

## Dependencies & Out of Scope
- Reuses core OCRA factories/calculator; no changes to core module for this feature.
- Adds `org.springdoc:springdoc-openapi-starter-webmvc-ui` to the REST facade so `/v3/api-docs` and Swagger UI stay aligned with implementation; rationale and approval recorded in Feature Plan 003 (R005).
- Credential persistence lookup, authentication/authorization, and rate limiting remain out of scope.
- Future async/batch evaluation endpoints will require separate specifications.

Update this document as new clarifications or constraints arise during implementation.
