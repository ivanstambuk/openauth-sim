# Feature 008 Tasks – EUDIW SIOPv2 Wallet Simulator

_Status: Placeholder_  
_Last updated: 2025-11-11_

> Keep this checklist aligned with the feature plan increments. Stage tests before implementation, record verification commands beside each task, and prefer bite-sized entries (≤90 minutes).
> When referencing requirements, keep feature IDs (`F-`), non-goal IDs (`N-`), and scenario IDs (`S-<NNN>-`) inside the same parentheses immediately after the task title (omit categories that do not apply).

Linked plan: [docs/4-architecture/features/008/plan.md](docs/4-architecture/features/008/plan.md)

## Checklist
- [ ] T-008-01 – Authorization parsing + consent summary
  _Intent:_ Parse HAIP SIOPv2 requests, surface consent summary, stage failing tests.
  _Verification commands:_ `./gradlew --no-daemon :application:test`
  _Notes:_ Include encrypted and plaintext request fixtures.
- [ ] T-008-02 – Presentation composition builders
  _Intent:_ Build deterministic SD-JWT + mdoc presentation helpers reused by CLI/REST/UI.
  _Verification commands:_ `./gradlew --no-daemon :core:test`, `./gradlew --no-daemon :application:test`
  _Notes:_ Align fixtures with Feature 006 + Feature 007.
- [ ] T-008-03 – CLI/REST/UI wiring
  _Intent:_ Expose wallet operations across all facades with stored/preset/manual toggles.
  _Verification commands:_ `./gradlew --no-daemon :rest-api:test`, `./gradlew --no-daemon :ui:test`, `node --test rest-api/src/test/javascript/eudiw/*.test.js`
  _Notes:_ Capture telemetry/regression logs in `_current-session.md`.
- [ ] T-008-04 – Documentation & telemetry
  _Intent:_ Update how-to guides, roadmap, knowledge map; document telemetry & fixtures.
  _Verification commands:_ `./gradlew --no-daemon spotlessApply check`
  _Notes:_ Link to Feature 006/007 references for shared components.

## Verification Log
_Pending._

## Notes / TODOs
