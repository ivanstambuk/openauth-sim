# Feature 026 Tasks – FIDO2/WebAuthn Attestation Support

_Linked plan:_ `docs/4-architecture/feature-plan-026-fido2-attestation-support.md`  
_Status:_ Complete  
_Last updated:_ 2025-10-31

☑ **T2601 – Fixture scaffolding**  
 ☑ Convert selected W3C Level 3 attestation examples and synthetic fixtures into JSON assets under `docs/`.  
 ☑ Add failing core attestation generation/verification tests for packed, FIDO-U2F, TPM, and Android Key formats.  
  _2025-10-16 – Added per-format bundles in `docs/webauthn_attestation/` plus `WebAuthnAttestationFixturesTest`; attestation verifier tests promoted to active coverage alongside I2._  

☑ **T2602 – Core attestation engine implementation**  
 ☑ Implement attestation helpers satisfying T2601 tests, including format-specific validation and error mapping.  
 ☑ Add failure-branch unit tests (bad signatures, unsupported formats, certificate issues).  
  _2025-10-16 – Implemented `WebAuthnAttestationVerifier` with packed/FIDO-U2F/TPM/Android Key verification paths, certificate parsing, and challenge/origin/RP-hash validation; enabled parameterized success cases and added tampered-signature + mismatched-format failure coverage._  

☑ **T2603 – Application services & telemetry**  
 ☑ Stage failing tests for `fido2.attest` / `fido2.attestReplay` telemetry emission.  
  _2025-10-16 – Added attestation verification/replay application tests (`WebAuthnAttestationVerificationApplicationServiceTest`, `WebAuthnAttestationReplayApplicationServiceTest`) and confirmed they failed ahead of service implementation._  
 ☑ Implement generation/replay services with sanitized telemetry and inline-only handling.  
  _2025-10-16 – Implemented attestation verification & replay application services with shared support helpers, trust-anchor enforcement, self-attested fallback warnings, and sanitized telemetry; tests now green via `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestation*"`._  

☑ **T2604 – CLI commands**  
	☑ Add failing Picocli tests covering attestation generation and replay (format selection, validation errors).  
		_2025-10-16 – Added `cli/src/test/java/io/openauth/sim/cli/Fido2CliAttestationTest.java` with self-attested fallback, trust-anchor success, and challenge-mismatch scenarios; currently red until the attestation commands are wired._  
	☑ Implement CLI commands wired to the new services and update help output.  
		_2025-10-16 – Implemented `fido2 attest` and `fido2 attest-replay` commands with trust-anchor parsing, telemetry emission, and doc refresh (`docs/2-how-to/use-fido2-cli-operations.md`); `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationTest"` now passes._  


☑ **T2605 – REST endpoints**  
    ☑ Add failing MockMvc + OpenAPI snapshot tests for `/api/v1/webauthn/attest` and `/api/v1/webauthn/attest/replay`.  
        _2025-10-16 – Added `Fido2AttestationEndpointTest` covering trust-anchor success, self-attested fallback, and challenge-mismatch validation._  
    ☑ Implement controllers/DTOs with validation, telemetry, and sanitized responses; regenerate OpenAPI artifacts.  
        _2025-10-16 – Implemented `WebAuthnAttestationController`/`WebAuthnAttestationService` with PEM trust-anchor parsing, telemetry propagation, and error handling; updated OpenAPI snapshots via `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`. Current targeted verification: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`._  

☑ **T2606 – Operator UI Evaluate toggle**  
 ☑ Add failing Selenium tests asserting the Assertion/Attestation toggle and attestation result rendering.  
  _2025-10-16 – Added Selenium expectations for the attestation toggle, inline-only lock, payload inputs, and trust-anchor helper in `rest-api/src/test/java/io/openauth/sim/rest/ui/Fido2OperatorUiSeleniumTest.java`. Confirmed they fail via `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestation*"` (NoSuchElement for new attestation selectors, as expected)._  
 ☑ Implement Evaluate tab toggle, attestation form fields, and result card updates.  
  _2025-10-16 – Wired the ceremony toggle and attestation inline form in `rest-api/src/main/resources/templates/ui/fido2/panel.html`, locking stored evaluation + exposing trust-anchor guidance, with corresponding controller/model wiring and console.js state machine updates. Selenium coverage now passes via `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestation*"`._  
  _2025-10-20 – Adjusted credential/attestation private-key text-area heights so EC JWK presets render without scrollbars, improving manual edit ergonomics._  

☑ **T2607 – Operator UI Replay attestation support**  
 ☑ Add failing Selenium tests covering attestation replay verification (inline-only) and error messaging.  
  _2025-10-17 – Authored `attestationReplay*` Selenium coverage asserting the Replay tab ceremony toggle, inline-only lock, trust-anchor helper, success metadata, and invalid-anchor messaging. Staged red via `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplay*"`._  
 ☑ Implement Replay tab logic + telemetry display for attestation verification.  
  _2025-10-17 – Extended `panel.html`/`console.js` with replay ceremony toggle, attestation form + result panel, trust-anchor parsing, and telemetry/error wiring; refreshed Selenium suite green (`./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplay*"`). Full quality gate re-ran via `./gradlew --no-daemon spotlessApply check`._  

☑ **T2608 – Fixture ingestion & documentation**  
	☑ Extend fixture loaders/tests (core/app/CLI/REST) to consume the new attestation datasets.  
		_2025-10-17 – Implemented attestation fixture ingestion across core loaders, application samples, CLI summaries, and operator UI models; targeted tests now green via module-specific Gradle invocations._  
	☑ Update how-to guides, knowledge map, and roadmap entry; run `./gradlew spotlessApply check` to close the feature.  
		_2025-10-17 – Refreshed CLI how-to guide, roadmap entry, and knowledge map; `./gradlew --no-daemon spotlessApply check` completed successfully (8m14s) to confirm green build._  

