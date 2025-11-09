# Feature 014 Tasks – Architecture Harmonization

_Status: Complete_  
_Last updated: 2025-10-02_

## Checklist
- [x] T1401 – Register Feature 014 in roadmap and knowledge map; confirm no open questions remain (S14-06).
  _Intent:_ Anchor the harmonization scope across governance docs before touching code.
  _Verification commands:_
  - `rg -n "Feature 014" docs/4-architecture/roadmap.md`
  - `rg -n "Feature 014" docs/4-architecture/knowledge-map.md`

- [x] T1402 – Add failing ArchUnit tests asserting facades delegate to the shared application layer (S14-01).
  _Intent:_ Create red tests that forbid direct facade → domain coupling.
  _Verification commands:_
  - `./gradlew --no-daemon :core-architecture-tests:test --tests "*ApplicationLayerArchitectureTest"`

- [x] T1403 – Implement shared application services and drive the new guards green (S14-01, S14-04).
  _Intent:_ Move orchestration/validation logic into `application` and update CLI/REST/UI wiring.
  _Verification commands:_
  - `./gradlew --no-daemon :application:test`
  - `./gradlew --no-daemon :core-architecture-tests:test --tests "*ApplicationLayer*"`

- [x] T1404 – Add failing DTO normalization tests covering inline vs stored flows across CLI/REST (S14-04).
  _Intent:_ Capture expected behaviour before extracting shared DTOs.
  _Verification commands:_
  - `./gradlew --no-daemon :core-architecture-tests:test --tests "*DtoNormalizationTest"`

- [x] T1405 – Implement shared DTO/normalization library and migrate facades (S14-04).
  _Intent:_ Introduce reusable requests/responses so every facade shares validation paths.
  _Verification commands:_
  - `./gradlew --no-daemon :application:test`
  - `./gradlew --no-daemon :rest-api:test --tests "*Ocra*"`

- [x] T1406 – Add failing tests around centralized `CredentialStoreFactory` (S14-02).
  _Intent:_ Prove the factory design before wiring it into facades.
  _Verification commands:_
  - `./gradlew --no-daemon :infra-persistence:test --tests "*CredentialStoreFactory*"`

- [x] T1407 – Implement `CredentialStoreFactory` infrastructure and update facades/tests (S14-02).
  _Intent:_ Ensure CLI/REST/UI obtain stores through the shared factory with swappable backends.
  _Verification commands:_
  - `./gradlew --no-daemon :cli:test :rest-api:test`

- [x] T1408 – Add failing telemetry contract tests to enforce sanitized outputs (S14-03).
  _Intent:_ Establish red coverage for success/validation/failure frames before introducing adapters.
  _Verification commands:_
  - `./gradlew --no-daemon :application:test --tests "*TelemetryContractTestSupport*"`
  - `./gradlew --no-daemon :core-architecture-tests:test --tests "*TelemetryContractArchitectureTest"`

- [x] T1409 – Implement telemetry contract + adapters and refresh docs/tests (S14-03, S14-06).
  _Intent:_ Route CLI/REST/UI logging through shared adapters and document the contract.
  _Verification commands:_
  - `./gradlew --no-daemon :cli:test :rest-api:test :ui:test`
  - `rg -n "telemetry" docs/3-reference/rest-ocra-telemetry-snapshot.md`

- [x] T1410 – Track core module split sub-tasks (T1415–T1419) and aggregate status (S14-05).
  _Intent:_ Keep the plan/tasks synchronized while the module split proceeds.
  _Verification commands:_
  - `rg -n "T141" docs/4-architecture/features/014/plan.md`

- [x] T1411 – Sync AGENTS/how-to docs, run `spotlessApply` + `qualityGate`, capture metrics (S14-01–S14-06).
  _Intent:_ Confirm all guardrails and docs reflect the harmonized architecture.
  _Verification commands:_
  - `./gradlew --no-daemon spotlessApply check`
  - `./gradlew --no-daemon qualityGate`

- [x] T1412 – Add failing tests covering negative branches in `OcraReplayVerifier` et al. (S14-01, S14-04).
  _Intent:_ Preserve branch coverage while consolidating orchestration.
  _Verification commands:_
  - `./gradlew --no-daemon :application:test --tests "*OcraReplayVerifierTest"`

- [x] T1413 – Add failing REST `OcraVerificationService` tests for validation/error branches (S14-01, S14-04).
  _Intent:_ Capture validation, unexpected error, and unexpected state paths.
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test --tests "*OcraVerificationServiceTest"`

- [x] T1414 – Implement the new REST tests and rerun targeted builds (S14-01, S14-04, S14-06).
  _Intent:_ Drive the validation/error branches green and record the quality gate outcome.
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test`
  - `./gradlew --no-daemon qualityGate`

- [x] T1415 – Add failing ArchUnit guard for `core-shared`/`core-ocra` boundaries (S14-05).
  _Intent:_ Prevent facades from depending on protocol internals once modules split.
  _Verification commands:_
  - `./gradlew --no-daemon :core-architecture-tests:test --tests "*CoreModuleSplitArchitectureTest"`

- [x] T1416 – Scaffold `core-shared` module and migrate model/support packages (S14-05).
  _Intent:_ Establish the new module and move generic primitives under red tests.
  _Verification commands:_
  - `./gradlew --no-daemon :core-shared:test`

- [x] T1417 – Relocate serialization/encryption primitives to `core-shared` with injection seams (S14-05).
  _Intent:_ Keep migrations extensible without backsliding into direct core access.
  _Verification commands:_
  - `./gradlew --no-daemon :core-shared:test :infra-persistence:test`

- [x] T1418 – Create `core-ocra`, move OCRA descriptors/registry/migrations, and update wiring (S14-05).
  _Intent:_ Isolate protocol-specific code while keeping facades routed through application services.
  _Verification commands:_
  - `./gradlew --no-daemon :core-ocra:test`
  - `./gradlew --no-daemon :application:test`

- [x] T1419 – Refresh root Gradle config, coverage, and ArchUnit suites after the split (S14-05, S14-06).
  _Intent:_ Ensure PIT/Jacoco/quality gates include the new modules.
  _Verification commands:_
  - `./gradlew --no-daemon architectureTest`
  - `./gradlew --no-daemon qualityGate`

## Planning Notes
### T1408/T1409 – Telemetry Contract (2025-10-02)
- `TelemetryContractArchitectureTest` enforces CLI/REST/UI adapter usage; keep it red until adapters land.
- `TelemetryContractTestSupport` owns the shared fixtures for success/validation/error frames; facades should import helpers instead of bespoke assertions.
- Update `docs/3-reference/rest-ocra-telemetry-snapshot.md` once adapters emit the unified schema.

### T1410 – Module Split (2025-10-02)
- Move `io.openauth.sim.core.model`, `support`, and non-OCRA persistence primitives into `core-shared`; isolate protocol-specific descriptors/migrations in `core-ocra`.
- Extend `MapDbCredentialStore.Builder` to accept injected `VersionedCredentialRecordMigration` lists so migrations ship from `core-ocra`.
- New ArchUnit guard fails until only `application`/`infra-persistence` depend on `core-ocra`; facades must stay indirect via shared services.
- Persistence wiring now uses `OcraStoreMigrations.apply(...)` so CLI/REST/UI register legacy migrations explicitly.
