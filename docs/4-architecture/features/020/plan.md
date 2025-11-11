# Feature Plan 020 – Operator UI Multi-Protocol Tabs

_Linked specification:_ `docs/4-architecture/features/020/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
Expose HOTP, TOTP, and EUDI wallet placeholder tabs inside `/ui/console` so operators see the upcoming protocols while
keeping routing, accessibility, and dark-theme styling consistent. Roadmap/knowledge map entries must reflect the new
workstreams. Success indicators:
- Tab order + placeholder panels verified via Selenium/DOM tests (FR-020-01/02, S-020-01).
- Query-parameter routing/history extended for new protocol keys (FR-020-03, S-020-02).
- Roadmap + knowledge map updated (FR-020-04, S-020-03).
- `./gradlew :rest-api:test` and `./gradlew spotlessApply check` pass after updates.

## Scope Alignment
- **In scope:** UI tab ordering, placeholder content, JS router updates, doc updates, roadmap adjustments.
- **Out of scope:** Implementing functional HOTP/TOTP/EUDI flows or backend endpoints.

## Dependencies & Interfaces
- `rest-api` module’s Thymeleaf template + JS router controlling `/ui/console`.
- Selenium/DOM tests that validate tab behaviour.
- `docs/4-architecture/roadmap.md` and `docs/4-architecture/knowledge-map.md`.

## Assumptions & Risks
- **Assumptions:** Placeholder content suffices until protocol specs land; query-parameter schema remains backward
  compatible.
- **Risks/Mitigations:**
  - Tab ordering regressions → enforce with automated tests.
  - Documentation drift → run spotless/Doc review after updates.

## Implementation Drift Gate
- Map FR/NFR IDs to increments/tasks (see Scenario Tracking).
- Capture Selenium screenshots or DOM dumps verifying placeholder panels.
- Rerun `./gradlew :rest-api:test` + `./gradlew spotlessApply check` after UI/doc updates.

## Increment Map
1. **I1 – Tests first (S-020-01, FR-020-01/02)**
   - _Goal:_ Extend Selenium/DOM tests to expect new tabs + placeholders.
   - _Steps:_
     - Update `OperatorConsoleUnificationSeleniumTest` (or equivalent) to assert order and placeholder copy.
     - Add router tests for new protocol keys.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test` (expected red).
   - _Exit:_ Tests fail due to missing UI updates.

2. **I2 – UI/JS implementation (S-020-01, S-020-02, FR-020-01..03)**
   - _Goal:_ Render the new tabs and extend routing/history.
   - _Steps:_
     - Update Thymeleaf template and JS router with new tab definitions.
     - Ensure placeholder copy/styling matches existing placeholders.
     - Rerun Selenium + router tests.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test`, `./gradlew --no-daemon spotlessApply check`.
   - _Exit:_ Tests green; UI exhibits correct order/placeholders.

3. **I3 – Documentation & roadmap updates (S-020-03, FR-020-04)**
   - _Goal:_ Reflect the new workstreams across docs.
   - _Steps:_
     - Update roadmap to list HOTP, TOTP, and EUDI wallet features (`Not started`).
     - Refresh knowledge map session + migration tracker.
     - Rerun `./gradlew --no-daemon spotlessApply check`.
   - _Exit:_ Docs consistent; build green.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-020-01 | I1/I2 → T-020-01, T-020-02 | Tab ordering & placeholders. |
| S-020-02 | I2 → T-020-02 | Routing/history for protocol keys. |
| S-020-03 | I3 → T-020-03 | Roadmap/knowledge map updates.

## Analysis Gate
Completed 2025-10-04 with clarifications logged; rerun if scope changes.

## Exit Criteria
- New tabs + placeholders render correctly.
- Query-parameter routing handles new keys.
- Docs updated.
- `./gradlew :rest-api:test` + `./gradlew spotlessApply check` green after final change.

## Follow-ups / Backlog
- Ship functional HOTP/TOTP/EUDI simulator flows via future feature IDs once specs are ready.