☑ **T2609 – Trust anchor ingestion helpers**  
	☑ Stage failing service/CLI/REST/UI tests for PEM trust-anchor parsing and telemetry updates.  
		_2025-10-17 – Added anchorMode assertions to application/CLI/REST suites and staged CLI failure output for absent telemetry field._  
	☑ Implement shared trust-anchor resolver, session caching, warning propagation, and telemetry wiring across application/CLI/REST/UI; rerun targeted suites.  
		_2025-10-17 – Introduced `WebAuthnTrustAnchorResolver`, cached trust-anchor telemetry, CLI/REST warnings, and metadata propagation; targeted tests now green: `:application:test --tests "...VerificationApplicationServiceTest"`, `:application:test --tests "...ReplayApplicationServiceTest"`, `:cli:test --tests "Fido2CliAttestationTest"`, `:rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`._  

☑ **T2610 – MDS scaffolding**  
	☑ Stage failing metadata ingestion tests covering offline MDS catalogue loading and trust-anchor exposure.  
		_2025-10-17 – Added offline dataset `docs/webauthn_attestation/mds/offline-sample.json` and staged `WebAuthnMetadataCatalogueTest`; command now green after loader implementation via `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnMetadataCatalogueTest"` (16.7s)._   
	☑ Implement offline MDS loader utilities, integrate with trust-anchor storage, document refresh procedure, and rerun targeted suites.  
		_2025-10-17 – `WebAuthnTrustAnchorResolver` now consumes catalogue anchors (metadata/manual/combined), refreshed via `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationTest"` and `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest" --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`; documented the refresh workflow in `docs/2-how-to/refresh-offline-webauthn-metadata.md` and added curated production samples in `docs/webauthn_attestation/mds/curated-mds-v3.json`._  

☑ **T2611 – Generator test scaffolding**  
	☑ Add failing tests capturing generator modes (self-signed, unsigned, custom-root) across core/application/CLI/REST/UI.  
		_2025-10-17 – Staged generator regression coverage: `WebAuthnAttestationGeneratorTest`, `WebAuthnAttestationGenerationApplicationServiceTest`, CLI/REST/Selenium attestation updates expecting generator outputs._  
	☑ Extend OpenAPI snapshots, CLI help expectations, and Selenium specs to assert new payload shapes and button labels.  
		_2025-10-17 – Selenium coverage (`Fido2OperatorUiSeleniumTest.attestationGenerationProducesDeterministicPayload`) now exercises the generator UI, asserting button copy and the rendered attestationObject/clientData/challenge outputs._  
	☑ Capture commands in plan (`:core:test` generator suite, `:application:test` generation service tests, REST/UI suites) so every agent reruns them.  
		_2025-10-17 – Recorded generator/regression command checklist under Feature Plan 026 (I14) covering :core:test, :application:test, Selenium + REST suites, and `spotlessApply check`._  

☑ **T2612 – Core generator implementation**  
	☑ Implement attestation generator utilities with selectable signing modes and deterministic outputs.  
		_2025-10-17 – Added `WebAuthnAttestationGenerator` covering self-signed/unsigned/custom-root paths with fixture validation; staged tests now pass via `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAttestationGeneratorTest"`._  
	☑ Wire fixture catalogues/presets for generator usage and update knowledge map if new helpers appear.  
		_2025-10-17 – Operator UI now consumes attestation presets via `WebAuthnAttestationSamples`; knowledge map updated to document the Evaluate flow’s generator delegation._  
	☑ Add failure-path coverage (unsupported mode, invalid custom-root chain).  
		_2025-10-17 – Added negative coverage across core/application suites (missing custom roots, mismatched formats); verified via `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAttestationGeneratorTest"` and `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationServiceTest"`._  

☑ **T2613 – Facade integration (CLI/REST)**  
	☑ Introduce application-layer generation service, including key/certificate parsing and telemetry.  
		_2025-10-17 – Implemented `WebAuthnAttestationGenerationApplicationService` with sanitized telemetry fields; covered by `WebAuthnAttestationGenerationApplicationServiceTest`._  
	☑ Swap CLI and REST Evaluate flows to call the generator endpoint, returning attestationObject/clientDataJSON/challenge payloads.  
		_2025-10-17 – Rewired `fido2 attest` and `/api/v1/webauthn/attest` to the generator service; CLI/REST tests green via `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationTest"` and `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`._  
	☑ Refresh OpenAPI docs, CLI help text, and tests; ensure Replay retains verification scenarios.  
		_2025-10-17 – Regenerated OpenAPI snapshots (`OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`) and updated CLI how-to guidance; replay flows remain unchanged with existing test coverage._  

☑ **T2614 – Operator UI Evaluate migration**  
	☑ Update Evaluate panel to call generation endpoint, render payload cards, and expose mode selection (self-signed/unsigned/custom-root upload/import).  
		_2025-10-17 – `panel.html` + `console.js` now pre-fill attestation samples, post to `/api/v1/webauthn/attest`, render generated payloads, and support self-signed/unsigned/custom-root signing modes._  
	☑ Adjust button labels and hints to reflect generation semantics.  
		_2025-10-17 – Evaluate copy updated (“Generate attestation”), ceremony heading now reads “Generate a WebAuthn attestation,” and the custom root helper explains inline signing options._  
	☑ Extend Selenium coverage and documentation to reflect the new workflow.  
		_2025-10-17 – Added deterministic generation assertions in `Fido2OperatorUiSeleniumTest`; documentation/knowledge map refreshed alongside Feature Plan 026 updates._  

