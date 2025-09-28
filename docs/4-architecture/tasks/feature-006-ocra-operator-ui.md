# Feature 006 – OCRA Operator UI Tasks

_Status: Draft_
_Last updated: 2025-09-28_

## Execution Notes
- Specification: `docs/4-architecture/specs/feature-006-ocra-operator-ui.md`.
- Keep each task ≤10 minutes; commit after every passing build.
- Follow test-first cadence: add/extend MVC tests before implementing controllers/templates.
- Capture telemetry/logging decisions in plan/spec if adjustments arise.

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

Update this checklist as work progresses.
