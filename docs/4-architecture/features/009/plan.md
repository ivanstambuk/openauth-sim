# Feature 009 – Operator Console Infrastructure Plan

_Linked specification:_ `docs/4-architecture/features/009/spec.md`  
_Linked tasks:_ `docs/4-architecture/features/009/tasks.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-13  
_Owners:_ Ivan (project owner)  
_Roadmap entry:_ #9 – Operator Console Infrastructure

## Vision & Success Criteria
Draw every operator-console story (tabs, placeholders, info drawer, presets, validation helpers, trace diagnostics, Base32 inputs, preview windows,
and JS harness tests) under Feature 009 so future batches can evolve concrete console capabilities from a single, auditable source.

## Scope Alignment
- Maintain the console tab shell, query-param routing, and stored credential seeding controls shared across all simulator protocols.
- Keep architectural additions (Protocol Info drawer, preset label harmonisation, validation helper, verbose traces/tiers, Base32 inline secrets, preview windows) current as new requirements land.
- Document and verify the console JS modularisation plus Node/Gradle harness that powers deterministic protocol suites.
- Synchronise documentation artefacts (roadmap, knowledge map, session log (docs/_current-session.md), `_current-session.md`, operator how-tos) with the active console ownership.

## Dependencies & Interfaces
| Dependency | Notes |
|------------|-------|
| `rest-api` module | Hosts the Thymeleaf templates, controllers, REST endpoints, and JS assets; must keep `TelemetryContracts` adapters aligned. |
| `application` module | Supplies orchestration helpers, trace builders, Base32 helpers, and preview window services used by CLI/REST/UI. |
| `cli` module | CLI flags/commands must match spec (verbose toggle, tiers, Base32, preview windows). |
| Node toolchain | Required for `operatorConsoleJsTest` and the modular console harness; ensures deterministic DOM fixtures. |
| Docs/roadmap/knowledge map | Must reference Feature 009 as the console authority and capture any future scope changes. |

## Assumptions & Risks
- Node harness/Gradle integration must stay deterministic; flaky JS suites would block the gate & require follow-up documentation.
- Trace tiers and Base32 helpers must keep telemetry sanitised; any regression in `TelemetryContracts` (e.g., leaking secrets) is a high-risk failure.


## Implementation Drift Gate

- Summary: Use this gate to ensure that the operator console (tabs, info drawer, presets, validation helper, verbose traces/tiers, Base32 inputs, preview windows, JS harness) remains aligned with FR-009-01..10 and NFR-009-01..05 and with the corresponding REST/CLI/application behaviours.

- **Checklist for future drift-gate runs (agents):**
  - **Preconditions**
    - [ ] `docs/4-architecture/features/009/{spec,plan,tasks}.md` updated to the current date, with console scope (tabs, presets, validation helper, traces, Base32, preview windows, JS harness) fully captured.  
    - [ ] `docs/4-architecture/open-questions.md` has no `Open` entries for Feature 009.  
    - [ ] The following commands have been run in this increment and logged in `docs/_current-session.md`:  
      - `./gradlew --no-daemon spotlessApply check` (includes JVM tests and Node-based `operatorConsoleJsTest`).  
      - Any focused UI/REST test targets referenced in this plan/tasks when console behaviour changes.  

  - **Spec ↔ console code/test mapping**
    - [ ] For FR-009-01..10 and NFR-009-01..05, identify corresponding:  
      - Templates/controllers and JS modules under `rest-api` that implement tab layout, info drawer, preset wiring, validation helper, trace tiers, Base32 inputs, preview windows.  
      - JVM tests and JS/Node tests (`operatorConsoleJsTest`) that cover the described behaviours.  
    - [ ] Ensure the Scenario Tracking table reflects current console scope and, where needed, augment it with links to specific tests/files.  

  - **Tab shell, info drawer, presets**
    - [ ] `/ui/console` tab order and labels (HOTP/TOTP/OCRA/FIDO2/EMV/EUDIW/… as per spec) match the Feature 009 spec and roadmap.  
    - [ ] Protocol Info drawer behaviour (open/close, per-protocol content, preference persistence) matches spec and how-to docs.  
    - [ ] Preset dropdowns across protocols show correct labels and seed the correct stored/inline data; tests verify preset consistency.  

  - **Validation helper, traces, Base32, preview windows**
    - [ ] Validation helper messaging and result cards for invalid responses match REST/CLI semantics and telemetry (no extra or missing cases).  
    - [ ] Verbose trace tiers (normal/educational/lab-secrets) apply the same masking rules as other facades and emit the expected telemetry events.  
    - [ ] Inline shared secret inputs accept Base32/hex as described; mutual exclusivity and helper text match tests and docs.  
    - [ ] Preview windows (Delta tables, offsets) display correct data and helper text; REST/CLI flags map cleanly into the UI.  

  - **JS harness & determinism**
    - [ ] `operatorConsoleJsTest` still covers: tab activation, deep-link routing, preset behaviour, validation helper, verbose-trace integration, and preview windows.  
    - [ ] Node/Gradle integration for console JS tests remains deterministic (no flaky tests); any flakiness is documented and tracked as tasks.  

  - **Docs & telemetry alignment**
    - [ ] Console how-to docs (if present) and references in README/how-to landing pages match the current tab set and console behaviour.  
    - [ ] Telemetry documentation and snapshots reflect the console’s use of `TelemetryContracts` (no extra raw secrets or missing events).  
    - [ ] Roadmap, knowledge map, and `_current-session.md` still refer to Feature 009 as the operator-console authority and describe the console scope correctly.  

  - **Drift capture & remediation**
    - [ ] Any high-/medium-impact drift (e.g., console behaviour not matching spec, missing trace tiers, outdated presets) is:  
      - Logged as an `Open` entry in `docs/4-architecture/open-questions.md` for Feature 009.  
      - Captured as explicit tasks in `docs/4-architecture/features/009/tasks.md`.  
    - [ ] Low-impact drift (typos, minor UI text mismatches, small doc changes) is corrected directly, with a brief note added in this section or the plan’s verification log.  

  - **Gate output**
    - [ ] This section is updated with the latest drift gate run date, listing key commands executed and a concise “matches vs gaps” summary plus remediation notes.  
    - [ ] `docs/_current-session.md` logs that the Feature 009 Implementation Drift Gate was executed (date, commands, and reference to this plan section).  

### Drift Report – 2025-11-13
- **Scope review:** Spec/plan/tasks now describe the steady-state operator console (tabs, info drawer, presets, validation helper, verbose traces + tiers, Base32 inline secrets, preview windows, JS harness, documentation duties). No migration references remain, and roadmap/knowledge map/session log all cite Feature 009 as the sole console authority (covers FR-009-01..10, S-009-01..10, FR-009-10).
- **Code & test alignment:** Console shell + preset helpers kept inside `rest-api` templates/controllers with Selenium + Node harness coverage (S-009-01/03/09). Validation helper wiring and verbose trace parity confirmed via `:rest-api:test`, `:application:test`, `:cli:test`, and `node --test …` suites exercised during the latest Gradle run (FR-009-04..06). Base32 helper + inline DTOs remain in `application` and REST/CLI facades with OpenAPI snapshots documenting the field (FR-009-07). Preview tables and tiered traces stay green in `operatorConsoleJsTest` plus JVM integration suites (FR-009-05..09).
- **NFR verification:** Telemetry hygiene + accessibility enforced via existing ArchUnit/telemetry guards and Selenium focus tests (NFR-009-02/04). Documentation traceability satisfied by updated roadmap, knowledge map, `_current-session.md`, and plan/tasks (NFR-009-01). Node harness determinism confirmed by the filtered Gradle target invoked through `./gradlew --no-daemon spotlessApply check` (NFR-009-03/NFR-009-05).
- **Verification commands:** `./gradlew --no-daemon spotlessApply check` (2025-11-13) captured in `_current-session.md` and Feature 009 tasks verification log; command includes `operatorConsoleJsTest`, PMD/Spotless, JVM suites, and Node harness outputs.

## Scenario Tracking
| Scenario | Description |
|----------|-------------|
| S-009-01 | `/ui/console` renders ordered tabs (hotp → eudi-siopv2), maintains query-param history, and surfaces stored credential seeding controls plus placeholder messaging. |
| S-009-02 | Protocol Info drawer opens via the tablist trigger, swaps schema-based content per protocol, persists preferences, and exposes embeddable docs. |
| S-009-03 | Preset dropdowns across HOTP/TOTP/OCRA/FIDO2 display `<scenario – attributes>` labels and seeded/stored catalogues stay in sync. |
| S-009-04 | Validation helper reveals the result card and message for invalid OTP/OCRA/WebAuthn responses. |
| S-009-05 | Verbose trace mode delivers identical payloads to CLI, REST, and UI consumers, including WebAuthn metadata. |
| S-009-06 | Trace tiers (normal/educational/lab-secrets) mask attributes via the shared helper and emit `telemetry.trace.*` events when requested. |
| S-009-07 | Inline shared secrets accept Base32 or hex inputs with CLI/UI helpers enforcing mutual exclusivity. |
| S-009-08 | Evaluation preview windows render ordered Delta tables, CLI/REST flags map offsets, and helper text remains concise in the UI. |
| S-009-09 | Console JS modules run inside the `operatorConsoleJsTest` harness with protocol-filtering support and deterministic fixtures. |
| S-009-10 | Documentation (roadmap, knowledge map, session log (docs/_current-session.md), `_current-session.md`, how-tos) captures the consolidated console scope. |

## Analysis Gate
Run `docs/5-operations/analysis-gate-checklist.md` whenever console scope changes to confirm spec/plan/tasks alignment and ensure `_current-session.md` captures the verification commands.

## Exit Criteria
- The Feature 009 spec/plan/tasks document the entire operator console scope (tabs, info drawer, presets, verbose diagnostics, Base32, preview windows, JS harness).
- Documentation (roadmap, knowledge map, session log (docs/_current-session.md), `_current-session.md`, how-tos) references Feature 009 as the operator-console authority.
- The console verification suites (JVM, Node, PMD/Spotless) remain green once the phase concludes.
