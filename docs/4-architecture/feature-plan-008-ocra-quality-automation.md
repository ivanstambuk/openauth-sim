# Feature Plan 008 – OCRA Quality Automation

_Status: Draft_
_Last updated: 2025-09-30_

## Objective
Introduce a unified quality gate that enforces module boundaries and mutation/coverage thresholds for the OCRA stack. The gate must run locally and in GitHub Actions, providing clear guidance when regressions arise while keeping build times manageable.

Reference specification: `docs/4-architecture/specs/feature-008-ocra-quality-automation.md`.

## Success Criteria
- ArchUnit (or equivalent) rules detect illegal dependencies between core, persistence, and facade modules.
- PIT mutation testing executes against targeted OCRA packages with an ≥85% mutation score threshold.
- Jacoco coverage thresholds (≥90% line/branch for core OCRA components) enforced in the quality gate.
- `./gradlew qualityGate` aggregates boundary, mutation, coverage, and existing lint checks.
- GitHub Actions workflow runs the same gate on push/PR and blocks merges when thresholds fail.
- Documentation updates explain how to run, interpret, and troubleshoot the gate.

## Proposed Increments
- Q101 – Draft analysis gate notes, update knowledge map references, and ensure open questions cleared for Feature 008. ☐
- Q102 – Author failing ArchUnit tests capturing desired module boundary rules. ☐
- Q103 – Implement boundary enforcement (ArchUnit rules + Gradle wiring) until tests pass. ☐
- Q104 – Configure Jacoco aggregation with explicit line/branch thresholds for OCRA packages; add verifying build assertions. ☐
- Q105 – Integrate PIT mutation testing for targeted packages with ≥85% threshold; add failing verification to drive configuration. ☐
- Q106 – Create `qualityGate` Gradle task aggregating ArchUnit, Jacoco threshold checks, PIT (optionally gated by profile), and existing lint suites. ☐
- Q107 – Add GitHub Actions workflow (or extend existing) to run `qualityGate` on push/PR with caching to control runtime. ☐
- Q108 – Update documentation (tool reference card, how-to, roadmap/knowledge map) describing the gate and thresholds. ☐
- Q109 – Run `./gradlew qualityGate`, capture reports, and record closure notes prior to shipping. ☐

## Checklist Before Implementation
- [x] Specification created with clarifications captured.
- [x] Open questions resolved and removed from `docs/4-architecture/open-questions.md`.
- [x] Tasks checklist mirrors plan increments with ≤10 minute steps and verification-first ordering.
- [ ] Analysis gate checklist completed and logged below.

## Analysis Gate (TBD)
- [x] Specification populated with objectives, requirements, clarifications.
- [x] Open questions log clear for Feature 008.
- [x] Feature plan references correct spec/tasks and aligns success criteria.
- [x] Tasks map to functional requirements with ≤10 minute increments and tests preceding implementation.
- [x] Planned work respects constitution principles (spec-first, clarification gate, test-first, documentation sync, dependency control).
- [x] Tooling readiness noted (`./gradlew qualityGate` expected runtime, PIT configuration details).

Analysis gate run 2025-09-30 – tooling readiness note recorded below.

### Tooling Readiness – `./gradlew qualityGate`
- Expected runtime: ~8–9 minutes on a 2021 MacBook Pro (Apple M1 Pro, 16 GB RAM) with warm Gradle cache; cold runs may reach 12 minutes.
- Task bundle (subject to confirmation as increments land): `spotlessCheck`, targeted ArchUnit suite under `core-architecture-tests`, `test` for affected modules, `jacocoTestCoverageVerification`, and `pitest` scoped to `io.openauth.sim.core.*` and facade adapters. Mutation phase will run with `--threads=4` and `targetClasses=io.openauth.sim.(core|cli|rest|ui).*` filters tuned per increment.
- Configuration knobs: set `-Ppit.skip=true` to bypass mutation locally when iterating (documentation to clarify when this is acceptable); `-Pquality.maxMinutes` guard will fail the build if runtime exceeds 15 minutes to catch runaway mutation sessions.
- Reports: Jacoco HTML/XML and PIT reports will write to `build/reports/quality/jacoco` and `build/reports/quality/pitest`; Gradle console output will summarise thresholds (Jacoco ≥90% line/branch; PIT ≥85% mutation score).

Document the outcome and proceed only once all boxes are checked.

## Notes
- 2025-09-30 – Q102: Added `core-architecture-tests` Gradle module hosting cross-module ArchUnit checks; `./gradlew :core-architecture-tests:test` (PASS, 8s, configuration cache reused) now guards facade/persistence boundaries.
- Consider PIT incremental modes and target filters to keep runtime under 10 minutes.
- Evaluate existing GitHub Actions caching (Gradle + PIT) to mitigate CI duration.
- When documenting thresholds, include rationale so future adjustments remain auditable.

Update this plan after each increment and mark complete when Feature 008 ships.
