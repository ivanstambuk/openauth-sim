# Feature 008 – OCRA Quality Automation Tasks

_Status: Draft_
_Last updated: 2025-09-30_

## Execution Notes
- Specification: `docs/4-architecture/specs/feature-008-ocra-quality-automation.md`.
- Keep each task ≤10 minutes; run failing checks/tests first to drive implementation.
- Record report locations and runtime observations in plan notes for future tuning.
- Follow-up: add targeted tests so PIT can cover `OcraChallengeFormat`/`OcraCredentialFactory` and extend mutation guard to CLI/REST facades without exclusions.
- Use `-Ppit.skip=true` with `./gradlew qualityGate` when mutation analysis needs to be skipped locally (documented runtime ≈27s with skip vs ≈1m56 full gate).

## Tasks
| ID | Task | Related Requirements | Status |
|----|------|----------------------|--------|
| Q101 | Complete analysis gate prep (checklist, knowledge map alignment, roadmap note) | QA-OCRA-001–QA-OCRA-006 | ✅ |
| Q102 | Add failing ArchUnit tests expressing module boundary expectations | QA-OCRA-001 | ✅ |
| Q103 | Implement/enforce ArchUnit rules and integrate with Gradle build | QA-OCRA-001, QA-OCRA-004 | ✅ |
| Q104 | Configure Jacoco aggregation with ≥90% thresholds and add failing verification task | QA-OCRA-003, QA-OCRA-004 | ✅ |
| Q105 | Wire PIT mutation testing with ≥85% threshold and failing guard | QA-OCRA-002, QA-OCRA-004 | ✅ |
| Q106 | Create `qualityGate` Gradle task bundling boundary, coverage, mutation, and lint checks | QA-OCRA-004 | ✅ |
| Q107 | Update/author GitHub Actions workflow invoking `./gradlew qualityGate` on push/PR | QA-OCRA-005 | ☐ |
| Q108 | Document gate usage, thresholds, and troubleshooting across docs | QA-OCRA-006 | ☐ |
| Q109 | Execute full gate, capture reports, and record closure notes | QA-OCRA-001–QA-OCRA-006 | ☐ |

Update this checklist as work progresses.
