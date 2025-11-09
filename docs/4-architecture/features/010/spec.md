# Feature 010 – CLI Exit Testing Maintenance

_Status: Complete_
_Last updated: 2025-10-01_

## Overview
Retire uses of `SecurityManager` within the CLI launcher tests so the suite remains compatible with modern JDKs (where the API is disabled) while retaining coverage of exit-code behaviour. The change must remain test-scoped and avoid adding new dependencies.


## Goals
- Standardise CLI exit codes and command ergonomics so automation can rely on deterministic results.
- Add maintenance helpers that surface health diagnostics without changing core features.

## Non-Goals
- Does not introduce new protocols or cryptographic behaviour.
- Does not modify REST/UI components.


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

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S10-01 | Direct invocation tests exercise `OcraCliLauncher.main` success paths without `System.exit`, proving the launcher can be validated without deprecated SecurityManager hooks. |
| S10-02 | Spawned JVM harness captures usage/failure flows, asserting the exit code matches Picocli usage and the usage message is emitted when arguments are invalid. |
| S10-03 | Repository-wide scans confirm zero `SecurityManager` references in CLI tests so the suite runs on Java 17+ without deprecated APIs. |
| S10-04 | Production CLI entry point remains untouched; diffs stay isolated to tests/documentation so runtime behaviour and dependencies stay stable. |

## Out of Scope
- Refactoring the CLI launcher to inject exit handlers (would be tracked separately).
- Introducing new testing dependencies.

Update this specification once implementation completes (Status → Complete).
