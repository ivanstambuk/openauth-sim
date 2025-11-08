# Current Session Snapshot

> Keep this file up to date across all active chats. Treat it as the single source of truth for in-progress workstreams so every hand-off is instant. Replace the bracketed text and prune sections you do not need.

- Date: 2025-11-08
- Primary branch: `main`
- Other active branches: none
- Last green commands: `node --test rest-api/src/test/javascript/emv/console.test.js` (2025-11-08 – Feature 039 T3944 IV/IPB/IAD single-line inputs), `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"` (2025-11-08), first attempt at `./gradlew --no-daemon spotlessApply check` (2025-11-08) hit the 300 s CLI timeout, reran with a 600 s timeout and the pipeline finished green the same day, and the existing guards remain (`OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.TotpOperatorUiSeleniumTest.totpReplayResultColumnPreservesStatusBadge"` + `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.storedCredentialDropdownUsesAlgorithmFirstLabels"`).
- Build status: Feature 037 shared-secret polish (T3707) wrapped; Feature 038 remains complete with aligned UI previews and accessibility documentation. Feature 039 now includes inline preset hydration, the Transaction fieldset (T3937), Session key derivation fieldset (T3938), the Branch factor/Height pairing (T3939), the Session key master key/ATC width alignment (T3940/I43), Card configuration isolation (T3941/I44), refreshed Input-from-customer rows (T3942/I45), the single-line session row (T3943/I46), **and** the new IV/IPB/IAD single-line controls (T3944/I46) with updated templates/JS/Selenium coverage plus Node + Gradle verification. Node console + targeted Selenium runs plus the 600 s `./gradlew --no-daemon spotlessApply check` remain green (full `:rest-api:test` still needs the longer timeout but passes after rerunning the flaky TOTP badge Selenium test). Feature 026 (FIDO2/WebAuthn) stored sanitisation (I45/T2650) is implemented and awaiting owner acceptance. Feature 029 PMD hardening (T2902) continues in parallel. Feature 040 S5 T4013 delivered the validation-mode service (stored/inline VP Token selection, telemetry, Trusted Authority enforcement); next increment moves to S6 T4014 for REST/CLI contract tests.
- New workstream kickoff (2025-11-07): Feature 041 – Operator Console JavaScript Modularization & Test Harness now has a Draft specification, feature plan, and tasks checklist; roadmap entry #40 created and the spec remains open so future protocol additions can extend it.
- Clarifications (2025-11-01): EUDIW OpenID4VP simulator scope confirmed—remote OpenID4VP flows only; credential formats include SD-JWT VC plus ISO/IEC 18013-5 mdoc; align with HAIP profile; simulator covers verifier+wallet roles; adopt hybrid fixture strategy (synthetic now, ingest EU conformance vectors when available). 2025-11-06 update: wallet simulator recomputes SD-JWT disclosure hashes from supplied disclosures (Option A).
- Clarifications (2025-11-05): EMV/CAP operator Evaluate panel keeps inline mode active when a sample vector is selected; no automatic switch to stored credential mode.
- Quality gate note: Full pipeline `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check` completed 2025-11-05 (Jacoco aggregated report regenerated; branch coverage remains 2161/3087 ≈0.7000) alongside refreshed OpenAPI snapshots for the sanitised EMV credential directory schema.

## Workstream Summary
| Workstream | Status | Last Increment | Next Increment | Notes |
|------------|--------|----------------|----------------|-------|
| Feature 039 – EMV/CAP Simulation Services | In progress | T3944 – IV/IPB/IAD single-line inputs | Next EMV polish TBD (e.g., inline replay UX) | Evaluate/Replay panels now include inline preset hydration, the Transaction fieldset, Session key derivation grouping, paired Branch/Height rows, master key/ATC single-line row, card configuration isolation, the condensed customer-input grid, and the new IV/IPB/IAD inline inputs with updated Node + Selenium coverage. Remaining scope focuses on follow-up EMV/CAP polish (preview tweaks, future R5.x directives). |
| Feature 026 – FIDO2/WebAuthn Attestation Support | In review | I45 – Stored credential secret sanitisation | Owner acceptance sign-off | Sanitisation merged (`bcaad35`): stored credential samples expose only signing key handles + `[stored-server-side]` placeholders; full suite green (2025-11-05). Awaiting owner confirmation to mark the workstream complete. |
| Feature 029 – PMD Rule Hardening | In progress | T2903 (Ruleset expansion & baseline) | T2902 – Governance sync & backlog updates | PMD toolVersion bumped to 7.17.0 with dependency locks refreshed via `--write-locks`; legacy `AssignmentInOperand` findings in CLI `MaintenanceCli`, core `CborDecoder`/`SimpleJson`, and core-ocra `OcraReplayVerifierBenchmark` have been refactored and `./gradlew --no-daemon pmdMain pmdTest` now passes; NonExhaustiveSwitch added permanently with green `pmdMain pmdTest` + `spotlessApply check`. |
| Feature 036 – Verbose Trace Tier Controls | Proposed | — | T3601 – Tier helper specification tests (red) | Spec/plan/tasks drafted 2025-10-25 to deliver shared tier helper and cross-protocol tagging; implementation yet to begin. |
| Feature 041 – Operator Console JS Modularization & Test Harness | Planning | New spec/plan/tasks seeded 2025-11-07 | T4101 – Console asset inventory & extraction notes | Workstream stays in Draft; upcoming increments will extract remaining inline scripts, build the Node harness, and integrate the aggregate Gradle test task before extending to every protocol. |
| Feature 040 – EUDIW OpenID4VP Simulator | In progress | T4013 – Validation mode implementation | T4014 – REST & CLI contract tests | Validation service now handles stored vs inline VP Tokens, reuses TrustedAuthorityEvaluator + DirectPostJwtEncryptionService, and emits `oid4vp.response.validated/failed` telemetry. Next step stages MockMvc/Picocli red tests before wiring the REST/CLI surfaces.
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
