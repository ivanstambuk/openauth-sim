# Feature 009 – OCRA Replay & Verification

 _Status: Complete_
 _Last updated: 2025-10-01_

## Overview
Establish replay and verification workflows that accept operator-supplied one-time passwords (OTPs) and validate them against stored or inline OCRA credentials to support non-repudiation scenarios. This feature focuses on verifying historical OTP submissions rather than regenerating new values, ensuring auditors can confirm whether a claimed OTP was valid under the original credential configuration. Implementation landed on 2025-10-01 alongside passing `./gradlew qualityGate` checks.

## Clarifications
1. 2025-10-01 – Facade scope will cover the CLI and REST API only; operator UI will defer until a separate UX scope is prioritised (Option A).
2. 2025-10-01 – Verification requests must include the OTP plus the full OCRA suite context used originally (challenge, counter/session/timestamp payloads, credential identifier, etc.) so the submission can be replayed exactly (Option B).
3. 2025-10-01 – Operators may verify against either persisted credentials or a supplied inline secret, provided both paths produce identical audit telemetry (Option B).
4. 2025-10-01 – Verification evidence will rely on existing structured telemetry/logging; no additional persisted receipts are required for this feature (Option A).
5. 2025-10-01 – Replay checks enforce strict validation with no tolerance windows or resynchronisation; any mismatch is a definitive failure (Option A).
6. 2025-10-01 – Performance benchmarks for R913 will run on the current WSL2 Linux host (x86_64) with OpenJDK 17.0.16; results will be recorded with hardware/JDK details for traceability (Option B).

## Functional Requirements
| ID | Requirement | Acceptance Signal |
|----|-------------|-------------------|
| ORV-001 | Provide a CLI command that accepts an OTP and its OCRA suite context and verifies it against a stored or inline credential without regenerating new values. | `./gradlew :cli:test` includes replay verification scenarios; manual CLI invocation returns explicit success/failure without altering counters. |
| ORV-002 | Expose a REST endpoint that accepts the same verification payload and returns a structured result while leaving existing evaluation endpoints untouched. | REST contract documented and exercised via integration tests; POST returns 200 with verification status or 422 for invalid context. |
| ORV-003 | Require callers to supply the complete OCRA execution context (challenge, counter, session, timestamp fields as dictated by the suite) alongside the OTP. | Validation rejects requests missing required context; tests confirm failure classification. |
| ORV-004 | Allow verification against persisted credentials (by identifier) or inline secrets while emitting identical telemetry metadata for both flows. | Tests show both credential modes succeed when context matches and produce telemetry with credential source indicated. |
| ORV-005 | Fail verification without tolerance windows—any drift in counter or timestamp yields a deterministic failure classification surfaced to the caller. | Tests assert that altered counter/timestamp values produce a non-success status with reason code `strict_mismatch`. |

## Non-Functional Requirements
| ID | Requirement | Target |
|----|-------------|--------|
| ORV-NFR-001 | Telemetry emitted for each verification must include operator principal, credential source (stored vs inline), and a hash of the OTP payload for audit reconstruction. | Telemetry schema documented; tests assert fields present. |
| ORV-NFR-002 | Verification requests must execute within 150 ms P95 for stored credentials and 200 ms P95 for inline secrets on the documented benchmark environment. | Performance test or benchmark script demonstrating latency targets. |
| ORV-NFR-003 | REST and CLI verification paths must be idempotent and side-effect free (no counter/session mutation). | Repeated calls with identical payloads yield identical outcomes; persistence snapshot unchanged in tests. |

## Interface Design

### CLI Verification Command
- Subcommand name: `ocra verify`, added alongside existing import/list/delete/evaluate commands within `OcraCli`.
- Invocation patterns (credential modes remain mutually exclusive):
  - Stored credential – `ocra verify --credential-id <id> --otp <otp> [context options]`
  - Inline credential – `ocra verify --suite <suite> --secret <hex> --otp <otp> [context options]`
- Context flags mirror the evaluation command so operators can replay historical submissions without translation.

| Option | Required | Notes |
|--------|----------|-------|
| `--otp <value>` | Always | Accepts decimal or hexadecimal OTP strings; validated against descriptor length. |
| `--credential-id <id>` | One of stored/inline | Resolves descriptor from MapDB; cannot be combined with `--secret`. |
| `--suite <ocra-suite>` + `--secret <hex>` | One of stored/inline | Required for inline mode; suite validated before replay. |
| `--challenge <value>` | Suite dependent | Mutually exclusive with split challenge flags. |
| `--client-challenge <value>` / `--server-challenge <value>` | Suite dependent | Optional pair for split challenges (QA10/QN08). |
| `--session <hex>` | Suite dependent | Hex-encoded session payload. |
| `--timestamp <hex>` | Suite dependent | Hex timestamp payload; strict replay forbids drift correction. |
| `--counter <value>` | Suite dependent | Non-negative long when suite includes a counter element. |
| `--pin-hash <hex>` | Contextual | Optional when descriptor expects PIN material. |
| `--database <path>` | Optional | Existing inherited option to target alternate MapDB stores. |