☑ **T2615 – Checkstyle/PMD remediation**  
	☑ Run `./gradlew --no-daemon checkstyleMain pmdMain` (followed by the full `spotlessApply check`) to collect the current violations.  
		_2025-10-17 – Captured whitespace + unused method issues across application/core/rest-api/cli._  
	☑ Update offending source/tests to satisfy the existing rule sets (Option A confirmed); avoid modifying Checkstyle/PMD configuration.  
		_2025-10-17 – Added record body comments to satisfy whitespace rules and removed the unused CLI attestation verification factory._  
	☑ Re-run `./gradlew --no-daemon spotlessApply check` and record the outcome in `_current-session.md`, the feature plan, and relevant docs.  
		_2025-10-17 – Full pipeline now green (runtime ≈7m46s with SpotBugs/Jacoco); session snapshot/plan updated._  

☑ **T2616 – Attestation result panel trim**  
	☑ Remove Attestation ID / Format / Signing mode / Status rows from the attestation result card template/JS.  
		_2025-10-17 – Trimmed `panel.html` metadata rows, dropped the redundant status line, and simplified `console.js` bindings; expected challenge excerpt dropped in the follow-up iteration._  
	☑ Update Selenium coverage to align with the leaner payload display.  
		_2025-10-17 – Adjusted `Fido2OperatorUiSeleniumTest` to assert metadata elements and telemetry hint are absent while validating payload fields._  
	☑ Re-run `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"` and `./gradlew --no-daemon spotlessApply check`.  
		_2025-10-17 – Both commands pass (REST Selenium ≈75s; full pipeline ≈4m57s, rerun after telemetry/challenge trim)._  

☑ **T2617 – Attestation response payload reduction**  
 ☑ Update application DTOs and transport models to expose only `clientDataJSON` and `attestationObject` in generated responses.  
  _2025-10-18 – Slimmed `WebAuthnAttestationGenerationApplicationService` payloads and adjusted generated DTOs (`WebAuthnGeneratedAttestation`, REST service wiring)._  
 ☑ Remove `expectedChallenge`, certificate PEM arrays, and `signatureIncluded` flags from CLI/REST outputs while keeping telemetry metadata fields.  
  _2025-10-18 – Updated `Fido2Cli`, MockMvc expectations, and telemetry assertions; OpenAPI snapshots regenerated to reflect the trimmed schema._  
 ☑ Refresh OpenAPI snapshots, CLI assertions, Selenium checks, and operator UI rendering to align with the slimmer payload.  
  _2025-10-18 – Reworked `console.js`/`panel.html` to display telemetry summary (signature + cert count), refreshed Selenium coverage, and reran targeted CLI/REST/Selenium suites plus `spotlessApply check`._  
 • Commands: `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`, `./gradlew --no-daemon spotlessApply check`.  

☑ **T2623 – Credential ID alignment**  
 ☑ Emit Base64URL credential IDs for PRESET attestation generation by extracting the credentialId bytes from fixtures.  
  _2025-10-18 – Updated `WebAuthnAttestationGenerator` to expose credentialId bytes and adjusted the application service to encode them for `id`/`rawId` while retaining `attestationId` for fixture references._  
 ☑ Update CLI/REST/UI tests, OpenAPI snapshots, and operator docs to expect Base64URL credential IDs while retaining `attestationId` for fixture references.  
  _2025-10-18 – Refreshed application/REST/Selenium assertions, rerouted certificate-chain display through the UI panel, regenerated OpenAPI snapshots, and reran targeted suites plus `spotlessApply check` (see command list below)._  
 • Commands: `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAttestationGeneratorTest"`, `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationService*"`; `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationTest"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestation*"`; `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`; `./gradlew --no-daemon spotlessApply check`.  

☑ **T2625 – UI certificate chain count badge**  
 ☑ Reintroduce the attestation result certificate-chain count label (matching the pre-T2617 summary styling) while keeping the PEM block separate from the JSON payload.  
  _2025-10-18 – Added a certificate-chain heading indicator in `panel.html`, wired `console.js` to display `certificate chain (N)` using metadata counts, and renewed accessibility defaults when chains are absent._  
 ☑ Update Selenium expectations to assert the count hint and adjust REST metadata assertions if needed.  
  _2025-10-18 – Extended `Fido2OperatorUiSeleniumTest.attestationGenerationProducesDeterministicPayload` to expect the count label using the verified chain size; REST endpoint assertions already validate the metadata count._  
 • Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationGenerationProducesDeterministicPayload"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`; `./gradlew --no-daemon spotlessApply check`.  

☑ **T2626 – UI attestation result layout**  
 ☑ Remove the “response” subheading from the attestation result card so the JSON payload appears directly beneath the card header.  
  _2025-10-18 – Simplified the attestation result layout to mirror assertions by dropping the response subheading and promoting the JSON block, leaving certificate-chain metadata as the only subtitle._  
 ☑ Update frontend logic/tests to ensure the attestation JSON still renders correctly and no stale labels remain.  
  _2025-10-18 – Selenium coverage now asserts the attestation panel exposes a single subtitle (certificate chain), verifying the label removal while UI rendering remains intact._  
 • Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationGenerationProducesDeterministicPayload"`; `./gradlew --no-daemon spotlessApply check`.  

