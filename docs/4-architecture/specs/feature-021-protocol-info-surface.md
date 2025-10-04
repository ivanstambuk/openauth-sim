# Feature 021 – Protocol Info Surface

_Status: Draft_
_Last updated: 2025-10-04_

## Overview
Introduce an "Info" drawer for every protocol tab in the operator console so users can access detailed guidance without leaving the UI. The surface mirrors the previously supplied prompt while aligning with OpenAuth Simulator’s workflow: specification-first execution, test-driven increments, and documentation/telemetry governance. The feature delivers drawer + modal UX, reusable embeddable assets, a React integration, and operational documentation.

## Clarifications
- 2025-10-04 – Drawer target width fixed at 520 px to satisfy the "open further to the left" requirement (user directive).
- 2025-10-04 – Implementation must follow existing operating procedures (spec/plan/tasks, ≤10 minute increments, Gradle checks, no new prompts/docs outside feature scope) (user directive).
- 2025-10-04 – The supplied prompt serves as the UX/content baseline; all requirements listed there must appear in this feature’s deliverables (user directive).
- 2025-10-04 – React wrapper requirement removed; ship vanilla DOM API integration guidance instead of a React component (user directive).
- 2025-10-04 – Size monitoring is out of scope; ship embeddable assets without minification/gzip checks (user directive).
- 2025-10-04 – Protocol info trigger is a single button aligned to the right of the protocol tablist; it always references the currently active protocol (user directive).

## Functional Requirements
| ID | Requirement | Acceptance Signal |
|----|-------------|-------------------|
| PIS-001 | Add a single icon-only "i" trigger positioned to the right of the protocol tablist. The button must expose aria-label="Protocol info", aria-haspopup="dialog", aria-controls, and aria-expanded, and it must always map to the currently active protocol. | Selenium/UI integration tests confirm trigger presence, accessible attributes, and active-protocol updates when tabs change. |
| PIS-002 | Render a right-aligned drawer (520 px width, full height, non-modal) that opens via trigger click or "?" / Shift+/ hotkeys, preserves page scroll, and closes on Esc or outside click. | Tests simulate trigger, hotkeys, Esc/outside click and assert drawer visibility/toggle state. |
| PIS-003 | Provide an "Expand" button in the drawer header that promotes the surface to a centered modal (role="dialog", aria-modal="true") with focus trap and focus-restoration to the trigger on close. | Selenium tests open modal, cycle focus, close, and verify focus returns to trigger. |
| PIS-004 | Populate both drawer and modal with a five-section accordion (Overview default open; How it works list with 5–8 steps; Parameters & formats; Security notes & pitfalls; Specifications & test vectors with links). Accordion headers are <button> elements with aria-controls/aria-expanded and respond to keyboard toggles (Enter/Space). | Integration tests assert accordion semantics, default open panel, toggling behaviour, and content presence. |
| PIS-005 | Load content from a JSON data source matching the schema in the original prompt, including sample entries for ocra, hotp, totp, emv, and fido2. Escape all strings before insertion; prohibit raw HTML injection. | Unit tests validate schema parsing, escaping, and rendering per protocol; sample data stored alongside implementation. |
| PIS-006 | Persist state in localStorage using keys `protoInfo.v1.seen.<protocol>`, `protoInfo.v1.surface.<protocol>`, and `protoInfo.v1.panel.<protocol>`. Auto-open once per protocol on viewports ≥ 992 px and remember last surface/panel selections. | Automated tests stub localStorage, simulate viewport width, and ensure persistence logic behaves as specified. |
| PIS-007 | Expose a framework-agnostic JS API (`ProtocolInfo`) with methods `mount`, `open`, `close`, and `setProtocol`, ensuring idempotent mounts and emitting the required CustomEvents (`protocolinfo:open`, `protocolinfo:close`, `protocolinfo:spec-click`). | Unit tests verify API methods, event emission, and absence of DOM leaks across repeated mounts. |
| PIS-008 | Ship embeddable assets: `protocol-info.css` and `protocol-info.js` suitable for non-React environments. | Manual verification confirms the assets load without console integration and expose the ProtocolInfo API. |
| PIS-009 | Deliver a standalone HTML demo file containing inline HTML/CSS/JS that mirrors production behaviour for manual QA. | Static asset checked into repository; reviewers can open locally to validate behaviour. |
| PIS-010 | Document how to consume the ProtocolInfo DOM API from vanilla applications (mount lifecycle, event handling, persistence contract) with runnable examples. | Documentation section and demo confirm the guidance; integration instructions validated via manual QA checklist. |
| PIS-011 | Provide documentation (README) describing Thymeleaf/Spring MVC integration, public API, data bootstrapping via `<script type="application/json" id="protocol-info-data">…</script>`, and an accessibility/test checklist. | README stored under project docs; review ensures instructions cover integration steps and QA checklist. |
| PIS-012 | Update operator console to keep the drawer open and swap content when protocol tabs change, without interfering with existing evaluation interactions, while the single trigger reflects the new protocol. | Selenium tests navigate between tabs with drawer open, verifying state persistence and trigger state updates. |

## Non-Functional Requirements
| ID | Requirement | Target |
|----|-------------|--------|
| PIS-NFR-002 | Performance | Drawer open/close operations complete within 16 ms average on reference hardware; auto-open logic avoids blocking the main thread (>50 ms work). |
| PIS-NFR-003 | Security | No inline event handlers; all external links use `rel="noopener"`; persisted data must not include secrets. |
| PIS-NFR-004 | Maintainability | Code respects reflection prohibition, dependency guardrails, and existing module boundaries (`core` untouched, UI logic confined to `ui`/`rest-api` modules as appropriate). |

## Test Strategy
- Extend Selenium/system tests to cover trigger/button presence, keyboard shortcuts, drawer/modal behaviour, accordion toggles, and per-protocol switching.
- Add unit tests for the ProtocolInfo JS module covering data parsing, escaping, persistence, events, and API methods.
- Implement React component tests (e.g., Jest + React Testing Library) verifying DOM structure parity and integration with shared assets.
- Before implementation, add failing tests representing new behaviours to drive development.

## Dependencies & Risks
- Requires stable protocol tab structure from Feature 020; layout changes might affect trigger placement.
- LocalStorage access must be guarded for SSR/non-browser environments (React component usage in tests). Ensure fallbacks exist.
- Accessibility regressions could arise if drawer overlays interfere with existing focus order; testing must cover regression scenarios.

## Out of Scope
- Introducing additional protocols beyond those listed (HOTP, TOTP, OCRA, EMV/CAP, FIDO2/WebAuthn, EUDIW entries).
- Server-side rendering of accordion content beyond providing JSON data for client consumption.
- Telemetry beyond CustomEvents; no backend analytics wiring in this feature.

## Verification
- All new/updated tests pass along with `./gradlew spotlessApply check`.
- Self-review confirms drawer/modal behaviour, persistence, and API contract across standalone, embeddable, and React variants.
