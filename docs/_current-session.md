# Current Session Snapshot

> Keep this file up to date across all active chats. Treat it as the single source of truth for in-progress workstreams so every hand-off is instant. Replace the bracketed text and prune sections you do not need.

## Meta
- Date: 2025-10-24
- Primary branch: `main`
- Other active branches: none
- Last green commands: `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.*"` (2025-10-24), `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.*VerboseTraceTest"` (2025-10-24), `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliVerboseTraceTest"` (2025-10-24), `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2*Test"` (2025-10-24), `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"` (2025-10-24), `./gradlew --no-daemon spotlessApply check` (2025-10-24).
- Build status: FIDO2 verbose traces now publish canonical `alg`/`cose.alg` metadata, normalise relying party identifiers, and emit `rpId.canonical`/`rpIdHash.expected`/`rpIdHash.match` across application/CLI/REST; updated OpenAPI snapshots and targeted suites passed after the canonicalisation.
- Quality gate note: Full `./gradlew --no-daemon spotlessApply check` completed successfully on 2025-10-24 with aggregated branch coverage at 70.45 % (1 645/2 335).
- Outstanding git state: Feature 035 follow-ups limited to OCRA tier gating (T3528) and message integrity docs (T3529).

## Workstream Summary
| Workstream | Status | Last Increment | Next Increment | Notes |
|------------|--------|----------------|----------------|-------|
| Feature 027 – Unified Credential Store Naming | In progress | T2704 (Documentation refresh and migration guidance) | TBD – Track operator adoption of the unified file; add migration FAQ if support requests surface | Factory/CLI/REST defaults anchored to `credentials.db`; legacy fallback checks removed, docs now instruct manual migration for existing stores. |
| Feature 028 – IDE Warning Remediation | In progress | T2810 (WebAuthn assertion lossy conversion warning) | — | Spec/plan/tasks added, Option B locked, TOTP constructors cleaned, WebAuthn attestation/REST metadata assertions updated; CLI/REST tests assert generated OTPs, Selenium suites verify inline/replay controls, full `spotlessApply check` passes; 2025-10-19 clarifications implemented (DTO extraction + SpotBugs annotation export); rest-api dependency lock refreshed to align `checker-qual` 3.51.1 with Gradle force. |
| Feature 029 – PMD Rule Hardening | In progress | T2903 (Ruleset expansion & baseline) | T2902 – Governance sync & backlog updates | PMD toolVersion bumped to 7.17.0 with dependency locks refreshed via `--write-locks`; legacy `AssignmentInOperand` findings in CLI `MaintenanceCli`, core `CborDecoder`/`SimpleJson`, and core-ocra `OcraReplayVerifierBenchmark` have been refactored and `./gradlew --no-daemon pmdMain pmdTest` now passes; NonExhaustiveSwitch added permanently with green `pmdMain pmdTest` + `spotlessApply check`. |
| Feature 034 – Unified Validation Feedback | In progress | I4 (Spotless/check closure) | I5 – Final review & handoff (if required) | HOTP inline/replay, TOTP replay, WebAuthn inline/attestation, and OCRA evaluate/replay flows now emit ResultCard messaging with Selenium coverage. `docs/2-how-to/use-ocra-operator-ui.md` covers the ResultCard behaviour and `./gradlew --no-daemon spotlessApply check` succeeded on 2025-10-22. |
| Feature 035 – Evaluate & Replay Audit Tracing | In progress | T3533 (WebAuthn flag map & UV policy trace) | T3528 – OCRA truncation tier gating | FIDO2 verbose traces now emit full authenticator flag bit maps plus `userVerificationRequired` / `uv.policy.ok`; remaining scope covers OCRA tier gating plus message integrity docs. |
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
