# Feature 020 – Operator UI Multi-Protocol Tabs

_Status: Draft_
_Last updated: 2025-10-04_

## Overview
Expand the operator console at `/ui/console` with clickable HOTP, TOTP, and EUDI wallet protocol tabs (OpenID4VP 1.0, ISO/IEC 18013-5, SIOPv2) ahead of the existing OCRA, FIDO2/WebAuthn, and EMV/CAP entries. The new tabs mirror the current placeholder behaviour—signalling upcoming support without exposing functional flows—while preserving the query-parameter routing and dark-theme layout introduced in Feature 017.

## Clarifications
- 2025-10-04 – New HOTP and TOTP tabs must be interactive but surface placeholder messaging only; no HOTP/TOTP flows ship in this feature (user directive).
- 2025-10-04 – Tabs must appear in the order HOTP → TOTP → OCRA → EMV/CAP → FIDO2/WebAuthn → EUDIW OpenID4VP 1.0 → EUDIW ISO/IEC 18013-5 → EUDIW SIOPv2 (user directive).
- 2025-10-04 – Each EUDI wallet protocol receives its own top-level tab (OpenID4VP 1.0, ISO/IEC 18013-5, SIOPv2) with placeholder content and participates in the existing query-parameter routing (user directive).
- 2025-10-04 – The roadmap must promote each HOTP, TOTP, and EUDI wallet protocol into its own numbered workstream marked `Not started`, replacing the prior catch-all "non-OCRA credential protocols" milestone (user directive).

## Functional Requirements
| ID | Requirement | Acceptance Signal |
|----|-------------|-------------------|
| OPT-001 | Render HOTP, TOTP, OCRA, EMV/CAP, FIDO2/WebAuthn, EUDIW OpenID4VP 1.0, EUDIW ISO/IEC 18013-5, and EUDIW SIOPv2 tabs in that exact order. | Selenium or DOM assertions confirm the tab list order and accessible labelling. |
| OPT-002 | Selecting any new non-OCRA tab shows a placeholder panel (no functional forms) styled consistently with existing placeholders. | UI tests verify the panel renders placeholder copy and no HOTP/TOTP/EUDI forms appear. |
| OPT-003 | Query-parameter routing recognises the new protocol keys (`hotp`, `totp`, `eudi-openid4vp`, `eudi-iso18013-5`, `eudi-siopv2`) so deep links restore the correct tab state. | Browser history/navigation tests confirm URL state updates and restores each tab without errors. |
| OPT-004 | Roadmap updates create individual workstreams for HOTP, TOTP, EUDIW OpenID4VP 1.0, EUDIW ISO/IEC 18013-5, and EUDIW SIOPv2, each marked `Not started`, and remove the legacy catch-all milestone. | Roadmap review shows new entries with dedicated numbering and the previous aggregate milestone absent. |

## Non-Functional Requirements
| ID | Requirement | Target |
|----|-------------|--------|
| OPT-NFR-001 | Accessibility | Preserve keyboard focus order, aria attributes, and placeholder tab semantics for all new entries. |
| OPT-NFR-002 | Consistency | Placeholder messaging and styling for HOTP, TOTP, and EUDIW tabs matches the existing FIDO2/EMV design system. |

## Test Strategy
- Extend existing Selenium/DOM assertions to cover tab ordering and query-parameter routing for the new protocols.
- Exercise history navigation and deep-link scenarios for each new protocol key using integration or end-to-end tests where feasible.

## Out of Scope
- Implementing actual HOTP, TOTP, or EUDI wallet flows.
- Wiring telemetry, REST, or persistence layers for the new protocols.

## Verification
- UI and routing tests pass with the expanded protocol tab set.
- `./gradlew spotlessApply check` remains green after UI and documentation updates.
- Roadmap shows discrete HOTP/TOTP/EUDI wallet workstreams with `Not started` status.
