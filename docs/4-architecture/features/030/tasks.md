# Feature 030 Tasks – Gradle 9 Upgrade

_Linked plan:_ `docs/4-architecture/features/030/plan.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

> Checklist mirrors the plan increments; each entry stayed ≤30 minutes and records the verification commands used when it closed.

## Checklist
- [x] T-030-01 – Governance setup (FR-030-01, S-030-01, S-030-04).  
  _Intent:_ Create spec/plan/tasks, capture owner approval, and update roadmap/knowledge map/session snapshot.  
  _Verification:_ `./gradlew --warning-mode=all clean check` (baseline, 2025-10-19).

- [x] T-030-02 – Pre-upgrade warning sweep (FR-030-01, S-030-01).  
  _Intent:_ Run `./gradlew --warning-mode=all clean check` under Gradle 8.10 and document any deprecations (none observed).  
  _Verification:_ `./gradlew --warning-mode=all clean check` (Gradle 8.10, 2025-10-19).

- [x] T-030-03 – Wrapper upgrade & plugin bump (FR-030-02, S-030-02).  
  _Intent:_ Execute `./gradlew wrapper --gradle-version 9.1.0 --distribution-type bin`, verify wrapper artefacts, and bump `info.solidsoft.pitest` to 1.19.0-rc.2.  
  _Verification:_ `./gradlew wrapper --gradle-version 9.1.0 --distribution-type bin` (2025-10-19).

- [x] T-030-04 – Post-upgrade validation (FR-030-03, S-030-03).  
  _Intent:_ Run `./gradlew --warning-mode=all clean check` with Gradle 9.1.0 plus targeted CLI/REST/Selenium tests and `./gradlew --configuration-cache help`.  
  _Verification:_  
  - `./gradlew --warning-mode=all clean check` (Gradle 9, 2025-10-19)  
  - `./gradlew --configuration-cache help` (2025-10-19)

- [x] T-030-05 – Artifact review & documentation sync (FR-030-04, S-030-04).  
  _Intent:_ Inspect reproducible artefacts (no diffs), update roadmap/knowledge map/session snapshot/migration tracker, and close the feature.  
  _Verification:_ `./gradlew --warning-mode=all clean check` (spotless confirmation, 2025-10-19).

## Verification Log
- 2025-10-19 – `./gradlew --warning-mode=all clean check` (Gradle 9.1.0)
- 2025-10-19 – `./gradlew --configuration-cache help`
- 2025-10-19 – `./gradlew wrapper --gradle-version 9.1.0 --distribution-type bin`
- 2025-10-19 – `./gradlew --warning-mode=all clean check` (Gradle 8.10 baseline)

## Notes / TODOs
- PIT plugin pinned to 1.19.0-rc.2 for Gradle 9 compatibility; unlock in future features if a final release lands.
