# Feature 038 - Evaluation Result Preview Table

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-11 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/038/plan.md` |
| Linked tasks | `docs/4-architecture/features/038/tasks.md` |
| Roadmap entry | #38 |

## Overview
Expose ordered OTP preview tables inside HOTP, TOTP, and OCRA evaluation responses so operators can see the evaluated OTP (Delta = 0) together with neighbouring values from the requested window. REST, CLI, and operator UI surfaces all render the same ordered data, drift inputs move back to replay-only forms, and telemetry records the requested window for audit.

## Clarifications
- 2025-11-01 - Preview table lives inside the existing Evaluation result card; no new panels. (operator directive)
- 2025-11-01 - The Delta = 0 row uses a slim protocol-coloured accent bar plus bold weight; typography stays uniform otherwise. (operator directive)
- 2025-11-01 - When backward and forward offsets are zero, render a single-row table with Delta = 0. (operator directive)
- 2025-11-01 - CLI ordering mirrors REST payloads; the flag syntax matches the preview window controls delivered alongside Feature 037. (owner directive)
- 2025-11-02 - Evaluation screens introduce Preview window offsets controls and drop drift inputs from evaluation flows; replay forms retain drift inputs. (owner directive)
- 2025-11-02 - REST DTO delta fields shipped via T3801 act as the baseline for later increments; no revert was required before application/CLI work. (owner directive)
- 2025-11-08 - Remove helper copy beneath the Preview window offsets heading so evaluation forms stay concise. (owner directive)

## Goals
- Accept preview window parameters on HOTP/TOTP/OCRA evaluation requests and return ordered preview rows with the evaluated Delta = 0 entry always present.
- Render the preview data consistently across REST, CLI, and operator UI (stored and inline flows) with accessible highlighting.
- Capture the requested window in telemetry/how-to documentation so operators understand the output provenance.

## Non-Goals
- Replay flows continue using drift inputs; they do not consume the preview window feature yet.
- Result-card previews do not introduce historical telemetry graphs or pagination.
- Persistence schemas remain unchanged.

## Functional Requirements

| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-038-01 | REST HOTP/TOTP/OCRA evaluation requests accept `window.backward`/`window.forward` (default 0) and return a `previews` array ordered by Delta with the evaluated entry at Delta = 0. | Request missing the window receives defaults; responses include ordered rows with counter/context metadata. | Integration tests assert defaults, ordering, single-row cases, and removal of evaluation drift fields. | Controllers return HTTP 422 for invalid window sizes (<0 or >max) and include field metadata; payload still contains evaluated row when window collapses. | Telemetry records window values and preview counts; verbose trace builder remains unchanged. | Owner directive 2025-11-01. |
| FR-038-02 | Application services and CLI commands propagate preview windows, drop evaluation drift options, and render preview tables (text + JSON) with Delta ordering. | CLI human-readable output shows table plus accent indicator; JSON output mirrors REST payload. | CLI unit + snapshot tests assert flag exclusivity, ordering, and removal of drift options. | CLI exits with code 3 if window flags are invalid; telemetry events capture error reason. | CLI telemetry shares window metadata with REST via shared DTO. | Owner directive 2025-11-01. |
| FR-038-03 | Operator UI evaluation panels expose Preview window offset controls (inline + stored) and render result-card tables with accent styling for Delta = 0. | Stored and inline panels reuse the same controls; result card shows previews for offsets {0,0} and arbitrarily larger windows. | Selenium tests toggle offsets, verify accent bars, and confirm helper text removal. | Invalid offsets surface inline validation and block submission until corrected. | UI telemetry adds `previewWindowBackward/Forward` fields and never emits OTP values. | Operator directive 2025-11-01/11-08. |
| FR-038-04 | Accessibility, documentation, roadmap, and knowledge map stay aligned with the preview behaviour and highlight treatment. | Docs describe preview usage, telemetry capture, and UI states; accessibility audit confirms bold+accent meets WCAG. | Analysis gate ensures docs updated before closing the feature. | Feature cannot be marked complete if docs drift from implementation; open questions must be logged. | Roadmap + knowledge map reference preview window contracts. | Constitution Principle 4. |

## Non-Functional Requirements

| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-038-01 | Preview window defaults (0,0) must return within the existing evaluation latency budget. | Prevent regressions for users that do not request extra rows. | REST/application tests show no latency regression beyond noise; CLI tests confirm constant-time default. | Application evaluation services. | Owner directive 2025-11-01. |
| NFR-038-02 | Accent styling must remain perceivable without colour (WCAG 2.1 AA). | Accessibility acceptance criteria. | Manual audit + Selenium asserts bold + inset accent; fallback text labels retained. | Operator UI components. | Operator acceptance 2025-11-01. |
| NFR-038-03 | Telemetry must never include OTP previews; only metadata (window size, counts, status) is allowed. | Preserve privacy and compliance. | Telemetry tests ensure previews are stripped before emission; log scanning remains clean. | Telemetry adapters, verbose trace builders. | Constitution Principle 4. |

## UI / Interaction Mock-ups
```
+---------------------------------------------+
| HOTP evaluation (stored credential)         |
|---------------------------------------------|
| Preview window offsets                      |
| Backward: [ 2 ]   Forward: [ 4 ]            |
|                                             |
| [Evaluate inline parameters]                |
+---------------------------------------------+

