# Feature 034 Tasks – Unified Validation Feedback Surfaces

_Linked plan:_ `docs/4-architecture/feature-plan-034-unified-validation-feedback.md`  
_Status:_ Draft  
_Last updated:_ 2025-10-21

☐ **T3401 – Audit current result-card toggles**  
 ☐ Review each ceremony’s controller + template to document current invalid-state behaviour.  
 ☐ Run existing Selenium suites to capture baseline screenshots/logs.

☐ **T3402 – Shared invalid-response helper**  
 ☐ Add controller/view-model helper that normalises invalid messages and toggles visibility.  
 ☐ Extend console JS module with a reusable `showResultMessage`.  
 ☐ Cover helper with targeted unit tests.

☐ **T3403 – Ceremony integration**  
 ☐ Update OCRA, HOTP, TOTP, WebAuthn assertion, and WebAuthn attestation templates/controllers to use the helper.  
 ☐ Render error banner/message within the result card for each ceremony.  
 ☐ Verify existing success flows remain unaffected.  
 ☑ HOTP stored replay ResultCard messaging + Selenium coverage recorded 2025-10-21.  
 ☑ TOTP replay ResultCard messaging updated with mismatch handling (2025-10-21).  
 ☑ HOTP inline evaluation surfaces ResultCard message + hint for invalid payloads (2025-10-21).  
 ☑ WebAuthn inline + attestation evaluation flows now drive ResultCard messaging (2025-10-21).  
 ☑ OCRA evaluate + replay panels now emit ResultCard messaging with updated Selenium coverage (2025-10-21).

☐ **T3404 – Regression tests & docs**  
 ☐ Add Selenium scenarios asserting visible messages on validation failures for every ceremony.  
 ☑ Refresh operator console documentation/how-to sections (2025-10-22 – `docs/2-how-to/use-ocra-operator-ui.md` now explains ResultCard messaging for invalid responses).  
 ☑ Run `./gradlew --no-daemon spotlessApply check` to close the feature (2025-10-22).  
 ☑ HOTP stored replay mismatch scenario added to Selenium suite (2025-10-21).  
 ☑ TOTP replay mismatch scenario asserts ResultCard message + hint (2025-10-21).  
 ☑ WebAuthn inline + attestation invalid scenarios validated via Selenium (2025-10-21).  
 ☑ Rerun full `spotlessApply check` once OCRA documentation refresh lands (2025-10-22).
