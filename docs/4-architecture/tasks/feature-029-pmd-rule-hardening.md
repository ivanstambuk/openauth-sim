# Feature 029 Tasks – PMD Rule Hardening

_Linked plan:_ `docs/4-architecture/feature-plan-029-pmd-rule-hardening.md`  
_Status:_ Planned  
_Last updated:_ 2025-10-19

☐ **T2901 – Governance sync**  
  ☐ Create spec/plan/tasks artefacts and record the Law-of-Demeter heuristic under clarifications.  
  ☐ Add the roadmap entry and refresh `docs/_current-session.md` with the new workstream.

☐ **T2902 – Ruleset expansion & baseline**  
  ☐ Append all targeted PMD rules to `config/pmd/ruleset.xml`.  
  ☐ Run `./gradlew --no-daemon pmdMain pmdTest` to capture baseline violations.  
  ☐ Log notable findings (modules, rule names) back into the plan/tasks.

☐ **T2903 – Law-of-Demeter scoping**  
  ☐ Add a whitelist resource for fluent interfaces (e.g., `config/pmd/law-of-demeter-excludes.txt`) and reference it via `<exclude-pattern>`.  
  ☐ Restrict enforcement to domain/service packages; verify PMD only reports violations in those areas.  
  ☐ Document any new exclude globs in the plan.

☐ **T2904 – Domain remediation**  
  ☐ Address violations in `core/`, `core-ocra/`, and related domain modules.  
  ☐ Add regression tests or refactor helpers where necessary.  
  ☐ Run `./gradlew --no-daemon :core:test :core-ocra:test` and targeted PMD tasks to confirm fixes.

☐ **T2905 – Service & facade remediation**  
  ☐ Resolve findings in `application/`, `infra-persistence/`, and facade modules (`cli/`, `rest-api/`, `ui/`).  
  ☐ Extend Law-of-Demeter exclude patterns only when justified; note each addition in the tasks file.  
  ☐ Run `./gradlew --no-daemon :application:test :cli:test :rest-api:test` (plus Selenium/UI suites as needed).

☐ **T2906 – Documentation updates**  
  ☐ Update `AGENTS.md`, relevant runbooks, and quality checklists with PMD rule guidance.  
  ☐ Record quality automation relationships in `docs/4-architecture/knowledge-map.md` if they change.

☐ **T2907 – Quality gate closure**  
  ☐ Execute `./gradlew --no-daemon spotlessApply check`.  
  ☐ Close out plan/tasks/current-session entries and summarise the work in the roadmap.
