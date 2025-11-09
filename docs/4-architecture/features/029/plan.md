# Feature Plan 029 – PMD Rule Hardening

_Linked specification:_ `docs/4-architecture/features/029/spec.md`  
_Status:_ Planned  
_Last updated:_ 2025-10-19

## Vision & Success Criteria
- Raise static-analysis coverage by enabling the agreed PMD error-prone, best-practice, and design rules without introducing excessive false positives.
- Enforce `LawOfDemeter` for core domain and application service packages as a locality-of-behaviour guardrail while documenting sanctioned fluent interfaces.
- Keep the build green by iteratively remediating violations, adding narrowly scoped exclusions, and ensuring `./gradlew --no-daemon spotlessApply check` (including PMD) passes before closing the feature.

## Scope Alignment
- **In scope:** PMD configuration updates, per-module remediation required to satisfy the new rules, documentation updates (AGENTS, quality runbooks), and telemetry around rule adoption if needed.
- **Out of scope:** Introducing additional static-analysis tools, rewriting architectural seams, or modifying dependency versions.

## Dependencies & Interfaces
- PMD configuration shared across modules via `config/pmd/ruleset.xml`.
- Potential Gradle wiring adjustments (ensuring `pmdMain`/`pmdTest` include the new rule files or exclude patterns).
- Law-of-Demeter whitelist resource (new file under `config/pmd/`) consumed by the PMD plugin.
- Documentation touchpoints: `AGENTS.md`, `docs/5-operations/analysis-gate-checklist.md`, and any contributor how-to guides referencing lint workflows.

## Increment Breakdown (≤30 minutes each)
1. **I1 – PMD 7 upgrade baseline**  
   - Bump the PMD toolVersion in `gradle/libs.versions.toml` / `build.gradle.kts` and verify Gradle compatibility (PMD 7 requires Gradle ≥8.6; workspace currently uses 8.10).  
   - Run `./gradlew --no-daemon pmdMain pmdTest` to capture migration fallout (rule name changes, reporting format shifts) before enabling additional rules.  
   - Document CLI/reporting/property changes surfaced by PMD 7 so downstream increments can address them deliberately.  
   - _2025-10-19 – Updated PMD to 7.17.0, refreshed dependency locks via `--write-locks`, and captured baseline failures (`AssignmentInOperand`) across `MaintenanceCli`, core `CborDecoder`/`SimpleJson`, and `OcraReplayVerifierBenchmark`; reports located under the module `build/reports/pmd/` directories. Follow-up cleanup removed the flagged increments and PMD now passes (`./gradlew --no-daemon pmdMain pmdTest`)._

2. **I2 – Governance sync & baseline capture**  
   - Create spec/plan/tasks artefacts, add a roadmap entry, and refresh `docs/_current-session.md`.  
   - Record the Law-of-Demeter heuristic directive under clarifications.  
   - _2025-10-19 – Started: spec/plan/tasks preparation underway; roadmap/session updates pending._

3. **I3 – Ruleset expansion & dry run**  
  - Append the targeted rule references (including `category/java/bestpractices.xml/NonExhaustiveSwitch`) to `config/pmd/ruleset.xml`.  
  - Run `./gradlew --no-daemon pmdMain pmdTest` across the full codebase to capture baseline violations and log them in the plan/tasks for follow-up.  
  - _2025-10-19 – Dry run executed by temporarily adding `NonExhaustiveSwitch`; `./gradlew --no-daemon pmdMain pmdTest` completed with zero violations reported across all modules._  
  - _2025-10-19 – Rule enabled permanently; both `./gradlew --no-daemon pmdMain pmdTest` and `./gradlew --no-daemon spotlessApply check` remain green._

4. **I4 – Law-of-Demeter scoping**  
   - Introduce a dedicated whitelist resource (e.g., `config/pmd/law-of-demeter-excludes.txt`) referenced via `<exclude-pattern>`.  
   - Limit enforcement to domain/service packages and document the rationale in the plan.  
   - Run PMD to confirm only intended packages are evaluated.

5. **I5 – Domain module remediation**  
   - Resolve violations across `core/`, `core-ocra/`, and related domain packages, extracting helpers or simplifying call chains as needed.  
   - Add targeted unit tests where behaviour changes to ensure coverage.  
   - Commands: `./gradlew --no-daemon :core:test`, `./gradlew --no-daemon :core-ocra:test`, plus module-specific PMD tasks if needed.

6. **I6 – Service/facade remediation**  
   - Address rule findings in `application/`, `infra-persistence/`, and facade modules (`cli/`, `rest-api/`, `ui/`).  
   - Ensure fluent APIs remain whitelisted; extend exclude patterns only with justification noted in tasks.  
   - Commands: `./gradlew --no-daemon :application:test`, `:rest-api:test`, `:cli:test`, `:ui:test` (if applicable).

7. **I7 – Documentation & governance updates**  
   - Update `AGENTS.md`, runbooks, and the analysis gate checklist with guidance on the new PMD rules and Law-of-Demeter interpretation.  
   - Reflect rule coverage in the knowledge map if new quality automation relationships emerge.

8. **I8 – Quality gate & closure**  
   - Execute `./gradlew --no-daemon spotlessApply check` to verify a green build.  
  - Close feature artefacts (tasks checklist, current-session entry) and summarise outcomes in the roadmap.

## Dependencies
- Requires existing Gradle PMD plugin configuration.  
- Potential cross-team coordination if violations intersect with in-progress features (communicate via roadmap/tasks).
