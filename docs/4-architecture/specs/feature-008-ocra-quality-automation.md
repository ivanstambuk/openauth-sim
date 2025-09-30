# Feature 008 – OCRA Quality Automation

_Status: Draft_
_Last updated: 2025-09-30_

## Overview
Establish automated quality gates for the OpenAuth Simulator’s OCRA stack so architecture boundaries remain enforced and behavioural regressions are detected early. This feature introduces boundary-verification rules and mutation/coverage thresholds that run both locally via Gradle and remotely in GitHub Actions. The workstream prioritises sustainable automation guardrails before introducing broader QA artefacts like visual badges or security scanning suites.

## Clarifications
- 2025-09-30 – Scope will cover architecture boundary enforcement plus mutation and coverage thresholds; CI badge wiring and additional QA tooling remain out of scope for now.
- 2025-09-30 – Quality automation must run consistently in both local Gradle workflows and the GitHub Actions pipeline to keep developer and CI feedback aligned.
- 2025-09-30 – Prefer the existing tooling stack: ArchUnit-style rules, PIT mutation testing, Jacoco coverage thresholds, and gitleaks for secret scanning expansion when needed.
- 2025-09-30 – Coverage thresholds remain set at ≥90% line/branch; we will add new tests to raise coverage to the target rather than relaxing thresholds.

## Functional Requirements
| ID | Requirement | Acceptance Signal |
|----|-------------|-------------------|
| QA-OCRA-001 | Define and enforce ArchUnit (or equivalent) rules that guard module boundaries between `core/`, `cli/`, `rest-api/`, and `ui/`. | Running the quality task fails when an illegal dependency is introduced; passes when boundaries hold. |
| QA-OCRA-002 | Integrate PIT mutation testing for OCRA domain and facade modules with a minimum surviving mutation threshold (target ≥ 85%). | Mutation test run included in the quality pipeline reports aggregated metrics and fails when threshold unmet. |
| QA-OCRA-003 | Establish Jacoco coverage thresholds (e.g., line/branch ≥ 90% for core OCRA classes) enforced during CI and local runs. | Quality task fails if coverage drops below configured thresholds; reports surfaced in build output. |
| QA-OCRA-004 | Provide a single Gradle entry point (e.g., `./gradlew qualityGate`) aggregating ArchUnit, mutation, coverage, and existing lint/checkstyle tasks. | Command executes all configured checks locally with pass/fail status. |
| QA-OCRA-005 | Wire the same checks into GitHub Actions so pull requests and pushes run the quality gate automatically. | GitHub Actions workflow reports success/failure for the aggregated quality gate; failure blocks merges. |
| QA-OCRA-006 | Document how to run and interpret the quality gate in operator/developer guides, including thresholds and troubleshooting. | Updated docs outline prerequisites, runtime expectations, and remediation steps. |

## Non-Functional Requirements
| ID | Requirement | Target |
|----|-------------|--------|
| QA-NFR-001 | Runtime | Local quality gate completes within 10 minutes on a developer laptop (PIT configured for targeted packages or incremental runs). |
| QA-NFR-002 | Maintainability | Thresholds and module definitions are parameterised/configurable, easing future tuning. |
| QA-NFR-003 | Transparency | Mutation/coverage reports stored under `build/reports/quality/` (or similar) and linked from documentation. |

## Test Strategy
- Unit tests covering new ArchUnit rules to validate failure scenarios.
- Integration tests or build-verification scripts ensuring PIT and Jacoco thresholds trip when seeded with regressions.
- GitHub Actions workflow dry-run documented before enabling required status checks.

## Dependencies & Risks
- PIT and Jacoco increase build time; mitigate by scoping packages and enabling incremental execution.
- GitHub Actions runtime must stay within hosted runner limits; consider nightly full runs with PR-level subsets if necessary.
- Future security tooling (e.g., gitleaks) deferred but should be kept compatible with the quality gate structure.

## Out of Scope
- Publishing status badges to README or external dashboards (tracked separately).
- Introducing additional security scanning beyond confirming compatibility with existing gitleaks configuration.
- Applying quality automation to non-OCRA modules until scope expands via new specifications.

## Verification
- `./gradlew qualityGate` exits 0 when thresholds met, non-zero otherwise.
- GitHub Actions workflow runs on push/PR and enforces identical thresholds.
- Documentation updated (e.g., `docs/5-operations/tool-reference-card.md` and relevant how-to) describing the gate.

Update this specification as further clarifications or scope adjustments occur.
