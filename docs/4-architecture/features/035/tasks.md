# Feature 035 Tasks – Evaluate & Replay Audit Tracing

_Linked plan:_ `docs/4-architecture/features/035/plan.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

> Checklist condenses the red→green workflow (trace model → application → facades → UI → WebAuthn enrichments → docs/gate).

## Checklist
- [x] T-035-01 – Trace model foundation (FR-035-01, S-035-01).  
  _Intent:_ Add failing `TraceModelTest` cases, implement immutable `VerboseTrace`/`TraceStep`, and rerun core tests.  
  _Verification:_ `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.trace.TraceModelTest"` (2025-10-22).

- [x] T-035-02 – Application verbose plumbing (FR-035-02, S-035-02).  
  _Intent:_ Propagate verbose toggles through HOTP/TOTP/OCRA/WebAuthn services and ensure non-verbose paths remain unchanged.  
  _Verification:_ `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.*VerboseTraceTest"` (2025-10-22).

- [x] T-035-03 – CLI & REST integration (FR-035-03, S-035-03).  
  _Intent:_ Add CLI `--verbose` flag, REST request/response fields, OpenAPI updates, and ensure payload parity.  
  _Verification:_ `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.*VerboseTraceTest"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.*VerboseTrace*"`; `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"` (2025-10-24).

- [x] T-035-04 – Operator UI trace panel (FR-035-03, S-035-04).  
  _Intent:_ Build the terminal-style panel, JS toggle, copy/download controls, and extend Selenium suites for invalid/verbose scenarios.  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.*OperatorUiSeleniumTest"` (2025-10-24).

- [x] T-035-05 – WebAuthn verbose enrichments (FR-035-04, S-035-05).  
  _Intent:_ Add canonical RP IDs, signature metadata, flag maps, policy markers, low-S enforcement traces, and matching CLI/REST/UI coverage.  
  _Verification:_ `./gradlew --no-daemon :core:test :application:test :cli:test :rest-api:test --tests "*Fido2*VerboseTraceTest"`; `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"` (2025-10-25).

- [x] T-035-06 – Documentation & analysis gate (FR-035-05, S-035-06).  
  _Intent:_ Update how-to guides/knowledge map/session snapshot, record verbose-mode guidance, and rerun the full Gradle gate.  
  _Verification:_ `./gradlew --no-daemon :core:test :application:test :cli:test :rest-api:test :ui:test spotlessApply check` (2025-10-29).

## Verification Log
- 2025-10-22 – `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.trace.TraceModelTest"`
- 2025-10-22 – `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.*VerboseTraceTest"`
- 2025-10-24 – `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.*VerboseTraceTest"`
- 2025-10-24 – `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.*VerboseTrace*"`
- 2025-10-24 – `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`
- 2025-10-25 – `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.*OperatorUiSeleniumTest"`
- 2025-10-29 – `./gradlew --no-daemon :core:test :application:test :cli:test :rest-api:test :ui:test spotlessApply check`

## Notes / TODOs
- Verbose tier controls (educational vs lab secrets) proceed under Feature 036.
