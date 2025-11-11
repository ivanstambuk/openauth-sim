# Feature 032 – Palantir Formatter Adoption

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/032/plan.md` |
| Linked tasks | `docs/4-architecture/features/032/tasks.md` |
| Roadmap entry | #13 – Toolchain & Quality Platform |

## Overview
Adopt Palantir Java Format 2.78.0 as the canonical formatter across Spotless, managed Git hooks, and IDE contributor
guidance so every JVM module follows the same 120-character style. The change swaps Spotless configuration, refreshes
locks, updates documentation, and sequences the repository-wide reformat once the tooling change is verified.

## Clarifications
- 2025-10-19 – Option B approved: pin Palantir Java Format to 2.78.0 (latest Spotless-supported release) and drop
  google-java-format across the toolchain.

## Goals
- G-032-01 – Update Spotless + version catalog entries to use Palantir Java Format 2.78.0.
- G-032-02 – Refresh dependency locks and managed Git hooks so automated tooling invokes Palantir consistently.
- G-032-03 – Execute the staged repository-wide reformat under Palantir and verify with `spotlessApply check`.
- G-032-04 – Document the formatter policy, IDE setup, and rebase guidance for contributors.

## Non-Goals
- N-032-01 – Adjusting other lint/quality tools (PMD, Checkstyle, SpotBugs) beyond incidental documentation edits.
- N-032-02 – Introducing module-specific formatter exceptions or fallback formatters.
- N-032-03 – Upgrading Java toolchains (remains Java 17).

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-032-01 | Spotless configuration uses `palantirJavaFormat("2.78.0")` with the pin recorded in `libs.versions.toml` (S-032-01). | Spotless tasks run Palantir formatter with 120-character wrap. | `./gradlew --no-daemon spotlessCheck` reports Palantir diffs pre-reformat. | Spotless still references google-java-format. | Build logs show Palantir invocation; no telemetry change. | Clarifications 2025-10-19. |
| FR-032-02 | Dependency locks refresh to capture Palantir artifacts and drop google-java-format (S-032-02). | Root + module lockfiles include Palantir coordinates; Google formatter removed. | `./gradlew --no-daemon --write-locks spotlessApply check` regenerates locks; git diff confirms replacements. | Locks still reference google-java-format. | N/A. | G-032-02. |
| FR-032-03 | Managed Git hooks and contributor tooling reference Palantir 2.78.0 (S-032-03). | `githooks/pre-commit` logs mention Palantir; dry run formats staged files via Spotless. | Manual hook run during the feature. | Hooks still refer to google-java-format. | Hook output states Palantir usage. | G-032-02. |
| FR-032-04 | Repository-wide reformat executed with Palantir; `spotlessApply check` returns green (S-032-04). | `./gradlew --no-daemon spotlessApply` followed by `spotlessApply check` succeeds. | Command logs stored in tasks/plan. | Formatting violations remain or build fails. | N/A. | G-032-03. |
| FR-032-05 | Documentation (AGENTS, CONTRIBUTING, roadmap, knowledge map, IDE guides) describes the Palantir policy and rollout plan (S-032-05). | Docs explain formatter pin, IDE setup, rebase guidance. | Documentation review + roadmap update. | Docs still mention google-java-format or lack guidance. | N/A. | G-032-04. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-032-01 | Maintain a green build (`./gradlew --no-daemon spotlessApply check`) after the formatter swap. | Constitution QA requirement. | Command success + recorded logs. | Gradle, Spotless plugin. | Project constitution. |
| NFR-032-02 | Provide IDE configuration guidance matching Palantir formatting. | Contributor experience. | Docs updated; IDE appendix references Palantir plugin. | Documentation stack. | G-032-04. |
| NFR-032-03 | Keep dependency locks reproducible with Palantir artifacts pinned. | Deterministic builds. | Lockfiles diff show only Palantir additions. | Gradle lockfiles. | G-032-02. |

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-032-01 | Spotless + version catalog switch to Palantir Java Format 2.78.0. |
| S-032-02 | Dependency locks refreshed to reflect Palantir artifacts. |
| S-032-03 | Managed Git hooks + contributor tooling reference the new formatter. |
| S-032-04 | Repository reformat executed and validated under Palantir. |
| S-032-05 | Documentation/knowledge artefacts outline the policy, IDE setup, and rollout guidance. |

## Test Strategy
- Pre-swap validation: `./gradlew --no-daemon spotlessCheck` (expect Palantir diffs).
- Lock refresh: `./gradlew --no-daemon --write-locks spotlessApply check` plus targeted module `compileJava` tasks as needed.
- Reformat verification: `./gradlew --no-daemon spotlessApply check` after applying Palantir format.
- Manual hook dry-run to confirm Palantir invocation on staged files.

## Interface & Contract Catalogue
### Build Artefacts
| ID | Description | Modules |
|----|-------------|---------|
| BA-032-01 | `gradle/libs.versions.toml` entry `palantirJavaFormat = "2.78.0"`. | Root build |
| BA-032-02 | Spotless configuration in `build.gradle.kts` referencing `palantirJavaFormat`. | Root build |
| BA-032-03 | `githooks/pre-commit` messaging updated to Palantir reference. | Repository hook |

## Documentation Deliverables
- Update `AGENTS.md`, `CONTRIBUTING.md`, formatting how-tos, roadmap, knowledge map, `_current-session.md`, and migration plan with the Palantir policy and rollout sequencing.
- Capture rebase/conflict mitigation notes in the feature plan/tasks.

## Fixtures & Sample Data
- Not applicable (formatter/tooling change only).

## Spec DSL
```
scenarios:
  - id: S-032-01
    focus: spotless-config
  - id: S-032-02
    focus: dependency-locks
  - id: S-032-03
    focus: tooling-hooks
  - id: S-032-04
    focus: repo-reformat
  - id: S-032-05
    focus: documentation
requirements:
  - id: FR-032-01
    maps_to: [S-032-01]
  - id: FR-032-02
    maps_to: [S-032-02]
  - id: FR-032-03
    maps_to: [S-032-03]
  - id: FR-032-04
    maps_to: [S-032-04]
  - id: FR-032-05
    maps_to: [S-032-05]
non_functional:
  - id: NFR-032-01
    maps_to: [S-032-04]
  - id: NFR-032-02
    maps_to: [S-032-05]
  - id: NFR-032-03
    maps_to: [S-032-02]
```
