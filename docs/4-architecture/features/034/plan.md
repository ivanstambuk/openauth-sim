# Feature Plan 034 – Unified Validation Feedback Surfaces

_Linked specification:_ `docs/4-architecture/features/034/spec.md`  
_Linked tasks:_ `docs/4-architecture/features/034/tasks.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
Ensure every operator-console ceremony surfaces API validation failures within the result card by introducing a shared
helper. Success requires baseline audits, helper implementation, ceremony wiring, and updated tests/docs that prove the
pattern.

## Scope Alignment
- **In scope:** Result-card audit, shared helper (view model + JS), ceremony wiring (OCRA/HOTP/TOTP/WebAuthn), Selenium
  invalid scenarios, documentation updates.
- **Out of scope:** Server-side validation copy changes, CLI/REST output changes, per-field inline errors.

## Dependencies & Interfaces
- `rest-api` operator console controller/view models and Thymeleaf templates.
- Console JS assets and Selenium suites.
- Knowledge map/how-to documentation for operator console workflows.

## Assumptions & Risks
- **Assumptions:** Existing APIs already surface meaningful `message` fields; Selenium fixtures cover invalid flows.
- **Risks:**
  - Helper misses a ceremony → mitigate by auditing all result cards before implementation.
  - Selenium runtime increase due to invalid scenarios → reuse shared fixtures.

## Implementation Drift Gate
- Evidence captured 2025-10-22: helper diff, Selenium invalid scenario logs, documentation updates, and
  `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.*OperatorUiSeleniumTest"` + `spotlessApply check`
  outputs. Gate stays satisfied unless the validation pattern changes.

## Increment Map
1. **I1 – Baseline audit (S-034-01)**
   - Capture current result-card behaviour/screenshots for all ceremonies.
   - Commands: manual review; document findings in plan/tasks.
2. **I2 – Shared helper implementation (S-034-02)**
   - Add view-model + JS helper toggling `showResultCard` + `validationMessage`; write unit tests.
   - Commands: `./gradlew --no-daemon :rest-api:test` (targeted unit tests).
3. **I3 – Ceremony wiring (S-034-03)**
   - Wire OCRA/HOTP/TOTP/WebAuthn assertion/attestation to helper.
   - Commands: `./gradlew --no-daemon :rest-api:test --tests "*OperatorUiControllerTest"`.
4. **I4 – Selenium invalid scenarios (S-034-03/S-034-04)**
   - Extend Selenium suites to drive invalid inputs per ceremony and assert message rendering.
   - Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.*OperatorUiSeleniumTest"`.
5. **I5 – Documentation & analysis gate (S-034-04)**
   - Update how-to guides/knowledge map/session snapshot; rerun `./gradlew spotlessApply check`.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-034-01 | I1 / T-034-01 | Baseline audit. |
| S-034-02 | I2 / T-034-02 | Helper implementation. |
| S-034-03 | I3, I4 / T-034-03, T-034-04 | Ceremony wiring + Selenium coverage. |
| S-034-04 | I5 / T-034-05 | Documentation + analysis gate. |

## Analysis Gate
- Completed 2025-10-22 after helper rollout + documentation updates; no open questions remain.

## Exit Criteria
- All ceremonies display validation messages via result card helper.
- Selenium invalid scenarios pass with new assertions.
- Documentation updated; `spotlessApply check` green.

## Follow-ups / Backlog
- Future ceremonies must adopt the helper; add guidance to new specs as they are created.
