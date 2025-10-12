# Feature Plan 025 – Sample Vector Label Harmonization

_Linked specification:_ `docs/4-architecture/specs/feature-025-sample-vector-label-harmonization.md`  
_Status:_ Complete  
_Last updated:_ 2025-10-12

## Vision & Success Criteria
- Present compact, attribute-focused dropdown labels for HOTP, TOTP, OCRA, and FIDO2 “Load a sample vector” controls without repeating the phrase “sample vector.”
- Preserve current preset coverage and ordering while boosting scanability via a consistent `<source or scenario> - <key attributes>` pattern.
- Keep Selenium/UI coverage and operator how-to docs in lockstep with the renamed entries.

## Scope Alignment
- In scope: Renaming inline preset labels and related metadata, adjusting placeholder copy where needed, updating dependent tests/documentation.
- Out of scope: Adding/removing presets, altering seed catalogues, changing CLI/REST preset naming.

## Dependencies & Interfaces
- UI templates under `rest-api/src/main/resources/templates/ui/**/`.
- Operator sample data helpers in `rest-api/src/main/java/io/openauth/sim/rest/ui/`.
- Console scripts (`rest-api/src/main/resources/static/ui/**/console.js`) that hydrate preset data.
- Selenium suites in `rest-api/src/test/java/io/openauth/sim/rest/ui/`.
- Operator how-to docs under `docs/2-how-to/`.

## Increment Breakdown (≤10 min each)
1. **I1 – HOTP presets rename**
   - Update `HotpOperatorSampleData`, `hotp/console.js`, and Thymeleaf templates with the new label format.
   - Refresh HOTP Selenium tests and docs.
   _2025-10-12 – Completed HOTP inline preset relabel, refreshed console script/tests, and updated the operator how-to copy._
2. **I2 – TOTP presets rename**
   - Apply the same pattern to TOTP sample data/helpers/templates.
   - Adjust TOTP Selenium assertions and documentation references.
   _2025-10-12 – TOTP inline presets now follow the `<source - attributes>` pattern with docs/tests synced._
3. **I3 – OCRA presets rename**
   - Rename suite labels within `OcraOperatorSampleData` and UI templates.
   - Update dependent tests and docs.
   _2025-10-12 – Renamed OCRA preset labels to `suite - attribute` format; no Selenium label assertions required._
4. **I4 – FIDO2 presets rename**
   - Update `Fido2OperatorSampleData`, inline/replay dropdown placeholders, and Selenium coverage.
   - Confirm generator/replay telemetry snapshots and docs reflect the changes.
   _2025-10-12 – Updated WebAuthn inline/replay labels to `<algorithm - UV status>`, adjusted placeholders/scripts/tests/docs._
5. **I5 – Verification sweep**
   - Run `./gradlew spotlessApply check` plus targeted Selenium suites.
   - Sync roadmap, knowledge map (if relationships shift), and close tasks.
   _2025-10-12 – Executed `spotlessApply check`; knowledge map unchanged because label-only edits introduce no new relationships._

6. **I6 – HOTP seeded coverage expansion**
   - Add a SHA-512 HOTP seeded credential entry and expose matching 6/8-digit inline presets.
   - Update seeding fixtures, inline preset data, Selenium coverage, and how-to docs; rerun targeted tests.
   _2025-10-12 – Added SHA-512 seeded credential + inline presets, refreshed tests/docs, and reran targeted :rest-api:test tasks._

7. **I7 – Inline label compacting**
   - Remove the “Seeded credential” prefix from inline dropdown labels across protocols, retaining only attribute text (add RFC annotations where applicable).
   - Sync Selenium/API/doc assertions to the compact labels.
   _2025-10-12 – Compacted HOTP/TOTP inline preset labels, updated Selenium/API expectations and docs, then reran `:rest-api:test` Hotp/Totp operator suites plus `spotlessApply check`._

8. **I8 – HOTP inline digit coverage expansion**
   - Add seeded HOTP inline presets for SHA-1 (8 digits) plus SHA-256/SHA-512 (6 digits) so every stored credential variant is represented in the dropdowns.
   - Refresh console metadata, Selenium assertions, REST expectations, and operator docs to reference the expanded preset set.
   _2025-10-12 – Added the missing presets, trimmed the “seeded demo” suffix, expanded HOTP stored seeding to mirror every inline preset, refreshed HOTP Selenium coverage, updated inline doc guidance, and reran `:rest-api:test` (evaluate + replay) alongside `spotlessApply check`._

9. **I9 – OCRA RFC label parity**
   - Append `(RFC 6287)` to RFC-backed OCRA inline presets while keeping the draft-only `C-QH64` unmarked.
   - Update any Selenium/docs assertions that reference the affected labels.

10. **I10 – Stored RFC label parity**
    - Ensure OCRA stored credential dropdowns append `(RFC 6287)` to RFC-backed seeds while leaving `C-QH64` unchanged.
    - Adjust controller helpers/tests to reflect the stored label format.
    _2025-10-12 – Annotated stored credential labels via controller helper, added regression test, reran `./gradlew spotlessApply check`._

## Risks & Mitigations
- **Missed test assertions:** audit every protocol-specific Selenium test for string expectations (mitigate with targeted test runs after each increment).
- **Documentation drift:** update how-to guides in the same increment as code changes.

## Telemetry & Observability
- No new telemetry events. Ensure existing metadata fields referencing preset labels stay accurate after renaming.

## Intent & Tooling Notes
- Changes touch multiple previously completed features; track progress here to preserve traceability.
