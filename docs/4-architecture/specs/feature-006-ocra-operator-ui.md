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
- **Results panel:** OTP output, metadata table (status, telemetryId, reasonCode, sanitized flag, suite), copy-to-clipboard helper, and contextual tips for common failure modes. Preset dropdown highlights both RFC 6287 vectors and the new `OCRA-1:HOTP-SHA256-6:C-QH64` sample sourced from the documented generator.
- **Error handling:** Inline form validation for missing fields, dedicated error view for unexpected faults with instructions to consult logs.

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
