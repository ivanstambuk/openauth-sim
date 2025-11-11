# Feature 022 Tasks – HOTP Operator Support

_Status: Complete_  
_Last updated: 2025-11-10_

> Tasks are recorded with verification commands for future reference; each originally stayed ≤30 minutes and staged tests first.

## Checklist
- [x] T-022-01 – Add failing HOTP generator/validator tests with RFC 4226 vectors (S-022-01, FR-022-01).  
  _Verification:_ `./gradlew --no-daemon :core:test`
- [x] T-022-02 – Implement HOTP domain logic + fixture loader to satisfy T-022-01 (S-022-01, FR-022-01).  
  _Verification:_ `./gradlew --no-daemon :core:test`
- [x] T-022-03 – Add failing MapDB integration tests mixing OCRA + HOTP credentials (S-022-01, FR-022-02).  
  _Verification:_ `./gradlew --no-daemon :application:test --tests "*CredentialStore*"`
- [x] T-022-04 – Implement shared persistence updates and rerun integration tests (S-022-01, FR-022-02).  
  _Verification:_ `./gradlew --no-daemon :application:test`
- [x] T-022-05 – Add failing application telemetry tests for HOTP evaluation/issuance/replay (S-022-02, FR-022-03/04).  
  _Verification:_ `./gradlew --no-daemon :application:test --tests "*Hotp*Telemetry*"`
- [x] T-022-06 – Implement application services/telemetry adapters (S-022-02, FR-022-03/04).  
  _Verification:_ `./gradlew --no-daemon :application:test`
- [x] T-022-07 – Add failing CLI command tests (import/list/evaluate/replay) (S-022-02, FR-022-03).  
  _Verification:_ `./gradlew --no-daemon :cli:test --tests "*Hotp*CommandTest"`
- [x] T-022-08 – Implement CLI commands + telemetry (S-022-02, FR-022-03).  
  _Verification:_ `./gradlew --no-daemon :cli:test`
- [x] T-022-09 – Add failing REST MockMvc + OpenAPI tests for HOTP evaluation (S-022-03, FR-022-04).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*HotpEvaluate*"`
- [x] T-022-10 – Implement REST evaluation endpoint, update OpenAPI snapshots (S-022-03, FR-022-04).  
  _Verification:_ `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "*OpenApiSnapshotTest"`
- [x] T-022-11 – Add failing REST/Selenium coverage for HOTP replay endpoints (stored + inline) (S-022-03, FR-022-04).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*HotpReplay*"`
- [x] T-022-12 – Implement HOTP replay service/controller/UI wiring (S-022-03/04, FR-022-04/05).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test`
- [x] T-022-13 – Add failing Selenium tests for HOTP stored evaluation UI (S-022-04, FR-022-05).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*HotpOperatorUiStored*"`
- [x] T-022-14 – Implement HOTP stored evaluation UI + seeding controls (S-022-04, FR-022-05).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test`
- [x] T-022-15 – Add failing Selenium tests for HOTP inline evaluation + accessibility (S-022-04, FR-022-05).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*HotpOperatorUiInline*"`
- [x] T-022-16 – Implement HOTP inline evaluation UI (S-022-04, FR-022-05).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test`
- [x] T-022-17 – Add failing Selenium coverage for HOTP replay UI enhancements (sample hints, automatic context) (S-022-04).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*HotpOperatorUiReplay*"`
- [x] T-022-18 – Implement HOTP replay UI tweaks (auto-fill counter/OTP, remove sample button) (S-022-04).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test`
- [x] T-022-19 – Publish `docs/hotp_validation_vectors.json`, add loader/tests across modules (S-022-05, FR-022-06).  
  _Verification:_ `./gradlew --no-daemon :core:test :cli:test :rest-api:test`
- [x] T-022-20 – Update CLI/REST/operator docs with HOTP guidance, rerun spotless (S-022-05, FR-022-07).  
  _Verification:_ `./gradlew --no-daemon spotlessApply check`

## Verification Log
- 2025-10-05 – `./gradlew --no-daemon :core:test :application:test :cli:test :rest-api:test :ui:test spotlessApply check`
- 2025-10-13 – Fixture catalogue + UI updates verified with `./gradlew --no-daemon :rest-api:test --tests "*Hotp*"` and `./gradlew spotlessApply check`
- 2025-11-10 – `./gradlew --no-daemon spotlessApply check` (template migration verification)

## Notes / TODOs
- HOTP issuance/provisioning will be handled under a future feature once prioritized.
