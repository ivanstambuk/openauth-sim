# Feature 034 – Unified Validation Feedback Surfaces

_Status:_ Complete  
_Last updated:_ 2025-10-31

## Overview
Ensure every operator-console workflow (existing and future) renders service validation failures in a consistent, visible location. When the underlying application/REST APIs return `status=invalid` responses, the UI should unhide the result card and surface the API-supplied `message` content without requiring the operator to inspect logs or browser dev tools. The change applies to all current authentication ceremonies (OCRA, HOTP, TOTP, WebAuthn assertion, WebAuthn attestation) and establishes a reusable pattern that future flows can adopt with minimal wiring.

## Goals
- Display API validation messages within the existing result card for all operator-console workflows.
- Automatically reveal the result card whenever an invalid response arrives so feedback is never hidden.
- Centralise the presentation logic so newly introduced ceremonies inherit the behaviour.
- Maintain telemetry and redaction guarantees (no leakage of raw secrets or sensitive payloads).

## Non-Goals
- Rework server-side validation copy beyond what the application/REST layers already provide.
- Introduce per-field inline errors (may be considered separately if needed).
- Modify CLI or REST textual outputs (they already surface messages directly).

## Clarifications
1. 2025-10-20 – Validation feedback should reuse the existing result card (Option 3.A). When an invalid response is returned, keep the card visible and render the API `message` prominently so operators see it without scrolling through the form (owner directive).
2. 2025-10-20 – Apply the behaviour to all current ceremonies (OCRA, HOTP, TOTP, WebAuthn assertion, WebAuthn attestation) and make the utility reusable for future flows. (Owner directive.)

## Branch & Scenario Matrix

| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S34-01 | Audit existing result-card toggles and capture baseline invalid-state behaviour/screens before rolling out the shared helper. |
| S34-02 | Implement a reusable controller/view-model helper (plus JS hook) that exposes invalid messages and forces the result card visible. |
| S34-03 | Wire all current ceremonies (OCRA, HOTP, TOTP, WebAuthn assertion/attestation) to the helper so invalid responses surface the message in-card. |
| S34-04 | Extend Selenium/docs to cover invalid scenarios for every ceremony and document the pattern for future flows. |

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

## Completion Notes
- 2025-10-21 – All operator-console ceremonies (OCRA, HOTP, TOTP, WebAuthn assertion, WebAuthn attestation) now use the shared result-card helper and expose validation messaging; Selenium suites (`./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.*OperatorUiSeleniumTest"`) assert the messaging for success and failure paths.
- 2025-10-22 – Operator console documentation (`docs/2-how-to/use-ocra-operator-ui.md`) updated to explain the unified feedback pattern and full pipeline `./gradlew --no-daemon spotlessApply check` confirmed green.
- 2025-10-29 – Specification accepted with no outstanding follow-ups; future ceremonies inherit the helper by default.

## Rollout & Future Work
- Update onboarding docs to mention the unified validation pattern.
- Future ceremonies must hook into the shared helper to stay consistent; document it in the knowledge map once implemented.
