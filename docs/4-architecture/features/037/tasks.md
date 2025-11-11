# Feature 037 Tasks - Base32 Inline Secret Support

_Linked plan:_ `docs/4-architecture/features/037/plan.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-11

> Keep this checklist aligned with the Increment Map. Stage tests before implementation, record verification commands beside each task, and keep entries <=90 minutes.

## Checklist
- [x] T-037-01 - Base32 helper implementation (FR-037-01, NFR-037-02, S-037-01).  
  _Intent:_ Build `SecretEncodings` to normalise Base32 input, convert to uppercase hex, and emit masking hints.  
  _Verification commands:_  
  - 2025-10-31 - `./gradlew --no-daemon :core:test`  
  - 2025-10-31 - `./gradlew --no-daemon spotlessApply check`

- [x] T-037-02 - REST DTOs/services accept Base32 (FR-037-02, NFR-037-01, S-037-02).  
  _Intent:_ Add `sharedSecretBase32` fields, enforce exclusivity, refresh OpenAPI snapshot, and extend HOTP/TOTP/OCRA REST tests.  
  _Verification commands:_  
  - 2025-10-31 - `./gradlew --no-daemon :rest-api:test`  
  - 2025-10-31 - `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`

- [x] T-037-03 - CLI Base32 flags (FR-037-03, NFR-037-02, S-037-03).  
  _Intent:_ Add mutually exclusive `--shared-secret-base32` options to HOTP/TOTP/OCRA commands and reuse the helper before dispatching services.  
  _Verification commands:_  
  - 2025-10-31 - `./gradlew --no-daemon :cli:test`

- [x] T-037-04 - Operator UI textarea + toggle (FR-037-04, NFR-037-03, S-037-04).  
  _Intent:_ Replace dual textareas with a single shared input, wire Hex/Base32 toggle, dynamic hint/error messaging, and extend Selenium coverage.  
  _Verification commands:_  
  - 2025-10-31 - `./gradlew --no-daemon :ui:test :rest-api:test`  
  - 2025-11-01 - `./gradlew --no-daemon :rest-api:test :ui:test`

- [x] T-037-05 - Documentation and governance sync (FR-037-05, S-037-05).  
  _Intent:_ Update how to guides, knowledge map, `_current-session.md`, migration plan, and run the full Gradle quality gate.  
  _Verification commands:_  
  - 2025-10-31 - `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`

## Verification Log
- 2025-10-31 - `./gradlew --no-daemon :core:test`
- 2025-10-31 - `./gradlew --no-daemon :rest-api:test`
- 2025-10-31 - `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`
- 2025-10-31 - `./gradlew --no-daemon :cli:test`
- 2025-10-31 - `./gradlew --no-daemon :ui:test :rest-api:test`
- 2025-10-31 - `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`
- 2025-11-01 - `./gradlew --no-daemon :rest-api:test :ui:test`

## Notes / TODOs
- Monitor future inline secret UX work to ensure it reuses the shared textarea/toggle helper; no open TODOs remain for Feature 037.
