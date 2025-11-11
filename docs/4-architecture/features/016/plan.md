# Feature Plan 016 – OCRA UI Replay

_Linked specification:_ `docs/4-architecture/features/016/spec.md`  
_Status:_ In Progress  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
- Deliver an operator-console Replay panel that mirrors CLI/REST behaviour for stored and inline credentials (FR-016-01–FR-016-04).
- Keep telemetry contract parity: `ui.console.replay` frames must align with `rest.ocra.verify` so auditors can correlate outcomes across facades (FR-016-05, NFR-016-03).
- Maintain accessibility and performance budgets established for Feature 006; Replay flows must honour WCAG 2.1 AA and stay under the 200 ms P95 latency bar (NFR-016-01/NFR-016-02).

## Scope Alignment
- **In scope:**
  - Surfacing Replay navigation, stored credential dropdowns, inline preset automation, and result cards that display telemetry IDs and sanitized reason codes.
  - Extending Selenium/System coverage as the enforcement mechanism for replay UX + validation.
  - Logging UI telemetry via `OperatorConsoleTelemetryLogger` and updating docs/telemetry snapshots.
  - Refreshing how-to guides and knowledge-map entries to reflect the new replay surface.
- **Out of scope:**
  - Changing the Evaluate experience (aside from shared components already touched by Replay work).
  - Introducing replay flows for non-OCRA protocols (HOTP/TOTP, EMV/CAP, WebAuthn, etc.).
  - Credential lifecycle CRUD or persistence schema changes; these remain under Feature 009 + CLI.
  - Adding fallbacks or legacy UI shims beyond the clarifications above (per governance: no implicit backwards compatibility).

## Dependencies & Interfaces
- `/api/v1/ocra/verify`, `/api/v1/ocra/credentials`, and `/api/v1/ocra/credentials/{id}/sample` for stored inventory + verification payloads.
- `OperatorConsoleController`, `ui/ocra/replay.html`, and associated JS fragments that toggle between Evaluate and Replay.
- `TelemetryContracts.ocraVerificationAdapter` + `OperatorConsoleTelemetryLogger` for sanitized logging.
- `docs/ocra_validation_vectors.json` and `data/credentials.db` fixtures that feed inline presets and stored credential seeding.
- Gradle Selenium/System test harness plus HtmlUnit/WebDriver dependencies for headless UI verification.

## Assumptions & Risks
- **Assumptions:**
  - CLI and REST replay semantics stay stable; UI simply orchestrates published contracts.
  - MapDB sample copy succeeds locally, but Selenium can fall back to programmatic seeding when the file is unavailable.
  - Preset catalogue (`docs/ocra_validation_vectors.json`) remains the single source for inline guidance.
- **Risks / Mitigations:**
  - *Selenium flakiness* – mitigate via deterministic HtmlUnit driver, explicit waits, and fallback credential seeding (Clarification 2025-10-04).
  - *Telemetry drift* – keep contract tests for `OperatorConsoleTelemetryLogger` wired to the shared adapter and document every new field.
  - *Accessibility regressions* – reuse Feature 006 helpers, re-run axe assertions, and keep colour palette + focus order identical to Evaluate panels.

## Implementation Drift Gate
Before declaring Feature 016 complete:
1. Reconcile FR/NFR IDs (FR-016-01–05, NFR-016-01–04) against code/tests; record evidence in this plan and the forthcoming drift report appendix.
2. Confirm scenario IDs (S-016-01–04) each have failing → passing tests (Selenium, MockMvc, docs) captured in the task checklist.
3. Run `./gradlew --no-daemon spotlessApply check` and `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.OperatorConsoleReplaySeleniumTest" --info` with logs attached to the drift report.
4. Update docs/knowledge-map/roadmap entries and attach telemetry snapshots referenced by the spec.
Any divergence must be logged as an open question or backlog item before sign-off.

## Increment Map
1. **I1 – Clarifications & red coverage (R1601–R1602)**
   - _Goal:_ Capture owner decisions, sync spec/plan/tasks, and create failing Selenium scenarios for navigation + stored/inline submissions.
   - _Preconditions:_ Feature 006 UI scaffolding + CLI/REST replay contracts.
   - _Steps:_
     - Update spec/plan with answered clarifications and roadmap notes.
     - Author `OperatorConsoleReplaySeleniumTest` cases for stored vs inline replay (expected to fail without UI).
     - Document red state + commands in tasks/plan.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleReplaySeleniumTest"` (fails), `./gradlew spotlessApply` (docs only).
   - _Exit:_ Clarifications recorded; Selenium suite failing on missing Replay panel; open questions log empty.

