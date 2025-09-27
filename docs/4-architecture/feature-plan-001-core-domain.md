# Feature Plan 001 – Core Credential Domain Expansion

_Status: Not started_
_Last updated: 2025-09-27_

## Objective

Design and implement a protocol-aware credential domain in the `core` module, enabling the emulator to represent FIDO2/WebAuthn, OATH/OCRA, EUDI mDL, EMV/CA, and generic credentials with appropriate metadata, secret handling, and validation hooks.

## Success Criteria

- Typed credential descriptors exist for each targeted protocol with clearly defined required/optional attributes.
- Secret material utilities support common encodings (raw, hex, Base64) and protocol-specific preprocessing (e.g., HMAC seeds for OATH, credential IDs for FIDO2).
- Validation logic rejects incomplete or inconsistent credential payloads.
- Unit tests cover creation, serialization, and update flows for each credential type.
- Documentation updates summarise the domain model in `docs/1-concepts` and reference relevant specifications.

## Task Tracker

| ID | Task | Owner | Status | Notes |
|----|------|-------|--------|-------|
| T1 | Produce protocol capability matrix and glossary in `docs/1-concepts` | TBD | ☐ Not started | Outline fields, cryptographic primitives, lifetimes |
| T2 | Introduce sealed interface/record hierarchy for credential types in `core` | TBD | ☐ Not started | Preserve backward compatibility with existing `Credential` record |
| T3 | Implement protocol-specific validation + factory methods | TBD | ☐ Not started | Consider builder pattern for clarity |
| T4 | Extend `SecretMaterial` helpers for hashing/derivation needs | TBD | ☐ Not started | e.g., convert to HMAC key, derive mDL data groups |
| T5 | Add comprehensive JUnit/ArchUnit tests for the new domain model | TBD | ☐ Not started | Include property-based tests where possible |
| T6 | Document migration guidance and update README/roadmap references | TBD | ☐ Not started | Summarise usage for downstream modules |

## Dependencies

- Align with roadmap Workstreams 2–6 to ensure downstream modules consume the same types.
- Surface any new security/privacy considerations as ADRs.

## Open Questions

- What minimum metadata is required for each protocol to satisfy emulator goals?
- Should the domain expose cryptographic operations directly or delegate to strategy interfaces?
- How will persistence versioning be handled when credential schemas evolve?

Update this plan as tasks progress: check off completed items, add new tasks, and note blockers.
