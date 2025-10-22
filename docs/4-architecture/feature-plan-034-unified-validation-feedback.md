# Feature Plan 034 – Unified Validation Feedback Surfaces

_Linked specification:_ `docs/4-architecture/specs/feature-034-unified-validation-feedback.md`  
_Status:_ Draft  
_Last updated:_ 2025-10-21

## Vision & Success Criteria
- Result cards across all operator-console ceremonies (OCRA, HOTP, TOTP, WebAuthn assertion, WebAuthn attestation) surface validation error messages returned by the application/REST APIs.
- Invalid responses automatically unhide the result card, preventing silent failures.
- Shared utilities/documentation make the behaviour the default for future ceremonies.
- Automation (`./gradlew --no-daemon :rest-api:test`, Selenium suites, and full `spotlessApply check`) remains green.

## Scope Alignment
- In scope: operator-console templates, controller/view-model plumbing, JavaScript helper updates, Selenium coverage, documentation refresh (console help/how-to).
- Out of scope: CLI/REST output changes, server-side validation copy rewrite, inline field errors.

## Dependencies & Interfaces
- Builds on existing Thymeleaf templates (`rest-api/src/main/resources/templates/ui/**`) and console JavaScript modules.
- Requires Selenium updates in `Fido2OperatorUiSeleniumTest`, `OcraOperatorUiSeleniumTest`, `TotpOperatorUiSeleniumTest`, etc.
- Documentation touchpoints: operator console how-to under `docs/2-how-to/`.

## Increment Breakdown (≤10 min each)
1. **I1 – Audit existing result panels**
   - Catalogue current result-card toggling logic per ceremony; note hidden states and message placeholders.
   - Command: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.*OperatorUiSeleniumTest"` (dry run).

2. **I2 – Shared invalid-state helper**
   - Introduce a controller/view-model helper that normalises invalid responses (message text + visibility flag).
   - Update console JS to expose `showResultMessage(message)` within the shared module.
   - Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.OperatorConsoleViewModelTest"` (new/updated).

3. **I3 – Apply helper to ceremonies**
   - Wire OCRA, HOTP, TOTP, WebAuthn assertion, and WebAuthn attestation template/controller pairs.
   - Ensure result card HTML renders the message in an error banner.
   - Commands: targeted Selenium suites per ceremony.
   - 2025-10-21: HOTP replay path now emits ResultCard messaging for mismatch responses; Selenium guard added.
   - 2025-10-21: TOTP replay stored/inline flows now reuse ResultCard messaging for mismatch responses with Selenium coverage.
   - 2025-10-21: HOTP inline evaluation uses ResultCard helper for invalid payloads (Selenium covered).
   - 2025-10-21: WebAuthn inline + attestation evaluation controllers now drive ResultCard messaging for invalid responses with Selenium coverage.
   - 2025-10-21: OCRA evaluate + replay panels migrated to the shared ResultCard helper; Selenium suite updated via `OperatorConsoleSeleniumTest`.

4. **I4 – Regression coverage & docs**
   - Add Selenium scenarios that trigger validation failures and assert visible messages.
   - Update operator console how-to / inline hints describing the behaviour.
   - Final command: `./gradlew --no-daemon spotlessApply check`.
   - 2025-10-21: HOTP stored replay invalid scenario covered by `HotpOperatorUiSeleniumTest`.
   - 2025-10-21: `TotpOperatorUiSeleniumTest` enforces ResultCard message/hint for replay mismatch cases.
   - 2025-10-21: `Fido2OperatorUiSeleniumTest` verifies inline + attestation invalid flows surface ResultCard message/hints.
   - 2025-10-21: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.OperatorConsoleSeleniumTest"` executed to validate OCRA migration; full `spotlessApply check` rerun after documentation refresh.
   - 2025-10-22: `docs/2-how-to/use-ocra-operator-ui.md` updated to describe ResultCard feedback for invalid responses; `./gradlew --no-daemon spotlessApply check` now green to close the increment.

## Risks & Mitigations
- **Risk:** Selenium runtime inflation.  
  **Mitigation:** Reuse existing fixture helpers and keep new scenarios scoped to validation checks.

- **Risk:** Future ceremonies forgetting to hook into the helper.  
  **Mitigation:** Document helper usage in knowledge map and add unit test that enforces presence for registered ceremonies.

## Follow-ups
- Update knowledge map once helper lands.
- Consider future enhancement for field-level inline errors if required.
