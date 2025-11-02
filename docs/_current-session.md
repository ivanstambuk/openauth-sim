# Current Session Snapshot

> Keep this file up to date across all active chats. Treat it as the single source of truth for in-progress workstreams so every hand-off is instant. Replace the bracketed text and prune sections you do not need.

- Date: 2025-11-02
- Primary branch: `main`
- Other active branches: none
- Last green commands: `./gradlew --no-daemon :ui:test`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, and `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.EmvCapReplayEndpointTest"` (2025-11-02, post-UI replay wiring). Targeted reruns of other module suites (`./gradlew --no-daemon --rerun-tasks :core:test :core-ocra:test :core-shared:test :application:test :infra-persistence:test :cli:test :rest-api:test :ui:test`) executed to refresh coverage inputs.
- Build status: Feature 037 shared-secret polish (T3707) wrapped; Feature 038 remains complete with aligned UI previews and accessibility documentation. Feature 039 replay scope now has T3915 closed—docs refreshed, knowledge map/roadmap synced, and the full Gradle quality gate passes with Jacoco branch coverage at 0.7000. Feature 029 PMD hardening (T2902) stays in flight as the other active increment.
- Handoff note (2025-11-02): Feature 037 remains ready to archive; Feature 038 stays closed after accessibility review, documentation sync, and full quality gate. Feature 039 replay work progressed through T3912 (REST replay endpoint/OpenAPI) with CLI (T3913) and UI (T3914) wiring up next, followed by T3915 documentation/quality gate. Feature 029 PMD hardening (T2902) remains the parallel focus.
- Clarifications (2025-11-01): EUDIW OpenID4VP simulator scope confirmed—remote OpenID4VP flows only; credential formats include SD-JWT VC plus ISO/IEC 18013-5 mdoc; align with HAIP profile; simulator covers verifier+wallet roles; adopt hybrid fixture strategy (synthetic now, ingest EU conformance vectors when available).
- Latest closure: Feature 037 – Base32 Inline Secret Support closed 2025-11-01 after dynamic shared-secret messaging shipped and `./gradlew --no-daemon :rest-api:test :ui:test` passed. Feature 026 – FIDO2/WebAuthn Attestation Support accepted 2025-10-31 after trust-anchor summaries were validated and the full Gradle suite reran. Feature 028 – IDE Warning Remediation closed 2025-10-30. Feature 039 replay scope now includes T3914 (operator UI replay tab) as of 2025-11-02; only T3915 (docs + quality sweep) remains.
- Quality gate note: Targeted suites are green – `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCliReplayTest"`, and `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.totp.TotpEvaluationServiceTest"` validated the new coverage. Full `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check` completed successfully with Jacoco branch coverage at 0.7000.
- Commit workflow update (2025-10-25): `githooks/commit-msg` now runs gitlint only; agents must use automated tooling to supply a Conventional Commit message (with a `Spec impact:` body when docs and code change together). `./tools/codex-commit-review.sh` remains the default helper (defaults to `codex exec --model gpt-5-codex --sandbox read-only --skip-git-repo-check --color never`).
- Outstanding git state: Feature 034/035 documentation sync prepared this session (spec/plan/tasks + roadmap + knowledge map); Feature 027 specs/plans/tasks marked complete; Feature 036 tier helper groundwork remains the next focus.

## Workstream Summary
| Workstream | Status | Last Increment | Next Increment | Notes |
|------------|--------|----------------|----------------|-------|
| Feature 039 – EMV/CAP Simulation Services | In progress | T3914 (Operator UI replay integration) | T3915 – Replay documentation & quality sweep | Replay parity expanded 2025-11-02: T3910 staged cross-facade red tests, T3911 delivered application replay orchestration, T3912 wired REST endpoint/snapshots, T3913 shipped the CLI replay command, and T3914 landed the operator UI replay tab plus Selenium coverage; final documentation + coverage clean-up (T3915) remains. |
| Feature 029 – PMD Rule Hardening | In progress | T2903 (Ruleset expansion & baseline) | T2902 – Governance sync & backlog updates | PMD toolVersion bumped to 7.17.0 with dependency locks refreshed via `--write-locks`; legacy `AssignmentInOperand` findings in CLI `MaintenanceCli`, core `CborDecoder`/`SimpleJson`, and core-ocra `OcraReplayVerifierBenchmark` have been refactored and `./gradlew --no-daemon pmdMain pmdTest` now passes; NonExhaustiveSwitch added permanently with green `pmdMain pmdTest` + `spotlessApply check`. |
| Feature 036 – Verbose Trace Tier Controls | Proposed | — | T3601 – Tier helper specification tests (red) | Spec/plan/tasks drafted 2025-10-25 to deliver shared tier helper and cross-protocol tagging; implementation yet to begin. |
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
