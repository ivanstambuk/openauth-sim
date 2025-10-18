# Feature 026 Tasks – FIDO2/WebAuthn Attestation Support

_Linked plan:_ `docs/4-architecture/feature-plan-026-fido2-attestation-support.md`  
_Status:_ In Progress  
_Last updated:_ 2025-10-18

☑ **T2601 – Fixture scaffolding**  
 ☑ Convert selected W3C Level 3 attestation examples and synthetic fixtures into JSON assets under `docs/`.  
 ☑ Add failing core attestation generation/verification tests for packed, FIDO-U2F, TPM, and Android Key formats.  
  _2025-10-16 – Added per-format bundles in `docs/webauthn_attestation/` plus `WebAuthnAttestationFixturesTest`; attestation verifier tests promoted to active coverage alongside I2._  

☑ **T2602 – Core attestation engine implementation**  
 ☑ Implement attestation helpers satisfying T2601 tests, including format-specific validation and error mapping.  
 ☑ Add failure-branch unit tests (bad signatures, unsupported formats, certificate issues).  
  _2025-10-16 – Implemented `WebAuthnAttestationVerifier` with packed/FIDO-U2F/TPM/Android Key verification paths, certificate parsing, and challenge/origin/RP-hash validation; enabled parameterized success cases and added tampered-signature + mismatched-format failure coverage._  

☐ **T2603 – Application services & telemetry**  
 ☑ Stage failing tests for `fido2.attest` / `fido2.attestReplay` telemetry emission.  
  _2025-10-16 – Added attestation verification/replay application tests (`WebAuthnAttestationVerificationApplicationServiceTest`, `WebAuthnAttestationReplayApplicationServiceTest`); both currently red pending service implementation._  
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

☐ **T2618 – Core Manual input source**  
 ☐ Stage failing unit tests for `WebAuthnAttestationGenerator` MANUAL mode (UNSIGNED/SELF_SIGNED/CUSTOM_ROOT across packed/fido-u2f/tpm/android-key).  
 ☐ Implement MANUAL mode without requiring `attestationId`; infer algorithm from key (pending Q5).  
 • Blocked by: Q1 (AAGUID default/override), Q2 (CUSTOM_ROOT chain length), Q5 (algorithm inference).

☐ **T2619 – Application service Manual wiring**  
 ☐ Accept `inputSource` (PRESET default, MANUAL allowed).  
 ☐ When MANUAL: bypass fixture validation, pass-through inputs, and emit telemetry fields {`inputSource`, optional `seedPresetId`, `overrides`}.  
 • Blocked by: Q1, Q5.

☐ **T2620 – REST inputSource + conditional validation**  
 ☐ Extend `WebAuthnAttestationGenerationRequest` with `inputSource` (PRESET|MANUAL).  
 ☐ PRESET → require `attestationId`; MANUAL → require format, rpId, origin, challenge, credential key; require UNSIGNED or attest key + serial (+ optional custom roots).  
 ☐ Reshape generation response to match assertion schema (`type`/`id`/`rawId` + nested `response` containing only `clientDataJSON` and `attestationObject`) and update OpenAPI snapshots/tests.  
 • Blocked by: Q1, Q2, Q5.

☐ **T2621 – UI auto-switch + Manual mode**  
 ☐ Detect overrides of challenge/RP ID/origin/credential+attestation keys/serial and flip to Manual; show a small “Manual” hint near Generate.  
 ☐ Send `inputSource=MANUAL` and omit `attestationId` when in Manual.  
 ☐ Render attestation results from the nested response object (clientDataJSON + attestationObject only), surfacing signature/certificate statistics via telemetry metadata.  
 ☐ Owner declined the “Copy preset ID” link; no additional action required.  
 • Blocked by: Q1.

☐ **T2622 – CLI parity for Manual (if approved)**  
 ☐ Support `--input-source=manual` and required inputs, mirroring REST.  
 ☐ Tests mirroring REST application cases.  
 • Blocked by: Q4, Q5.
