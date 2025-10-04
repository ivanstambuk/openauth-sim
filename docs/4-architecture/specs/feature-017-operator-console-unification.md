# Feature 017 – Operator Console Unification

_Status: Draft_
_Last updated: 2025-10-04_

## Overview
Deliver a single dark-themed operator console that unifies OCRA evaluation and replay workflows while establishing protocol tabs for future facades (FIDO2/WebAuthn, EMV/CAP). The console retains the existing Spring Boot + Thymeleaf + vanilla JS stack, removes surplus whitespace, and keeps OCRA as the only fully interactive protocol while the others surface disabled previews until their implementations arrive.

## Clarifications
- 2025-10-03 – Evaluation and replay screens will be collapsed into one console with protocol tabs, allowing operators to switch between OCRA modes without leaving the page (user approved).
- 2025-10-03 – Non-OCRA protocol tabs (FIDO2/WebAuthn, EMV/CAP) will ship as disabled placeholders that signal upcoming support but do not expose active flows yet (user selected Option B).
- 2025-10-03 – The futuristic visual design must rely on the current Thymeleaf + vanilla JS tooling; no new JavaScript/CSS dependencies may be added without explicit approval (user directive).
- 2025-10-03 – Legacy `/ui/ocra/evaluate` and `/ui/ocra/replay` routes must be decommissioned now that the unified console is live; navigation should flow through `/ui/console` only (owner directive).
- 2025-10-03 – In the OCRA replay tab, the "Load a sample vector" control must appear immediately below the replay mode selector, mirroring the evaluate tab layout (user directive).
- 2025-10-03 – Replay result cards must omit Telemetry ID, Credential Source, Context Fingerprint, and Sanitized fields to reduce visual clutter (user directive).
- 2025-10-03 – Result metadata in both evaluation and replay panels must display one label/value per row instead of multi-column grids (user directive).
- 2025-10-03 – Evaluation result cards must omit the Suite field to keep the summary minimal (user directive).
- 2025-10-04 – When no stored credentials exist, surface a manual "Seed sample credentials" action in the operator console instead of auto-populating (user selected Option B).
- 2025-10-04 – Seed the MapDB store using the same canonical suites used by the inline autofill option (one credential per suite) so future iterations can append project-specific fixtures (user selected Option B).
- 2025-10-04 – Allow operators to invoke seeding multiple times; subsequent runs append only credentials that are not already present (user selected Option C).
- 2025-10-04 – Implement seeding through a REST endpoint invoked from the UI button, capturing telemetry for each invocation (user selected Option A).
- 2025-10-04 – The `Seed sample credentials` control should appear only when the stored credential mode is selected; hide it for inline mode to reduce noise (user directive).

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
| OCU-010 | Remove the Suite field from the evaluation result metadata while preserving status and sanitized indicators. | Selenium/UI tests verify the evaluation result renders only Status and Sanitized rows alongside the OTP value. |
| OCU-011 | Provide a `Seed sample credentials` control when the stored credential registry is empty; the action calls a REST endpoint to insert canonical OCRA suites (matching inline autofill) and may be re-run to add any missing suites without overwriting existing ones. | UI/system tests confirm the button appears only when appropriate, invokes the endpoint, appends missing suites, and records telemetry; stored credentials remain intact if already present. |

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
