# Feature 025 – Sample Vector Label Harmonization

_Status: Complete_  
_Last updated: 2025-10-12_

## Overview
Unify the operator console “Load a sample vector” dropdown entries across HOTP, TOTP, OCRA, and FIDO2 so labels present concise, comparable information without repeating “sample vector” (the UI already communicates that context). The change keeps existing preset coverage while renaming each option to emphasise the source scenario and key differentiators (algorithm, digits, session identifiers, user-verification flags), allowing operators to scan choices quickly when switching protocols.

## Clarifications
- 2025-10-12 – Remove the literal phrase “sample vector” from every dropdown option; rely on the surrounding UI copy for that context and conserve horizontal space (user approved Option A).
- 2025-10-12 – HOTP inline presets should include both RFC reference vectors and seeded credentials across the 6-digit and 8-digit variants, adding a SHA-512 seeded sample to match the stored credential set (user request).
- 2025-10-12 – Inline dropdown labels should omit the “Seeded credential” prefix and keep only the attribute block (e.g., `SHA-1, 6 digits (RFC 4226)`), assuming presets remain backed by seeded/demo data across all protocols (user directive).
- 2025-10-12 – HOTP inline presets must surface both 6-digit and 8-digit options for SHA-1, SHA-256, and SHA-512 seeded credentials so operators can exercise every stored preset variant (user follow-up).
- 2025-10-12 – Remove the `(seeded demo)` suffix from HOTP inline labels; the surrounding UI already conveys that presets use demo credentials (user directive).
- 2025-10-12 – Stored HOTP seeding must provision credentials for each inline preset (SHA-1/6, SHA-1/8, SHA-256/6, SHA-256/8, SHA-512/6, SHA-512/8) so stored and inline dropdowns stay aligned (user directive).
- 2025-10-12 – Inline OCRA presets that originate from RFC 6287 Appendix C must append `(RFC 6287)` to their labels, keeping the draft-only `OCRA-1:HOTP-SHA256-6:C-QH64` entry unmarked (user request).
- 2025-10-12 – Stored credential dropdown entries referencing RFC 6287 Appendix C vectors must also append `(RFC 6287)` while leaving the draft-only `OCRA-1:HOTP-SHA256-6:C-QH64` label unchanged (user request).

## Requirements
1. Replace every inline preset label surfaced through the operator UI dropdowns with the format `<source or scenario> - <key attributes>` while preserving protocol-specific cues (e.g., RFC references, suite IDs, algorithm names).
2. Extend HOTP seeded presets to cover SHA-512 alongside existing SHA-1/SHA-256 entries and surface both 6-digit and 8-digit OTP variants in the inline dropdown for every SHA algorithm.
2. Ensure placeholder copy and inline hints remain accurate after the label rename (e.g., “Select a sample” vs “Select a sample vector”).
3. Update Selenium, unit, and integration tests that assert preset labels to reflect the new terminology.
4. Sync how-to documentation and telemetry metadata snapshots so they reference the renamed presets.
5. Retain existing preset ordering and option counts for each protocol; only textual labels and any derived metadata fields should change.
6. Validate `./gradlew spotlessApply check` and targeted UI tests after renaming to confirm no regressions in rendering or telemetry.

## Out of Scope
- Adding or removing preset options.
- Altering the seeding catalogue or underlying sample data payloads.
- Modifying CLI or REST sample catalogue names (those can remain verbose for QA reproducibility unless separately requested).

## Success Criteria
- All operator console dropdowns render the harmonised label pattern with no instances of the literal phrase “sample vector.”
- Automated tests and documentation references stay in sync with the new wording.
- Operators can still distinguish presets by protocol-specific traits with equal or better clarity than before the rename.
