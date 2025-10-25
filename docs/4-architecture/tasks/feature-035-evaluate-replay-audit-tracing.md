# Feature 035 Tasks – Evaluate & Replay Audit Tracing

_Linked plan:_ `docs/4-architecture/feature-plan-035-evaluate-replay-audit-tracing.md`  
_Status:_ Draft  
_Last updated:_ 2025-10-25

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

☑ **T3515 – Trace envelope + formatter extensions (red/green)**  
 ☑ Extend `VerboseTraceTest` (core) to require tier metadata, SHA-256 secret digests, and per-step spec anchors.  
 ☑ Extend CLI/REST/UI formatter unit tests to expect typed attribute labelling while keeping human-readable layout.  
 ☑ Command (red): `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.trace.VerboseTraceTest"`  
 ☑ Implement trace model changes (tier enum, spec anchors, attribute typing) plus formatter updates; rerun targeted tests to green.  
 ☑ Command (green): `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.trace.VerboseTraceTest"`

☑ **T3516 – HOTP/TOTP trace enrichment (tests-first)**  
 ☑ Add failing application tests covering new HOTP/TOTP steps (time counter details, HMAC/truncation attributes, spec anchors).  
 ☑ Implement HOTP/TOTP trace builders to populate the enriched attributes; rerun tests.  
 ☑ Command: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.hotp.*VerboseTraceTest" --tests "io.openauth.sim.application.totp.*VerboseTraceTest"`

☑ **T3517 – OCRA trace enrichment (tests-first)**
 ☑ Add failing application tests asserting suite parsing, message assembly segments, and SHA-256 digests. (2025-10-23 – `OcraEvaluationApplicationServiceVerboseTraceTest` now covers suite parsing, message assembly, and digest expectations.)
 ☑ Implement OCRA trace step population and rerun tests. (2025-10-23 – application service emits `parse.suite`/`normalize.inputs`/`assemble.message`/`compute.hmac`/`truncate.dynamic`/`mod.reduce`; CLI verbose expectations updated.)
 ☑ Command: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.ocra.*VerboseTraceTest"`

☑ **T3518 – WebAuthn attestation/assertion enrichment (tests-first)**  
 ☑ Extend WebAuthn verbose trace tests to verify `parse.clientData`, `parse.authenticatorData`, `build.signatureBase`, and `evaluate.counter` step attributes (decoded JSON, RP ID hash, flags/counters, and SHA-256 signature-base digest). *(2025-10-24 – assertions extended across evaluation + attestation suites and captured expected fields before implementation)*  
 ☑ Implement WebAuthn application/service trace updates; rerun tests. *(2025-10-24 – enrichment implemented for assertion + attestation services, verbose traces now emit the new steps)*  
 ☑ Command: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.*VerboseTraceTest"`

☑ **T3519 – Facade formatting & Selenium verification**  
 ☑ Refined CLI verbose trace assertions across HOTP/TOTP/OCRA/FIDO2 to lock SHA-256 hashes, counter derivations, and WebAuthn signature-base digests; REST endpoint tests now inspect ordered attribute payloads for evaluation/replay/attestation slices; operator UI Selenium suite verifies the rendered trace includes hashed secrets, time counters, and OTP output.  
 ☑ Commands:  
  - `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.*VerboseTraceTest"`  
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2EvaluationEndpointTest"`  
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.TotpReplayEndpointTest"`  
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.HotpReplayEndpointTest"`  
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OcraEvaluationEndpointTest"`  
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationManualEndpointTest"`  
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.TotpOperatorUiSeleniumTest"`

☑ **T3523 – HOTP trace canonical algorithm labels**  
 ☑ Swapped HOTP verbose trace `alg` values and HMAC step details to canonical `HMAC-SHA-*` labels, added match derivation detail attribute, and flipped the `non_standard_hash` note to a boolean flag.  
 ☑ Tests: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.hotp.*VerboseTraceTest"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.OcraCliErrorHandlingTest.importCommandValidationFailure"` (ensured clean MapDB lock before rerun), `./gradlew --no-daemon spotlessApply check`.

☐ **T3520 – Documentation & follow-up logging**  
 ☐ Update operator/CLI/REST docs with enriched trace examples, note the tier metadata, and record the redaction-toggle follow-up.  
☑ Sync knowledge map and `_current-session.md` with the enrichment outcomes.  
☑ Command: `./gradlew --no-daemon spotlessApply`

☑ **T3524 – OCRA operator console verbose integration**  
 ☑ Update `ui/ocra/evaluate` and `ui/ocra/replay` scripts to route payloads through `VerboseTraceConsole`, attaching the verbose flag and rendering returned traces in the shared console panel.  
 ☑ Add Selenium coverage for verbose-enabled OCRA evaluate and replay flows; ensure traces remain absent when the toggle is off.  
  ☑ New `OcraOperatorUiSeleniumTest` exercises stored evaluate/replay with verbose enabled, verifies the verbose flag on outbound payloads, and asserts the console remains hidden when responses omit trace data.  
  ☑ Captured pre-change failure with `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.OcraOperatorUiSeleniumTest"` before wiring the UI.  
 ☑ Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.OcraOperatorUiSeleniumTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Ocra*EndpointTest"`

