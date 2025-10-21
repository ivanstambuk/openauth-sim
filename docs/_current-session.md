# Current Session Snapshot

> Keep this file up to date across all active chats. Treat it as the single source of truth for in-progress workstreams so every hand-off is instant. Replace the bracketed text and prune sections you do not need.

## Meta
- Date: 2025-10-21
- Primary branch: `main`
- Other active branches: none
- Last green commands: `./gradlew --no-daemon :application:test :cli:test :rest-api:test` (2025-10-21) validating stored attestation seeding, and `./gradlew --no-daemon spotlessApply check` (2025-10-21).
- Build status: Workspace green after stored-mode CLI/REST/UI wiring; dependency forces remain aligned with Gradle locks as of 2025-10-21.
- Quality gate note: Stored-mode increments (T2631–T2635) revalidated via the analysis gate on 2025-10-21; Jacoco branch coverage stays ≥0.70.
- Outstanding git state: CLI/REST/UI stored-attestation diffs staged on `main` pending documentation sync and final review; Feature 031 documentation refresh remains staged elsewhere.

## Workstream Summary
| Workstream | Status | Last Increment | Next Increment | Notes |
|------------|--------|----------------|----------------|-------|
| Feature 026 – FIDO2/WebAuthn Attestation Support | Complete | T2635 (Seed stored attestation credentials) | Merge readiness review | Stored-mode flow delivered CLI `seed-attestations`, REST `/api/v1/webauthn/attestations/seed`, metadata fetch endpoints, and operator UI selectors backed by MapDB; docs/roadmap/knowledge map refreshed and analysis gate closed 2025-10-21. |
| Feature 027 – Unified Credential Store Naming | In progress | T2704 (Documentation refresh and migration guidance) | TBD – Track operator adoption of the unified file; add migration FAQ if support requests surface | Factory/CLI/REST defaults anchored to `credentials.db`; legacy fallback checks removed, docs now instruct manual migration for existing stores. |
| Feature 028 – IDE Warning Remediation | In progress | T2810 (WebAuthn assertion lossy conversion warning) | — | Spec/plan/tasks added, Option B locked, TOTP constructors cleaned, WebAuthn attestation/REST metadata assertions updated; CLI/REST tests assert generated OTPs, Selenium suites verify inline/replay controls, full `spotlessApply check` passes; 2025-10-19 clarifications implemented (DTO extraction + SpotBugs annotation export); rest-api dependency lock refreshed to align `checker-qual` 3.51.1 with Gradle force. |
| Feature 029 – PMD Rule Hardening | In progress | T2903 (Ruleset expansion & baseline) | T2902 – Governance sync & backlog updates | PMD toolVersion bumped to 7.17.0 with dependency locks refreshed via `--write-locks`; legacy `AssignmentInOperand` findings in CLI `MaintenanceCli`, core `CborDecoder`/`SimpleJson`, and core-ocra `OcraReplayVerifierBenchmark` have been refactored and `./gradlew --no-daemon pmdMain pmdTest` now passes; NonExhaustiveSwitch added permanently with green `pmdMain pmdTest` + `spotlessApply check`. |
| Feature 031 – Legacy Entry-Point Removal | In progress | T3108 (Docs/analysis gate post-fetch + preset rename) | Confirm closure & changelog updates | WebAuthn presets now use W3C fixture identifiers, the legacy generator sample is gone, docs/knowledge map refreshed, HtmlUnit fetch polyfill enabled within Selenium suites, and targeted UI suites plus `spotlessApply check` reran 2025-10-19. |
| Feature 032 – Palantir Formatter Adoption | Complete | T3209 (Roadmap/changelog updates) | — | Palantir Java Format 2.78.0 is now enforced via Spotless + hooks, all JVM sources were reformatted, roadmap/changelog updated, and Feature 032 artefacts retained for traceability. |
| _Reminder_ |  |  |  | Keep this table limited to active workstreams; move completed features to the roadmap instead of tracking them here. |

> Open questions live exclusively in `docs/4-architecture/open-questions.md`; consult that log for any pending clarifications.

## Reference Links
- Roadmap entries: `docs/4-architecture/roadmap.md` (Workstreams 21 & 31)
- Specification: `docs/4-architecture/specs/feature-026-fido2-attestation-support.md`
- Feature plan: `docs/4-architecture/feature-plan-026-fido2-attestation-support.md`
- Tasks checklist: `docs/4-architecture/tasks/feature-026-fido2-attestation-support.md`
- Additional spec: `docs/4-architecture/specs/feature-029-pmd-rule-hardening.md`
- Additional plan: `docs/4-architecture/feature-plan-029-pmd-rule-hardening.md`
- Additional tasks: `docs/4-architecture/tasks/feature-029-pmd-rule-hardening.md`
- Additional spec: `docs/4-architecture/specs/feature-031-legacy-entrypoint-removal.md`
- Additional plan: `docs/4-architecture/feature-plan-031-legacy-entrypoint-removal.md`
- Additional tasks: `docs/4-architecture/tasks/feature-031-legacy-entrypoint-removal.md`
- Additional spec: `docs/4-architecture/specs/feature-032-palantir-formatter-adoption.md`
- Quick reference: `docs/5-operations/session-quick-reference.md`

> Update this snapshot before ending each session and after significant context changes.
