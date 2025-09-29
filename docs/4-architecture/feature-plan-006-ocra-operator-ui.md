# Feature Plan 006 – OCRA Operator UI

_Status: Draft_
_Last updated: 2025-09-29_

## Objective
Ship a server-rendered operator UI (hosted inside the Spring Boot `rest-api` module) that lets humans execute OCRA evaluations by calling the existing REST endpoints. The UI must respect sanitized telemetry practices, mirror REST validation semantics, and provide an accessible form-driven experience focused on evaluation scenarios.

Reference specification: `docs/4-architecture/specs/feature-006-ocra-operator-ui.md`.

## Success Criteria
- Browser-accessible landing and evaluation pages live under a `/ui/ocra` route served by Spring MVC.
- Operators can evaluate OCRA responses by either selecting stored credentials (via REST lookup) or supplying inline suite/secret data, with validation identical to the REST API.
- Result views display OTPs, telemetry IDs, and sanitized status/reason metadata without exposing secrets.
- UI interactions include CSRF protection, basic accessibility affordances (labels, landmarks), and sanitized logging aligned with REST telemetry.
- Documentation (how-to/roadmap notes) reflects operator UI availability and usage prerequisites.

## Proposed Increments
- R021 – Draft/align spec, plan, tasks; record clarification decisions and roadmap/knowledge-map updates. ✅ 2025-09-28
- R022 – Author failing Spring MVC/MockMvc tests covering landing page, evaluation form validation (stored vs inline), and rendering of sanitized responses/errors. ✅ 2025-09-28
- R023 – Introduce templating dependency (Thymeleaf) + MVC controller/template scaffolding to satisfy tests, ensuring REST client wiring and CSRF handling. ✅ 2025-09-28
- R024 – Polish UX (aria landmarks, field hints, telemetry panel copy helpers), document operator workflow + sanitized telemetry expectations, and rerun `./gradlew spotlessApply check` prior to commit. ✅ 2025-09-28
- R025 – Remove `required` validation from hidden inline fields so stored credential mode submits cleanly; add regression coverage if possible. ✅ 2025-09-28
- R026 – Wire the REST module to the MapDB credential store (configurable path) so stored credential mode works end-to-end; refresh documentation. ✅ 2025-09-28
- R027 – Add end-to-end UI test covering stored credential submission via MapDB-backed persistence. ✅ 2025-09-28
- R028 – Provide inline OCRA policy presets that auto-populate illustrative test vectors. ✅ 2025-09-28
- R029 – Add Selenium-based system test to validate UI presets and stored credential flow end-to-end. ✅ 2025-09-28
- R030 – Plan asynchronous JSON submission workflow, update spec/tasks with clarified scope. ✅ 2025-09-28
- R031 – Author failing tests covering fetch-based submissions (MockMvc JSON expectations, UI script behaviour). ✅ 2025-09-29
- R032 – Implement fetch-driven submission, remove form POST round-trip, and surface JSON errors in-page. ✅ 2025-09-29
- R033 – Update Selenium and documentation to reflect the JavaScript-only flow, verify telemetry and accessibility. ✅ 2025-09-29
- R034 – Publish Appendix B generator how-to and cross-link specs/plans/tasks to enforce the workflow. ✅ 2025-09-29
- R035 – Extend domain compliance tests with `OCRA-1:HOTP-SHA256-6:C-QH64` vectors generated via the documented process. ✅ 2025-09-29
- R036 – Surface the new policy in UI presets, refresh inline samples/tests, and document OTP expectations. ✅ 2025-09-29
- R040 – Capture styling implementation plan (tokens, professional dashboard layout, responsive breakpoints) inside spec/plan/tasks. ✅ 2025-09-29
- R041 – Add design tokens and base stylesheet delivering the accessible navy/teal palette and typography updates. ✅ 2025-09-29
- R042 – Apply redesigned styles to templates, adjust layouts for desktop/tablet, update docs/tests, and run `./gradlew spotlessApply check`. ✅ 2025-09-29
- R043 – Design compact layout (collapsible sections + guided inline builder plan) and capture spec/task updates. ✅ 2025-09-29
- R044 – Simplify telemetry panel and improve error messaging for REST responses. ✅ 2025-09-29
- R045 – Document guided inline policy builder flow (component selection, preview behaviour, accessibility) across spec/plan/tasks. ✅ 2025-09-29
- R046 – Implement builder UI controls + preview logic with tests (MockMvc + Selenium) and integrate with existing form population. ✅ 2025-09-29
- R047 – Polish builder UX (validation hints, apply/reset handling), sync docs, rerun `./gradlew spotlessApply check`, and push. ✅ 2025-09-29
- R048 – Restore advanced parameters collapse behaviour and add regression coverage. ✅ 2025-09-29
- R049 – Reposition advanced disclosure immediately below its toggle and update regression coverage. ✅ 2025-09-29
- R052 – Document stored credential combo requirement and sync spec/tasks. ✅ 2025-09-29
- R053 – Expose REST credential directory endpoint with tests. ✅ 2025-09-29
- R054 – Populate stored credential dropdown from REST and update UI tests. ✅ 2025-09-29
- R055 – Align CLI/REST default credential store to repo-root `data/` path and document the change. ✅ 2025-09-29
- R056 – Remove inline mode quick diagnostics hint to match operator feedback. ✅ 2025-09-29
- R057 – Drop redundant mode selection hint now that radio logic is self-explanatory. ✅ 2025-09-29
- R058 – Remove inline/stored mode headings + descriptive hints when sections toggle. ✅ 2025-09-29
- R050 – Lock builder version to read-only OCRA-1 after verifying future variants do not exist. ✅ 2025-09-29
- R051 – Ensure stored credential inputs hide when inline mode is active. ✅ 2025-09-29
- R059 – Inline Appendix A/B OCRA generator code within the how-to guide to remove repeated external fetch steps. ✅ 2025-09-29
- R060 – Capture stored credential auto-population scope (clarifications, plan, tasks) and confirm client-side generation strategy. ✅ 2025-09-29
- R061 – Add failing MockMvc + Selenium coverage for stored credential auto-populate button (required field injection, disallowed field clearing, timestamp derivation). ☐
- R062 – Implement client-side suite parsing and random generation for stored credential auto-population, snapping timestamp to current UTC step. ✅ 2025-09-29
- R063 – Refresh documentation, tasks, and knowledge map entries; rerun `./gradlew spotlessApply check`. ✅ 2025-09-29
- R064 – Polish stored credential auto-fill styling/text (spacing, button label tweak, remove redundant hints). ✅ 2025-09-29
- R065 – Add failing Selenium/MockMvc assertions covering enhanced inline checkbox styling (layout, sizing, accent colors). ✅ 2025-09-29
- R066 – Implement enlarged two-column checkbox styling using design tokens and update docs/tests before rerunning checks. ✅ 2025-09-29

