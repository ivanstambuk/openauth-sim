# Feature Plan 017 – Operator Console Unification

_Linked specification:_ `docs/4-architecture/features/017/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
Unify the operator console into a single `/ui/console` surface with protocol tabs, dark theme, and shared assets so every
facade inherits the same scaffolding. Success means all OCRA evaluation/replay workflows execute inside the unified
console, telemetry mirrors pre-existing events, seed workflows remain idempotent, disabled tabs advertise future facades,
and the documentation/knowledge map reflect the architecture without regressions.

## Scope Alignment
- **In scope:** Unified route + tabs, dark theme, evaluation/replay refactor, legacy route cleanup, query-parameter state,
  seeding workflow, shared stylesheet relocation, doc/telemetry alignment.
- **Out of scope:** Delivering functional FIDO2/WebAuthn or EMV/CAP flows, adding third-party UI frameworks, offline/export
  tooling, or persistence refactors beyond idempotent seeding.

## Dependencies & Interfaces
- `rest-api` module delivers `/ui/console`, OCRA credential REST endpoints, and Selenium harnesses.
- `application` module surfaces seeding, evaluation, verification services, and `TelemetryContracts` adapters.
- MapDB credential store must be available for stored-mode seeding.
- `docs/test-vectors/ocra/operator-samples.json` supplies canonical suites for inline + stored modes.

## Assumptions & Risks
- **Assumptions:** Selenium grid can exercise the dark theme reliably; operator docs can be updated alongside code;
  telemetry adapters already expose `ocra.evaluate`, `ocra.verify`, and `ocra.seed` events.
- **Risks / Mitigations:**
  - Theming regressions could hide focus outlines → keep Percy/axe-core snapshots in CI and add manual review checklists.
  - Query-parameter routing may conflict with future facades → isolate router helper in JS module with unit tests.
  - Seeding endpoint failures could duplicate credentials → enforce append-only semantics in application service tests and
    assert counts via integration tests.

## Implementation Drift Gate
Run the drift gate after all tasks complete:
1. Trace FR/NFR coverage by mapping each spec ID to code/tests in the plan.
2. Capture evidence (links to Selenium recordings, REST contract diffs) inside `docs/4-architecture/features/017/plan.md`
   and attach any artefacts referenced by the specification.
3. Rerun `./gradlew --no-daemon spotlessApply check` plus targeted suites noted under each increment.
4. Summarise lessons + coverage deltas; if drift is detected, raise open questions before marking Feature 017 complete.

## Increment Map
1. **I1 – Console shell & theming (S-017-01, FR-017-01, FR-017-03, FR-017-13)**
   - _Preconditions:_ Specification settled; Selenium baseline updated to expect `/ui/console` route failure.
   - _Steps:_
     - Add failing Selenium journey asserting `/ui/console` renders OCRA tab active and other tabs disabled.
     - Create unified Thymeleaf template + shared JS bootstrap; migrate dark theme tokens + neutral stylesheet path.
     - Verify placeholder tabs show disabled messaging and query params default to OCRA when absent.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleUnificationSeleniumTest"`,
     `./gradlew --no-daemon spotlessApply check`.
   - _Exit:_ Tabs render correctly, stylesheet served from `/ui/console/console.css`, failing tests now green.

