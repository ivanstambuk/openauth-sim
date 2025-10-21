# Current Session Snapshot

> Keep this file up to date across all active chats. Treat it as the single source of truth for in-progress workstreams so every hand-off is instant. Replace the bracketed text and prune sections you do not need.

## Meta
- Date: 2025-10-21
- Primary branch: `main`
- Other active branches: none
- Last green commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"` (2025-10-21) and `./gradlew --no-daemon spotlessApply check` (2025-10-21) validating the inline assertion private-key textarea update.
- Build status: Workspace green following the FIDO2 inline private-key textarea height adjustment; Feature 034 planning artefacts remain staged for upcoming validation improvements.
- Quality gate note: Latest Selenium/Spotless runs (2025-10-21) remain green after the inline private-key ergonomics update; global coverage unchanged.
- Outstanding git state: Feature 034 planning documents (spec/plan/tasks), roadmap entry, and session snapshot updates staged on `main` (2025-10-21); Feature 031 documentation refresh remains staged elsewhere.

## Workstream Summary
| Workstream | Status | Last Increment | Next Increment | Notes |
|------------|--------|----------------|----------------|-------|
| Feature 027 – Unified Credential Store Naming | In progress | T2704 (Documentation refresh and migration guidance) | TBD – Track operator adoption of the unified file; add migration FAQ if support requests surface | Factory/CLI/REST defaults anchored to `credentials.db`; legacy fallback checks removed, docs now instruct manual migration for existing stores. |
| Feature 028 – IDE Warning Remediation | In progress | T2810 (WebAuthn assertion lossy conversion warning) | — | Spec/plan/tasks added, Option B locked, TOTP constructors cleaned, WebAuthn attestation/REST metadata assertions updated; CLI/REST tests assert generated OTPs, Selenium suites verify inline/replay controls, full `spotlessApply check` passes; 2025-10-19 clarifications implemented (DTO extraction + SpotBugs annotation export); rest-api dependency lock refreshed to align `checker-qual` 3.51.1 with Gradle force. |
| Feature 029 – PMD Rule Hardening | In progress | T2903 (Ruleset expansion & baseline) | T2902 – Governance sync & backlog updates | PMD toolVersion bumped to 7.17.0 with dependency locks refreshed via `--write-locks`; legacy `AssignmentInOperand` findings in CLI `MaintenanceCli`, core `CborDecoder`/`SimpleJson`, and core-ocra `OcraReplayVerifierBenchmark` have been refactored and `./gradlew --no-daemon pmdMain pmdTest` now passes; NonExhaustiveSwitch added permanently with green `pmdMain pmdTest` + `spotlessApply check`. |
| Feature 034 – Unified Validation Feedback | Planned | — | I1 – Audit toggles | New UI workstream to reveal result cards on invalid responses and surface API messages across all ceremonies; spec/plan/tasks drafted 2025-10-21 pending implementation kickoff. |
| _Reminder_ |  |  |  | Keep this table limited to active workstreams; move completed features to the roadmap instead of tracking them here. |

> Open questions live exclusively in `docs/4-architecture/open-questions.md`; consult that log for any pending clarifications.

## Reference Links
- Roadmap entries: `docs/4-architecture/roadmap.md` (Workstreams 27, 31, 34)
- Additional spec: `docs/4-architecture/specs/feature-029-pmd-rule-hardening.md`
- Additional plan: `docs/4-architecture/feature-plan-029-pmd-rule-hardening.md`
- Additional tasks: `docs/4-architecture/tasks/feature-029-pmd-rule-hardening.md`
- Feature 034 spec/plan/tasks: `docs/4-architecture/specs/feature-034-unified-validation-feedback.md`, `docs/4-architecture/feature-plan-034-unified-validation-feedback.md`, `docs/4-architecture/tasks/feature-034-unified-validation-feedback.md`
- Quick reference: `docs/5-operations/session-quick-reference.md`

> Update this snapshot before ending each session and after significant context changes.
