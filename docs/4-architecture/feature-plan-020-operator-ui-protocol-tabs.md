# Feature Plan 020 – Operator UI Protocol Tabs Expansion

_Status: Draft_
_Last updated: 2025-10-04_

## Objective
Expose HOTP, TOTP, and EUDI wallet protocol tabs on the operator console while keeping them as placeholders, and align roadmap planning with the new protocol scope.

Reference specification: `docs/4-architecture/specs/feature-020-operator-ui-protocol-tabs.md`.

## Success Criteria
- Console tab list reflects the required ordering (HOTP → TOTP → OCRA → EMV/CAP → FIDO2/WebAuthn → EUDIW OpenID4VP 1.0 → EUDIW ISO/IEC 18013-5 → EUDIW SIOPv2) and maintains accessible semantics (OPT-001, OPT-NFR-001).
- Selecting non-OCRA protocols surfaces placeholder messaging with no interactive forms, mirroring the current placeholder styling (OPT-002, OPT-NFR-002).
- Query-parameter routing accepts and restores each new protocol key without breaking existing OCRA tab handling (OPT-003).
- Roadmap table and milestones enumerate dedicated HOTP, TOTP, and EUDI wallet workstreams marked `Not started`, and the catch-all milestone is removed (OPT-004).
- `./gradlew spotlessApply check` passes after each implementation increment.

## Proposed Increments
- ☑ R2001 – Add failing Selenium/DOM assertions (or equivalent unit tests) for the expanded protocol tab order and placeholder panels, then run the relevant Gradle test task to confirm the new expectations fail.
- ☑ R2002 – Update the Thymeleaf template and console script to render the new tab order, placeholder panels, and protocol keys; update supporting tests and rerun `./gradlew :rest-api:test`.
- ☑ R2003 – Refresh roadmap (and knowledge map if needed) to reflect the new workstreams, remove the catch-all milestone, document changes, and rerun `./gradlew spotlessApply check`.

Each increment should complete within ≤10 minutes and end with a green build for the affected modules.

## Checklist Before Implementation
- [x] Specification created with clarifications recorded.
- [x] Open questions resolved and captured in spec (tracked in `docs/4-architecture/open-questions.md`).
- [x] Feature tasks drafted with test-first ordering.
- [x] Analysis gate rerun once plan/tasks align.

## Tooling Readiness
- `./gradlew :rest-api:test` covers console UI/JS; extend Selenium/DOM assertions within that task or accompanying unit tests.
- `./gradlew spotlessApply check` remains the final gate per increment.

## Notes
- 2025-10-04 – T2001 added Selenium assertions for HOTP/TOTP/EUDIW placeholders; `./gradlew :rest-api:test` initially red until template updates landed.
- 2025-10-04 – T2002 implemented the expanded tab skeleton and placeholders; Selenium regression suite now green.
- 2025-10-04 – T2003 refreshed roadmap + knowledge map and ran `./gradlew spotlessApply check`; build succeeded.
- 2025-10-04 – User updated desired tab ordering to place EMV/CAP and FIDO2/WebAuthn ahead of EUDIW entries; specs/tests will reflect the revised sequence.

## Analysis Gate
- 2025-10-04 – Checklist executed; specification, plan, tasks, and open questions aligned. No follow-up actions required.
