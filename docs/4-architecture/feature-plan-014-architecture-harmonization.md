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
- R1406 – Consolidate telemetry via a common contract and adapters; update CLI/REST/UI emitters and tests. ☐ (Plan 2025-10-02 – Introduce `TelemetryEnvelope` interface + application-level adapter, add failing `TelemetryContractArchitectureTest`, and provide shared `TelemetryContractTestSupport` fixtures covering success/validation/error frames with sanitised payload flags before wiring facades.)
- R1407 – Restructure core into shared vs protocol-specific modules; update Gradle configuration, ArchUnit rules, and ensure build passes. ☐
  - R1407a – Record package-to-module mapping and add failing ArchUnit guard for `core-shared`/`core-ocra` boundaries. ☐
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
- Tasks coverage – PASS: Tasks T1401–T1419 (with T1410 aggregating the module split sub-tasks) map to AH-001–AH-005 and sequence tests before implementation.
- Constitution compliance – PASS: Work adheres to spec-first, clarification gate, test-first, documentation sync, dependency control principles.
- Tooling readiness – PASS: Plan lists `qualityGate`, scoped ArchUnit runs, and formatting commands; results logged here.

## Notes
- Pending: update roadmap entry and knowledge map once increments begin.
- Pending: capture telemetry schema decisions as part of R1406; log any open questions immediately if surfaced.
- 2025-10-02 – R1407 planning: `core-shared` will retain foundational `model`, `support`, and persistence primitives while `core-ocra` owns protocol descriptors, registry wiring, and OCRA migrations. `MapDbCredentialStore.Builder` will expose migration injection so ocra defaults move out of the shared module.
- 2025-10-02 – R1407a: Added `CoreModuleSplitArchitectureTest` enforcing that CLI/REST/UI avoid direct dependencies on OCRA internals and recorded target package mapping ahead of the module split (test currently red until migration).
- 2025-10-02 – R1407b: Introduced `core-shared` Gradle module, migrated `model`/`support` packages, and reran architecture guard (still red until ocra descriptors move and facades drop direct dependencies).
- 2025-10-02 – R1407c: Shifted serialization/encryption primitives into `core-shared` and taught `MapDbCredentialStore.Builder` to accept injected migrations while keeping current defaults.
- 2025-10-02 – R1407d: Launched `core-ocra` module with descriptor/registry/migration moves, added OCRA persistence helper, and rewired CLI/REST/UI dependencies; `core-ocra` tests cover legacy migration path.
- 2025-10-02 – R1407e: Expanded aggregated Jacoco/PIT configuration to include `core-ocra`, boosted module branch coverage to 90.87 %, and captured a passing `qualityGate` run (branches 0.9042, line 0.9691, PIT 91%).
- 2025-10-01 – R1402: Added `FacadeDelegationArchitectureTest` asserting current CLI/REST dependencies on core OCRA/MapDB; rules expect violations until shared application layer lands.
- 2025-10-01 – R1403 (in progress): Shared application module added; CLI evaluate/verify now delegate through `OcraEvaluationApplicationService` / `OcraVerificationApplicationService`. REST delegation and ArchUnit updates pending.
- 2025-10-02 – R1403a/b: Added targeted tests across core (`OcraReplayVerifier`, `OcraSecretMaterialSupport`, descriptor/persistence helpers) and REST (`OcraVerificationService`, evaluation failure mapping) to raise Jacoco branch coverage to 90.67 %. `./gradlew :rest-api:test` and `./gradlew qualityGate` now pass.
- 2025-10-02 – R1403 complete: REST/UI rely on shared application services; ArchUnit now enforces delegation to `io.openauth.sim.application.ocra`. `./gradlew qualityGate` reused configuration cache (≈12 s) with reflectionScan and architectureTest green.
- 2025-10-02 – R1404 complete: Shared inline identifier helper (`OcraInlineIdentifiers`) applied across CLI/REST; `OcraDtoNormalizationAlignmentTest` now exercises stored/inline flows without skips.
- 2025-10-02 – R1405 complete: Introduced `infra-persistence` module providing `CredentialStoreFactory`; CLI/REST delegate store provisioning and ArchUnit verifies MapDB access is confined to the factory.
- 2025-10-02 – R1406 planning: Define `TelemetryContract` + `TelemetryEmitter` abstractions in application module, add failing `TelemetryContractArchitectureTest` ensuring facades depend only on the shared adapter, introduce shared `TelemetryContractTestSupport` fixtures, and extend REST/CLI telemetry unit tests to assert consistent sanitisation fields before implementation.
- 2025-10-02 – R1406: Added shared `TelemetryContracts`/`TelemetryFrame`, migrated CLI/REST telemetry to the adapter, introduced architecture coverage, and refreshed REST telemetry snapshot; CLI tests assert command outputs without bespoke telemetry classes.

Keep this plan synced after each increment, marking completion timestamps and summarising findings in the Notes section.
