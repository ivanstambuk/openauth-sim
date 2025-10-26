# Feature 011 – Reflection Policy Hardening

_Status: Complete_
_Last updated: 2025-10-02_

## Overview
Eliminate reflection-based access across the OpenAuth Simulator codebase, improve testability of affected components, and establish forward-looking guidance so future contributions avoid reflective patterns. The effort spans refactoring existing production and test code, updating contributor instructions, and adding automation that flags reflection usage before it reaches the repository.

## Clarifications
- 2025-10-01 – The no-reflection policy applies to both production and test sources; legitimate use cases must be replaced with non-reflective alternatives or explicitly exempted via governance.
- 2025-10-01 – Broad refactors are acceptable when required to expose collaborators for testing (e.g., introducing interfaces, restructuring services), provided they are captured in this specification and follow standard documentation/verification steps.
- 2025-10-01 – Anti-reflection guidance will be encoded primarily in `AGENTS.md`; derivative templates may reference it later but are not mandatory for the initial change.

## Functional Requirements
| ID | Requirement | Acceptance Signal |
|----|-------------|-------------------|
| REF-001 | Identify and remove reflection usage (e.g., `getDeclaredMethod`, `setAccessible`) across production and test modules, replacing it with explicit APIs or test seams. | Repository-wide search for reflection APIs returns no occurrences except for approved exemptions documented in this spec. |
| REF-002 | Refactor impacted production code to expose deterministic, testable collaborators without relying on reflection in clients or tests. | Updated tests interact with public or package-private APIs; builds pass with reflection-free code paths. |
| REF-003 | Update `AGENTS.md` to communicate the no-reflection policy and expected mitigation strategies for future contributors. | AGENTS guidance includes the new policy, and documentation references this specification. |
| REF-004 | Introduce automation (e.g., Gradle check, ArchUnit rule, or pre-commit guard) that fails when new reflection usage is introduced without an explicit exemption. | Running the selected guard as part of the standard quality pipeline fails on newly added reflection calls and passes when none exist. |

## Non-Functional Requirements
| ID | Requirement | Target |
|----|-------------|--------|
| REF-NFR-001 | Maintainability | Refactors should minimise API churn; any new seams must be documented and covered by tests. |
| REF-NFR-002 | Developer Feedback | Reflection guard executes within existing `./gradlew qualityGate` runtime budget (≤30 minutes) or pre-commit checks complete within acceptable latency (<30s). |
| REF-NFR-003 | Traceability | All exceptions to the anti-reflection policy require explicit documentation in specs/ADRs with rationale. |

## Test Strategy
- Extend or create unit/integration tests that exercise the new public seams replacing reflection-based access.
- Add automation tests (e.g., ArchUnit rule) validating that disallowed reflection APIs are absent.
- Verify `./gradlew spotlessApply check` continues to pass after refactors and guard integration.

## Dependencies & Risks
- Refactoring may require touching shared services; coordinate with module owners to avoid regressions.
- The reflection guard must account for legitimate uses in third-party libraries; build configurations may need safe exceptions.
- Introducing new seams could widen public APIs; ensure access levels remain as tight as possible (package-private/internal) while enabling tests.

## Out of Scope
- Replacing reflection in third-party dependencies; scope is limited to project-owned code and configuration.
- Introducing new testing frameworks; existing JUnit/AssertJ setups remain.
- Expanding policy enforcement beyond reflection (e.g., dynamic proxies) unless a follow-up specification is created.

## Verification
- Grep or static analysis reports confirm no reflection API usage outside documented exceptions.
- Automation guard is integrated into CI and local workflows, failing appropriately when reflection is reintroduced.
- Documentation updates (`AGENTS.md`, relevant how-to guides) reflect the new policy.

Update this specification as additional decisions or scope adjustments emerge.
