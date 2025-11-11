# Feature Plan 031 – Legacy Entry-Point Removal

_Linked specification:_ `docs/4-architecture/features/031/spec.md`  
_Linked tasks:_ `docs/4-architecture/features/031/tasks.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
Retire the CLI/JS compatibility shims so telemetry, router state, and networking flows rely solely on the canonical APIs
introduced in Features 017/024/026/027. Success requires:
- FR-031-01 – CLI emits telemetry exclusively via adapters.
- FR-031-02 – Operator console routing/query-state shims removed.
- FR-031-03 – Console networking + FIDO2 bridges rely only on Fetch + canonical helpers.
- FR-031-04 – WebAuthn legacy presets/docs cleaned up and analysis gate re-run.

## Scope Alignment
- **In scope:** CLI telemetry path, operator console router scripts, FIDO2 ceremony helpers, Selenium fetch polyfills,
  WebAuthn generator presets, documentation sync.
- **Out of scope:** REST payload validation changes, removal of unrelated test-only flags, new telemetry schemas.

## Dependencies & Interfaces
- CLI modules (`OcraCli`), `TelemetryContracts` adapters.
- UI router scripts under `rest-api/src/main/resources/static/ui/**` and Selenium harnesses.
- WebAuthn generator fixtures and knowledge artefacts.

## Assumptions & Risks
- **Assumptions:** Operators will update bookmarks/automation; HtmlUnit fetch polyfill available in Selenium harness.
- **Risks:**
  - CLI telemetry consumers parsing legacy text -> provide release notes + doc updates.
  - HtmlUnit lacking fetch -> ensure harness enables built-in polyfill.
  - Bookmark breakage -> document canonical param usage in how-tos.

## Implementation Drift Gate
- Evidence captured 2025-10-19: CLI telemetry test diff, router/JS removal commits, Selenium fetch polyfill configuration,
  WebAuthn preset updates, knowledge map + roadmap diffs, `./gradlew --no-daemon :rest-api:test` + `spotlessApply check`
  logs. Gate remains satisfied; rerun only if legacy affordances reappear.

## Increment Map
1. **I1 – CLI telemetry tests (S-031-01)**
   - Add failing tests asserting adapter-only frames.
   - Commands: `./gradlew --no-daemon :cli:test` (targeted).
2. **I2 – Remove `legacyEmit` path (S-031-01)**
   - Delete legacy branch, update integration tests.
   - Commands: `./gradlew --no-daemon :cli:test`, `./gradlew spotlessApply check`.
3. **I3 – Console router cleanup (S-031-02)**
   - Remove `__openauth*` globals + legacy param coercion.
   - Commands: `./gradlew --no-daemon :rest-api:test --tests "*OperatorUi*"`, `./gradlew spotlessApply check`.
4. **I4 – HOTP/TOTP router parity (S-031-02)**
   - Mirror cleanup across other protocol tabs; adjust Selenium nav.
   - Commands: `./gradlew --no-daemon :rest-api:test --tests "*Hotp*" "*Totp*"`.
5. **I5 – FIDO2 canonical API (S-031-03)**
   - Drop `legacySetMode`, rely on canonical ceremony helpers.
   - Commands: `./gradlew --no-daemon :rest-api:test --tests "*Fido2*"`.
6. **I6 – Fetch-only networking (S-031-03)**
   - Remove XMLHttpRequest fallbacks, enable HtmlUnit fetch polyfill, rerun UI suites.
   - Commands: `./gradlew --no-daemon :rest-api:test --tests "*OperatorUi*"`, `./gradlew spotlessApply check`.
7. **I7 – WebAuthn preset cleanup (S-031-04)**
   - Prune legacy generator sample, align preset IDs, update docs/tests.
   - Commands: `./gradlew --no-daemon :application:test`, `./gradlew --no-daemon :rest-api:test --tests "*WebAuthn*"`.
8. **I8 – Documentation + knowledge sync (S-031-04)**
   - Update how-to guides, roadmap, knowledge map, `_current-session`; rerun analysis gate.
   - Commands: `./gradlew --no-daemon spotlessApply check`.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-031-01 | I1, I2 / T-031-01, T-031-02 | CLI telemetry removal. |
| S-031-02 | I3, I4 / T-031-03, T-031-04 | Router/query param cleanup. |
| S-031-03 | I5, I6 / T-031-05, T-031-06 | Networking + canonical API. |
| S-031-04 | I7, I8 / T-031-07, T-031-08 | WebAuthn presets + documentation sync. |

## Analysis Gate
- Completed 2025-10-19 (post T3108) after documentation, tests, and analysis checklist were updated; no open questions remain.

## Exit Criteria
- No references to `legacyEmit`, `legacySetMode`, `__openauth*`, or XHR fallbacks in production.
- Selenium/UI tests pass using canonical params + Fetch.
- Knowledge artefacts reflect the simplified routing/telemetry flows.
- Gradle gate (`./gradlew --no-daemon :rest-api:test spotlessApply check`) recorded as green.

## Follow-ups / Backlog
- None; future router or telemetry adjustments require new features.
