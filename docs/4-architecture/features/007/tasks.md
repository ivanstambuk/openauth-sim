# Feature 007 Tasks – EUDIW ISO/IEC 18013-5 (mdoc PID) Simulator

| Field | Value |
|-------|-------|
| Status | Placeholder |
| Last updated | 2025-11-11 |
| Linked plan | `docs/4-architecture/features/007/plan.md` |

> Keep tasks ≤90-minute slices; record verification commands for each entry.

## Checklist
- [ ] T-007-01 – Fixture ingestion + deterministic DeviceResponse generator
  _Intent:_ Port HAIP PID fixtures, build deterministic DeviceResponse builder, expose baseline CLI/REST commands in noop mode.
  _Verification commands:_ `./gradlew --no-daemon :core:test`
  _Notes:_ Stage failing tests before implementation per Spec-as-Source rules.
- [ ] T-007-02 – Verifier validation harness
  _Intent:_ Implement signature/namespace validation plus Trusted Authority filtering.
  _Verification commands:_ `./gradlew --no-daemon :application:test`
  _Notes:_ Include negative fixtures for tampered payloads.
- [ ] T-007-03 – CLI/REST/UI integration and presets
  _Intent:_ Wire stored/preset/manual modes across all facades; add Selenium coverage.
  _Verification commands:_ `./gradlew --no-daemon :rest-api:test`, `./gradlew --no-daemon :ui:test`, `node --test rest-api/src/test/javascript/eudiw/*.test.js`
  _Notes:_ Ensure telemetry parity with Feature 006.
- [ ] T-007-04 – Documentation + telemetry
  _Intent:_ Update how-to guides, roadmap, knowledge map; confirm telemetry/trace docs.
  _Verification commands:_ `./gradlew --no-daemon spotlessApply check`
  _Notes:_ Capture command logs in `_current-session.md`.

## Verification Log
_Pending once implementation begins._

## Notes
- Legacy operator documentation suite preserved under `docs/4-architecture/features/new-010/legacy/007/`.
