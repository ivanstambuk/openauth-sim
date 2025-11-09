# Feature 008 – OCRA Quality Automation

_Status:_ Complete  
_Last updated:_ 2025-10-31

## Overview
Establish automated quality gates for the OpenAuth Simulator’s OCRA stack so architecture boundaries remain enforced and behavioural regressions are detected early. This feature introduces boundary-verification rules and mutation/coverage thresholds that run both locally via Gradle and remotely in GitHub Actions. The workstream prioritises sustainable automation guardrails before introducing broader QA artefacts like visual badges or security scanning suites.


## Goals
- Expand automated quality gates (ArchUnit, SpotBugs, mutation tests) specific to OCRA modules.
- Codify quality thresholds and CI wiring so regressions are caught before release.

## Non-Goals
- Does not replace the existing Gradle build or formatter stack.
- Does not add user-facing features; it focuses on automation.


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

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S08-01 | ArchUnit rules guard module boundaries so CLI/REST/UI cannot bypass the shared application layer or reach core internals directly. |
| S08-02 | Jacoco aggregation enforces ≥90% line/branch thresholds for OCRA modules and fails builds when coverage drops. |
| S08-03 | PIT mutation testing executes against OCRA packages with ≥85% survival threshold, blocking merges when the score regresses. |
| S08-04 | `./gradlew qualityGate` aggregates boundary, coverage, mutation, lint, and formatting checks into one local command. |
| S08-05 | GitHub Actions workflow runs the same quality gate per push/PR, gating merges on identical thresholds. |
| S08-06 | Documentation/how-to guides explain how to run, interpret, and troubleshoot the quality gate (including skip parameters). |

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
