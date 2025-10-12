# Feature 025 Tasks – Sample Vector Label Harmonization

_Linked plan:_ `docs/4-architecture/feature-plan-025-sample-vector-label-harmonization.md`  
_Status:_ Completed  
_Last updated:_ 2025-10-12

☑ **T1 – HOTP label updates**  
 ☑ Rename HOTP inline preset labels/metadata to the new pattern across sample data, templates, and console script.  
 ☑ Update HOTP Selenium tests and operator how-to references.

☑ **T2 – TOTP label updates**  
 ☑ Apply the label pattern to TOTP sample data/templates/scripts.  
 ☑ Refresh TOTP Selenium tests and documentation snippets.

☑ **T3 – OCRA label updates**  
 ☑ Rename OCRA preset labels and ensure policy builder hints stay accurate.  
 ☑ Update OCRA Selenium/UI tests and docs.

☑ **T4 – FIDO2 label updates**  
 ☑ Update FIDO2 sample data/template placeholders/console script with new labels.  
 ☑ Adjust FIDO2 Selenium assertions and docs.

☑ **T5 – Verification & sync**  
 ☑ Run `./gradlew spotlessApply check` plus targeted Selenium suites.  
 ☑ Sync roadmap/knowledge map, then close plan/tasks once work is verified. (_2025-10-12 – Roadmap already updated with Workstream 21; knowledge map unchanged because label refactors do not introduce new relationships._)

☑ **T6 – HOTP seeded expansion**  
 ☑ Add a SHA-512 seeded HOTP credential and expose inline presets covering both 6-digit and 8-digit OTP variants.  
 ☑ Update HOTP Selenium coverage, API tests, and operator docs to reflect the new preset set. (_2025-10-12 – Added ui-hotp-demo-sha512 seed definition + matching SHA-1/256/512 inline presets, refreshed Selenium/API tests, and reran targeted :rest-api:test suites._)

☑ **T7 – Inline label compacting**  
 ☑ Drop the “Seeded credential” prefix from all inline dropdown labels and retain only attribute details (e.g., `SHA-1, 6 digits (RFC 4226)`), ensuring protocol docs/tests reflect the compact wording. (_2025-10-12 – Updated HOTP/TOTP inline presets + metadata, refreshed Selenium/API expectations, reran targeted `:rest-api:test` Hotp/Totp suites, and finished with `spotlessApply check`._)

☑ **T8 – HOTP inline digit coverage**  
 ☑ Add SHA-1 8-digit and SHA-256/SHA-512 6-digit inline presets to the operator console, then sync Selenium/UI/REST coverage and documentation. (_2025-10-12 – Expanded HOTP inline catalog to six presets, removed the redundant “seeded demo” suffix, ensured stored seeding provisions the same six credentials, updated replay/evaluate Selenium assertions, refreshed docs, and revalidated with targeted :rest-api:test runs plus `spotlessApply check`._)