## Dependencies
- Add `spring-boot-starter-thymeleaf` (or approved templating starter) to `rest-api`. Ensure dependency approval is recorded (captured in spec clarifications).
- Reuse existing REST DTOs/service beans; no direct access to persistence from views.
- Add `org.seleniumhq.selenium:htmlunit-driver` as a test-scoped dependency to enable Selenium-based system testing (approved 2025-09-28).

## Risks & Mitigations
- **Template security:** Ensure HTML escapes dynamic values by default; add tests verifying sanitized output.
- **REST coupling:** Mock REST responses in tests to avoid tight coupling with network calls; rely on service layer or client bean abstractions.
- **Accessibility gaps:** Incorporate basic axe-style assertions (or HTMLUnit checks) early to avoid rework.

## Intent & Tooling Log
- 2025-09-28 – Session kickoff recorded decisions from user (UI hosted in REST module, REST integration, evaluation-only scope); created spec + plan skeleton.
- 2025-09-28 – R022: Added MockMvc-driven UI tests (landing, evaluation success, sanitized errors, CSRF enforcement) to drive Thymeleaf implementation; tests currently red pending controller/templates.
- 2025-09-28 – R023: Added `spring-boot-starter-thymeleaf`, manual session-backed CSRF tokens, MVC controller, and Thymeleaf templates; tests now green, sanitizing secrets from rendered HTML and verifying REST delegation.
- 2025-09-28 – R024 planning: identified accessibility gaps (form mode toggle lacks labels, results lack summary semantics) and documentation work items (how-to guide, telemetry/logging notes, roadmap status update) to address before final polish.
- 2025-09-28 – R024 delivery: Introduced radio-based mode toggle with accessible sections, expanded templates to surface telemetry summary + sanitized error messaging, refreshed operator how-to/knowledge map, and recorded UX polish in roadmap/tasks.
- 2025-09-28 – R025: Relaxed `required` attributes and disabled inactive inputs so stored credential submissions pass native validation; retained accessibility hints and kept MockMvc suite green.
- 2025-09-28 – R026 planning: identified that the REST app lacked a `CredentialStore` bean, causing stored credential lookups to fail; need configurable MapDB wiring + docs alignment with CLI database path.
- 2025-09-28 – R026 delivery: Added conditional MapDB-backed `CredentialStore` bean with configurable path, disabled during tests, and updated operator how-to/knowledge map to document the shared database configuration.
- 2025-09-28 – R027 planning: need `TestRestTemplate`-driven UI flow to assert stored credential path works against MapDB store using CSRF/session handling.
- 2025-09-28 – R027 delivery: Added `OcraOperatorUiEndToEndTest` (now updated for JSON fetch submissions) covering stored credential workflows against the shared MapDB store to prevent regressions.
- 2025-09-28 – R028 planning: expose preset dropdown sourced from existing test vectors (e.g., S064, S128, PIN, timestamp) and auto-fill inline fields while keeping secrets sanitized.
- 2025-09-28 – R028 delivery: Added preset dropdown sourced from REST tests, front-end auto-fill script, and regression coverage (MockMvc + end-to-end) ensuring inline mode vectors stay in sync.
- 2025-09-28 – R029 delivery: Introduced HtmlUnit-driven Selenium system test covering inline preset auto-fill and stored credential submissions, wired new test dependency, and adjusted UI script for broader JS engine compatibility.
- 2025-09-28 – R030 planning: Captured decision to submit evaluations via JSON fetch calls (no form POST fallback) targeting `/api/v1/ocra/evaluate`; tasks and upcoming increments updated accordingly.
- 2025-09-29 – R031: MockMvc tests now green with fetch metadata, XHR fallback, and 405 POST guard; JS fetch polyfill + result panels implemented, rotating HTMLUnit + Selenium suites to pass.
- 2025-09-29 – R033: Updated Selenium assertions to verify result/error panels, refreshed operator UI how-to + spec wording for JSON-only submissions, and reran full `spotlessApply check`.
- 2025-09-29 – R034: Authored Appendix B generator how-to, updated spec/tasks/knowledge map, and linked the workflow across docs.
- 2025-09-29 – R035: Captured `OCRA-1:HOTP-SHA256-6:C-QH64` vectors from the draft generator into new core fixtures and compliance tests.
- 2025-09-29 – R036: Added the C-QH64 preset to the operator UI, refreshed MockMvc/Selenium coverage, and documented preset behaviour for operators.
- 2025-09-29 – R037: Unmasked the shared secret inline field, kept its value after evaluations, and refreshed copy/tests to reflect the hygiene stance. ✅
- 2025-09-29 – R038: Removed the legacy form POST fallback so Evaluate relies entirely on the fetch workflow. ✅
- 2025-09-29 – R039: Converted the Evaluate button to a JS-only trigger, added keyboard handling, and refreshed tests to cover the new markup. ✅
- 2025-09-29 – Decision: Shared secret field remains populated after evaluations (no auto-clear) since the UI handles test data and future verification flows rather than live secrets.
- 2025-09-29 – Decision: Locked in professional dashboard styling, custom accessible palette, CSS token approach, desktop/tablet responsiveness, and lightweight in-code branding following user selections (Options B across styling questions).
- 2025-09-29 – R040: Updated plan/tasks/spec with styling increments and documented the design direction ahead of implementation.
- 2025-09-29 – R041: Added `console.css` design tokens, gradients, typography, and responsive shell; linked the stylesheet into the evaluation template.
- 2025-09-29 – R042: Refined the evaluation template with professional dashboard layout, responsive column stack, styled controls, and verified via `./gradlew spotlessApply check`.
- 2025-09-29 – Decision: Inline policy builder will be a guided form that assembles suite components with a live preview rather than free-form text entry.
- 2025-09-29 – R043: Documented compact layout approach (collapsing inactive sections, advanced-parameters disclosure) and outlined guided policy builder UX in the spec.
- 2025-09-29 – R044: Collapsed optional request parameters behind an advanced disclosure, trimmed telemetry fields, added friendly error messaging, and updated tests/documentation.
- 2025-09-29 – R047: Added builder validation/status messaging, disabled apply on invalid input, refreshed docs/tasks, and reran checks.
- 2025-09-29 – R046: Added guided builder controls (version/algorithm/digits/data inputs), live preview + secret generator, wired apply/reset actions, and updated MockMvc/Selenium coverage.
- 2025-09-29 – R048: Repaired advanced parameters disclosure by tightening CSS attribute selectors and added Selenium coverage to ensure the panel hides/shows correctly.
- 2025-09-29 – R049: Moved advanced panel markup to sit directly after the disclosure stack, updated Selenium coverage to assert sibling order, and reran checks.
- 2025-09-29 – R050: Confirmed via RFC 6287 that OCRA-1 is the sole published variant, updated spec/tasks, locked the builder version field to a read-only `OCRA-1`, refreshed UI copy, and extended Selenium coverage before rerunning checks.
- 2025-09-29 – R051: Added global `[hidden]` CSS guard, asserted section visibility via Selenium, and reran targeted tests.
- 2025-09-29 – R052 planning: expand spec/tasks for REST-backed stored credential picker so operators select IDs instead of typing manually.
- 2025-09-29 – R052: Spec, plan, and tasks updated to capture REST-backed credential dropdown requirement and new UI behaviour.
- 2025-09-29 – R053: Added `/api/v1/ocra/credentials` endpoint returning sanitized summaries with snapshot/test coverage.
- 2025-09-29 – R054: Wired stored credential mode to fetch summaries, render dropdown/status messages, and updated Selenium coverage + controller assertions.
- 2025-09-29 – R055: Unified CLI/REST defaults on repo-root `data/ocra-credentials.db`, added shared path utility, refreshed docs/tests, and re-ran checks.
- 2025-09-29 – R059: Embedded Appendix A/B reference code directly in the generator how-to, refreshed spec/tasks, and re-ran `./gradlew spotlessApply check` to confirm the documentation-only change passes gates.
- Tooling: Codex CLI, shell commands (sed/apply_patch) logged in terminal history for reproducibility.

## Analysis Gate Notes
- 2025-09-28 – Checklist complete prior to implementation:
  - Specification populated with objectives, functional + non-functional requirements, and clarified decisions.
  - Open questions log clear (no blocking entries for Feature 006).
  - Plan references spec/tasks; success criteria aligned.
  - Tasks map to UI-OCRA-001–006 and sequence tests before implementation.
  - Work respects constitution guardrails (spec-first, clarification gate, test-first, dependency control, documentation sync).
  - Implementation reminders: run `./gradlew spotlessApply check` each increment and note Thymeleaf dependency addition in commit rationale.

Update this plan after each increment and remove once Feature 006 ships.
