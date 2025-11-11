# Feature 020 – Operator UI Multi-Protocol Tabs

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/020/plan.md` |
| Linked tasks | `docs/4-architecture/features/020/tasks.md` |
| Roadmap entry | #9 – Operator Console Infrastructure |

## Overview
Expand the `/ui/console` shell so HOTP, TOTP, and the three EUDI wallet simulators (OpenID4VP 1.0, ISO/IEC 18013-5, SIOPv2)
appear alongside the existing OCRA, EMV/CAP, and FIDO2/WebAuthn tabs. The new entries remain placeholders that inherit
the dark theme, accessibility semantics, and query-parameter routing introduced in Feature 017, while roadmap docs are
updated to track each protocol as its own workstream.

## Clarifications
- 2025-10-04 – New HOTP and TOTP tabs must be interactive but show placeholder messaging only; no HOTP/TOTP forms ship in
  this feature.
- 2025-10-04 – Tabs must appear in the exact order HOTP → TOTP → OCRA → EMV/CAP → FIDO2/WebAuthn → EUDIW OpenID4VP 1.0 →
  EUDIW ISO/IEC 18013-5 → EUDIW SIOPv2.
- 2025-10-04 – Each EUDI wallet protocol requires its own top-level tab and participates in the existing
  query-parameter/historical routing.
- 2025-10-04 – Roadmap promotes HOTP, TOTP, and the three EUDI wallet simulators into individual numbered workstreams
  marked `Not started`, replacing the prior catch-all milestone.

## Goals
- G-020-01 – Surface HOTP, TOTP, and EUDI wallet protocol tabs in the console with consistent placeholder UX.
- G-020-02 – Ensure query-parameter routing/history supports the new protocol keys.
- G-020-03 – Reflect the additional protocols in roadmap/knowledge-map documentation.

## Non-Goals
- N-020-01 – Delivering functional HOTP/TOTP/EUDI flows or backend APIs.
- N-020-02 – Changing telemetry beyond existing placeholder events.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-020-01 | Render tabs in the mandated order (HOTP → TOTP → OCRA → EMV/CAP → FIDO2/WebAuthn → EUDIW OpenID4VP → EUDIW ISO/IEC 18013-5 → EUDIW SIOPv2) with accessible labels. | Selenium/DOM assertions confirm ordering and aria attributes. | Keyboard navigation cycles through tabs without skips. | Out-of-order tabs or missing aria labels fail tests. | n/a | Clarifications (2025-10-04). |
| FR-020-02 | Selecting any new non-OCRA tab shows a placeholder panel styled like existing placeholders; no forms or API calls trigger. | UI placeholder copy displays, and no network calls fire. | Observed via Selenium + JS console logs. | Placeholder missing or interactive forms appear. | n/a | Clarifications (2025-10-04). |
| FR-020-03 | Query-parameter routing/history recognises `protocol=hotp`, `totp`, `eudi-openid4vp`, `eudi-iso18013-5`, `eudi-siopv2`. | Deep-link visits restore the correct tab; history navigation maintains state. | Invalid keys fall back to defaults with warning logs. | Incorrect tab loads or history gets stuck. | n/a | Clarifications (2025-10-04). |
| FR-020-04 | Roadmap (and knowledge map if referenced) lists dedicated HOTP, TOTP, EUDIW OpenID4VP, ISO/IEC 18013-5, and SIOPv2 workstreams marked `Not started`; remove catch-all milestone. | Docs show new entries with statuses and references. | Doc review ensures milestone removed. | Missing entries or stale milestone flagged during review. | n/a | Clarifications (2025-10-04). |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-020-01 | Accessibility | Placeholder tabs must preserve keyboard focus order and aria attributes. | Selenium/axe checks across tabs. | Thymeleaf template, JS router. | Clarifications (2025-10-04). |
| NFR-020-02 | Visual consistency | Placeholder panels reuse the dark-theme layout + messaging. | Visual review + snapshot tests. | CSS tokens, console template. | Clarifications (2025-10-04). |

## UI / Interaction Mock-ups
```
+--------------------------------------------------------------------------------+
| Operator Console                                                                |
| [ HOTP ] [ TOTP ] [ OCRA ] [ EMV/CAP ] [ FIDO2/WebAuthn ] [ EUDIW OID4VP ] ...  |
|--------------------------------------------------------------------------------|
| Placeholder Panel                                                               |
|  Title: HOTP simulator (coming soon)                                            |
|  Body : “HOTP flows will reuse this console shell once Feature 00X ships.”      |
|--------------------------------------------------------------------------------|
```

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-020-01 | Tab ordering + placeholder panels render consistently.
| S-020-02 | Query-parameter routing/history handles new protocol keys.
| S-020-03 | Roadmap/knowledge map document discrete workstreams.

## Test Strategy
- **UI/Selenium:** Extend `OperatorConsoleUnificationSeleniumTest` (or equivalent) to assert tab ordering, placeholder
  copy, and query-parameter behaviour.
- **JS unit tests:** Update router tests to cover new protocol keys and fallbacks.
- **Docs:** Run `./gradlew spotlessApply check` after updating roadmap/knowledge map to keep formatting consistent.

## Interface & Contract Catalogue
### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-020-01 | `OperatorProtocolTab` enumerates available tabs (hotp, totp, ocra, emv, fido2, eudi-*). | rest-api (UI), JS router |
| DO-020-02 | `OperatorConsoleRoutingState` includes new protocol keys for query params/history. | rest-api, JS |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| — | — | No API changes; UI uses existing console endpoint. | |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| — | — | No CLI changes. |

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|-----------|---------------------------|
| — | — | No telemetry additions; navigation telemetry already exists. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-020-01 | `rest-api/src/test/resources/templates/ui/console/tab-order.html` (test fixture) | DOM snippet used by tests to verify ordering (if applicable). |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| UI-020-01 | HOTP placeholder tab | `protocol=hotp` or user clicks HOTP; shows “coming soon” copy.
| UI-020-02 | TOTP placeholder tab | As above for TOTP.
| UI-020-03 | EUDIW placeholder tabs | Each EUDIW protocol shows dedicated messaging and query-parameter integration.

## Telemetry & Observability
- No new telemetry; rely on existing navigation logs (`ui.console.navigation`).

## Documentation Deliverables
- Update roadmap (`docs/4-architecture/roadmap.md`) and knowledge map to reference the new workstreams.
- Note placeholder coverage in the current session snapshot and migration tracker.

## Fixtures & Sample Data
- Optional DOM fixture for tab ordering tests (see FX-020-01) if needed by Selenium/unit assertions.

## Spec DSL
```
domain_objects:
  - id: DO-020-01
    name: OperatorProtocolTab
    fields:
      - name: key
        type: enum[hotp,totp,ocra,emv,fido2,eudi-openid4vp,eudi-iso18013-5,eudi-siopv2]
  - id: DO-020-02
    name: OperatorConsoleRoutingState
    fields:
      - name: protocol
        type: string
        constraints: "must match OperatorProtocolTab.key"
cli_commands: []
telemetry_events: []
fixtures:
  - id: FX-020-01
    path: rest-api/src/test/resources/templates/ui/console/tab-order.html
ui_states:
  - id: UI-020-01
    description: HOTP placeholder panel
  - id: UI-020-02
    description: TOTP placeholder panel
  - id: UI-020-03
    description: EUDIW placeholder panels
```

## Appendix
_None._
