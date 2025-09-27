# Feature Plan 001 – Core Credential Domain Expansion

_Status: In progress_
_Last updated: 2025-09-27_

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

## Dependencies

- Align with roadmap Workstreams 2–6 to ensure downstream modules consume the same types.
- Surface any new security/privacy considerations as ADRs.
- Complete the analysis gate checklist (`docs/5-operations/analysis-gate-checklist.md`) before implementation sessions begin.
- Shared persistence decision: MapDB store and core-managed cache are shared across all facades (2025-09-27).
- Crypto strategy decision: keep credential packages pure data/validation and integrate pluggable strategy interfaces for crypto execution (preferred option recorded 2025-09-27).
- Persistence evolution decision: adopt per-record schema versioning with a migration pipeline that upgrades records on read/write (preferred option recorded 2025-09-27).

## Analysis Gate

- 2025-09-27 – Checklist reviewed.
  - Specification objectives, FR/NFR tables, and clarifications are populated and current (including per-protocol packaging cadence).
  - Open questions log shows no blocking entries for this feature.
  - Plan references the correct spec/tasks and mirrors success criteria and dependencies.
- Task list covers FR-001–FR-009/NFRs with tests scheduled ahead of implementation work and ≤10 minute increments.
  - Planned work remains compliant with constitution principles (spec-first, clarification gate, test-first, documentation sync, dependency control).
  - Tooling readiness captured (`./gradlew spotlessApply check`) and this analysis record stored here.
  - Outcome: PASS – proceed to test design (Phase 1 tasks).

## Open Questions

- What minimum metadata is required for each protocol to satisfy emulator goals?
- Should the domain expose cryptographic operations directly or delegate to strategy interfaces?
- How will persistence versioning be handled when credential schemas evolve?

Update this plan as tasks progress: check off completed items, add new tasks, and note blockers.