☑ **T3525 – OCRA verification verbose trace implementation**  
 ☑ Added dedicated stored/inline replay verbose trace tests (`application/src/test/java/io/openauth/sim/application/ocra/OcraVerificationApplicationServiceVerboseTraceTest.java`).  
 ☑ Enhanced `OcraVerificationApplicationService` to build verification traces (normalize/resolve/assemble/hmac/truncate/mod/compare) and propagate them through REST/CLI/UI responses without mutating `OcraReplayVerifier`.  
 ☑ Extended REST (`OcraVerificationEndpointTest`), CLI (`OcraCliVerboseTraceTest`), and Selenium (`OcraOperatorUiSeleniumTest`) suites to assert verbose replay behaviour and rendering.  
 ☑ Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.ocra.*VerboseTraceTest"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.OcraCliVerboseTraceTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OcraVerificationEndpointTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.OcraOperatorUiSeleniumTest"`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`

☑ **T3521 – HOTP evaluate trace formatting compliance**  
 ☑ HOTP evaluation traces now emit the mandated six-step breakdown with key mode, inner/outer inputs, and padded result attributes; CLI printer/UI formatter updated for line-per-field layout with refreshed tests (`HotpEvaluationApplicationServiceVerboseTraceTest`, `HotpCliVerboseTraceTest`).  
 ☑ REST payload + OpenAPI snapshots refreshed to surface the ordered attributes list used by the UI renderer.  
 ☑ Command: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.hotp.HotpEvaluationApplicationServiceVerboseTraceTest"` & `:cli:test --tests "io.openauth.sim.cli.HotpCliVerboseTraceTest"`

☑ **T3522 – HOTP verify trace expansion**  
 ☑ Added application-level window scanning with attempt logs and match derivation reuse, propagating trace envelopes through REST/UI plus new verbose test coverage (`HotpReplayApplicationServiceVerboseTraceTest`, `HotpReplayEndpointTest`).  
 ☑ CLI/REST telemetry expectations updated to report next-expected counter while store state remains unchanged; OpenAPI snapshots rewritten.  
 ☑ Command: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.hotp.HotpReplayApplicationService*"`; `:rest-api:test --tests "io.openauth.sim.rest.HotpReplayEndpointTest"`

☑ **T3526 – OCRA segment length metadata**  
 ☑ Extend `OcraEvaluationApplicationService` and `OcraVerificationApplicationService` trace builders to publish `segment.*.len.bytes` and `message.len.bytes` attributes.  
 ☑ Update application, CLI, and REST verbose trace tests to assert representative length fields for stored/inline evaluate and replay flows (UI Selenium coverage unchanged).  
 ☑ Command: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.ocra.*VerboseTraceTest"`; `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.OcraCliVerboseTraceTest"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Ocra*EndpointTest"`; `./gradlew --no-daemon spotlessApply check`

☑ **T3527 – OCRA canonical HMAC labels**  
 ☑ Rename OCRA verbose trace algorithm fields to `alg = HMAC-SHA-*`, update `compute.hmac` step detail/spec anchors, and ensure dual citation of RFC 6287 §7 and RFC 2104.  
 ☑ Refresh application, CLI, and REST verbose trace assertions to expect the new `alg` key/value while keeping formatting stable.  
 ☑ Command: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.ocra.*VerboseTraceTest"`; `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.OcraCliVerboseTraceTest"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Ocra*EndpointTest"`; `./gradlew --no-daemon spotlessApply check`

☐ **T3529 – OCRA message integrity summary**  
 ☐ Add `parts.count` and `parts.order` attributes to the OCRA `assemble.message` trace step so operators can confirm concatenation ordering; reuse them across evaluation and verification traces.  
 ☐ Update application, CLI, and REST trace assertions to check the new summary fields for representative suites.  
 ☐ Command: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.ocra.*VerboseTraceTest"`; `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.OcraCliVerboseTraceTest"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Ocra*EndpointTest"`

