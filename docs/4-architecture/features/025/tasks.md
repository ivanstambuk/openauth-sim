# Feature 025 Tasks – Sample Vector Label Harmonization

_Status: Complete_  
_Last updated:_ 2025-11-10

> Checklist mirrors the Feature 025 plan increments; every entry kept ≤30 minutes and recorded its verification command the same day it closed.

## Checklist
- [x] T-025-01 – HOTP preset relabel (FR-025-01, S-025-01).  
  _Intent:_ Rename HOTP inline/stored preset labels + placeholders, refresh console script and operator docs.  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*HotpOperator*"`, `./gradlew --no-daemon spotlessApply check` (2025-10-12).
- [x] T-025-02 – TOTP preset relabel (FR-025-01, S-025-01).  
  _Intent:_ Apply the concise label pattern to `TotpOperatorSampleData`, templates, and docs/tests.  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*TotpOperator*"`, `./gradlew --no-daemon spotlessApply check` (2025-10-12).
- [x] T-025-03 – OCRA preset relabel (FR-025-01, S-025-01).  
  _Intent:_ Align inline placeholders + docs, prep for RFC suffix step.  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*OcraOperator*"`, `./gradlew --no-daemon spotlessApply check` (2025-10-12).
- [x] T-025-04 – FIDO2 preset relabel (FR-025-01, S-025-01).  
  _Intent:_ Update FIDO2 inline/replay dropdown copy and Selenium coverage.  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*Fido2Operator*"`, `./gradlew --no-daemon spotlessApply check` (2025-10-12).
- [x] T-025-05 – HOTP seeded coverage expansion (FR-025-02, S-025-02).  
  _Intent:_ Add SHA-512 seeded credential + align inline/stored preset lists.  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*HotpOperator*"`, `./gradlew --no-daemon spotlessApply check` (2025-10-12).
- [x] T-025-06 – Inline label compacting (FR-025-01, S-025-01).  
  _Intent:_ Remove “Seeded credential” prefixes across protocols and refresh documentation.  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*HotpOperator*" "*TotpOperator*"`, `./gradlew --no-daemon spotlessApply check` (2025-10-12).
- [x] T-025-07 – HOTP digit coverage alignment (FR-025-02, S-025-02).  
  _Intent:_ Ensure inline presets cover SHA-1 8-digit + SHA-256/SHA-512 6-digit combos; stored catalog mirrors list.  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*HotpOperator*"`, `./gradlew --no-daemon spotlessApply check` (2025-10-12).
- [x] T-025-08 – OCRA RFC inline suffixes (FR-025-03, S-025-03).  
  _Intent:_ Append `(RFC 6287)` labels to inline presets referencing Appendix C while leaving draft entry untouched.  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*OcraOperator*"`, `./gradlew --no-daemon spotlessApply check` (2025-10-12).
- [x] T-025-09 – OCRA stored RFC suffixes (FR-025-03, S-025-03).  
  _Intent:_ Update stored credential dropdown/controller helper + docs/tests.  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*OcraCredentials*"`, `./gradlew --no-daemon spotlessApply check` (2025-10-12).
- [x] T-025-10 – Verification + documentation sync (FR-025-04, S-025-04).  
  _Intent:_ Record roadmap/session updates, knowledge map review, and final green `spotlessApply check`.  
  _Verification:_ `./gradlew --no-daemon spotlessApply check` (2025-10-12).

## Verification Log
- 2025-10-12 – `./gradlew --no-daemon spotlessApply check` (final label harmonization sweep).
- 2025-10-12 – `./gradlew --no-daemon :rest-api:test --tests "*HotpOperator*" "*TotpOperator*" "*OcraOperator*" "*Fido2Operator*"` (dropdown regression suite).
- 2025-11-10 – `./gradlew --no-daemon spotlessApply check` (template migration verification).

## Notes / TODOs
- Template sweep directive blocks directory renumbering until every feature migrates; Feature 025 is now compliant.
EOF,workdir:.,max_output_tokens:6000}
