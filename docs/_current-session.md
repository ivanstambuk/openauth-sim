# Current Session Snapshot

> Keep this file up to date across all active chats. Treat it as the single source of truth for in-progress workstreams so every hand-off is instant. Replace the bracketed text and prune sections you do not need.

- Date: 2025-11-05
- Primary branch: `main`
- Other active branches: none
- Last green commands: `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check` (executed 2025-11-05 after I30 sanitisation landed).
- Build status: Feature 037 shared-secret polish (T3707) wrapped; Feature 038 remains complete with aligned UI previews and accessibility documentation. Feature 039 completed I30 stored credential secret sanitisation—REST summaries now emit digests + length metadata, UI masks consume the new fields, and stored evaluate/replay flows hydrate secrets server-side. Feature 026 (FIDO2/WebAuthn) stored sanitisation (I45/T2650) is implemented and awaiting owner acceptance. Feature 029 PMD hardening (T2902) continues in parallel.
- Handoff note (2025-11-05): Feature 037 remains ready to archive; Feature 038 stays closed after accessibility review. Feature 039 I30 + drift gate shipped (docs/spec/plan/tasks synced). Feature 026 sanitisation delivered; await owner sign-off to re-close. Feature 029 PMD hardening continues. Constitution update (Principle 6) remains in effect for drift-gate handling.
- Clarifications (2025-11-01): EUDIW OpenID4VP simulator scope confirmed—remote OpenID4VP flows only; credential formats include SD-JWT VC plus ISO/IEC 18013-5 mdoc; align with HAIP profile; simulator covers verifier+wallet roles; adopt hybrid fixture strategy (synthetic now, ingest EU conformance vectors when available).
- Latest closure: Feature 037 – Base32 Inline Secret Support closed 2025-11-01 after dynamic shared-secret messaging shipped and `./gradlew --no-daemon :rest-api:test :ui:test` passed. Feature 039 – EMV/CAP Simulation Services completed I30 stored credential sanitisation on 2025-11-05 with green REST/CLI/UI suites and refreshed OpenAPI snapshots. Feature 026 – FIDO2/WebAuthn Attestation Support (initial scope) accepted 2025-10-31 but re-opened 2025-11-05 for its sanitisation sweep. Feature 028 – IDE Warning Remediation closed 2025-10-30.
- Quality gate note: Full pipeline `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check` completed 2025-11-05 (Jacoco aggregated report regenerated; branch coverage remains 2161/3087 ≈0.7000) alongside refreshed OpenAPI snapshots for the sanitised EMV credential directory schema.
- Commit workflow update (2025-10-25): `githooks/commit-msg` now runs gitlint only; agents must use automated tooling to supply a Conventional Commit message (with a `Spec impact:` body when docs and code change together). `./tools/codex-commit-review.sh` remains the default helper (defaults to `codex exec --model gpt-5-codex --sandbox read-only --skip-git-repo-check --color never`).
- Outstanding git state: Feature 034/035 documentation sync prepared this session (spec/plan/tasks + roadmap + knowledge map); Feature 027 specs/plans/tasks marked complete; Feature 036 tier helper groundwork remains the next focus.

## Workstream Summary
| Workstream | Status | Last Increment | Next Increment | Notes |
|------------|--------|----------------|----------------|-------|
| Feature 039 – EMV/CAP Simulation Services | In progress | I33 – Implementation drift gate & acceptance review | Owner acceptance sign-off | Stored credential directory responses now expose digests + length metadata only; drift report captured in `feature-plan-039`, awaiting owner sign-off to close the feature. |
| Feature 026 – FIDO2/WebAuthn Attestation Support | In review | I45 – Stored credential secret sanitisation | Owner acceptance sign-off | Sanitisation merged (`bcaad35`): stored credential samples expose only signing key handles + `[stored-server-side]` placeholders; full suite green (2025-11-05). Awaiting owner confirmation to mark the workstream complete. |
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
