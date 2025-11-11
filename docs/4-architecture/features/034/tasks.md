# Feature 034 Tasks – Unified Validation Feedback Surfaces

_Linked plan:_ `docs/4-architecture/features/034/plan.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

> Checklist documents the staged rollout from audit → helper → ceremony wiring → docs/tests.

## Checklist
- [x] T-034-01 – Baseline result-card audit (FR-034-01, S-034-01).  
  _Intent:_ Capture existing invalid-state behaviour/screenshots for OCRA/HOTP/TOTP/WebAuthn flows; note gaps.  
  _Verification:_ Manual audit logged 2025-10-20.

- [x] T-034-02 – Shared helper implementation (FR-034-02, S-034-02).  
  _Intent:_ Add view-model + JS helper toggling result-card visibility and message; add unit tests.  
  _Verification:_ `./gradlew --no-daemon :rest-api:test` (targeted controller/helper tests, 2025-10-21).

- [x] T-034-03 – Ceremony wiring (FR-034-03, S-034-03).  
  _Intent:_ Wire OCRA/HOTP/TOTP/WebAuthn flows to the helper so invalid responses display messages.  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*OperatorUiControllerTest"` (2025-10-21).

- [x] T-034-04 – Selenium invalid scenarios (FR-034-03, FR-034-04, S-034-03/S-034-04).  
  _Intent:_ Extend Selenium suites to drive invalid inputs per ceremony and assert result-card visibility + message copy.  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.*OperatorUiSeleniumTest"` (2025-10-21).

- [x] T-034-05 – Documentation & analysis gate (FR-034-04, S-034-04).  
  _Intent:_ Update how-to guides/knowledge map/session snapshot with the unified pattern; rerun `spotlessApply check`.  
  _Verification:_ `./gradlew --no-daemon spotlessApply check` (2025-10-22).

## Verification Log
- 2025-10-21 – `./gradlew --no-daemon :rest-api:test`
- 2025-10-21 – `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.*OperatorUiSeleniumTest"`
- 2025-10-22 – `./gradlew --no-daemon spotlessApply check`

## Notes / TODOs
- Helper pattern documented for future ceremonies; no outstanding follow-ups.
