# Feature 007 Tasks – EUDIW ISO/IEC 18013-5 (mdoc PID) Simulator

_Status: Placeholder_  
_Last updated: 2025-11-11_

> Keep this checklist aligned with the feature plan increments. Stage tests before implementation, record verification commands beside each task, and prefer bite-sized entries (≤90 minutes).
> When referencing requirements, keep feature IDs (`F-`), non-goal IDs (`N-`), and scenario IDs (`S-<NNN>-`) inside the same parentheses immediately after the task title (omit categories that do not apply).

Linked plan: `docs/4-architecture/features/007/plan.md`

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

## Notes / TODOs
- Legacy operator documentation suite preserved under `docs/4-architecture/features/new-010/legacy/007/`.
