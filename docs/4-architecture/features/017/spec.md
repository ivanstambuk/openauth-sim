# Feature 017 – Operator Console Unification

_Status:_ Complete  
_Last updated:_ 2025-10-31

## Overview
Deliver a single dark-themed operator console that unifies OCRA evaluation and replay workflows while establishing protocol tabs for future facades (FIDO2/WebAuthn, EMV/CAP). The console retains the existing Spring Boot + Thymeleaf + vanilla JS stack, removes surplus whitespace, and keeps OCRA as the only fully interactive protocol while the others surface disabled previews until their implementations arrive.


## Goals
- Unify operator console layouts, components, and styles across protocols to reduce drift.
- Extract shared assets (JS/CSS/templates) so future protocols inherit the same UX scaffolding.

## Non-Goals
- Does not add protocol-specific business logic.
- Does not redesign the console beyond harmonizing existing patterns.


## Clarifications
- 2025-10-03 – Evaluation and replay screens will be collapsed into one console with protocol tabs, allowing operators to switch between OCRA modes without leaving the page (user approved).
- 2025-10-03 – Non-OCRA protocol tabs (FIDO2/WebAuthn, EMV/CAP) will ship as disabled placeholders that signal upcoming support but do not expose active flows yet (user selected Option B).
- 2025-10-03 – The futuristic visual design must rely on the current Thymeleaf + vanilla JS tooling; no new JavaScript/CSS dependencies may be added without explicit approval (user directive).
- 2025-10-03 – Legacy `/ui/ocra/evaluate` and `/ui/ocra/replay` routes must be decommissioned now that the unified console is live; navigation should flow through `/ui/console` only (owner directive).
- 2025-10-03 – In the OCRA replay tab, the "Load a sample vector" control must appear immediately below the replay mode selector, mirroring the evaluate tab layout (user directive).
- 2025-10-03 – Replay result cards must omit Telemetry ID, Credential Source, Context Fingerprint, and Sanitized fields to reduce visual clutter (user directive).
- 2025-10-03 – Result metadata in both evaluation and replay panels must display one label/value per row instead of multi-column grids (user directive).
- 2025-10-03 – Evaluation result cards must omit the Suite field to keep the summary minimal (user directive).
- 2025-10-04 – Evaluation results should no longer display the Sanitized indicator; the UI may assume sanitisation is always enforced server-side (user directive).
- 2025-10-04 – When no stored credentials exist, surface a manual "Seed sample credentials" action in the operator console instead of auto-populating (user selected Option B).
- 2025-10-04 – Seed the MapDB store using the same canonical suites used by the inline autofill option (one credential per suite) so future iterations can append project-specific fixtures (user selected Option B).
- 2025-10-04 – Allow operators to invoke seeding multiple times; subsequent runs append only credentials that are not already present (user selected Option C).
- 2025-10-04 – Implement seeding through a REST endpoint invoked from the UI button, capturing telemetry for each invocation (user selected Option A).
- 2025-10-04 – The `Seed sample credentials` control should appear only when the stored credential mode is selected; hide it for inline mode to reduce noise (user directive).
- 2025-10-04 – In the replay tab, the stored credential selector must render immediately beneath the mode chooser so operators can access it without scrolling past inline fields (user directive).
- 2025-10-04 – Encode the selected protocol and tab in `/ui/console` using query parameters (e.g., `protocol=ocra&tab=replay`), ensuring refreshable deep links (user selected Option A).
- 2025-10-04 – Preserve disabled protocol placeholders when deep-linked or navigated via the browser history, pushing history entries for tab changes (user selected Option B).
- 2025-10-04 – Display seeding status messages directly beneath the `Seed sample credentials` button while keeping the original hint text visible (user selected Option B).
- 2025-10-04 – Highlight seeding failures with the existing red/danger styling so operators can spot errors quickly (user directive).
- 2025-10-04 – Treat "no credentials added" outcomes as a warning: render the status in an accent distinct from the neutral hint (user directive).
- 2025-10-15 – Shared operator console styles must live under a neutral namespace (e.g., `/ui/console/console.css`) rather than `ui/ocra/` so all protocol tabs reference the same asset without implying OCRA ownership (user selected Option B).

## Branch & Scenario Matrix

| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S17-01 | Unified `/ui/console` route, protocol tabs, and dark theme layout replace legacy OCRA-specific routes. |
| S17-02 | Evaluation/replay flows, result cards, and telemetry operate entirely within the unified console. |
| S17-03 | Sample seeding controls and status hints append missing credentials idempotently. |
| S17-04 | Query-parameter routing/history, redirects, and Selenium coverage ensure `/ui/console` is the single entry point. |
| S17-05 | Shared assets (`console.css`), documentation, and knowledge map capture the new architecture. |

