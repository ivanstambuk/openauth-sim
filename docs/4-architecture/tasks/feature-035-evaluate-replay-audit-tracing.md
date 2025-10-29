# Feature 035 Tasks – Evaluate & Replay Audit Tracing

_Linked plan:_ `docs/4-architecture/feature-plan-035-evaluate-replay-audit-tracing.md`  
_Status:_ Complete  
_Last updated:_ 2025-10-29

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

☑ **T3520 – Documentation & follow-up logging**  
 ☑ Update operator/CLI/REST docs with enriched trace examples, note the tier metadata, and record the redaction-toggle follow-up (2025-10-26 – guides refreshed; Feature 036 holds tier helper follow-up).  
 ☑ Sync knowledge map and `_current-session.md` with the enrichment outcomes (2025-10-29 – roadmap, knowledge map, and session snapshot capture verbose trace completion).  

☑ **T3541 – WebAuthn extensions trace tests (red)**  
 ☑ 2025-10-26 – Extended application verbose trace suites (assertion + attestation) with extension-enabled fixtures and verified `parse.extensions` captures raw CBOR plus decoded credProps/credProtect/largeBlob/hmac-secret attributes.  
 ☑ 2025-10-26 – Propagated expectations to CLI (`Fido2CliVerboseTraceTest`), REST (`Fido2ReplayEndpointTest`), and operator UI Selenium (`Fido2OperatorUiSeleniumTest.verboseTraceSurfacesExtensionMetadataForInlineReplay`) suites; all now assert decoded extension metadata.  
 ☑ 2025-10-26 – Command: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.*VerboseTraceTest" :cli:test --tests "io.openauth.sim.cli.Fido2CliVerboseTraceTest" :rest-api:test --tests "io.openauth.sim.rest.Fido2*Test" --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"` (green).  
 ☑ 2025-10-25 – CLI (`Fido2CliVerboseTraceTest`) and REST (`Fido2ReplayEndpointTest`) suites now assert the presence of `parse.extensions` with decoded credProps/credProtect/largeBlob/hmac-secret metadata.  
 ☑ 2025-10-25 – UI Selenium (`Fido2OperatorUiSeleniumTest.verboseTraceSurfacesExtensionMetadataForInlineReplay`) verifies verbose inline replay surfaces `parse.extensions` with credProps/credProtect/largeBlob/hmac-secret attributes.  

☑ **T3542 – WebAuthn extensions parsing & trace implementation (green)**  
	☑ 2025-10-26 – Shared `WebAuthnAuthenticatorDataParser` to expose extension CBOR bytes for assertion/attestation flows; decode known extension keys into typed values while retaining unknown entries for trace emission.  
	☑ 2025-10-26 – Wired the `parse.extensions` trace step across application services and CLI/REST formatters so decoded metadata and unknown entries surface consistently (under `extensions.unknown.*`).  
	☑ Adjusted Selenium inline replay coverage to wait for non-pending badge text (eliminating the race with the fetch response) so the existing UI update is asserted reliably.  
		- Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2ReplayEndpointTest.inlineReplayWithExtensionsReturnsExtensionMetadata"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.verboseTraceSurfacesExtensionMetadataForInlineReplay"`  
	☑ 2025-10-26 – Reran targeted suites until green: `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.*" :application:test --tests "io.openauth.sim.application.fido2.*VerboseTraceTest" :cli:test --tests "io.openauth.sim.cli.Fido2CliVerboseTraceTest" :rest-api:test --tests "io.openauth.sim.rest.Fido2*Test"`.

☑ **T3543 – Facade documentation & trace sample sync**  
 ☑ 2025-10-26 – Updated CLI (`docs/2-how-to/use-fido2-cli-operations.md`), REST (`docs/2-how-to/use-fido2-rest-operations.md`), and operator UI (`docs/2-how-to/use-fido2-operator-ui.md`) guides with extension-aware verbose trace snippets and guidance on `extensions.unknown.*`.  
 ☑ 2025-10-26 – No OpenAPI schema changes detected; existing snapshots remain valid.  
 ☑ 2025-10-26 – Re-ran `./gradlew --no-daemon spotlessApply check` to confirm documentation edits keep the quality gate green.

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

☑ **T3529 – OCRA message integrity summary**  
 ☑ Added `parts.count` and `parts.order` attributes to OCRA `assemble.message` traces across application/CLI/REST suites (2025-10-24 – tests now assert the summaries alongside length metadata).  
 ☑ Updated operator documentation (CLI + REST how-tos) and the Feature 035 specification to explain the new integrity summary fields for auditors (2025-10-25).  
 ☑ Command: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.ocra.*VerboseTraceTest"`; `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.OcraCliVerboseTraceTest"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Ocra*EndpointTest"`

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