2. **I2 – Evaluation & replay flows (S-017-02, FR-017-02, FR-017-04, FR-017-08..FR-017-10)**
   - _Preconditions:_ I1 deployed; REST endpoints still wired to old templates.
   - _Steps:_
     - Stage failing Selenium + MockMvc coverage validating inline/stored toggles, trimmed metadata rows, and telemetry.
     - Embed evaluation/replay forms in the unified template; remove Suite/Sanitized/extra replay metadata rows.
     - Ensure telemetry adapters receive the same payloads; add contract tests around `TelemetryContracts`.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test`, `./gradlew --no-daemon :application:test`, `./gradlew spotlessApply check`.
   - _Exit:_ Evaluation/replay succeed entirely inside `/ui/console`, result cards match single-row layout, telemetry
     contracts green.

3. **I3 – Legacy route cleanup & navigation (S-017-04, FR-017-06, FR-017-07, FR-017-12)**
   - _Preconditions:_ I1–I2 complete; old `/ui/ocra/*` routes still exist.
   - _Steps:_
     - Add failing Selenium tests covering legacy route redirects and query-parameter deep links/history navigation.
     - Remove/redirect legacy Thymeleaf templates/controllers; ensure "Load a sample vector" placement assertions exist.
     - Wire JS router to update `protocol`/`tab` query params and push/pop state.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleHistorySeleniumTest"`,
     `./gradlew --no-daemon spotlessApply check`.
   - _Exit:_ Legacy routes return 404/redirect, navigation tests green, control ordering locked in.

4. **I4 – Seed sample credentials workflow (S-017-03, FR-017-11)**
   - _Preconditions:_ Unified console live; seed button not yet implemented.
   - _Steps:_
     - Stage failing Selenium + REST tests for visibility gating, append-only seeding, and warning styling.
     - Implement REST POST `/api/v1/ocra/credentials/seed` integration with application service + telemetry adapter.
     - Surface the button + status hints only in stored mode when registry empty; ensure results refresh dropdowns.
   - _Commands:_ `./gradlew --no-daemon :application:test --tests "*OcraSeedApplicationServiceTest"`,
     `./gradlew --no-daemon :rest-api:test --tests "*OcraOperatorUiSeeding*"`, `./gradlew spotlessApply check`.
   - _Exit:_ Button toggles correctly, telemetry logs `ocra.seed`, repeated presses add only missing suites.

5. **I5 – Documentation & shared assets (S-017-05, FR-017-05, FR-017-13)**
   - _Preconditions:_ I1–I4 merged.
   - _Steps:_
     - Update operator how-to guides, concepts overview, knowledge map, and migration tracker to reference `/ui/console`.
     - Capture implementation notes + verification commands in plan/tasks; refresh screenshots as needed.
     - Re-run full `./gradlew --no-daemon spotlessApply check` and note outputs in verification log.
   - _Commands:_ `./gradlew --no-daemon spotlessApply check`.
   - _Exit:_ Docs + artefacts synced, plan/tasks referencing new IDs, migration tracker updated.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-017-01 | I1 / T-017-01, T-017-02, T-017-03 | Tabs + dark theme shell. |
| S-017-02 | I2 / T-017-04 through T-017-10 | Evaluation/replay flows, telemetry, metadata layout. |
| S-017-03 | I4 / T-017-11 through T-017-16 | Seeding visibility, append-only REST path. |
| S-017-04 | I3 / T-017-17 through T-017-20 | Legacy redirect removal + query-parameter routing/history. |
| S-017-05 | I5 / T-017-21 through T-017-24 | Documentation, knowledge map, shared assets. |

## Analysis Gate
- Completed 2025-10-03 after clarifications locked: spec, plan, and tasks were aligned; open-questions log empty; tests
  staged ahead of implementation. Future contributors should rerun the gate if new scope is added.

## Exit Criteria
- `./gradlew --no-daemon :rest-api:test :application:test :ui:test spotlessApply check` green at least once after final
  doc updates.
- Selenium suites cover evaluate/replay, seeding, navigation, and styling diffs.
- Docs/knowledge map/migration tracker updated; `docs/_current-session.md` reflects completion.
- Telemetry dashboards show `ocra.evaluate`, `ocra.verify`, `ocra.seed` frames emitted from `/ui/console` flows.

## Follow-ups / Backlog
- Enable the disabled FIDO2/WebAuthn and EMV/CAP tabs once their specs land (tracked in Features 024/039/040).
- Explore console module extraction (Feature 041) so JS controllers become reusable packages.
- Automate `ui.console.navigation` telemetry dashboards to detect regressions early.
