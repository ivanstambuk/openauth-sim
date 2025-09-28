# Feature Plan 001 – Core Credential Domain Expansion

_Status: In progress_
_Last updated: 2025-09-28_

## Objective

Design and implement a protocol-aware credential domain in the `core` module, enabling the emulator to represent FIDO2/WebAuthn, OATH/OCRA, EU Digital Identity Wallet credential suites (ISO/IEC 18013-5 mDL, ISO/IEC 23220-2 mdoc, SD-JWT + W3C VC 2.0, OpenID4VCI/4VP/ISO/IEC 18013-7), EMV/CAP, and generic credentials with appropriate metadata, secret handling, and validation hooks. Each protocol will be isolated in its own package (`io.openauth.sim.core.credentials.{ocra|fido2|eudiw|emvcap}`) so we can deliver them sequentially, starting with OCRA.

Reference specification: `docs/4-architecture/specs/feature-001-core-credential-domain.md`.

## Success Criteria

- Typed credential descriptors exist for each targeted protocol and EUDIW issuance/presentation profile with clearly defined required/optional attributes, recorded within their dedicated packages.
- Secret material utilities support common encodings (raw, hex, Base64) and protocol-specific preprocessing (e.g., HMAC seeds for OATH, credential IDs for FIDO2).
- Validation logic rejects incomplete or inconsistent credential payloads and enforces lifecycle-stage constraints for EUDIW issuance vs presentation, implemented incrementally per protocol package.
- Unit tests cover creation, serialization, issuance (registration), and presentation (authentication) flows for each credential type, beginning with OCRA before expanding to others.
- Documentation updates summarise the domain model in `docs/1-concepts` and reference relevant specifications.

## Task Tracker

