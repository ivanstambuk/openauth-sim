# Current Session Snapshot

> Keep this file up to date across all active chats. Treat it as the single source of truth for in-progress workstreams so every hand-off is instant. Replace the bracketed text and prune sections you do not need.

- Date: 2025-11-08
- Primary branch: `main`
- Other active branches: none
- Last green commands: `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"` (2025-11-08 – Feature 039 T3939 branch/height row alignment), `./gradlew --no-daemon spotlessApply check` (2025-11-08 – reran with a 600 s timeout after the initial 300 s timeout stalled). Targeted FIDO2 Selenium guard: `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.storedCredentialDropdownUsesAlgorithmFirstLabels"` (2025-11-08).
- Build status: Feature 037 shared-secret polish (T3707) wrapped; Feature 038 remains complete with aligned UI previews and accessibility documentation. Feature 039 now covers inline preset hydration, the Transaction fieldset (T3937), Session key derivation fieldset (T3938), and the new Branch factor/Height pairing (T3939) so the derivation inputs stay on paired rows while only secrets hide in stored mode—targeted EMV Selenium + the full `spotlessApply check` run are green after re-running the quality gate with an extended timeout. The flaky FIDO2 preset-label Selenium test remains hardened via the sorted-label wait and is rerun when HtmlUnit flakes occur. Feature 026 (FIDO2/WebAuthn) stored sanitisation (I45/T2650) is implemented and awaiting owner acceptance. Feature 029 PMD hardening (T2902) continues in parallel. Feature 040 S4 T4011 implemented the HAIP `direct_post.jwt` encryption service (P-256 ECDH-ES + A128GCM, telemetry latency, invalid-request mapping) and restored the build to green; next increment focuses on validation-mode coverage (T4012).
- New workstream kickoff (2025-11-07): Feature 041 – Operator Console JavaScript Modularization & Test Harness now has a Draft specification, feature plan, and tasks checklist; roadmap entry #40 created and the spec remains open so future protocol additions can extend it.
- Handoff note (2025-11-05): Feature 037 remains ready to archive; Feature 038 stays closed after accessibility review. Feature 039 I30 + drift gate shipped (docs/spec/plan/tasks synced). Feature 026 sanitisation delivered; await owner sign-off to re-close. Feature 029 PMD hardening continues. Constitution update (Principle 6) remains in effect for drift-gate handling.
- Clarifications (2025-11-01): EUDIW OpenID4VP simulator scope confirmed—remote OpenID4VP flows only; credential formats include SD-JWT VC plus ISO/IEC 18013-5 mdoc; align with HAIP profile; simulator covers verifier+wallet roles; adopt hybrid fixture strategy (synthetic now, ingest EU conformance vectors when available). 2025-11-06 update: wallet simulator recomputes SD-JWT disclosure hashes from supplied disclosures (Option A).
- Clarifications (2025-11-05): EMV/CAP operator Evaluate panel keeps inline mode active when a sample vector is selected; no automatic switch to stored credential mode.
- Latest closure: Feature 037 – Base32 Inline Secret Support closed 2025-11-01 after dynamic shared-secret messaging shipped and `./gradlew --no-daemon :rest-api:test :ui:test` passed. Feature 039 – EMV/CAP Simulation Services completed T3933 stored mode secret hiding on 2025-11-06 with refreshed spec/plan/tasks, updated console assets, and green REST/UI/Selenium/JS suites plus full quality gate. Feature 026 – FIDO2/WebAuthn Attestation Support (initial scope) accepted 2025-10-31 but re-opened 2025-11-05 for its sanitisation sweep. Feature 028 – IDE Warning Remediation closed 2025-10-30.
- Quality gate note: Full pipeline `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check` completed 2025-11-05 (Jacoco aggregated report regenerated; branch coverage remains 2161/3087 ≈0.7000) alongside refreshed OpenAPI snapshots for the sanitised EMV credential directory schema.
- Commit workflow update (2025-10-25): `githooks/commit-msg` now runs gitlint only; agents must use automated tooling to supply a Conventional Commit message (with a `Spec impact:` body when docs and code change together). `./tools/codex-commit-review.sh` remains the default helper (defaults to `codex exec --model gpt-5-codex --sandbox read-only --skip-git-repo-check --color never`).
- Outstanding git state: Feature 034/035 documentation sync prepared this session (spec/plan/tasks + roadmap + knowledge map); Feature 027 specs/plans/tasks marked complete; Feature 036 tier helper groundwork remains the next focus.

