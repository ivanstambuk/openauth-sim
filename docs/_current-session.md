# Current Session Snapshot

> Keep this file up to date across all active chats. Treat it as the single source of truth for in-progress workstreams so every hand-off is instant. Replace the bracketed text and prune sections you do not need.

- Date: 2025-10-29
- Primary branch: `main`
- Other active branches: none
- Last green commands: `./gradlew --no-daemon :application:test :cli:test :rest-api:test spotlessApply check` (2025-10-28), `./gradlew --no-daemon jacocoAggregatedReport` (2025-10-28), `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplayStoredModeDisplaysPersistedPayloads"` (2025-10-28), `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.VerboseTracePayloadTest"` (2025-10-28), `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"` (2025-10-27).
- Build status: Synthetic PS256 packed fixture ships across docs and seeders, PS256 stored credentials hydrate automatically, and full CLI/REST/UI suites (including Selenium) are green.
- Quality gate note: `./gradlew --no-daemon :application:test :cli:test :rest-api:test spotlessApply check` completed successfully on 2025-10-28 alongside doc updates.
- Commit workflow update (2025-10-25): `githooks/commit-msg` now runs gitlint only; agents must use automated tooling to supply a Conventional Commit message (with a `Spec impact:` body when docs and code change together). `./tools/codex-commit-review.sh` remains the default helper (defaults to `codex exec --model gpt-5-codex --sandbox read-only --skip-git-repo-check --color never`).
- Outstanding git state: Feature 034/035 documentation sync prepared this session (spec/plan/tasks + roadmap + knowledge map); Feature 027 specs/plans/tasks marked complete; Feature 036 tier helper groundwork remains the next focus.

## Workstream Summary
| Workstream | Status | Last Increment | Next Increment | Notes |
|------------|--------|----------------|----------------|-------|
| Feature 026 – Stored Replay & UI Cleanup | Complete | I41 (Stored replay read-only styling parity) | — | Stored replay fields now inherit the muted read-only styling in the operator console, Selenium waits guard dropdown hydration, and PS256 fixture seeding keeps demo credentials ready. Ready for owner sign-off. |
| Feature 023 – TOTP Operator Support | In progress | T2355 (Auto-fill toggle rollout) | — | T2353–T2355 added TOTP “Use current Unix seconds” toggles + reset helpers across evaluate/replay modes, refreshed Selenium coverage, docs, and knowledge map; `./gradlew --no-daemon spotlessApply check` green on 2025-10-28. |
| Feature 028 – IDE Warning Remediation | In progress | T2810 (WebAuthn assertion lossy conversion warning) | — | Spec/plan/tasks added, Option B locked, TOTP constructors cleaned, WebAuthn attestation/REST metadata assertions updated; CLI/REST tests assert generated OTPs, Selenium suites verify inline/replay controls, full `spotlessApply check` passes; 2025-10-19 clarifications implemented (DTO extraction + SpotBugs annotation export); rest-api dependency lock refreshed to align `checker-qual` 3.51.1 with Gradle force. |
| Feature 029 – PMD Rule Hardening | In progress | T2903 (Ruleset expansion & baseline) | T2902 – Governance sync & backlog updates | PMD toolVersion bumped to 7.17.0 with dependency locks refreshed via `--write-locks`; legacy `AssignmentInOperand` findings in CLI `MaintenanceCli`, core `CborDecoder`/`SimpleJson`, and core-ocra `OcraReplayVerifierBenchmark` have been refactored and `./gradlew --no-daemon pmdMain pmdTest` now passes; NonExhaustiveSwitch added permanently with green `pmdMain pmdTest` + `spotlessApply check`. |
| Feature 036 – Verbose Trace Tier Controls | Proposed | — | T3601 – Tier helper specification tests (red) | Spec/plan/tasks drafted 2025-10-25 to deliver shared tier helper and cross-protocol tagging; implementation yet to begin. |
| _Reminder_ |  |  |  | Keep this table limited to active workstreams; move completed features to the roadmap instead of tracking them here. |

> Open questions live exclusively in `docs/4-architecture/open-questions.md`; consult that log for any pending clarifications.

## Reference Links
- Roadmap entries: `docs/4-architecture/roadmap.md` (Workstreams 27, 34, 35)
- Additional spec: `docs/4-architecture/specs/feature-029-pmd-rule-hardening.md`
- Additional plan: `docs/4-architecture/feature-plan-029-pmd-rule-hardening.md`
- Additional tasks: `docs/4-architecture/tasks/feature-029-pmd-rule-hardening.md`
- Quick reference: `docs/5-operations/session-quick-reference.md`

> Update this snapshot before ending each session and after significant context changes.
