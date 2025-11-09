# Feature Plan 037 – Base32 Inline Secret Support

_Linked specification:_ `docs/4-architecture/features/037/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-01

## Vision & Success Criteria
- Enable operators to paste Base32 secrets across HOTP, TOTP, and OCRA inline flows without breaking existing hex-based automation or telemetry.
- Preserve redaction guarantees and persistence schema while keeping the build green (`./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`).

## Scope Alignment
- **In scope:** shared encoding helper, REST DTO/validation updates, CLI option wiring, operator UI UX adjustments, documentation/knowledge-map sync.
- **Out of scope:** stored credential seeding format changes, QR/otpauth generation, additional encoding formats.

## Dependencies & Interfaces
- Shared helper likely belongs in `core` or `application` alongside existing OTP utilities.
- REST DTO changes will affect OpenAPI snapshots and Selenium fixtures.
- CLI commands use Picocli; help text and tests must reflect new options.
- Operator console changes impact TypeScript modules and Selenium suites.

## Increment Breakdown (≤30 minutes each)
1. **I1 – Encoding helper & HOTP/TOTP REST scaffolding**  
   - Implement Base32→hex helper with unit tests.  
   - Add `sharedSecretBase32` fields to HOTP/TOTP inline DTOs with updated validation.  
   - 2025-10-31 – REST HOTP/TOTP/OCRA inline DTOs/tests now share `InlineSecretInput`; OpenAPI snapshot refreshed after updating controller/service fixtures for `sharedSecretBase32`.  
   - Commands: `./gradlew --no-daemon :core:test :rest-api:test`.

2. **I2 – OCRA REST integration**  
   - Extend OCRA evaluate/replay inline DTOs and services for Base32 conversion.  
   - Update REST tests for Base32 success and validation errors.  
   - 2025-10-31 – Added OCRA evaluation/verification Base32 success + invalid-path tests to confirm REST services route through `InlineSecretInput`.  
   - Commands: `./gradlew --no-daemon :rest-api:test`.

3. **I3 – CLI flag support**  
   - Add Base32 options to HOTP/TOTP/OCRA inline commands with Picocli validation and tests.  
   - 2025-10-31 – Picocli inline commands now honour `--secret-base32` exclusivity across TOTP/OCRA flows; `./gradlew --no-daemon :cli:test` passes with the new coverage.  
   - Commands: `./gradlew --no-daemon :cli:test`.

4. **I4 – Operator console UX & Selenium coverage**  
   - Introduce Base32 inputs and sync logic to operator UI inline panels.  
   - Refresh Selenium coverage for Base32 paths and validation.  
   - 2025-10-31 – Shared `secret-fields.js` keeps Base32/hex inputs in sync across HOTP/TOTP/OCRA consoles; Selenium suites assert Base32 success + conflict validation in all panels.  
   - Commands: `./gradlew --no-daemon :ui:test :rest-api:test`.

5. **I5 – Documentation & knowledge map**  
   - Update how-to guides, knowledge map, analysis gate checklist if needed.  
   - Run full quality command set and capture notes in `_current-session.md`.  
   - 2025-10-31 – Docs/knowledge map refreshed; `Base32SecretCodecTest` now uses JUnit assertions, and the full quality suite passes (`./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`).  
   - Commands: `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`.

6. **I6 – Unified shared-secret textarea**  
   - Replace dual inline secret textareas with a single textarea and Hex/Base32 toggle, reusing the shared conversion helper.  
   - Update validation, telemetry sanitisation, and Selenium coverage to assert mode switching and failed conversions.  
   - 2025-11-01 – Selenium suites updated for unified textarea; HOTP/TOTP assertions rely on API conflict injections while OCRA scenarios verify toggle controls and shared `SharedSecretField` helper coverage.  
   - Commands: `./gradlew --no-daemon :rest-api:test :ui:test`.

7. **I7 – Shared-secret message consolidation**  
   - Collapse the dual hint/warning rows into a single dynamic message that defaults to the conversion hint.  
   - Extend `secret-fields.js` to bind error text into the shared message node and apply alert styling only while conversion fails.  
   - 2025-11-01 – Dynamic hint/error messaging now powers HOTP/TOTP/OCRA panels; Selenium suites assert default hint, error swap, and reset behaviour (`./gradlew --no-daemon :rest-api:test :ui:test`).

## Dependencies
- Requires existing inline flows across protocols and passing Selenium suites.
- Coordinate with Feature 029 PMD updates to ensure new helper complies with rule set.
