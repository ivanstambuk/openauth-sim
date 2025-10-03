# Feature 015 – SpotBugs Dead-State Enforcement

_Status: Complete_
_Last updated: 2025-10-03_

## Overview
Harden the static-analysis quality gate by promoting SpotBugs dead-state detectors so unread or uninitialised fields fail the build. The feature aligns local development and CI behaviour by adding a shared include filter, wiring the Gradle SpotBugs tasks to consume it, and documenting the expanded guardrails. This keeps latent state from drifting into production modules, complementing existing Checkstyle/PMD coverage.

## Clarifications
- 2025-10-03 – Engineering approved enabling SpotBugs patterns `URF_UNREAD_FIELD`, `URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD`, `UUF_UNUSED_FIELD`, `UWF_UNWRITTEN_FIELD`, and `NP_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD`, failing the build when triggered. `SS_SHOULD_BE_STATIC` and `SI_INSTANCE_BEFORE_FINALS_ASSIGNED` remain optional follow-ups.
- 2025-10-03 – Enforcement applies to all JVM subprojects to maintain consistency; suppressions must be justified inline with `@SuppressFBWarnings` and referenced in documentation when used.
- 2025-10-03 – Documentation updates will live in the quality automation section (`docs/5-operations/analysis-gate-checklist.md` and the developer tooling guide) so future contributors understand the new failure modes.
- 2025-10-03 – PMD enforces `UnusedPrivateField` and `UnusedPrivateMethod` to complement SpotBugs dead-state checks with source-level validation (tests included).

## Functional Requirements
| ID | Requirement | Acceptance Signal |
|----|-------------|-------------------|
| SB-001 | Provide a SpotBugs include filter listing approved dead-state bug patterns and share it across all modules. | Filter file present under `config/spotbugs/`, referenced by every `spotbugs*` task. |
| SB-002 | Configure Gradle SpotBugs tasks to fail when any included dead-state bug pattern is reported. | Running `./gradlew :application:spotbugsMain` on the current codebase fails before cleanup; rerunning after remediation passes. |
| SB-003 | Document the new enforcement in existing quality gate runbooks with remediation guidance. | Updated docs describe detectors, failure examples, and suppression policy. |
| SB-004 | Enforce PMD `UnusedPrivateField` and `UnusedPrivateMethod` across JVM modules (main + test sources). | `./gradlew pmdMain pmdTest` fails on unused private members or helpers until they are removed or justified; rule noted in quality gate guide. |

## Non-Functional Requirements
| ID | Requirement | Target |
|----|-------------|--------|
| SB-NFR-001 | Build impact | SpotBugs analysis increases total `./gradlew check` runtime by ≤15%. |
| SB-NFR-002 | Suppressions | Any required suppressions reference a justification and link back to this feature plan. |
| SB-NFR-003 | Consistency | Local developer runs and CI quality gates use the same filter file to prevent divergent results. |

## Test Strategy
- Drive the new detector by running SpotBugs before remediation to observe expected failures (e.g., the unused `Clock` field). Capture command output in the feature tasks log.
- After cleanup, rerun `./gradlew spotlessApply check` to verify the build now passes with the detector active.
- Perform a targeted regression by temporarily reintroducing an unread field in a sandbox branch to ensure the detector fails as expected; document observations in the plan notes.

## Dependencies & Risks
- Initial activation will fail the build until existing violations are cleaned or suppressed; schedule cleanup within the same increment.
- Additional detectors may expose new hotspots (e.g., generated DTOs); be prepared to evaluate whether suppressions or refactors are appropriate.
- SpotBugs runtime may rise slightly; monitor CI timings and adjust effort level if needed.

## Out of Scope
- Enabling optional patterns such as `SS_SHOULD_BE_STATIC` or `SI_INSTANCE_BEFORE_FINALS_ASSIGNED` (tracked as future enhancements).
- Introducing new linting tooling beyond SpotBugs; Checkstyle/PMD configurations remain unchanged in this feature.

## Verification
- `./gradlew spotlessApply check` succeeds with the include filter in place and no open findings.
- Documentation updates merged alongside code so the analysis gate reflects the new guardrails.
- 2025-10-03 – Verified `./gradlew :rest-api:pmdTest`, `./gradlew pmdMain`, and `./gradlew check` with dead-state detectors active.

Update this specification as clarifications evolve or scope expands.
