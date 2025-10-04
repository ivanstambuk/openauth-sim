# Feature Plan 021 – Protocol Info Surface

_Status: Completed_
_Last updated: 2025-10-04_

## Objective
Implement the protocol info drawer/modal across the operator console, deliver embeddable assets, and document integration guidance per `docs/4-architecture/specs/feature-021-protocol-info-surface.md` while adhering to OpenAuth Simulator workflow guardrails.

## Success Criteria
- Drawer/modal behaviour, keyboard shortcuts, and persistence validated by new Selenium/UI integration tests (PIS-001 — PIS-006, PIS-012).
- ProtocolInfo JS module exposes the required API, events, and schema-driven rendering with automated unit tests (PIS-005 — PIS-007).
- Embeddable assets ship alongside the standalone HTML demo, vanilla DOM integration guidance, and README updates (PIS-008 — PIS-011).
- Single global protocol info trigger reflects the active protocol while supporting drawer/modal behaviours across tab changes (PIS-001, PIS-012).
- Accessibility and performance requirements remain scoped to existing performance/security/maintainability targets (PIS-NFR-002 — PIS-NFR-004).
- `./gradlew spotlessApply check` and targeted module tests pass after each increment.

## Proposed Increments (≤10 minutes each)
- ☑ R2110 – Capture failing Selenium test covering trigger aria attributes, keyboard shortcuts, drawer open/close, and per-protocol switching (apply to UI test suite).
- ☑ R2111 – Added JS-driven Selenium assertions covering ProtocolInfo API exposure, schema/escaping guarantees, persistence keys, and CustomEvents (currently red pending implementation).
- ☑ R2112 – Implemented protocol info triggers, drawer/modal scaffolding, and schema-driven rendering (all protocols) to satisfy new Selenium coverage.
- ☑ R2113 – Wired localStorage persistence (seen/surface/panel), auto-open once-per-protocol logic, ProtocolInfo API, and CustomEvents (open/close/spec-click) with Selenium + JS coverage.
- ☑ R2114 – Hardened modal focus trap/aria guards, added reduced-motion preference handling, and expanded Selenium coverage for accessibility behaviours (modal focus cycle, aria-hidden toggling, motion flags).
- ☑ R2115 – Produce embeddable `protocol-info.css` / `protocol-info.js`, standalone HTML demo, DOM integration guide, and README.
- ☑ R2115A – Rework protocol info trigger to a single global button, update Selenium coverage, and ensure content switches with active protocol.
- ☑ R2116 – Self-review, rerun `./gradlew spotlessApply check` + applicable UI test suites, update knowledge map/roadmap if necessary, and prepare final commit + push.

## Completion Notes
- 2025-10-04 – Feature 021 closed after final self-review, documentation sync, `./gradlew spotlessApply check`, and conventional commit.

## Tooling & Commands
- `./gradlew :rest-api:test` (UI + Selenium suite)
- `./gradlew :ui:test` or equivalent JS test runner command (confirm existing setup)
- `./gradlew spotlessApply check`

## Dependencies & Coordination
- Requires existing protocol tab markup from Feature 020; coordinate if concurrent changes occur.
- Ensure no new dependencies are introduced; rely on current build tooling.

## Notes
- Respect reflection prohibition and dependency guardrails during implementation.
- Adjust Selenium and JS expectations to reflect the single-trigger design before coding changes.
  
## Analysis Gate
- 2025-10-04 – Checklist complete. Outcomes:
  - [x] Specification populated with objectives, requirements, clarifications.
  - [x] No open questions remain for Feature 021.
  - [x] Plan references the correct specification and tasks; success criteria align with requirements.
  - [x] Tasks cover every functional requirement and precede implementation with tests.
  - [x] Planned work complies with constitution guardrails (spec-first, clarification gate, dependency control, reflection ban).
  - [x] Tooling readiness captured (./gradlew :rest-api:test, ./gradlew spotlessApply check, etc.).
