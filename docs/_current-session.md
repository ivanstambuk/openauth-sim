# Current Session Snapshot

> Keep this file up to date across all active chats. Treat it as the single source of truth for in-progress workstreams so every hand-off is instant. Replace the bracketed text and prune sections you do not need.

## Meta
- Date: 2025-10-19
- Primary branch: `main`
- Other active branches: none
- Last green commands: `./gradlew spotlessApply check` (2025-10-19); `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationGenerationProducesDeterministicPayload"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.OcraOperatorUiSeleniumTest.inlinePolicyPresetEvaluatesSuccessfully"` (all completed 2025-10-18 unless noted).
- Quality gate note: Full pipeline (`spotlessApply check`) plus targeted REST/UI Selenium suites rerun 2025-10-18; all tasks exited green after re-running a flaky Ocra Selenium scenario.
- Outstanding git state: No unstaged code changes; documentation updates landed during prior increments.

## Workstream Summary
| Workstream | Status | Last Increment | Next Increment | Notes |
|------------|--------|----------------|----------------|-------|
| Feature 026 – FIDO2/WebAuthn Attestation Support | In progress | T2628 (Shared private-key parser integration) | T2628 – Stack attestation key textareas, surface pretty-printed JWK presets, and prune legacy Base64URL code paths; ensure manual attestation labels reflect JWK/PEM-only support after removing `attestationId` | Parser shared across core/application/CLI/REST; Option B decisions locked to require JWK or PEM inputs and render presets as multi-line JWK JSON ahead of UI/docs/test updates. |
| Feature 027 – Unified Credential Store Naming | In progress | T2704 (Documentation refresh and migration guidance) | TBD – Coordinate fallback deprecation timeline after telemetry confirms unified default adoption | Factory/CLI/REST defaults updated to `credentials.db`, docs refreshed with legacy fallback notes; monitoring telemetry before retiring legacy probes. |
| Feature 028 – IDE Warning Remediation | In progress | T2806 (Quality gate verification) | — | Spec/plan/tasks added, Option B locked, TOTP constructors cleaned, WebAuthn attestation/REST metadata assertions updated; CLI/REST tests assert generated OTPs, Selenium suites verify inline/replay controls, and full `spotlessApply check` now passes. |

## Active TODOs / Blocking Items
- [ ] T2618 – Core Manual input source scaffolding (pending kickoff once Manual-mode clarifications are fully consumed in the spec).

> Open questions live exclusively in `docs/4-architecture/open-questions.md`; consult that log for any pending clarifications.

## Reference Links
- Roadmap entry: `docs/4-architecture/roadmap.md` (Workstream 21)
- Specification: `docs/4-architecture/specs/feature-026-fido2-attestation-support.md`
- Feature plan: `docs/4-architecture/feature-plan-026-fido2-attestation-support.md`
- Tasks checklist: `docs/4-architecture/tasks/feature-026-fido2-attestation-support.md`
- Quick reference: `docs/5-operations/session-quick-reference.md`

> Update this snapshot before ending each session and after significant context changes.
