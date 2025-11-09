# Feature Plan 014 – Architecture Harmonization

_Status: Complete_
_Last updated: 2025-10-02_

## Objective
Unify OCRA orchestration, persistence provisioning, telemetry, and DTO normalization across all facades while preparing the codebase for protocol-specific module boundaries. Scope is limited to existing OCRA behaviour; no new credential families ship in this feature.

Reference specification: `docs/4-architecture/features/014/spec.md`.

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
- R1406 – Consolidate telemetry via a common contract and adapters; update CLI/REST/UI emitters and tests. ☑ (2025-10-02 – Introduced `TelemetryContracts` with shared adapters, added `TelemetryContractTestSupport`, and greened `TelemetryContractArchitectureTest` across CLI/REST/UI.)
- R1407 – Restructure core into shared vs protocol-specific modules; update Gradle configuration, ArchUnit rules, and ensure build passes. ☑ (2025-10-02 – Completed `core-shared`/`core-ocra` split, refreshed Gradle/ArchUnit wiring, and verified `qualityGate` + `architectureTest` success.)
  - R1407a – Record package-to-module mapping and add failing ArchUnit guard for `core-shared`/`core-ocra` boundaries. ☑ (2025-10-02 – Documented target package allocation and activated `CoreModuleSplitArchitectureTest` enforcing new boundaries.)
  - R1407b – Scaffold `core-shared` module (Gradle wiring) and migrate `model` + `support` packages under red tests. ☑ (2025-10-02 – Module created; shared packages moved; architecture guard still red.)
  - R1407c – Relocate serialization/encryption primitives to `core-shared`, exposing migration injection seams for consumers. ☑ (2025-10-02 – Shared module now hosts serialization/encryption packages; MapDB builder supports injected migrations.)
  - R1407d – Stand up `core-ocra` module by moving OCRA descriptors, registry, and migrations; update dependent build files. ☑ (2025-10-02 – `core-ocra` module created, ocra packages moved, build graph updated.)
  - R1407e – Refresh root Gradle (settings, PIT/Jacoco) and ArchUnit rules; drive module split to green via `architectureTest` + targeted module checks. ☑ (2025-10-02 – Added `core-ocra` to aggregated coverage/PIT targets, architecture suite passes, and `qualityGate` runs green.)

  | Package | Destination module |
  |---------|-------------------|
  | `io.openauth.sim.core.model..` | `core-shared` |
  | `io.openauth.sim.core.support..` | `core-shared` |
  | `io.openauth.sim.core.store.serialization..` | `core-shared` |
  | `io.openauth.sim.core.store.encryption..` | `core-shared` |
  | `io.openauth.sim.core.credentials.ocra..` | `core-ocra` |
  | `io.openauth.sim.core.credentials.CredentialRegistry` | `core-ocra` |
  | `io.openauth.sim.core.store.ocra..` | `core-ocra` |
- R1408 – Refresh documentation (AGENTS, how-to guides), knowledge map, and run `./gradlew spotlessApply check` + `qualityGate`; record metrics. ☑ (2025-10-02 – Updated AGENTS, persistence/REST how-to guides, telemetry snapshot, and re-ran spotless/quality gate post-doc edits.)

Ensure each increment stays within ≤30 minutes, following test-first cadence where applicable.

## Checklist Before Implementation
- [x] Specification clarifications resolved and logged.
- [x] Open questions tracked/cleared (update `docs/4-architecture/open-questions.md` if any arise).
- [x] Tasks ordered with tests before code, each ≤30 minutes.
- [x] Tooling readiness documented.
- [x] Analysis gate run before implementation commences.

### Tooling Readiness (initial)
- `./gradlew qualityGate` – regression coverage after module restructuring.
- `./gradlew :core-architecture-tests:test --tests "*ArchitectureHarmonization*"` – forthcoming ArchUnit suite for new boundaries.
- `./gradlew :cli:test`, `:rest-api:test`, `:ui:test` – verify facade migrations.
- `./gradlew spotlessApply` – formatting guard.

