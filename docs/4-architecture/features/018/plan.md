# Feature Plan 018 – OCRA Migration Retirement

_Linked specification:_ `docs/4-architecture/features/018/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
Retire the legacy schema-v0 migration path so the simulator assumes schema-v1 records everywhere, preserves the
`OcraStoreMigrations.apply` seam for future upgrades, and documents the persistence baseline. Success requires:
- All legacy migration classes/tests deleted with builders still functional (FR-018-01).
- Every façade and CLI helper continues to call `OcraStoreMigrations.apply` before opening MapDB stores (FR-018-02).
- Documentation/knowledge map/how-to content clearly states schema-v1 is the baseline (FR-018-03).
- `./gradlew --no-daemon spotlessApply check` passes after the cleanup.

## Scope Alignment
- **In scope:** Removing schema-v0 migration code/tests, verifying seam usage, refreshing docs/runbooks, running build
gates.
- **Out of scope:** New schema versions, migration tooling for external MapDB files, telemetry/event changes.

## Dependencies & Interfaces
- `core-ocra` module housing `OcraStoreMigrations`, MapDB builders, and associated tests.
- CLI/REST/UI modules that open stores through shared factories.
- Documentation artefacts (`knowledge-map.md`, how-to guides) that describe persistence assumptions.

## Assumptions & Risks
- **Assumptions:** No developer environments rely on schema-v0 files; any remaining ones can be regenerated from
  fixtures. Existing telemetry already captures persistence actions.
- **Risks / Mitigations:**
  - Hidden schema-v0 data might exist → communicate baseline in docs/how-to; keep seam invocation so reintroducing a
    migration later remains possible.
  - Removing classes could break builder wiring → run targeted `core-ocra` tests plus full Gradle checks.

## Implementation Drift Gate
- Record mapping between FR/NFR IDs and increments/tasks in this plan.
- Rerun `./gradlew --no-daemon spotlessApply check` after documentation updates.
- Capture builder/CLI test evidence plus doc diffs before closing Feature 018.

## Increment Map
1. **I1 – Test-first schema-v1 baseline (S-018-01, FR-018-01)**
   - _Goal:_ Ensure tests describe the desired migration-less behaviour before deleting code.
   - _Preconditions:_ Spec + tasks approved.
   - _Steps:_ Update `OcraStoreMigrationsTest` (and related fixtures) to fail when schema-v0 migrations exist; commit red
     state.
   - _Commands:_ `./gradlew --no-daemon :core-ocra:test` (expected red initially).
   - _Exit:_ Tests assert absence of schema-v0 helpers.

2. **I2 – Remove migration code & verify seam (S-018-01, S-018-02, FR-018-01, FR-018-02)**
   - _Goal:_ Delete migration classes, keep `OcraStoreMigrations.apply`, verify façades still call the seam.
   - _Steps:_ Remove legacy classes, update factories/CLI/REST references, run targeted module tests.
   - _Commands:_ `./gradlew --no-daemon :core-ocra:test`, `./gradlew --no-daemon :application:test`,
     `./gradlew --no-daemon :rest-api:test`.
   - _Exit:_ Builds/tests succeed with seam intact.

3. **I3 – Documentation + full gate (S-018-03, FR-018-03, NFR-018-01/02)**
   - _Goal:_ Update documentation artefacts and rerun the full Gradle gate.
   - _Steps:_ Refresh knowledge map/how-to/roadmap, capture persistence baseline statements, rerun spotless/check.
   - _Commands:_ `./gradlew --no-daemon spotlessApply check`.
   - _Exit:_ Docs updated, build green, Feature 018 ready for drift gate sign-off.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-018-01 | I1 / T-018-01, I2 / T-018-02 | Removal of migration code + updated tests.
| S-018-02 | I2 / T-018-02 | Seam verification across façades/CLI.
| S-018-03 | I3 / T-018-03 | Documentation + knowledge map updates.

## Analysis Gate
Completed on 2025-10-03 when clarifications landed. Re-run only if new scope emerges; current artefacts align with the
spec and tasks.

## Exit Criteria
- Legacy migration code/tests removed.
- `./gradlew --no-daemon spotlessApply check` green.
- Documentation/knowledge map/roadmap mention schema-v1 baseline.
- Feature tasks checklist complete.

## Follow-ups / Backlog
- None; future schema upgrades will be scoped under new features if required.