Result card excerpt
+---------------------------------------------+
| Delta | Counter | OTP                       |
|-------+---------+---------------------------|
| -2    | 146     | 123456                    |
| -1    | 147     | 789012                    |
|  0    | 148     | 345678  <accent bar>      |
| +1    | 149     | 901234                    |
| +2    | 150     | 567890                    |
+---------------------------------------------+
```

## Branch & Scenario Matrix

| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-038-01 | REST HOTP/TOTP/OCRA evaluation endpoints accept `window.backward`/`window.forward`, default to zero, and return ordered preview rows with Delta = 0 always present. |
| S-038-02 | Application services and CLI commands propagate preview windows, remove evaluation drift options, and render text/JSON tables. |
| S-038-03 | Operator UI evaluation panels expose preview window controls, render result-card tables for stored and inline flows, and highlight Delta = 0 accessibly. |
| S-038-04 | Documentation, telemetry, and accessibility audits remain aligned; roadmap/knowledge map entries describe preview behaviour and drift control changes. |
| S-038-05 | Helper text removal leaves forms concise while Replay screens retain drift inputs. |

## Test Strategy
- **REST:** Extend HOTP/TOTP/OCRA controller/service suites to assert default windows, ordered previews, single-row cases, and validation errors.
- **Application:** Add service-level tests covering preview assembly and telemetry metadata.
- **CLI:** Update snapshot tests for human-readable and JSON outputs; ensure window flags replace drift options.
- **UI (JS/Selenium):** Selenium flows toggle offsets {0,0} and {2,4}, verifying accent styling, helper-text removal, and stored vs inline parity.
- **Docs/Contracts:** Regenerate OpenAPI snapshots; update how-to guides, knowledge map, and roadmap entries once preview controls ship.

## Interface & Contract Catalogue

### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-038-01 | `PreviewWindow` value object containing `backward` and `forward` integers (>=0). | rest-api, application, cli |
| DO-038-02 | `PreviewRow` record with `delta`, `otp`, and protocol-specific `counter/context` metadata. | rest-api, application, cli, ui |
| DO-038-03 | `EvaluationResultPayload` extended DTO that now includes `previews` array. | rest-api, cli, ui |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-038-01 | REST POST `/api/v1/hotp/evaluate` | Accepts `window` object (backward/forward) and returns `previews` array. | Defaults to 0/0 when omitted, removes evaluation drift fields. |
| API-038-02 | REST POST `/api/v1/totp/evaluate` | Same semantics as HOTP. | |
| API-038-03 | REST POST `/api/v1/ocra/evaluate` | Supports preview rows even when counters are timestamp/context pairs. | |
| API-038-04 | Application `PreviewAssemblyService` | Builds preview rows, clamps window sizes, emits telemetry metadata. | |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-038-01 | `hotp evaluate --window-backward=<n> --window-forward=<n>` | Outputs ordered preview table and JSON payload; drift options removed. |
| CLI-038-02 | `totp evaluate --window-backward=<n> --window-forward=<n>` | Mirrors HOTP behaviour. |
| CLI-038-03 | `ocra evaluate --window-backward=<n> --window-forward=<n>` | Includes context columns when available. |

### Telemetry Events
| ID | Event name | Fields / Redaction summary |
|----|------------|----------------------------|
| TE-038-01 | `otp.evaluate.preview` | `protocol`, `windowBackward`, `windowForward`, `previewCount`, `result`. No OTP data captured. |
| TE-038-02 | `otp.evaluate.preview_invalid_window` | Same metadata plus sanitised reason code when validation fails. |

### Fixtures & Sample Data
| ID | Path | Description |
|----|------|-------------|
| FX-038-01 | `rest-api/src/test/resources/.../preview-window/*.json` | Canonical REST payloads showing Delta ordering and 0/0 windows. |
| FX-038-02 | `cli/src/test/resources/.../preview-table/*.txt` | CLI snapshot outputs for text/JSON previews. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|----------------------------|
| UI-038-01 | Preview window offsets control group (stored/inline) | Operators change backward/forward offsets; helper copy removed. |
| UI-038-02 | Result-card preview table (default window) | Shows single Delta = 0 row with accent bar when offsets are zero. |
| UI-038-03 | Result-card preview table (custom window) | Shows multi-row table with accent on Delta = 0 and stable column widths. |

## Telemetry & Observability
- Telemetry events `otp.evaluate.preview` and `otp.evaluate.preview_invalid_window` capture only metadata (window sizes, preview counts, protocol, status); OTP values remain redacted.
- CLI/REST loggers record validation summaries without including preview rows.
- Operator UI result-card tracing reuses existing verbose trace toggles; no new trace IDs required.

## Documentation Deliverables
- Update operator how-to guides to describe preview window controls, table behaviour, and CLI flags.
- Refresh roadmap entry #38 with outcome and lessons learned.
- Extend `docs/4-architecture/knowledge-map.md` to mention the preview-contract link between REST, CLI, and UI.
- Note telemetry additions in `docs/5-operations/analysis-gate-checklist.md` when running the gate.

## Fixtures & Sample Data
- Maintain preview payload fixtures under REST/CLI test resources for Delta ordering and validation paths.
- Selenium screenshots showing accent styling stored with test evidence (referenced in the plan).

## Spec DSL
```yaml
domain_objects:
  - id: DO-038-01
    name: PreviewWindow
    fields:
      - name: backward
        type: integer
        constraints: ">= 0"
      - name: forward
        type: integer
        constraints: ">= 0"
  - id: DO-038-02
    name: PreviewRow
    fields:
      - name: delta
        type: integer
      - name: otp
        type: string
        redaction: masked
      - name: counter
        type: long|string
routes:
  - id: API-038-01
    method: POST
    path: /api/v1/{protocol}/evaluate
    body:
      window: PreviewWindow?
    response:
      previews: list<PreviewRow>
cli_commands:
  - id: CLI-038-01
    command: hotp evaluate --window-backward=<n> --window-forward=<n>
telemetry_events:
  - id: TE-038-01
    event: otp.evaluate.preview
    fields: [protocol, windowBackward, windowForward, previewCount, result]
ui_states:
  - id: UI-038-01
    description: Result card preview table with accent highlight on delta 0
```

## Appendix
- Accessibility audit (2025-11-01) confirmed bold+accent treatment meets WCAG contrast when paired with Delta labels.
- CLI examples: `hotp evaluate --window-backward=2 --window-forward=4 --otp ...` outputs a five-row table plus JSON preview excerpt.
- Replay flows keep drift inputs and do not render preview tables until a future feature extends them.