## Analysis Gate (2025-10-01)
- Specification completeness – PASS: Feature 014 spec documents objectives, requirements, and clarifications (`docs/4-architecture/features/014/spec.md`).
- Open questions review – PASS: `docs/4-architecture/open-questions.md` has no Feature 014 entries.
- Plan alignment – PASS: Plan references Feature 014 spec/tasks and mirrors success criteria.
- Tasks coverage – PASS: Tasks T1401–T1419 (with T1410 aggregating the module split sub-tasks) map to AH-001–AH-005 and sequence tests before implementation.
- Constitution compliance – PASS: Work adheres to spec-first, clarification gate, test-first, documentation sync, dependency control principles.
- Tooling readiness – PASS: Plan lists `qualityGate`, scoped ArchUnit runs, and formatting commands; results logged here.

## Notes
- 2025-10-02 – Roadmap and knowledge map updated; Workstream 12 now marked in progress and shared telemetry/module split relationships captured.
- 2025-10-01 – R1402: Added `FacadeDelegationArchitectureTest` asserting existing CLI/REST dependencies on core OCRA/MapDB; rules flipped green after R1403 completion.
- 2025-10-01 – R1403 kickoff: Shared application module scaffolded so CLI evaluation/verification delegate through `OcraEvaluationApplicationService` / `OcraVerificationApplicationService` ahead of REST/UI wiring.
- 2025-10-02 – R1403a/b: Added targeted tests across core (`OcraReplayVerifier`, `OcraSecretMaterialSupport`, descriptor/persistence helpers) and REST (`OcraVerificationService`, evaluation failure mapping) to raise Jacoco branch coverage to 90.67 %. `./gradlew :rest-api:test` and `./gradlew qualityGate` now pass.
- 2025-10-02 – R1403 complete: REST/UI rely on shared application services; ArchUnit now enforces delegation to `io.openauth.sim.application.ocra`. `./gradlew qualityGate` reused configuration cache (≈12 s) with `reflectionScan` and `architectureTest` green.
- 2025-10-02 – R1404 complete: Shared inline identifier helper (`OcraInlineIdentifiers`) applied across CLI/REST; `OcraDtoNormalizationAlignmentTest` now exercises stored/inline flows without skips.
- 2025-10-02 – R1405 complete: Introduced `infra-persistence` module providing `CredentialStoreFactory`; CLI/REST delegate store provisioning and ArchUnit verifies MapDB access is confined to the factory.
- 2025-10-02 – R1406: Added shared `TelemetryContracts`/`TelemetryFrame`, migrated CLI/REST telemetry to the adapter, introduced architecture coverage, and refreshed REST telemetry snapshot; CLI tests assert command outputs without bespoke telemetry classes.
- 2025-10-02 – R1407a: Documented package allocation and enabled `CoreModuleSplitArchitectureTest` enforcing new boundaries; suite now passes.
- 2025-10-02 – R1407b–e: Completed `core-shared` / `core-ocra` module split, relocated serialization/encryption primitives, wired migrations, refreshed Gradle/ArchUnit/PIT/Jacoco configuration, and recorded a passing `qualityGate` run (branches 0.9042, line 0.9691, PIT 91%).
- 2025-10-02 – T1411: Synced documentation (knowledge map updated with `infra-persistence` relationship) and reran `./gradlew spotlessApply` + `./gradlew qualityGate` (configuration cache reuse ≈1s/0.8s, no changes required).
- 2025-10-02 – R1408 complete: Refreshed AGENTS, persistence configuration how-to, CLI/REST guides, and REST telemetry snapshot to reference `CredentialStoreFactory` + shared telemetry adapters; post-edit runs of `./gradlew spotlessApply` (~1.2s) and `./gradlew qualityGate` (~0.8s) remained green.

Keep this plan synced after each increment, marking completion timestamps and summarising findings in the Notes section.
