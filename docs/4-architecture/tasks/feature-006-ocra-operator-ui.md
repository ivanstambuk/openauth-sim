# Feature 006 – OCRA Operator UI Tasks

_Status: Draft_
_Last updated: 2025-09-29_

## Execution Notes
- Specification: `docs/4-architecture/specs/feature-006-ocra-operator-ui.md`.
- Keep each task ≤10 minutes; commit after every passing build.
- Follow test-first cadence: add/extend MVC tests before implementing controllers/templates.
- Capture telemetry/logging decisions in plan/spec if adjustments arise.
- 2025-09-29: Fetch submission flow implemented with tests + polyfill; Selenium coverage updated; docs refreshed to describe JS-only workflow.

## Tasks
| ID | Task | Related Requirements | Status |
|----|------|----------------------|--------|
| R021 | Draft spec, feature plan, tasks; update roadmap + knowledge map | UI-OCRA-001–UI-OCRA-006 | ✅ |
| R022 | Add failing MockMvc + template tests for landing page, evaluation flow, sanitized error handling, CSRF token presence | UI-OCRA-001–UI-OCRA-006 | ✅ |
| R023 | Introduce templating dependency, MVC controller, REST client wiring, and Thymeleaf templates to satisfy tests (ensure CSRF + accessibility basics) | UI-OCRA-001–UI-OCRA-006, UI-NFR-001–UI-NFR-004 | ✅ |
| R024 | Polish UX (aria landmarks, mode toggle hints, telemetry summary), update operator docs/how-to + logging notes, run `./gradlew spotlessApply check` | UI-OCRA-001–UI-OCRA-006, UI-NFR-001–UI-NFR-004 | ✅ |
| R025 | Ensure inline-only fields are not required when stored credential mode is active; update tests/docs as needed | UI-OCRA-002, UI-OCRA-004, UI-NFR-001 | ✅ |
| R026 | Provide MapDB-backed CredentialStore bean for REST app with configurable path; update how-to docs | UI-OCRA-002, UI-OCRA-004, UI-NFR-003 | ✅ |
| R027 | Add UI end-to-end test exercising stored credential flow against MapDB store | UI-OCRA-002, UI-OCRA-004, UI-OCRA-005 | ✅ |
| R028 | Offer inline OCRA policy presets that auto-fill illustrative vectors; add coverage | UI-OCRA-002, UI-OCRA-003, UI-OCRA-005 | ✅ |
| R029 | Add Selenium-based system test for UI preset + stored credential flows | UI-OCRA-002, UI-OCRA-005 | ✅ |
| R030 | Plan async JSON submission workflow; update spec/plan/tasks with clarified scope | UI-OCRA-002, UI-OCRA-004 | ✅ |
| R031 | Add failing tests asserting fetch-based submission behaviour (MockMvc + UI script) | UI-OCRA-002, UI-OCRA-004, UI-OCRA-005 | ✅ |
| R032 | Implement fetch-based submission, remove form POST, expose JSON-driven result rendering | UI-OCRA-002, UI-OCRA-004, UI-OCRA-005 | ✅ |
| R033 | Refresh Selenium + docs to reflect JavaScript-only flow and telemetry/accessibility guarantees | UI-OCRA-002, UI-OCRA-005, UI-NFR-001 | ✅ |
| R034 | Publish Appendix B generator how-to, cross-link spec/plan/tasks, and update doc index | UI-OCRA-007, Documentation | ✅ |
| R035 | Add `OCRA-1:HOTP-SHA256-6:C-QH64` vectors to domain tests using Appendix B generator outputs | UI-OCRA-007, Test Strategy | ✅ |
| R036 | Extend UI presets/tests (MockMvc + Selenium) with the new policy and OTP expectations | UI-OCRA-002, UI-OCRA-007 | ✅ |
| R037 | Unmask shared secret field, stop clearing it post-evaluation, update copy/tests | UI-OCRA-002, UI-OCRA-005 | ✅ |
| R038 | Remove form POST fallback; rely exclusively on fetch workflow | UI-OCRA-002, UI-OCRA-004 | ✅ |
| R039 | Switch Evaluate button to JS trigger, add keyboard fallback, refresh tests | UI-OCRA-002, UI-OCRA-004 | ✅ |
| R040 | Document styling plan (tokens, layout strategy, responsive breakpoints) across spec/plan/tasks | UI-OCRA-001–UI-OCRA-006 | ✅ |
| R041 | Implement CSS design tokens + base stylesheet delivering the approved palette/typography | UI-OCRA-001–UI-OCRA-006, UI-NFR-001–UI-NFR-004 | ✅ |
| R042 | Apply redesigned styles to templates, adjust scripts/ARIA as needed, update docs/tests, run `./gradlew spotlessApply check` | UI-OCRA-001–UI-OCRA-006, UI-NFR-001–UI-NFR-004 | ✅ |
| R043 | Design compact layout (collapsible sections, guided inline builder concept), capture plan/spec updates | UI-OCRA-001–UI-OCRA-006 | ✅ |
| R044 | Implement telemetry panel simplification and descriptive error messaging | UI-OCRA-004, UI-OCRA-005 | ✅ |
| R045 | Flesh out guided inline builder specification (controls, preview, apply/reset) in spec/plan/tasks | UI-OCRA-002, UI-OCRA-008 | ✅ |
| R046 | Add builder UI components + preview logic with tests updating MockMvc & Selenium flows | UI-OCRA-002, UI-OCRA-008 | ✅ |
| R047 | Finalize builder UX (validation, accessibility copy), update docs/tests, run `./gradlew spotlessApply check` | UI-OCRA-001–UI-OCRA-008 | ✅ |
| R048 | Restore advanced parameters collapse behaviour via CSS + Selenium regression | UI-OCRA-004, UI-NFR-001 | ✅ |
| R049 | Reorder advanced panel to appear directly under disclosure toggle and extend Selenium coverage | UI-OCRA-004, UI-NFR-001 | ✅ |
| R050 | Lock inline builder version control to OCRA-1 and remove references to non-existent variants | UI-OCRA-008 | ✅ |
| R051 | Hide stored credential inputs whenever inline mode is active; adjust CSS/tests accordingly | UI-OCRA-002, UI-OCRA-004 | ✅ |
| R052 | Update spec/plan/tasks for REST-backed credential picker | UI-OCRA-002, UI-OCRA-009 | ✅ |
| R053 | Add REST endpoint returning stored credential summaries + tests | UI-OCRA-009 | ✅ |
| R054 | Populate stored credential dropdown via fetch + adjust UI/Selenium coverage | UI-OCRA-009 | ✅ |
| R055 | Unify default MapDB path under repo-root `data/` and update docs/tests | UI-OCRA-002, UI-OCRA-009 | ✅ |
| R056 | Remove inline mode "quick diagnostics" hint per operator feedback | UI-OCRA-002, UI-OCRA-005 | ✅ |
| R057 | Drop redundant mode selection help text beneath radio buttons | UI-OCRA-002, UI-OCRA-005 | ✅ |
| R058 | Trim section headings/descriptions for inline vs stored mode per operator request | UI-OCRA-002, UI-OCRA-005 | ✅ |
| R059 | Embed Appendix A/B OCRA generator code directly in the how-to to eliminate repeated fetch/setup steps | UI-OCRA-007, Documentation | ✅ |
| R060 | Record stored credential auto-pop scope + clarifications across spec/plan/tasks | UI-OCRA-009 | ✅ |
| R061 | Add failing MockMvc + Selenium tests ensuring stored credential selection immediately disables unsupported request parameters | UI-OCRA-009, UI-OCRA-005 | ✅ |
| R062 | Implement client-side suite parsing + auto-pop generation (challenge/session/counter/timestamp/PIN) with disallowed field clearing | UI-OCRA-009, UI-OCRA-005 | ✅ |
| R063 | Update documentation, knowledge map, and rerun `./gradlew spotlessApply check` | UI-OCRA-009, Documentation | ✅ |
| R064 | Tweak stored credential auto-fill button label/hint spacing | UI-OCRA-009, UI-NFR-001 | ✅ |
| R065 | Add failing Selenium/MockMvc coverage verifying enhanced inline checkbox layout, sizing, and accent styling | UI-OCRA-002, UI-NFR-001 | ✅ |
| R066 | Implement enlarged two-column checkbox styling with design tokens, refresh docs/tests, rerun `./gradlew spotlessApply check` | UI-OCRA-002, UI-NFR-001 | ✅ |
| R067 | Add failing Selenium assertions that enforce compact builder select height/class styling | UI-OCRA-002, UI-NFR-001 | ✅ |
| R068 | Implement compact select styling, sync docs/tests, rerun `./gradlew spotlessApply check` | UI-OCRA-002, UI-NFR-001 | ✅ |
| R069 | Add failing Selenium assertion confirming OCRA-1 helper text is absent from builder copy | UI-OCRA-002, UI-NFR-001 | ✅ |
| R070 | Remove redundant OCRA-1 helper text from templates, rerun checks | UI-OCRA-002, UI-NFR-001 | ✅ |
| R071 | Add failing Selenium assertion covering compact session checkbox styling | UI-OCRA-002, UI-NFR-001 | ✅ |
| R072 | Implement compact session checkbox styling, rerun `./gradlew spotlessApply check` | UI-OCRA-002, UI-NFR-001 | ✅ |
| R073 | Ensure stored credential selection disables split challenge inputs when suites only support combined challenges | UI-OCRA-009, UI-OCRA-005 | ✅ |

Update this checklist as work progresses.
