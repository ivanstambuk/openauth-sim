# Feature Plan 029 – PMD Rule Hardening

| Field | Value |
|-------|-------|
| Status | Planned |
| Last updated | 2025-11-10 |
| Linked specification | `docs/4-architecture/features/029/spec.md` |
| Linked tasks | `docs/4-architecture/features/029/tasks.md` |

## Vision & Success Criteria
- Expand PMD coverage with the approved rule set while keeping developer ergonomics acceptable.
- Scope Law-of-Demeter enforcement to domain/service packages with a transparent whitelist.
- Remediate violations across core, application, infra, CLI, REST, and UI modules without regressing `spotlessApply check`.
- Publish contributor guidance so future agents understand how to run and remediate PMD findings.

## Dependencies & Interfaces
- PMD plugin configured via Gradle 8.10+ and `config/pmd/ruleset.xml`.
- Shared documentation touchpoints: `AGENTS.md`, `docs/5-operations/analysis-gate-checklist.md`, and knowledge map.
- Potential coordination with parallel features editing the same modules (record conflicts in roadmap/current-session).

## Implementation Drift Gate
- Run after tasks T2901–T2908 complete with a green `./gradlew spotlessApply check`.
- Evidence package stored in this plan:
  - Table mapping FR29-01…FR29-05 to code/test references (rule files, whitelist resources, remediation PRs).
  - Screenshot or log excerpt from `pmdMain` showing zero violations post-remediation.
  - Excerpt of contributor docs referencing new workflow.
  - Summary of intentional exclude patterns with rationale.

## Increment Map
0. **I0 – PMD 7 upgrade baseline (T2901)**  
   - Upgrade toolVersion, refresh dependency locks, archive baseline reports.  
   - Commands: `./gradlew --no-daemon --write-locks pmdMain pmdTest`.
1. **I1 – Governance sync (T2902)**  
   - Finalise spec/plan/tasks, roadmap entry, `_current-session` update.  
   - Commands: documentation only.
2. **I2 – Ruleset expansion & dry run (T2903)**  
   - Append rules to `config/pmd/ruleset.xml`, run PMD, log findings.  
   - Commands: `./gradlew --no-daemon pmdMain pmdTest`.
3. **I3 – Law-of-Demeter scoping (T2904)**  
   - Add whitelist resource, restrict enforcement, verify targeted packages.  
   - Commands: `./gradlew --no-daemon pmdMain`.
4. **I4 – Domain remediation (T2905)**  
   - Fix violations in `core/` + `core-ocra/`, add tests, note exclusions.  
   - Commands: `./gradlew --no-daemon :core:test :core-ocra:test pmdMain`.
5. **I5 – Service/facade remediation (T2906)**  
   - Address findings in `application/`, `infra-persistence/`, `cli/`, `rest-api/`, `ui/`.  
   - Commands: `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain`.
6. **I6 – Documentation & governance (T2907)**  
   - Update AGENTS/runbooks/knowledge map with PMD guidance.  
   - Commands: documentation + `./gradlew --no-daemon spotlessApply check`.
7. **I7 – Quality gate closure (T2908)**  
   - Run full Gradle gate, archive drift-gate evidence, close tasks.  
   - Commands: `./gradlew --no-daemon spotlessApply check`.

## Scenario Tracking
| Scenario ID | Increment reference | Notes |
|-------------|--------------------|-------|
| S29-01 | I0 | PMD 7 upgrade baseline reports archived. |
| S29-02 | I2 | Ruleset expansion logged with zero-config failures. |
| S29-03 | I3 | Law-of-Demeter whitelist enforced. |
| S29-04 | I4 | Core/domain violations remediated. |
| S29-05 | I5 | Service/facade remediation complete. |
| S29-06 | I6–I7 | Documentation + quality gates updated. |

## Quality & Tooling Gates
- Every increment concludes with `./gradlew --no-daemon spotlessApply check`.
- Spotless uses Palantir Java Format 2.78.0; rerun after rule changes that touch Java sources.
- Document each executed PMD command inside the tasks verification log.
- Keep PMD reports under version control only when referenced in docs; otherwise, share paths.

## Analysis Gate
- Execute once I7 finishes. Checklist: PMD + spotless green, clarifications resolved, roadmap/current-session updated, evidence archived.

## Exit Criteria
- FR29-01…FR29-05 satisfied with corresponding documentation.
- `config/pmd/ruleset.xml` + whitelist resources committed with rationale.
- All targeted modules pass PMD + unit tests; no outstanding violations.
- Knowledge map/roadmap updated, tasks checklist fully checked.

## Follow-ups / Backlog
- Monitor PMD runtime; consider per-module task parallelism if CI impact grows.
- Evaluate introducing mutation tests for Law-of-Demeter helper rules.
- Track additional PMD categories (security rules) for future features.
