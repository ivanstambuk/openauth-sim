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
Before closing this feature, confirm every FR/NFR (FR-009-01..FR-009-10, NFR-009-01..NFR-009-05) maps to code/tests (console shell, info drawer, presets, validation helper, verbose traces/tiers, Base32 inputs, preview windows, JS harness). Capture the drift report inside this plan once the gate runs.

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
