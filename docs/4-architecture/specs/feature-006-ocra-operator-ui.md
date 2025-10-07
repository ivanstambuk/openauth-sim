# Feature 006 – OCRA Operator UI Specification

_Status: Draft_
_Last updated: 2025-09-29_

## Overview
Deliver an operator-facing UI that allows manual OCRA evaluation without relying on the CLI. The experience will be hosted within the existing Spring Boot `rest-api` module as server-rendered pages, consume the published REST endpoints, and surface sanitized telemetry so operators can troubleshoot requests quickly. This first increment focuses on evaluation flows; credential lifecycle management remains CLI-only until future workstreams extend the REST surface.

## Clarifications
- 2025-09-28 – Operator UI will ship as server-rendered views inside the existing Spring Boot `rest-api` service (user chose option C); we will introduce a templating engine dependency (e.g., `spring-boot-starter-thymeleaf`) with approval captured by this decision.
- 2025-09-28 – UI interactions will invoke the REST API (`/api/v1/ocra/...`) even though the UI is co-hosted, preserving facade contracts (user chose option A).
- 2025-09-28 – Initial scope covers an evaluation console: credential selection/entry, request parameter capture, OTP result display, and telemetry summary; credential import/delete remains out of scope (user chose option A).
- 2025-09-28 – Selenium-based system tests may depend on `org.seleniumhq.selenium:htmlunit-driver` in test scope to keep browser automation headless and deterministic (user chose option A).
- 2025-09-28 – Evaluation submissions will transition to an asynchronous JSON `fetch` call targeting `/api/v1/ocra/evaluate`, reusing the existing request/response schema (user confirmed option A).
- 2025-09-28 – The server-rendered form POST flow will be removed; the UI is allowed to depend entirely on JavaScript for submissions (user confirmed option B).
- 2025-09-29 – Test vector generation must follow the Appendix B Java workflow documented in `docs/2-how-to/generate-ocra-test-vectors.md` so new suites share a single source of truth (user chose option B).
- 2025-09-29 – The inline preset catalogue will include the `OCRA-1:HOTP-SHA256-6:C-QH64` policy derived from the same generator, keeping UI fixtures aligned with domain regressions (user chose option A).
- 2025-09-29 – When operators unmask the "Shared Secret (hex)" field, the UI will leave the value in place after evaluations (no automatic clearing) because operators rely on static test data and future verification flows rather than sensitive production secrets (user chose option C).
- 2025-09-29 – Operator console will adopt a professional dashboard aesthetic with neutral cards and accent colors to keep the experience polished yet focused (user chose option B).
- 2025-09-29 – UI palette will follow a newly proposed accessible scheme (navy/teal accents with neutral backgrounds) derived in-code to maintain WCAG contrast without relying on external branding assets (user chose option B).
- 2025-09-29 – Styling will rely on custom CSS tokens/variables instead of third-party frameworks to avoid new dependencies while enabling cohesive theming (user chose option B).
- 2025-09-29 – Layout must remain responsive for desktop and tablet operators, ensuring key panels stack gracefully on medium breakpoints (user chose option B).
- 2025-09-29 – Branding will be limited to lightweight in-code typography/wordmark treatments until official assets arrive, avoiding external files while adding subtle identity (user chose option B).
- 2025-09-29 – Inline data input checkboxes will remain native elements styled with enlarged hit areas, a two-column grid, and navy/teal accent colors derived from console tokens to preserve accessibility while polishing their appearance (user chose option A).
- 2025-09-29 – Builder select controls (algorithm, digits, challenge) will use a compact height variant so the dropdowns align visually with adjacent inputs while retaining accessible hit areas (user feedback).
- 2025-09-29 – Inline policy builder will use a guided form that assembles suite components and previews the resulting descriptor live, reducing reliance on memorised strings while staying inline (user chose option B).
- 2025-09-29 – Inline policy builder will use a guided form that assembles suite components and previews the resulting descriptor live, reducing reliance on memorised strings while staying inline (user chose option B).
- 2025-09-29 – Verified the OATH specification only defines OCRA-1 today; the builder will present the version as a read-only OCRA-1 label to avoid implying future variants exist.
- 2025-09-29 – Stored credential mode will surface a dropdown that lists available credential IDs fetched from the REST API; operators no longer type identifiers manually and selections hydrate the evaluation request.
- 2025-09-29 – Stored credential mode will surface a dropdown that lists available credential IDs fetched from the REST API; operators no longer type identifiers manually and selections hydrate the evaluation request.
- 2025-10-07 – OCRA Evaluate tab must keep the same vertical spacing between the “Load a sample vector” label and selector as the Replay tab baseline so inline sample affordances align (user directive).
- 2025-10-07 – Inline preset hint copy must read “Selecting a preset auto-fills the inline fields with illustrative data.” so the UI matches HOTP guidance (user directive).
- 2025-09-29 – Default credential database lives at `data/ocra-credentials.db` in the repo root so CLI and REST/Operator UI share the same persistence by default; environment/property overrides remain supported.
- 2025-09-29 – Builder must surface inline validation and disable Apply when configuration is incomplete (e.g., invalid session length or missing challenge) while keeping assistive messaging accessible (agreed during UX polish).
- 2025-09-29 – Stored credential auto-populate button will fill every suite-required field (challenge, counter, session, timestamp, PIN) and clear disallowed inputs to prevent validation conflicts; logic executes entirely in the client.
- 2025-10-04 – Stored replay mode will expose an explicit "Load sample data" action per curated credential so operators can opt into the preset payload; selecting a credential must not auto-fill context data (user chose option A).
- 2025-09-29 – Timestamp values generated during stored credential auto-population will derive from the current UTC clock snapped to the suite’s declared timestep so evaluations succeed without drift.
- 2025-09-29 – Appendix A/B OCRA reference code from the draft is embedded directly in the generator how-to so agents work offline while preserving the canonical URL reference.
- 2025-09-29 – When an operator selects a stored credential, the UI immediately disables and clears request parameter inputs not supported by that credential’s OCRA suite (user chose option A).
- 2025-09-29 – Disabled request parameter inputs must present a muted/disabled visual state (e.g., grey background, not-allowed cursor) so operators can tell they are non-editable (user request).
- 2025-10-06 – Evaluate and replay tabs list Inline parameters before Stored credential so the inline option remains the default across protocols (user directive).

