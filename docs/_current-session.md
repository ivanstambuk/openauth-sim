# Current Session Snapshot

> Keep this file up to date across all active chats. Treat it as the single source of truth for in-progress workstreams so every hand-off is instant. Replace the bracketed text and prune sections you do not need.

- Date: 2025-11-01
- Primary branch: `main`
- Other active branches: none
- Last green commands: `./gradlew --no-daemon :cli:test` (2025-11-01), `./gradlew --no-daemon spotlessApply check` (2025-11-01), `./gradlew --no-daemon :rest-api:test` (2025-11-01), `./gradlew --no-daemon :rest-api:test :ui:test` (2025-11-01), `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check` (2025-11-01), `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"` (2025-10-31).
- Build status: Feature 037 shared-secret polish (T3707) wrapped; Feature 038 now fully complete with aligned UI previews and accessibility documentation, closing with a green full quality gate on 2025-11-01. Resume Feature 029 PMD work next unless priorities shift.
- Handoff note (2025-11-01): Feature 037 is complete and ready to archive; Feature 038 closed after accessibility review, documentation sync, and full quality gate. Next focus is Feature 029 PMD hardening (T2902) or Feature 036 planning as bandwidth allows.
- Latest closure: Feature 037 – Base32 Inline Secret Support closed 2025-11-01 after dynamic shared-secret messaging shipped and `./gradlew --no-daemon :rest-api:test :ui:test` passed. Feature 026 – FIDO2/WebAuthn Attestation Support accepted 2025-10-31 after trust-anchor summaries were validated and the full Gradle suite reran. Feature 028 – IDE Warning Remediation closed 2025-10-30.
- Quality gate note: `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check` completed successfully on 2025-10-31 after converting `Base32SecretCodecTest` to JUnit assertions and syncing documentation.
- Commit workflow update (2025-10-25): `githooks/commit-msg` now runs gitlint only; agents must use automated tooling to supply a Conventional Commit message (with a `Spec impact:` body when docs and code change together). `./tools/codex-commit-review.sh` remains the default helper (defaults to `codex exec --model gpt-5-codex --sandbox read-only --skip-git-repo-check --color never`).
- Outstanding git state: Feature 034/035 documentation sync prepared this session (spec/plan/tasks + roadmap + knowledge map); Feature 027 specs/plans/tasks marked complete; Feature 036 tier helper groundwork remains the next focus.

## Workstream Summary
| Workstream | Status | Last Increment | Next Increment | Notes |
|------------|--------|----------------|----------------|-------|
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