2. **I2 – REST + telemetry scaffolding (R1603)**
   - _Goal:_ Ensure `/api/v1/ocra/verify` metadata exposes `mode`, `credentialSource`, and telemetry hooks; add MockMvc coverage for replay pathways.
   - _Preconditions:_ I1 failing tests; telemetry adapters available.
   - _Steps:_
     - Extend `OcraVerificationEndpointTest` + telemetry unit tests for stored/inline contexts and verbose traces.
     - Wire `OperatorConsoleTelemetryLogger` POST endpoint and red tests verifying sanitized payloads.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test --tests "*OcraVerificationEndpointTest"`.
   - _Exit:_ REST/telemetry tests green; Selenium still red awaiting UI fragment.

3. **I3 – Replay panel implementation (R1604–R1605)**
   - _Goal:_ Build `ui/ocra/replay.html`, controller endpoints, JS wiring, and accessibility polish.
   - _Preconditions:_ REST + telemetry green, Selenium still failing.
   - _Steps:_
     - Implement stored/inline forms, mode toggles, preset scaffolding, and result card.
     - Reuse shared CSS + status badges; ensure ARIA labels present.
     - Re-run Selenium until stored + inline paths pass.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleReplaySeleniumTest"`, `./gradlew --no-daemon spotlessApply check`.
   - _Exit:_ Selenium suite green for baseline flows; telemetry + MockMvc remain green.

4. **I4 – Documentation + knowledge sync (R1606)**
   - _Goal:_ Update operator how-to, telemetry snapshots, knowledge map, and roadmap.
   - _Preconditions:_ UI + tests green.
   - _Steps:_
     - Refresh docs/2-how-to, docs/3-reference telemetry snapshots.
     - Capture notes in `_current-session.md`, roadmap, migration tracker.
   - _Commands:_ `./gradlew --no-daemon spotlessApply check` (docs only).
   - _Exit:_ Documentation references Replay usage; build stays green.

5. **I5 – Inline preset automation + UX polish (R1607–R1615)**
   - _Goal:_ Add inline preset dropdown, OTP auto-fill, preset visibility toggles, and result-card copy tweaks.
   - _Preconditions:_ I3 complete; Selenium green for baseline.
   - _Steps:_
     - Stage failing Selenium cases for preset visibility + OTP auto-fill (stored vs inline separation).
     - Update controller/template/JS/preset catalogue; rerun Selenium + Gradle gate.
     - Remove temporary auto-fill button (Option B) and align copy.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleReplaySeleniumTest"`, `./gradlew --no-daemon spotlessApply check`.
   - _Exit:_ Inline presets auto-fill suite/secret/context/OTP, stored mode hides presets, result cards match Evaluate styling.

6. **I6 – Stored sample auto-fill + backlog clean-up (T1620)**
   - _Goal:_ Remove the legacy “Load sample data” button for stored credentials, auto-fill context on selection, and capture any deferred tasks.
   - _Preconditions:_ I5 complete; updated spec clarifications accepted.
   - _Steps:_
     - Delete the button, ensure credential selection triggers sample hydration, and cover error fallbacks.
     - Refresh Selenium + MockMvc + docs + migration tracker.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleReplaySeleniumTest"`, `./gradlew --no-daemon spotlessApply check`.
   - _Exit:_ Stored credential UX matches inline preset parity; backlog reduced to telemetry/documentation items only.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-016-01 | I3, I5 | Replay navigation + UI polish validated by Selenium + accessibility checks. |
| S-016-02 | I1, I3, I6 | Stored credential flow (dropdown, auto-fill, REST submission) incl. T1620 cleanup. |
| S-016-03 | I1, I5 | Inline preset automation + validation handled by failing/green Selenium loops. |
| S-016-04 | I2, I4 | Telemetry endpoint + documentation/knowledge-map sync; tracked via MockMvc + docs tasks. |

## Analysis Gate
- **Status:** Completed on 2025-10-03 (see historical notes). Re-run the checklist after I6 lands to confirm no new drift.
- **Next run items:** Attach evidence of telemetry frame parity, Selenium logs, and docs updates; ensure `_current-session.md` references the final commands.

## Exit Criteria
- All FR-016-xx and NFR-016-xx mapped to passing tests + implementation.
- `OperatorConsoleReplaySeleniumTest`, `OcraVerificationEndpointTest`, and telemetry logger tests green in CI.
- `./gradlew --no-daemon spotlessApply check` succeeds after docs + code changes.
- Telemetry snapshots, how-to guides, knowledge map, roadmap, and migration tracker updated to mention Replay.
- Implementation Drift Gate report filed with links to spec/plan/tasks, plus open questions resolved or logged as backlog items.

## Follow-ups / Backlog
1. T-016-20 – Complete stored replay “Load sample data” removal and ensure Selenium + MockMvc cover the auto-fill path (pending in tasks list).
2. Evaluate whether Replay verbose-trace opt-in should expose UI hints similar to Evaluate panels (defer to Feature 040 telemetry consolidation).
3. Monitor test runtime; split Selenium scenarios if the replay suite exceeds acceptable wall-clock limits during future refactors.
