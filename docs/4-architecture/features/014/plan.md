# Feature Plan 014 – Architecture Harmonization

_Linked specification:_ `docs/4-architecture/features/014/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
- Shared application services handle OCRA orchestration so CLI/REST/UI delegate through common code paths (FR-014-01, S-014-01).
- Persistence provisioning, DTO normalization, and telemetry adapters rely on shared modules/contracts (FR-014-02, FR-014-03, FR-014-05, S-014-02–S-014-04).
- `core` restructures into `core-shared` and `core-ocra` with updated ArchUnit guards and green quality gate (FR-014-04, S-014-05).
- Documentation/diagrams reflect the harmonized architecture for future protocols (S-014-06).

## Scope Alignment
- **In scope:** Application module creation/wiring, `infra-persistence` factory, telemetry contract adapters, DTO normalization helpers, core module split, documentation updates.
- **Out of scope:** New protocol support, new telemetry sinks, or persistence engine changes.

## Dependencies & Interfaces
- CLI/REST/UI modules consuming application services.
- `infra-persistence` module providing `CredentialStoreFactory`.
- `core-shared` / `core-ocra` modules referenced via Gradle settings and ArchUnit rules.
- Telemetry adapters referencing existing logging backends.

## Assumptions & Risks
- **Assumptions:** Existing tests cover critical OCRA flows; shared modules won’t regress external contracts; module split won’t significantly increase build times.
- **Risks / Mitigations:**
  - _Build churn:_ Adjust Gradle + ArchUnit incrementally, running targeted commands after each move.
  - _Telemetry leakage:_ Telemetry contract tests enforce sanitization before adapters ship.
  - _Persistence regressions:_ `CredentialStoreFactory` tests guarantee swappable stores and shared configuration.

## Implementation Drift Gate
- **Trigger:** After T-014-01–T-014-19 completed (2025-10-02).
- **Evidence:** Shared application services, DTO helpers, persistence factory, telemetry adapters, core module split, roadmap/knowledge-map updates, `qualityGate` logs.
- **Outcome:** Gate confirmed each FR/NFR requirement matched implementation and documentation.

## Increment Map
1. **I1 – Governance & analysis setup** _(T-014-01–T-014-03)_  
   - _Goal:_ Register roadmap entry, update knowledge map, run failing architecture tests, and execute analysis gate.  
   - _Commands:_ `rg -n "Feature 014" docs/4-architecture/roadmap.md`, `./gradlew :core-architecture-tests:test --tests "*ApplicationLayerArchitectureTest"`.  
   - _Exit:_ Documentation synced; red tests ready for implementation.
2. **I2 – Application services + DTO helpers** _(T-014-03–T-014-05, T-014-12–T-014-14)_  
   - _Goal:_ Introduce shared application services, DTO normalization helpers, and negative-path tests.  
   - _Commands:_ `./gradlew :application:test`, `./gradlew :rest-api:test --tests "*Ocra*ServiceTest"`, `./gradlew :cli:test`.  
   - _Exit:_ Facades delegate through application services; DTO tests green.
3. **I3 – Persistence factory** _(T-014-06–T-014-07)_  
   - _Goal:_ Ship `CredentialStoreFactory`, migrate facades/tests, update architecture guards.  
   - _Commands:_ `./gradlew :infra-persistence:test`, `./gradlew :cli:test :rest-api:test`.  
   - _Exit:_ Persistence wiring centralized; ArchUnit guard green.
4. **I4 – Telemetry contract** _(T-014-08–T-014-09)_  
   - _Goal:_ Add telemetry contract tests, implement adapters, refresh documentation.  
   - _Commands:_ `./gradlew :application:test --tests "*Telemetry*"`, `./gradlew :core-architecture-tests:test --tests "*Telemetry*"`, `./gradlew :cli:test :rest-api:test :ui:test`.  
   - _Exit:_ Telemetry adapters adopted across facades; fixtures updated.
5. **I5 – Core module split** _(T-014-10–T-014-19)_  
   - _Goal:_ Add ArchUnit guard, scaffold `core-shared`/`core-ocra`, migrate packages, update Gradle/PIT/Jacoco, drive quality gate green.  
   - _Commands:_ `./gradlew :core-architecture-tests:test --tests "*CoreModuleSplit*"`, `./gradlew :core-shared:test`, `./gradlew :core-ocra:test`, `./gradlew qualityGate`.  
   - _Exit:_ Module split enforced; quality gate passes.
6. **I6 – Documentation & final verification** _(T-014-11)_  
   - _Goal:_ Update AGENTS/how-to guides, knowledge map, diagrams, and rerun spotless + quality gate.  
   - _Commands:_ `./gradlew spotlessApply check`, `./gradlew qualityGate`.  
   - _Exit:_ Docs synced; feature marked complete.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-014-01 | I1–I2 / T-014-02–T-014-05, T-014-12–T-014-14 | Shared application services + DTO helpers. |
| S-014-02 | I3 / T-014-06–T-014-07 | Persistence factory adoption. |
| S-014-03 | I4 / T-014-08–T-014-09 | Telemetry contract + adapters. |
| S-014-04 | I2 / T-014-04–T-014-05 | Shared DTO normalization cross-facade. |
| S-014-05 | I5 / T-014-10–T-014-19 | Core module split enforcement. |
| S-014-06 | I6 / T-014-01, T-014-09, T-014-11 | Documentation/diagram updates. |

## Analysis Gate (2025-10-01)
- ✅ Spec complete with goals/requirements/clarifications.
- ✅ No open questions pending; roadmap/knowledge map entries filed.
- ✅ Plan/tasks mapped to FR/NFR IDs with tests-first increments.
- ✅ Tooling readiness recorded (architecture tests, module tests, quality gate, spotless).

## Exit Criteria
- Application services + DTO helpers in use across facades.
- `CredentialStoreFactory` supplies persistence for all facades.
- Telemetry adapters share sanitized contracts.
- Core module split enforced by ArchUnit and quality gate.
- Documentation updated; `./gradlew spotlessApply check` + `qualityGate` pass.

## Follow-ups / Backlog
- Replicate the shared architecture pattern for upcoming protocols (FIDO2, EMV, EUDI) once scoped.
- Consider introducing OpenTelemetry exporters in a future feature; telemetry contract now centralizes adapters.
- Monitor Gradle build times post-split; adjust caching or composite builds if runtimes increase beyond the ±10% target.
