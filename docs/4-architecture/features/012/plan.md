# Feature 012 – Core Cryptography & Persistence Plan

_Linked specification:_ `docs/4-architecture/features/012/spec.md`  
_Linked tasks:_ `docs/4-architecture/features/012/tasks.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-13  
_Owners:_ Ivan (project owner)  
_Roadmap entry:_ #12 – Core Cryptography & Persistence

## Vision & Success Criteria
Provide a single planning hub for shared credential-store guidance—deployment profiles, cache tuning, telemetry contracts,
maintenance helpers, optional encryption, unified filenames, and IDE remediation steps—so facade modules consume a
consistent persistence abstraction and operators understand how to configure/maintain credential stores.

## Scope Alignment
- Keep persistence profiles, telemetry contracts, maintenance helpers, encryption defaults, and IDE remediation steps current inside this spec/plan/tasks set.
- Document verification commands (benchmarks, telemetry contract tests, maintenance CLI runs, doc linting) and log them in `_current-session.md`.
- Synchronise roadmap, knowledge map, architecture graph, and how-to docs with the Feature 012 scope.

_Out of scope:_ Shipping new persistence code or changing credential-store behaviour in this documentation-only increment.

## Dependencies & Interfaces
| Dependency | Notes |
|------------|-------|
| `docs/test-vectors/ocra/`, `data/credentials.db` | Fixtures referenced by cache tuning + maintenance docs. |
| `docs/4-architecture/knowledge-map.md`, `docs/architecture-graph.json`, `docs/4-architecture/roadmap.md` | Must highlight Feature 012 ownership. |
| `_current-session.md` | Receives log entries + command lists for each increment. |
| `docs/2-how-to/configure-persistence-profiles.md` and related how-tos | Need updates referencing profiles, defaults, maintenance helpers, and manual migration steps. |

## Assumptions & Risks
- `./gradlew --no-daemon spotlessApply check` remains the lint gate for documentation.
- Forgetting to log `credentials.db` usage or maintenance commands could reintroduce drift—mitigate by embedding the
  verification commands inside tasks.
- Benchmarks/perf targets rely on existing tooling; skipping them would void NFR-012-01, so capture status in plan/tasks
  even if they run outside this planning cycle.

## Implementation Drift Gate

- Summary: Use this gate to ensure persistence and cryptography documentation (profiles, telemetry contracts, maintenance helpers, optional encryption, unified `credentials.db` defaults, IDE remediation, benchmarks) remain aligned with FR-012-01..08 and NFR-012-01..05 and with the actual behaviour of `core`/`infra-persistence`/`application`/CLI/REST.

- **Checklist for future drift-gate runs (agents):**
  - **Preconditions**
    - [ ] `docs/4-architecture/features/012/{spec,plan,tasks}.md` updated to the current date; all clarifications encoded in normative sections.  
    - [ ] `docs/4-architecture/open-questions.md` has no `Open` entries for Feature 012.  
    - [ ] The following commands have been run in this increment and logged in `docs/_current-session.md`:  
      - `./gradlew --no-daemon :infra-persistence:test :application:test :cli:test :rest-api:test :ui:test spotlessApply check`  
      - `./gradlew --no-daemon :core:test --tests "*MapDbCredentialStoreTest*"` (or the equivalent once encryption tests move to `infra-persistence`).  

  - **Spec ↔ docs/implementation mapping**
    - [ ] For FR-012-01..08 and NFR-012-01..05, confirm:  
      - Deployment profiles, cache tuning, and persistence defaults are described in `docs/2-how-to/configure-persistence-profiles.md` and match the actual configuration in `infra-persistence`/`application`.  
      - Telemetry contracts for persistence operations are documented and implemented via `TelemetryContracts`.  
      - Maintenance helpers/CLI flows (compact/verify, migrations) exist and match the how-to docs.  
      - Optional encryption guidance aligns with tests and any existing flags/configuration.  
      - Unified `credentials.db` defaults and override logging are documented and reflected in code and CLI/REST behaviour.  
      - IDE remediation guidance (e.g., addressing persistence warnings) is backed by code/tests where applicable.  

  - **Roadmap/knowledge map/architecture graph**
    - [ ] Verify that roadmap, knowledge map, and architecture graph clearly attribute persistence ownership to Feature 012 and reflect current module boundaries.  
    - [ ] Confirm references to persistence profiles, `credentials.db`, and maintenance helpers in those documents match their implementation.  

  - **Drift capture & remediation**
    - [ ] Any high-/medium-impact drift (e.g., docs promising encryption/perf behaviour that code does not implement, missing maintenance commands, inconsistent defaults) is:  
      - Logged as an `Open` entry in `docs/4-architecture/open-questions.md` for Feature 012.  
      - Captured as explicit tasks in `docs/4-architecture/features/012/tasks.md`.  
    - [ ] Low-impact drift (typos, wording tweaks, small example corrections) is fixed directly, with a brief note added in this section or the plan’s verification log.  

  - **Gate output**
    - [ ] This section is updated with the latest drift gate run date, key commands executed, and a concise “matches vs gaps” summary plus remediation notes.  
    - [ ] `docs/_current-session.md` logs that the Feature 012 Implementation Drift Gate was executed (date, commands, and reference to this plan section).  

### Drift Report – 2025-11-13
- **Scope review:** Spec/plan/tasks describe persistence profiles, telemetry contracts, maintenance helpers, optional encryption, unified `credentials.db` defaults, IDE remediation, and documentation logging without legacy references. Roadmap + knowledge map cite Feature 012 as the persistence authority (FR-012-01..08, NFR-012-01..05, S-012-01..08).
- **Doc alignment:** How-to guides, roadmap, and knowledge map reflect the persistence scope; `_current-session.md` logging expectations remain in spec/plan/tasks (FR-012-05/06/08).
- **Automation alignment:** Maintenance CLI guidance, telemetry contracts, and IDE remediation steps remain tied to verification commands listed in tasks, ensuring NFR-012 coverage.
- **Verification commands:** `./gradlew --no-daemon spotlessApply check` (2025-11-13, 7 s, 96 tasks: 2 executed, 94 up-to-date) recorded for this drift gate (configuration cache reused).


## Increment Map
1. **I1 – Persistence profile + telemetry documentation** (Owner: Ivan, Status: Completed)  
   - Captured deployment profiles, telemetry contracts, maintenance helpers, and encryption guidance (FR-012-01..04).  
2. **I2 – Unified defaults + remediation** (Owner: Ivan, Status: Completed)  
   - Documented unified `credentials.db`, IDE remediation steps, and benchmarks (FR-012-05..07).  
3. **I3 – Directory cleanup** (Owner: Ivan, Status: Completed on 2025-11-11)  
   - Recorded the filesystem cleanup + spotless outputs in `_current-session.md` and confirmed no lingering references to the earlier directories.  
4. **I4 – Cross-doc sync + logging** (Owner: Ivan, Status: Completed on 2025-11-11)  
   - Updated roadmap/knowledge map/architecture graph/how-to docs with the consolidated persistence story, captured `rg "credentials.db"` + `./gradlew --no-daemon spotlessApply check` outputs in the session log, and noted the remaining benchmark/encryption follow-ups for future phases.

_Verification commands:_ `./gradlew --no-daemon :infra-persistence:test :application:test :cli:test :rest-api:test :ui:test spotlessApply check`, `./gradlew --no-daemon :core:test --tests "*MapDbCredentialStoreTest*"` (until infra-persistence ships encryption tests), targeted maintenance CLI commands (compact/verify), and `rg "credentials.db" docs/` to enforce naming consistency.

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
| S-012-08 | Cross-cutting docs + session logs updated for persistence scope. | P3-I3/P3-I4 |

## Analysis Gate
Run `docs/5-operations/analysis-gate-checklist.md` whenever persistence docs or automation scope changes land and record the execution in `_current-session.md`.

- 2025-11-11 – Analysis gate rerun after updating roadmap/knowledge map/architecture graph/persistence how-to docs to cite Feature 012.
  - Spec/plan/tasks coverage confirmed; no open questions.
  - Verification commands logged alongside governance work: `git config core.hooksPath`, `tmp_index=$(mktemp); GIT_INDEX_FILE=$tmp_index git read-tree --empty; GIT_INDEX_FILE=$tmp_index ./githooks/pre-commit`, `./gradlew --no-daemon spotlessApply check`, `./gradlew --no-daemon qualityGate`.
- `_current-session.md` includes the outcomes plus the initial malformed `spotlessApply check,workdir:` invocation that was rerun immediately.

## Exit Criteria
- Spec/plan/tasks describe persistence profiles, telemetry, maintenance, encryption, unified defaults, and IDE remediation
  with verification commands logged.
- Roadmap/knowledge map/architecture graph/how-to docs mention Feature 012 with consistent terminology.
- Spotless/doc lint gate rerun after documentation updates.

## Follow-ups / Backlog
- Record benchmark results + telemetry dashboards once persistence perf work resumes.
- Evaluate adding automated drift detection (scripted `rg credentials.db`) to Feature 013’s tooling backlog.
- Track encryption key-management automation as a future increment once documentation stabilises.
- Encryption verification currently uses `./gradlew --no-daemon :core:test --tests "*MapDbCredentialStoreTest*"`; promote equivalent coverage into `infra-persistence` once tests are available.

$chunk
