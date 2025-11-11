# Feature 021 – Protocol Info Surface

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/021/plan.md` |
| Linked tasks | `docs/4-architecture/features/021/tasks.md` |
| Roadmap entry | #9 – Operator Console Infrastructure |

## Overview
Add a reusable protocol info drawer/modal to the operator console so every protocol tab exposes contextual guidance,
telemetry hints, and quick links without leaving `/ui/console`. The surface relies on schema-driven JSON data, a single
trigger button that follows the active tab, persistence via `localStorage`, and embeddable assets/documentation so other
facades (vanilla DOM or React wrappers) can adopt the same UX.

## Clarifications
- 2025-10-04 – Drawer width fixed at 520 px; opens from the right using the existing dark theme (owner directive).
- 2025-10-04 – Follow standard workflow guardrails: spec-first, ≤30 min increments, Gradle gate per increment (owner directive).
- 2025-10-04 – Use the supplied prompt as the UX/content baseline; all behaviours described there must ship (owner directive).
- 2025-10-04 – React wrapper requirement was dropped; provide vanilla DOM integration guidance instead (owner directive).
- 2025-10-04 – Size monitoring/minification is out of scope; just ship embeddable assets (owner directive).
- 2025-10-04 – Protocol info trigger is a single right-aligned button referencing the active protocol (owner directive).

## Goals
- G-021-01 – Deliver an accessible drawer/modal that surfaces per-protocol metadata, persists preferences, and supports
  keyboard navigation/reduced-motion users.
- G-021-02 – Provide schema-driven data ingestion, CustomEvent hooks, and embeddable assets/README so other apps reuse
  the Protocol Info surface.
- G-021-03 – Keep documentation/roadmap/knowledge map aligned with the new UX.

## Non-Goals
- N-021-01 – Modify protocol execution logic or backend APIs.
- N-021-02 – Add analytics pipelines beyond CustomEvents emitted in the browser.
- N-021-03 – Introduce size budgets or bundle tooling beyond current build scripts.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-021-01 | Render a single icon-only trigger (aria-label "Protocol info") to the right of the tablist; attributes reflect drawer state and tab focus. | Button appears in tablist header, toggles aria-expanded/aria-controls, inherits active protocol name in tooltip. | Selenium asserts aria attributes, focus order, and tab-to-button relationship. | Missing button or incorrect aria wiring blocks accessibility checks. | Emits `ui.protocolInfo.trigger.clicked` CustomEvent. | Clarifications. |
| FR-021-02 | Drawer opens with 520 px width, left edge anchored to trigger, animates respecting reduced-motion, and can pin in “drawer” or expand to modal. | Drawer slides in/out, respects prefers-reduced-motion CSS. | Axe/keyboard tests verify focus trap, ESC/Enter shortcuts. | Drawer fails to open/close, focus trap broken, or motion preference ignored. | Emits `ui.protocolInfo.opened/closed`. | Clarifications. |
| FR-021-03 | Content loads from `<script type="application/json" id="protocol-info-data">` schema (per protocol) and sanitises inline HTML (tensor-of escaped strings). | Data parser renders sections/accordions per schema. | Unit tests feed malformed schema to ensure fallback messaging. | Unescaped HTML or schema errors cause console warnings/test failures. | n/a | Spec baseline. |
| FR-021-04 | Persist drawer state (last protocol + open/closed + accordion scope) in `localStorage` with per-protocol keys; auto-open each protocol once. | LocalStorage entries created, repeated visits auto-open once, respect opt-out. | Unit + Selenium tests toggle storage, simulate first-time visits. | Persistence errors log warnings and disable auto-open. | Emits `ui.protocolInfo.persistence.updated`. | Spec baseline. |
| FR-021-05 | Swap drawer content when protocol tabs change without closing the drawer; trigger reflects current protocol. | With drawer open, clicking new tab updates content instantly. | Selenium ensures `aria-controls` and heading text change. | Drawer closes unexpectedly or shows stale content. | CustomEvent `ui.protocolInfo.protocol.changed`. | Clarifications. |
| FR-021-06 | Provide vanilla DOM integration guide + standalone demo referencing embeddable `protocol-info.css/js` bundles. | README + demo page illustrate mounting ProtocolInfo with sample schema. | Manual QA checklist validated during self-review. | Missing docs/demo fails review checklist. | n/a | Spec baseline. |
| FR-021-07 | Update knowledge map + roadmap to document the new UX and future protocol info workstreams. | Docs mention drawer/modal plus integration steps. | Spotless/doc lint passes after updates. | Docs lacking reference flagged in review. | n/a | Spec baseline. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-021-01 | Performance | Drawer open/close <16 ms average; auto-open work <50 ms on reference hardware. | Performance tests/manual profiling. | JS router/drawer code. | Clarifications. |
| NFR-021-02 | Security | No inline event handlers; `rel="noopener"` for external links; persisted data excludes secrets. | ESLint/CSP review + unit tests. | JS modules/templates. | Clarifications. |
| NFR-021-03 | Maintainability | Respect module boundaries (`core` untouched); no reflection/dependency changes. | Code review + ArchUnit/Spotless. | All modules. | Constitution. |

## UI / Interaction Mock-ups
```
Tabs: [ HOTP ][ TOTP ][ OCRA ]...............[ Info ⓘ ]
--------------------------------------------------------
| Protocol Info Drawer (520px)                         |
|  Header: "HOTP – Operator Guidance"                 |
|  Sections:                                          |
|   - Overview                                        |
|   - Telemetry & troubleshooting                     |
|   - Links                                           |
|  [Open as modal] [Close]                             |
--------------------------------------------------------
Modal view overlays entire console with focus trap.
```

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-021-01 | Trigger + drawer scaffolding wired into `/ui/console`. |
| S-021-02 | Schema-driven content, persistence, and CustomEvents land. |
| S-021-03 | Accessible modal behaviour (focus trap, reduced motion). |
| S-021-04 | Embeddable assets, demo, and integration docs published. |
| S-021-05 | Roadmap/knowledge map/docs updated, Gradle gate green.

## Test Strategy
- **Selenium/UI:** Cover trigger aria attributes, keyboard shortcuts, drawer/modal toggles, per-protocol switching, and
  persistence toggles.
- **JS unit tests:** Validate schema parsing, escaping, persistence, CustomEvents, API methods.
- **Docs:** Run `./gradlew spotlessApply check` to ensure README/how-to changes stay formatted.

## Interface & Contract Catalogue
### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-021-01 | `ProtocolInfoSchema` JSON describing sections, accordions, CTA links, reduced-motion hints. | rest-api (UI), ui JS |
| DO-021-02 | `ProtocolInfoState` (protocol key, open/closed flag, accordions, auto-open flags). | JS router, ui |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| — | — | No new backend routes; data bootstrapped inline via `<script>` block. | |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| — | — | No CLI changes. |

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|-----------|---------------------------|
| TE-021-01 | `ui.protocolInfo.trigger.clicked` | `protocol`, `source`. |
| TE-021-02 | `ui.protocolInfo.opened` / `ui.protocolInfo.closed` | `protocol`, `entryPoint`, `durationMs`. |
| TE-021-03 | `ui.protocolInfo.protocol.changed` | `fromProtocol`, `toProtocol`. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-021-01 | `rest-api/src/main/resources/static/ui/protocol-info/protocol-info.js|.css` | Embeddable assets. |
| FX-021-02 | `rest-api/src/main/resources/templates/ui/console/_protocol-info-data.html` | JSON bootstrap snippet for Thymeleaf. |
| FX-021-03 | `rest-api/src/test/resources/.../ProtocolInfoSchemaTest.json` | Unit-test schemas. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| UI-021-01 | Drawer open | User clicks trigger; drawer slides in with content, focus moves to close button. |
| UI-021-02 | Modal expanded | User selects "Open as modal"; overlay traps focus until closed. |
| UI-021-03 | Auto-open once per protocol | First visit to protocol auto-opens drawer; subsequent visits respect stored preference. |

## Telemetry & Observability
- Hook into existing navigation telemetry by emitting CustomEvents (listed above) so future observers can bridge to
  application telemetry.
- Log persistence warnings in development mode to help diagnose storage exceptions.

## Documentation Deliverables
- Update README/integration guide, roadmap, and knowledge map with protocol info surface details.
- Capture QA checklist (keyboard, screen reader, reduced motion) in the README.

## Fixtures & Sample Data
See FX-021-01..03 above; no additional fixtures required beyond schema snippets already referenced.

## Spec DSL
```
domain_objects:
  - id: DO-021-01
    name: ProtocolInfoSchema
    fields:
      - name: protocol
        type: string
        constraints: hotp|totp|ocra|emv|fido2|eudi-openid4vp|eudi-iso18013-5|eudi-siopv2
      - name: sections
        type: list<section>
  - id: DO-021-02
    name: ProtocolInfoState
    fields:
      - name: isOpen
        type: boolean
      - name: lastProtocol
        type: string
cli_commands: []
telemetry_events:
  - id: TE-021-01
    event: ui.protocolInfo.trigger.clicked
  - id: TE-021-02
    event: ui.protocolInfo.opened
fixtures:
  - id: FX-021-01
    path: rest-api/src/main/resources/static/ui/protocol-info/protocol-info.js
ui_states:
  - id: UI-021-01
    description: Drawer open state
```

## Appendix
_None._