☑ **T2627 – Certificate heading styling**  
	☑ Capitalise the attestation certificate-chain heading (“Certificate chain”) and apply the same typography class used by result cards (`section-title`).  
		_2025-10-18 – Updated `panel.html` to promote the heading to an `h3.section-title` and ensured the default label uses title case._  
	☑ Update JS/tests to expect the new casing and ensure no legacy subtitles remain.  
		_2025-10-18 – Adjusted `console.js` heading text, refreshed Selenium assertions to require the new typography, increased the vertical spacing above the certificate panel via `certificate-chain-block`, and reran targeted UI + spotless checks._  
	• Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationGenerationProducesDeterministicPayload"`; `./gradlew --no-daemon spotlessApply check`.  

☑ **T2629 – Assertion payload sanitization**  
	☑ Update CLI and REST generation outputs to emit only `type`, `id`, `rawId`, and `response` in the credential payload, keeping relying-party metadata in telemetry/metadata sections.  
	 _2025-10-19 – `WebAuthnGeneratedAssertion` + CLI formatter trimmed to spec-compliant PublicKeyCredential shape; relying-party/algorithm/UV details now reside solely in metadata/telemetry.)_  
	☑ Refresh MockMvc/CLI expectations, OpenAPI snapshots, and contributor docs that describe the response structure.  
	 _2025-10-19 – Updated `Fido2EvaluationEndpointTest`, `Fido2CliTest`, regenerated OpenAPI snapshots, and documented the contract under Feature 026 spec/plan/tasks/current-session._  
	☑ Verify UI rendering continues to display the payload with the lean schema.  
	 _2025-10-19 – Selenium checks already ensure presence of `"type": "public-key"`; inline panel renders the lean payload without regression._  
	• Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2EvaluationEndpointTest"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliTest"`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon spotlessApply check`.  

☑ **T2628 – Attestation private key format parity**  
	☑ Replace Base64URL-only attestation private-key handling with shared loaders that accept JWK or PEM/PKCS#8 (matching assertions) while still supporting preset seeding.  
	☑ Stack the credential and attestation private-key textareas vertically in the operator UI to match the assertion layout and remove horizontal scrolling.  
		☑ Convert attestation fixture key material and manual-mode outputs to surface pretty-printed JWK representations; update REST/CLI/UI labels, DTO validation, and core/app services accordingly.  
		☑ Refresh CLI/REST/UI tests, fixtures, and docs to reflect the new formats (including removal of the legacy Base64URL branches).  
			_2025-10-19 – Fixture bundles now publish credential/attestation keys as structured JWK objects; the core loader infers canonical scalars from the JWK `d` field, and new coverage (`WebAuthnAttestationFixturesJwkFormattingTest`) guards key ordering._  
			_2025-10-19 – CLI help/tests, REST MockMvc suites, and operator documentation switched to JWK inputs; `./gradlew spotlessApply check` verified the end-to-end change._  
  • Commands: (to be defined per subtask; expect targeted core/application/REST/CLI/UI suites plus `spotlessApply check`).  

☑ **T2618 – Core Manual input source**  
 ☑ Stage failing unit tests for `WebAuthnAttestationGenerator` MANUAL mode (UNSIGNED/SELF_SIGNED/CUSTOM_ROOT across packed/fido-u2f/tpm/android-key).  
  _2025-10-19 – Parameterized coverage in `WebAuthnAttestationGeneratorTest` drove UNSIGNED/SELF_SIGNED/CUSTOM_ROOT paths red prior to implementation; the suite now passes once manual mode wiring completes._  
 ☑ Implement MANUAL mode without requiring `attestationId`; infer algorithm from key (pending Q5).  
  _2025-10-19 – Manual generation rebuilds authenticator data, COSE public keys, attestation certificates, and signatures (supporting JWK/PEM parsing plus unsigned sanitization). Verification: `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAttestationGeneratorTest"` (green)._  

☑ **T2619 – Application service Manual wiring**  
 ☑ Accept `inputSource` (PRESET default, MANUAL allowed).  
 ☑ When MANUAL: bypass fixture validation, pass-through inputs, and emit telemetry fields {`inputSource`, optional `seedPresetId`, `overrides`}.  
  _2025-10-19 – Manual telemetry coverage now verifies relying-party, AAGUID, and certificate fingerprint fields via `WebAuthnAttestationGenerationApplicationServiceManualTest`; `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationServiceManualTest"` passes after wiring the enrichment._  
  _2025-10-20 – Inline generation defaults `inputSource=PRESET`, rejects manual overrides at the inline level, and telemetry asserts the preset/manual labels via `WebAuthnAttestationGenerationApplicationServiceTest`. Manual-mode DTO passthrough is exercised by `Fido2AttestationEndpointTest.manualInputSourceGeneratesAttestation`. Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationService*"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`, `./gradlew --no-daemon spotlessApply check` (all green)._  
 • Blocked by: Q1, Q5.

☑ **T2620 – REST inputSource + conditional validation**  
 ☑ Extend `WebAuthnAttestationGenerationRequest` with `inputSource` (PRESET|MANUAL).  
 ☑ PRESET → require `attestationId`; MANUAL → require format, rpId, origin, challenge, credential key; require UNSIGNED or attest key + serial (+ optional custom roots).  
 ☑ Reshape generation response to match assertion schema (`type`/`id`/`rawId` + nested `response` containing only `clientDataJSON` and `attestationObject`) and update OpenAPI snapshots/tests.  
  _2025-10-20 – Completed: reject unsupported `inputSource` values with `input_source_invalid`, refreshed attestation endpoint tests, and reran `:rest-api:test` + `spotlessApply check`._  
 • Blocked by: — (Clarifications resolved 2025-10-18).

