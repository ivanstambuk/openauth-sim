# Feature 036 Tasks - Verbose Trace Tier Controls

_Linked plan:_ `docs/4-architecture/features/036/plan.md`  
_Status:_ Draft  
_Last updated:_ 2025-11-11

> Keep this checklist aligned with the Increment Map. Stage tests before implementation, record verification commands beside each task, and keep entries <=90 minutes.

## Checklist
- [ ] T-036-01 - Tier helper specification tests (FR-036-01, NFR-036-01, S-036-01).  
  _Intent:_ Define `TraceTier`/`TraceTierFilter` behaviour via failing unit tests covering tier comparison, validation, and attribute tagging contracts.  
  _Verification commands:_  
  - `./gradlew --no-daemon :core:test --tests "*TraceTier*"` (expected red)  
  - `./gradlew --no-daemon spotlessApply check`

- [ ] T-036-02 - Trace builder tagging (FR-036-02, NFR-036-02, S-036-02).  
  _Intent:_ Tag HOTP/TOTP/OCRA/FIDO2 verbose trace builders with minimum tiers, wire the helper, and update fixtures/OpenAPI snapshots.  
  _Verification commands:_  
  - `./gradlew --no-daemon :application:test :rest-api:test :cli:test`  
  - `./gradlew --no-daemon spotlessApply check`

- [ ] T-036-03 - Facade parity & snapshots (FR-036-03, S-036-03).  
  _Intent:_ Ensure REST query parameters, CLI flags, and UI configuration propagate tier selection; refresh snapshots and Selenium assertions where payloads change.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test :cli:test :ui:test`  
  - `./gradlew --no-daemon :rest-api:openapiSnapshot` (if applicable)

- [ ] T-036-04 - Telemetry & governance docs (FR-036-04, S-036-04).  
  _Intent:_ Emit `telemetry.trace.filtered` events, capture sample payloads, and update spec/plan/how-to/knowledge map entries describing governance + UX dependency.  
  _Verification commands:_  
  - `./gradlew --no-daemon :application:test`  
  - `./gradlew --no-daemon spotlessApply check`

- [ ] T-036-05 - Quality gate closure (FR-036-05, NFR-036-03, S-036-05).  
  _Intent:_ Rerun the full Gradle gate, refresh current-session/migration docs, and archive drift-gate evidence.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`  
  - `./gradlew --no-daemon :core:test :application:test :rest-api:test :cli:test :ui:test`

## Verification Log
- 2025-11-10 - `./gradlew --no-daemon spotlessApply check` (doc/template migration sweep)

## Notes / TODOs
- UI toggle feature remains out of scope; document dependency once UX workstream starts.