## Workstream Summary
| Workstream | Status | Last Increment | Next Increment | Notes |
|------------|--------|----------------|----------------|-------|
| Feature 039 – EMV/CAP Simulation Services | In progress | T3939 – Branch factor & height row alignment | T3940 – TBD (await owner direction) | Evaluate/Replay panels now include inline preset hydration, the Transaction fieldset, Session key derivation grouping, and the paired Branch factor/Height row enforced by Selenium. Targeted EMV Selenium plus the full `spotlessApply check` (rerun after an initial timeout) remain green. |
| Feature 026 – FIDO2/WebAuthn Attestation Support | In review | I45 – Stored credential secret sanitisation | Owner acceptance sign-off | Sanitisation merged (`bcaad35`): stored credential samples expose only signing key handles + `[stored-server-side]` placeholders; full suite green (2025-11-05). Awaiting owner confirmation to mark the workstream complete. |
| Feature 029 – PMD Rule Hardening | In progress | T2903 (Ruleset expansion & baseline) | T2902 – Governance sync & backlog updates | PMD toolVersion bumped to 7.17.0 with dependency locks refreshed via `--write-locks`; legacy `AssignmentInOperand` findings in CLI `MaintenanceCli`, core `CborDecoder`/`SimpleJson`, and core-ocra `OcraReplayVerifierBenchmark` have been refactored and `./gradlew --no-daemon pmdMain pmdTest` now passes; NonExhaustiveSwitch added permanently with green `pmdMain pmdTest` + `spotlessApply check`. |
| Feature 036 – Verbose Trace Tier Controls | Proposed | — | T3601 – Tier helper specification tests (red) | Spec/plan/tasks drafted 2025-10-25 to deliver shared tier helper and cross-protocol tagging; implementation yet to begin. |
| Feature 041 – Operator Console JS Modularization & Test Harness | Planning | New spec/plan/tasks seeded 2025-11-07 | T4101 – Console asset inventory & extraction notes | Workstream stays in Draft; upcoming increments will extract remaining inline scripts, build the Node harness, and integrate the aggregate Gradle test task before extending to every protocol. |
| Feature 040 – EUDIW OpenID4VP Simulator | In progress | T4011 – direct_post.jwt encryption implementation | T4012 – Validation mode tests | DirectPostJwtEncryptionService now performs HAIP P-256 ECDH-ES + A128GCM encryption/decryption, derives verifier public coordinates from fixture private keys, records telemetry latency, and raises `invalid_request` problem-details on key/encryption failures. Next step adds validation-mode coverage before REST/CLI wiring.
| _Reminder_ |  |  |  | Keep this table limited to active workstreams; move completed features to the roadmap instead of tracking them here. |

> Open questions live exclusively in `docs/4-architecture/open-questions.md`; consult that log for any pending clarifications.

## Reference Links
- Roadmap entries: `docs/4-architecture/roadmap.md` (Workstreams 27, 34, 35, 40)
- Additional spec: `docs/4-architecture/specs/feature-029-pmd-rule-hardening.md`
- Additional plan: `docs/4-architecture/feature-plan-029-pmd-rule-hardening.md`
- Additional tasks: `docs/4-architecture/tasks/feature-029-pmd-rule-hardening.md`
- Spec (Feature 041): `docs/4-architecture/specs/feature-041-operator-console-js-test-harness.md`
- Plan (Feature 041): `docs/4-architecture/feature-plan-041-operator-console-js-test-harness.md`
- Tasks (Feature 041): `docs/4-architecture/tasks/feature-041-operator-console-js-test-harness.md`
- Spec (Feature 040): `docs/4-architecture/specs/feature-040-eudiw-openid4vp-simulator.md`
- Plan (Feature 040): `docs/4-architecture/feature-plan-040-eudiw-openid4vp-simulator.md`
- Tasks (Feature 040): `docs/4-architecture/tasks/feature-040-eudiw-openid4vp-simulator.md`
- Quick reference: `docs/5-operations/session-quick-reference.md`

> Update this snapshot before ending each session and after significant context changes.