☑ **T2621 – UI auto-switch + Manual mode**  
 ☑ Detect overrides of challenge/RP ID/origin/credential+attestation keys/serial and flip to Manual; show a small “Manual” hint near Generate.  
 ☑ Send `inputSource=MANUAL` and omit `attestationId` when in Manual.  
 ☑ Render attestation results from the nested response object (clientDataJSON + attestationObject only), surfacing signature/certificate statistics via telemetry metadata.  
 ☑ Owner declined the “Copy preset ID” link; no additional action required.  
  _2025-10-20 – Completed: attestation form now surfaces a live mode indicator (preset vs manual), auto-detects overrides, and Selenium verifies the toggle behaviour._  
  • Blocked by: — (Clarifications resolved 2025-10-18).

☑ **T2622 – CLI parity for Manual**  
	☑ Extended Picocli options to accept `--input-source=manual` (plus `--seed-preset-id`/`--override`), enforced Manual-mode validation, and defaulted to presets.  
	☑ Added Manual success + missing-credential-key tests ahead of implementation (`Fido2CliAttestationTest`), then drove them green.  
	☑ Updated CLI usage strings and `docs/2-how-to/use-fido2-cli-operations.md` with Manual-mode guidance.  
	☑ Ran `./gradlew --no-daemon :cli:test` and `./gradlew --no-daemon spotlessApply check` (coverage guard satisfied after expanding Manual-mode tests).

☑ **T2630 – Attestation preset label harmonisation**  
☑ Update `OperatorConsoleController` attestation label builder to emit `<algorithm> (format, W3C <section>)` with origin fallback when no section exists.  
☑ Extend `OperatorConsoleControllerAttestationTest` (or similar unit coverage) to assert the new label for the packed ES256 vector.  
☑ Run `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.OperatorConsoleControllerAttestationTest"` and `./gradlew --no-daemon spotlessApply check`.  
		_2025-10-20 – Updated label builder, added JSON assertion coverage for the packed ES256 vector, reran targeted `:rest-api:test` plus full `spotlessApply check` (second invocation required an extended timeout)._ 

☑ **T2631 – Credential store attestation schema**  
	☑ Stage failing persistence/application tests ensuring MapDB persists attestation descriptors (format, signing mode, certificate chain, attestation private key reference) alongside assertions.  
	☑ Extend `WebAuthnCredentialPersistenceAdapter` (and related DTOs) to serialize stored attestation credentials, update schema metadata, and document migration notes.  
		_2025-10-20 – Added `WebAuthnAttestationPersistenceAdapterTest`, `WebAuthnCredentialStoreAttestationTest`, and `WebAuthnCredentialPersistenceSmokeTest` (initially red). Implemented the new attestation descriptor record, taught the persistence adapter to serialize attestation metadata, and rehydrated MapDB records with certificate chains/private keys. All targeted tests now pass._  
	• Commands: `./gradlew --no-daemon :infra-persistence:test --tests "io.openauth.sim.infra.persistence.webauthn.WebAuthnCredentialStoreAttestationTest"`, `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnCredentialPersistenceSmokeTest"`, `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAttestationPersistenceAdapterTest"`, followed by `./gradlew --no-daemon spotlessApply check`.  

☑ **T2632 – Stored attestation generation services**  
	☑ Add failing application tests (`WebAuthnAttestationGenerationStoredTest`, `WebAuthnAttestationReplayStoredTest`) exercising `inputSource=STORED`, missing-credential errors, and telemetry enrichment (`storedCredentialId`, `seedPresetId`, `inputSource`).  
	☑ Implement service wiring to resolve MapDB entries, reuse manual validation, and emit detailed telemetry; ensure replay flows reuse the same lookup path.  
		_2025-10-20 – Stored generation now loads attestation descriptors from MapDB, regenerates attestation payloads via the core generator, reuses stored certificate chains, and emits telemetry (`inputSource=stored`, `storedCredentialId`). Stored replay resolves attestation payloads from persistence attributes, verifies them through `WebAuthnAttestationServiceSupport`, and surfaces the same telemetry enrichment._  
	• Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationGenerationStoredTest" --tests "io.openauth.sim.application.fido2.WebAuthnAttestationReplayStoredTest"`, `./gradlew --no-daemon :application:test`.  

☑ **T2633 – CLI/REST stored flow support**  
	☑ Stage failing Picocli and MockMvc tests covering `inputSource=stored`, stored credential selection, and not-found validation (`Fido2CliAttestationStoredTest`, `Fido2AttestationStoredEndpointTest`).  
		_2025-10-20 – Added CLI stored-mode coverage asserting success, missing-credential validation, and not-found handling; mirrored REST MockMvc tests to expect deterministic payloads, telemetry (`inputSource=stored`, `storedCredentialId`), and validation errors. Both suites intentionally red via `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationStoredTest"` and `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationStoredEndpointTest"`._  
	☑ Update CLI flags/help (`--input-source=stored`, `--credential-id`) and REST DTO validation (`credentialId`, Base64URL `challenge`, optional RP/origin overrides) to bypass manual-only requirements, resolve MapDB entries, and propagate telemetry; refresh OpenAPI snapshots and CLI docs.  
		_2025-10-21 – CLI stored generation now loads MapDB-backed descriptors and surfaces telemetry (`inputSource=stored`, `storedCredentialId`); REST controller/service accept `inputSource=STORED`, enforce `credentialId`/challenge validation, reuse generation fallback mapping, and expose stored metadata. Targeted suites plus OpenAPI snapshot updates run green._  
	• Commands: `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationStoredTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationStoredEndpointTest" --tests "io.openauth.sim.rest.Fido2AttestationStoredOperatorControllerTest"`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, and `./gradlew --no-daemon spotlessApply check`.  

