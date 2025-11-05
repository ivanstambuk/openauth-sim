# Current Session Snapshot

> Keep this file up to date across all active chats. Treat it as the single source of truth for in-progress workstreams so every hand-off is instant. Replace the bracketed text and prune sections you do not need.

- Date: 2025-11-05
- Primary branch: `main`
- Other active branches: none
- Last green commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `./gradlew --no-daemon :ui:test`, `./gradlew --no-daemon spotlessApply check` (executed 2025-11-04 for I29 sample vector styling parity).
- Build status: Feature 037 shared-secret polish (T3707) wrapped; Feature 038 remains complete with aligned UI previews and accessibility documentation. Feature 039 advanced to I30 planning for stored credential secret sanitisation; Feature 026 (FIDO2/WebAuthn) has been re-opened for an equivalent sanitisation sweep (I45/T2650). Feature 029 PMD hardening (T2902) stays in flight as the other active increment (no change this session). CLI stored evaluation regression cleared 2025-11-05 by treating request `privateKey` payloads as optional and suppressing the stored verbose trace hash; `./gradlew --no-daemon :cli:test` now passes while REST/Selenium sanitisation coverage remains staged red.
- Handoff note (2025-11-05): Feature 037 remains ready to archive; Feature 038 stays closed after accessibility review, documentation sync, and full quality gate. Feature 039 shipped I29 and now targets I30 to strip EMV stored secrets from REST/UI responses; specification, plan, and tasks updated accordingly. Feature 026 was re-opened (I45/T2650) after confirming that the FIDO2 operator UI still receives private key material in stored mode—spec/plan/tasks now capture the sanitisation requirement. Feature 029 PMD hardening continues in parallel. Constitution update (Principle 6) remains in effect for drift-gate handling.
- Clarifications (2025-11-01): EUDIW OpenID4VP simulator scope confirmed—remote OpenID4VP flows only; credential formats include SD-JWT VC plus ISO/IEC 18013-5 mdoc; align with HAIP profile; simulator covers verifier+wallet roles; adopt hybrid fixture strategy (synthetic now, ingest EU conformance vectors when available).
- Latest closure: Feature 037 – Base32 Inline Secret Support closed 2025-11-01 after dynamic shared-secret messaging shipped and `./gradlew --no-daemon :rest-api:test :ui:test` passed. Feature 026 – FIDO2/WebAuthn Attestation Support (initial scope) accepted 2025-10-31 after trust-anchor summaries were validated and the full Gradle suite reran; workstream re-opened 2025-11-05 for stored credential sanitisation. Feature 028 – IDE Warning Remediation closed 2025-10-30. Feature 039 completed I29 sample vector styling parity and now tracks I30 sanitisation + drift gate.
- Quality gate note: Full pipeline `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check` completed 2025-11-04 (Jacoco aggregated report regenerated; branch coverage remains 2161/3087 ≈0.7000) alongside refreshed OpenAPI snapshots for the expanded trace schema.
- Commit workflow update (2025-10-25): `githooks/commit-msg` now runs gitlint only; agents must use automated tooling to supply a Conventional Commit message (with a `Spec impact:` body when docs and code change together). `./tools/codex-commit-review.sh` remains the default helper (defaults to `codex exec --model gpt-5-codex --sandbox read-only --skip-git-repo-check --color never`).
- Outstanding git state: Feature 034/035 documentation sync prepared this session (spec/plan/tasks + roadmap + knowledge map); Feature 027 specs/plans/tasks marked complete; Feature 036 tier helper groundwork remains the next focus.

## Workstream Summary
| Workstream | Status | Last Increment | Next Increment | Notes |
|------------|--------|----------------|----------------|-------|
| Feature 039 – EMV/CAP Simulation Services | In progress | I29 – Sample vector styling parity | I30 – Stored credential secret sanitisation | I30 will remove master key/CDOL1/IPB/ICC template/issuer data from REST summaries and operator UI stored modes; red tests plus sanitised digests land before rerunning the full pipeline. |
| Feature 026 – FIDO2/WebAuthn Attestation Support | In progress | I45 – Sanitisation coverage staged (red) | I45 – Implement stored credential sanitisation | Re-opened to strip authenticator private keys from stored credential/sample responses, update operator UI to masked placeholders, and adjust backend services to keep signing keys server-side. CLI stored evaluation now succeeds without request-side secrets (`:cli:test` green 2025-11-05); REST `WebAuthnCredentialSanitisationTest` and Selenium stored-evaluate flows now pass locally after sanitisation, pending full-suite validation. |
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
