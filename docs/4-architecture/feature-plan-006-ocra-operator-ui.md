# Feature Plan 006 – OCRA Operator UI

_Status: Draft_
_Last updated: 2025-09-28_

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

## Dependencies
- Add `spring-boot-starter-thymeleaf` (or approved templating starter) to `rest-api`. Ensure dependency approval is recorded (captured in spec clarifications).
- Reuse existing REST DTOs/service beans; no direct access to persistence from views.

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
