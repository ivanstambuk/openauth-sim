# Feature Plan 032 – Palantir Formatter Adoption

_Linked specification:_ `docs/4-architecture/features/032/spec.md`  
_Linked tasks:_ `docs/4-architecture/features/032/tasks.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
Adopt Palantir Java Format 2.78.0 across Spotless, managed Git hooks, and IDE guidance, then reformat the repository once
the tooling swap is validated. Success requires: (a) Spotless + locks pin Palantir, (b) hooks/docs reflect the policy,
(c) `spotlessApply check` returns green after the reformat, and (d) the rollout plan is documented for downstream
contributors.

## Scope Alignment
- **In scope:** Spotless configuration, version catalog pin, dependency lock refresh, Git hook messaging, documentation
  updates, staged reformat (apply + verify), roadmap/session entries.
- **Out of scope:** Alternative formatters, unrelated lint tooling changes, Java toolchain updates.

## Dependencies & Interfaces
- Spotless plugin configuration in `build.gradle.kts`.
- Version catalog (`gradle/libs.versions.toml`) + all Gradle lockfiles.
- Managed Git hooks (`githooks/pre-commit`).
- Documentation stack (AGENTS, CONTRIBUTING, how-tos, roadmap, knowledge map, `_current-session.md`).

## Assumptions & Risks
- **Assumptions:** Palantir Java Format 2.78.0 is available via Spotless and compatible with current codebase; network
  access exists to download the formatter; contributors can coordinate around the global reformat.
- **Risks / Mitigations:**
  1. Formatting conflicts for concurrent branches → communicate rebase steps in docs and tasks.
  2. Plugin incompatibility → dry-run `spotlessCheck` before applying, and roll back if issues arise.
  3. Lock regeneration churn → use narrow `--write-locks` invocations and document commands.

## Implementation Drift Gate
- Evidence captured 2025-10-19: Spotless config diff, version catalog update, lockfile regeneration logs (`--write-locks`),
  hook dry-run notes, documentation diffs, and final `./gradlew --no-daemon spotlessApply check` output. Gate stays
  satisfied; rerun only if the formatter policy changes again.

## Increment Map
1. **I1 – Governance setup (S-032-05)**
   - Create spec/plan/tasks, update `_current-session.md`, roadmap, and migration plan with Option B decision.
   - Commands: docs edits, baseline `./gradlew --no-daemon spotlessCheck`.
2. **I2 – Spotless configuration swap (S-032-01)**
   - Add `palantirJavaFormat = "2.78.0"` to the catalog, switch Spotless to Palantir.
   - Commands: `./gradlew --no-daemon spotlessCheck` (expect diff).
3. **I3 – Dependency lock refresh (S-032-02)**
   - Run `./gradlew --no-daemon --write-locks spotlessApply check` plus targeted module compiles as needed.
4. **I4 – Git hook update + dry run (S-032-03)**
   - Update hook messaging, dry-run pre-commit with staged sample files.
5. **I5 – Documentation & IDE guidance (S-032-05)**
   - Revise AGENTS/CONTRIBUTING/how-tos/roadmap/knowledge map with Palantir policy + IDE tips.
6. **I6 – Stage 1 verification (S-032-01..03)**
   - Rerun `spotlessCheck`, ensure no unexpected warnings before reformat.
7. **I7 – Repository-wide reformat (S-032-04)**
   - Execute `./gradlew --no-daemon spotlessApply`, spot-check modules for Palantir style.
8. **I8 – Final quality gate (S-032-04)**
   - Run `./gradlew --no-daemon spotlessApply check`; ensure build green.
9. **I9 – Documentation handoff (S-032-05)**
   - Update roadmap/session snapshot/migration tracker, archive plan/tasks once reviewers acknowledge the change.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-032-01 | I2, I6 / T-032-02, T-032-06 | Spotless config swap + verification. |
| S-032-02 | I3 / T-032-03 | Dependency locks refreshed. |
| S-032-03 | I4 / T-032-04 | Managed hooks reference Palantir. |
| S-032-04 | I7, I8 / T-032-07, T-032-08 | Repository reformat + quality gate. |
| S-032-05 | I1, I5, I9 / T-032-01, T-032-05, T-032-09 | Governance + documentation updates. |

## Analysis Gate
- Completed 2025-10-19 after the configuration swap + documentation plan were reviewed; no open questions remain.

## Exit Criteria
- FR-032-01…FR-032-05 satisfied with recorded commands/logs.
- `./gradlew --no-daemon spotlessApply check` returns green post-reformat.
- Docs/roadmap/knowledge map reference Palantir Java Format 2.78.0; migration tracker updated.

## Follow-ups / Backlog
- Monitor future Palantir formatter releases; open a new feature if an upgrade is needed.
