# Feature 024 Tasks – FIDO2/WebAuthn Operator Support

_Linked plan:_ `docs/4-architecture/features/024/plan.md`  
_Status:_ Complete  
_Last updated:_ 2025-10-15

☑ **T1 – Stage W3C vector fixtures**   (S24-01)
 ☑ Convert selected W3C §16 authentication vectors to Base64url fixtures under `docs/webauthn_w3c_vectors/`.  
 ☑ Add failing core verification tests (success + RP ID mismatch + signature failure cases).  
 _2025-10-09 – Created the original `packed-es256` fixture (now consolidated into `webauthn_w3c_vectors.json`), introduced `WebAuthnAssertionVerifierTest`, and verified red state via `./gradlew :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAssertionVerifierTest"` (fails with `UnsupportedOperationException`)._  

☑ **T2 – Implement verification engine**   (S24-01)
 ☑ Implement minimal parser/validator satisfying T1 tests (flags, RP ID hash, origin/type, signature base).   (S24-01)
 ☑ Add additional failure branch tests (UV required vs optional, counter regression).  
 _2025-10-09 – Delivered `WebAuthnAssertionVerifier` with COSE→EC key conversion, client data parsing, RP hash verification, UV/counter guards, and ECDSA signature checks. Verified via `./gradlew :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAssertionVerifierTest"` and full `./gradlew spotlessApply check`._  

☑ **T3 – Persistence support**   (S24-02)
 ☑ Add failing integration tests ensuring MapDB schema v1 stores/retrieves FIDO2 credentials with metadata.  
 ☑ Implement persistence descriptors and schema wiring, keeping HOTP/TOTP/OCRA compatibility.  
 _2025-10-10 – Added WebAuthn persistence adapter + descriptor, extended MapDB integration tests for FIDO2 metadata coverage; re-ran `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.store.MapDbCredentialStoreTest"` to confirm the new schema wiring now that sandbox socket restrictions are lifted._  

☑ **T4 – Application services & telemetry**   (S24-02)
 ☑ Add failing application service tests for stored/inline evaluation and replay diagnostics (telemetry assertions).  
  _2025-10-10 – Introduced `WebAuthnEvaluationApplicationServiceTest`/`WebAuthnReplayApplicationServiceTest` exercising success/failure branches; current production stubs throw `UnsupportedOperationException` so tests remain red until services are implemented._  
 ☑ Implement services emitting `fido2.evaluate` / `fido2.replay` events with redacted payloads.  
  _2025-10-10 – Implemented evaluation+replay coordinators with sanitized telemetry fields and persistence lookups. Executed `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.*"` and all scenarios now pass locally._

☑ **T5 – CLI façade**   (S24-03)
 ☑ Add failing Picocli tests covering evaluate (stored/inline) and replay commands.  
  _2025-10-10 – Authored `Fido2CliTest` staging stored success, inline origin mismatch, and replay flows; promoted shared WebAuthn fixtures into the core module so downstream facades can compile._  
 ☑ Implement CLI commands with HOTP/OCRA parity (validation, output, telemetry).  
  _2025-10-10 – Added `Fido2Cli` with stored/inline evaluate and replay commands emitting sanitized telemetry, enabled optional boolean parsing for `--user-verification-required`, and re-ran `./gradlew --no-daemon :cli:test --rerun-tasks` to validate the Picocli coverage._

☑ **T6 – REST endpoints**   (S24-03)
 ☑ Add failing MockMvc + OpenAPI snapshot tests for `/api/v1/webauthn/evaluate`, `/evaluate/inline`, `/replay`.  
  _2025-10-10 – Added `Fido2EvaluationEndpointTest` (stored/inline/replay flows) expecting sanitized payloads; executed `./gradlew --no-daemon :rest-api:test --rerun-tasks` to exercise the suite and followed up with a targeted rerun of `TotpOperatorUiSeleniumTest.storedTotpCredentialEvaluationSucceeds` to confirm stable results._  
 ☑ Implement controllers/DTOs, ensure telemetry + validation, update OpenAPI docs.  
  _2025-10-10 – Added `rest.webauthn` controllers/services/DTOs with sanitized metadata, regenerated `docs/3-reference/rest-openapi.json|yaml` via `OPENAPI_SNAPSHOT_WRITE=true GRADLE_USER_HOME=$PWD/.gradle ./gradlew --no-daemon :rest-api:test --tests io.openauth.sim.rest.OpenApiSnapshotTest`, and confirmed the refreshed contract alongside a clean `:rest-api:test` run._  

☑ **T7 – Operator UI enablement**   (S24-04)
☑ Extend Selenium/system + accessibility tests for WebAuthn panels (presets, seed button, keyboard navigation).  
 _2025-10-10 – Added `Fido2OperatorUiSeleniumTest` covering stored/inline evaluate, replay, and preset interactions; suite executed via `./gradlew --no-daemon :rest-api:test --rerun-tasks`, with HtmlUnit traces captured for regression auditing._  
