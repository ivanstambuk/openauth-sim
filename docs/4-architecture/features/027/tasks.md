# Feature 027 Tasks – Unified Credential Store Naming

_Linked plan:_ `docs/4-architecture/features/027/plan.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

> Checklist mirrors the Feature 027 plan increments; each task stayed ≤30 minutes and recorded its verification commands when it closed.

## Checklist
- [x] T-027-01 – Governance sync (FR-027-01, S-027-01, S-027-04).  
  _Intent:_ Update roadmap, knowledge map, session snapshot, and spec clarifications to reference `credentials.db` exclusively.  
  _Verification:_ `./gradlew --no-daemon spotlessApply check` (2025-10-18).

- [x] T-027-02 – Persistence resolver simplification (FR-027-02, S-027-02).  
  _Intent:_ Update `CredentialStoreFactory.resolveDatabasePath` to always return `credentials.db`, remove legacy probes, and refresh `CredentialStoreFactoryTest`.  
  _Verification:_ `./gradlew --no-daemon :infra-persistence:test`, `./gradlew --no-daemon :application:test` (2025-10-19).

- [x] T-027-03 – Facade defaults & regression tests (FR-027-03, S-027-03).  
  _Intent:_ Replace CLI/REST constants/help text, refresh CLI/REST/Selenium tests, and run the regression stack.  
  _Verification:_ `./gradlew --no-daemon :cli:test`, `./gradlew --no-daemon :rest-api:test`, Selenium operator console suites, `./gradlew --no-daemon spotlessApply check` (2025-10-19).

- [x] T-027-04 – Documentation & migration guidance (FR-027-04, S-027-04).  
  _Intent:_ Update how-to guides, release notes, and knowledge map with manual rename instructions; remove references to automatic fallbacks.  
  _Verification:_ `./gradlew --no-daemon spotlessApply check` (2025-10-19).

## Verification Log
- 2025-10-19 – `./gradlew --no-daemon :infra-persistence:test :cli:test :rest-api:test spotlessApply check`
- 2025-10-18 – `./gradlew --no-daemon spotlessApply check` (documentation + governance sync)
- 2025-11-10 – `./gradlew --no-daemon spotlessApply check` (template migration verification)

## Notes / TODOs
- Manual migration guidance remains in `docs/2-how-to/configure-persistence-profiles.md`; no outstanding follow-ups.
