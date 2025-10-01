# Feature Plan 013 – Java 17 Language Enhancements

_Status: Complete_
_Last updated: 2025-10-01_

## Objective
Apply targeted Java 17 features across the CLI and REST OCRA modules to improve compile-time guarantees and documentation clarity without altering external contracts. Reference specification: `docs/4-architecture/specs/feature-013-java17-enhancements.md`.

## Success Criteria
- `OcraCli.AbstractOcraCommand` is declared sealed with an explicit permit list covering all existing subcommands (J17-CLI-001).
- REST OCRA evaluation/verification services rely on sealed request variants instead of nullable fields when normalising inputs (J17-REST-002).
- REST controller OpenAPI example payloads use Java text blocks while preserving snapshot expectations (J17-DOC-003).
- `./gradlew spotlessApply check` and `./gradlew qualityGate` complete successfully after the changes (J17-NFR-001).

## Proposed Increments
- R1301 – Register Feature 013 in roadmap knowledge artefacts and capture baseline context.
- R1302 – Update `docs/4-architecture/tasks/feature-013-java17-enhancements.md` with ≤10 minute increments.
- R1303 – Run `docs/5-operations/analysis-gate-checklist.md` and document outcomes here.
- R1304 – Introduce sealed CLI command hierarchy with supporting tests (test-first cadence).
  - Completed 2025-10-01 – `OcraCli.AbstractOcraCommand` sealed with explicit permit list, tests updated to assert sealing and to use production subcommands.
- R1305 – Implement sealed request variants for REST OCRA evaluation, update tests, and ensure pattern matching covers all cases.
  - Completed 2025-10-01 – Evaluation service now emits `StoredCredential`/`InlineSecret` variants; tests exercise trimming and variant selection.
- R1306 – Mirror sealed request variant refactor for REST OCRA verification, updating telemetry flows/tests.
  - Completed 2025-10-01 – Verification service refactored to sealed variants with telemetry assertions covering stored vs inline flows.
- R1307 – Replace OpenAPI example strings with Java text blocks and refresh relevant tests/snapshots.
  - Completed 2025-10-01 – OCRA evaluation/verification controllers converted to text blocks; regression tests updated.
- R1308 – Run formatting + quality gate, update knowledge map/notes, and capture metrics.
  - Completed 2025-10-01 – `spotlessApply`, `check`, and `qualityGate` executed; metrics recorded below.
- R1309 – Audit CLI modules for additional sealed-hierarchy opportunities and document outcome.
  - Completed 2025-10-01 – No other CLI command hierarchies require sealing; guidance noted in spec/tasks.
- R1310 – Audit REST controllers for escaped JSON examples and confirm text block policy.
  - Completed 2025-10-01 – No additional controllers contain escaped JSON; policy captured in spec.

Update increment statuses and timestamps as work completes.

## Checklist Before Implementation
- [x] Specification clarifications resolved (Feature 013 spec).
- [x] Open questions logged/cleared for Feature 013 (none required; log remains empty as of 2025-10-01).
- [x] Tasks mapped to requirements with ≤10 minute increments in test-first order.
- [x] Planned work honours constitutional guardrails (spec-first, clarification gate satisfied, test-first strategy, documentation sync, dependency freeze).
- [x] Tooling readiness documented (commands, expected runtime, environment notes).

### Tooling Readiness
- `./gradlew :cli:test` – verifies CLI sealed hierarchy changes quickly (~12s warm).
- `./gradlew :rest-api:test --tests "*Ocra*ServiceTest"` – focused REST service validation (~18s warm).
- `./gradlew rest-api:test` – regression run for controller snapshots/text blocks (~40s warm).
- `./gradlew spotlessApply` – formatter for Java/Gradle sources (~8s warm).
- `./gradlew qualityGate` – full guard prior to commit (~2m warm, includes mutation tests).

## Analysis Gate
- 2025-10-01 – Checklist completed: spec clarifications confirmed, roadmap/tasks synced, tooling readiness captured above, and no open questions outstanding. Proceed to R1304 after test-first adjustments are drafted.

## Notes
- 2025-10-01 – Spec drafted to cover sealed CLI hierarchy, REST request variants, and text-block conversion.
- 2025-10-01 – `./gradlew rest-api:test --tests "*Ocra*ServiceTest"`, `./gradlew check`, and `./gradlew qualityGate` rerun after sealed variant and text block updates (no regressions, PIT unchanged).