☑ **T2634 – Operator UI stored selector**  
	☑ Add failing Selenium/controller tests asserting Preset/Manual/Stored radio buttons, stored credential list rendering, and error display when selection fails.  
		_2025-10-20 – Added `Fido2OperatorUiSeleniumTest.attestationStoredModeRendersStoredForm` plus `Fido2AttestationStoredOperatorControllerTest`; both suites currently red, confirming UI expectations for stored attestation selectors and operator API metadata. Verified failures via `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationStoredModeRendersStoredForm"` and `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationStoredOperatorControllerTest"`._  
	☑ Implement UI state management (`console.js`, `panel.html`) to fetch stored credential summaries, toggle input panels, and submit `inputSource=STORED`; verify manual/preset regressions with existing suites.  
		_2025-10-21 – Console now preloads credential selectors, fetches attestation metadata/seed results via new endpoints, and disables manual-only inputs while stored mode is active; Selenium stored-mode, seeding, and replay scenarios pass._  
	• Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationStored*"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationStoredOperatorControllerTest"`, Selenium stored seed suite, and `./gradlew --no-daemon spotlessApply check`.  

☑ **T2635 – Seed stored attestation credentials**  
	☑ Stage failing application/CLI/REST/UI tests for a deterministic seed helper (idempotent MapDB writes, telemetry, operator feedback) (`WebAuthnAttestationSeedServiceTest`, `Fido2CliAttestationSeedTest`, `Fido2AttestationSeedEndpointTest`, Selenium stored seed spec).  
		_2025-10-20 – Added application seeding specification (`WebAuthnAttestationSeedServiceTest` + stub service), CLI flow (`Fido2CliAttestationSeedTest`), REST endpoint coverage (`Fido2AttestationSeedEndpointTest`), and operator console seeding Selenium test (`attestationSeedPopulatesStoredCredentials`). Verified red runs via `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationSeedServiceTest"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationSeedTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationSeedEndpointTest"`, and `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationSeedPopulatesStoredCredentials"`._  
	☑ Implement fixture-driven seed workflow, expose it via CLI (`seed-attestations`), REST (`POST /api/v1/webauthn/attestations/seed`), and operator UI control, update documentation/knowledge map/roadmap, and ensure telemetry captures seeding results.  
		_2025-10-21 – Application seed service promotes canonical stored fixtures (including persisted attestation payloads) and CLI/REST commands surface idempotent output; operator UI now offers stored-mode seeding and selectors for attestation credentials. Targeted suites + full `:rest-api:test` and `spotlessApply check` run green._  
	• Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationSeedServiceTest"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationSeedTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationSeedEndpointTest" --tests "io.openauth.sim.rest.Fido2AttestationStoredEndpointTest" --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`, Selenium stored seed suite, and `./gradlew --no-daemon spotlessApply check`.  

☑ **T2636 – Stored attestation replay test scaffolding**  
	☑ Extend application (`WebAuthnAttestationReplayStoredTest`), CLI (`Fido2CliAttestationReplayStoredTest`), and REST (`Fido2AttestationReplayStoredEndpointTest`) suites to cover `inputSource=stored` replay flows; record expected red runs before implementation.  
	☑ Add Selenium coverage (`Fido2OperatorUiSeleniumTest.attestationReplayStoredMode`) asserting Manual/Stored toggles, stored credential selection, challenge validation, and absence of the attestation identifier field.  
		_2025-10-27 - Stored replay scaffolding now green across application/CLI/REST/Selenium suites after wiring stored input handling; HtmlUnit flow waits for metadata hydration before submit._  
	• Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationReplayStoredTest"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationReplayStoredTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationReplayStoredEndpointTest" --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplayStoredMode"`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"` (green as of 2025-10-27).  

☑ **T2637 – Stored attestation replay implementation**  
	☑ Implement REST `inputSource=STORED` replay handling (DTO validation, service/controller branches), CLI flag plumbing, and telemetry enrichment mirroring stored generation reason codes.  
		- CLI: introduce `--input-source` on `attest-replay`, require `--credential-id` in stored mode, reuse trust-anchor parsing for inline mode, and delegate to `ReplayCommand.Stored` so telemetry carries `storedCredentialId`.  
		- REST: extend `WebAuthnAttestationReplayRequest` with `inputSource` + `credentialId`, branch in `WebAuthnAttestationService.replay` to construct stored commands without demanding inline payload fields, and refresh OpenAPI documents to advertise the new schema/validation.  
		- UI: align with stored replay toggle (covered under T2638) but ensure API responds with stored challenge/clientData/attestationObject to hydrate Selenium expectations.  
	☑ Drive T2636 tests green, refresh OpenAPI snapshots, and document any new reason codes.  
		_2025-10-27 - CLI `attest-replay` now supports `--input-source stored` + `--credential-id`, REST payloads accept `inputSource`/`credentialId`, metadata returns stored challenge, and OpenAPI snapshots capture the new schema/reason codes._  
	• Commands: Targeted module suites plus `./gradlew --no-daemon :application:test :cli:test :rest-api:test spotlessApply check`.  

