# Feature 018 – OCRA Migration Retirement

_Status: Implemented_
_Last updated: 2025-10-03_

## Overview
Retire the legacy OCRA schema migration layer now that no MapDB stores using schema version 0 exist in the project. Simplify the persistence story by removing unused upgrade helpers and documenting the assumption that only schema-v1 records are supported going forward.


## Goals
- Provide tooling and documentation to retire legacy OCRA flows and migrate data into the unified simulator.
- Ensure persistence consistency and telemetry parity during the migration.

## Non-Goals
- Does not continue supporting deprecated OCRA entry points post-migration.
- Does not introduce new protocol capabilities.


## Clarifications
- 2025-10-03 – Project owner confirmed no production or shared MapDB files exist with schema version 0; all environments can start from schema v1 without data loss.
- 2025-10-03 – `OcraStoreMigrations.apply` must continue to act as the single hook façades use for persistence builders, even after migrations are removed.
- 2025-10-03 – Documentation (knowledge map, how-to guide) must explicitly state that OCRA persistence now requires schema v1 or newer.

## Branch & Scenario Matrix

| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S18-01 | Remove schema-v0 migration classes/tests so schema-v1 becomes the baseline. |
| S18-02 | Persistence factories/CLI helpers continue to call `OcraStoreMigrations.apply`, ensuring builders remain consistent. |
| S18-03 | Documentation (knowledge map, how-to, roadmap) communicates the schema-v1 baseline and lack of migrations. |

## Functional Requirements
| ID | Requirement | Acceptance Signal |
|----|-------------|-------------------|
| OMR-001 | Remove the `OcraRecordSchemaV0ToV1Migration` implementation and update `OcraStoreMigrations` so it no longer registers legacy migrations. | Core unit tests compile and pass without referencing the migration class; builders continue to open stores successfully. |
| OMR-002 | Update persistence factory and CLI helpers to ensure they still call `OcraStoreMigrations.apply`, preserving the central hook without dead wiring. | CLI/REST/UI integration tests open MapDB stores without failure and without referencing removed migrations. |
| OMR-003 | Remove or update tests that validated the legacy migration path. | `core-ocra` test suite passes with updated coverage ensuring builder application still works. |
| OMR-004 | Refresh documentation (knowledge map, how-to guide, roadmap/plan notes) to reflect that schema-v1 is the baseline and no automatic upgrades are present. | Docs mention the new baseline and no longer reference schema-v0 migrations. |

## Non-Functional Requirements
| ID | Requirement | Target |
|----|-------------|--------|
| OMR-NFR-001 | Safety | Ensure removal does not break existing bootstrap flows; run `./gradlew spotlessApply check` to verify all modules. |
| OMR-NFR-002 | Traceability | Specification, feature plan, and tasks must document the removal and its rationale before code changes merge. |

## Test Strategy
- Lean on existing `MapDbCredentialStore` creation tests in `core-ocra` and façade modules to confirm stores open correctly post-cleanup.
- Run `./gradlew spotlessApply check` after each increment to validate formatting, unit tests, and static analysis across modules.

## Dependencies & Risks
- Risk of undiscovered schema-v0 files in private developer sandboxes; communicate removal via documentation updates.
- Ensure no other modules instantiate the removed migration class; static imports should fail compilation if missed.

## Out of Scope
- Introducing new persistence schemas or versioning logic.
- Building tooling to upgrade legacy files outside the repository.

## Verification
- Codebase compiles without the legacy migration classes/tests.
- All Gradle checks pass.
- Documentation accurately reflects the new migration-less persistence story.

Update this specification as further clarifications emerge.