Output remains telemetry-style over stdout/stderr: `event=cli.ocra.verify status=<success|mismatch|invalid> reasonCode=<match|strict_mismatch|validation_failure> sanitized=true credentialSource=<stored|inline> durationMillis=<ms>`. Exit codes align with Picocli conventions: `0` (match), `2` (strict mismatch), `64` (`USAGE`, validation failure), and `70` (`SOFTWARE`, unexpected error).

### REST Verification Endpoint
- Route: `POST /api/v1/ocra/verify` handled by a dedicated controller; evaluation endpoint stays untouched.
- JSON request schema:
  ```json
  {
    "otp": "17477202",
    "credentialId": "demo-token-1",
    "inlineCredential": {
      "suite": "OCRA-1:HOTP-SHA256-8:QA08-S064",
      "sharedSecretHex": "3132333435363738393031323334353637383930313233343536373839303132"
    },
    "context": {
      "challenge": "BANK-REF-2024",
      "clientChallenge": null,
      "serverChallenge": null,
      "sessionHex": "0011223344556677",
      "timestampHex": "0000018D4F3C2A",
      "counter": 42,
      "pinHashHex": null
    }
  }
  ```
- Validation rules:
  - Exactly one of `credentialId` or `inlineCredential` must be supplied (mutual exclusivity).
  - `context` fields follow suite requirements (missing counter, invalid hex, etc., yield HTTP 422 with `reasonCode=validation_failure`).
  - Absent or mismatched OTP length returns HTTP 422 with `reasonCode=otp_length_mismatch`.
- Response contract:
  - `200 OK` with body `{"status":"match","reasonCode":"match","metadata":{...}}` when verification succeeds.
  - `200 OK` with body `{"status":"mismatch","reasonCode":"strict_mismatch","metadata":{...}}` for strict failures (no tolerance applied).
  - `404 Not Found` when `credentialId` does not resolve (reasonCode `credential_not_found`).
  - `422 Unprocessable Entity` for validation failures (reasonCode `validation_failure`, errors array).
- Metadata payload includes `credentialSource`, `suite`, `otpLength`, `durationMillis`, and a `contextFingerprint` hash (see telemetry section). OpenAPI definitions (`docs/3-reference/rest-openapi.json|yaml`) will add `OcraVerificationRequest`, `OcraVerificationContext`, and `OcraVerificationResult` schemas.

## Telemetry & Logging
- CLI event name: `cli.ocra.verify` emitting `{status, reasonCode, credentialSource, credentialId?, otpHash, contextFingerprint, durationMillis, operatorPrincipal, sanitized}`.
- REST event name: `rest.ocra.verify` emitting the same fields plus `httpStatus`, `requestId`, and optional `clientId` header value; logged at INFO for success/mismatch and WARN for validation failures.
- Core-level instrumentation: `core.ocra.verify` event emitted when the replay engine runs, enabling cross-facade correlation.
- Hashing requirements:
  - `otpHash` = Base64URL(SHA-256(uppercase OTP bytes)).
  - `contextFingerprint` = Base64URL(SHA-256(suite + '|' + normalized challenge payloads + '|' + sessionHex + '|' + timestampHex + '|' + counter value)).
  - Hashes computed in-memory; raw values never written to logs, telemetry, or persistence.
- Telemetry must include `sanitized=true` and omit shared secrets, full OTP, session payloads, or challenges. Tests will capture logger output to assert only hashed or categorical fields appear.
- Duration captured in milliseconds; CLI/REST events must also include `outcome=<match|mismatch|invalid>` for downstream filtering.

## Test Strategy
- Add CLI integration tests covering success and failure replays (strict mismatch, missing context).
- Extend REST API contract tests to cover verification endpoint payload/response combinations.
- Introduce core unit tests ensuring OCRA replay logic reproduces original signature calculation without state mutation.
- Validate telemetry contents via test doubles or log capture to confirm audit fields.

## Dependencies & Risks
- Requires existing persistence module to expose immutable reads without counter mutation.
- Inline secret verification must ensure secrets are not logged or persisted inadvertently.
- Strict validation may reject legacy OTPs if original capture was flawed; operators must understand failure messaging.

## Out of Scope
- Operator UI flows for replay/verification (tracked for future UX scope).
- Automatic counter or timestamp resynchronisation.
- OTP generation/regeneration utilities.

## Verification
- `./gradlew qualityGate` must include new replay tests and remain green.
- Manual CLI and REST smoke tests documented in the feature plan to confirm behaviour against sample credentials.

Update this specification once clarification responses are received.
