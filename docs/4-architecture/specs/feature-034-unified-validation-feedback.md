# Feature 034 – Unified Validation Feedback Surfaces

_Status: Proposed_  
_Last updated: 2025-10-20_

## Overview
Ensure every operator-console workflow (existing and future) renders service validation failures in a consistent, visible location. When the underlying application/REST APIs return `status=invalid` responses, the UI should unhide the result card and surface the API-supplied `message` content without requiring the operator to inspect logs or browser dev tools. The change applies to all current authentication ceremonies (OCRA, HOTP, TOTP, WebAuthn assertion, WebAuthn attestation) and establishes a reusable pattern that future flows can adopt with minimal wiring.

## Goals
- Display API validation messages within the existing result card for all operator-console workflows.
- Automatically reveal the result card whenever an invalid response arrives so feedback is never hidden.
- Centralise the presentation logic so newly introduced ceremonies inherit the behaviour.
- Maintain telemetry and redaction guarantees (no leakage of raw secrets or sensitive payloads).

## Non-goals
- Rework server-side validation copy beyond what the application/REST layers already provide.
- Introduce per-field inline errors (may be considered separately if needed).
- Modify CLI or REST textual outputs (they already surface messages directly).

## Clarifications
1. 2025-10-20 – Validation feedback should reuse the existing result card (Option 3.A). When an invalid response is returned, keep the card visible and render the API `message` prominently so operators see it without scrolling through the form (owner directive).
2. 2025-10-20 – Apply the behaviour to all current ceremonies (OCRA, HOTP, TOTP, WebAuthn assertion, WebAuthn attestation) and make the utility reusable for future flows. (Owner directive.)

## Requirements
- Update the shared operator-console view model/controller layer so invalid responses mark the result panel as visible and inject the API `message`.
- Ensure each existing ceremony card updates its HTML template to display the error copy (e.g., banner or status block) within the result card while preserving current success layout.
- Add regression coverage (e.g., Selenium acceptance tests) that exercises an invalid request for each ceremony and asserts that the result panel appears with the expected message text.
- Provide a reusable helper (JS/Thymeleaf fragment) so new ceremonies can adopt the same pattern with one hook.
- Refresh user-facing documentation describing error handling, if needed.

## Dependencies & Considerations
- Reuse existing telemetry pathways; no new events are required.
- Expect Selenium updates across multiple suites; keep runtime manageable by reusing fixtures.
- Confirm that result cards currently hidden via `aria-hidden` toggles behave correctly when forced visible for invalid responses.

## Success Criteria
- Operator console always surfaces validation messages for all supported ceremonies.
- Selenium suites include failing-then-passing scenarios for each flow verifying result-card visibility and message text.
- Documentation (how-to guides, help text) reflects the new behaviour.
- `./gradlew spotlessApply check` remains green after implementation.

## Rollout & Future Work
- Update onboarding docs to mention the unified validation pattern.
- Future ceremonies must hook into the shared helper to stay consistent; document it in the knowledge map once implemented.
