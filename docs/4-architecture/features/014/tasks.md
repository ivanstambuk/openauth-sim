# Feature 014 Tasks – Architecture Harmonization

_Status:_ Complete  
_Last updated:_ 2025-11-10_

> Checklist mirrors the Feature 014 plan increments; entries remain checked for audit history while migrating templates.

## Checklist
- [x] T-014-01 – Register Feature 014 in roadmap/knowledge map; confirm no open questions (S-014-06).  
  _Intent:_ Anchor the harmonization scope across governance docs before implementation.  
  _Verification commands:_  
  - `rg -n "Feature 014" docs/4-architecture/roadmap.md`  
  - `rg -n "Feature 014" docs/4-architecture/knowledge-map.md`

- [x] T-014-02 – Add failing ArchUnit tests asserting facade delegation to the application layer (FR-014-01, S-014-01).  
  _Intent:_ Prevent direct facade → domain coupling before shared services land.  
  _Verification commands:_  
  - `./gradlew --no-daemon :core-architecture-tests:test --tests "*ApplicationLayerArchitectureTest"`

- [x] T-014-03 – Implement shared application services and drive guards/tests green (FR-014-01, S-014-01, S-014-04).  
  _Intent:_ Move orchestration/validation logic into `application` and update CLI/REST/UI wiring.  
  _Verification commands:_  
  - `./gradlew --no-daemon :application:test`  
  - `./gradlew --no-daemon :cli:test :rest-api:test :ui:test`

- [x] T-014-04 – Add failing DTO normalization tests covering inline vs stored flows (FR-014-05, S-014-04).  
  _Intent:_ Capture expected behaviour before extracting shared DTOs.  
  _Verification commands:_  
  - `./gradlew --no-daemon :core-architecture-tests:test --tests "*DtoNormalizationTest"`

- [x] T-014-05 – Implement shared DTO/normalization helpers and migrate facades (FR-014-05, S-014-04).  
  _Intent:_ Ensure all facades share the same validation paths and error messaging.  
  _Verification commands:_  
  - `./gradlew --no-daemon :application:test`  
  - `./gradlew --no-daemon :cli:test :rest-api:test :ui:test`

- [x] T-014-06 – Add failing tests for `CredentialStoreFactory` provisioning (FR-014-02, S-014-02).  
  _Intent:_ Prove the factory design before wiring it into facades.  
  _Verification commands:_  
  - `./gradlew --no-daemon :infra-persistence:test --tests "*CredentialStoreFactory*"`

- [x] T-014-07 – Implement `CredentialStoreFactory` infrastructure and update facades/tests (FR-014-02, S-014-02).  
  _Intent:_ Centralize persistence wiring with swappable backend support.  
  _Verification commands:_  
  - `./gradlew --no-daemon :cli:test :rest-api:test`

- [x] T-014-08 – Add failing telemetry contract tests (FR-014-03, S-014-03).  
  _Intent:_ Establish red coverage for success/validation/failure frames before adopting adapters.  
  _Verification commands:_  
  - `./gradlew --no-daemon :application:test --tests "*TelemetryContractTestSupport*"`  
  - `./gradlew --no-daemon :core-architecture-tests:test --tests "*TelemetryContractArchitectureTest"`

- [x] T-014-09 – Implement telemetry adapters and refresh docs/snapshots (FR-014-03, S-014-03, S-014-06).  
  _Intent:_ Route CLI/REST/UI logging through shared adapters and align documentation.  
  _Verification commands:_  
  - `./gradlew --no-daemon :cli:test :rest-api:test :ui:test`  
  - `rg -n "telemetry" docs/3-reference/rest-ocra-telemetry-snapshot.md`

- [x] T-014-10 – Track core module split sub-tasks (T-014-15–T-014-19) and aggregate status (FR-014-04, S-014-05).  
  _Intent:_ Keep plan/tasks synchronized while splitting `core`.  
  _Verification commands:_  
  - `rg -n "T-014-1" docs/4-architecture/features/014/plan.md`

- [x] T-014-11 – Sync AGENTS/how-to docs, run `spotlessApply` + `qualityGate`, capture metrics (FR-014-01–FR-014-05, S-014-01–S-014-06).  
  _Intent:_ Confirm documentation and guardrails reflect the harmonized architecture.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`  
  - `./gradlew --no-daemon qualityGate`

- [x] T-014-12 – Add failing application-level tests for negative `OcraReplayVerifier`/DTO branches (FR-014-01, FR-014-05, S-014-01, S-014-04).  
  _Intent:_ Preserve branch coverage while consolidating orchestration.  
  _Verification commands:_  
  - `./gradlew --no-daemon :application:test --tests "*OcraReplay*"`

- [x] T-014-13 – Add failing REST service tests covering validation/error branches (FR-014-01, FR-014-05, S-014-04).  
  _Intent:_ Capture stored/inline validation plus unexpected errors before final implementation.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OcraVerification*"`

- [x] T-014-14 – Implement new REST tests and rerun targeted builds (FR-014-01, FR-014-05, S-014-04).  
  _Intent:_ Drive new tests green and record coverage metrics.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test`  
  - `./gradlew --no-daemon qualityGate`

- [x] T-014-15 – Add failing ArchUnit guard enforcing `core-shared`/`core-ocra` boundaries (FR-014-04, S-014-05).  
  _Intent:_ Prevent facades from depending on protocol internals.  
  _Verification commands:_  
  - `./gradlew --no-daemon :core-architecture-tests:test --tests "*CoreModuleSplitArchitectureTest"`

- [x] T-014-16 – Scaffold `core-shared` module and migrate shared packages (FR-014-04, S-014-05).  
  _Intent:_ Establish the new module before moving protocol-specific code.  
  _Verification commands:_  
  - `./gradlew --no-daemon :core-shared:test`

- [x] T-014-17 – Move serialization/encryption primitives to `core-shared` with injected migrations (FR-014-04, S-014-05).  
  _Intent:_ Keep shared functionality available without bloating `core-ocra`.  
  _Verification commands:_  
  - `./gradlew --no-daemon :core-shared:test :infra-persistence:test`

- [x] T-014-18 – Create `core-ocra`, move descriptors/registry/migrations, update wiring (FR-014-04, S-014-05).  
  _Intent:_ Isolate protocol-specific code while keeping facades indirect.  
  _Verification commands:_  
  - `./gradlew --no-daemon :core-ocra:test`  
  - `./gradlew --no-daemon :application:test`

- [x] T-014-19 – Refresh Gradle/PIT/Jacoco configs and drive quality gate green post-split (FR-014-04, NFR-014-01, S-014-05).  
  _Intent:_ Ensure automation includes the new modules.  
  _Verification commands:_  
  - `./gradlew --no-daemon architectureTest`  
  - `./gradlew --no-daemon qualityGate`

## Verification Log (Optional)
- 2025-10-02 – `./gradlew --no-daemon qualityGate` (PASS; runtime remained within ±10% after module split).
- 2025-10-02 – `./gradlew --no-daemon :cli:test :rest-api:test :ui:test :application:test` (PASS; shared services + telemetry adapters).
- 2025-10-02 – `./gradlew --no-daemon :core-architecture-tests:test --tests "*ApplicationLayer*" "*CoreModuleSplit*"` (PASS; guards enforced).

## Notes / TODOs
- Future protocol features should reuse the shared application/persistence/telemetry patterns defined here.
