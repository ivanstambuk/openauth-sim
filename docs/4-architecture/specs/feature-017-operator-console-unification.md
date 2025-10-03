# Feature 017 – Operator Console Unification

_Status: Draft_
_Last updated: 2025-10-03_

## Overview
Deliver a single dark-themed operator console that unifies OCRA evaluation and replay workflows while establishing protocol tabs for future facades (FIDO2/WebAuthn, EMV/CAP). The console retains the existing Spring Boot + Thymeleaf + vanilla JS stack, removes surplus whitespace, and keeps OCRA as the only fully interactive protocol while the others surface disabled previews until their implementations arrive.

## Clarifications
- 2025-10-03 – Evaluation and replay screens will be collapsed into one console with protocol tabs, allowing operators to switch between OCRA modes without leaving the page (user approved).
- 2025-10-03 – Non-OCRA protocol tabs (FIDO2/WebAuthn, EMV/CAP) will ship as disabled placeholders that signal upcoming support but do not expose active flows yet (user selected Option B).
- 2025-10-03 – The futuristic visual design must rely on the current Thymeleaf + vanilla JS tooling; no new JavaScript/CSS dependencies may be added without explicit approval (user directive).
- 2025-10-03 – Legacy `/ui/ocra/evaluate` and `/ui/ocra/replay` routes must be decommissioned now that the unified console is live; navigation should flow through `/ui/console` only (owner directive).
- 2025-10-03 – In the OCRA replay tab, the "Load a sample vector" control must appear immediately below the replay mode selector, mirroring the evaluate tab layout (user directive).

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
