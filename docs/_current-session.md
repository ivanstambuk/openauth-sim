# Current Session Snapshot

> Keep this file up to date across all active chats. Treat it as the single source of truth for in-progress workstreams so every hand-off is instant. Replace the bracketed text and prune sections you do not need.

## Meta
- Date: 2025-10-26
- Primary branch: `main`
- Other active branches: none
- Last green commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationServiceTest"`, `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationServiceManualTest"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliVerboseTraceTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2EvaluationEndpointTest"` `--tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"` `--tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`, `./gradlew --no-daemon spotlessApply check` (all on 2025-10-26).
- Build status: WebAuthn generation traces now emit the expanded verbose steps across application/CLI/REST/UI; full `spotlessApply check` completed after the implementation tests passed on 2025-10-26. OpenAPI snapshots unchanged.
- Quality gate note: `./gradlew --no-daemon spotlessApply check` completed successfully on 2025-10-26 after documentation refresh (T3543); no OpenAPI snapshot changes were required.
- Commit workflow update (2025-10-25): `githooks/commit-msg` now runs gitlint only; agents must use automated tooling to supply a Conventional Commit message (with a `Spec impact:` body when docs and code change together). `./tools/codex-commit-review.sh` remains the default helper (defaults to `codex exec --model gpt-5-codex --sandbox read-only --skip-git-repo-check --color never`).
- Outstanding git state: Feature 035 docs/tasks updated through T3545 (trace restructure complete); Feature 036 tier helper groundwork remains the next focus.

## Workstream Summary
| Workstream | Status | Last Increment | Next Increment | Notes |
|------------|--------|----------------|----------------|-------|
| Feature 027 – Unified Credential Store Naming | In progress | T2704 (Documentation refresh and migration guidance) | TBD – Track operator adoption of the unified file; add migration FAQ if support requests surface | Factory/CLI/REST defaults anchored to `credentials.db`; legacy fallback checks removed, docs now instruct manual migration for existing stores. |
| Feature 028 – IDE Warning Remediation | In progress | T2810 (WebAuthn assertion lossy conversion warning) | — | Spec/plan/tasks added, Option B locked, TOTP constructors cleaned, WebAuthn attestation/REST metadata assertions updated; CLI/REST tests assert generated OTPs, Selenium suites verify inline/replay controls, full `spotlessApply check` passes; 2025-10-19 clarifications implemented (DTO extraction + SpotBugs annotation export); rest-api dependency lock refreshed to align `checker-qual` 3.51.1 with Gradle force. |
| Feature 029 – PMD Rule Hardening | In progress | T2903 (Ruleset expansion & baseline) | T2902 – Governance sync & backlog updates | PMD toolVersion bumped to 7.17.0 with dependency locks refreshed via `--write-locks`; legacy `AssignmentInOperand` findings in CLI `MaintenanceCli`, core `CborDecoder`/`SimpleJson`, and core-ocra `OcraReplayVerifierBenchmark` have been refactored and `./gradlew --no-daemon pmdMain pmdTest` now passes; NonExhaustiveSwitch added permanently with green `pmdMain pmdTest` + `spotlessApply check`. |
| Feature 034 – Unified Validation Feedback | In progress | I4 (Spotless/check closure) | I5 – Final review & handoff (if required) | HOTP inline/replay, TOTP replay, WebAuthn inline/attestation, and OCRA evaluate/replay flows now emit ResultCard messaging with Selenium coverage. `docs/2-how-to/use-ocra-operator-ui.md` covers the ResultCard behaviour and `./gradlew --no-daemon spotlessApply check` succeeded on 2025-10-22. |
| Feature 035 – Evaluate & Replay Audit Tracing | In progress | I23 – WebAuthn generation trace implementation | Follow-up: Feature 036 tier helpers | Generation trace builders now populate CLI/REST/UI verbose payloads with hashed client/authenticator/signature metadata; all targeted suites and `spotlessApply check` reran green on 2025-10-26. Tier-control work continues under Feature 036. |
| Feature 036 – Verbose Trace Tier Controls | Proposed | — | T3601 – Tier helper specification tests (red) | Spec/plan/tasks drafted 2025-10-25 to deliver shared tier helper and cross-protocol tagging; implementation yet to begin. |
| _Reminder_ |  |  |  | Keep this table limited to active workstreams; move completed features to the roadmap instead of tracking them here. |

> Open questions live exclusively in `docs/4-architecture/open-questions.md`; consult that log for any pending clarifications.

## Reference Links
- Roadmap entries: `docs/4-architecture/roadmap.md` (Workstreams 27, 31, 34)
- Additional spec: `docs/4-architecture/specs/feature-029-pmd-rule-hardening.md`
- Additional plan: `docs/4-architecture/feature-plan-029-pmd-rule-hardening.md`
- Additional tasks: `docs/4-architecture/tasks/feature-029-pmd-rule-hardening.md`
- Feature 034 spec/plan/tasks: `docs/4-architecture/specs/feature-034-unified-validation-feedback.md`, `docs/4-architecture/feature-plan-034-unified-validation-feedback.md`, `docs/4-architecture/tasks/feature-034-unified-validation-feedback.md`
- Feature 035 spec: `docs/4-architecture/specs/feature-035-evaluate-replay-audit-tracing.md`
- Quick reference: `docs/5-operations/session-quick-reference.md`

> Update this snapshot before ending each session and after significant context changes.
