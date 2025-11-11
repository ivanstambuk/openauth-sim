# Feature 018 Tasks – OCRA Migration Retirement

_Status: Complete_  
_Last updated: 2025-11-10_

> Tasks remained ≤30 minutes each and followed the test-first cadence before implementation.

## Checklist
- [x] T-018-01 – Stage failing `core-ocra` tests that assume schema-v1 baseline (FR-018-01, S-018-01).  
  _Intent:_ Ensure `OcraStoreMigrationsTest` and related coverage assert the migration-less contract before code removal.  
  _Verification commands:_  
  - `./gradlew --no-daemon :core-ocra:test` (expected red prior to I2).  
  _Notes:_ Locked the expectation that schema-v0 helpers no longer exist.

- [x] T-018-02 – Remove migration classes, keep `OcraStoreMigrations.apply`, and confirm façades still call the seam (FR-018-01, FR-018-02, S-018-01, S-018-02).  
  _Intent:_ Delete `OcraRecordSchemaV0ToV1Migration`, update factories/CLI/REST wiring, and run module tests.  
  _Verification commands:_  
  - `./gradlew --no-daemon :core-ocra:test`  
  - `./gradlew --no-daemon :application:test`  
  - `./gradlew --no-daemon :rest-api:test`

- [x] T-018-03 – Refresh documentation/knowledge map/roadmap to state schema-v1 baseline; rerun spotless/check (FR-018-03, NFR-018-01, NFR-018-02, S-018-03).  
  _Intent:_ Communicate the removal and validate the full gate.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`

## Verification Log
- 2025-10-03 – `./gradlew --no-daemon :core-ocra:test :application:test :rest-api:test spotlessApply check`
- 2025-11-10 – `./gradlew --no-daemon spotlessApply check` (doc/template migration verification)

## Notes / TODOs
- None; future schema upgrades will be managed under new feature IDs.
