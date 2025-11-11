# Feature Plan 025 – Sample Vector Label Harmonization

_Linked specification:_ `docs/4-architecture/features/025/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
Deliver consistent, compact preset labels across HOTP, TOTP, OCRA, and FIDO2 operator panels so operators can scan
sample data quickly while seeded/stored catalogues stay aligned. Success requires:
- FR-025-01 / S-025-01 – Dropdowns + placeholders adopt the `<scenario – attributes>` format with Selenium coverage.
- FR-025-02 / S-025-02 – HOTP inline and stored presets expose the full SHA/digit matrix with matching seeded data.
- FR-025-03 / S-025-03 – RFC suffixes appear only where mandated.
- FR-025-04 / S-025-04 – Docs/roadmap/session logs updated with a recorded green `./gradlew spotlessApply check`.

## Scope Alignment
- **In scope:** Operator console dropdown copy, placeholder text, seeded preset catalogues, Selenium/doc updates.
- **Out of scope:** CLI/REST payload naming, new sample data, telemetry schema changes, directory renumbering (per migration directive).

## Dependencies & Interfaces
- Thymeleaf templates + JS under `rest-api/src/main/resources/templates/ui/**` and `.../static/ui/**/console.js`.
- Sample data helpers (`HotpOperatorSampleData`, etc.) in `rest-api/src/main/java/io/openauth/sim/rest/ui/`.
- Selenium suites in `rest-api/src/test/java/io/openauth/sim/rest/ui/` verifying dropdown content.
- Operator how-to docs in `docs/2-how-to/` and roadmap entry #21.

## Assumptions & Risks
- **Assumptions:** Existing seeded credential data already covers SHA/digit combinations; only labels need alignment.
- **Risks / Mitigations:**
  - Missed Selenium assertions → rerun targeted suites per increment.
  - Documentation drift → update docs in the same increment as code changes.
  - Label truncation on small viewports → keep copy ≤ 40 characters and verify via UI tests.

## Implementation Drift Gate
- Map FR/NFR IDs to increments (see Scenario Tracking) and ensure each dropdown/test/doc change references the spec.
- Capture verification evidence (commands + notes) in `docs/_current-session.md` and the tasks checklist once increments finish.
- Rerun `./gradlew --no-daemon spotlessApply check` plus targeted `:rest-api:test --tests "*Operator*"` before marking the feature complete.
- Document any future rename/renumbering blockers in `docs/migration_plan.md`.

## Increment Map
1. **I1 – HOTP preset relabel (S-025-01)**
   - _Preconditions:_ Spec + clarifications approved; baseline Selenium coverage exists.
   - _Steps:_ Update `HotpOperatorSampleData`, templates, console script, and Selenium assertions; refresh operator docs.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test --tests "*HotpOperator*"`, `./gradlew --no-daemon spotlessApply check`.
   - _Exit:_ HOTP dropdown shows concise labels; docs updated (completed 2025-10-12).
2. **I2 – TOTP preset relabel (S-025-01)**
   - _Steps:_ Mirror I1 for `TotpOperatorSampleData` + templates/tests/docs.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test --tests "*TotpOperator*"`, `./gradlew spotlessApply check`.
   - _Exit:_ TOTP dropdown copy aligned (completed 2025-10-12).
3. **I3 – OCRA preset relabel (S-025-01)**
   - _Steps:_ Rename OCRA inline placeholders + docs; ensure hints remain accurate.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test --tests "*Ocra*"`, `./gradlew spotlessApply check`.
   - _Exit:_ OCRA labels compact with RFC suffix plan in place (completed 2025-10-12).
4. **I4 – FIDO2 preset relabel (S-025-01)**
   - _Steps:_ Update `Fido2OperatorSampleData`, inline/replay placeholders, Selenium coverage, docs.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test --tests "*Fido2Operator*"`, `./gradlew spotlessApply check`.
   - _Exit:_ FIDO2 inline/replay dropdowns use `<algorithm – UV state>` (completed 2025-10-12).
5. **I5 – Verification sweep (S-025-04)**
   - _Steps:_ Run aggregate verification (`spotlessApply check`, targeted Selenium), sync roadmap/knowledge map, close plan entries.
   - _Commands:_ `./gradlew --no-daemon spotlessApply check`, targeted Selenium suites.
   - _Exit:_ Verification logged in docs/session snapshot (completed 2025-10-12).
6. **I6 – HOTP seeded coverage expansion (S-025-02)**
   - _Steps:_ Add SHA-512 seeded credential + matching inline presets; sync stored catalog + Selenium coverage.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test --tests "*HotpOperator*"`, `./gradlew spotlessApply check`.
   - _Exit:_ Six HOTP presets confirmed with stored alignment (completed 2025-10-12).
7. **I7 – Inline label compacting (S-025-01)**
   - _Steps:_ Remove “Seeded credential” prefixes, keep attribute-only labels, refresh copy/tests/docs.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test --tests "*HotpOperator*" "*TotpOperator*"`, `./gradlew spotlessApply check`.
   - _Exit:_ Compact labels verified across HOTP/TOTP (completed 2025-10-12).
8. **I8 – HOTP digit coverage alignment (S-025-02)**
   - _Steps:_ Ensure inline presets include SHA-1 8-digit + SHA-256/512 6-digit combos; stored seeding mirrors list.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test --tests "*HotpOperator*"`, `./gradlew spotlessApply check`.
   - _Exit:_ Dropdown counts/order identical between inline/stored (completed 2025-10-12).
9. **I9 – OCRA RFC label parity (S-025-03)**
   - _Steps:_ Append `(RFC 6287)` to inline presets referencing Appendix C; update docs/tests.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test --tests "*OcraOperator*"`, `./gradlew spotlessApply check`.
   - _Exit:_ Inline dropdown matches RFC attribution policy (completed 2025-10-12).
10. **I10 – Stored RFC label parity (S-025-03, S-025-04)**
    - _Steps:_ Ensure stored credential dropdowns/REST listings append `(RFC 6287)` as required; refresh controller tests + docs; rerun verification.
    - _Commands:_ `./gradlew --no-daemon :rest-api:test --tests "*OcraCredentials*"`, `./gradlew spotlessApply check`.
    - _Exit:_ Stored dropdown + docs + verification logs updated (completed 2025-10-12).

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-025-01 | I1, I2, I3, I4, I7 | Label + placeholder alignment across protocols.
| S-025-02 | I6, I8 | HOTP seeded/inline catalogue parity.
| S-025-03 | I9, I10 | RFC suffix alignment.
| S-025-04 | I5, I10 | Documentation + verification logs.

## Analysis Gate
Completed 2025-10-12 after clarifications were captured in the spec; rerun only if preset scope changes again.

## Exit Criteria
- All increments logged with passing `./gradlew spotlessApply check` runs.
- Selenium suites for HOTP/TOTP/OCRA/FIDO2 dropdowns pass without label regressions.
- Operator docs, roadmap entry #21, and migration/session trackers updated.
- No outstanding clarifications in `docs/4-architecture/open-questions.md`.

## Follow-ups / Backlog
- None; directory renumbering remains blocked until every feature adopts the refreshed templates per `docs/migration_plan.md`.
EOF,workdir:.,max_output_tokens:6000}
