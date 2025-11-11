# Feature Plan 008 – OCRA Quality Automation

_Linked specification:_ `docs/4-architecture/features/008/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
- Protect OCRA module boundaries and behavioural invariants via deterministic ArchUnit rules, Jacoco thresholds (≥90% line/branch), and PIT mutation scoring (≥85%).
- Provide a single Gradle entry point (`qualityGate`) plus a GitHub Actions workflow that runs identical checks per push/PR.
- Publish actionable documentation so contributors can run, interpret, and remediate the gate without inspecting build scripts.
- Keep local runtime ≤10 minutes through caching, targeted package filters, and optional PIT skip flags for triage.

## Scope Alignment
- **In scope:** ArchUnit boundary tests, Jacoco aggregation + thresholds, PIT configuration, Gradle `qualityGate` task, GitHub Actions workflow updates, documentation/roadmap/knowledge-map updates.
- **Out of scope:** New runtime features or telemetry, automation for non-OCRA modules, status badges, or unrelated security scanners.

## Dependencies & Interfaces
- Gradle 8.x build, ArchUnit suites under `core-architecture-tests`, Jacoco aggregated reports, PIT mutation plugin, Spotless/SpotBugs/Checkstyle tasks.
- GitHub Actions workflow (`.github/workflows/quality-gate.yml`) with Gradle/PIT caching.
- Documentation touchpoints: `docs/5-operations/quality-gate.md`, roadmap, knowledge map, `_current-session.md`.

## Assumptions & Risks
- **Assumptions:**
  - OCRA code coverage can reach ≥90% with existing fixtures.
  - PIT runtime remains manageable via targeted package filters.
  - GitHub Actions minutes budget tolerates the gate once caching is enabled.
- **Risks / Mitigations:**
  - **Long runtimes:** Scope PIT to OCRA packages and enable `-Ppit.skip=true` for local triage.
  - **Drift between local and CI gates:** Keep `qualityGate` the single source of truth and reuse it in workflows.
  - **Threshold maintenance:** Parameterise min values via Gradle properties so future tuning does not require code changes.

## Implementation Drift Gate
- Triggered after T-008-01–T-008-07 completed; evidence captured in this plan (coverage/mutation metrics, workflow links).
- Deliverables:
  - Mapping of FR/NFR IDs to tests/tasks (see Scenario Tracking section).
  - Attach PIT/Jacoco report snapshots (stored under `build/reports/quality/`).
  - Log `./gradlew qualityGate` output and CI workflow links in `_current-session.md`.
- Outcome: Gate verified 2025-10-01 with 97.05% line / 90.24% branch coverage and 91.83% mutation score.

## Increment Map
1. **I1 – Clarify scope & baselines** _(T-008-01)_
   - _Goal:_ Confirm clarifications, baseline coverage/mutation metrics, update roadmap/knowledge map.
   - _Commands:_ `less docs/4-architecture/features/008/spec.md`, `rg -n "Feature 008" docs/4-architecture/roadmap.md`.
   - _Exit:_ Open questions cleared; baseline metrics recorded.
2. **I2 – ArchUnit + Jacoco enforcement** _(T-008-02)_
   - _Goal:_ Add failing ArchUnit suites and Jacoco threshold tasks before implementation.
   - _Commands:_ `./gradlew --no-daemon :core-architecture-tests:test --tests "*Architecture*"`, `./gradlew --no-daemon jacocoAggregatedReport`.
   - _Exit:_ Rules and thresholds codified; failing tests drive implementation.
3. **I3 – PIT integration** _(T-008-03)_
   - _Goal:_ Configure PIT with ≥85% threshold, generate baseline reports.
   - _Commands:_ `./gradlew --no-daemon pitest`, inspect `build/reports/pitest`.
   - _Exit:_ Mutation tests wired into build; reports available.
4. **I4 – Aggregated quality gate task** _(T-008-04)_
   - _Goal:_ Create `qualityGate` task bundling architecture, coverage, mutation, lint, and formatting tasks with optional PIT skip flag.
   - _Commands:_ `./gradlew --no-daemon qualityGate -Ppit.skip=true`.
   - _Exit:_ Single entry point verified locally.
5. **I5 – CI workflow + documentation** _(T-008-05 & T-008-06)_
   - _Goal:_ Update GitHub Actions workflow to run `qualityGate` and document usage/skip flags/troubleshooting.
   - _Commands:_ `rg -n "qualityGate" .github/workflows`, `markdownlint docs/5-operations/quality-gate.md`.
   - _Exit:_ Workflow mirrors local gate; docs updated across roadmap/knowledge map/how-to guides.
6. **I6 – Final verification & reporting** _(T-008-07)_
   - _Goal:_ Run full gate without skips, capture PIT/Jacoco metrics, record closure notes.
   - _Commands:_ `./gradlew --no-daemon qualityGate`.
   - _Exit:_ Evidence stored in plan/tasks; feature marked complete.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-008-01 | I2 / T-008-02 | ArchUnit rules covering module boundaries. |
| S-008-02 | I2 / T-008-02 | Jacoco thresholds codified in quality gate. |
| S-008-03 | I3 / T-008-03 | PIT integration + threshold enforcement. |
| S-008-04 | I4 / T-008-04 | `qualityGate` task wiring. |
| S-008-05 | I5 / T-008-05 | GitHub Actions workflow mirroring local gate. |
| S-008-06 | I1, I5, I6 / T-008-01, T-008-06, T-008-07 | Documentation + governance updates recorded. |

## Analysis Gate (2025-09-30)
- ✅ Specification captured goals, requirements, clarifications.
- ✅ Open questions cleared in `docs/4-architecture/open-questions.md`.
- ✅ Plan/tasks aligned with scope, governance artefacts updated.
- ✅ Tasks ≤30 minutes with verification commands sequenced first.
- ✅ Tooling readiness noted (Gradle commands, workflow updates, doc linting).

## Exit Criteria
- ArchUnit, Jacoco, PIT, and lint/formatting tasks aggregated under `qualityGate`.
- GitHub Actions workflow runs `qualityGate` per push/PR with caching.
- Documentation (how-to, roadmap, knowledge map, `_current-session.md`) explains commands, thresholds, and troubleshooting.
- `./gradlew --no-daemon qualityGate` passes with ≥90% coverage and ≥85% mutation score; reports archived under `build/reports/`.

## Follow-ups / Backlog
- Monitor PIT runtime; consider incremental or targeted execution if future modules extend the gate.
- Evaluate extending the gate to non-OCRA modules once prioritised via new specifications.