## Objectives & Success Criteria
- Provide browser-accessible pages that let operators evaluate OCRA responses using stored credentials or inline parameters, mirroring REST validation semantics.
- Display structured, sanitized result summaries (status, telemetry ID, reason codes) so operators can capture troubleshooting data without exposing secrets.
- Reuse existing REST DTOs and validation rules to avoid divergent logic across facades.
- Deliver documentation and tests validating the UI contract and its reliance on REST endpoints.

## Functional Requirements
| ID | Requirement | Acceptance Signal |
|----|-------------|-------------------|
| UI-OCRA-001 | Serve a landing page under `/ui/ocra` (exact route TBD) explaining available evaluation capabilities and linking to the evaluation form. | Accessing the route renders HTML with navigation to the evaluation form and basic instructions. |
| UI-OCRA-002 | Provide an evaluation form that supports choosing an existing credential (via REST lookup) or entering inline suite/secret parameters, enforcing the same mutual exclusivity rules as the REST API. | Submitting valid combinations yields an OTP identical to REST responses; invalid combinations render validation messages matching REST reason codes. |
| UI-OCRA-003 | Allow operators to supply optional challenge/counter/session/timestamp inputs with inline validation hints based on the selected suite. | Form adapts or validates fields so unsupported parameters produce descriptive errors without submitting. |
| UI-OCRA-004 | Display the computed OTP, status, telemetry ID, sanitized details, and request echo after evaluation; surface REST errors (400/500) with user-friendly messaging. | Successful evaluations show OTP and metadata; REST errors show sanitized failure summary without leaking secrets. |
| UI-OCRA-005 | Include an activity log or summary pane that mirrors key telemetry fields (`telemetryId`, `status`, `reasonCode`, `sanitized`). | After submissions, the UI presents these fields in a structured block that operators can copy. |
| UI-OCRA-006 | Ensure CSRF protection and input sanitization for rendered templates per Spring MVC best practices. | Integration tests verify CSRF token presence on forms; linting or tests confirm no unsanitized user input is echoed in HTML. |
| UI-OCRA-007 | Keep inline policy presets aligned with the curated OCRA vector catalog (including `OCRA-1:HOTP-SHA256-6:C-QH64`). | UI tests iterate over each preset and match OTPs against domain compliance fixtures. |
| UI-OCRA-008 | Provide a guided inline policy builder that assembles suite components (crypto function, response length, data inputs) with a live preview and the ability to apply the generated suite/secret to the form. | Selecting builder options updates the preview text and, when applied, populates suite/secret fields identically to manual entry/presets. |
| UI-OCRA-009 | Present stored credentials via a REST-backed dropdown so operators pick an identifier instead of typing it manually. | Switching to stored credential mode triggers a fetch to `/api/v1/ocra/credentials`, populates the combo box, and evaluation requests include the chosen ID without additional input. |
| UI-OCRA-010 | Offer an explicit replay helper that loads curated sample data for stored credentials without auto-populating on selection. | Stored replay mode renders a "Load sample data" action; clicking it fetches canonical context/OTP values via `/api/v1/ocra/credentials/{id}/sample` when available and fills the form, otherwise the UI surfaces a friendly message and leaves existing input untouched. |

