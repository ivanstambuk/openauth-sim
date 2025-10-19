# Feature 029 – PMD Rule Hardening

_Status: Planned_  
_Last updated: 2025-10-19_

## Overview
Expand the PMD ruleset to surface high-signal findings in the core domain and service layers without overwhelming builders, adapters, or fluent APIs. The effort focuses on enabling a curated set of error-prone, best-practice, and design rules while carving out intentional exclusions where chaining is idiomatic.

## Goals
- Enable the following rules in `config/pmd/ruleset.xml` and ensure they gate `pmdMain`/`pmdTest` across all modules:
  - **Error-prone:** `AvoidCatchingNPE`, `AssignmentInOperand`, `BrokenNullCheck`, `UseEqualsToCompareStrings`.
  - **Best practices:** `LawOfDemeter`, `LooseCoupling`, `PositionLiteralsFirstInComparisons`, `SwitchStmtsShouldHaveDefault`.
  - **Design:** `DataClass`, `ExcessiveMethodLength`, `CyclomaticComplexity`, `GodClass`, `CouplingBetweenObjects`.
- Extend error-prone coverage with `NonSerializableClass`, enforcing it for domain and service layers while supplying targeted excludes for DTOs/builders in other modules.
- Scope `LawOfDemeter` enforcement to domain and service packages as a locality-of-behaviour heuristic and introduce `<exclude-pattern>` globs for fluent APIs, builders, DTO/adapters, and Stream pipelines where chaining is expected.
- Document the rule coverage, exclusions, and remediation expectations in the contributor docs so future agents understand how to interpret violations.
- Ensure `./gradlew --no-daemon spotlessApply check` (including PMD tasks) passes once the rules are enabled and the codebase is updated.

## Non-Goals
- Rewriting module architecture or refactoring unrelated code purely to satisfy the new rules.
- Adjusting SpotBugs, Checkstyle, or other static analysis tooling beyond incidental documentation updates.
- Introducing new dependencies; PMD configuration changes must use existing tooling.

## Clarifications
- 2025-10-19 – Treat `LawOfDemeter` as a domain/service locality heuristic: enable it for those layers, whitelist fluent APIs, builder patterns, Streams, and adapter/DTO packages via `<exclude-pattern>` rules, and track the whitelist work as a dedicated task. (Owner directive.)
- 2025-10-19 – Upgrade the PMD toolchain to the latest supported 7.x release (currently 7.17.0) before enabling the additional rules so we can baseline behaviour and address any migration fallout within this feature. Account for the PMD 7 CLI/reporting changes during planning. (Owner directive.)
- 2025-10-19 – Enable the PMD `NonSerializableClass` rule for core domain and application service modules, using exclude patterns to suppress DTO/builder/adaptor classes in other layers; remediation should focus on making domain/service classes explicitly serializable where required or documenting intentional non-serializable types. (Option B approved by owner.)

## Architecture & Design
- Extend `config/pmd/ruleset.xml` with the targeted rule references grouped by category to maintain readability.
- Add a dedicated suppression/whitelist resource (for example, `config/pmd/law-of-demeter-excludes.txt`) referenced via `<exclude-pattern>` so fluent interfaces remain compliant without masking genuine “train-wreck” call chains in domain logic.
- Update Gradle PMD configuration, if required, to ensure the new rule resources load correctly and can be toggled per module when exceptions are unavoidable (for example, via `pmd { ruleSetFiles(...) }` overrides).
- Stage code probes that exercise PMD against `core`, `application`, and related service packages, documenting any systemic violations before remediation.

## Test Strategy
- `./gradlew --no-daemon pmdMain pmdTest`
- `./gradlew --no-daemon spotlessApply check`
- Targeted module checks after remediation (for example, `:core:test`, `:application:test`, `:rest-api:test`) when code changes are introduced to satisfy new rules.

## Rollout & Regression
- Phase in rules incrementally: update configuration, run PMD to capture baseline violations, remediate findings module-by-module, and add exclusions only when they meet the documented fluent-interface criteria.
- Record any intentional exclusions or follow-up remediation items in the feature plan/tasks so future sessions maintain visibility.
- Update contributor guidance (`AGENTS.md`, relevant how-to/quality docs) once the new rules are active, highlighting how to interpret Law of Demeter violations and when to extend the whitelist.