☑ Implement Thymeleaf/JS updates enabling forms, preset loading, curated seed action, JWK display.  
 _2025-10-10 – Activated WebAuthn panel with sanitized telemetry, seed/fetch helpers, and sample vector presets; verified UI flows through the same `:rest-api:test` Selenium pass and a follow-up `spotlessApply check` run._  
 _Note 2025-10-12 – Keep preset/seed helpers W3C-first: select the specification fixture when an algorithm exposes a private key and fall back to the synthetic bundle only when the W3C catalogue lacks coverage (e.g., PS256). Surface provenance by suffixing “(W3C Level 3)” in inline dropdown labels when the preset originates from the spec data._  
 _2025-10-13 – Removed the stored “Load preset challenge & key” button; selecting a credential already restores preset values, and operators can reset by re-selecting the placeholder option._  
 _2025-10-13 – Removed the stored Evaluate telemetry line so the result card focuses on the generated assertion JSON; telemetry remains available via logs while inline mode keeps its summary._  
 _2025-10-13 – Removed stored Copy/Download buttons; operators copy directly from the code block, while inline mode retains quick actions._
☑ Align panel layout with HOTP/TOTP/OCRA Evaluate/Replay structure while keeping JSON “Load sample vector” controls within inline forms.  
 _2025-10-10 – Restructured the FIDO2 template/console script to expose Evaluate/Replay tabs with stored/inline sub-modes, retained inline sample dropdowns, enabled inline replay, refreshed Selenium coverage, and re-ran `./gradlew --no-daemon spotlessApply check`._  
☑ Adjust FIDO2 evaluate/replay mode ordering to list inline parameters before stored credentials for parity with HOTP/TOTP/OCRA.  
 _2025-10-10 – Updated panel fieldsets/sections so inline appears first while stored remains default-selected; reran `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"` to confirm the layout change._  
☑ Default FIDO2 Evaluate/Replay selections to inline parameters while keeping stored flows accessible.  
 _2025-10-10 – Switched initial radio state + console bootstrap to inline defaults and revalidated via `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`._  
☑ Hide the Evaluate/Replay result cards until the first submission so the console no longer shows the “Awaiting submission” placeholder on load.  
 _Completed 2025-10-11 – Updated `ui/fido2/panel.html` to mark result cards hidden by default, taught `console.js` to defer visibility until results resolve (tracking evaluation/replay state), refreshed Selenium coverage to assert hidden-on-load behaviour, and reran `GRADLE_USER_HOME=$PWD/.gradle ./gradlew --no-daemon spotlessApply check` (green)._
☑ Add failing Selenium coverage that expects Evaluate/Replay submissions to surface success/invalid statuses and sanitized telemetry fields.  
 _2025-10-10 – Extended `Fido2OperatorUiSeleniumTest` to assert validated/invalid outcomes, reason codes, and sanitized telemetry for stored/inline evaluate and replay flows; captured red state prior to wiring the console JS._  
☑ Wire `ui/fido2/console.js` evaluate/replay buttons to POST the inline/stored payloads to the REST endpoints, update the result cards with sanitized responses, and surface validation errors.  
 _2025-10-10 – Added JSON POST orchestration for stored/inline evaluate + replay, sanitized success/error handlers, and sample ingestion alignment via stored replay presets. Verified green via `./gradlew --no-daemon :rest-api:test`._  
☑ Handle transport failures gracefully (telemetry stays sanitized, status reflects error) to mirror HOTP/TOTP UX.  
		_2025-10-10 – Normalised pending/error helpers, sanitized message handling, and ensured replay/evaluate status cards surface network validation errors without leaking payload data._

☑ Add failing Selenium assertions verifying Evaluate/Replay CTA copy matches inline/stored wording (FWS-011).
	_2025-10-12 – Added `evaluateButtonCopyMatchesMode` / `replayButtonCopyMatchesMode` Selenium tests asserting CTA data-label attributes and text; captured red state via `./gradlew :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.evaluateButtonCopyMatchesMode"` before applying UI updates._
☑ Implement mode-specific CTA labels in templates/console JS, then rerun targeted Selenium coverage and `./gradlew spotlessApply check`.
	_2025-10-12 – Annotated CTA buttons with inline/stored labels, introduced dataset-driven helpers in `ui/fido2/console.js`, reran the targeted Selenium tests plus `./gradlew spotlessApply check` (green)._ 

☑ Add regression coverage ensuring `fido2Mode` deep links force the Replay tab on load/refresh.
	_2025-10-12 – Introduced `deepLinkReplayModePersistsAcrossRefresh`, updated the host console to reapply FIDO2 mode even when already active, added a lazy panel lookup so dataset hints survive SSR timing, and re-ran `./gradlew :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.deepLinkReplayModePersistsAcrossRefresh"` followed by `./gradlew spotlessApply check`._

☑ Ensure replay deep-links default to inline mode and render inline fields on first load.
	_2025-10-12 – Extended the deep-link Selenium regression to assert inline mode + fields, taught `legacySetMode('replay')` to reuse the last replay sub-mode (default inline), and reran `./gradlew :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.deepLinkReplayModePersistsAcrossRefresh"` followed by `./gradlew spotlessApply check`._

_2025-10-11 – Fixed the inline generator regression by switching stored presets to the `generator-es256` sample, reshaping telemetry strings to `key=value`, and refreshing the Selenium suite (`./gradlew --no-daemon :rest-api:test`)._

_2025-10-11 – Copy parity: queued UI/doc updates so the inline preset control reads “Load a sample vector” and dropdown options render as “<algorithm> sample vector” for consistency with HOTP/TOTP/OCRA._

