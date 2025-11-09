# Current Session Snapshot

> Keep this file up to date across all active chats. Treat it as the single source of truth for in-progress workstreams so every hand-off is instant. Replace the bracketed text and prune sections you do not need.

- Date: 2025-11-09
- Primary branch: `main`
- Other active branches: none
- Last green commands: `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"` (2025-11-09 – provenance schema snapshot regenerated), `./gradlew --no-daemon --console=plain :application:test`, `./gradlew --no-daemon --console=plain :cli:test`, `./gradlew --no-daemon --console=plain :rest-api:test` (full EMV + EUDIW suites), `./gradlew --no-daemon --console=plain :ui:test`, `./gradlew --no-daemon --console=plain pmdMain pmdTest`, `./gradlew --no-daemon --console=plain spotlessApply check`, `node --test rest-api/src/test/javascript/emv/console.test.js`, and `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`. All commands completed successfully after copying `trace-provenance-example.json` into `rest-api/docs/test-vectors/emv-cap/`. Additional doc-only verification: `./gradlew --no-daemon spotlessApply check` (2025-11-09 – Implementation Drift Gate sweep) remained green after the roadmap/how-to updates.
- Build status: Feature 039’s Implementation Drift Gate is complete—console assets render all six provenance sections, REST/CLI/application layers honour `includeTrace` opt-outs, the canonical fixture now lives in both `docs/test-vectors` and `rest-api/docs/test-vectors`, the drift report is logged, and the aggregate Gradle gate is green. Feature 040 is unblocked and can resume at T4018 (console alias/history wiring + verbose builder consolidation) while Feature 041 planning remains unchanged.
- New workstream kickoff (2025-11-07): Feature 041 – Operator Console JavaScript Modularization & Test Harness now has a Draft specification, feature plan, and tasks checklist; roadmap entry #40 created and the spec remains open so future protocol additions can extend it.
- Clarifications (2025-11-01): EUDIW OpenID4VP simulator scope confirmed—remote OpenID4VP flows only; credential formats include SD-JWT VC plus ISO/IEC 18013-5 mdoc; align with HAIP profile; simulator covers verifier+wallet roles; adopt hybrid fixture strategy (synthetic now, ingest EU conformance vectors when available). 2025-11-06 update: wallet simulator recomputes SD-JWT disclosure hashes from supplied disclosures (Option A).
- Clarifications (2025-11-05): EMV/CAP operator Evaluate panel keeps inline mode active when a sample vector is selected; no automatic switch to stored credential mode.
- Quality gate note: Full pipeline `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check` completed 2025-11-05 (Jacoco aggregated report regenerated; branch coverage remains 2161/3087 ≈0.7000) alongside refreshed OpenAPI snapshots for the sanitised EMV credential directory schema.

## Workstream Summary
| Workstream | Status | Last Increment | Next Increment | Notes |
|------------|--------|----------------|----------------|-------|
| Feature 039 – EMV/CAP Simulation Services | In review | T3952 – Implementation Drift Gate | Owner acceptance hand-off | Drift Gate verifies spec R1–R10 coverage, dual `trace-provenance-example.json` fixtures stay mirrored, roadmap/how-to docs now carry the sync requirement, and `./gradlew --no-daemon spotlessApply check` remained green post-doc sweep. Awaiting owner sign-off before archiving the workstream. |
| Feature 026 – FIDO2/WebAuthn Attestation Support | In review | I45 – Stored credential secret sanitisation | Owner acceptance sign-off | Sanitisation merged (`bcaad35`): stored credential samples expose only signing key handles + `[stored-server-side]` placeholders; full suite green (2025-11-05). Awaiting owner confirmation to mark the workstream complete. |
| Feature 029 – PMD Rule Hardening | In progress | T2903 (Ruleset expansion & baseline) | T2902 – Governance sync & backlog updates | PMD toolVersion bumped to 7.17.0 with dependency locks refreshed via `--write-locks`; legacy `AssignmentInOperand` findings in CLI `MaintenanceCli`, core `CborDecoder`/`SimpleJson`, and core-ocra `OcraReplayVerifierBenchmark` have been refactored and `./gradlew --no-daemon pmdMain pmdTest` now passes; NonExhaustiveSwitch added permanently with green `pmdMain pmdTest` + `spotlessApply check`. |
| Feature 036 – Verbose Trace Tier Controls | Proposed | — | T3601 – Tier helper specification tests (red) | Spec/plan/tasks drafted 2025-10-25 to deliver shared tier helper and cross-protocol tagging; implementation yet to begin. |
| Feature 041 – Operator Console JS Modularization & Test Harness | Planning | New spec/plan/tasks seeded 2025-11-07 | T4101 – Console asset inventory & extraction notes | Workstream stays in Draft; upcoming increments will extract remaining inline scripts, build the Node harness, and integrate the aggregate Gradle test task before extending to every protocol. |
| Feature 040 – EUDIW OpenID4VP Simulator | In progress | T4018 – Deep-link & flag consolidation | I11 – Drift gate + final documentation | Validation mode (T4012/T4013) plus REST/CLI facades landed earlier this week; the latest S7 increments delivered fixture ingestion toggles (T4019/T4020), how-to guides/telemetry snapshot (T4021), trace dock parity (T4015b), multi-presentation result cards (T4016), and now alias-preserving deep links + history navigation with refreshed REST/CLI verbose traces (T4018). With Feature 039’s gate complete and provenance fixtures synced, resume at T4018 follow-ups (console alias/history wiring + verbose builder consolidation) and drive toward I11’s drift gate/documentation. |
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
