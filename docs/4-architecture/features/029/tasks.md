# Feature 029 Tasks – PMD Rule Hardening

_Linked plan:_ `docs/4-architecture/features/029/plan.md`  
_Status:_ Planned  
_Last updated:_ 2025-10-19

☑ **T2901 – PMD 7 upgrade baseline**   (S29-01)
  ☑ Update the PMD toolVersion in `gradle/libs.versions.toml` / `build.gradle.kts` to the latest 7.x release.  
  ☑ Run `./gradlew --no-daemon --write-locks pmdMain pmdTest` to capture migration fallout and refresh dependency locks.  
  ☑ Note CLI/reporting/property shifts in the feature plan before proceeding to rule additions.

☐ **T2902 – Governance sync**   (S29-02)
  ☐ Create spec/plan/tasks artefacts and record the Law-of-Demeter heuristic under clarifications.  
  ☐ Add the roadmap entry and refresh `docs/_current-session.md` with the new workstream.

☑ **T2903 – Ruleset expansion & baseline**   (S29-02)
  ☑ Append all targeted PMD rules (including `category/java/bestpractices.xml/NonExhaustiveSwitch`) to `config/pmd/ruleset.xml`.  
  ☑ Run `./gradlew --no-daemon pmdMain pmdTest` across the full codebase to capture baseline violations.  
    - 2025-10-19 – Temporary dry run showed 0 NonExhaustiveSwitch violations; rule reverted pending enforcement decision.  
    - 2025-10-19 – Rule enabled permanently; `pmdMain pmdTest` stayed green.  
  ☑ Log notable findings (modules, rule names) back into the plan/tasks.

☐ **T2904 – Law-of-Demeter scoping**   (S29-03)
  ☐ Add a whitelist resource for fluent interfaces (e.g., `config/pmd/law-of-demeter-excludes.txt`) and reference it via `<exclude-pattern>`.  
  ☐ Restrict enforcement to domain/service packages; verify PMD only reports violations in those areas.  
  ☐ Document any new exclude globs in the plan.

☐ **T2905 – Domain remediation**   (S29-04)
  ☐ Address violations in `core/`, `core-ocra/`, and related domain modules.  
  ☐ Add regression tests or refactor helpers where necessary.  
  ☐ Run `./gradlew --no-daemon :core:test :core-ocra:test` and targeted PMD tasks to confirm fixes.

☐ **T2906 – Service & facade remediation**   (S29-05)
  ☐ Resolve findings in `application/`, `infra-persistence/`, and facade modules (`cli/`, `rest-api/`, `ui/`).  
  ☐ Extend Law-of-Demeter exclude patterns only when justified; note each addition in the tasks file.  
  ☐ Run `./gradlew --no-daemon :application:test :cli:test :rest-api:test` (plus Selenium/UI suites as needed).

☐ **T2907 – Documentation updates**   (S29-06)
  ☐ Update `AGENTS.md`, relevant runbooks, and quality checklists with PMD rule guidance.  
  ☐ Record quality automation relationships in `docs/4-architecture/knowledge-map.md` if they change.

☐ **T2908 – Quality gate closure**   (S29-06)
  ☐ Execute `./gradlew --no-daemon spotlessApply check`.  
  ☐ Close out plan/tasks/current-session entries and summarise the work in the roadmap.
