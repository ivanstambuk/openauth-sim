# Feature 004 – REST OCRA Credential Resolution Specification

_Status: Accepted_
_Last updated: 2025-09-28_

## Overview
Extend the `/api/v1/ocra/evaluate` endpoint so callers can either reference a persisted OCRA credential by identifier or continue supplying raw secret material inline. The service must load descriptors from the core persistence layer when a credential reference is provided, preserve existing inline behaviour for ad-hoc requests, and enforce mutually exclusive payload semantics with descriptive reason codes.

## Clarifications
- 2025-09-28 – Support dual input modes: either `credentialId` references an existing descriptor, or the request includes raw `sharedSecretHex` (plus optional PIN/timestamp inputs). The service rejects payloads that specify both modes simultaneously.
- 2025-09-28 – No authentication or rate limiting is required; endpoint remains internal-only per Feature 003 decisions.
- 2025-09-28 – Credential lookup uses the existing core persistence gateway; descriptors are read-only for this feature (no creation/update flows exposed via REST).

## Objectives & Success Criteria
- Resolve credential descriptors by ID via the core persistence API, including shared secret, counter, PIN hash, and allowed timestamp drift.
- Maintain backward compatibility for inline shared secret requests with unchanged success/failure semantics.
- Reject ambiguous or invalid payloads with field-specific reason codes and redacted telemetry.
- Document both modes in OpenAPI, operator how-to, and telemetry snapshot.

## Functional Requirements
| ID | Requirement | Acceptance Signal |
|----|-------------|-------------------|
| FR-REST-010 | Accept `credentialId` in the request body and fetch the corresponding descriptor before evaluation. | Requests containing a valid credential ID return the expected OTP without providing `sharedSecretHex`. |
| FR-REST-011 | Preserve current inline secret mode when `sharedSecretHex` is supplied (and `credentialId` omitted). | Existing MockMvc fixtures continue to pass; responses/telemetry remain unchanged. |
| FR-REST-012 | Enforce mutual exclusivity and validation between modes. | Requests supplying both `credentialId` and inline secrets return HTTP 400 with `reasonCode=credential_conflict`; missing descriptor yields `reasonCode=credential_not_found`. |
| FR-REST-013 | Telemetry captures credential resolution metadata without leaking secrets. | Warning events include reason codes for lookup failures; success events note `hasCredentialReference=true/false`. |

## Non-Functional Requirements
| ID | Requirement | Target |
|----|-------------|--------|
| NFR-REST-010 | Compatibility | Java 17, Spring Boot stack; no dependency changes without owner approval. |
| NFR-REST-011 | Observability | Telemetry/logging reflect new input mode flags and remain sanitized. |
| NFR-REST-012 | Performance | Credential lookup should reuse existing persistence APIs; target ≤50 ms p95 locally (same as Feature 003). |

## API Contract Updates
- Add optional `credentialId` to the request schema.
- Define mutual exclusivity: request must include either `credentialId` _or_ `sharedSecretHex`; providing both or neither is invalid.
- Error payloads gain new `reasonCode` values: `credential_not_found`, `credential_conflict`, `credential_missing`.

## Telemetry Expectations
- Include `hasCredentialReference` boolean in success/failure events.
- Lookup failures emit `reasonCode=credential_not_found` (or `credential_conflict`) with sanitized messages; no secrets logged.

## Test Strategy
- **MockMvc integration**: cover credential lookup success, missing credential, conflict cases, and legacy inline flows.
- **Unit tests**: for resolver helper enforcing mode rules and mapping errors to reason codes.
- **Contract tests**: regenerate OpenAPI snapshot including `credentialId` schema and examples.

## Dependencies & Out of Scope
- Uses existing persistence adapter; no schema migrations required.
- No write/update operations exposed via REST.
- Authentication, rate limiting, and other operational controls remain out of scope per prior decisions.

Update this specification as new clarifications arise.
