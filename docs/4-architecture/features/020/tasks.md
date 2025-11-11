# Feature 020 Tasks – Operator UI Multi-Protocol Tabs

_Status: Complete_  
_Last updated: 2025-11-10_

> Tasks followed the ≤30-minute rule with tests staged before implementation.

## Checklist
- [x] T-020-01 – Add failing Selenium/DOM/router assertions for tab ordering and placeholders (FR-020-01, FR-020-02, S-020-01).  
  _Intent:_ Guard the required tab order + placeholder copy before touching templates.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test`

- [x] T-020-02 – Implement HOTP/TOTP/EUDI tab skeleton + routing keys, update JS router, rerun tests (FR-020-01..03, S-020-01, S-020-02).  
  _Intent:_ Render new tabs, ensure query parameters/history recognise protocol keys.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test`  
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-020-03 – Update roadmap/knowledge map/migration tracker with discrete HOTP/TOTP/EUDI workstreams (FR-020-04, S-020-03).  
  _Intent:_ Reflect placeholder scope in governance docs.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`

## Verification Log
- 2025-10-04 – `./gradlew --no-daemon :rest-api:test` (red after T-020-01, green after T-020-02)
- 2025-10-04 – `./gradlew --no-daemon spotlessApply check` (green after doc updates)
- 2025-11-10 – `./gradlew --no-daemon spotlessApply check` (template migration verification)

## Notes / TODOs
- None.
