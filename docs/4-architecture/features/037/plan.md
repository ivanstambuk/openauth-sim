# Feature Plan 037 - Base32 Inline Secret Support

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-11 |
| Linked specification | `docs/4-architecture/features/037/spec.md` |
| Linked tasks | `docs/4-architecture/features/037/tasks.md` |

## Vision & Success Criteria
- Enable HOTP, TOTP, and OCRA inline flows to accept Base32 secrets without changing downstream persistence or telemetry.
- Provide consistent tooling across REST, CLI, and UI by centralising Base32 conversion in a shared helper.
- Keep documentation, knowledge map, and governance artefacts in sync so Base32 workflows stay self service.
- Exit criteria: `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check` stays green with updated fixtures and documentation.

## Scope Alignment
- **In scope:** Encoding helper, REST DTO/service updates, CLI flag wiring, operator UI unified textarea + toggle, documentation/RUNBOOK updates, verification log maintenance.
- **Out of scope:** Stored credential schema changes, QR or `otpauth://` generation, non-OTP protocol modifications.

## Dependencies & Interfaces
- Existing inline HOTP/TOTP/OCRA flows and Selenium coverage.
- Picocli command definitions and help text.
- Operator console shared JS modules (`secret-fields.js`).
- Telemetry adapters to ensure secrets remain hex only.

## Assumptions & Risks
- **Assumptions:** OTP modules already expose inline DTOs; telemetry sanitisation is enforced at helper boundaries; QA fixtures cover both success and validation paths.
- **Risks:**
  - Performance regression when converting large secrets -> Mitigated via helper unit tests and reused HOTP/TOTP benchmarks.
  - UI desync between hex and Base32 fields -> Avoided by consolidating into a single textarea plus toggle.
  - Documentation drift -> Addressed by final increment (I5/I7) and analysis gate review.

## Implementation Drift Gate
- Evidence collected 2025-11-01:
  - Helper-to-facade trace recorded in knowledge map and plan appendix.
  - JSON diffs showing Base32 success and validation errors for HOTP and OCRA inline requests.
  - Selenium screenshots proving toggle + dynamic hint behaviour.
  - Telemetry sample verifying secrets remain hex only.
- Gate outcome: no divergences; roadmap and knowledge map updated before marking feature complete.

## Increment Map
1. **I1 - Encoding helper and HOTP/TOTP REST scaffolding (T-037-01, T-037-02)**
   - _Goal:_ Implement Base32 to hex helper, add `sharedSecretBase32` fields to HOTP/TOTP inline DTOs, and refresh OpenAPI hints.
   - _Preconditions:_ Clarifications approved; inline DTOs already exist.
   - _Steps:_ Build helper, add validation, wire into HOTP/TOTP services, write unit + REST tests.
   - _Commands:_ `./gradlew --no-daemon :core:test :rest-api:test`.
   - _Exit:_ 2025-10-31 - Helper shipped, HOTP/TOTP DTOs accept Base32, OpenAPI snapshot updated.
2. **I2 - OCRA REST integration (T-037-02)**
   - _Goal:_ Extend OCRA evaluate/verify inline requests and services.
   - _Steps:_ Add Base32 fields, validation, tests for success/error cases.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test`.
   - _Exit:_ 2025-10-31 - OCRA REST suites cover Base32 success + invalid paths.
3. **I3 - CLI flag support (T-037-03)**
   - _Goal:_ Add Base32 options to HOTP/TOTP/OCRA CLI commands with exclusivity enforcement.
   - _Steps:_ Introduce Picocli options, reuse helper, update help text, run CLI tests.
   - _Commands:_ `./gradlew --no-daemon :cli:test`.
   - _Exit:_ 2025-10-31 - CLI tests green with Base32 flags.
4. **I4 - Operator console UX & Selenium coverage (T-037-04)**
   - _Goal:_ Sync Base32 and hex inputs across inline panels, update Selenium tests.
   - _Steps:_ Implement shared JS helpers, update UI forms, refresh Selenium flows.
   - _Commands:_ `./gradlew --no-daemon :ui:test :rest-api:test`.
   - _Exit:_ 2025-10-31 - Selenium suites cover Base32 paths across all protocols.
5. **I5 - Documentation & knowledge map sync (T-037-05)**
   - _Goal:_ Update how tos, knowledge map, session snapshot, and run quality gate.
   - _Steps:_ Document Base32 usage, capture telemetry notes, run full Gradle gate.
   - _Commands:_ `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`.
   - _Exit:_ 2025-10-31 - Docs updated and quality gate recorded.
6. **I6 - Unified shared secret textarea (T-037-04)**
   - _Goal:_ Replace dual inputs with a single textarea + toggle, wire validation messaging.
   - _Steps:_ Update UI components, add toggle logic, extend Selenium coverage.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test :ui:test`.
   - _Exit:_ 2025-11-01 - Unified textarea live in all inline panels.
7. **I7 - Shared secret message consolidation (T-037-04)**
   - _Goal:_ Collapse hint/warning rows into a single dynamic message that swaps between hint and validation text.
   - _Steps:_ Extend JS helper, adjust CSS, refresh Selenium assertions.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test :ui:test`.
   - _Exit:_ 2025-11-01 - Dynamic messaging verified via Selenium.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-037-01 | I1 / T-037-01 | Helper normalisation + telemetry masking. |
| S-037-02 | I1-I2 / T-037-02 | REST DTO/service behaviour and validation. |
| S-037-03 | I3 / T-037-03 | CLI flag exclusivity + helper reuse. |
| S-037-04 | I4-I7 / T-037-04 | Operator UI textarea/toggle + messaging. |
| S-037-05 | I5 / T-037-05 | Documentation, knowledge map, gate updates. |

## Analysis Gate
- Status: Complete (2025-11-01).
- Findings: No drift; telemetry samples confirmed redaction, and knowledge map references were updated. Future UI work should reuse the shared textarea helper to avoid regressions.

## Exit Criteria
- Base32 helper + mutual exclusivity enforcement implemented across REST/CLI/UI.
- Telemetry and persistence remain hex only.
- Documentation and knowledge map updated.
- Full Gradle gate passes with refreshed snapshots and Selenium evidence.

## Follow-ups / Backlog
- Monitor upcoming UX features for inline secret presets to ensure they reuse the helper.
- None otherwise; feature closed after template migration.
