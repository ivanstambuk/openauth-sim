# Feature 001 – Core Credential Domain Tasks

_Status: In progress_
_Last updated: 2025-09-27_

## Execution Notes
- Follow the specification in `docs/4-architecture/specs/feature-001-core-credential-domain.md`.
- Maintain discrete Java packages per protocol (`io.openauth.sim.core.credentials.{ocra|fido2|eudiw|emvcap}`) and roll out one protocol at a time.
- Start with OCRA; keep other protocol tasks pending until their dedicated plans are approved.
- Keep each task to ≤10 minutes of focused work and commit after completing one task cluster.
- Tests precede implementations; mark tasks as complete (`[x]`) as they ship.
- Use this checklist with the analysis gate before coding.

## Phase 0 – Setup
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| T001 | Ensure `core` module module-info and build files accommodate new credential domain packages. | FR-001–FR-008, NFR-001 | No |
| T002 | Add package structure placeholders (`io.openauth.sim.core.credentials.*`) with javadoc stubs. | FR-001–FR-005 | No |

## Phase 1 – OCRA Specification Tests
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| T003 | [x] Capture the minimum OCRA metadata set (suite string, secret material requirements, optional counter/PIN handling) and document it under spec clarifications alongside global name/custom metadata rules. | FR-002, FR-010 | No |
| T004 | [x] Create OCRA unit test skeletons covering valid/invalid payload samples, counter/time drifts, and hash suite mismatches (landed disabled per spec clarification). | FR-002, NFR-004 | No |
| T005 | Add property-based tests for OCRA secret material encoding/decoding utilities. | FR-006, NFR-004 | No |
| T006 | Define ArchUnit rules ensuring only the OCRA package accesses its internals from outside `core`. | FR-007, NFR-004 | No |

## Phase 2 – OCRA Core Implementation
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| T007 | Implement OCRA credential descriptors inside `io.openauth.sim.core.credentials.ocra`. | FR-002, NFR-003 | No |
| T008 | Implement OCRA validation/factory utilities with descriptive error messaging. | FR-002, FR-006, NFR-005 | No |
| T009 | Extend secret material helpers to normalise raw/hex/Base64 inputs for OCRA secrets. | FR-006, NFR-002 | No |
| T010 | Wire OCRA entries into the credential registry with capability metadata. | FR-007, NFR-002 | No |

## Phase 3 – Integration & Observability
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| T011 | Connect OCRA credentials to persistence serialization interfaces (no persistence implementation yet). | FR-006–FR-008 | No |
| T012 | Implement schema-versioned persistence envelope helpers and migration pipeline tests for OCRA records. | FR-006–FR-008, NFR-004 | No |
| T013 | Emit structured validation failure events/log markers for OCRA flows without secret leakage. | FR-008, NFR-005 | No |

## Phase 4 – Documentation & Cleanup
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| T014 | Update `docs/1-concepts` with OCRA capability matrix and glossary. | FR-002 | No |
| T015 | Refresh `docs/4-architecture/knowledge-map.md` to reflect OCRA package relationships; note pending protocols. | FR-007 | No |
| T016 | Update roadmap and feature plan status; capture lessons learned/self-review notes. | All | No |

## Phase 5 – Future Protocol Packages (Pending Separate Plans)
| Protocol | Notes |
|----------|-------|
| FIDO2/WebAuthn | Await dedicated clarification on minimum metadata, lifecycle expectations, and package rollout tasks. |
| EUDI Wallet suites | Depends on broader clarifications already logged; schedule after OCRA delivery. |
| EMV/CAP | Pending tailored plan mirroring the OCRA approach. |

## Open Follow-ups
- Populate task outcomes and timestamps upon completion.
- Attach Gradle command outputs and analysis gate results to the feature plan when tasks close.
