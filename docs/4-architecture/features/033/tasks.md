# Feature 033 Tasks – Operator Console Naming Alignment

_Linked plan:_ `docs/4-architecture/features/033/plan.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

> Checklist captures the five increments used to remove the legacy `Ocra*` naming.

## Checklist
- [x] T-033-01 – Planning artefacts & clarification closure (FR-033-04, S-033-04).  
  _Intent:_ Create spec/plan/tasks, update `_current-session.md`, mark Option B resolved.  
  _Verification:_ Documentation updates (2025-10-21).

- [x] T-033-02 – Controller/bean rename (FR-033-01, S-033-01).  
  _Intent:_ Rename `OcraOperatorUiController` and related beans to `OperatorConsole*`; update imports/tests.  
  _Verification:_ `./gradlew --no-daemon :rest-api:test` (2025-10-21).

- [x] T-033-03 – Telemetry endpoint/logger alignment (FR-033-02, S-033-02).  
  _Intent:_ Rename `/ui/console/replay/telemetry`, update telemetry logger/event keys, and refresh MockMvc/Selenium assertions.  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*OperatorUi*"` (2025-10-21).

- [x] T-033-04 – Template/JS/Selenium updates (FR-033-03, S-033-03).  
  _Intent:_ Update Thymeleaf templates + JS assets plus Selenium selectors to reference the new naming.  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.*"` (2025-10-21).

- [x] T-033-05 – Documentation & verification (FR-033-04, S-033-04).  
  _Intent:_ Refresh knowledge map, session snapshot, roadmap, and rerun the analysis gate.  
  _Verification:_ `./gradlew --no-daemon :rest-api:test`, `./gradlew --no-daemon spotlessApply check` (2025-10-21).

## Verification Log
- 2025-10-21 – `./gradlew --no-daemon :rest-api:test`
- 2025-10-21 – `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.*"`
- 2025-10-21 – `./gradlew --no-daemon spotlessApply check`

## Notes / TODOs
- None; operator console artefacts now use neutral naming.
