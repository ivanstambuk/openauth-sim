# Feature Plan 033 – Operator Console Naming Alignment

_Linked specification:_ `docs/4-architecture/specs/feature-033-operator-console-naming-alignment.md`  
_Status:_ Complete  
_Last updated:_ 2025-10-21

## Vision & Success Criteria
- Present the operator console as a protocol-agnostic surface by renaming legacy `Ocra*` UI components to neutral `OperatorConsole*` terminology.
- Ensure telemetry emitted from the console matches the updated naming (event keys, endpoint paths) while preserving payload structure.
- Keep documentation and architectural knowledge in sync so future contributors see the renamed controller reflected across plans, tasks, and maps.

## Scope Alignment
- **In scope:** Renaming Spring MVC controller, replay logger, telemetry endpoint, bean definitions, tests, Selenium suites, and Thymeleaf attributes to `OperatorConsole*`. Updating telemetry event keys/paths and corresponding documentation.
- **Out of scope:** Changing REST API contracts outside the UI telemetry endpoint, rewriting protocol-specific sample-data helpers (`OcraOperatorSampleData`, etc.), or introducing new operator console features.

## Dependencies & Interfaces
- Spring MVC components in `rest-api/src/main/java/io/openauth/sim/rest/ui/`.
- Operator console Thymeleaf templates and JavaScript assets under `rest-api/src/main/resources/templates/ui` and `static/ui`.
- Telemetry adapters via `TelemetryContracts`.
- Selenium and unit tests targeting the operator console (`rest-api/src/test/java/io/openauth/sim/rest/ui/`).
- Documentation: knowledge map, current session snapshot, prior feature plans referencing the legacy names.

## Increment Breakdown (≤30 minutes each)
1. **I1 – Documentation setup**  
   - Add Feature 033 plan/tasks and update `docs/_current-session.md` with the new workstream.  
   - Confirm clarification Option B is recorded in the spec and mark the open question as “Resolved”.

2. **I2 – Controller & telemetry rename**  
   - Rename `OcraOperatorUiController` → `OperatorConsoleController` (file/class/package references).  
   - Update bean definitions (`OperatorConsoleUiConfiguration`), telemetry classes/endpoints (`OperatorConsoleTelemetryLogger`, `/ui/console/replay/telemetry`), and adjust imports/usages accordingly.  
   - Update Java tests (unit, MockMvc) to reference the new types and event keys.
   - _2025-10-21 – Renamed controller, configuration bean, telemetry logger, and replay request record; updated OCRA sample data, MockMvc coverage, and Selenium tests to compile against the new names._

3. **I3 – UI template & Selenium alignment**  
   - Update Thymeleaf templates/JS modules and Selenium/MockMvc tests to expect the renamed attributes/endpoints.  
   - Ensure telemetry event assertions match the new naming.
   - _2025-10-21 – Moved shared console script to `static/ui/console/console.js`, updated template references, and refreshed Selenium flows to assert `/ui/console/replay/telemetry` + `event=ui.console.replay`._

4. **I4 – Documentation & knowledge refresh**  
   - Update relevant feature plans/tasks referencing the old controller name.  
   - Refresh knowledge map and current-session snapshot with the new terminology.

5. **I5 – Verification & closure**  
   - Run `./gradlew --no-daemon :rest-api:test :rest-api:integrationTest` (or targeted suites) followed by `./gradlew --no-daemon spotlessApply check`.  
   - Record outcomes in plan/tasks and prepare the feature for closure.
   - _2025-10-21 – Executed `./gradlew --no-daemon :rest-api:test` and `./gradlew --no-daemon spotlessApply check` (green after renaming `OperatorConsoleReplayEventRequest` to satisfy architecture tests)._

## Risks & Mitigations
- **Risk:** Selenium selectors or data attributes rely on the legacy endpoint path.  
  **Mitigation:** Update selectors/tests concurrently with template changes; run targeted Selenium suite before full build.
- **Risk:** Telemetry event key change could break log processing.  
  **Mitigation:** Confirm no external consumers depend on the prior key; document change in session snapshot/roadmap.

## Checkpoints
- Run the analysis gate checklist once plan/tasks are in sync.
- Maintain ≤30 minute increments, ensuring every change lands with a passing `spotlessApply check`.
