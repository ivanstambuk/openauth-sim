
# Feature Plan 006 – OCRA Operator UI

_Linked specification:_ `docs/4-architecture/features/006/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
- Ship a server-rendered operator UI under `/ui/ocra` that lets humans evaluate OCRA credentials by calling the existing REST endpoints.
- Mirror REST validation + telemetry semantics so inline and stored-credential flows behave exactly like CLI/REST facades.
- Keep accessibility, CSRF, and sanitization guardrails in place while documenting workflows in the how-to guides.
- Maintain deterministic presets/reference vectors (JSON catalogue + RFC 6287 files) so Selenium, MockMvc, and compliance tests remain stable.

## Scope Alignment
- **In scope:**
  - Spring MVC controllers, templates, and JavaScript required for inline/stored evaluation modes.
  - REST client wiring (fetch) plus activity log panes that expose sanitized telemetry.
  - Preset catalogue, inline policy builder, stored credential dropdowns, sample loader buttons, timestamp auto-fill toggles.
  - Accessibility + Selenium regression suites, documentation + roadmap sync, telemetry/knowledge-map updates.
- **Out of scope:**
  - New REST APIs beyond the evaluate + credential helper endpoints.
  - HOTP/TOTP tabs, credential lifecycle management, or CLI changes.
  - SPA frameworks or client-side routing—server-rendered views remain the standard.

## Dependencies & Interfaces
- Spring Boot `rest-api` module (controllers, Thymeleaf/Mustache starter, CSRF config).
- Existing OCRA REST endpoints (`/api/v1/ocra/evaluate`, `/api/v1/ocra/credentials`, `/api/v1/ocra/credentials/{id}/sample`).
- Shared persistence (`data/credentials.db` MapDB store) and fixture catalogues (`docs/ocra_validation_vectors.json`, RFC 6287 files).
- `TelemetryContracts` for `rest.ui.ocra.*` events plus shared console JavaScript/CSS assets.

## Assumptions & Risks
- **Assumptions:** Shared credential store remains available; preset catalogue stays authoritative; Selenium HtmlUnit driver remains acceptable for headless coverage.
- **Risks / Mitigations:**
  - _Fixture drift:_ keep builder/preset tests tied to `docs/ocra_validation_vectors.json` (rerun when vectors update).
  - _Telemetry regressions:_ assert UI activity log matches REST frames via MockMvc snapshot tests.
  - _Accessibility regressions:_ maintain HtmlUnit + Selenium suites and reuse shared console components.

## Implementation Drift Gate
- Evidence stored in this plan + `docs/4-architecture/features/006/tasks.md` (trace FR/NFR → increments/tests).
- Gate checks: verify spec/plan/tasks alignment, map FR-006 + S-006 scenarios to code/tests, document telemetry + preset provenance, rerun `./gradlew --no-daemon spotlessApply check` and targeted Selenium suites.

## Increment Map
1. **I1 – Clarifications & groundwork (T-006-01)**
   - _Goal:_ Close clarifications, sync roadmap/knowledge map, confirm templating + REST fetch approach.
   - _Preconditions:_ Draft spec + open questions logged.
   - _Steps:_ Capture decisions, update docs, ensure templates reference latest governance notes.
   - _Commands:_ `less docs/4-architecture/features/006/spec.md`, `rg -n "Feature 006" docs/4-architecture/roadmap.md`.
   - _Exit:_ Clarifications resolved; roadmap/knowledge map updated.
2. **I2 – Failing tests for inline/stored flows (T-006-02)**
   - _Goal:_ Author MockMvc + JS unit tests for fetch submissions, stored credential dropdown, sanitized errors.
   - _Preconditions:_ I1 complete; spec IDs locked.
   - _Steps:_ Write red tests for controller/template responses + client JS.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test --tests "*OcraOperatorUiControllerTest"`, `node --test ui/static/tests/ocra-console.test.js`.
   - _Exit:_ Tests fail for missing implementation, covering S-006-01/S-006-02/S-006-04.
