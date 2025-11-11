# Feature Plan 038 - Evaluation Result Preview Table

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-11 |
| Linked specification | `docs/4-architecture/features/038/spec.md` |
| Linked tasks | `docs/4-architecture/features/038/tasks.md` |

## Vision & Success Criteria
- Surface Delta-ordered preview tables directly in REST payloads, CLI outputs, and operator UI result cards for HOTP/TOTP/OCRA evaluation flows.
- Provide accessible highlighting (accent bar + bold text) for the evaluated row (Delta = 0) while keeping telemetry/persistence hex only.
- Exit with a green aggregate build (`./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`) and updated documentation/knowledge map entries describing preview windows.

## Scope Alignment
- **In scope:** REST DTO/contracts, OpenAPI snapshot, CLI flag/output updates, operator UI forms/result cards, telemetry metadata, documentation and knowledge map sync, helper-text removal for preview window controls.
- **Out of scope:** Replay result cards, persistence schema changes, telemetry history visualisations, preview storage beyond the evaluation response.

## Dependencies & Interfaces
- Builds on Feature 037 inline-secret changes (shared DTOs).
- Uses existing application evaluation services and CLI formatting helpers.
- Operator UI table components and Selenium harness verify the result-card rendering.
- Telemetry adapters emit metadata for `otp.evaluate.preview` events.

## Assumptions & Risks
- **Assumptions:** Default window (0,0) is sufficient for most operators; preview assembly can reuse existing OTP calculators; telemetry sanitisation already removes OTP values.
- **Risks / Mitigations:**
  - Contract drift across facades -> mitigate via shared DTO definitions and snapshot tests.
  - UI accessibility regressions -> run targeted accessibility review, keep bold+accent combination.
  - CLI/help text confusion -> document new window flags and remove drift options simultaneously.

## Implementation Drift Gate
- Evidence captured 2025-11-08:
  - Mapping of FR-038-01..04 to REST/CLI/UI commits and corresponding test suites.
  - JSON payloads showing preview arrays for HOTP/OCRA plus CLI snapshot outputs.
  - Selenium screenshots demonstrating accent styling for offsets {0,0} and {2,4}.
  - Telemetry sample proving metadata-only emission.
- Gate outcome: no divergences; knowledge map + how-to entries updated; open questions closed.

## Increment Map
0. **I0 - Governance sync (<=30 min)**
   - _Goal:_ Record clarifications, update roadmap/current-session, confirm migration tracker context before implementation.
   - _Commands:_ Documentation only.
   - _Exit:_ 2025-10-31 - Clarifications logged; open questions cleared.
1. **I1 - REST schema scaffolding (T-038-01, S-038-01)**
   - _Goal:_ Add request `window` object, response `previews` array, remove evaluation drift fields, and update OpenAPI snapshot.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`.
   - _Exit:_ 2025-11-02 - REST DTOs updated with placeholders pending service wiring.
2. **I2 - Application + CLI propagation (T-038-02, S-038-02)**
   - _Goal:_ Expose preview windows via application services, replace drift options, render ordered tables in CLI text/JSON outputs.
   - _Commands:_ `./gradlew --no-daemon :application:test`, `./gradlew --no-daemon :cli:test`, `./gradlew --no-daemon spotlessApply check`.
   - _Exit:_ 2025-11-01 - CLI window flags live; drift options removed.
3. **I3 - Operator UI integration (T-038-03, S-038-03)**
   - _Goal:_ Render preview tables with accent styling, add Preview window offsets controls (stored + inline), refresh Selenium coverage.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test :ui:test`.
   - _Exit:_ 2025-11-01 - Result cards show previews for offsets {0,0} and {2,4}.
4. **I4 - Accessibility, telemetry, docs (T-038-04, S-038-04)**
   - _Goal:_ Complete accessibility review, document preview behaviour, update knowledge map/how-tos, capture telemetry evidence.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test`, `./gradlew --no-daemon spotlessApply check`.
   - _Exit:_ 2025-11-01 - Docs/telemetry updated; analysis gate run.
5. **I5 - Helper-text cleanup (T-038-05, S-038-05)**
   - _Goal:_ Remove redundant helper sentence beneath Preview window offsets controls and rerun the aggregate Gradle gate.
   - _Commands:_ `./gradlew --no-daemon spotlessApply check` (>=600s timeout).
   - _Exit:_ 2025-11-08 - Forms slimmed down; build green.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-038-01 | I1 / T-038-01 | REST contracts + default window behaviour. |
| S-038-02 | I2 / T-038-02 | Application/CLI propagation and ordering. |
| S-038-03 | I3 / T-038-03 | Operator UI controls + accent styling. |
| S-038-04 | I4 / T-038-04 | Documentation, telemetry, accessibility. |
| S-038-05 | I5 / T-038-05 | Helper-text cleanup, replay drift separation. |

## Analysis Gate
- Status: Complete (2025-11-01) after I4.
- Findings: No open questions; telemetry confirmed metadata-only emission; future work should extend previews to replay result cards.

## Exit Criteria
- REST/CLI/UI preview tables shipped with accessible highlight treatment and shared telemetry metadata.
- Drift inputs limited to replay forms; evaluation flows use preview window controls instead.
- Roadmap, knowledge map, and how-to guides updated; migration tracker logged.
- Aggregate Gradle gate green after helper-text cleanup.

## Follow-ups / Backlog
- Extend preview tables to replay result cards (tracked as a future feature).
- Consider persisted preview telemetry for audit once governance approves.
