# Feature Plan 035 – Evaluate & Replay Audit Tracing

_Linked specification:_ `docs/4-architecture/features/035/spec.md`  
_Linked tasks:_ `docs/4-architecture/features/035/tasks.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
Deliver opt-in verbose traces for HOTP, TOTP, OCRA, and WebAuthn evaluate/replay/attest flows across CLI/REST/UI. Success
requires a shared trace model, per-request toggles, consistent facade rendering, WebAuthn enrichments, and documentation
+ analysis-gate evidence.

## Scope Alignment
- **In scope:** Core trace model/builders, application plumbing, CLI/REST toggles + payloads, operator-console trace
  panel, WebAuthn metadata enrichments, documentation + Selenium coverage.
- **Out of scope:** Persistent tracing, global verbose switches, telemetry schema changes.

## Dependencies & Interfaces
- Core modules for HOTP/TOTP/OCRA/WebAuthn algorithms.
- Application services emitting verbose traces.
- CLI flag + REST request/response contracts + operator UI JS/templates.
- Selenium suites and how-to/knowledge map documentation.

## Assumptions & Risks
- **Assumptions:** APIs already expose meaningful `message` fields; CLI/REST responses can carry trace payloads without
  breaking clients; Selenium fixtures exist for invalid scenarios.
- **Risks:**
  - Verbose traces leak outside responses → ensure helper scope + manual review.
  - UI trace panel performance issues → lazy-render content and keep panel collapsed by default.
  - WebAuthn metadata drift → extend tests before implementation (red → green).

## Implementation Drift Gate
- Evidence captured 2025-10-29: trace model diff, CLI/REST/UI verbose responses, Selenium invalid scenarios, documentation
  updates, and Gradle logs (`:core:test`, `:application:test`, `:cli:test`, `:rest-api:test`, `spotlessApply check`). Gate
  remains satisfied.

## Increment Map
1. **I1 – Trace model foundation (S-035-01)**
   - Add failing core tests describing verbose trace structure; implement immutable builders.
   - Commands: `./gradlew --no-daemon :core:test --tests "*TraceModelTest"`.
2. **I2 – Application verbose plumbing (S-035-02)**
   - Propagate verbose toggles through HOTP/TOTP/OCRA/WebAuthn services; add unit tests.
   - Commands: `./gradlew --no-daemon :application:test --tests "*VerboseTraceTest"`.
3. **I3 – CLI & REST integration (S-035-03)**
   - Add CLI flags + REST request/response fields; ensure payload parity; update tests/OpenAPI snapshots.
   - Commands: `./gradlew --no-daemon :cli:test --tests "*VerboseTraceTest"`; `./gradlew --no-daemon :rest-api:test --tests "*VerboseTrace*"`; `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`.
4. **I4 – Operator UI trace panel (S-035-04)**
   - Build terminal-style panel, JS toggle, copy/download actions; extend Selenium invalid scenarios.
   - Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.*OperatorUiSeleniumTest"`.
5. **I5 – WebAuthn enrichments (S-035-05)**
   - Add canonical RP IDs, signature metadata, flag maps, policy markers, low-S enforcement traces.
   - Commands: `./gradlew --no-daemon :core:test :application:test :cli:test :rest-api:test --tests "*Fido2*VerboseTraceTest"`; targeted Selenium + OpenAPI runs.
6. **I6 – Documentation & analysis gate (S-035-06)**
   - Update how-to/knowledge map/session snapshot; rerun `./gradlew --no-daemon spotlessApply check` to confirm green build.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-035-01 | I1 / T-035-01 | Trace model foundation. |
| S-035-02 | I2 / T-035-02 | Application plumbing. |
| S-035-03 | I3 / T-035-03 | CLI & REST verbose toggles. |
| S-035-04 | I4 / T-035-04 | Operator UI trace panel. |
| S-035-05 | I5 / T-035-05 | WebAuthn enrichments. |
| S-035-06 | I6 / T-035-06 | Documentation + gate. |

## Analysis Gate
- Completed 2025-10-29 with recorded Gradle logs and documentation diffs; no open questions remain.

## Exit Criteria
- Verbose traces available across all facades; default behaviour unchanged when verbose disabled.
- Selenium invalid scenarios verify trace rendering per ceremony.
- Docs/knowledge map updated; `spotlessApply check` green.

## Follow-ups / Backlog
- Feature 036 handles tier controls/remapping for verbose traces.
