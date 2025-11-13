# Feature 010 – Documentation & Knowledge Automation Plan

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-13 |
| Owners | Ivan (project owner) |
| Specification | `docs/4-architecture/features/010/spec.md` |
| Tasks checklist | `docs/4-architecture/features/010/tasks.md` |
| Roadmap entry | #10 – Documentation & Knowledge Automation |

## Vision & Success Criteria
Centralise every operator-facing guide, roadmap/knowledge-map reference, and quality-automation workflow under one feature so
all documentation and the aggregated `qualityGate` evolve together. Success means the Java/CLI/REST guides, README, roadmap,
knowledge map, `_current-session.md`, session log (docs/_current-session.md), and GitHub Actions workflow share a single authoritative spec/plan/tasks
set with deterministic verification commands.

## Scope Alignment
- Maintain the operator documentation suite (Java/CLI/REST guides, README, roadmap/knowledge map references, `_current-session.md`, session quick reference) inside Feature 010’s spec/plan/tasks so these artefacts evolve together.
- Keep roadmap, knowledge map, architecture graph, session log (docs/_current-session.md), and session quick reference aligned with documentation and automation changes.
- Maintain the aggregated `qualityGate` task plus its CI workflow, report locations, skip flags, and troubleshooting guides.
- Log every documentation/automation increment inside `_current-session.md` with command history.

_Out of scope:_ shipping runtime simulator changes, expanding the quality gate to non-OCRA modules, or introducing new
publishing tooling.

## Dependencies & Interfaces
| Dependency | Notes |
|------------|-------|
| Docs templates (`docs/templates/*.md`) | Govern structure for specs/plans/tasks and operator guides. |
| Operator guides (`docs/2-how-to/*.md`) + README | Must reflect current simulator behaviour and telemetry expectations. |
| Roadmap / knowledge map / session log (docs/_current-session.md) / `_current-session.md` | Need synchronized references and command logs per increment. |
| Gradle build logic (`qualityGate` task, Spotless, ArchUnit, Jacoco, PIT, SpotBugs, Checkstyle, gitleaks) | Provide the automation enforced by this feature. |
| GitHub Actions workflow (`.github/workflows/quality-gate.yml`) | Mirrors local gate execution and uploads reports. |

## Assumptions & Risks
- Documentation remains Markdown/ASCII; deviations require template updates.
- Developers run `./gradlew --no-daemon spotlessApply check` for every doc change and `./gradlew --no-daemon qualityGate` when automations change.
- Risk: forgetting to log commands in `_current-session.md` or session log (docs/_current-session.md) reduces auditability—mitigate via checklist items.
- Risk: PIT/Jacoco runtimes can exceed NFR limits if cache hints regress—monitor timings inside tasks/plan notes.

## Implementation Drift Gate
Periodically cross-check FR-010-01..FR-010-10 and NFR-010-01..05 against the operator guides, README, roadmap/knowledge map entries,
`_current-session.md` logs, and `qualityGate` configuration/report folders. Log each drift report inside this plan with links to verification commands before closing an increment.

### Drift Report – 2025-11-13
- **Scope review:** Spec/plan/tasks describe the steady-state documentation + knowledge automation scope (Java/CLI/REST guides, README, roadmap/knowledge map, session quick reference, `_current-session.md`, `qualityGate` automation). No legacy references remain and roadmap/knowledge-map entries point to Feature 010 as the authority (covers FR-010-01..10, NFR-010-01..05, Scenarios S-010-01..10).
- **Doc alignment:** Operator guides and README paths remain documented in the spec; roadmap, knowledge map, and session quick reference updates are logged in `_current-session.md`, satisfying FR-010-03/S-010-03. Templates + how-tos reference telemetry expectations and troubleshooting coverage (FR-010-01/02).
- **Automation alignment:** Aggregated `qualityGate`, ArchUnit, Jacoco, PIT, and lint suites remain captured in plan/spec with skip flags and CI parity guidance (FR-010-04..09, S-010-04..09). Documentation now highlights ongoing logging expectations (FR-010-10) without migration framing.
- **Verification commands:** `./gradlew --no-daemon spotlessApply check` (2025-11-13, 18 s, 96 tasks: 2 executed, 94 up-to-date) recorded in Feature 010 tasks and `_current-session.md`; command exercises Spotless/PMD/Checkstyle suites plus the console Node harness.


