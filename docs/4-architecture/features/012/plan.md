# Feature 012 – Core Cryptography & Persistence Plan

| Field | Value |
|-------|-------|
| Status | In migration (Batch P3) |
| Last updated | 2025-11-11 |
| Owners | Ivan (project owner) |
| Specification | `docs/4-architecture/features/012/spec.md` |
| Tasks checklist | `docs/4-architecture/features/012/tasks.md` |
| Roadmap entry | #12 – Core Cryptography & Persistence |

## Vision
Provide a single planning hub for shared credential-store guidance—deployment profiles, cache tuning, telemetry contracts,
maintenance helpers, optional encryption, unified filenames, and IDE remediation steps—so facade modules consume a
consistent persistence abstraction and operators understand how to configure/maintain credential stores.

## Scope
- Fold the legacy persistence specs (Features 002/027/028) into the consolidated spec/plan/tasks.
- Document verification commands (benchmarks, telemetry contract tests, maintenance CLI runs, doc linting) and log them in
  `_current-session.md`.
- Remove `docs/4-architecture/features/012/legacy/` once the migration is captured.
- Synchronise roadmap, knowledge map, and architecture graph entries with the new Feature 012 scope.

_Out of scope:_ Shipping new persistence code or changing credential-store behaviour in this documentation-only increment.

## Dependencies
| Dependency | Notes |
|------------|-------|
| `docs/test-vectors/ocra/`, `data/credentials.db` | Fixtures referenced by cache tuning + maintenance docs. |
| `docs/4-architecture/knowledge-map.md`, `docs/architecture-graph.json`, `docs/4-architecture/roadmap.md` | Must highlight Feature 012 ownership. |
| `_current-session.md` | Receives log entries + command lists for each increment. |
| `docs/2-how-to/configure-persistence-profiles.md` and related how-tos | Need updates referencing profiles, defaults, maintenance helpers, and manual migration steps. |

## Legacy Integration Tracker
| Legacy Feature(s) | Increment(s) | Status | Notes |
|-------------------|--------------|--------|-------|
| 002 | P3-I1 (spec), P3-I2 (plan/tasks) | Completed | FR-012-01..04 and NFR-012-01/02 capture the deployment profiles, telemetry contracts, and unified `credentials.db` defaults migrated from the original persistence hardening spec. |
| 027 | P3-I2 | Completed | FR-012-05/06 and NFR-012-03 cover maintenance CLI commands, benchmark references, and documentation updates previously tracked in Feature 027. |
| 028 | P3-I2 | Completed | FR-012-07 and NFR-012-04 record IDE remediation helpers and encryption guidance that lived in Feature 028. |

## Assumptions & Risks
- `./gradlew --no-daemon spotlessApply check` remains the lint gate for documentation.
- Forgetting to log `credentials.db` usage or maintenance commands could reintroduce drift—mitigate by embedding the
  verification commands inside tasks.
- Benchmarks/perf targets rely on existing tooling; skipping them would void NFR-012-01, so capture status in plan/tasks
  even if they run outside this migration.

## Increment Map
| Increment | Intent | Owner | Status | Notes |
|-----------|--------|-------|--------|-------|
| P3-I1 | Capture legacy Feature 002 (profiles, telemetry, maintenance, encryption) in the consolidated spec. | Ivan | Completed | FR-012-01..04 recorded. |
| P3-I2 | Capture legacy Features 027/028 (unified `credentials.db`, IDE remediation) in the spec/plan/tasks. | Ivan | Completed | FR-012-05..07 recorded. |
| P3-I3 | Delete `docs/4-architecture/features/012/legacy/` after verifying the migration; log `rm -rf …` + `ls` output in `_current-session.md`/session log (docs/_current-session.md). | Ivan | Pending | Execute once reviews finish. |
| P3-I4 | Sync roadmap/knowledge map/architecture graph/how-tos with the consolidated persistence story and log verification commands. | Ivan | Pending | Requires doc edits + spotless run. |

## Scenario Tracking
| Scenario | Description | Increment |
|----------|-------------|-----------|
| S-012-01 | Deployment profiles + cache tuning documented/tests. | P3-I1 |
| S-012-02 | Telemetry contract recorded + redaction rules enforced. | P3-I1 |
| S-012-03 | Maintenance helpers/CLI flows documented. | P3-I1 |
| S-012-04 | Optional encryption guidance captured. | P3-I1 |
| S-012-05 | Unified `credentials.db` default + override logging documented. | P3-I2 |
| S-012-06 | Manual migration guidance + documentation parity recorded. | P3-I2 |
| S-012-07 | IDE warning remediation logged with verification commands. | P3-I2 |
| S-012-08 | Cross-cutting docs + session/migration logs updated for persistence scope. | P3-I3/P3-I4 |

## Legacy Parity Review
- 2025-11-11 – Reviewed Feature 012 FR/NFR/scenario coverage against legacy Features 002/027/028. All persistence profile, telemetry, maintenance helper, encryption, and IDE remediation requirements are represented; performance benchmark follow-ups remain in the backlog.

## Quality & Tooling Gates
- `./gradlew --no-daemon :infra-persistence:test :application:test :cli:test :rest-api:test :ui:test spotlessApply check` when
  persistence docs reference verification runs.
- `rg "credentials.db"` across docs to ensure unified naming.
- CLI maintenance commands (compact/verify) documented + optionally dry-run when instructions change.

## Analysis Gate
Run `docs/5-operations/analysis-gate-checklist.md` after P3-I4 completes (doc sync + logging finished) and record the
execution in `_current-session.md`.

- 2025-11-11 – Analysis gate rerun after updating roadmap/knowledge map/architecture graph/persistence how-to docs to cite Feature 012.
  - Spec/plan/tasks coverage confirmed; no open questions.
  - Verification commands logged alongside governance work: `git config core.hooksPath`, `tmp_index=$(mktemp); GIT_INDEX_FILE=$tmp_index git read-tree --empty; GIT_INDEX_FILE=$tmp_index ./githooks/pre-commit`, `./gradlew --no-daemon spotlessApply check`, `./gradlew --no-daemon qualityGate`.
- `_current-session.md` includes the outcomes plus the initial malformed `spotlessApply check,workdir:` invocation that was rerun immediately.

## Implementation Drift Gate
Once Batch P3 closes, map FR-012-01..08 and NFR-012-01..05 to roadmap/knowledge map entries, CLI/REST documentation,
benchmarks, and session logs; capture findings in this plan’s appendix.

## Exit Criteria
- Spec/plan/tasks describe persistence profiles, telemetry, maintenance, encryption, unified defaults, and IDE remediation
  with verification commands logged.
- `docs/4-architecture/features/012/legacy/` removed; migration/session logs capture command history.
- Roadmap/knowledge map/architecture graph/how-to docs mention Feature 012 with consistent terminology.
- Spotless/doc lint gate rerun after documentation updates.

## Follow-ups / Backlog
- Record benchmark results + telemetry dashboards once persistence perf work resumes.
- Evaluate adding automated drift detection (scripted `rg credentials.db`) to Feature 013’s tooling backlog.
- Track encryption key-management automation as a future increment once documentation stabilises.
