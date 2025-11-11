# Feature Plan 005 – CLI OCRA Operations

_Linked specification:_ `docs/4-architecture/features/005/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
- Deliver Picocli commands (`ocra import/list/show/delete/evaluate`) that manage OCRA descriptors via the existing persistence layer.
- Evaluation supports both credential-id lookup and inline inputs, matching REST/core OTP results.
- Validation, telemetry, and error reason codes mirror the REST feature set; documentation reflects the new CLI workflow.

## Scope Alignment
- **In scope:**
  - CLI command definitions, option parsing, and persistence integration.
  - Mutual exclusivity enforcement for evaluation (stored vs inline).
  - Telemetry/logging parity with constitution redaction rules.
  - Documentation updates (how-to, knowledge map, roadmap) covering CLI usage.
- **Out of scope:**
  - REST/UI changes, replay/audit features, new credential types, or authentication/rate limiting.

## Dependencies & Interfaces
- Picocli infrastructure already used by `MaintenanceCli`.
- `CredentialStoreFactory` + OCRA descriptor/persistence classes from Feature 001/002.
- RFC 6287 vector fixtures for OTP parity tests.
- `TelemetryContracts` formatting for CLI log output.

## Assumptions & Risks
- **Assumptions:** CLI runs in a trusted environment; persistence layer already configured locally.
- **Risks:**
  - _Telemetry leakage:_ mitigated with log capture tests verifying `sanitized=true`.
  - _User ergonomics:_ addressed by Picocli usage help and deterministic exit codes.
  - _Parity drift with REST:_ managed via shared fixtures/tests referencing REST reason codes.

## Implementation Drift Gate
- Evidence: spec/plan/tasks updates, CLI tests, telemetry snapshots, and documentation changes.
- Checklist ensures FR/NFR coverage, telemetry redaction, and doc sync before marking feature complete; status PASS as of 2025-09-28.

## Increment Map
1. **I1 – Clarifications + failing CLI tests (T0501–T0502)**
   - _Goal:_ Align docs/roadmap entries and stage failing Picocli tests for import/list/delete/evaluate flows.
   - _Preconditions:_ Spec outlines CLI goals; persistence adapter accessible.
   - _Steps:_ update roadmap, add tests for stored vs inline evaluation and telemetry logging.
   - _Commands:_ `./gradlew --no-daemon :cli:test --tests "*OcraCli*"`
   - _Exit:_ Tests red, clarifications recorded.
2. **I2 – Implement CLI handlers + telemetry (T0503)**
   - _Goal:_ Implement commands, persistence wiring, and telemetry/logging sanitisation.
   - _Preconditions:_ I1 tests exist.
   - _Steps:_ wire import/list/delete/evaluate commands to `CredentialStore`, enforce exclusivity, produce structured logs.
   - _Commands:_ `./gradlew --no-daemon :cli:test`
   - _Exit:_ Tests green, telemetry asserts pass.
3. **I3 – Documentation + final verification (T0504)**
   - _Goal:_ Update how-to docs/knowledge map, capture telemetry samples, and run spotless/check.
   - _Preconditions:_ I2 green.
   - _Steps:_ refresh docs, record telemetry snippets for doc inclusion, run spotless/check.
   - _Commands:_ `./gradlew --no-daemon spotlessApply check`
   - _Exit:_ Docs in sync, feature ready for hand-off.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S05-01 | I1–I2 / T0502–T0503 | Import command persists descriptors. |
| S05-02 | I1–I2 / T0502–T0503 | List/show commands with redacted metadata. |
| S05-03 | I1–I2 / T0502–T0503 | Delete command handles missing descriptors. |
| S05-04 | I1–I2 / T0502–T0503 | Evaluate command mutual exclusivity + OTP parity. |
| S05-05 | I2–I3 / T0503–T0504 | Telemetry/logging parity + documentation updates. |

## Analysis Gate
- Completed 2025-09-28 (PASS). Spec/plan/tasks aligned, tests-first ordering captured, dependencies enumerated, and `./gradlew --no-daemon :cli:test spotlessApply check` recorded.

## Exit Criteria
- `./gradlew --no-daemon :cli:test` and `./gradlew --no-daemon spotlessApply check` green.
- Documentation (how-to, knowledge map, roadmap) reflects CLI commands.
- Telemetry/log capture tests confirm sanitized output and reason codes.
- No outstanding open questions for CLI OCRA operations.

## Follow-ups / Backlog
1. Investigate CLI replay/audit commands (future feature).
2. Explore credential export/import packaging for migration between environments.
3. Consider shared config templates for CLI/REST persistence settings.
