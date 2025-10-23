# Feature 035 Tasks – Evaluate & Replay Audit Tracing

_Linked plan:_ `docs/4-architecture/feature-plan-035-evaluate-replay-audit-tracing.md`  
_Status:_ Draft  
_Last updated:_ 2025-10-22

☑ **T3501 – Map evaluation injection points**  
 ☑ Review HOTP/TOTP/OCRA/FIDO2 evaluate, replay, and attest services to document hook locations (2025-10-22 – captured in feature plan “Evaluation Injection Map”).  
 ☑ Run `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.*EvaluationApplicationServiceTest"` to capture the current baseline (2025-10-22 – build successful).

☑ **T3502 – Trace model specification tests (red)**  
 ☑ Add failing unit tests describing the ordered trace structure under `core` (2025-10-22 – `TraceModelTest` now defines desired API).  
 ☑ Execute `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.trace.TraceModelTest"` (2025-10-22 – fails to compile until trace model exists).

☑ **T3503 – Trace model implementation (green)**  
 ☑ Implement immutable trace model and builders to satisfy T3502 tests (2025-10-22 – `VerboseTrace` + nested `TraceStep` added).  
 ☑ Re-run `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.trace.TraceModelTest"` until green (2025-10-22 – passing).

☑ **T3504 – Application verbose plumbing tests (red)**  
 ☑ Add HOTP evaluation verbose trace expectations (`HotpEvaluationApplicationServiceVerboseTraceTest`, 2025-10-22 – now green after HOTP wiring).  
 ☑ Enable TOTP/OCRA/WebAuthn assertion/attestation verbose trace tests (`TotpEvaluationApplicationServiceVerboseTraceTest`, `OcraEvaluationApplicationServiceVerboseTraceTest`, `WebAuthnEvaluationApplicationServiceVerboseTraceTest`, `WebAuthnAttestationVerificationApplicationServiceVerboseTraceTest`, 2025-10-22).  
 ☑ Run `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.*VerboseTraceTest"` (2025-10-22 – passing).

☑ **T3505 – Application verbose plumbing (green)**  
 ☑ Wire verbose flag propagation and populate traces from core helpers for HOTP evaluation (2025-10-22 – trace builder in place, tests pass).  
 ☑ Repeat for TOTP, OCRA, WebAuthn assertion, and WebAuthn attestation services (2025-10-22 – trace builders + operation steps in place).  
 ☑ Ensure default (non-verbose) paths remain unchanged.  
 ☑ Re-run `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.*VerboseTraceTest"` until green once all services emit traces (2025-10-22 – passing).

☑ **T3506 – CLI verbose flag tests (red)**  
 ☑ 2025-10-22 – Added `HotpCliVerboseTraceTest`, `TotpCliVerboseTraceTest`, `OcraCliVerboseTraceTest`, and `Fido2CliVerboseTraceTest` to assert `--verbose` produces trace headers/operations across stored and inline evaluation commands.  
 ☑ 2025-10-22 – `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.*VerboseTraceTest"` (fails as expected until CLI wiring surfaces traces).

☑ **T3507 – CLI verbose implementation (green)**  
 ☑ 2025-10-22 – Added `--verbose` flag to HOTP/TOTP/OCRA evaluate commands and FIDO2 replay, rendering traces via shared `VerboseTracePrinter`; telemetry output unchanged.  
 ☑ 2025-10-22 – Updated `WebAuthnReplayApplicationService` to propagate verbose traces so CLI replay surfaces FIDO2 evaluation steps.  
 ☑ 2025-10-22 – `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.*VerboseTraceTest"`

☑ **T3508a – REST contract tests (TOTP slice, red)**  
 ☑ Extend TOTP evaluation/replay controller tests to accept a `verbose` flag and validate success plus validation/error trace payloads.  
 ☑ Run `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Totp*VerboseTraceTest"` (expected red, confirmed 2025-10-22).  
 ☑ 2025-10-22 – Owner approved Option B sequencing: land TOTP first to codify the test/DTO pattern before expanding to other protocols.

☑ **T3509a – REST verbose implementation & schema sync (TOTP slice, green)**  
 ☑ Propagate verbose flag through TOTP controllers/services, serialise trace payloads, refresh OpenAPI snapshots, and update REST docs as needed.  
 ☑ Re-run `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Totp*VerboseTraceTest"` until green.

☑ **T3508b / T3509b – HOTP slice**  
 ☑ 2025-10-22 – Mirrored the TOTP contract pattern for HOTP evaluate/replay: added verbose flag tests, updated DTOs/controllers/services to emit `trace` payloads on success and error, refreshed snapshots via `OPENAPI_SNAPSHOT_WRITE=true ./gradlew :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, and re-ran `./gradlew :rest-api:test --tests "io.openauth.sim.rest.hotp.*"`.

☑ **T3508c / T3509c – OCRA slice**  
 ☑ 2025-10-23 – Re-ran OCRA endpoint suites to assert `trace` propagation for stored/inline flows and regenerated OpenAPI snapshots with the shared `VerboseTracePayload` schema.  
 ☑ 2025-10-23 – `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Ocra*EndpointTest"`

☑ **T3508d / T3509d – WebAuthn slice**  
 ☑ 2025-10-23 – Confirmed evaluation/replay/attestation endpoints emit verbose traces, refreshed OpenAPI artefacts after documenting the payload schema, and revalidated suites (`Fido2EvaluationEndpointTest`, `Fido2ReplayEndpointTest`, `Fido2AttestationManualEndpointTest`).  
 ☑ 2025-10-23 – `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2*EndpointTest"`

☑ **T3510 – UI layout proposals (decision)**  
 ☑ Document at least two terminal-style panel layout options (bottom dock vs side drawer) with pros/cons and recommendation (2025-10-22 – see feature plan “UI Layout Decision”).  
 ☑ Capture owner decision under plan “UI Layout Decision” (2025-10-22 – Option A approved).

☑ **T3511 – UI verbose toggle tests (red)**  
 ☑ Extended Selenium coverage (`TotpOperatorUiSeleniumTest.verboseTraceToggleSurfacesTracePanelForStoredTotpEvaluation`) to assert toggle presence, trace visibility, and copy control.  
 ☑ Verified the new test failed prior to implementation via `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.TotpOperatorUiSeleniumTest.verboseTraceToggleSurfacesTracePanelForStoredTotpEvaluation"`.

☑ **T3512 – UI verbose implementation (green)**  
 ☑ Implemented global verbose toggle + docked trace panel (copy support, variant styling) and wired HOTP/TOTP/FIDO2 operator flows to propagate verbose requests and render traces.  
 ☑ Re-ran targeted suites to green:  
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.TotpOperatorUiSeleniumTest"`  
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.HotpOperatorUiSeleniumTest"`  
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`

☑ **T3513 – Documentation & knowledge sync**  
 ☑ Updated operator/UI and REST guides (HOTP/TOTP/WebAuthn) plus `knowledge-map.md` and `_current-session.md` to cover the verbose toggle, trace payloads, and new UI behaviour.  
 ☑ Ran `./gradlew --no-daemon spotlessApply` (2025-10-22) to format updated sources.

☑ **T3514 – Final quality gate**  
 ☑ 2025-10-23 – Executed `./gradlew --no-daemon spotlessApply check`; build completed successfully with aggregated branch coverage now at 70.45 % (1 645/2 335), closing the lingering quality gate failure.