## Non-Functional Requirements
| ID | Requirement | Target |
|----|-------------|--------|
| UI-NFR-001 | Accessibility | Follow basic WCAG 2.1 AA practices (semantic HTML, labels, keyboard navigation, ARIA for dynamic content). |
| UI-NFR-002 | Performance | Initial page render under 1s on local deployments; evaluate calls reuse REST latencies (<100ms typical). |
| UI-NFR-003 | Observability | Server logs annotate UI interactions with sanitized telemetry consistent with REST endpoints. |
| UI-NFR-004 | Maintainability | Templates and controllers remain decoupled so future additions (credential management) can extend using the same patterns. |

## UX Outline
- **Landing page:** Brief overview, links to evaluation form, reminder that credential management stays in CLI for now, and guidance on running the REST service.
- **Evaluation form:** Credential selector (dropdown populated via REST lookup) alongside an option to toggle into inline mode; fields for challenge, counter, session, pin; telemetry consent note. Submissions are issued via asynchronous JSON `fetch` calls (with an XMLHttpRequest fallback for HtmlUnit-based automation).
- **Compact layout:** Only the active mode’s inputs are visible; the inactive section collapses to a brief summary. Optional request parameters reside inside an “Advanced parameters” disclosure to keep the primary flow above the fold on desktop and tablet breakpoints.
- **Inline policy builder:** Guided controls let operators configure hashing algorithm, response length, and data inputs while locking the suite prefix to the officially specified `OCRA-1`. A read-only preview updates live, auto-populating the suite/secret fields while preserving the existing preset dropdown. Inline validation warns about unsupported digit counts or session lengths and disables the apply button until the configuration is valid.
- **Stored credential picker:** When operators switch to stored credential mode, the UI fetches available OCRA credential IDs from the REST API and renders them in a searchable dropdown, removing manual identifier entry while keeping copy-friendly labels. Canonical presets embed a `presetKey` so the frontend can identify which credentials expose curated vectors.
- **Stored replay helper:** Stored replay mode keeps fields untouched until the operator triggers “Load sample data.” When clicked, the UI calls `/api/v1/ocra/credentials/{id}/sample`; if the credential maps to a curated preset, the response fills OTP + context values and opens the advanced panel as required. Credentials without presets return 404/204, prompting the UI to show a friendly message instead of altering inputs.
- **Results panel:** OTP output, concise status summary, sanitized flag, and suite preview. Telemetry identifiers remain available via browser dev tools/logs but are no longer surfaced in the card. Provide contextual copy actions for OTP/suite only.
- **Error handling:** Inline form validation for missing fields plus user-friendly error statements derived from REST `reasonCode` values (e.g., “Suite prefix 342424 is not supported”). A sanitized technical detail remains accessible for operators needing escalation notes.

## Test Strategy
- **Spring MVC slice tests** asserting controller + template rendering, CSRF enforcement, and validation error messages.
- **Integration tests** using `MockMvc` to drive the fetch-based JSON submission (stored credential and inline modes) verifying OTP parity with REST responses and sanitised error handling.
- **UI contract tests** ensuring REST error payloads render sanitized messages and do not leak secrets.
- **Draft vector compliance tests** asserting `OCRA-1:HOTP-SHA256-6:C-QH64` outputs generated via the Appendix B workflow remain stable across domain and UI layers.
- **Accessibility smoke checks** (e.g., HTMLUnit or jsoup-based assertions) to confirm label associations and landmark usage.

## Dependencies & Out of Scope
- Add a Spring Boot templating starter (Thymeleaf or Mustache) to enable server-rendered views; decision captured under clarifications.
- No introduction of client-side SPA frameworks in this workstream.
- Credential import/delete and broader operator dashboards remain out of scope pending future features.
- Continue to rely on existing `core` and persistence contracts; no direct database access from templates.

Update this specification as new clarifications emerge or when scope expands beyond the initial evaluation console.