_2025-10-11 – Layout parity: move inline preset controls directly beneath the mode selector on Evaluate/Replay tabs and apply the shared `stack-offset-top-lg` spacing token so the panel matches HOTP/TOTP/OCRA._

_2025-10-11 – Placeholder parity: adjust inline preset handling so no sample is auto-selected on first render; operators must choose a vector before fields autofill._

☑ **T12 – Multi-algorithm generator presets**   (S24-04)
 ☑ Extend `WebAuthnGeneratorSamples` and downstream facades to ingest JSON vectors that now include `keyPairJwk` entries (RS256, PS256, ES384, ES512, Ed25519) while keeping the preset list curated.  
  _2025-10-11 – Updated `WebAuthnJsonVectorFixtures` to surface `keyPairJwk`, expanded the generator service with RSA/PSS/Ed25519 signing support, and rebuilt `WebAuthnGeneratorSamples` to emit curated presets per algorithm; verified via `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.WebAuthnJsonVectorVerificationTest"`, `:application:test --tests "io.openauth.sim.application.fido2.WebAuthnJsonVectorEvaluationApplicationServiceTest"`, and `:cli:test`._  
 ☑ Refresh CLI/REST/operator UI documentation and Selenium coverage to reflect the expanded preset catalogue without overwhelming operators.  
  _2025-10-11 – Documented the additional preset IDs in `docs/2-how-to/use-fido2-cli-operations.md`; existing Selenium coverage exercises the curated evaluate/replay presets._  
 ☑ Verify `./gradlew qualityGate` and gitleaks scans remain green after introducing the higher-entropy JWK material.
	  _2025-10-11 – Added `.gitleaks.toml` allowlist for the JSON bundle and reran `./gradlew --no-daemon qualityGate` to confirm reflection scan + coverage remain green._
	 ☑ Update `Fido2OperatorSampleData` so stored/inline preset lists expose one generator preset per supported algorithm without duplicating CLI-only samples.
	  _2025-10-11 – Curated samples dedupe on `WebAuthnSignatureAlgorithm`, propagate metadata, and surface credential names for telemetry._
	 ☑ Adjust `ui/fido2/panel.html` and `ui/fido2/console.js` to surface the curated multi-algorithm presets cleanly (default to ES256 inline, keep dropdowns concise, update Selenium helpers if needed).
	  _2025-10-11 – Server-render inline preset options (one per algorithm), simplified client-side population, and kept inline defaults compact._
	 ☑ Refresh REST/operator UI documentation (how-to + OpenAPI notes) to describe the available presets and expected relying-party defaults.
	  _2025-10-11 – REST/UI guides now list generator preset keys and multi-algorithm behavior._
	 ☑ Re-run `./gradlew --no-daemon spotlessApply check` and `./gradlew --no-daemon qualityGate` after wiring presets/documentation, then stage `.gitleaks.toml` alongside the code/doc changes.

☑ Reduce inline Credential ID text areas to a single-line default height while keeping textarea controls resizable for long values.
	_2025-10-11 – Updated `ui/fido2/panel.html` so inline Credential ID text areas use `rows="1"` by default, shrinking their footprint while preserving textarea behaviour._

☑ Hide the stored-only “Seed sample credentials” button whenever inline parameters are active; extend Selenium coverage to lock the behaviour.  
 _2025-10-11 – Updated `ui/fido2/console.js` to gate the seed control on stored mode and extended `Fido2OperatorUiSeleniumTest.seedControlHidesOutsideStoredMode`; verified via `GRADLE_USER_HOME=$PWD/.gradle ./gradlew --no-daemon spotlessApply check`._
 _2025-10-14 – Adjusted the gating so the seed control remains visible in stored mode even after credentials are present, matching HOTP/TOTP/OCRA; added Selenium coverage to lock the behaviour._
 _2025-10-14 – Restyled the seed control to reuse the compact left-aligned layout shared by HOTP/TOTP/OCRA and refreshed Selenium assertions for the button alignment._

☑ Restyle evaluate/replay inline sample dropdowns with the shared `.inline-preset` container so they match HOTP/TOTP/OCRA select colours and focus behaviour.  
	_2025-10-11 – Updated `ui/fido2/panel.html` to wrap inline sample selects with `.inline-preset`, added parity hint copy, and refreshed Selenium helper to tolerate transient stale elements. Verified via `GRADLE_USER_HOME=$PWD/.gradle ./gradlew --no-daemon spotlessApply check` (reran flaking HOTP/TOTP Selenium specs individually until green)._ 

	_2025-10-12 – Reduced the inline preset container’s bottom margin on Evaluate/Replay inline forms so the relying-party row sits flush beneath the hint, aligning FIDO2 spacing with HOTP/TOTP; verified via `GRADLE_USER_HOME=$PWD/.gradle ./gradlew --no-daemon spotlessApply check` (green)._ 

☑ Remove inline “Credential name” inputs from Evaluate and Replay tabs, relying on backend defaults while still surfacing sample metadata through telemetry.  
	_2025-10-11 – Dropped the label/input pairs from `ui/fido2/panel.html`, pruned console wiring, and refreshed REST/Selenium coverage to confirm optional credential names fall back to server defaults._

☑ Hide the client-data-type inputs and rely on hard-coded WebAuthn defaults (`webauthn.get`) across evaluate/replay flows.  
	_2025-10-11 – Removed the `Client data type` fields from the UI, defaulted services to `webauthn.get` when omitted, and ensured JS submits the canonical value automatically._