☑ **T2638 – Operator UI stored replay & identifier removal**  
	☑ Update `panel.html`/`console.js` to surface stored replay, hydrate fields via `/api/v1/webauthn/attestations/{id}`, and remove the user-facing attestation identifier input.  
	☑ Extend Selenium + controller tests to verify stored replay submissions, trust-anchor messaging, and the missing identifier field across evaluate/replay panels.  
		_2025-10-27 - Stored replay form renders read-only RP/origin/challenge/format fields, submit button remains enabled, and Selenium verifies stored submissions alongside identifier removal._  
	• Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplayStoredMode" --tests "io.openauth.sim.rest.Fido2AttestationStoredOperatorControllerTest"`, `./gradlew --no-daemon spotlessApply check`.  

☑ **T2639 – Documentation & telemetry sync**  
	☑ Update how-to guides, knowledge map, and roadmap with stored replay guidance and UI field removal; capture telemetry sample frames.  
	☑ Run full regression (`./gradlew --no-daemon :application:test :cli:test :rest-api:test spotlessApply check`) and note lessons in this task file/plan.  
		_2025-10-27 - Updated CLI/REST/operator UI how-tos, roadmap, and knowledge map with stored attestation replay details; full suite (:application:test :cli:test :rest-api:test spotlessApply check) now green with refreshed OpenAPI snapshots._  

☑ **T2640 – Attestation replay sample loader parity**  
	☑ Add failing Selenium coverage asserting the attestation replay inline form renders a “Load a sample vector” selector and hydrates inline fields from curated fixtures.  
	☑ Reintroduce the preset dropdown and console wiring (without restoring the Preset radio mode), verify stored mode remains unaffected, and rerun targeted + full gradle checks.  
		_2025-10-27 – Added `attestationReplayInlineSampleLoadsPreset` Selenium spec (red), restored the inline preset selector + JS handlers using attestation vector metadata, and refreshed targeted/UI + full Gradle suites (all green)._  
	• Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplayInlineSampleLoadsPreset"`, `./gradlew --no-daemon :application:test :cli:test :rest-api:test spotlessApply check`.  

☑ **T2641 – Stored replay payload display**  
	☑ Added failing Selenium spec `attestationReplayStoredModeDisplaysPersistedPayloads` (superseding placeholder name) to require read-only `attestationObject`/`clientDataJSON` hydration for stored attestation replay.  
	☑ Updated `panel.html` and `console.js` to render read-only payload text areas and populate them from `/api/v1/webauthn/attestations/{id}` metadata, mirroring manual replay layout without enabling edits.  
	• Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplayStoredModeDisplaysPersistedPayloads"`, `./gradlew --no-daemon :application:test :cli:test :rest-api:test spotlessApply check`.  

☑ **T2642 – Stored replay challenge hydration fix**  
	☑ Exercised application Selenium coverage to enforce non-empty stored replay challenge values and wired console metadata helpers to clear/set challenge, attestationObject, and clientData from MapDB response payloads.  
	☑ Confirmed application service contract still guards missing payload attributes (no code change required) while UI hydration now relies on persisted metadata before inline vector fallbacks.  
	• Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationReplayStoredTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplayStoredModeDisplaysPersistedPayloads"`, `./gradlew --no-daemon :application:test :cli:test :rest-api:test spotlessApply check`.  

☑ **T2643 – Stored attestation label normalization**  
	☑ Add failing unit/UI tests asserting stored attestation dropdown entries render algorithm/W3C labels instead of raw vector identifiers.  
	☑ Persist `fido2.metadata.label` when seeding attestation credentials and update console label resolution to fall back to curated labels when metadata is absent.  
		_2025-10-28 – Application and CLI seeding tests now assert canonical labels, Selenium verifies stored dropdown text, and console formatting prefers curated metadata while retaining algorithm/format fallbacks. Added REST `VerboseTracePayloadTest` to lock hex formatting and prevent Jacoco regressions._  
	• Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationSeedServiceTest"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationSeedTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplayStoredLabels" --tests "io.openauth.sim.rest.VerboseTracePayloadTest"`, `./gradlew --no-daemon jacocoAggregatedReport`, `./gradlew --no-daemon :application:test :cli:test :rest-api:test spotlessApply check`.  

☑ **T2644 – Unified stored credential seeding**  
	☑ Stage failing application + REST/UI tests that seed assertion credentials first, run the attestation seeder, and assert only one credential record remains with both assertion and attestation attributes populated.  
	☑ Implement seeding changes so attestation workflows update existing credentials rather than persisting duplicates, update documentation/how-to guidance, and refresh OpenAPI snapshots if needed.  
	• Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationSeedServiceTest"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationSeedTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationSeedEndpointTest"` (followed by `./gradlew --no-daemon :application:test :cli:test :rest-api:test spotlessApply check`).  
	 _2025-10-28 – Attestation seeding now preserves the canonical assertion credential secret while layering stored attestation payload attributes; CLI/REST seeding flows stay idempotent and no duplicate dropdown entries surface._  

☑ **T2645 – Synthetic PS256 attestation fixture**  
    ☑ Record the 2025-10-28 clarification in the spec/plan/tasks noting the synthetic packed PS256 fixture requirement.  
    ☑ Add failing tests: `WebAuthnAttestationFixturesTest` (core), `WebAuthnAttestationSeedServiceTest` (application), `Fido2CliAttestationSeedTest` (CLI), and REST seed + Selenium stored attestation specs to require PS256 metadata/challenge hydration.  
    ☑ Author the synthetic packed PS256 attestation entry under `docs/webauthn_attestation/packed.json`, regenerate any fixture indexes, and update seeding/UI logic until staged tests pass.  
    ☑ Update operator/CLI/REST how-to guidance once the fixture is live, then run `./gradlew --no-daemon :application:test :cli:test :rest-api:test spotlessApply check`.  
    _2025-10-28 – Added `synthetic-packed-ps256` to the packed attestation catalogue with deterministic RSA-PSS material, updated core/application/CLI/REST/Selenium tests to demand the new preset, and taught seeders to derive stored metadata from generator samples when no W3C fixture exists. Operator/CLI/REST how-tos now call out the PS256 preset._  
    • Commands: `./gradlew --no-daemon :application:test :cli:test :rest-api:test spotlessApply check`

