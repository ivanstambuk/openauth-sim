# Feature Plan 033 – Operator Console Naming Alignment

_Linked specification:_ `docs/4-architecture/features/033/spec.md`  
_Linked tasks:_ `docs/4-architecture/features/033/tasks.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
Rename the operator console’s internal components from `Ocra*` to neutral `OperatorConsole*` so code, telemetry, and
documentation reflect the console’s multi-protocol scope. Success requires:
- FR-033-01 – Controllers/beans/templates renamed.
- FR-033-02 – Telemetry endpoints/loggers renamed, payloads unchanged.
- FR-033-03 – Templates/JS/Selenium references updated and green.
- FR-033-04 – Documentation/knowledge artefacts capture the change after a green analysis gate.

## Scope Alignment
- **In scope:** Spring MVC controller/bean rename, telemetry endpoint updates, template/JS changes, Selenium + MockMvc test
  updates, documentation sync.
- **Out of scope:** Protocol-specific sample data classes that intentionally remain OCRA-prefixed, REST contracts beyond
  the UI telemetry endpoint, new console features.

## Dependencies & Interfaces
- `rest-api` MVC components (`OperatorConsoleController`, telemetry logger, configuration beans).
- Thymeleaf templates (`templates/ui/**`), console JS assets, Selenium tests.
- Telemetry emits via `TelemetryContracts`.
- Knowledge map, roadmap, `_current-session.md`, and related features referencing console naming.

## Assumptions & Risks
- **Assumptions:** No external consumers rely on the legacy telemetry event key; Selenium harness can tolerate renamed
  data attributes.
- **Risks:**
  - Missed template references → run targeted Selenium suites before closure.
  - Telemetry consumers unaware of event key rename → document change in session snapshot/roadmap.

## Implementation Drift Gate
- Evidence captured 2025-10-21: controller/bean rename diff, Selenium + MockMvc test logs, documentation updates, and
  `./gradlew --no-daemon :rest-api:test spotlessApply check` outputs. Gate remains satisfied unless console naming changes again.

## Increment Map
1. **I1 – Planning & clarification closure (S-033-04)**
   - Create spec/plan/tasks, update `_current-session.md`, mark Option B resolved.
2. **I2 – Controller/bean rename (S-033-01)**
   - Rename `OcraOperatorUiController`/configuration beans; update references/tests.
   - Commands: `./gradlew --no-daemon :rest-api:test` (targeted).
3. **I3 – Telemetry endpoint/logger alignment (S-033-02)**
   - Rename `/ui/console/replay/telemetry` endpoint + logger, update event keys, adjust MockMvc/Selenium assertions.
4. **I4 – Template/JS/Selenium updates (S-033-03)**
   - Refresh Thymeleaf templates/JS assets and Selenium selectors; ensure UI flows remain green.
5. **I5 – Documentation & verification (S-033-04)**
   - Update knowledge map/session snapshot/roadmap; rerun `./gradlew --no-daemon :rest-api:test` and `./gradlew --no-daemon spotlessApply check`.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-033-01 | I2 / T-033-02 | Controller/bean rename. |
| S-033-02 | I3 / T-033-03 | Telemetry endpoint/logger rename. |
| S-033-03 | I4 / T-033-04 | Template/JS/Selenium alignment. |
| S-033-04 | I1, I5 / T-033-01, T-033-05 | Docs + analysis gate. |

## Analysis Gate
- Completed 2025-10-21 with documentation updates and recorded Gradle runs; no open questions remain.

## Exit Criteria
- All code/tests/docs reference `OperatorConsole*` naming.
- Telemetry event keys updated and documented.
- Targeted Gradle commands (`:rest-api:test`, `spotlessApply check`) pass.

## Follow-ups / Backlog
- None.