- Detailed execution steps live in `docs/4-architecture/tasks/feature-001-core-credential-domain.md`. Update that checklist as work progresses and mirror status changes here; current sequencing delivers the OCRA package first with other protocols queued.
- Map task outcomes back to the specification requirements (FR/NFR IDs) to maintain traceability; metadata capture for OCRA now covers FR-002 and FR-010.
- Record Gradle command outputs (`./gradlew spotlessApply check`) and analysis-gate results after each work session.
- 2025-09-27 – Phase 1/T004 landed: disabled OCRA unit-test skeleton in place; `./gradlew spotlessApply check` passed post-change.
- 2025-09-27 – Phase 1/T005 landed: disabled property-based secret material tests in place; `./gradlew spotlessApply check` executed successfully after commit preparation.
- 2025-09-27 – Phase 1/T006 landed: disabled ArchUnit guardrails prevent cross-package leakage; revisit in Phase 2 to re-enable once descriptors exist.
- 2025-09-27 – Clarification resolved: OCRA descriptors parse the suite during construction and store secrets using `SecretMaterial`, guiding the T007 implementation approach.
- 2025-09-27 – Phase 2/T007: descriptor parser + factory landed with OCRA suite coverage; `./gradlew spotlessApply check` succeeded (27s, configuration cache reused).
- 2025-09-27 – Phase 2/T008 delivered: Added `OcraCredentialFactory`, tightened descriptor validation error messaging, re-enabled the T004 unit suite, and confirmed `./gradlew spotlessApply check` success at 23:16Z while keeping T005/T006 disabled per plan.
- 2025-09-27 – Phase 2/T009 delivered: Introduced shared secret normalisation helpers covering RAW/HEX/Base64 inputs, updated descriptor/factory flows to consume canonical `SecretMaterial`, re-enabled the property-based suite from T005, and recorded `./gradlew spotlessApply check` success at 23:34Z.
- 2025-09-27 – Phase 2/T010 delivered: Added `CredentialCapability` and `CredentialRegistry` seeded with OCRA metadata and factory wiring, validated via registry tests, and recorded `./gradlew spotlessApply check` pending post-doc update.
- 2025-09-28 – Phase 3/T011 delivered: Introduced versioned credential record + persistence adapter interfaces, added OCRA descriptor round-trip tests, implemented the adapter, and captured `./gradlew spotlessApply check` success (2025-09-28T15:17:00Z, 33s, configuration cache reused).
- 2025-09-28 – Phase 3/T012 initiated: Wire `VersionedCredentialRecord` envelopes into `MapDbCredentialStore`, add upgrade hooks for future schema versions, and prove OCRA record migrations via targeted persistence tests.
- 2025-09-28 – Phase 3/T012 delivered: Migrated `MapDbCredentialStore` to versioned envelopes with OCRA-focused upgrade pipeline, added legacy schema-0 migration coverage, and logged `./gradlew spotlessApply check` success (2025-09-28T16:12:00Z, 33s, configuration cache reused).
- 2025-09-28 – Phase 3/T013 initiated: Add structured validation telemetry for OCRA flows, emitting redacted diagnostics suitable for future observability pipelines while keeping constitution logging rules intact.
- 2025-09-28 – Phase 3/T013 delivered: Structured debug telemetry added to OCRA validations with log capture tests; `./gradlew spotlessApply check` succeeded (2025-09-28T16:58:00Z, 18s, configuration cache reused).
- 2025-09-28 – Phase 4/T014 delivered: `docs/1-concepts/README.md` now includes the OCRA capability matrix, glossary, and telemetry reference; `./gradlew spotlessApply check` succeeded (2025-09-28T17:05:00Z, reuse configuration cache).
- 2025-09-28 – Phase 4/T015 initiated: Refresh knowledge map to highlight telemetry flows, documentation touchpoints, and pending protocol packages.
- 2025-09-28 – Phase 4/T015 delivered: Knowledge map now references the OCRA documentation/telemetry contract and flags pending protocol packages for future plans.
- 2025-09-28 – Phase 4/T016 initiated: Update roadmap status and capture lessons/self-review notes following the telemetry+documentation increments.
- 2025-09-28 – Phase 4/T016 delivered: Roadmap and action items refreshed (Workstream 1 now In progress, OCRA documentation noted); self-review captured in this plan.
- Phase 5/T017–T018 planned: Catalogue the RFC 6287 reference vectors (Appendix C Standard, Challenge/Response, Mutual Challenge/Response, Plain Signature suites) and introduce placeholder tests that currently assert `UnsupportedOperationException` until the OCRA response calculator ships in a later increment; vectors remain test-only fixtures sourced under the RFC’s Simplified BSD terms. citeturn3view0
- 2025-09-28 – Phase 5/T017–T018 progress: Added `OcraRfc6287VectorFixtures`, stubbed `OcraResponseCalculator` (throws `UnsupportedOperationException` until implemented), and introduced `OcraRfc6287PlaceholderTest` covering Standard, Counter/PIN, Mutual, and Signature suites with TODO markers to flip assertions when the helper lands; `./gradlew spotlessApply check` passed (≈59s, configuration cache reused).
- Phase 5/T019 planned: Implement `OcraResponseCalculator` to generate RFC 6287-compliant responses, replace placeholder assertions with real OTP comparisons, and extend telemetry checks to ensure runtime inputs remain redacted. citeturn1search0turn1search1turn1search5
- 2025-09-28 – Phase 5/T019–T020 delivered: Implemented `OcraResponseCalculator` using the RFC 6287 reference algorithm, corrected the 64-byte SEED constant, added session-information encoding for `Snnn` suites, replaced the placeholder suite with `OcraRfc6287ComplianceTest`, and verified `./gradlew spotlessApply check` (≈72s, configuration cache reused). citeturn1search0turn1search1turn1search5
- 2025-09-28 – Phase 5/T021 delivered: Ran the IETF draft vector generator logic with the standard 32-byte demo key, challenge `SESSION01`, and the published S064 pattern repeated to 64/128/256/512-byte payloads to derive OTPs (`17477202`, `18468077`, `77715695`, `05806151`); recorded parameters in spec/tasks, captured fixtures in `OcraRfc6287VectorFixtures`, and confirmed `./gradlew spotlessApply check` (≈62s, configuration cache reused). citeturn0search0turn0search5
- 2025-09-28 – Phase 5/T022 delivered: Extended the compliance suite to assert session-enabled suites expose the correct byte lengths, added redaction checks for session payloads, documented the behaviour in spec/tasks, and re-ran `./gradlew spotlessApply check` (≈27s, configuration cache reused). citeturn0search0turn0search5
- 2025-09-28 – Session coverage follow-up: Use the IETF OCRA Internet-Draft’s vector generator to add compliance fixtures for S128/S256 (and beyond) so tests cover all documented session lengths (S064, S128, S256, S512). citeturn0search0turn0search5

