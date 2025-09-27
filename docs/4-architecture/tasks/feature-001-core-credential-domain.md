# Feature 001 – Core Credential Domain Tasks

_Status: Not started_
_Last updated: 2025-09-27_

## Execution Notes
- Follow the specification in `docs/4-architecture/specs/feature-001-core-credential-domain.md`.
- Keep each task to ≤10 minutes of focused work and commit after completing one task cluster.
- Tests precede implementations; mark tasks as complete (`[x]`) as they ship.
- Use this checklist with the analysis gate before coding.

## Phase 0 – Setup
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| T001 | Ensure `core` module module-info and build files accommodate new credential domain packages. | FR-001–FR-008, NFR-001 | No |
| T002 | Add package structure placeholders (`io.openauth.sim.core.credentials.*`) with javadoc stubs. | FR-001–FR-005 | No |

## Phase 1 – Specification Tests
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| T003 | Create unit test skeletons for each credential type covering valid/invalid payload samples. | FR-001–FR-005, NFR-004 | No |
| T004 | Add property-based tests for secret material encoding/decoding utilities. | FR-006, NFR-004 | No |
| T005 | Define ArchUnit rules enforcing core-only access to credential internals. | FR-007, NFR-004 | No |

## Phase 2 – Core Implementation
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| T006 | Implement sealed credential hierarchy (records/interfaces) with protocol-specific descriptors. | FR-001–FR-005, NFR-003 | No |
| T007 | Implement credential registry with protocol capability introspection. | FR-007, NFR-002 | No |
| T008 | Build validation/factory utilities for each protocol with descriptive error messaging. | FR-001–FR-008, NFR-005 | No |
| T009 | Extend secret material helpers to normalise raw/hex/Base64 inputs. | FR-006, NFR-002 | No |

## Phase 3 – Integration & Observability
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| T010 | Connect credential registry to persistence serialization interfaces (no persistence implementation yet). | FR-006–FR-008 | No |
| T011 | Emit structured validation failure events/log markers without secret leakage. | FR-008, NFR-005 | No |

## Phase 4 – Documentation & Cleanup
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| T012 | Update `docs/1-concepts` with protocol capability matrix and glossary. | FR-001–FR-005 | No |
| T013 | Refresh `docs/4-architecture/knowledge-map.md` to reflect credential registry relationships. | FR-007 | No |
| T014 | Update roadmap and feature plan status; capture lessons learned/self-review notes. | All | No |

## Open Follow-ups
- Populate task outcomes and timestamps upon completion.
- Attach Gradle command outputs and analysis gate results to the feature plan when tasks close.
