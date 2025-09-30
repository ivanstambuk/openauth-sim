# Feature 008 – OCRA Quality Automation Tasks

_Status: Draft_
_Last updated: 2025-09-30_

## Execution Notes
- Specification: `docs/4-architecture/specs/feature-008-ocra-quality-automation.md`.
- Keep each task ≤10 minutes; run failing checks/tests first to drive implementation.
- Record report locations and runtime observations in plan notes for future tuning.
- Follow-up: add targeted tests so PIT can cover `OcraChallengeFormat`/`OcraCredentialFactory` and extend mutation guard to CLI/REST facades without exclusions.
- Use `-Ppit.skip=true` with `./gradlew qualityGate` when mutation analysis needs to be skipped locally (documented runtime ≈27s with skip vs ≈1m56 full gate).
- Reference `docs/5-operations/quality-gate.md` for command summary, thresholds, and troubleshooting tips.
- Current baseline (2025-09-30): Jacoco aggregated 93.65 % line / 83.18 % branch; PIT mutation score ~94 % with surviving conditionals noted in the latest report (no new PIT run this increment).

## Tasks
| ID | Task | Related Requirements | Status |
|----|------|----------------------|--------|
| Q101 | Complete analysis gate prep (checklist, knowledge map alignment, roadmap note) | QA-OCRA-001–QA-OCRA-006 | ✅ |
| Q102 | Add failing ArchUnit tests expressing module boundary expectations | QA-OCRA-001 | ✅ |
| Q103 | Implement/enforce ArchUnit rules and integrate with Gradle build | QA-OCRA-001, QA-OCRA-004 | ✅ |
| Q104 | Configure Jacoco aggregation with ≥90% thresholds and add failing verification task | QA-OCRA-003, QA-OCRA-004 | ✅ |
| Q105 | Wire PIT mutation testing with ≥85% threshold and failing guard | QA-OCRA-002, QA-OCRA-004 | ✅ |
| Q106 | Create `qualityGate` Gradle task bundling boundary, coverage, mutation, and lint checks | QA-OCRA-004 | ✅ |
| Q107 | Update/author GitHub Actions workflow invoking `./gradlew qualityGate` on push/PR | QA-OCRA-005 | ✅ |
| Q108 | Document gate usage, thresholds, and troubleshooting across docs | QA-OCRA-006 | ✅ |
| Q109 | Execute full gate, capture reports, and record closure notes | QA-OCRA-001–QA-OCRA-006 | ✅ |
| Q110 | Review aggregated Jacoco report and catalogue coverage gaps blocking ≥90% thresholds | QA-OCRA-003 | ✅ |
| Q111 | Add failing tests for uncovered OCRA scenarios (starting with `OcraChallengeFormat`) | QA-OCRA-003 | ✅ |
| Q112 | Implement test fixtures/assertions to satisfy coverage gaps and rerun targeted modules | QA-OCRA-003 | ✅ |
| Q113 | Add REST facade tests for `OcraEvaluationService` error handling and satisfy assertions | QA-OCRA-003 | ✅ |
| Q114 | Increase CLI coverage (launcher + command errors) and verify expectations | QA-OCRA-003 | ✅ |
| Q115 | Re-run `./gradlew qualityGate` to verify thresholds met and update documentation | QA-OCRA-001–QA-OCRA-006 | ☐ |
| Q116 | Add REST UI form tests covering mode toggles and scrubbing | QA-OCRA-003 | ✅ |
| Q117 | Extend REST validation failure coverage (timestamp/challenge) | QA-OCRA-003 | ✅ |
| Q118 | Cover Maintenance CLI usage/compact flows | QA-OCRA-003 | ✅ |
| Q119 | Add REST controller coverage (UI summaries + application boot) | QA-OCRA-003 | ✅ |
| Q120 | Raise `RestApiApplication` coverage with targeted main-context test | QA-OCRA-003 | ✅ |
| Q121 | Extend `OcraCli` coverage for delete/list error + verbose branches | QA-OCRA-003 | ✅ |
| Q122 | Exercise `MaintenanceCli` failure/verbose flows to close helper gaps | QA-OCRA-003 | ✅ |
| Q123 | Expand `OcraEvaluationServiceTest` for remaining `FailureDetails` mappings | QA-OCRA-003 | ✅ |
| Q124 | Add credential-reference coverage for `OcraEvaluationService` success/failure flows | QA-OCRA-003 | ✅ |
| Q125 | Add failing core tests for parser/calculator edge cases (challenge validation, session/session padding, timestamp fallback) | QA-OCRA-003 | ✅ |
| Q126 | Add CLI helper tests covering launcher exit + error telemetry (`AbstractOcraCommand`, import failure) | QA-OCRA-003 | ✅ |
| Q127 | Draft failing CLI tests for `OcraCli` help/emit/hasText/ensureParentDirectory branches | QA-OCRA-003 | ✅ |
| Q128 | Implement CLI fixtures to cover list/delete verbose and exception flows, drive tests green | QA-OCRA-003 | ✅ |
| Q129 | Write REST DTO tests capturing trim/null semantics and defensive copy behaviour | QA-OCRA-003 | ✅ |
| Q130 | Extend `OcraEvaluationServiceTest` for timestamp/challenge/telemetry/failure branches | QA-OCRA-003 | ✅ |
| Q131 | Run `./gradlew jacocoAggregatedReport` and log updated coverage metrics | QA-OCRA-003 | ✅ |
| Q132a | Add failing core tests for descriptor/factory request validation branches, drive coverage ≥90% branch | QA-OCRA-003 | ✅ |
| Q132b | Extend CLI delete/list command coverage (verbose + telemetry branches) to reach ≥90% branch | QA-OCRA-003 | ✅ |
| Q132c | Add REST controller/service tests covering success/error permutations for ≥90% branch | QA-OCRA-003 | ✅ |
| Q132d | Cover telemetry/persistence adapters (failure detail mapping, maintenance helpers) for ≥90% branch | QA-OCRA-003 | ✅ |
| Q132 | Raise Jacoco thresholds to 0.90/0.90, run full `./gradlew qualityGate`, update docs | QA-OCRA-001–QA-OCRA-006 | ☐ |

Update this checklist as work progresses.