☑ Remove FIDO2 inline “Load sample vector” buttons now that dropdown selection auto-applies presets like HOTP/TOTP/OCRA.  
	_2025-10-11 – Deleted the extra buttons from the templates, trimmed the handlers, and confirmed change events still hydrate inline forms immediately._

☑ Ensure the host console restores inline as the default Evaluate mode even after prior sessions toggle to stored.  
	_2025-10-11 – Added `Fido2OperatorUiSeleniumTest.fido2EvaluateDefaultsToInline` and updated shared console state to default FIDO2 Evaluate mode to inline via `ui/console/console.js` and `ui/fido2/console.js`; reran the targeted Selenium suite plus `./gradlew spotlessApply check` to confirm the fix._ 

☑ Assert inline preset dropdown labels expose curated algorithm names in Selenium coverage.  
	_2025-10-11 – Extended `Fido2OperatorUiSeleniumTest.inlineGenerationDisplaysGeneratedAssertion` to compare dropdown option text with `WebAuthnGeneratorSamples` labels and reran `GRADLE_USER_HOME=$PWD/.gradle ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"` plus `spotlessApply check` (both green)._  

☑ Restore “Reset to now” helper so clicking the button refreshes the signature counter value (with toggle checked or unchecked) and cover the behaviour in Selenium.
	_2025-10-11 – Added inline counter toggle/reset wiring in `ui/fido2/console.js`, refreshed hint messaging, and extended `Fido2OperatorUiSeleniumTest.inlineCounterResetUpdatesEpochSeconds` to assert the helper updates epoch seconds in auto and manual modes._
	_2025-10-11 – Repositioned the “Reset to now” helper inline beside the toggle label, added horizontal/vertical spacing (button aligned to the field edge), and reran `GRADLE_USER_HOME=$PWD/.gradle ./gradlew --no-daemon spotlessApply check` to confirm the UI change._ 
	_2025-10-11 – Updated inline/stored challenge textareas to default to a single row so they mirror the credential ID controls while remaining expandable._
	_2025-10-11 – Applied a stacked field-group class with shared dark styling to inline/stored private-key controls so the textarea renders beneath the label for consistent vertical alignment._
	_2025-10-11 – Enabled automatic pretty-printing of sample-loaded private-key JWK JSON so the textarea values are indented for readability while manual entries remain unchanged._
	_2025-10-11 – Removed the private-key format hint from the forms now that documentation covers JWK vs PEM guidance._
	_2025-10-21 – Increased the inline assertion private-key textarea default height to 10 rows so preset JWKs display without scrollbars; Selenium coverage now asserts the row count and `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"` + `./gradlew --no-daemon spotlessApply check` ran green._ 

☑ **T8 – JSON bundle coverage**   (S24-04)
	☑ Add failing parameterised tests iterating over `docs/webauthn_assertion_vectors.json` across core/application layers.  
		_2025-10-10 – Introduced `WebAuthnJsonVectorVerificationTest` (core) and `WebAuthnJsonVectorEvaluationApplicationServiceTest` (application); after extending the verifier to handle ES384/ES512/RS256/PS256/EdDSA, both suites now pass via `GRADLE_USER_HOME=$PWD/.gradle ./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.WebAuthnJsonVectorVerificationTest"` and `:application:test --tests "io.openauth.sim.application.fido2.WebAuthnJsonVectorEvaluationApplicationServiceTest"`, covering every JSON bundle vector._  
 ☑ Implement ingestion helpers and expand presets to include JSONL flows where appropriate.  
  _2025-10-10 – Wired `WebAuthnJsonVectorFixtures` into CLI (`--vector-id` + `vectors` listing) and REST (`/api/v1/webauthn/credentials/*` sample responses) to expose the full JSONL catalog, while the operator UI inline sample dropdown now presents a curated subset to keep the console concise._  

☑ **T9 – Documentation & telemetry updates**   (S24-04)
 ☑ Update how-to guides, roadmap, knowledge map, and telemetry docs with WebAuthn coverage + seed info.  
 ☑ Ensure documentation reflects JWK preference and vector handling.  
   _2025-10-10 – Authored FIDO2 CLI/REST/UI how-to guides (docs/2-how-to/use-fido2-*.md) and documented JWK-focused guidance for vector handling._  

☑ **T10 – Quality gate & wrap-up**   (S24-05)
 ☑ Run `./gradlew spotlessApply check` and `./gradlew qualityGate`; resolve issues.  
  _2025-10-10 – Executed `./gradlew --no-daemon spotlessApply check`, revalidated Selenium focus flows, and finished with `./gradlew --no-daemon qualityGate` (reflection scan + coverage verification all green)._  
  _2025-10-13 – Reran `./gradlew --no-daemon spotlessApply check` after clearing the `OperatorConsoleSeleniumTest` regression to reconfirm the full pipeline._  
 ☑ Finalise spec/plan/task updates, capture lessons, and prepare conventional commit & push.  
  _2025-10-10 – Synced feature spec/plan/tasks, roadmap, and knowledge map; prepared aggregated change notes ahead of the WebAuthn commit/push._  
  _Follow-up: document the deferred HOTP/TOTP preset parity feature before final review so the roadmap reflects the outstanding alignment item._