3. **I3 – Controllers/templates/CSRF plumbing (T-006-03)**
   - _Goal:_ Implement inline/stored flows, fetch bridge, telemetry activity log, CSRF + sanitization.
   - _Preconditions:_ I2 failing tests in place.
   - _Steps:_ Add Thymeleaf views, controllers, JS fetch helpers, sanitized error handling.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test`, `./gradlew --no-daemon spotlessApply`.
   - _Exit:_ Tests from I2 pass; telemetry/validation verified; CSRF tokens present.
4. **I4 – Presets, policy builder, sample loader (T-006-04)**
   - _Goal:_ Expand preset catalogue + replay helpers with curated vectors and stored credential hydration.
   - _Preconditions:_ I3 complete; preset JSON available.
   - _Steps:_ Wire presets to builder, add REST sample loader integration, update docs/comments.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test --tests "*Preset*"`, `./gradlew --no-daemon :rest-api:test --tests "*Replay*"`.
   - _Exit:_ Builder + sample loader behaviour green; presets map to compliance fixtures.
5. **I5 – Accessibility, Selenium, timestamp toggle polish (T-006-05)**
   - _Goal:_ Cover layout/ARIA, timestamp auto-fill toggle, CTA label parity across tabs.
   - _Preconditions:_ I4 complete.
   - _Steps:_ Add/upsert Selenium + HtmlUnit assertions, refine CSS/JS for toggles + CTA text.
   - _Commands:_ `./gradlew --no-daemon :ui:test --tests "*OcraConsoleSeleniumTest"`, targeted accessibility tests.
   - _Exit:_ Accessibility + UX suites pass; toggles proven stable.
6. **I6 – Documentation + final verification (T-006-06)**
   - _Goal:_ Refresh how-to/telemetry docs, capture migration notes, run spotless + full check.
   - _Preconditions:_ I1–I5 complete, build green.
   - _Steps:_ Update operator docs, telemetry copy, verification logs; rerun Gradle pipeline.
   - _Commands:_ `rg -n "OCRA operator" docs/2-how-to`, `./gradlew --no-daemon spotlessApply check`.
   - _Exit:_ Docs synced, verification log updated, final build green.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-006-01 | I2–I3 / T-006-02–T-006-03 | Inline fetch submission + result rendering. |
| S-006-02 | I2–I4 / T-006-02–T-006-04 | Stored credential dropdown + auto-fill behaviour. |
| S-006-03 | I4 / T-006-04 | Preset builder + sample loader guardrails. |
| S-006-04 | I2–I3 / T-006-02–T-006-03 | Sanitized error + telemetry surfaces. |
| S-006-05 | I5 / T-006-05 | Accessibility + CTA label parity. |
| S-006-06 | I5 / T-006-05 | Timestamp auto-fill toggle sync. |

## Analysis Gate
- 2025-09-28 – Checklist executed (PASS): spec populated with FR/NFR + clarifications, plan references correct artefacts, tasks order tests before code, and `./gradlew --no-daemon spotlessApply check` confirmed baseline readiness.

## Exit Criteria
- Spec/plan/tasks + telemetry references remain in sync; drift gate documents any deltas.
- `./gradlew --no-daemon spotlessApply check` and targeted Selenium/MockMvc suites are green.
- Inline/stored evaluations verified against shared fixtures; builder/preset provenance logged.
- Knowledge map, roadmap, and how-to documentation updated with UI availability + workflows.
- Activity log + telemetry snapshots archived for audit (no secret leakage).

## Follow-ups / Backlog
1. Capture additional RFC/draft fixture metadata for presets beyond Appendix C.
2. Stage placeholder tests for future credential management flows to guard CLI/UI parity.
3. Document coverage metrics + Gradle command outputs for future drift gates (esp. once REST/UI evolve).
4. Extend negative tests for missing session input / invalid suites to keep validation explicit.
5. Track upcoming features (Feature 003/009+) to ensure they consume the canonical UI contracts without drift.