☑ **T2646 – Stored replay dropdown stabilisation**  
    ☑ Update Selenium stored replay coverage to wait for the target credential option before selection, preventing `IllegalStateException` when MapDB hydration lags.  
    ☑ Run `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplayStoredModeDisplaysPersistedPayloads"` and full `./gradlew --no-daemon spotlessApply check`.  
    _2025-10-28 – Reused the `waitForOption` helper ahead of selecting stored attestation credentials, reran the targeted Selenium test, and completed the full spotless/check pipeline._  

☑ **T2647 – Stored replay read-only styling parity**  
    ☑ Update the Feature 026 spec/plan to require muted styling for read-only stored attestation fields.  
    ☑ Extend `console.css` so read-only textarea elements adopt the same disabled background/cursor treatment used for readonly inputs, keeping stored replay payloads visibly locked.  
    ☑ Run `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplayStoredModeDisplaysPersistedPayloads"` and full `./gradlew --no-daemon spotlessApply check`.  
    _2025-10-28 – Added UI styling requirement to spec/plan, updated `console.css` so stored replay textareas inherit the read-only palette, visually confirming the change and rerunning full spotless/check (includes Selenium scenario) to ensure regressions stay green._   

☑ **T2648 – Curated trust anchor selector**  
    ☑ Stage failing coverage: extend application tests for metadata id resolution, add CLI replay spec covering `--metadata-anchor` multi-select, REST unit test expecting `metadataAnchorIds`, and a Selenium scenario verifying the filtered multi-select plus selection summary.  
        _2025-10-30 – `WebAuthnTrustAnchorResolverMetadataTest` (application) exercises metadata-id selections and combined manual anchors; now green via `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnTrustAnchorResolverMetadataTest"`._  
        _2025-10-30 – `Fido2CliAttestationReplayMetadataAnchorsTest` validates repeatable `--metadata-anchor` behaviour; green via `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationReplayMetadataAnchorsTest"`._  
        _2025-10-30 – `io.openauth.sim.rest.webauthn.Fido2AttestationReplayMetadataAnchorTest` verifies the controller forwards identifier arrays and echoes them in metadata; green via `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.webauthn.Fido2AttestationReplayMetadataAnchorTest"`._  
        _2025-10-30 – Selenium scenario `Fido2OperatorUiSeleniumTest.attestationReplayManualMetadataAnchors` confirms the filtered multi-select rendering; green via `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplayManualMetadataAnchors"`._  
    ☑ Implement metadata-id plumbing across CLI/REST/UI: accept metadata entry identifiers alongside PEM bundles, update `WebAuthnTrustAnchorResolver` call sites, render the format-filtered multi-select, and ensure telemetry records selected metadata ids.  
        _2025-10-30 – Resolver now merges curated selections with manual PEM bundles while exposing identifier lists to telemetry; CLI/REST request paths reject metadata anchors for stored replay and surface selection counts in traces._  
        _2025-10-30 – Operator UI keeps curated anchors unselected on vector load, highlights recommended entries in the summary, and preserves the PEM textarea for manual roots; Selenium verifies manual replay plus inline sample flows._  
    ☑ Regenerate OpenAPI snapshots, refresh CLI/operator documentation, and rerun the full quality gate (`./gradlew --no-daemon :application:test :cli:test :rest-api:test spotlessApply check`).  
        _2025-10-30 – Snapshot regenerated with `metadataAnchorIds` schema, documentation entries synced via feature plan updates, and full `./gradlew --no-daemon :application:test :cli:test :rest-api:test spotlessApply check` recorded with a green result._  
    • Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnTrustAnchorResolverMetadataTest"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationReplayMetadataAnchorsTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.webauthn.Fido2AttestationReplayMetadataAnchorTest" --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplayManualMetadataAnchors" --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplayInlineSampleLoadsPreset"`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon :application:test :cli:test :rest-api:test spotlessApply check`.  

☑ **T2649 – Stored replay trust-anchor summary**  
    ☑ Update spec/plan/tasks to capture the read-only anchor summary requirement and reopen the increment.  
    ☑ Extend the stored attestation metadata endpoint with `trustAnchorSummaries`, covering curated metadata ids and certificate-subject fallbacks in unit tests.  
    ☑ Render the summary in the operator console stored replay panel (read-only), refresh Selenium coverage, and rerun `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplayStoredModeDisplaysPersistedPayloads"` plus the full `./gradlew --no-daemon :application:test :cli:test :rest-api:test spotlessApply check`.  
_2025-10-31 – Spec/plan/tasks/session/knowledge-map updated for Option B; stored metadata endpoint now returns `trustAnchorSummaries` (metadata description fallback or certificate subjects) with controller + unit coverage, operator console renders the read-only textarea, Selenium asserts populated summary, OpenAPI snapshots regenerated, and Gradle suite `./gradlew --no-daemon :application:test :cli:test :rest-api:test spotlessApply check` completed after reseeding._  

**Acceptance**  
- 2025-10-31 – Feature 026 closed after trust-anchor summaries were validated across REST/operator UI facets and full Gradle verification succeeded.