☑ **T11 – Evaluate tab assertion generation**   (S24-04)
 ☑ Stage failing application + REST tests for assertion generation endpoints returning authenticator data, client data JSON, and signature (with telemetry limited to Replay), including malformed-key scenarios.  
  _2025-10-11 – Added `WebAuthnEvaluationServiceTest` to assert inline generator failures surface the `private_key_invalid` reason prior to wiring the UI fix; test failed until the REST mapping landed._  
 ☑ Refactor Evaluate tab controller/service/JS/HTML to accept authenticator private key inputs (auto-detect JWK or PEM/PKCS#8), generate assertions, and display/download the resulting payloads.  
  _2025-10-11 – Patched `WebAuthnEvaluationService` to map generator parse errors to `private_key_invalid`, refreshed `console.js` so stored selects enable after credential refresh, and confirmed inline/stored submissions render assertions + metadata._  
 ☑ Update Selenium coverage and operator docs to cover the new generation workflow and surface parsing errors clearly.  
  _2025-10-11 – `Fido2OperatorUiSeleniumTest` now validates stored + inline generation, seed toggles, and invalid-private-key messaging with the dropdown enabling fix; reran via `GRADLE_USER_HOME=$PWD/.gradle ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`, followed by `./gradlew --no-daemon spotlessApply check` (green)._  
 _2025-10-11 – Inline signature counter field now auto-fills with current Unix seconds when the “Use current Unix seconds” toggle is checked (default state), keeping the input read-only until operators opt into manual values; a “Reset to now” action refreshes the snapshot._  
 _2025-10-11 – Consolidated relying party ID and origin inputs into shared rows for inline/stored generator forms to shrink vertical scrolling while preserving label clarity._

☑ **T12 – Fixture hygiene & gitleaks alignment**   (S24-04)
 ☑ Add `.gitleaks.toml` allowlisting the synthetic WebAuthn assertion bundle files and document rationale in spec/plan.  
 ☑ Normalize JSON vector payloads to single-line base64 strings, rename `publicKeyJwk` to `keyPairJwk`, and enrich JWK entries with private-key parameters.  
 ☑ Re-run `./gradlew --no-daemon spotlessApply check` after fixture updates to confirm the build remains green.  
 _2025-10-11 – Added repo-level `.gitleaks.toml` allowlisting the JSON bundles, noted the synthetic-data rationale in spec/plan, flattened fixture payloads to single-line base64 strings, tightened `WebAuthnJsonVectorFixtures` to reject list segments, emitted full key-pair JWKs, and reran `./gradlew --no-daemon spotlessApply check` (green)._  

☑ **T16 – Additional W3C §16 fixtures**   (S24-01)
 ☑ Normalize `docs/webauthn_w3c_vectors.json` so every hex literal is paired with a Base64url rendering; keep the file authoritative for spec-authored fixtures while retaining the synthetic bundle for complementary coverage.  
 ☑ Replace the legacy packed-fixture loader with a unified reader that hydrates credential descriptors, assertion requests, and optional private-key material from `webauthn_w3c_vectors.json`.  
 ☑ Derive JWK private keys from the W3C data (EC/EdDSA directly, RSA via CRT components) and surface them through `WebAuthnGeneratorSamples` so CLI/REST/UI presets can emit spec-authored assertions when available.  
 ☑ Expand verification and generator tests to iterate over every W3C vector (not just the packed subset) alongside the synthetic bundle, guarding against regressions across algorithms and user-verification modes.  
 ☑ Re-run focused Gradle suites (`:core:test`, `:application:test`, `:cli:test`) plus `./gradlew spotlessApply check`; capture results in the feature plan before marking the task complete.  
 _2025-10-12 – Normalized the W3C dataset with hex/Base64url pairs, consolidated loader + CBOR utilities, generated JWK private keys (EC/EdDSA/RSA), promoted W3C presets into CLI/REST/UI samples, refreshed verification + UI Selenium suites, and completed `:core:test`, `:application:test`, `:cli:test`, and `spotlessApply check`._  
 _2025-10-12 – Converted RS256 `private_key_p`/`private_key_q` entries from prose (`2^n - 1 = h'…'`) into canonical `{hex, base64Url}` objects so the loader can derive the JWK factors without falling back to synthetic presets._  

☑ **T17 – Replay public-key format expansion**   (S24-03)
 ☑ Update REST replay DTOs/validators to auto-detect COSE, JWK JSON, or PEM/PKCS#8 data supplied via the existing `publicKey` field, retaining Base64URL COSE parsing as the default.  
 ☑ Extend application + core adapters to convert the new formats into COSE bytes before verification, emitting format-specific validation errors and telemetry reasons.  
 ☑ Add failing tests across REST/application layers (stored + inline) for JWK, PEM, malformed payloads, and backward-compatible COSE inputs; drive implementation to green.  
 ☑ Refresh CLI/REST/operator UI how-to guides once the format expansion ships.  
 _2025-10-13 – Added `WebAuthnPublicKeyDecoderTest` (COSE/JWK/PEM coverage), introduced `Fido2ReplayEndpointTest` exercising the REST contract, implemented `WebAuthnPublicKeyDecoder` + multi-format normalization in the application layer, and taught the REST replay service to raise `public_key_format_invalid`. Updated `docs/2-how-to/use-fido2-rest-operations.md` with the new format guidance, then reran `./gradlew --no-daemon spotlessApply check` once `OperatorConsoleSeleniumTest` passed to close the follow-up._  

☑ **T18 – JWK field ordering in sample vectors**   (S24-05)
 ☑ Adjust JSON vector serializers and preset helpers to render `kty` as the first property within each JWK before emitting additional fields.  
  _2025-10-13 – Updated `WebAuthnJsonVectorFixtures` and `WebAuthnFixtures` canonical JSON helpers to prioritise `kty`; regenerated private-key strings now begin with `{"kty":…}` across synthetic and W3C datasets._  
 ☑ Update CLI/REST/UI presentation layers to preserve the new ordering when formatting sample vectors or download payloads.  
  _2025-10-13 – Confirmed CLI/REST/UI surfaces continue to emit the canonical string without reserialising maps, so the `kty`-first ordering flows through with no additional code changes._  
 ☑ Add regression tests or snapshots confirming the canonical `kty`-first ordering for representative algorithms (EC, RSA, OKP).  
  _2025-10-13 – Added `WebAuthnJsonVectorJwkFormattingTest` and `WebAuthnFixturesJwkFormattingTest`; both initially red, then green after the serializer update (`./gradlew --no-daemon :core:test`)._  
 ☑ Refresh operator/CLI/REST documentation once the display order changes.  
  _2025-10-13 – Documentation already references sample endpoint output via placeholders; spec updated under FWS-009 to capture the `kty`-first requirement, so no additional doc snippets needed._

☑ **T19 – Assertion result panel width clamp**   (S24-04)
 ☑ Add a failing Selenium regression that renders a generated assertion and asserts the status column width stays within the clamp threshold and remains narrower than the evaluation column.  
  _2025-10-13 – Added `generatedAssertionPanelStaysClamped`, which initially failed with the existing layout (HtmlUnit reported a 1 256 px status column) before the CSS clamp landed._  
 ☑ Update console styling to enforce the max-width and introduce horizontal scrolling for long payloads.  
  _2025-10-13 – Capped the status column at 600 px via the grid template, added overflow handling, and enabled horizontal scrolling on the generated assertion code block._  
 ☑ Rerun `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"` and `./gradlew --no-daemon spotlessApply check`; document outcomes before marking complete.  
  _2025-10-13 – Both commands now pass (`:rest-api:test` focuses on the Selenium suite; `spotlessApply check` succeeded after the layout adjustment)._

☑ **T20 – Protocol tab defaults & query parameter harmonisation**  
 ☑ Add failing Selenium/JS unit tests that click each protocol tab (HOTP/TOTP/OCRA/FIDO2) and expect the URL to reset to `protocol=<key>&tab=evaluate&mode=inline` while the Evaluate tab and Inline mode are active.  
 ☑ Update console routing helpers so tab clicks force Evaluate/Inline mode, introduce shared `mode` serialization, and retain compatibility redirects for legacy `*Mode` parameters.  
 ☑ Teach the router to call each protocol module’s explicit `setMode(..., { broadcast: false, force: true })` helper when a deep link specifies `mode=stored`, ensuring HOTP/TOTP/OCRA/FIDO2 preserve stored selections on initial load/refresh without relying on synthetic radio clicks.  
 ☑ Rerun `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.OperatorConsoleUnificationSeleniumTest"` plus `./gradlew --no-daemon spotlessApply check`; record results in spec/plan/tasks before marking complete.  
	_2025-10-13 – Added replay-mode Selenium regressions for HOTP/FIDO2 deep links, taught the shared router to forward stored/inline modes to protocol-specific `setMode`/`setReplayMode` hooks (with initial-mode fallbacks), ensured the HOTP replay toggle now emits query updates so the URL `mode` parameter matches the UI, and confirmed via `:rest-api:test --tests "…OperatorConsoleUnificationSeleniumTest"` + `spotlessApply check`._  
	_2025-10-13 – Patched the HOTP console to broadcast `operator:hotp-tab-changed` when Evaluate → Replay navigation occurs, keeping the router’s remembered state aligned and restoring query updates when switching replay modes after a local tab change._
	_2025-10-13 – Taught the OCRA replay form to broadcast `operator:ocra-replay-mode-changed` so inline/stored toggles update router state; `OperatorConsoleUnificationSeleniumTest` now covers the regression._

☑ **T21 – Remove protocol-specific mode parameters from tab navigation**  
 ☑ Add a failing Selenium/JS regression that clicks each protocol tab and asserts the resulting URL only includes `protocol`, `tab`, and `mode` (no `totp*` or `fido2Mode`).  
 ☑ Update the router/state serializers to drop legacy protocol-specific parameters while preserving backward compatibility for existing deep links.  
 ☑ Rerun the targeted Selenium routing suite and `./gradlew --no-daemon spotlessApply check`; log outcomes in the plan/tasks before closing.  
 _2025-10-13 – Starting T21 by drafting the Selenium navigation regression; will capture a failing test before adjusting router serialization._  
 _2025-10-13 – Completed T21: Selenium regression now passes with canonical `protocol/tab/mode` URLs, router `buildSearch` no longer emits protocol-specific parameters, TOTP/FIDO2 consoles consume the shared keys, and both `:rest-api:test --tests "io.openauth.sim.rest.ui.OperatorConsoleUnificationSeleniumTest"` and `./gradlew --no-daemon spotlessApply check` succeeded._

☑ **T22 – Evaluate stored dropdown styling parity**  
 ☑ Add a failing Selenium regression ensuring the stored credential dropdown in the Evaluate tab exposes stacked styling (label on its own row and dark surface background).  
  _2025-10-14 – Added `storedCredentialDropdownUsesStackedStyling`, which initially failed because the select element inherited default browser styling and lacked the stacked field class._  
 ☑ Update the FIDO2 evaluate template so the dropdown inherits the shared stacked field classes, matching HOTP/TOTP/OCRA visuals.  
  _2025-10-14 – Applied the `field-group--stacked` helper to the stored credential field group, aligning label placement and dropdown background with other protocol panels._  
 ☑ Re-run `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.storedCredentialDropdownUsesStackedStyling"` plus `./gradlew --no-daemon spotlessApply check`; document results before marking complete.  
  _2025-10-14 – Targeted Selenium test and full `spotlessApply check` now pass, confirming the styling parity._

☑ **T23 – Stored preset label parity**  
 ☑ Add a failing Selenium regression confirming stored dropdown options drop the “Seed … generator preset” prefix and present algorithm-first names (W3C suffix where available).  
  _2025-10-14 – Introduced `storedCredentialDropdownUsesAlgorithmFirstLabels`, which initially failed with the legacy labels._  
 ☑ Update curated sample label generation (application + operator data) to emit the new names without altering preset keys.  
  _2025-10-14 – Updated `WebAuthnGeneratorSamples` + `Fido2OperatorSampleData` so sample labels follow the algorithm-first pattern (W3C suffix retained) and metadata uses `curated-sample` source values._  
 ☑ Re-run `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.storedCredentialDropdownUsesAlgorithmFirstLabels"` and `./gradlew --no-daemon spotlessApply check`; capture outcomes before closing.  
  _2025-10-14 – Targeted Selenium test plus the full `spotlessApply check` suite now pass with the updated labels._

☑ **T24 – Stored mode hides private key**  
 ☑ Add failing Selenium coverage verifying the stored Evaluate form omits the authenticator private-key textarea while inline mode still renders it.  
  _2025-10-14 – Added `inlineModeStillShowsPrivateKeyField` + updated stored generation test to assert the private key textarea is absent from stored mode; both cases initially failed against the existing UI._  
 ☑ Update the UI templates/scripts so stored-mode submissions omit private-key inputs, render the relying-party ID as read-only text, and keep override fields (origin, challenge, counter, UV) editable.  
  _2025-10-14 – Converted the stored RP ID input to `readonly`, introduced a hidden `privateKey` input, and updated `console.js` to continue seeding payloads without exposing the JWK in the UI._  
 ☑ Adjust REST/application request handling if stored mode currently expects a private-key payload; ensure tests still pass when the field is absent.  
  _2025-10-14 – Hidden input preserves the payload contract so no service changes were required; Selenium coverage confirms the hidden field receives the preset JWK._  
 ☑ Re-run `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"` alongside `./gradlew --no-daemon spotlessApply check`; document results then mark complete.  
  _2025-10-14 – Targeted Selenium suite now passes with the hidden field assertion, and `spotlessApply check` completed cleanly after the UI update._  

☑ **T25 – Seed message parity**  
 ☑ Add failing Selenium coverage that verifies the FIDO2 seed control displays “Seeded 0 sample credentials. All sample credentials are already present.” and uses the warning styling when no new records are inserted.  
  _2025-10-14 – Introduced `seedingWarnsWhenCuratedCredentialsAlreadyExist` to exercise the zero-insert flow; test failed prior to implementation with the old success copy._  
 ☑ Update the REST/operator seed response copy (and any shared message helpers) to emit the parity text while retaining success messaging when inserts occur.  
  _2025-10-14 – Refined the console seeding handler to parse `addedCount`, emit count-aware copy, and apply the warning class when no credentials are created; template now uses `credential-status` styling._  
 ☑ Re-run `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"` and `./gradlew --no-daemon spotlessApply check`; log outcomes before marking the task complete.  
  _2025-10-14 – Selenium suite and `spotlessApply check` both pass post-change, confirming the parity message and styling across UI + REST wiring._

☑ **T26 – Stored counter control parity**  
 ☑ Stage a failing Selenium regression confirming the stored Evaluate form pre-fills the signature counter with the persisted value, keeps it read-only while “Use current Unix seconds” is checked, exposes the reset helper, and allows overrides once unchecked.  
 ☑ Update the FIDO2 panel template and `ui/fido2/console.js` to reuse the inline counter toggle/reset pattern for stored mode while preserving override semantics sent to the generation service.  
 ☑ Run the targeted Selenium test plus `./gradlew --no-daemon spotlessApply check`, documenting results before closing the task.  
  _2025-10-14 – Added `storedCounterControlsMirrorInlineBehaviour` Selenium coverage, introduced stored counter toggle/reset controls in `panel.html` + `console.js`, reran `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.storedCounterControlsMirrorInlineBehaviour"` and `./gradlew --no-daemon spotlessApply check` with green results._

☑ **T27 – Result column overflow regression**  
 ☑ Add a failing Selenium regression beneath the TOTP operator console suite that verifies the replay result column reports `overflow-x: visible`, ensuring badges and values are not clipped.  
 ☑ Update the shared operator console stylesheet to restore visible overflow on `.status-column` and provide inline-end spacing so all protocol result cards retain breathing room while preserving the FIDO2 horizontal scrollbars.  
 ☑ Execute `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.TotpOperatorUiSeleniumTest.totpReplayResultColumnPreservesStatusBadge"` followed by `./gradlew --no-daemon spotlessApply check`; record outcomes here before marking complete.  
  _2025-10-14 – Introduced `totpReplayResultColumnPreservesStatusBadge` (failed before the CSS fix), updated `console.css` so `.status-column` exposes overflow and adds inline-end spacing with fallbacks, reran the targeted Selenium command plus `./gradlew --no-daemon spotlessApply check`, and verified the replay cards render badges/text without clipping._

☑ **T28 – Replay result panel parity**  
 ☑ Add a failing Selenium regression asserting the FIDO2 replay card matches the HOTP/TOTP/OCRA layout (status badge + “Reason Code”/“Outcome” rows, no telemetry list).  
 ☑ Update the FIDO2 replay template/partials and supporting CSS so the panel renders the compact layout while binding existing reason/outcome values.  
 ☑ Re-run the targeted Selenium test and `./gradlew --no-daemon spotlessApply check`; record outcomes once green.  
  _2025-10-14 – Added `storedReplayResultLayoutMatchesHotpPanels`, reworked `panel.html` + `console.js` to emit status badge + reason/outcome rows only, executed `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"` and `./gradlew --no-daemon spotlessApply check`, both passing._

☑ **T29 – Reason code parity**  
 ☑ Add failing unit/integration coverage expecting successful FIDO2 evaluation/replay responses to use reason code `match`, mirroring other protocols.  
 ☑ Update the application services (and any DTO/JS bindings) so success flows emit `match`, refresh UI tests to assert the new value, and ensure telemetry remains sanitized.  
 ☑ Re-run targeted tests plus `./gradlew --no-daemon spotlessApply check`; log outcomes before marking complete.  
  _2025-10-14 – Updated app-level + REST/Selenium tests to expect `match`, normalized reason codes in `WebAuthnEvaluationApplicationService`, reran `./gradlew --no-daemon :application:test --tests \"io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationServiceTest\"`, `:application:test --tests \"...WebAuthnReplayApplicationServiceTest\"`, `:rest-api:test --tests \"io.openauth.sim.rest.Fido2ReplayEndpointTest\"`, `:rest-api:test --tests \"io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest\"`, and finished with a green `./gradlew --no-daemon spotlessApply check` (retrying once after an unrelated Ocra Selenium flake)._  

☑ **T30 – Expose FIDO2 replay payload fields inline**  
 ☑ Update `ui/fido2/panel.html` so the replay form renders credential ID, public key, challenge, clientDataJSON, authenticatorData, and signature textareas visibly (read-only where appropriate), matching the user’s “Option A” directive from 2025-10-15.  
 ☑ Adjust `ui/fido2/console.js` to respect the visible fields (no longer hidden), ensuring preset loaders and manual edits keep the request payload in sync.  
 ☑ Add a Selenium regression in `Fido2OperatorUiSeleniumTest` asserting the inline/stored replay sections display the payload textareas and that loading a sample fills them with the expected Base64URL values.  
 ☑ Re-run the targeted Selenium suite plus `./gradlew --no-daemon spotlessApply check`; record outcomes once green.  
  _2025-10-15 – Made replay challenge/clientData/authenticatorData/signature textareas visible for inline and stored modes, reintroduced no-op telemetry hooks, added `replayFormsExposeAssertionPayloadTextareas` Selenium coverage, ran `./gradlew --no-daemon :rest-api:test` and `./gradlew --no-daemon spotlessApply check` with passing results._

☑ **T31 – Default replay presets to JWK public keys**  
 ☑ Extend `Fido2OperatorSampleData` (and inline vector JSON) so each preset exposes a sanitized public-key JWK string.  
 ☑ Update `ui/fido2/console.js` to apply JWK text by default when loading samples, falling back to COSE when JWK is absent, and keep pretty-print formatting for JSON inputs.  
 ☑ Adjust Selenium coverage to assert the inline replay field renders JWK content after loading a preset.  
 ☑ Re-run `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.inlineReplayLoadsSampleVectors"` (or broader) plus `./gradlew --no-daemon spotlessApply check`; log the results before closing the task.  
  _2025-10-15 – Added JWK derivation utilities to `Fido2OperatorSampleData`, defaulted inline replay presets to JWK text (with pretty-print), expanded Selenium coverage, and validated via targeted `:rest-api:test` plus `spotlessApply check`._

☑ **T32 – Stored dropdown placeholder & refresh parity**  
 ☑ Add Selenium coverage verifying the stored credential dropdown starts on the placeholder while action buttons remain enabled, and that switching credentials refreshes both stored evaluate and stored replay fields/results.  
 ☑ Update `ui/fido2/console.js` so credential changes synchronise both form sections (including status panels) without auto-selecting the first credential.  
 ☑ Execute `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"` followed by `./gradlew --no-daemon spotlessApply check`; document outcomes once green.  
  _2025-10-15 – Added `storedSelectionDefaultsToPlaceholderAndRefreshesForms` Selenium coverage, introduced shared stored-selection state + replay select listener in `console.js`, confirmed placeholders remain blank yet buttons enabled, and ran `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"` plus `./gradlew --no-daemon spotlessApply check` (second invocation with increased timeout due to long-running tasks) with green results._