## Functional Requirements
| ID | Requirement | Acceptance Signal |
|----|-------------|-------------------|
| OCU-001 | Present a main operator console reachable at `/ui/console` with protocol tabs (OCRA active, FIDO2/WebAuthn + EMV/CAP disabled) to replace separate evaluation and replay routes. | Selenium/system tests load `/ui/console` and assert OCRA is selected while other protocols expose disabled states. |
| OCU-002 | Surface both OCRA evaluation (generate) and replay (verify) interactions from the unified console, allowing operators to switch modes without leaving the page. | UI tests confirm toggling between evaluation and replay flows proceeds within the unified screen and drives the REST endpoints. |
| OCU-003 | Apply a responsive dark theme with minimized whitespace so the unified console reflects the requested terminal aesthetic. | Visual review and accessibility tooling verify the dark palette meets WCAG AA and layouts expand across the viewport. |
| OCU-004 | Maintain telemetry emission for both evaluation and replay actions, including any new metadata introduced by the unified console. | Telemetry tests verify payloads include mode/outcome/context without leaking secrets and remain compatible with existing adapters. |
| OCU-005 | Update operator documentation to describe the unified console, dark theme, and protocol tabs, including guidance for placeholder protocols. | Docs under `docs/1-concepts` or how-to guides reflect the new UI with updated screenshots/description. |
| OCU-006 | Remove or redirect legacy `/ui/ocra/evaluate` and `/ui/ocra/replay` routes so operators access flows exclusively via `/ui/console`. | UI/system tests verify the legacy endpoints return 404/redirect responses and `/ui/console` remains the single entry point. |
| OCU-007 | Align replay tab controls so "Load a sample vector" sits directly beneath the replay mode selector, providing consistent spacing with the evaluate tab. | Selenium/UI assertions confirm the control order and spacing within the replay tab matches the evaluate tab arrangement. |
| OCU-008 | Remove Telemetry ID, Credential Source, Context Fingerprint, and Sanitized fields from replay results while keeping mode, reason, and outcome visible. | Selenium/UI tests verify the result card no longer renders the removed fields and continues to present remaining metadata. |
| OCU-009 | Render replay and evaluation metadata with a single label/value per row layout. | Selenium/UI tests confirm result cards expose `.result-row` groupings for each metadata item and no multi-column grid remains. |
| OCU-010 | Remove Suite and Sanitized fields from the evaluation result metadata, leaving only the Status row alongside the generated OTP. | Selenium/UI tests verify the evaluation result renders only the Status row with the OTP and omits Suite/Sanitized entries. |
| OCU-011 | Provide a `Seed sample credentials` control when the stored credential registry is empty; the action calls a REST endpoint to insert canonical OCRA suites (matching inline autofill) and may be re-run to add any missing suites without overwriting existing ones. | UI/system tests confirm the button appears only when appropriate, invokes the endpoint, appends missing suites, and records telemetry; stored credentials remain intact if already present. |
| OCU-012 | Reflect the selected authenticator protocol and sub-tab in `/ui/console` query parameters and restore the appropriate state (including disabled placeholders) on refresh, deep-link, and history navigation. | Selenium/UI tests verify query parameters update on tab changes, direct visits load the corresponding view, and history navigation replays tab state without desynchronisation. |
| OCU-013 | Relocate the shared console stylesheet to a neutral namespace (`/ui/console/console.css`) and update HTML/JS/tests to reference the new path so future protocols consume the asset without OCRA coupling. | Static asset served from `/ui/console/console.css`; Selenium fetch helpers and Thymeleaf templates load the new URL; regression tests confirm styles apply across HOTP/TOTP/OCRA/FIDO2 tabs. |

## Non-Functional Requirements
| ID | Requirement | Target |
|----|-------------|--------|
| OCU-NFR-001 | Performance | Maintain ≤200 ms P95 latency for OCRA submissions despite the added console interactions (measured locally). |
| OCU-NFR-002 | Accessibility | Preserve keyboard navigation, focus outlines, and aria labelling for tab switches and mode panels in the dark theme. |
| OCU-NFR-003 | Responsiveness | Support desktop and large-tablet layouts by adapting column widths without introducing horizontal scroll at ≥1024 px viewports. |
| OCU-NFR-004 | Test Coverage | Extend Selenium/system and integration suites to cover unified navigation, mode toggles, and telemetry paths. |

## Test Strategy
- Extend Selenium/system tests to drive the unified console: verifying tab states, evaluation/replay flows, and visual mode toggles.
- Reuse/extend MockMvc/WebTestClient tests to confirm REST calls remain correct from the consolidated controller endpoints.
- Run `./gradlew :rest-api:test spotlessApply check` for each increment to keep unit and Selenium coverage aligned.

## Dependencies & Risks
- Dark theme must meet accessibility standards; neon accents may require iterative tuning.
- Consolidation could disrupt existing routes/bookmarks; ensure redirects or navigation hints maintain continuity.

## Out of Scope
- Implementing functional FIDO2/WebAuthn or EMV/CAP facades; placeholders suffice.
- Introducing third-party visualization libraries or CSS frameworks.
- Offline/export functionality for future protocol data exports.

## Verification
- Unified console deployed with dark theme, responsive layout, and protocol tabs passes automated UI tests.
- Telemetry + REST integration tests remain green with updated payloads.
- `./gradlew spotlessApply check` runs cleanly with updated Selenium/unit coverage.
- Documentation updates merged alongside UI changes.

Update this specification as further clarifications emerge.
