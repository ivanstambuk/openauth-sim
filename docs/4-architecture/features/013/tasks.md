# Feature 013 Tasks – Toolchain & Quality Platform

_Status:_ Complete  
_Last updated:_ 2025-11-13

> Keep this checklist aligned with the feature plan increments. Stage tests before implementation, record verification commands beside each task, and prefer bite-sized entries (≤90 minutes).
> When referencing requirements, keep feature IDs (`F-`), non-goal IDs (`N-`), and scenario IDs (`S-<NNN>-`) inside the same parentheses immediately after the task title (omit categories that do not apply).

## Checklist
- _No active tasks._ Capture the next toolchain/quality increment (≤90 min plan effort) with explicit FR/NFR/Scenario references before editing docs or automation scripts.

## Verification Log
- 2025-11-13 – `./gradlew --no-daemon spotlessApply check` (toolchain drift gate; 11 s, 96 tasks: 2 executed, 94 up-to-date)
- 2025-11-13 – `./gradlew --no-daemon qualityGate` (toolchain closure gate; 9 s, 40 tasks: 1 executed, 39 up-to-date)
- 2025-11-11 – `rg "Feature 013" docs/4-architecture/roadmap.md docs/4-architecture/knowledge-map.md`
- 2025-11-11 – `./gradlew --no-daemon spotlessApply check`
- 2025-11-11 – `./gradlew --no-daemon qualityGate`

## Notes / TODOs
- Track Maintenance CLI coverage buffer restoration and spotbugs/pmd follow-ups once tooling work resumes.
- Capture wrapper upgrade cadence + warning-mode sweeps each time Gradle versions bump.
- Consider adding automation to block `legacyEmit` or router shim regressions.
- Legacy Coverage – T-013-L1 (Features 010/011/012/013/014/015 CLI harness, maintenance buffers, reflection policy). _Intent:_ Ensure FR-013-01..06 and NFR-013-02/03 memorialize the CLI exit harness, maintenance CLI coverage buffer, reflection ban, and wrapper guidance migrated from these features. _Verification commands:_ `./gradlew --no-daemon :cli:test --tests "*OcraCliLauncherTest"`, `./gradlew --no-daemon qualityGate`, `./gradlew --no-daemon reflectionScan`. _Notes:_ `_current-session.md` entries from 2025-11-11 capture the command outputs tied to P3-I1/P3-I2.
- Legacy Coverage – T-013-L2 (Features 029/030/031 toolchain automation + quality gates). _Intent:_ Confirm FR-013-07..09 and NFR-013-01/04/05 represent the PMD/SpotBugs/pmdMain/pmdTest orchestration, configuration-cache requirements, and CI parity from these features. _Verification commands:_ `./gradlew --no-daemon pmdMain pmdTest`, `./gradlew --no-daemon spotbugsMain`, `./gradlew --no-daemon qualityGate`. _Notes:_ Logged in `_current-session.md` and referenced by the consolidation session log (docs/_current-session.md) entry.
