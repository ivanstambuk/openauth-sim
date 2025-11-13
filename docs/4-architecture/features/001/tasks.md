# Feature 001 Tasks – HOTP Simulator & Tooling

_Status:_ Complete  
_Last updated:_ 2025-11-13

> Keep this checklist aligned with the feature plan increments. Stage tests before implementation, record verification commands beside each task, and prefer bite-sized entries (≤90 minutes).
> When referencing requirements, keep feature IDs (`F-`), non-goal IDs (`N-`), and scenario IDs (`S-<NNN>-`) inside the same parentheses immediately after the task title (omit categories that do not apply).

## Checklist
- [x] T-001-01 – Add failing HOTP generator/validator tests with RFC 4226 vectors (S-001-01, FR-001-01).  
  _Verification:_ `./gradlew --no-daemon :core:test`
- [x] T-001-02 – Implement HOTP domain logic + fixture loader to satisfy T-001-01 (S-001-01, FR-001-01).  
  _Verification:_ `./gradlew --no-daemon :core:test`
- [x] T-001-03 – Add failing MapDB integration tests mixing OCRA + HOTP credentials (S-001-01, FR-001-02).  
  _Verification:_ `./gradlew --no-daemon :application:test --tests "*CredentialStore*"`
- [x] T-001-04 – Implement shared persistence updates and rerun integration tests (S-001-01, FR-001-02).  
  _Verification:_ `./gradlew --no-daemon :application:test`
- [x] T-001-05 – Add failing application telemetry tests for HOTP evaluation/issuance/replay (S-001-02, FR-001-03/04).  
  _Verification:_ `./gradlew --no-daemon :application:test --tests "*Hotp*Telemetry*"`
- [x] T-001-06 – Implement application services/telemetry adapters (S-001-02, FR-001-03/04).  
  _Verification:_ `./gradlew --no-daemon :application:test`
- [x] T-001-07 – Add failing CLI command tests (import/list/evaluate/replay) (S-001-02, FR-001-03).  
  _Verification:_ `./gradlew --no-daemon :cli:test --tests "*Hotp*CommandTest"`
- [x] T-001-08 – Implement CLI commands + telemetry (S-001-02, FR-001-03).  
  _Verification:_ `./gradlew --no-daemon :cli:test`
- [x] T-001-09 – Add failing REST MockMvc + OpenAPI tests for HOTP evaluation (S-001-03, FR-001-04).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*HotpEvaluate*"`
- [x] T-001-10 – Implement REST evaluation endpoint, update OpenAPI snapshots (S-001-03, FR-001-04).  
  _Verification:_ `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "*OpenApiSnapshotTest"`
- [x] T-001-11 – Add failing REST/Selenium coverage for HOTP replay endpoints (stored + inline) (S-001-03, FR-001-04).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*HotpReplay*"`
- [x] T-001-12 – Implement HOTP replay service/controller/UI wiring (S-001-03/04, FR-001-04/05).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test`
- [x] T-001-13 – Add failing Selenium tests for HOTP stored evaluation UI (S-001-04, FR-001-05).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*HotpOperatorUiStored*"`
- [x] T-001-14 – Implement HOTP stored evaluation UI + seeding controls (S-001-04, FR-001-05).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test`
- [x] T-001-15 – Add failing Selenium tests for HOTP inline evaluation + accessibility (S-001-04, FR-001-05).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*HotpOperatorUiInline*"`
- [x] T-001-16 – Implement HOTP inline evaluation UI (S-001-04, FR-001-05).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test`
- [x] T-001-17 – Add failing Selenium coverage for HOTP replay UI enhancements (sample hints, automatic context) (S-001-04).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*HotpOperatorUiReplay*"`
- [x] T-001-18 – Implement HOTP replay UI tweaks (auto-fill counter/OTP, remove sample button) (S-001-04).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test`
- [x] T-001-19 – Publish `docs/hotp_validation_vectors.json`, add loader/tests across modules (S-001-05, FR-001-06).  
  _Verification:_ `./gradlew --no-daemon :core:test :cli:test :rest-api:test`
- [x] T-001-20 – Update CLI/REST/operator docs with HOTP guidance, rerun spotless (S-001-05, FR-001-07).  
  _Verification:_ `./gradlew --no-daemon spotlessApply check`

## Verification Log
- 2025-10-05 – `./gradlew --no-daemon :core:test :application:test :cli:test :rest-api:test :ui:test spotlessApply check`
- 2025-10-13 – Fixture catalogue + UI updates verified with `./gradlew --no-daemon :rest-api:test --tests "*Hotp*"` and `./gradlew spotlessApply check`
- 2025-11-10 – `./gradlew --no-daemon spotlessApply check` (template migration verification)

## Notes / TODOs
- HOTP issuance/provisioning will be handled under a future feature once prioritized.