## Phase 4 – Next Increment (T016 Roadmap & Self-Review)

1. Update `docs/4-architecture/roadmap.md` to reflect Workstream 1 progress (T011–T015) and surface upcoming actions.
2. Capture lessons learned, test outcomes, and telemetry documentation notes within this feature plan and associated tasks.
3. Verify action items/follow-ups are current and archive completed TODOs.
4. Run `./gradlew spotlessApply check`, capture timing, and self-review before committing/pushing.

## Phase 5 – RFC 6287 Verification (T017–T018)

1. Extend the specification/tasks to cover integration of the official RFC 6287 (OCRA) reference vectors, documenting the source and expected OTP outcomes.
2. Add placeholder tests that load the vectors and currently assert `UnsupportedOperationException` (or similar) until the OCRA response calculator is implemented; document the TODO so the assertions can be flipped when the helper lands.
3. Capture the new coverage notes, Gradle command results (once tests go green with the future helper), and any follow-up actions within this plan.
4. Implement `OcraResponseCalculator` to assemble the OCRA message (suite, counter, challenge(s), PIN, session, timestamp) and compute the HMAC/HOTP response prescribed by RFC 6287.
5. Update the RFC vector tests to assert actual OTP values, add negative coverage for missing inputs, and validate telemetry remains redacted during execution.

## Dependencies

- Align with roadmap Workstreams 2–6 to ensure downstream modules consume the same types.
- Surface any new security/privacy considerations as ADRs.
- Complete the analysis gate checklist (`docs/5-operations/analysis-gate-checklist.md`) before implementation sessions begin.
- Shared persistence decision: MapDB store and core-managed cache are shared across all facades (2025-09-27).
- Crypto strategy decision: keep credential packages pure data/validation and integrate pluggable strategy interfaces for crypto execution (preferred option recorded 2025-09-27).
- Persistence evolution decision: adopt per-record schema versioning with a migration pipeline that upgrades records on read/write (preferred option recorded 2025-09-27).

## Self-Review Notes (2025-09-28)
- Telemetry logging remains at `Level.FINE` to avoid operator noise; tests assert payload redaction to prevent regressions.
- SpotBugs suppression limited to the test harness attaching log handlers; production code avoids suppressions.
- Roadmap/action items updated to reference completed documentation work, keeping future contributors oriented toward remaining plans (Workstreams 2–6, crypto ADRs).

## Analysis Gate

- 2025-09-27 – Checklist reviewed.
  - Specification objectives, FR/NFR tables, and clarifications are populated and current (including per-protocol packaging cadence).
  - Open questions log shows no blocking entries for this feature.
  - Plan references the correct spec/tasks and mirrors success criteria and dependencies.
- Task list covers FR-001–FR-009/NFRs with tests scheduled ahead of implementation work and ≤10 minute increments.
  - Planned work remains compliant with constitution principles (spec-first, clarification gate, test-first, documentation sync, dependency control).
  - Tooling readiness captured (`./gradlew spotlessApply check`) and this analysis record stored here.
  - Outcome: PASS – proceed to test design (Phase 1 tasks).

Update this plan as tasks progress: check off completed items, add new tasks, and note blockers.
