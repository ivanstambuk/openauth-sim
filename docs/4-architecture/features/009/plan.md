# Feature 009 – Operator Console Infrastructure Plan

_Linked specification:_ `docs/4-architecture/features/009/spec.md`  
_Linked tasks:_ `docs/4-architecture/features/009/tasks.md`  
_Status:_ In migration (Batch P3)  
_Last updated:_ 2025-11-11  
_Owners:_ Ivan (project owner)  
_Roadmap entry:_ #9 – Operator Console Infrastructure

## Vision & Success Criteria
Draw every operator-console story (tabs, placeholders, info drawer, presets, validation helpers, trace diagnostics, Base32 inputs, preview windows,
and JS harness tests) under Feature 009 so future batches can evolve concrete console capabilities from a single, auditable source.

## Scope Alignment
- Consolidate the console tab shell, query-param routing, and stored credential seeding controls introduced by the legacy features.
- Surface architectural additions (Protocol Info drawer, preset label harmonisation, validation helper, verbose traces/tiers, Base32 inline secrets, preview windows).
- Document and verify the console JS modularisation plus Node/Gradle harness that powers deterministic protocol suites.
- Synchronise documentation artefacts (roadmap, knowledge map, session log (docs/_current-session.md), `_current-session.md`, operator how-tos) with the consolidated console ownership.

## Dependencies & Interfaces
| Dependency | Notes |
|------------|-------|
| `rest-api` module | Hosts the Thymeleaf templates, controllers, REST endpoints, and JS assets; must keep `TelemetryContracts` adapters aligned. |
| `application` module | Supplies orchestration helpers, trace builders, Base32 helpers, and preview window services used by CLI/REST/UI. |
| `cli` module | CLI flags/commands must match spec (verbose toggle, tiers, Base32, preview windows). |
| Node toolchain | Required for `operatorConsoleJsTest` and the modular console harness; ensures deterministic DOM fixtures. |
| Docs/roadmap/knowledge map | Need updates to cite Feature 009 exclusively and to log the migration once legacy directories disappear. |

## Assumptions & Risks
- Legacy specs dot the landscape (017/020/021/025/033–038/041); this rewrite assumes no new functional details exist outside these folders.
- Node harness/Gradle integration must stay deterministic; flaky JS suites would block the gate & require follow-up documentation.
- Deleting the `legacy/` folders before verifying the content migrated would harm auditability; log the deletions in `_current-session.md` immediately after removal.
- Trace tiers and Base32 helpers must keep telemetry sanitised; any regression in `TelemetryContracts` (e.g., leaking secrets) is a high-risk failure.


## Implementation Drift Gate
Before closing this feature, confirm every FR/NFR (FR-009-01..FR-009-10, NFR-009-01..NFR-009-05) maps to code/tests (console shell, info drawer, presets, validation helper, verbose traces/tiers, Base32 inputs, preview windows, JS harness). Capture the drift report inside this plan once the gate runs.


## Increment Map
1. **P3-I1 – Legacy FR/NFR absorption** (Owner: Ivan, Status: Completed)  
   - Absorbed requirements from Features 017/020/021/025/033–038/041 so `spec.md` now lists the unified FRs, NFRs, scenarios, interfaces, and DSL entries.  
2. **P3-I2 – Plan/tasks/doc gate refresh** (Owner: Ivan, Status: Completed)  
   - Updated `plan.md` and `tasks.md` to match the consolidated scope, scenario tracking, and tooling commands.  
3. **P3-I3 – Legacy directory cleanup** (Owner: Ivan, Status: Completed)  
   - Removed `docs/4-architecture/features/009/legacy/<old-id>/`, logging `rm` commands + outputs in `_current-session.md`.  
4. **P3-I4 – Batch P3 Phase 2 verification** (Owner: Ivan, Status: Pending)  
   - After Features 009–013 finish rewrites, rerun `./gradlew --no-daemon spotlessApply check`, log the Batch P3 Phase 2 result, and capture follow-ups before hand-off.

_Verification commands:_ `./gradlew --no-daemon spotlessApply check`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.*OperatorUiSeleniumTest"`, `./gradlew --no-daemon :cli:test --tests "*VerboseTrace*"`, `./gradlew --no-daemon :application:test --tests "*VerboseTrace*"`, `node --test rest-api/src/test/javascript/emv/console.test.js`, `./gradlew --no-daemon operatorConsoleJsTest -PconsoleTestFilter=<protocol>`, `./gradlew --no-daemon pmdMain pmdTest`.

## Scenario Tracking
| Scenario | Description | Increment |
|----------|-------------|-----------|
| S-009-01 | `/ui/console` renders ordered tabs (hotp → eudi-siopv2), maintains query-param history, and surfaces stored credential seeding controls plus placeholder messaging. | P3-I1 |
| S-009-02 | Protocol Info drawer opens via the tablist trigger, swaps schema-based content per protocol, persists preferences, and exposes embeddable docs. | P3-I1 |
| S-009-03 | Preset dropdowns across HOTP/TOTP/OCRA/FIDO2 display `<scenario – attributes>` labels and seeded/stored catalogues stay in sync. | P3-I1 |
| S-009-04 | Validation helper reveals the result card and message for invalid OTP/OCRA/WebAuthn responses. | P3-I1 |
| S-009-05 | Verbose trace mode delivers identical payloads to CLI, REST, and UI consumers, including WebAuthn metadata. | P3-I1 |
| S-009-06 | Trace tiers (normal/educational/lab-secrets) mask attributes via the shared helper and emit `telemetry.trace.*` events when requested. | P3-I1 |
| S-009-07 | Inline shared secrets accept Base32 or hex inputs with CLI/UI helpers enforcing mutual exclusivity. | P3-I1 |
| S-009-08 | Evaluation preview windows render ordered Delta tables, CLI/REST flags map offsets, and helper text remains concise in the UI. | P3-I1 |
| S-009-09 | Console JS modules run inside the `operatorConsoleJsTest` harness with protocol-filtering support and deterministic fixtures. | P3-I1 |
| S-009-10 | Documentation (roadmap, knowledge map, session log (docs/_current-session.md), `_current-session.md`, how-tos) captures the consolidated console scope and marks legacy directories as removed. | P3-I2/P3-I3 |

## Analysis Gate
Run `docs/5-operations/analysis-gate-checklist.md` after the spec/plan/tasks mention Feature 009 as the single console owner, all scenarios trace to requirements, and `_current-session.md` logs the directory removals.

## Exit Criteria
- The consolidated Feature 009 spec/plan/tasks document the entire operator console scope (tabs, info drawer, presets, verbose diagnostics, Base32, preview windows, JS harness).
- Documentation (roadmap, knowledge map, session log (docs/_current-session.md), `_current-session.md`, how-tos) references Feature 009 and notes the legacy directories are gone.
- Legacy directories `docs/4-architecture/features/009/legacy/<old-id>` no longer exist and their deletions/logs are recorded.
- The console verification suites (JVM, Node, PMD/Spotless) remain green once the phase concludes.

## Follow-ups / Backlog
- Repeat this legacy absorption process for Features 010–013 so their specs/plans/tasks become authoritative and the remaining `legacy/` trees are removed.
- After all features migrate, rerun `./gradlew --no-daemon spotlessApply check`, log the Batch P3 Phase 2 result in `_current-session.md`, and update the session snapshot before handing off.

$chunk
