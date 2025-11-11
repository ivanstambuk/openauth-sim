# Feature 013 Tasks – Toolchain & Quality Platform

_Status:_ In migration (Batch P3)  
_Last updated:_ 2025-11-11

## Checklist
- [x] T-013-01 – Merge legacy toolchain specs (Features 010–015/029/030/031) into the consolidated spec.
  - _Intent:_ Capture CLI exit harness, Maintenance coverage buffer, reflection policy, Java 17 refactor notes, architecture harmonization, SpotBugs/PMD enforcement, Gradle upgrades, and legacy-entry removal requirements plus verification commands.
  - _Verification commands:_ `rg "FR-013" docs/4-architecture/features/013/spec.md`, `git diff --stat docs/4-architecture/features/013/spec.md`.
  - _Notes:_ Spec now lists FR-013-01..10 and NFR-013-01..05.
- [x] T-013-02 – Refresh plan/tasks to enumerate required commands (`qualityGate`, `jacocoAggregatedReport`, `reflectionScan`, `spotbugsMain`, `pmdMain pmdTest`, Gradle wrapper/warning sweeps) and outstanding backlog items (coverage buffer restoration, PMD whitelist, etc.).
  - _Intent:_ Keep execution playbooks and backlog explicit for future agents.
  - _Verification commands:_ `rg "P3-I" docs/4-architecture/features/013/plan.md`, `rg "T-013" docs/4-architecture/features/013/tasks.md`.
  - _Notes:_ Include reminders to log wrapper updates and quality-gate runs in `_current-session.md`.
- [x] T-013-03 – Remove `docs/4-architecture/features/013/legacy/` after review; log `rm -rf …`, `ls docs/4-architecture/features/013`, and queued verification commands (spotless/qualityGate) in `_current-session.md` + `docs/migration_plan.md`.
  - _Intent:_ Complete the legacy absorption with audit logs.
  - _Verification commands:_ `rm -rf docs/4-architecture/features/013/legacy`, `ls docs/4-architecture/features/013`.
  - _Notes:_ Mention pending spotless/qualityGate reruns for Phase 2 close-out.
- [x] T-013-04 – Update roadmap/knowledge map/architecture graph/session log (docs/_current-session.md) with toolchain ownership and verification notes; queue final `./gradlew --no-daemon spotlessApply check` + `./gradlew --no-daemon qualityGate` after Feature 013 migration completes.
  - _Intent:_ Keep cross-cutting docs synchronized and prep the Phase 2 close-out commands.
  - _Verification commands:_ `rg "Feature 013" docs/4-architecture/roadmap.md docs/4-architecture/knowledge-map.md docs/migration_plan.md` (2025-11-11), `./gradlew --no-daemon spotlessApply check`, `./gradlew --no-daemon qualityGate`.
  - _Notes:_ Command outputs recorded in `_current-session.md`; session log (docs/_current-session.md) now includes the Batch P3 Phase 2 verification log.

### Legacy Coverage Checklist
- [x] T-013-L1 – Features 010/011/012/013/014/015 (CLI harness, maintenance buffers, reflection policy).
  - _Intent:_ Ensure FR-013-01..06 and NFR-013-02/03 memorialize the CLI exit harness, maintenance CLI coverage buffer, reflection ban, and wrapper guidance migrated from these features.
  - _Verification commands:_ `./gradlew --no-daemon :cli:test --tests "*OcraCliLauncherTest"`, `./gradlew --no-daemon qualityGate`, `./gradlew --no-daemon reflectionScan`.
  - _Notes:_ `_current-session.md` entries from 2025-11-11 capture the command outputs tied to P3-I1/P3-I2.
- [x] T-013-L2 – Features 029/030/031 (toolchain automation + quality gates).
  - _Intent:_ Confirm FR-013-07..09 and NFR-013-01/04/05 represent the PMD/SpotBugs/pmdMain/pmdTest orchestration, configuration-cache requirements, and CI parity from these features.
  - _Verification commands:_ `./gradlew --no-daemon pmdMain pmdTest`, `./gradlew --no-daemon spotbugsMain`, `./gradlew --no-daemon qualityGate`.
  - _Notes:_ Logged in `_current-session.md` and referenced by the Batch P3 Phase 2 session log (docs/_current-session.md) entry.

## Verification Log
- 2025-11-11 – `rg "Feature 013" docs/4-architecture/roadmap.md docs/4-architecture/knowledge-map.md docs/migration_plan.md`
- 2025-11-11 – `./gradlew --no-daemon spotlessApply check`
- 2025-11-11 – `./gradlew --no-daemon qualityGate`

## Notes / TODOs
- Track Maintenance CLI coverage buffer restoration and spotbugs/pmd follow-ups once tooling work resumes.
- Capture wrapper upgrade cadence + warning-mode sweeps each time Gradle versions bump.
- Consider adding automation to block `legacyEmit` or router shim regressions.
