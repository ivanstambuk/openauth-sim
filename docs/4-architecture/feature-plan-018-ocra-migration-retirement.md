# Feature Plan 018 – OCRA Migration Retirement

_Status: Complete_
_Last updated: 2025-10-03_

## Objective
Remove the unused OCRA schema-v0 upgrade path while keeping persistence builder wiring consistent across modules and documenting schema-v1 as the baseline.

Reference specification: `docs/4-architecture/specs/feature-018-ocra-migration-retirement.md`.

## Success Criteria
- Legacy migration classes/tests are removed and `OcraStoreMigrations.apply` continues to provide a single hook for MapDB builder configuration (OMR-001/OMR-002/OMR-003).
- Documentation (knowledge map, how-to guide, roadmap notes) reflects the schema-v1 baseline and no longer mentions schema-v0 upgrades (OMR-004).
- `./gradlew spotlessApply check` passes after each increment.

## Proposed Increments
- ☑ R1801 – Update `core-ocra` tests to describe the migration-less builder contract and ensure coverage focuses on successful store opening.
- ☑ R1802 – Remove `OcraRecordSchemaV0ToV1Migration`, adjust `OcraStoreMigrations`, and clean up references across modules.
- ☑ R1803 – Refresh documentation (knowledge map, how-to guide, roadmap/plan notes) and rerun full Gradle checks.

Each increment must stay within ≤30 minutes, lead with tests where possible, and log notes/telemetry adjustments below as work progresses.

## Checklist Before Implementation
- [x] Specification created with clarifications logged.
- [x] Open questions resolved and captured in spec.
- [x] Feature tasks drafted with test-first ordering.
- [x] Analysis gate rerun once plan/tasks align (pending updates below).

## Tooling Readiness
- `./gradlew spotlessApply check` remains the required gate per increment.
- `core-ocra` tests provide coverage of builder wiring; façade tests will be rerun via the Gradle check.

## Notes
- 2025-10-03 – R1801 updated `OcraStoreMigrationsTest` to expect legacy schema records to fail and added coverage for current schema success; `:core-ocra:test` failed as expected with legacy migration still present.
- 2025-10-03 – R1802 removed `OcraRecordSchemaV0ToV1Migration`, simplified `OcraStoreMigrations`, and reran `./gradlew :core-ocra:test` to confirm green.
- 2025-10-03 – R1803 refreshed roadmap, knowledge map, and persistence how-to docs; updated CLI expectations; and validated the full build with `./gradlew spotlessApply check`.

## Analysis Gate (2025-10-03)
- [x] Specification completeness – spec documents clarifications and updated requirements.
- [x] Open questions review – none tracked; confirmed clear as of 2025-10-03.
- [x] Plan alignment – increments map to OMR requirements after R1802 updates.
- [x] Tasks coverage – checklist mirrors R1801–R1803 scope with spec references.
- [x] Constitution compliance – removal avoided new dependencies and preserved façade wiring; CLI/REST/UI tests rerun via Gradle check.
- [x] Tooling readiness – recorded `./gradlew :core-ocra:test` outcomes for R1801 (red) and R1802 (green).
