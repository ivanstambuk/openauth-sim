# Feature 013 – Toolchain & Quality Platform Plan

_Linked specification:_ `docs/4-architecture/features/013/spec.md`  
_Linked tasks:_ `docs/4-architecture/features/013/tasks.md`  
_Status:_ In migration (Batch P3)  
_Last updated:_ 2025-11-11  
_Owners:_ Ivan (project owner)  
_Roadmap entry:_ #13 – Toolchain & Quality Platform

## Vision & Success Criteria
Maintain a single plan for every toolchain/quality guardrail—CLI exit harnesses, Maintenance CLI coverage buffers, anti-
reflection policy, Java 17 refactors, architecture harmonization, SpotBugs/PMD enforcement, Gradle upgrades, and removal of
legacy entry points—so future increments know which commands to run and which backlog items remain.

## Scope Alignment
- Absorb the requirements from legacy Features 010–015/029/030/031 into the consolidated spec/plan/tasks.
- List the commands that must run before/after toolchain changes (`qualityGate`, module tests, jacocoAggregatedReport,
  reflectionScan, spotbugsMain, pmdMain pmdTest, Gradle wrapper/warning sweeps) and log them in `_current-session.md`.
- Remove `docs/4-architecture/features/013/legacy/` once absorption is complete, logging the deletion and verification
  commands.
- Synchronize roadmap, knowledge map, architecture graph, session log (docs/_current-session.md), and `_current-session.md` entries with the
  refreshed toolchain ownership.

_Out of scope:_ Executing the verification suites during this documentation-only increment or modifying build scripts.

## Dependencies & Interfaces
| Dependency | Notes |
|------------|-------|
| `config/spotbugs/dead-state-include.xml`, `config/pmd/ruleset.xml`, `config/pmd/law-of-demeter-excludes.txt` | Shared static-analysis config referenced by the spec. |
| `gradle/wrapper/gradle-wrapper.properties`, `gradle/libs.versions.toml` | Record Gradle 9/pugin versions. |
| `docs/5-operations/analysis-gate-checklist.md`, `docs/5-operations/session-quick-reference.md`, `AGENTS.md` | Reference reflection policy, sealed hierarchies, and static-analysis expectations. |
| `_current-session.md`, roadmap, knowledge map, architecture graph | Receive updated toolchain ownership + command logs. |

## Assumptions & Risks
- Commands listed in this plan remain invocable (Gradle wrapper present, Node tests available).
- Risk: failure to capture outstanding TODOs (e.g., PMD Law-of-Demeter exclusions, Maintenance CLI coverage buffer follow-ups)
  would hide future work—mitigate by scanning each legacy plan/tasks while updating this plan.
- Risk: forgetting to log wrapper updates/command outputs reduces auditability; tasks checklist enforces logging.

## Implementation Drift Gate
Once Batch P3 closes, verify that FR-013-01..10 and NFR-013-01..05 map to the archived plan/tasks, roadmap/knowledge map
entries, and command logs. Document findings in this plan’s appendix with links to remediation tasks as needed.


## Increment Map
1. **P3-I1 – Legacy toolchain requirements** (Owner: Ivan, Status: Completed)  
   - Consolidated Features 010–015/029–031 into the spec, capturing FR-013-01..10 and NFR-013-01..05 (exit harnesses, reflection policy, wrapper guidance).  
2. **P3-I2 – Plan/tasks command inventory** (Owner: Ivan, Status: Completed)  
   - Updated plan/tasks with `qualityGate`, `spotbugsMain`, `pmdMain`, `reflectionScan`, wrapper updates, and backlog follow-ups linked to `_current-session.md`.  
3. **P3-I3 – Legacy tree cleanup** (Owner: Ivan, Status: Pending)  
   - Remove `docs/4-architecture/features/013/legacy/` after reviews, logging `rm` + `ls` output and pending verification commands.  
4. **P3-I4 – Toolchain sync + final gate** (Owner: Ivan, Status: Pending)  
   - Sync roadmap/knowledge map/architecture graph/session log, rerun `./gradlew --no-daemon spotlessApply check` + `qualityGate`, and log the results before close-out.

_Verification commands:_ `./gradlew --no-daemon qualityGate`, module-specific runs (`spotbugsMain spotbugsTest`, `pmdMain pmdTest`, `jacocoAggregatedReport`, `reflectionScan`, wrapper upgrades), and relevant Node/JS console tests when toolchain docs touch JS assets.

## Scenario Tracking
| Scenario | Description | Increment |
|----------|-------------|-----------|
| S-013-01 | CLI exit harness + Maintenance coverage buffer documented with commands. | P3-I1 |
| S-013-02 | Reflection policy + sealed hierarchies captured with verification steps. | P3-I1 |
| S-013-03 | Architecture harmonization + Java 17 refactors described with ArchUnit/qualityGate requirements. | P3-I1 |
| S-013-04 | SpotBugs/PMD enforcement + suppression etiquette recorded. | P3-I1 |
| S-013-05 | Gradle wrapper/plugin upgrade steps captured with warning-mode + configuration-cache commands. | P3-I1 |
| S-013-06 | Legacy entry-point removal documented; telemetry/router references updated. | P3-I1 |
| S-013-07 | Roadmap/knowledge map/session log (docs/_current-session.md)/session logs reflect toolchain ownership. | P3-I4 |

## Analysis Gate
After P3-I4 completes (cross-doc sync + logging), run `docs/5-operations/analysis-gate-checklist.md`, capturing the
commands executed and any outstanding TODOs in `_current-session.md` and this plan.

- 2025-11-11 – Analysis gate rerun after roadmap/knowledge map/architecture graph/session log (docs/_current-session.md) updates referencing Feature 013.
  - Spec/plan/tasks alignment confirmed; outstanding backlog items remain tracked under Follow-ups.
  - Logged commands: `git config core.hooksPath`, `tmp_index=$(mktemp); GIT_INDEX_FILE=$tmp_index git read-tree --empty; GIT_INDEX_FILE=$tmp_index ./githooks/pre-commit` (dry-run), `./gradlew --no-daemon spotlessApply check`, `./gradlew --no-daemon qualityGate`.
- `_current-session.md` documents the outcomes plus the initial malformed `spotlessApply check,workdir:` command that was rerun without the stray argument.

## Exit Criteria
- Spec/plan/tasks describe all legacy toolchain requirements, verification commands, and backlog items.
- `docs/4-architecture/features/013/legacy/` removed; migration/session logs capture the deletion + queued verification.
- Roadmap/knowledge map/architecture graph/session log (docs/_current-session.md) mention Feature 013 for toolchain ownership.
- Spotless/doc lint rerun after cross-doc edits; final `qualityGate` rerun logged during Phase 2 close-out.

## Follow-ups / Backlog
- Track Maintenance CLI coverage buffer restoration (≥0.90) and Jacoco hotspot updates.
- Monitor PMD Law-of-Demeter whitelist health + backlog of NonExhaustiveSwitch remediation.
- Capture Gradle wrapper/plugin upgrade cadence for future releases.
- Consider automation to ensure `legacyEmit`/router shims never reappear (simple `rg` guard in quality pipeline).

$chunk
