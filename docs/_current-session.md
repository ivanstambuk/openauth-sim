# Current Session Snapshot

> Keep this file up to date across all active chats. Treat it as the single source of truth for in-progress workstreams so every hand-off is instant. Replace the bracketed text and prune sections you do not need.

- Date: 2025-11-02
- Primary branch: `main`
- Other active branches: none
- Last green commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"` (2025-11-02), `./gradlew --no-daemon :rest-api:test` (2025-11-02), `./gradlew --no-daemon :ui:test` (2025-11-02), `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"` (2025-11-02), `./gradlew --no-daemon spotlessApply check` (2025-11-02).
- Build status: Feature 037 shared-secret polish (T3707) wrapped; Feature 038 remains complete with aligned UI previews and accessibility documentation. Feature 039 EMV/CAP simulation now includes T3908 operator UI integration (console tab live, presets wired, Selenium coverage added); Feature 029 PMD hardening (T2902) stays in flight as the other active increment.
- Handoff note (2025-11-02): Feature 037 remains ready to archive; Feature 038 stays closed after accessibility review, documentation sync, and full quality gate. Feature 039 ships persistence + seeding and now delivers the EMV/CAP operator console tab (T3908) with presets, trace toggle, and Selenium coverage; Feature 029 PMD hardening (T2902) remains the parallel focus.
- Clarifications (2025-11-01): EUDIW OpenID4VP simulator scope confirmed—remote OpenID4VP flows only; credential formats include SD-JWT VC plus ISO/IEC 18013-5 mdoc; align with HAIP profile; simulator covers verifier+wallet roles; adopt hybrid fixture strategy (synthetic now, ingest EU conformance vectors when available).
- Latest closure: Feature 037 – Base32 Inline Secret Support closed 2025-11-01 after dynamic shared-secret messaging shipped and `./gradlew --no-daemon :rest-api:test :ui:test` passed. Feature 026 – FIDO2/WebAuthn Attestation Support accepted 2025-10-31 after trust-anchor summaries were validated and the full Gradle suite reran. Feature 028 – IDE Warning Remediation closed 2025-10-30.
- Quality gate note: `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check` completed successfully on 2025-10-31 after converting `Base32SecretCodecTest` to JUnit assertions and syncing documentation.
- Commit workflow update (2025-10-25): `githooks/commit-msg` now runs gitlint only; agents must use automated tooling to supply a Conventional Commit message (with a `Spec impact:` body when docs and code change together). `./tools/codex-commit-review.sh` remains the default helper (defaults to `codex exec --model gpt-5-codex --sandbox read-only --skip-git-repo-check --color never`).
- Outstanding git state: Feature 034/035 documentation sync prepared this session (spec/plan/tasks + roadmap + knowledge map); Feature 027 specs/plans/tasks marked complete; Feature 036 tier helper groundwork remains the next focus.

## Workstream Summary
| Workstream | Status | Last Increment | Next Increment | Notes |
|------------|--------|----------------|----------------|-------|
| Feature 029 – PMD Rule Hardening | In progress | T2903 (Ruleset expansion & baseline) | T2902 – Governance sync & backlog updates | PMD toolVersion bumped to 7.17.0 with dependency locks refreshed via `--write-locks`; legacy `AssignmentInOperand` findings in CLI `MaintenanceCli`, core `CborDecoder`/`SimpleJson`, and core-ocra `OcraReplayVerifierBenchmark` have been refactored and `./gradlew --no-daemon pmdMain pmdTest` now passes; NonExhaustiveSwitch added permanently with green `pmdMain pmdTest` + `spotlessApply check`. |
| Feature 036 – Verbose Trace Tier Controls | Proposed | — | T3601 – Tier helper specification tests (red) | Spec/plan/tasks drafted 2025-10-25 to deliver shared tier helper and cross-protocol tagging; implementation yet to begin. |
| Feature 039 – EMV/CAP Simulation Services | In progress | T3908 (Operator console wiring) | T3909 – Documentation & full gate | MapDB persistence adapter + seeding service populate canonical fixtures; EMV/CAP operator tab now loads stored presets, include-trace toggle, and Selenium coverage with REST seeding hooked in. |
| Feature 040 – EUDIW OpenID4VP Simulator | Planning | Specification/plan/tasks refreshed with HAIP-aligned scope | T4001 – Fixture scaffolding & seed setup | Remote OpenID4VP simulator documented (DCQL, SD-JWT VC, mdoc, Trusted Authorities, HAIP encryption) with Evaluate/Replay tabs, inline/stored credential selectors, friendly Trusted Authority labels, ETSI TL/OpenID Federation ingestion, global verbose trace integration, and Generate/Validate flows; hybrid fixture strategy and facade integrations queued behind tests-first increments. |
| _Reminder_ |  |  |  | Keep this table limited to active workstreams; move completed features to the roadmap instead of tracking them here. |

> Open questions live exclusively in `docs/4-architecture/open-questions.md`; consult that log for any pending clarifications.

## Reference Links
- Roadmap entries: `docs/4-architecture/roadmap.md` (Workstreams 27, 34, 35)
- Additional spec: `docs/4-architecture/specs/feature-029-pmd-rule-hardening.md`
- Additional plan: `docs/4-architecture/feature-plan-029-pmd-rule-hardening.md`
- Additional tasks: `docs/4-architecture/tasks/feature-029-pmd-rule-hardening.md`
- Spec (Feature 040): `docs/4-architecture/specs/feature-040-eudiw-openid4vp-simulator.md`
- Plan (Feature 040): `docs/4-architecture/feature-plan-040-eudiw-openid4vp-simulator.md`
- Tasks (Feature 040): `docs/4-architecture/tasks/feature-040-eudiw-openid4vp-simulator.md`
- Quick reference: `docs/5-operations/session-quick-reference.md`

> Update this snapshot before ending each session and after significant context changes.
