# Feature Plan 011 – Reflection Policy Hardening

_Status: Complete_
_Last updated: 2025-10-02_

## Objective
Remove all reflection usage from the OpenAuth Simulator codebase, refactor affected services to expose explicit seams for testing, and tighten automation/documentation so reflective patterns cannot reappear.

Reference specification: `docs/4-architecture/features/011/spec.md`.

## Success Criteria
- Repository contains no direct reflection API usage (production or tests) except for explicitly documented exemptions in the specification.
- CLI, REST, and core modules expose deterministic collaborators/interfaces that make previously reflective tests pass without reflective access.
- New reflection guardrail (ArchUnit rule + Gradle regex scan) runs inside `./gradlew qualityGate` and fails when reflection is introduced.
- `AGENTS.md` communicates the anti-reflection policy and mitigation strategies; related documentation stays in sync.
- `./gradlew spotlessApply check` passes after all refactors and guard integrations.

## Proposed Increments
- R1101 – Register this feature in roadmap/knowledge-map notes and capture current reflection inventory. ☑ (2025-10-01 – roadmap updated; inventory logged below)
- R1102 – Run `docs/5-operations/analysis-gate-checklist.md` for Feature 011; record results here. ☑ (2025-10-01 – see Analysis Gate section)
- R1103 – Add failing reflection guard tests (ArchUnit + Gradle regex task) demonstrating current reflective usage. ☑ (2025-10-01 – tests red as expected)
- R1104 – Implement reflection guard (ArchUnit rule + regex-based Gradle check) and wire into `qualityGate`. ☑ (2025-10-01 – `reflectionScan` wired to quality gate)
- R1105 – Introduce explicit collaborator/interface for CLI OCRA commands, migrate tests away from reflection. ☑ (2025-10-01 – CLI tests call package-private helpers)
- R1106 – Refactor CLI maintenance command parsing to expose structured DTO/test seam; remove reflection from tests. ☑ (2025-10-01 – records exposed; tests call accessors directly)
- R1107 – Refactor REST OCRA services (evaluation + verification) to surface required seams, updating tests accordingly. ☑ (2025-10-01 – evaluation + verification tests now call package-private seams; reflection removed)
- R1108 – Replace reflection in core OCRA credential tests with direct API access or purpose-built fixtures. ☑ (2025-10-01 – MapDb store + Ocra factory expose package-private seams; tests updated)
- R1109 – Refresh documentation (`AGENTS.md`, plan/spec notes) describing the policy and guard usage. ☑ (2025-10-01 – AGENTS now documents no-reflection policy referencing Feature 011)
- R1110 – Update knowledge map with new seams/automation, run `./gradlew spotlessApply check`, and record metrics. ☑ (2025-10-01 – knowledge map extended; `spotlessApply check` + `reflectionScan` green)

Update increment statuses/date stamps as work progresses; ensure each increment stays within a ≤30 minute change window.

## Checklist Before Implementation
- [x] Specification clarifications resolved (see Clarifications section).
- [x] Open questions logged/cleared for Feature 011.
- [x] Tasks mapped to requirements with ≤30 minute increments in test-first order.
- [x] Planned work aligns with constitutional principles (spec-first, clarification gate, test-first, documentation sync, dependency control).
- [x] Tooling readiness captured (commands, expected runtime, environment notes).

### Tooling Readiness
- `./gradlew qualityGate` – target entry point; will include new ArchUnit rule and regex scan once implemented.
- `./gradlew :core-architecture-tests:test --tests "*Reflection*"` – scoped run for the upcoming ArchUnit rule.
- `./gradlew reflectionScan` (to be introduced) – standalone regex scan for reflective APIs; expected runtime <10s on developer laptop.
- Standard formatting via `./gradlew spotlessApply` before committing.

## Analysis Gate (2025-10-01)
Checklist executed per `docs/5-operations/analysis-gate-checklist.md`.

- Specification complete with objectives, requirements, clarifications (Feature 011 spec lines 1–47).
- Open questions log clear for Feature 011 (docs/4-architecture/open-questions.md).
- Plan references correct spec/tasks; success criteria align with REF-001–REF-004.
- Tasks cover all functional requirements, sequenced tests before implementation, ≤30 minute increments.
- Work honours constitution guardrails (spec-first, clarification gate satisfied, test-first increments, documentation sync plan, no new dependencies).
- Tooling readiness documented above (`qualityGate`, targeted ArchUnit/scan tasks).

No blockers identified; proceed to R1103 (failing guard tests).

## Notes
- 2025-10-01 – Plan drafted pending analysis gate; roadmap/task documents to be updated in subsequent increments.
- 2025-10-01 – Reflection inventory captured: CLI tests (`OcraCliTest`, `OcraCliHelpersTest`, `MaintenanceCliTest`, `OcraCliErrorHandlingTest`), core tests (`OcraCredentialFactoryLoggingTest`, `MapDbCredentialStoreTest`), REST tests (`RestPersistenceConfigurationTest`, `OcraEvaluationServiceTest`, `OcraVerificationServiceTest`), and documentation snippet (`docs/2-how-to/generate-ocra-test-vectors.md`).
- 2025-10-01 – R1103: added `ReflectionPolicyTest` (ArchUnit) and `ReflectionRegexScanTest` (regex guard). Initial runs flagged CLI/core/REST reflections; suite now passes after R1108.
- 2025-10-01 – R1104: Registered Gradle `reflectionScan` task (qualityGate dependency); task now returns clean results following R1108 (see `./gradlew reflectionScan`).
- 2025-10-01 – R1105: Updated `OcraCli` helpers to expose package-private seams and replaced CLI tests' reflective calls with direct method invocations; `./gradlew :cli:test` passes.
- 2025-10-01 – R1106: Exposed `MaintenanceCli` parse records/methods, introduced `TestPaths` helper, and removed reflection from maintenance CLI tests; CLI suite remains green.
- 2025-10-01 – R1107 (partial): Converted `RestPersistenceConfiguration` helpers and `OcraEvaluationService` nested types/methods to package-private seams; updated evaluation tests to call APIs directly and `./gradlew :rest-api:test --tests OcraEvaluationServiceTest` passes.
- 2025-10-01 – R1107 (complete): `OcraVerificationService` now exposes package-private seams, and verification tests assert telemetry by invoking public helpers; `./gradlew :rest-api:test --tests OcraVerificationServiceTest` passes.
- 2025-10-01 – R1108: `MapDbCredentialStore` now exposes a package-private cache view and `OcraCredentialFactory` publishes telemetry accessors, eliminating reflection from core tests; `./gradlew :core:test` green.
- 2025-10-01 – R1109: Updated `AGENTS.md` to include the reflection ban with pointers to Feature 011 and guardrails; plan/spec notes synced.
- 2025-10-01 – R1110: Knowledge map documents new package-private seams; `./gradlew spotlessApply check` (≈21s, mutation score 91%, test strength 94%) and `./gradlew reflectionScan` both green on local run.

Record additional observations, benchmark data, or tool usage here during implementation.
