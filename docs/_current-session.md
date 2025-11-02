# Current Session Snapshot

> Keep this file up to date across all active chats. Treat it as the single source of truth for in-progress workstreams so every hand-off is instant. Replace the bracketed text and prune sections you do not need.

- Date: 2025-11-02
- Primary branch: `main`
- Other active branches: none
- Last green commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.emv.cap.*"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCli*"`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.EmvCapReplayEndpointTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `./gradlew --no-daemon :ui:test`, and `./gradlew --no-daemon spotlessApply check` (2025-11-02, T3917 coverage follow-up).
- Build status: Feature 037 shared-secret polish (T3707) wrapped; Feature 038 remains complete with aligned UI previews and accessibility documentation. Feature 039 T3917 now harmonises the console verbose toggle and ships replay tests that restored Jacoco branch coverage (2161/3087 ≈0.7000), so the full `spotlessApply check` pipeline is green again. Feature 029 PMD hardening (T2902) stays in flight as the other active increment.
- Handoff note (2025-11-02): Feature 037 remains ready to archive; Feature 038 stays closed after accessibility review, documentation sync, and full quality gate. Feature 039 T3917 now includes stored replay coverage for omitted/false `includeTrace` paths and the quality gate is green; next increments focus on stored credential parity (T3918–T3920). Feature 029 PMD hardening (T2902) remains the parallel focus.
- Clarifications (2025-11-01): EUDIW OpenID4VP simulator scope confirmed—remote OpenID4VP flows only; credential formats include SD-JWT VC plus ISO/IEC 18013-5 mdoc; align with HAIP profile; simulator covers verifier+wallet roles; adopt hybrid fixture strategy (synthetic now, ingest EU conformance vectors when available).
- Latest closure: Feature 037 – Base32 Inline Secret Support closed 2025-11-01 after dynamic shared-secret messaging shipped and `./gradlew --no-daemon :rest-api:test :ui:test` passed. Feature 026 – FIDO2/WebAuthn Attestation Support accepted 2025-10-31 after trust-anchor summaries were validated and the full Gradle suite reran. Feature 028 – IDE Warning Remediation closed 2025-10-30. Feature 039 now has T3916 (verbose trace SHA-256 hashing) complete; T3917 (console verbose toggle harmonization) retains the remaining red Selenium assertions.
- Quality gate note: Targeted suites remain green – `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.emv.cap.*"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCli*"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.EmvCapReplayEndpointTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, and `./gradlew --no-daemon :ui:test` all passed on 2025-11-02, and the full `./gradlew --no-daemon spotlessApply check` now completes with Jacoco branches 2161/3087 (≈0.7000).
- Commit workflow update (2025-10-25): `githooks/commit-msg` now runs gitlint only; agents must use automated tooling to supply a Conventional Commit message (with a `Spec impact:` body when docs and code change together). `./tools/codex-commit-review.sh` remains the default helper (defaults to `codex exec --model gpt-5-codex --sandbox read-only --skip-git-repo-check --color never`).
- Outstanding git state: Feature 034/035 documentation sync prepared this session (spec/plan/tasks + roadmap + knowledge map); Feature 027 specs/plans/tasks marked complete; Feature 036 tier helper groundwork remains the next focus.

## Workstream Summary
| Workstream | Status | Last Increment | Next Increment | Notes |
|------------|--------|----------------|----------------|-------|
| Feature 039 – EMV/CAP Simulation Services | In progress | T3917 – Console verbose toggle harmonization (coverage restored) | T3918 – Operator UI stored evaluate parity → T3920 – CLI stored evaluate support | EMV/CAP evaluate/replay panels now defer to the global verbose toggle, stored replay tests cover omitted/false `includeTrace`, and the full `spotlessApply check` (Jacoco 2161/3087 branches ≈0.7000) is green. |
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
