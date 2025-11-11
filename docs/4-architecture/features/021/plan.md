# Feature Plan 021 – Protocol Info Surface

_Linked specification:_ `docs/4-architecture/features/021/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
Deliver an accessible protocol info drawer/modal plus embeddable assets so operators and other applications can surface
per-protocol tips inline. Success looks like:
- Trigger + drawer/modal UX wired into `/ui/console`, covered by Selenium tests.
- Schema-driven JS module exposing persistence + CustomEvents, verified via unit tests.
- Embeddable CSS/JS, demo page, and README describing vanilla integration.
- Documentation (roadmap/knowledge map) updated, Gradle gate green.

## Scope Alignment
- **In scope:** UI trigger/drawer/modal, schema parser, persistence, CustomEvents, embeddable bundles, README/demo,
  roadmap/knowledge-map updates.
- **Out of scope:** Backend API additions, analytics pipelines, React-specific wrappers.

## Dependencies & Interfaces
- `/ui/console` Thymeleaf template + JS router.
- Selenium/UI tests under `rest-api` module.
- Embeddable assets located under `rest-api/src/main/resources/static/ui/protocol-info/`.
- Documentation assets (README, roadmap, knowledge map).

## Assumptions & Risks
- **Assumptions:** Feature 020 tab ordering is stable; localStorage is available (fallback to in-memory if blocked).
- **Risks:**
  - Drawer may break accessibility if focus trap fails → maintain dedicated Selenium coverage.
  - Persistence/localStorage may throw in private browser contexts → guard with try/catch and log warnings.

## Implementation Drift Gate
- Map each Scenario ID to increments/tasks below.
- Collect screenshots or DOM dumps verifying drawer/modal states.
- Rerun `./gradlew --no-daemon :rest-api:test` and `./gradlew --no-daemon spotlessApply check` before closing the feature.
- Update roadmap/knowledge map references and confirm CustomEvent names appear in documentation.

## Increment Map
1. **I1 – Test scaffolding (S-021-01/02)**
   - _Goal:_ Add failing Selenium + JS tests for trigger/drawer schema behaviours.
   - _Preconditions:_ Spec clarified; tab ordering available.
   - _Steps:_
     - Extend Selenium tests for trigger aria attributes, keyboard shortcuts, tab switching.
     - Add JS unit tests for schema parsing/persistence/CustomEvents.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test` (expected red).
   - _Exit:_ Tests fail until implementation lands.

2. **I2 – Drawer/modal implementation (S-021-01/02)**
   - _Steps:_ Implement trigger, drawer, schema parser, persistence, CustomEvents; rerun tests.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test`, `./gradlew --no-daemon spotlessApply check`.
   - _Exit:_ Drawer operational with new tests green.

3. **I3 – Accessibility + modal refinements (S-021-03)**
   - _Steps:_ Add modal focus trap, reduced-motion handling, ESC shortcuts, aria-hidden toggles; extend Selenium coverage.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test`.
   - _Exit:_ Accessibility/regression tests green.

4. **I4 – Embeddable assets & docs (S-021-04/05)**
   - _Steps:_ Produce CSS/JS bundles, demo HTML, README/integration guide, roadmap/knowledge map updates.
   - _Commands:_ `./gradlew --no-daemon spotlessApply check`.
   - _Exit:_ Docs synced, Gradle gate green.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-021-01 | I1/I2 – T-021-01, T-021-02 | Trigger + drawer scaffolding. |
| S-021-02 | I1/I2 – T-021-02, T-021-03 | Schema, persistence, CustomEvents. |
| S-021-03 | I3 – T-021-04 | Modal accessibility. |
| S-021-04 | I4 – T-021-05 | Embeddable assets + integration docs. |
| S-021-05 | I4 – T-021-06 | Documentation + Gradle gate updates.

## Analysis Gate
Completed 2025-10-04 (see spec) – no open questions remain; re-run only if scope changes.

## Exit Criteria
- Selenium + JS tests covering drawer/modal/persistence/CustomEvents green.
- Embeddable assets/demo/README merged.
- Roadmap/knowledge map mention the Protocol Info surface.
- `./gradlew --no-daemon :rest-api:test` and `./gradlew --no-daemon spotlessApply check` run successfully after final edits.

## Follow-ups / Backlog
- When HOTP/TOTP/EUDI protocols ship full flows, update schema content accordingly.
