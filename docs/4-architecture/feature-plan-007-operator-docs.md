# Feature Plan 007 – Operator-Facing Documentation Suite

_Status: Complete_
_Last updated: 2025-09-30_

## Objective
Deliver an operator-oriented documentation package covering Java integrations, CLI usage, and REST endpoints while updating the README to reflect shipped capabilities and highlight the Swagger UI entry point. The plan tracks small, auditable increments that modernise existing guides and add new ones per Feature 007 specification.

Reference specification: `docs/4-architecture/specs/feature-007-operator-docs.md`.

## Success Criteria
- Java operators can follow a new how-to guide to drive OTP generation/replay flows from external Java applications without consulting source code.
- All OCRA CLI commands are documented in a single operator-friendly guide with realistic command examples and outcomes.
- A refreshed REST guide replaces the existing evaluation-only document and documents every available endpoint, including credential discovery and Swagger UI usage.
- `README.md` accurately reflects the current simulator capabilities, exposes the Swagger UI link, and removes outdated placeholder messaging.
- Roadmap, knowledge map, and relevant cross-links reference the new operator documentation.

## Proposed Increments
- D071 – Align specification & clarifications, capture open questions, and prepare doc outline notes. ✅ 2025-09-30
- D072 – Draft Java operator integration guide (structure, prerequisites, runnable examples); add failing TODO placeholders if coverage gaps remain. ✅ 2025-09-30
- D073 – Author CLI operations guide documenting all commands; include sample outputs and troubleshooting notes. ✅ 2025-09-30
- D074 – Replace the REST evaluation how-to with a comprehensive REST operations guide covering all endpoints and Swagger UI usage. ✅ 2025-09-30
- D075 – Update `README.md` to reflect current capabilities, add the Swagger UI link, and remove future-work placeholders. ✅ 2025-09-30
- D076 – Sync roadmap/knowledge map references and ensure cross-document links are updated. ✅ 2025-09-30

## Checklist Before Implementation
- [x] Specification created with clarified scope and requirements.
- [x] Open questions resolved and removed from `docs/4-architecture/open-questions.md` once captured in spec.
- [x] Tasks checklist mirrors the increments above with ≤10 minute steps and sequences verification before commits.
- [x] Analysis gate (docs/5-operations/analysis-gate-checklist.md) updated once tasks are ready.

## Analysis Gate (2025-09-30)
- [x] Specification populated with objectives, requirements, clarifications (≤5 questions satisfied).
- [x] Open questions log clear for Feature 007.
- [x] Feature plan references correct spec/tasks and aligns dependencies/success criteria.
- [x] Tasks map to every functional requirement with ≤10 minute increments and verification sequencing.
- [x] Planned work respects constitution principles (spec-first, clarification gate, documentation sync, dependency control).
- [x] Tooling readiness noted (`./gradlew spotlessApply check` before each commit).

Outcome: proceed to implementation; no follow-up actions required at this stage.

## Notes
- Confirm existing CLI/REST commands via `./gradlew :cli:runOcraCli --args="help"` and REST OpenAPI snapshots before finalising prose.
- Keep examples aligned with stored credential defaults (`data/ocra-credentials.db`).
- Include telemetry references where relevant so operators can correlate command/API invocations with logs.

Update this plan after each increment and mark complete when Feature 007 ships.
