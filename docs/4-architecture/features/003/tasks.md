# Feature 003 Tasks – OCRA Simulator & Replay

_Status: Complete_  
_Last updated: 2025-11-11_

> Keep this checklist aligned with the feature plan increments. Stage tests before implementation, record verification commands beside each task, and prefer bite-sized entries (≤90 minutes).
> When referencing requirements, keep feature IDs (`F-`), non-goal IDs (`N-`), and scenario IDs (`S-<NNN>-`) inside the same parentheses immediately after the task title (omit categories that do not apply).

## Checklist
- [x] T-003-01 – Stage failing RFC 6287/S064/S512 descriptor + calculator tests (S-003-01, FR-003-01/02).  
  _Verification:_ `./gradlew --no-daemon :core:test`
- [x] T-003-02 – Implement descriptor canonicalisation + `OcraResponseCalculator` helpers (S-003-01, FR-003-01/02).  
  _Verification:_ `./gradlew --no-daemon :core:test`, ArchUnit + mutation suites.
- [x] T-003-03 – Add failing persistence envelope upgrade tests (schema-v1 only) (S-003-01, FR-003-03).  
  _Verification:_ `./gradlew --no-daemon :application:test --tests "*CredentialStore*"`
- [x] T-003-04 – Implement persistence upgrades + telemetry logging (S-003-01, FR-003-03).  
  _Verification:_ `./gradlew --no-daemon :application:test`
- [x] T-003-05 – Remove schema-v0 migration helpers; document schema-v1 requirement (S-003-05, FR-003-08/09).  
  _Verification:_ `./gradlew --no-daemon :application:test :core:test`, doc lint.
- [x] T-003-06 – Add failing MockMvc/OpenAPI tests for `/api/v1/ocra/evaluate` (inline payloads) (S-003-02, FR-003-04).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*OcraEvaluate*"`
- [x] T-003-07 – Implement inline REST evaluation controller + telemetry + OpenAPI snapshots (S-003-02, FR-003-04).  
  _Verification:_ `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "*OpenApiSnapshotTest"`
- [x] T-003-08 – Add failing tests for stored credential resolution + exclusivity (S-003-02, FR-003-05).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*OcraStored*"`
- [x] T-003-09 – Implement stored credential resolution + reason codes, update docs (S-003-02, FR-003-05).  
  _Verification:_ `./gradlew --no-daemon :application:test :rest-api:test`
- [x] T-003-10 – Add failing CLI replay + REST `/ocra/verify` tests with hashed telemetry assertions (S-003-03, FR-003-06).  
  _Verification:_ `./gradlew --no-daemon :cli:test --tests "*OcraVerify*"`, `./gradlew --no-daemon :rest-api:test --tests "*OcraVerify*"`
- [x] T-003-11 – Implement CLI/REST replay logic, telemetry hashing, and benchmark harness (S-003-03, FR-003-06).  
  _Verification:_ `IO_OPENAUTH_SIM_BENCHMARK=true ./gradlew :core:test --tests "*OcraReplayBenchmark*"`
- [x] T-003-12 – Add failing UI unit/Selenium tests for OCRA stored/inline evaluation tabs (S-003-04, FR-003-07).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*OcraOperatorUiEvaluate*"`
- [x] T-003-13 – Implement evaluation tab presets, timestamp toggles, verbose trace wiring (S-003-04, FR-003-07).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test :ui:test`
- [x] T-003-14 – Add failing UI/Selenium coverage for replay workspace + spacing/a11y checks (S-003-04, FR-003-07).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*OcraOperatorUiReplay*"`
- [x] T-003-15 – Implement replay workspace, hashed telemetry surfaces, and docs (S-003-04/05, FR-003-06/07/09).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test`, doc lint.
- [x] T-003-16 – Update roadmap/knowledge map/how-to guides to reference Feature 003 + new telemetry events (S-003-05, FR-003-09).  
  _Verification:_ `./gradlew --no-daemon spotlessApply check`

## Verification Log
- 2025-09-30 – `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "*OcraEvaluateOpenApi*"`
- 2025-10-03 – `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test`
- 2025-10-07 – `IO_OPENAUTH_SIM_BENCHMARK=true ./gradlew :core:test --tests "*OcraReplayBenchmark*"`
- 2025-10-15 – Selenium + UI regression suite (`OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "*OcraOperatorUi*"`)
- 2025-11-10 – `./gradlew --no-daemon spotlessApply check` (template migration)

## Notes / TODOs
- Verbose trace enhancements move to Feature 035/040; the `legacy/` subdirectory preserves prior specs for auditors.
