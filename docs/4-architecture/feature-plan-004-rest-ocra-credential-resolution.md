# Feature Plan 004 – REST OCRA Credential Resolution

_Status: Draft_
_Last updated: 2025-09-28_

## Objective
Enable `/api/v1/ocra/evaluate` to resolve credential descriptors from persistence when a caller supplies `credentialId`, while preserving the existing inline secret mode for ad-hoc usage. The plan delivers mutually exclusive payload handling, descriptor lookup, and updated documentation/telemetry.

Reference specification: `docs/4-architecture/specs/feature-004-rest-ocra-credential-resolution.md`.

## Success Criteria
- Requests that include `credentialId` return OTPs without requiring inline secrets.
- Inline secret requests continue to work unchanged.
- Ambiguous or invalid payloads yield descriptive 400 responses and telemetry (`credential_conflict`, `credential_not_found`, etc.).
- OpenAPI snapshot, operator how-to, and telemetry docs reflect both input modes.

## Proposed Increments
- R013 – Specification & planning (this document, tasks, open questions if any).
- R014 – Add failing MockMvc/ unit tests for credential lookup, missing credential, and conflict cases.
- R015 – Implement credential resolver in service, integrate persistence gateway, and update telemetry.
- R016 – Refresh OpenAPI/docs/telemetry snapshots and rerun build checks.

## Dependencies
- Requires access to the persistence adapter APIs already introduced in Workstream 1/2 (no new libraries).
- Must maintain backward compatibility with Feature 003 behaviour.

## Analysis Gate Notes
- TBD after tests and implementation planning are fleshed out.

## Timeline & Notes
- 2025-09-28 – R013 complete: spec/plan/tasks drafted for dual-mode credential resolution.
- 2025-09-28 – R014 delivered test-first MockMvc coverage (lookup success/missing/conflict/legacy); initial run red prior to implementation.
- 2025-09-28 – R015 implemented credential store integration, input mode validation, `hasCredentialReference` telemetry; MockMvc suite green.
- 2025-09-28 – R016 refreshed OpenAPI snapshot, operator how-to, telemetry docs; full build (`./gradlew :rest-api:test`, `./gradlew spotlessApply check`) green.

## Status
- 2025-09-28 – Feature delivered; plan retained for historical context until archived.

Update this plan as increments progress. Remove once Feature 004 ships.
