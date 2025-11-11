# Feature 029 Tasks – PMD Rule Hardening

| Field | Value |
|-------|-------|
| Status | Planned |
| Last updated | 2025-11-10 |
| Linked plan | `docs/4-architecture/features/029/plan.md` |

> Keep entries ≤90 minutes. Stage failing tests or PMD runs before implementation and capture verification commands beside each task.

## Checklist
- [x] T2901 – PMD 7 upgrade baseline (S29-01).  
  _Intent:_ Raise PMD to 7.17.0, refresh dependency locks, and archive baseline reports.  
  _Verification commands:_  
  - `./gradlew --no-daemon --write-locks pmdMain pmdTest`  
  _Notes:_ 2025-10-19 – Upgrade completed; baseline violations (`AssignmentInOperand`) captured and remediated.

- [ ] T2902 – Governance sync (S29-02).  
  _Intent:_ Finalise spec/plan/tasks, update roadmap and `_current-session`, and log clarifications.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check` (docs touched)  
  _Notes:_ Pending after template migration.

- [x] T2903 – Ruleset expansion & baseline (S29-02).  
  _Intent:_ Append approved rules (including `NonExhaustiveSwitch`) and run PMD to record baseline.  
  _Verification commands:_  
  - `./gradlew --no-daemon pmdMain pmdTest`  
  _Notes:_ 2025-10-19 – Dry run showed zero NonExhaustiveSwitch violations; rule enabled permanently.

- [ ] T2904 – Law-of-Demeter scoping (S29-03).  
  _Intent:_ Add whitelist resource, restrict enforcement to domain/service packages, and document exclude patterns.  
  _Verification commands:_  
  - `./gradlew --no-daemon pmdMain`  
  _Notes:_ Capture glob rationale in the spec appendix.

- [ ] T2905 – Domain remediation (S29-04).  
  _Intent:_ Fix findings in `core/`, `core-ocra/`, and related domain modules; add regression tests.  
  _Verification commands:_  
  - `./gradlew --no-daemon :core:test :core-ocra:test pmdMain`  
  _Notes:_ Reference code pointers in the plan’s drift-gate appendix.

- [ ] T2906 – Service & facade remediation (S29-05).  
  _Intent:_ Address violations in `application/`, `infra-persistence/`, `cli/`, `rest-api/`, `ui/`; justify any excludes.  
  _Verification commands:_  
  - `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain`  
  _Notes:_ Track per-module summaries in this file for future audits.

- [ ] T2907 – Documentation updates (S29-06).  
  _Intent:_ Refresh AGENTS, analysis-gate checklist, knowledge map, and how-to guides with PMD workflow.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`  
  _Notes:_ Include screenshot or snippet of updated docs in plan appendix.

- [ ] T2908 – Quality gate closure (S29-06).  
  _Intent:_ Run full Gradle gate, finalize roadmap/current-session notes, and archive drift-gate evidence.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`  
  - `./gradlew --no-daemon pmdMain pmdTest`  
  _Notes:_ Capture final PMD report path for reference.

## Verification log
- 2025-10-19 – `./gradlew --no-daemon --write-locks pmdMain pmdTest`
- 2025-10-19 – `./gradlew --no-daemon pmdMain pmdTest`
- 2025-11-10 – `./gradlew --no-daemon spotlessApply check` (template migration doc verification)

## Notes / TODOs
- Determine if `NonExhaustiveSwitch` requires module-specific suppressions; track decisions in tasks before closing.
