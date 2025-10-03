# Feature 015 – SpotBugs Dead-State Enforcement Tasks

_Status: Complete_
_Last updated: 2025-10-03_

## Tasks
| ID | Task | Related Requirements | Status |
|----|------|----------------------|--------|
| T1501 | Register Feature 015 in roadmap/knowledge map, confirm `open-questions.md` stays empty. | SB-001–SB-003 | ✅ (2025-10-03 – Roadmap row added, knowledge map entry appended, open questions verified empty) |
| T1502 | Add shared SpotBugs include filter + Gradle wiring; run `./gradlew :application:spotbugsMain --rerun-tasks` to observe the expected failure with current violations. | SB-001, SB-002 | ✅ (2025-10-03 – Added `config/spotbugs/dead-state-include.xml`, configured `SpotBugsExtension`, command failed with `URF_UNREAD_FIELD` on `clock`) |
| T1503 | Remediate dead-state findings (e.g., unused `Clock`), rerun `./gradlew :application:spotbugsMain --rerun-tasks` then `./gradlew spotlessApply check` to verify green status. | SB-002 | ✅ (2025-10-03 – Removed unused `Clock` dependency via constructor signature update, spotbugs/check now pass) |
| T1504 | Update documentation (analysis gate checklist + tooling guide) describing the new detectors and suppression policy; rerun `./gradlew spotlessApply check` post-doc changes. | SB-003 | ✅ (2025-10-03 – Updated analysis gate checklist + quality gate guide, reran `./gradlew spotlessApply check`) |
| T1505 | Capture runtime impact/notes in feature plan and synchronise final artefacts (plan/tasks/spec). | SB-001–SB-003 | ✅ (2025-10-03 – Added command notes to feature plan and synced spec/plan/tasks) |
| T1506 | Add PMD `UnusedPrivateField`, address violations (e.g., remove unused constants), rerun `./gradlew :rest-api:pmdTest` and `./gradlew check`. | SB-004 | ✅ (2025-10-03 – Rule added to PMD ruleset, removed `PIN_SUITE`, `:rest-api:pmdTest` green) |
| T1507 | Add PMD `UnusedPrivateMethod`, remove dead helpers (e.g., `inlineCredential()`), rerun `./gradlew :rest-api:pmdTest` and `./gradlew check`. | SB-004 | ✅ (2025-10-03 – Rule enforced; commands rerun, no violations) |

Update the status column as tasks complete, keeping each increment ≤10 minutes and sequencing validation commands before code implementation when feasible.