## Increment Map
1. **I1 – Documentation suite foundation** (Owner: Ivan, Status: Completed)  
   - Established the consolidated spec for Java/CLI/REST guides and README cross-links; FR-010-01/02 reference guide paths, telemetry expectations, and troubleshooting coverage.  
2. **I2 – Quality automation charter** (Owner: Ivan, Status: Completed)  
   - Captured the ArchUnit/Jacoco/PIT/`qualityGate` workflow plus CI troubleshooting docs (FR-010-04..09 + NFR updates).  
3. **I3 – Directory + template cleanup** (Owner: Ivan, Status: Completed)  
   - Ensured legacy documentation directories were retired after verifying coverage; command outputs remain logged in `_current-session.md`.  
4. **I4 – Documentation/automation verification gate** (Owner: Ivan, Status: Pending)  
   - Record the cross-feature summary, rerun `./gradlew --no-daemon spotlessApply check`, and schedule the final `qualityGate` once Features 011–013 finish their rewrites.

_Verification commands:_ `./gradlew --no-daemon spotlessApply check`, `./gradlew --no-daemon qualityGate [-Ppit.skip=true]`, targeted ArchUnit/Jacoco/PIT/SpotBugs/Checkstyle runs, and `rg "Feature 010" docs/` to confirm cross-document references.

## Scenario Tracking
| Scenario | Description | Increment |
|----------|-------------|-----------|
| S-010-01 | Operator guides remain runnable with telemetry and troubleshooting coverage. | P3-I1 |
| S-010-02 | README/doc landing pages point to shipped capabilities only. | P3-I1 |
| S-010-03 | Roadmap/knowledge map/session log (docs/_current-session.md)/session quick reference stay synchronized and log commands. | P3-I1–P3-I4 |
| S-010-04 | `qualityGate` aggregates Spotless, Checkstyle, SpotBugs, ArchUnit, Jacoco, PIT, gitleaks. | P3-I2 |
| S-010-05 | ArchUnit rules enforce module boundaries. | P3-I2 |
| S-010-06 | Jacoco aggregated thresholds remain ≥90% line/branch. | P3-I2 |
| S-010-07 | PIT mutation score stays ≥85% with documented skip flag. | P3-I2 |
| S-010-08 | GitHub Actions workflow mirrors the gate and uploads reports. | P3-I2 |
| S-010-09 | Docs/runbooks explain how to run/remediate the gate. | P3-I2 |
| S-010-10 | `_current-session.md` + session log (docs/_current-session.md) log every documentation/automation increment. | P3-I3 |

## Analysis Gate
Run `docs/5-operations/analysis-gate-checklist.md` whenever major doc/automation scope changes land. Ensure roadmap, knowledge map, session log (docs/_current-session.md), and `_current-session.md` match the updated spec/plan/tasks before moving to implementation work.

## Exit Criteria
- Feature 010 spec/plan/tasks describe the operator doc suite and quality automation guardrails (no `legacy/` references).
- Roadmap, knowledge map, architecture graph, session log (docs/_current-session.md), session quick reference, and `_current-session.md` cite Feature 010 for documentation and automation work.
- `./gradlew --no-daemon spotlessApply check` recorded after each major doc/automation change; `qualityGate` rerun once Features 011–013 integrate.

## Follow-ups / Backlog
- Script knowledge-map regeneration to reduce manual edits (capture timing + instructions in Feature 010 tasks once designed).
- Evaluate adding Markdown lint to the managed hook after Batch P3 verification.
- Expand `qualityGate` coverage beyond the OCRA stack in a future feature once this migration stabilises.

$chunk