☑ **T3537 – WebAuthn signature inspection tests (red)**
	☑ Extended `WebAuthnEvaluationApplicationServiceVerboseTraceTest` and `WebAuthnAttestationVerificationApplicationServiceVerboseTraceTest` to validate signature trace fields, including DER/Base64 encodings, R/S hex components, low-S status, and new policy/validation flags. Added CLI coverage to assert human-readable traces contain the new attributes.
	☑ Command: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.*VerboseTraceTest"`; `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliVerboseTraceTest"`.

☑ **T3538 – Signature inspector implementation (green)**
	☑ Introduced `SignatureInspector` with DER parsing, low-S detection for ES256/384/512, RSA metadata helpers, and base64url exports. Integrated the helper into assertion/attestation trace builders and added unit coverage in `SignatureInspectorTest`.
	☑ Command: `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.SignatureInspectorTest"`; `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.*VerboseTraceTest"`.

☑ **T3539 – Low-S policy wiring & enforcement**
	☑ Added `WebAuthnSignaturePolicy`, defaulting to observe-only, and plumbed enforcement through assertion and attestation services. Traces now emit `policy.lowS.enforced`; high-S signatures trigger `error.lowS`, `verify.ok=false`, and fail verification when the policy is enabled. Added tests exercising enforced-mode behavior with synthetic high-S signatures.
	☑ Command: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.*VerboseTraceTest"`.

☑ **T3540 – Facade + documentation sync**
	☑ 2025-10-25 – Extended REST WebAuthn replay/attestation tests to assert the new `verify.signature` schema, taught `WebAuthnAttestationService` to emit signature inspection steps, and refreshed FIDO2 REST how-to + knowledge map entries. CLI/UI already reflected the attributes from previous increments, so no renderer changes were required.
	☑ Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2*Test"`; `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`; attempted `./gradlew --no-daemon spotlessApply check` (timed out after 5 min due to pre-existing Checkstyle/PMD findings in SignatureInspector suites).
	☑ 2025-10-25 – Follow-up: coverage regression traced to unexecuted `SignatureInspector` fallback branches; added malformed-signature tests across application/REST suites and re-ran `./gradlew --no-daemon jacocoCoverageVerification` (green at 85 % line / 70 % branch). Normalised empty record declarations to satisfy Checkstyle’s `WhitespaceAround` and confirmed `./gradlew --no-daemon checkstyleMain checkstyleTest` plus `./gradlew --no-daemon spotlessApply check` now succeed.

☑ **T3544 – WebAuthn trace step restructure tests (red)**
	☑ 2025-10-26 – Extended application (`WebAuthnEvaluation/AttestationVerboseTraceTest`), CLI (`Fido2CliVerboseTraceTest`), REST (`Fido2ReplayEndpointTest`, `Fido2AttestationManualEndpointTest`), and operator UI Selenium suites to require the new step IDs plus `tokenBinding.status/id`, `rpIdHash.expected`, and `signedBytes.preview`; suites now fail until trace builders emit the expanded metadata.
	☑ Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.*VerboseTraceTest"`; `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliVerboseTraceTest"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2*Test"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"` (expected red until T3545).

☑ **T3545 – WebAuthn trace step restructure implementation (green)**
	☑ 2025-10-26 – Appended application-layer WebAuthn verification traces into REST/CLI/UI flows, set default token-binding metadata, merged signed-bytes preview reporting, and removed obsolete REST signature helpers.
	☑ Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.*VerboseTraceTest" :cli:test --tests "io.openauth.sim.cli.Fido2CliVerboseTraceTest" :rest-api:test --tests "io.openauth.sim.rest.Fido2*Test" :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon spotlessApply check`.

☑ **T3546 – WebAuthn generation trace tests (red)**
	☑ Extended fail-first coverage for WebAuthn generation traces across application (assertion + attestation services), CLI `fido2 evaluate` flows, REST evaluation/attestation endpoints, and Selenium UI. New tests require the build/generate steps and SHA-256 hashing placeholders and currently fail pending implementation (see Gradle runs below).
	☑ Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationServiceTest"`; `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationServiceManualTest"`; `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliVerboseTraceTest"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"` (each run red until T3547).

☑ **T3547 – WebAuthn generation trace implementation (green)**
	☑ 2025-10-26 – Added shared generation trace builders for assertion/attestation services, hashed sensitive inputs (`clientData.sha256`, `rpIdHash.expected`, `privateKey.sha256`) without re-hashing derived digests, and plumbed the verbose trace payload through CLI stored/inline evaluation (`--verbose`) plus REST evaluation/attestation endpoints and the operator UI. Updated how-to/OpenAPI references remain unchanged (no new schema fields beyond verbose trace metadata).
	☑ Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationServiceTest"`, `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationServiceManualTest"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliVerboseTraceTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2EvaluationEndpointTest"` `--tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"` `--tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`, `./gradlew --no-daemon spotlessApply check`.

☑ **T3548 – WebAuthn stored credential ordering alignment (Option A)**
	☑ Captured the Option A directive in spec/plan/tasks (2025-10-26) and confirmed the pre-change selector ordering for regression notes.
	☑ Updated `WebAuthnCredentialDirectoryController` to sort summaries by `WebAuthnSignatureAlgorithm` ordinal with label/ID fallbacks; added `WebAuthnCredentialDirectoryControllerTest` covering mixed algorithm/metadata cases.
	☑ Mirrored the ordering helper in `rest-api/src/main/resources/static/ui/fido2/console.js`, normalising algorithm labels for API/seed data; refreshed Selenium expectations to rely on the shared algorithm-first ordering.
	☑ Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.webauthn.WebAuthnCredentialDirectoryControllerTest"`; `./gradlew --no-daemon spotlessApply check` (includes Selenium suite).
