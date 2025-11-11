# Feature 013 – Toolchain & Quality Platform Plan

| Field | Value |
|-------|-------|
| Status | In migration (Batch P3) |
| Last updated | 2025-11-11 |
| Owners | Ivan (project owner) |
| Specification | `docs/4-architecture/features/013/spec.md` |
| Tasks checklist | `docs/4-architecture/features/013/tasks.md` |
| Roadmap entry | #13 – Toolchain & Quality Platform |

## Vision
Maintain a single plan for every toolchain/quality guardrail—CLI exit harnesses, Maintenance CLI coverage buffers, anti-
reflection policy, Java 17 refactors, architecture harmonization, SpotBugs/PMD enforcement, Gradle upgrades, and removal of
legacy entry points—so future increments know which commands to run and which backlog items remain.

## Scope
- Absorb the requirements from legacy Features 010–015/029/030/031 into the consolidated spec/plan/tasks.
- List the commands that must run before/after toolchain changes (`qualityGate`, module tests, jacocoAggregatedReport,
  reflectionScan, spotbugsMain, pmdMain pmdTest, Gradle wrapper/warning sweeps) and log them in `_current-session.md`.
- Remove `docs/4-architecture/features/013/legacy/` once absorption is complete, logging the deletion and verification
  commands.
- Synchronize roadmap, knowledge map, architecture graph, session log (docs/_current-session.md), and `_current-session.md` entries with the
  refreshed toolchain ownership.

_Out of scope:_ Executing the verification suites during this documentation-only increment or modifying build scripts.

## Dependencies
| Dependency | Notes |
|------------|-------|
| `config/spotbugs/dead-state-include.xml`, `config/pmd/ruleset.xml`, `config/pmd/law-of-demeter-excludes.txt` | Shared static-analysis config referenced by the spec. |
| `gradle/wrapper/gradle-wrapper.properties`, `gradle/libs.versions.toml` | Record Gradle 9/pugin versions. |
| `docs/5-operations/analysis-gate-checklist.md`, `docs/5-operations/session-quick-reference.md`, `AGENTS.md` | Reference reflection policy, sealed hierarchies, and static-analysis expectations. |
| `_current-session.md`, roadmap, knowledge map, architecture graph | Receive updated toolchain ownership + command logs. |

## Legacy Integration Tracker
| Legacy Feature(s) | Increment(s) | Status | Notes |
|-------------------|--------------|--------|-------|
| 010/011/012/013/014/015 | P3-I1 (spec), P3-I2 (plan/tasks) | Completed | FR-013-01..06 and NFR-013-02/03 describe the CLI exit harness, maintenance CLI buffer, reflection policy, and wrapper guidance migrated from Features 010–015. |
| 029/030/031 | P3-I1, P3-I2 | Completed | FR-013-07..09 and NFR-013-01/04/05 cover the quality gate orchestration (SpotBugs, PMD, wrapper, configuration cache) previously housed in Features 029–031. |

## Assumptions & Risks
- Commands listed in this plan remain invocable (Gradle wrapper present, Node tests available).
- Risk: failure to capture outstanding TODOs (e.g., PMD Law-of-Demeter exclusions, Maintenance CLI coverage buffer follow-ups)
  would hide future work—mitigate by scanning each legacy plan/tasks while updating this plan.
- Risk: forgetting to log wrapper updates/command outputs reduces auditability; tasks checklist enforces logging.

## Increment Map (≤90 min)
| Increment | Intent | Owner | Status | Notes |
|-----------|--------|-------|--------|-------|
| P3-I1 | Consolidate legacy Features 010–015/029–031 into the spec (requirements, verification commands, backlog items). | Ivan | Completed | FR-013-01..10 and NFR-013-01..05 recorded. |
| P3-I2 | Update plan/tasks to enumerate required verification commands, hotspot reports, whitelists, wrapper steps, and backlog follow-ups. | Ivan | Completed | Plan/tasks now list `qualityGate`, `spotbugsMain`, `pmdMain`, `reflectionScan`, wrapper updates, and backlog follow-ups with links to `_current-session.md` entries.
| P3-I3 | Remove `docs/4-architecture/features/013/legacy/` once reviews finish; log `rm -rf …`, `ls docs/4-architecture/features/013`, and pending verification commands in `_current-session.md` + session log (docs/_current-session.md). | Ivan | Pending | Execute after plan/tasks reviewed. |
| P3-I4 | Sync roadmap/knowledge map/architecture graph/session log (docs/_current-session.md) with Feature 013 ownership and log the required commands (`spotlessApply check`, `qualityGate`) for the final Phase 2 close-out. | Ivan | Pending | Requires cross-doc edits + spotless/qualityGate reruns (tracked separately in Step 3 of the master plan). |

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

## Legacy Parity Review
- 2025-11-11 – Compared Feature 013 FR/NFR/scenario coverage with legacy Features 010–015/029/030/031. CLI exit harnesses, Maintenance CLI coverage buffers, reflection policy, Java 17 refactors, architecture harmonization, SpotBugs/PMD enforcement, and Gradle upgrade requirements are all represented; outstanding backlog items (coverage buffer restoration, PMD whitelist tuning) remain under Follow-ups.

## Quality & Tooling Gates
- `./gradlew --no-daemon qualityGate` (full suite: Spotless, ArchUnit, Jacoco, PIT, reflectionScan, SpotBugs, PMD).
- Module-specific commands: `./gradlew :cli:test --tests "*OcraCliLauncherTest"`, `./gradlew jacocoAggregatedReport`,
  `./gradlew spotbugsMain spotbugsTest`, `./gradlew pmdMain pmdTest`, `./gradlew --warning-mode=all clean check`,
  `./gradlew --configuration-cache help`, `./gradlew wrapper --gradle-version 9.1.0 --distribution-type bin`.
- Node/JS tooling (operator console tests) referenced from Feature 009 remain prerequisites when toolchain docs touch JS assets.

## Analysis Gate
After P3-I4 completes (cross-doc sync + logging), run `docs/5-operations/analysis-gate-checklist.md`, capturing the
commands executed and any outstanding TODOs in `_current-session.md` and this plan.

- 2025-11-11 – Analysis gate rerun after roadmap/knowledge map/architecture graph/session log (docs/_current-session.md) updates referencing Feature 013.
  - Spec/plan/tasks alignment confirmed; outstanding backlog items remain tracked under Follow-ups.
  - Logged commands: `git config core.hooksPath`, `tmp_index=$(mktemp); GIT_INDEX_FILE=$tmp_index git read-tree --empty; GIT_INDEX_FILE=$tmp_index ./githooks/pre-commit` (dry-run), `./gradlew --no-daemon spotlessApply check`, `./gradlew --no-daemon qualityGate`.
- `_current-session.md` documents the outcomes plus the initial malformed `spotlessApply check,workdir:` command that was rerun without the stray argument.

## Implementation Drift Gate
Once Batch P3 closes, verify that FR-013-01..10 and NFR-013-01..05 map to the archived plan/tasks, roadmap/knowledge map
entries, and command logs. Document findings in this plan’s appendix with links to remediation tasks as needed.

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
