# Feature 023 Tasks – TOTP Operator Support

_Status: Complete_  
_Last updated: 2025-11-10_

> Tasks reference the refreshed template with scenario IDs and verification commands for historical traceability.

## Checklist
- [x] T-023-01 – Stage failing core TOTP generator/validator tests (SHA-1/256/512, digits, steps, drift) (S-023-01, FR-023-01).  
  _Verification:_ `./gradlew --no-daemon :core:test`
- [x] T-023-02 – Implement TOTP domain logic + fixture loader (S-023-01, FR-023-01).  
  _Verification:_ `./gradlew --no-daemon :core:test`
- [x] T-023-03 – Add failing persistence integration tests mixing HOTP/OCRA/TOTP descriptors (S-023-01, FR-023-02).  
  _Verification:_ `./gradlew --no-daemon :infra-persistence:test --tests "*TotpIntegrationTest"`
- [x] T-023-04 – Implement persistence descriptors/defaults; rerun targeted tests (S-023-01, FR-023-02).  
  _Verification:_ `./gradlew --no-daemon :infra-persistence:test`
- [x] T-023-05 – Stage failing application telemetry tests covering stored/inline evaluation/replay (S-023-02, FR-023-03).  
  _Verification:_ `./gradlew --no-daemon :application:test --tests "*TotpEvaluationApplicationServiceTest"`
- [x] T-023-06 – Implement application services/telemetry adapters (S-023-02, FR-023-03).  
  _Verification:_ `./gradlew --no-daemon :application:test`
- [x] T-023-07 – Stage failing CLI tests for import/list/evaluate/replay (drift/timestamp overrides) (S-023-02, FR-023-04).  
  _Verification:_ `./gradlew --no-daemon :cli:test --tests "*TotpCliTest"`
- [x] T-023-08 – Implement CLI commands, rerun suite (S-023-02, FR-023-04).  
  _Verification:_ `./gradlew --no-daemon :cli:test`
- [x] T-023-09 – Stage failing REST MockMvc/OpenAPI tests for evaluation/replay (S-023-03, FR-023-05).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*Totp*EndpointTest"`
- [x] T-023-10 – Implement REST controllers/services, regenerate OpenAPI snapshots (S-023-03, FR-023-05).  
  _Verification:_ `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "*OpenApiSnapshotTest"`
- [x] T-023-11 – Stage failing Selenium coverage for TOTP stored/inline evaluate panels (S-023-04, FR-023-06).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*TotpOperatorUiSeleniumTest"`
- [x] T-023-12 – Implement TOTP stored/inline evaluate UI, including inline default (S-023-04, FR-023-06).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test`
- [x] T-023-13 – Stage failing Selenium coverage for TOTP replay (stored/inline) including auto-applied samples (S-023-04, FR-023-06).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*TotpOperatorUiSeleniumTest*Replay*"`
- [x] T-023-14 – Implement TOTP replay UI updates (auto-fill, remove legacy sample button) (S-023-04, FR-023-06).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test`
- [x] T-023-15 – Stage failing Selenium coverage for timestamp toggles (“Use current Unix seconds”, “Reset to now”) (S-023-04).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*TotpOperatorUiSeleniumTest*TimestampControls"`
- [x] T-023-16 – Implement timestamp toggle UI, quantised reset helpers (S-023-04).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test`, `./gradlew --no-daemon spotlessApply check`
- [x] T-023-17 – Publish `docs/totp_validation_vectors.json`, add shared loader/tests across modules (S-023-05, FR-023-07).  
  _Verification:_ `./gradlew --no-daemon :core:test :cli:test :rest-api:test`
- [x] T-023-18 – Update operator/CLI/REST how-to guides, roadmap, knowledge map; rerun spotless (S-023-05, FR-023-07).  
  _Verification:_ `./gradlew --no-daemon spotlessApply check`

## Verification Log
- 2025-10-18 – `./gradlew --no-daemon :core:test :application:test :cli:test :rest-api:test :ui:test spotlessApply check`
- 2025-11-10 – `./gradlew --no-daemon spotlessApply check` (template migration verification)

## Notes / TODOs
- TOTP issuance/enrollment deferred to future feature.
