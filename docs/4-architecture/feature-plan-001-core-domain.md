# Feature Plan 001 – Core Credential Domain Expansion

_Status: Not started_
_Last updated: 2025-09-27_

## Objective

Design and implement a protocol-aware credential domain in the `core` module, enabling the emulator to represent FIDO2/WebAuthn, OATH/OCRA, EUDI mDL, EMV/CA, and generic credentials with appropriate metadata, secret handling, and validation hooks.

Reference specification: `docs/4-architecture/specs/feature-001-core-credential-domain.md`.

## Success Criteria

- Typed credential descriptors exist for each targeted protocol with clearly defined required/optional attributes.
- Secret material utilities support common encodings (raw, hex, Base64) and protocol-specific preprocessing (e.g., HMAC seeds for OATH, credential IDs for FIDO2).
- Validation logic rejects incomplete or inconsistent credential payloads.
- Unit tests cover creation, serialization, and update flows for each credential type.
- Documentation updates summarise the domain model in `docs/1-concepts` and reference relevant specifications.

## Task Tracker

- Detailed execution steps live in `docs/4-architecture/tasks/feature-001-core-credential-domain.md`. Update that checklist as work progresses and mirror status changes here.
- Map task outcomes back to the specification requirements (FR/NFR IDs) to maintain traceability.
- Record Gradle command outputs (`./gradlew spotlessApply check`) and analysis-gate results after each work session.

## Dependencies

- Align with roadmap Workstreams 2–6 to ensure downstream modules consume the same types.
- Surface any new security/privacy considerations as ADRs.
- Complete the analysis gate checklist (`docs/5-operations/analysis-gate-checklist.md`) before implementation sessions begin.

## Open Questions

- What minimum metadata is required for each protocol to satisfy emulator goals?
- Should the domain expose cryptographic operations directly or delegate to strategy interfaces?
- How will persistence versioning be handled when credential schemas evolve?

Update this plan as tasks progress: check off completed items, add new tasks, and note blockers.
