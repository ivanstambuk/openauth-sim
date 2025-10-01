# Feature 009 – OCRA Replay & Verification

_Status: Draft_
_Last updated: 2025-10-01_

## Overview
Establish replay and verification workflows that accept operator-supplied one-time passwords (OTPs) and validate them against stored or inline OCRA credentials to support non-repudiation scenarios. This feature focuses on verifying historical OTP submissions rather than regenerating new values, ensuring auditors can confirm whether a claimed OTP was valid under the original credential configuration.

## Clarifications
1. 2025-10-01 – Facade scope will cover the CLI and REST API only; operator UI will defer until a separate UX scope is prioritised (Option A).
2. 2025-10-01 – Verification requests must include the OTP plus the full OCRA suite context used originally (challenge, counter/session/timestamp payloads, credential identifier, etc.) so the submission can be replayed exactly (Option B).
3. 2025-10-01 – Operators may verify against either persisted credentials or a supplied inline secret, provided both paths produce identical audit telemetry (Option B).
4. 2025-10-01 – Verification evidence will rely on existing structured telemetry/logging; no additional persisted receipts are required for this feature (Option A).
5. 2025-10-01 – Replay checks enforce strict validation with no tolerance windows or resynchronisation; any mismatch is a definitive failure (Option A).

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