☑ **T3530 – FIDO2 canonical trace naming**  
 ☑ Update WebAuthn assertion/attestation trace builders to emit `alg`, `cose.alg`, `rpIdHash.hex`, `clientDataHash.sha256`, `signedBytes.sha256`, and lowercased single-byte flags without `0x` prefixes.  
 ☑ Refresh CLI, REST, and Selenium tests plus OpenAPI snapshots to expect the new field names while keeping payload ordering stable.  
 ☑ Sync Feature 035 docs/spec/plan with the canonical naming and rerun `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.*VerboseTraceTest" :cli:test --tests "io.openauth.sim.cli.Fido2CliVerboseTraceTest" :rest-api:test --tests "io.openauth.sim.rest.Fido2*Test"`.

☑ **T3531 – WebAuthn RP ID canonicalisation plumbing**  
 ☑ Add failing assertions in WebAuthn verbose trace and persistence tests to expect canonical (lower-case, IDNA ASCII) relying party identifiers before hash comparison (e.g., update `WebAuthnAttestationVerificationApplicationServiceVerboseTraceTest`, `WebAuthnEvaluationApplicationServiceVerboseTraceTest`, and relevant CLI/REST fixtures).  
 ☑ Canonicalise RP IDs within credential descriptors, stored credential construction, and attestation/evaluation request builders so persistence and verifier paths share the normalised value.  
 ☑ Command (red → green): `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.*"`; `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.*VerboseTraceTest"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2*Test"`; `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliVerboseTraceTest"`.

☑ **T3532 – WebAuthn trace match indicators**  
 ☑ Extend failing tests to assert `rpId.canonical`, `rpIdHash.expected`, and `rpIdHash.match` attributes in attestation/assertion verbose traces across application, CLI, and REST layers before implementing the fields.  
 ☑ Implement trace updates, propagate canonical value through metadata, and refresh fixtures/OpenAPI snapshots while keeping default (non-verbose) behaviour unchanged.  
 ☑ Command (red → green): `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.*VerboseTraceTest"`; `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliVerboseTraceTest"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2*Test"`; `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`; `./gradlew --no-daemon spotlessApply check`.

☑ **T3533 – WebAuthn flag map & UV policy trace**  
 ☑ Extend WebAuthn assertion/attestation verbose trace tests (application + CLI) to expect `flags.bits.*`, `userVerificationRequired`, and `uv.policy.ok` attributes before wiring the implementation.  
 ☑ Update verbose trace builders and CLI output to emit the full flag map (UP/RFU1/UV/BE/BS/RFU2/AT/ED) plus the policy guard, ensuring attestation surfaces the policy attributes once credential metadata is available.  
 ☑ Command: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.*VerboseTraceTest"`; `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliVerboseTraceTest"`; final `./gradlew --no-daemon spotlessApply check`.


☑ **T3534 – WebAuthn COSE key decode & thumbprint trace**
	☑ Added failing verbose trace assertions across application, CLI, and REST suites (including stored/inline flows) to expect decoded COSE metadata (`cose.kty`, `cose.kty.name`, `cose.alg.name`, curve identifiers, base64url coordinates/modulus/exponent) and the RFC 7638 thumbprint.
	☑ Introduced `core/fido2/CoseKeyInspector` to expose COSE key attribute decoding + thumbprint generation; integrated into WebAuthn evaluation/attestation trace builders while preserving the existing hex output.
	☑ Updated trace builders, CLI output, REST DTO traces, and OpenAPI snapshot to emit the new attributes; commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationServiceVerboseTraceTest"`, `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationVerificationApplicationServiceVerboseTraceTest"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliVerboseTraceTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2EvaluationEndpointTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationManualEndpointTest"`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, final `./gradlew --no-daemon spotlessApply check`.

☑ **T3535 – Verbose trace clearing on context switches**
	☑ Add Selenium coverage that proves the verbose trace panel clears when switching protocols, evaluation/replay tabs, or inline/stored modes.
	☑ Update operator console scripts (core `console.js`, HOTP/TOTP/FIDO2 panels, and OCRA inline handlers) to call `VerboseTraceConsole.clearTrace()` whenever these context changes occur.
	☑ Command: `./gradlew --no-daemon spotlessApply check`.

☑ **T3536 – WebAuthn signature base length & preview metadata**
	☑ Extend WebAuthn verbose trace application tests (evaluation + attestation) and CLI trace expectations to require `authenticatorData.len.bytes`, `clientDataHash.len.bytes`, `signedBytes.hex`, `signedBytes.len.bytes`, and `signedBytes.preview`.
	☑ Update WebAuthn verbose trace builders to populate the new attributes with typed entries (INT for lengths, HEX for concatenated bytes) and introduce a helper that renders the preview (first/last 16 bytes with ellipsis).
	☑ Re-run targeted suites: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.*VerboseTraceTest" :cli:test --tests "io.openauth.sim.cli.Fido2CliVerboseTraceTest"`, then refresh documentation/session snapshot as needed.
