# Feature Plan 014 – Architecture Harmonization

_Status: In Progress_
_Last updated: 2025-10-02_

## Objective
Unify OCRA orchestration, persistence provisioning, telemetry, and DTO normalization across all facades while preparing the codebase for protocol-specific module boundaries. Scope is limited to existing OCRA behaviour; no new credential families ship in this feature.

Reference specification: `docs/4-architecture/specs/feature-014-architecture-harmonization.md`.

## Success Criteria
- Shared OCRA application layer exposes evaluation and verification services consumed by CLI, REST, and UI modules (satisfies AH-001, AH-005).
- Central `CredentialStoreFactory` replaces per-facade MapDB bootstrapping (AH-002).
- Common telemetry contract feeds each facade without leaking secrets and retains current log surfaces (AH-003, AH-NFR-002).
- Core modules reorganized into protocol-specific boundaries with updated ArchUnit enforcement and green `qualityGate` (AH-004, AH-NFR-001).
- External surfaces (CLI flags, REST contracts, UI flows) remain backwards compatible (AH-NFR-003).

## Clarifications
_All clarifications captured in the specification have been resolved; no additional questions are open as of 2025-10-02._

## Proposed Increments
- R1401 – Register Feature 014 across roadmap/knowledge map; confirm no open questions. ☑ (2025-10-01 – roadmap row added, knowledge map follow-up logged)
- R1402 – Add failing architecture tests capturing desired module boundaries and facade delegation expectations. ☑ (2025-10-01 – ArchUnit guards assert current violations in CLI/REST; will flip once app layer lands)
- R1403 – Implement shared OCRA application layer (evaluation + verification services) with dependency injection seams; update facades to delegate through it. ☑ (2025-10-02 – REST/UI delegate via OcraApplicationConfiguration; ArchUnit enforces application-layer dependency)
- R1403a – Extend application and core tests to cover negative/exceptional paths (credential missing, secret material mismatches) restoring branch coverage buffer. ☑ (2025-10-02)
- R1403b – Expand REST verification service tests for `handleInvalid` and command envelope branches; ensure Jacoco branch coverage ≥0.90. ☑ (2025-10-02)
- R1404 – Introduce shared DTO/normalization library and migrate CLI/REST/UI validation to use it (tests first). ☑ (2025-10-02 – Shared inline identifier helper added to `application`; CLI/REST consume it and alignment tests pass across stored/inline flows)
- R1405 – Implement `CredentialStoreFactory` infrastructure module and refactor facades/integration tests to use it. ☑ (2025-10-02 – New `infra-persistence` module supplies factory; CLI/REST delegate store provisioning and ArchUnit rule enforces factory usage)
- R1406 – Consolidate telemetry via a common contract and adapters; update CLI/REST/UI emitters and tests. ☐ (Plan 2025-10-02 – Introduce `TelemetryEnvelope` interface + application-level adapter, add failing contract tests in core-architecture-tests before wiring facades)
- R1407 – Restructure core into shared vs protocol-specific modules; update Gradle configuration, ArchUnit rules, and ensure build passes. ☐
- R1408 – Refresh documentation (AGENTS, how-to guides), knowledge map, and run `./gradlew spotlessApply check` + `qualityGate`; record metrics. ☐

Ensure each increment stays within ≤10 minutes, following test-first cadence where applicable.

## Checklist Before Implementation
- [x] Specification clarifications resolved and logged.
- [x] Open questions tracked/cleared (update `docs/4-architecture/open-questions.md` if any arise).
- [x] Tasks ordered with tests before code, each ≤10 minutes.
- [x] Tooling readiness documented.
- [x] Analysis gate run before implementation commences.

### Tooling Readiness (initial)
- `./gradlew qualityGate` – regression coverage after module restructuring.
- `./gradlew :core-architecture-tests:test --tests "*ArchitectureHarmonization*"` – forthcoming ArchUnit suite for new boundaries.
- `./gradlew :cli:test`, `:rest-api:test`, `:ui:test` – verify facade migrations.
- `./gradlew spotlessApply` – formatting guard.

## Analysis Gate (2025-10-01)
- Specification completeness – PASS: Feature 014 spec documents objectives, requirements, and clarifications (`docs/4-architecture/specs/feature-014-architecture-harmonization.md`).
- Open questions review – PASS: `docs/4-architecture/open-questions.md` has no Feature 014 entries.
- Plan alignment – PASS: Plan references Feature 014 spec/tasks and mirrors success criteria.
- Tasks coverage – PASS: Tasks T1401–T1411 map to AH-001–AH-005 and sequence tests before implementation.
- Constitution compliance – PASS: Work adheres to spec-first, clarification gate, test-first, documentation sync, dependency control principles.
- Tooling readiness – PASS: Plan lists `qualityGate`, scoped ArchUnit runs, and formatting commands; results logged here.

## Notes
- Pending: update roadmap entry and knowledge map once increments begin.
- Pending: capture telemetry schema decisions as part of R1406; log any open questions immediately if surfaced.
- 2025-10-01 – R1402: Added `FacadeDelegationArchitectureTest` asserting current CLI/REST dependencies on core OCRA/MapDB; rules expect violations until shared application layer lands.
- 2025-10-01 – R1403 (in progress): Shared application module added; CLI evaluate/verify now delegate through `OcraEvaluationApplicationService` / `OcraVerificationApplicationService`. REST delegation and ArchUnit updates pending.
- 2025-10-02 – R1403a/b: Added targeted tests across core (`OcraReplayVerifier`, `OcraSecretMaterialSupport`, descriptor/persistence helpers) and REST (`OcraVerificationService`, evaluation failure mapping) to raise Jacoco branch coverage to 90.67 %. `./gradlew :rest-api:test` and `./gradlew qualityGate` now pass.
- 2025-10-02 – R1403 complete: REST/UI rely on shared application services; ArchUnit now enforces delegation to `io.openauth.sim.application.ocra`. `./gradlew qualityGate` reused configuration cache (≈12 s) with reflectionScan and architectureTest green.
- 2025-10-02 – R1404 complete: Shared inline identifier helper (`OcraInlineIdentifiers`) applied across CLI/REST; `OcraDtoNormalizationAlignmentTest` now exercises stored/inline flows without skips.
- 2025-10-02 – R1405 complete: Introduced `infra-persistence` module providing `CredentialStoreFactory`; CLI/REST delegate store provisioning and ArchUnit verifies MapDB access is confined to the factory.
- 2025-10-02 – R1406 planning: Define `TelemetryContract` + `TelemetryEmitter` abstractions in application module, add failing `TelemetryContractArchitectureTest` ensuring facades depend only on the shared adapter, and extend REST/CLI telemetry unit tests to assert consistent sanitisation fields before implementation.

Keep this plan synced after each increment, marking completion timestamps and summarising findings in the Notes section.
