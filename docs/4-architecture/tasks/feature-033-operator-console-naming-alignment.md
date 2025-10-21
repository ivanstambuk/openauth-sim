# Feature 033 Tasks – Operator Console Naming Alignment

_Linked plan:_ `docs/4-architecture/feature-plan-033-operator-console-naming-alignment.md`  
_Status:_ Complete  
_Last updated:_ 2025-10-21

☑ **T3301 – Planning artefacts & clarification closure**  
  ☑ Add Feature 033 spec/plan/tasks, update `docs/_current-session.md`, and mark the open naming question as resolved with Option B.

☑ **T3302 – Controller/bean rename**  
  ☑ Rename `OcraOperatorUiController` → `OperatorConsoleController` (file/class) and adjust constructor wiring.  
  ☑ Update `OperatorConsoleUiConfiguration` bean and any imports/usages to the new naming.

☑ **T3303 – Telemetry endpoint & logger alignment**  
  ☑ Rename `/ui/ocra/replay/telemetry` endpoint to `/ui/console/replay/telemetry` and promote telemetry classes to `OperatorConsoleTelemetryLogger`.  
  ☑ Update telemetry event keys/fields accordingly and ensure MockMvc/unit coverage exercises the new name.

☑ **T3304 – Template & Selenium updates**  
  ☑ Refresh Thymeleaf templates/JS assets for the new telemetry endpoint and controller references.  
  ☑ Update Selenium/MockMvc tests to expect the renamed attributes and endpoints.

☑ **T3305 – Documentation & verification**  
  ☑ Update knowledge map, relevant feature plans, and session snapshot with the new terminology.  
  ☑ Execute `./gradlew --no-daemon :rest-api:test` (or targeted suites) and `./gradlew --no-daemon spotlessApply check`; record outcomes.
