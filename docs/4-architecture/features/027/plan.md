# Feature Plan 027 – Unified Credential Store Naming

_Linked specification:_ `docs/4-architecture/features/027/spec.md`  
_Linked tasks:_ `docs/4-architecture/features/027/tasks.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
Deliver a seamless shift to the shared `credentials.db` default so every facade persists credentials in the same file
without opaque fallbacks. Success means:
- Persistence factory + infra code always pick `credentials.db` unless a custom path is supplied (FR-027-02).
- CLI/REST/UI copy, help text, and tests reference the shared default (FR-027-03).
- Governance artefacts + how-to guides highlight the manual migration requirement, with no lingering legacy filenames (FR-027-01/04).
- Regression suite `./gradlew --no-daemon :infra-persistence:test :cli:test :rest-api:test spotlessApply check` remains green.

## Scope Alignment
- **In scope:** Persistence factory logic, CLI/REST configuration defaults, documentation updates (roadmap, knowledge map, how-to, release notes), session snapshot, migration guidance.
- **Out of scope:** Schema changes, automatic file migration, encryption, or telemetry format changes.

## Dependencies & Interfaces
- `infra-persistence` (`CredentialStoreFactory`) and consumers in application/REST/CLI modules.
- CLI help generators and REST configuration beans referencing default paths.
- Documentation stack (`docs/2-how-to`, roadmap, knowledge map, session snapshot).

## Assumptions & Risks
- **Assumptions:** Operators can manually rename/relocate legacy `*-credentials.db` files; existing MapDB schema stays compatible.
- **Risks:**
  - Operators miss the manual migration guidance → Mitigate via docs + startup log reminder.
  - Tests continue referencing legacy filenames → Mitigate by auditing all modules after factory change (Increment I3).

## Implementation Drift Gate
- Evidence bundle (captured 2025-10-29):
  - `CredentialStoreFactoryTest` diff proving deterministic default.
  - CLI/REST/Selenium screenshots/logs showing `credentials.db` references only.
  - Docs (how-to, roadmap, knowledge map, session snapshot) mentioning manual migration.
  - Verification log for `./gradlew --no-daemon :infra-persistence:test :cli:test :rest-api:test spotlessApply check`.
- Gate remains satisfied; rerun if persistence strategy changes again.

## Increment Map
1. **I1 – Governance & documentation sync (S-027-01, S-027-04)**
   - Update roadmap, knowledge map, current-session, and spec clarifications with the unified filename.
   - Commands: documentation edits + `./gradlew --no-daemon spotlessApply check`.
   - Status: Completed 2025-10-18.
2. **I2 – Persistence factory simplification (S-027-02)**
   - Update `CredentialStoreFactory.resolveDatabasePath` to always return `credentials.db`; delete legacy detection logic.
   - Refresh `CredentialStoreFactoryTest` coverage.
   - Commands: `./gradlew --no-daemon :infra-persistence:test :application:test`.
   - Status: Completed 2025-10-19.
3. **I3 – Facade defaults & regression tests (S-027-03)**
   - Replace CLI/REST constants, help text, and tests; sync Selenium expectations.
   - Commands: `./gradlew --no-daemon :cli:test`, `./gradlew --no-daemon :rest-api:test`, Selenium suites, `./gradlew --no-daemon spotlessApply check`.
   - Status: Completed 2025-10-19.
4. **I4 – Migration guidance & release notes (S-027-04)**
   - Update how-to guides, release notes, and roadmap entries with manual migration steps.
   - Commands: documentation edits + `./gradlew --no-daemon spotlessApply check`.
   - Status: Completed 2025-10-19.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-027-01 | I1 / T-027-01 | Governance artefacts updated.
| S-027-02 | I2 / T-027-02 | Persistence factory + tests.
| S-027-03 | I3 / T-027-03 | CLI/REST/UI defaults + regression suite.
| S-027-04 | I1, I4 / T-027-04 | Documentation/how-to guidance.

## Analysis Gate
- Completed 2025-10-18 once clarifications and governance updates landed.
- No re-run required unless another persistence change is proposed.

## Exit Criteria
- FR-027-01…FR-027-04 satisfied with code/tests/docs evidence.
- Regression suite (`./gradlew --no-daemon :infra-persistence:test :cli:test :rest-api:test spotlessApply check`) executed and logged.
- Roadmap/knowledge map/how-to/session snapshot reference only `credentials.db`.
- Owner acknowledged manual migration guidance.

## Follow-ups / Backlog
- None; future persistence enhancements (encryption, alternative stores) belong to new features.
