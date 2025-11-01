# Feature 038 – Evaluation Result Preview Table

_Status: Draft_  
_Last updated: 2025-11-01_

## Overview
Extend HOTP, TOTP, and OCRA evaluation responses so operators can see the evaluated OTP together with neighbouring values from the requested window inside the existing result card. The preview table must appear even when both backward and forward offsets are zero (showing a single row) and the evaluated entry must receive a subtle accent bar for quick recognition.

## Clarifications
- 2025-11-01 – The preview table integrates into the current “Evaluation result” card; no new panels or headings are introduced (operator directive).  
- 2025-11-01 – Highlight the evaluated row (Δ = 0) with a slim protocol-coloured accent bar; typography may stay uniform (operator directive).  
- 2025-11-01 – When offsets are both zero, render a single-row table with Δ = 0 so the evaluated OTP still appears in tabular form (operator directive).  
- 2025-11-01 – Continue accepting explicit backward/forward offsets via existing inputs; no additional counter fields are required (operator directive).  
- 2025-11-01 – CLI flows should mirror the REST payload ordering when the window feature ships; they may opt-in with the same flags delivered in Feature 037 (owner directive).

## Requirements

### R1 – REST evaluation payloads surface ordered previews
1. Extend HOTP, TOTP, and OCRA evaluation endpoints to include a `previews` array when the caller supplies `window.backward`/`window.forward` (defaulting to zero when omitted).  
2. Each element exposes `{ "counter": long|string, "delta": int, "otp": string }`; OCRA may replace `counter` with `context` fields when timestamps are present.  
3. Always include the evaluated entry at `delta = 0` even if both offsets equal zero; ordering is ascending by counter/time.  
4. Preserve existing metadata fields so current clients remain compatible.  
5. Document schema changes in the OpenAPI snapshot.

### R2 – Operator UI result card embeds the preview table
1. Replace the standalone OTP line with a table containing three columns: `Counter`, `Δ`, and `OTP`.  
2. Render one row per preview item; apply a protocol-specific accent bar on the left edge of the `Δ = 0` row.  
3. Table appears immediately beneath the card title; the status badge remains pinned to the lower-right as today.  
4. Support single-row scenarios (offsets zero) without altering card dimensions.  
5. Provide keyboard and screen-reader hints (row headers or aria-labels) so Δ values are announced.

### R3 – CLI mirror and copy helpers
1. HOTP/TOTP/OCRA inline evaluation commands print the preview table when offsets are supplied (default zero produces one row).  
2. Highlight the evaluated Δ = 0 entry with ANSI-safe styling (e.g., brackets) while preserving log-friendly output.  
3. Respect quiet/JSON modes; JSON output must include the `previews` array identical to the REST schema.

## ASCII Mock-up (Operator UI)
```
┌─────────────────────────────────────────────────────────────┐
│ Evaluation result                                           │
│                                                             │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Counter    Δ    OTP                                     │ │
│ │────────────────────────────────────────────────────────│ │
│ │ 000121    -2    421 707                                 │ │
│ │ 000122    -1    123 456                                 │ │
│ │▌000123     0    867 530                                 │ │
│ │ 000124    +1    908 112                                 │ │
│ │ 000125    +2    660 994                                 │ │
│ │ 000126    +3    776 201                                 │ │
│ │ 000127    +4    554 880                                 │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ Status                                           [ SUCCESS ]│
└─────────────────────────────────────────────────────────────┘
Legend: `▌` = accent bar tinted per protocol palette (orange for HOTP, blue for TOTP, teal for OCRA).
```

### ASCII Mock-up (Operator UI – Offset Controls)
```
Left column (inputs)                                      Right column (result)
┌─────────────────────────────────────────────┐           ┌────────────────────┐
│ Counter / Timestamp                         │           │ Evaluation result  │
│  Counter:  [ 000123 ]                       │           │  (preview table)…  │
│                                             │           └────────────────────┘
│ Preview window offsets                      │
│  Backward steps: [ 2 ]                      │
│  Forward steps:  [ 4 ]                      │
│  Include verbose traces [x]                 │
│                                             │
│ [Evaluate inline parameters]                │
└─────────────────────────────────────────────┘
────────────────────────────────────────────────
Enable verbose tracing for the next request [x]

Notes:
- Inputs remain in the existing left column stack.
- Offsets accept zero to show a single row (Δ = 0) on the result card.
- Same controls apply to stored mode; the result card renders previews regardless of source.
- Verbose tracing toggle stays in the footer section, matching existing console placement.
```

## Non-Goals
- Changing how backward/forward offsets are entered in the UI (handled elsewhere).  
- Showing historical telemetry inside the result card.  
- Modifying replay result cards (tracked under a future feature).

## Acceptance Criteria
1. REST evaluation responses include the `previews` array for HOTP/TOTP/OCRA and pass updated integration tests.  
2. Operator UI result cards render the table with accent bar semantics and pass Selenium coverage for offsets {0,0} and {2,4}.  
3. CLI commands output the preview list in both human-readable and JSON modes, and unit tests assert Δ ordering.  
4. Accessibility audit confirms the evaluated row remains distinguishable without colour.

## Test Strategy
- REST: Extend controller/service integration suites to assert `previews` content, sorting, and single-row behaviour.  
- UI: Update Cypress/Selenium flows to toggle offsets and verify rendered rows (including Δ = 0 highlight).  
- CLI: Add golden tests covering each protocol and JSON payload snapshots.  
- Contract: Regenerate OpenAPI documentation and ensure backwards-compatible clients remain unaffected.
