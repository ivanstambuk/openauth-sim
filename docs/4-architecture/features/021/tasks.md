# Feature 021 Tasks – Protocol Info Surface

_Status: Complete_  
_Last updated: 2025-11-10_

> Tasks remained ≤30 minutes and staged validation before implementation. Keep the verification commands handy for future changes.

## Checklist
- [x] T-021-01 – Add failing Selenium coverage for the protocol info trigger (aria) and drawer toggles (S-021-01).  
  _Intent:_ Lock in trigger placement, aria wiring, keyboard shortcuts, and per-protocol switching before coding.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test`

- [x] T-021-02 – Write JS unit tests for schema parsing, escaping, persistence, and CustomEvents (S-021-02).  
  _Intent:_ Ensure ProtocolInfo module exposes the required API and guards against malformed data.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*ProtocolInfo*Test"`

- [x] T-021-03 – Implement trigger/drawer/modal scaffolding plus schema-driven rendering (S-021-01, S-021-02).  
  _Intent:_ Make the drawer functional with JSON data, persistence, and CustomEvents.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test`  
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-021-04 – Add modal focus trap, reduced-motion handling, and accessibility refinements (S-021-03).  
  _Intent:_ Harden the modal experience without impacting existing workflows.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test`

- [x] T-021-05 – Ship embeddable CSS/JS bundles, standalone demo, and vanilla DOM integration guide (S-021-04).  
  _Intent:_ Allow other applications to reuse the Protocol Info surface.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-021-06 – Update roadmap/knowledge map/session log and run final Gradle gate (S-021-05).  
  _Intent:_ Document the feature, ensure governance artefacts stay synchronized, and close the workstream.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`

## Verification Log
- 2025-10-04 – `./gradlew --no-daemon :rest-api:test`, `./gradlew --no-daemon spotlessApply check`
- 2025-11-10 – `./gradlew --no-daemon spotlessApply check` (template migration verification)

## Notes / TODOs
- None.
