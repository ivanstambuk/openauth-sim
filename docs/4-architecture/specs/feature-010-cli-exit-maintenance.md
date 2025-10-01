# Feature 010 – CLI Exit Testing Maintenance

_Status: Complete_
_Last updated: 2025-10-01_

## Overview
Retire uses of `SecurityManager` within the CLI launcher tests so the suite remains compatible with modern JDKs (where the API is disabled) while retaining coverage of exit-code behaviour. The change must remain test-scoped and avoid adding new dependencies.

## Clarifications
1. 2025-10-01 – Limit scope to the existing CLI launcher tests; production `OcraCliLauncher` must stay untouched (Option A, selected by user).
2. 2025-10-01 – Do not introduce third-party helpers (e.g., System Lambda) to intercept exits; reuse built-in facilities even if that requires bespoke fixtures (Option A).
3. 2025-10-01 – Preserve assertions validating `main` exits with the Picocli usage code for failure scenarios, with equivalent confidence to the current SecurityManager-based approach (Option A).

## Functional Requirements
| ID | Requirement | Acceptance Signal |
|----|-------------|-------------------|
| CEM-001 | Remove all direct `SecurityManager` usage from CLI launcher tests. | Static analysis (`rg SecurityManager cli/src/test`) returns no references; tests compile.
| CEM-002 | Ensure tests still cover both `main` success (no process termination) and failure (exit code equals Picocli usage). | Updated tests run via `./gradlew :cli:test` and assert exit behaviour using alternative mechanisms.
| CEM-003 | Maintain existing CLI command behaviour (no production code changes). | `git diff` shows changes limited to test fixtures/documentation; CLI integration tests remain green.

## Non-Functional Requirements
| ID | Requirement | Target |
|----|-------------|--------|
| CEM-NFR-001 | Solution must run on JDK 17+ without relying on deprecated APIs. | Build passes on current JDK without `SecurityManager` warnings/errors.

## Out of Scope
- Refactoring the CLI launcher to inject exit handlers (would be tracked separately).
- Introducing new testing dependencies.

Update this specification once implementation completes (Status → Complete).
