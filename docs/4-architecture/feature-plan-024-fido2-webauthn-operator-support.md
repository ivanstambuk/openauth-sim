# Feature Plan 024 – FIDO2/WebAuthn Operator Support

_Linked specification:_ `docs/4-architecture/specs/feature-024-fido2-webauthn-operator-support.md`  
_Status:_ In Progress  
_Last updated:_ 2025-10-15

## Vision & Success Criteria
- Ship a parity WebAuthn assertion generation + verification experience across core, persistence, CLI, REST, and operator UI facades, mirroring HOTP/OCRA ergonomics (authenticator-style generation on Evaluate, verification on Replay).
- Validate canonical W3C WebAuthn Level 3 §16 authentication vectors end-to-end, then extend coverage to the synthetic JSONL bundle to safeguard algorithm breadth (ES256/384/512, RS256, PS256, Ed25519).
- Enable operators to preload curated stored credentials and inject inline assertion vectors via UI presets, with key material surfaced as JWKs.
- Maintain a W3C-first preset policy: UI seed/preset flows use spec-authored fixtures when available, falling back to synthetic vectors only when the W3C catalogue lacks private keys.
- Surface provenance to operators by appending “(W3C Level 3)” to preset labels sourced from the specification, keeping synthetic-only entries unlabelled.
- Normalize RSA fixtures (RS256) to expose `{hex, base64Url}` fields so the loader can compute JWK factors directly from the specification data.
- Maintain green coverage/quality gates (`spotlessApply`, SpotBugs, ArchUnit, reflectionScan, Jacoco ≥0.90, PIT ≥ baseline).

## Scope Alignment
- In scope: verification engine, persistence schema entries, telemetry integration, CLI/REST/UI wiring, W3C + JSONL fixtures, seed/preset utilities, documentation, and a WebAuthn authenticator simulator that generates assertions from private-key inputs.
- Out of scope: registration/attestation ceremonies, authenticator emulation, dependency upgrades, UI issuance flows.

## Dependencies & Interfaces
- Reuse existing MapDB schema v1 (Feature 002) via `CredentialStoreFactory`.
- Telemetry events must flow through `TelemetryContracts` (`application` module).
- Operator console tab already present from Feature 020; this plan activates the FIDO2/WebAuthn content and REST wiring.
- JSON bundle located at `docs/webauthn_assertion_vectors.json`; W3C vector conversions to be stored under `docs/webauthn_w3c_vectors/` (new).
- Core verification suites must continue to execute against both datasets (`webauthn_w3c_vectors.json` + `webauthn_assertion_vectors.json`) so fixture parity is enforced independently of UI preferences.
- JSON bundle stores high-entropy payloads as 16-character base64url segments to satisfy gitleaks; ingest utilities must join segments before verification.

## Increment Breakdown (≤10 min each)
Each increment stages failing tests first, drives implementation to green, and runs `./gradlew spotlessApply check`.

1. **I1 – Fixture bootstrap (tests only)**  
   - Convert targeted W3C §16 authentication vectors to Base64url fixtures under `docs/webauthn_w3c_vectors/`.  
   - Add failing core tests asserting successful verification for ES256 baseline and expected failures (RP ID mismatch, bad signature).  
   - Analysis gate: update spec clarifications with vector references.  
   _2025-10-09 – Authored the initial `packed-es256` fixture (since consolidated into `webauthn_w3c_vectors.json`), added `WebAuthnAssertionVerifierTest` plus fixture loader, and confirmed red state via `./gradlew :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAssertionVerifierTest"` (throws `UnsupportedOperationException` as verifier not yet implemented)._ 

2. **I2 – Verification engine skeleton**  
   - Implement minimal parser/validator in `core/fido2` package satisfying I1 tests.  
   - Cover flag parsing, RP ID hash, `clientDataJSON` validation, signature base assembly.  
   - Add failure branch tests (UV flag requirements, type/origin mismatch).  
   _2025-10-09 – Implemented `WebAuthnAssertionVerifier` with CBOR COSE parsing, client data validation, RP hash checks, and ECDSA signature verification. `./gradlew :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAssertionVerifierTest"` now passes alongside `./gradlew spotlessApply check`._

3. **I3 – Persistence descriptors**  
   - Extend MapDB schema v1 with FIDO2 credential metadata (algorithm, credential ID, public key, flags).  
   - Add failing integration tests mixing HOTP/TOTP/OCRA/FIDO2 records; implement to green.  
   - Ensure seeds use curated subset.

4. **I4 – Application services & telemetry**  
   - Stage failing application-level tests for stored/inline evaluation + replay diagnostics.  
   - Implement services returning rich diagnostics, emitting `fido2.evaluate`/`fido2.replay` telemetry with redacted payloads.  
   - **Next action (2025-10-10):** hydrate the evaluation service to resolve stored lookups through `WebAuthnCredentialPersistenceAdapter`, reuse the verifier for inline requests, and build sanitized telemetry maps that omit challenges/signatures before wiring replay delegation.

5. **I5 – CLI façade**  
   - Add failing Picocli integration tests for evaluate/replay commands (stored + inline).  
   - Implement commands, ensure help/validation parity with HOTP/OCRA.  
   - _2025-10-10 – Promoted shared WebAuthn fixtures into the core module, implemented `Fido2Cli` (stored/inline evaluate + replay), enabled optional `--user-verification-required[=<bool>]` parsing, and verified via `GRADLE_USER_HOME=$PWD/.gradle ./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliTest"`; follow-up `./gradlew --no-daemon :cli:test --rerun-tasks` confirms the suite remains green under the full-access sandbox._

6. **I6 – REST endpoints + OpenAPI**  
   - Introduce failing MockMvc tests for `/api/v1/webauthn/evaluate`, `/evaluate/inline`, `/replay`.  
   - Wire controllers, request/response DTOs, telemetry, OpenAPI updates.  
   - **Next action (2025-10-10):** capture MockMvc expectations mirroring CLI/application behaviour, stage OpenAPI snapshot adjustments, and run the refreshed suite under `./gradlew --no-daemon :rest-api:test`.  
   - _2025-10-10 – MockMvc tests staged in `Fido2EvaluationEndpointTest`; controllers/DTOs/services implemented with sanitized telemetry. Regenerated `docs/3-reference/rest-openapi.json|yaml` via `OPENAPI_SNAPSHOT_WRITE=true GRADLE_USER_HOME=$PWD/.gradle ./gradlew --no-daemon :rest-api:test --tests io.openauth.sim.rest.OpenApiSnapshotTest`, then re-ran `./gradlew --no-daemon :rest-api:test --rerun-tasks` to confirm the refreshed contract._ 

7. **I7 – Operator UI enablement**  
   - Extend Selenium/system tests to assert tab activation, mode switching, preset buttons, seed control, accessibility.  
   - Implement Thymeleaf/JS updates to enable forms, load vectors (inline) and seed curated stored credentials.  
   - Surface key material as JWK in UI modals/panels.  
   - 2025-10-10 – Confirmed with product that the JSON “Load sample vector” controls remain inside the inline forms on Evaluate and Replay tabs after we adopt the HOTP/TOTP/OCRA layout (Option A).  
   - 2025-10-10 – Realigned the FIDO2 panel with Evaluate/Replay tabs, enabled inline replay, refreshed REST wiring, and updated Selenium/OpenAPI snapshots.  
   - **Next action (2025-10-10):** describe target WebAuthn panel interactions (stored/inline evaluate, replay diagnostics, “Load sample vector” + JWK display), stage failing Selenium specs, and keep HtmlUnit traces for regression debugging.  
   - Introduce failing Selenium assertions that expect success/invalid statuses to surface after Evaluate/Replay submissions and verify sanitized payload details (no challenge/signature leakage).  
   - Implement fetch/XHR flows in `ui/fido2/console.js` that POST inline/stored evaluate and replay requests to the REST endpoints, update result panels with sanitized responses (status, reason code, telemetry ID), and surface validation errors.  
   - Add defensive error handling for network failures (display `status=error`, keep telemetry sanitized) and ensure the result cards mirror HOTP/TOTP semantics.  
  - _2025-10-10 – WebAuthn operator panel activated with sanitized telemetry, seed helpers, and inline presets; Selenium coverage exercised via `./gradlew --no-daemon :rest-api:test --rerun-tasks` plus a focused rerun of `TotpOperatorUiSeleniumTest` to confirm stability._ 
  - _2025-10-11 – Added Selenium assertions confirming inline preset dropdown option text matches the curated generator labels before re-running `:rest-api:test` and `spotlessApply check`._ 
   - 2025-10-10 – **Follow-up:** Refactor Evaluate tab into an authenticator simulator. Stage failing Selenium/UI tests asserting assertion-generation output (signed authenticator data, client data JSON, signature) and removal of verification-only telemetry from the Evaluate result card before implementing JS/HTML changes.
  - _2025-10-11 – Regression observed: “Reset to now” button fails to refresh the signature counter in any toggle state; fix implemented by snapshotting epoch seconds in `ui/fido2/console.js` and locking behaviour with Selenium coverage (Reset helper)._ 
  - _2025-10-11 – Layout polish delivered: repositioned “Reset to now” inline to the right of the “Use current Unix seconds” label, aligned it to the field edge with added horizontal/vertical spacing, and verified via `GRADLE_USER_HOME=$PWD/.gradle ./gradlew --no-daemon spotlessApply check`._
  - _2025-10-11 – Inline/stored challenge textareas now default to a single row (still expandable) and private-key field groups gained a stacked, dark-styled layout so labels sit above the textarea._
  - _2025-10-11 – Sample-loaded private keys auto pretty-print their JWK JSON (two-space indent) so operators can read keys quickly while manual edits stay untouched._
  - _2025-10-11 – Trimmed the private-key hint text from inline/stored forms to keep the stacked layout clean; format expectations live in docs/how-to guides._
  - _2025-10-11 – Inline and stored challenge textareas now default to `rows=1` (matching credential ID fields) so operators see a compact form until they expand the inputs._
  - _2025-10-11 – Addressed inline generator regressions by aligning telemetry to `key=value` strings, seeding generator presets for stored flows, and updating Selenium coverage; `./gradlew --no-daemon :rest-api:test` is green post-fix._ 
  - _2025-10-11 – Copy parity follow-up: rename the inline preset label to “Load a sample vector” and update dropdown entries to “<algorithm> sample vector” so FIDO2 matches HOTP/TOTP/OCRA wording._
  - _2025-10-11 – Layout parity follow-up: reposition inline preset controls directly beneath the mode selector on Evaluate/Replay tabs and apply the shared `stack-offset-top-lg` spacing so FIDO2 mirrors HOTP/TOTP/OCRA._
  - _2025-10-12 – Trimmed inline preset container spacing on Evaluate/Replay inline forms so the relying-party grid sits directly beneath the preset hint (matching HOTP/TOTP/OCRA vertical rhythm); reran `GRADLE_USER_HOME=$PWD/.gradle ./gradlew --no-daemon spotlessApply check` (green) after the CSS adjustment._
  - _2025-10-11 – Placeholder parity: ensure inline preset selects load with no vector chosen by default so operators must opt in before fields autofill, matching HOTP/TOTP/OCRA behaviour._
  - _2025-10-11 – Result card parity: hide Evaluate/Replay result cards until after the first submission so the console no longer shows the “Awaiting submission” placeholder on load (implemented with state-tracked visibility + Selenium coverage)._ 
   - _2025-10-11 – Inline sample dropdowns now reuse the shared `.inline-preset` styling, matching the HOTP/TOTP/OCRA palette; Selenium helper hardened against stale selects and `./gradlew --no-daemon spotlessApply check` confirmed green._
   - _2025-10-11 – Removed inline “Credential name” inputs from Evaluate and Replay tabs; payloads now rely on server-side defaults while preserving sample metadata via telemetry._
   - _2025-10-11 – Converted the client-data-type entries to internal defaults (always `webauthn.get`), removed the UI inputs, and adjusted services to fall back automatically when callers omit the field._
   - _2025-10-11 – Dropped redundant “Load sample vector” buttons from inline Evaluate/Replay presets; dropdown change events now mirror HOTP/TOTP/OCRA behaviour._
   - _2025-10-11 – Identified regression where the host console defaulted the Evaluate tab back to stored mode; staged Selenium coverage and updated shared console scripts so inline remains the default even when protocol context restores prior state._
   - _2025-10-11 – Verified fix via `Fido2OperatorUiSeleniumTest.fido2EvaluateDefaultsToInline` after updating `ui/ocra/console.js` and `ui/fido2/console.js`; reran `./gradlew :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"` and full `spotlessApply check`._
  - _2025-10-11 – Hid the stored-only “Seed sample credentials” control whenever inline mode is active and added Selenium coverage (`Fido2OperatorUiSeleniumTest.seedControlHidesOutsideStoredMode`), exercised via `GRADLE_USER_HOME=$PWD/.gradle ./gradlew --no-daemon spotlessApply check`._
  - _2025-10-14 – Realigned the FIDO2 seed control with HOTP/TOTP/OCRA by keeping it visible throughout stored mode even when credentials exist, while extending Selenium coverage to assert visibility after seeding._
  - _2025-10-14 – Restyled the FIDO2 seed control to the compact left-aligned layout shared by HOTP/TOTP/OCRA; Selenium coverage updated to guard the layout regression._
  - _2025-10-11 – Reduced inline Credential ID text areas to a single-line default height while retaining textarea controls so operators can expand as needed; Selenium coverage unchanged (visual-only tweak)._
  - _2025-10-13 – Removed the stored “Load preset challenge & key” button; stored credential selection already auto-populates preset values, and operators can reset by re-selecting the placeholder option. Updated docs to reflect the streamlined flow._
  - _2025-10-13 – Trimmed the stored Evaluate result card telemetry line so the panel now focuses on the generated assertion JSON; sanitized telemetry remains in backend logs and inline mode retains its summary._
  - _2025-10-13 – Removed the stored Copy/Download controls; the assertion JSON now renders read-only text for manual copy, keeping inline mode as the only panel with quick actions._
  - **Next action (2025-10-11):** propagate the multi-algorithm generator presets into `Fido2OperatorSampleData`, update Thymeleaf/console JS preset selectors per algorithm, and stage documentation/test updates before rerunning Gradle quality gates.

8. **I8 – JSONL coverage expansion**  
   - Add failing parameterised tests iterating over JSONL bundle entries across core/application layers.  
   - Implement ingestion utilities (parsing Base64url, verifying algorithms) ensuring deterministic seeds.  
   - Update UI preset catalogue to include JSONL-only flows where useful.
   - _2025-10-10 – Added `WebAuthnJsonVectorVerificationTest` (core) and `WebAuthnJsonVectorEvaluationApplicationServiceTest` (application); extended `WebAuthnAssertionVerifier` to support ES384/ES512/RS256/PS256/EdDSA so both suites pass across all 42 JSON bundle vectors. CLI presets (`--vector-id`/`vectors`) and REST sample responses expose the full catalog for reproducibility, while the operator UI inline dropdown presents a curated subset to avoid clutter._ 

9. **I9 – Documentation & knowledge sync**  
   - Update how-to guides, roadmap, knowledge map, and protocol docs to reflect WebAuthn launch.  
   - Record telemetry contract additions, seed vector catalogue, and operator instructions.
   - _2025-10-10 – Authored new how-to guides for CLI, REST, and operator UI workflows (docs/2-how-to/use-fido2-*.md) and documented JWK/vector handling guidance._
   - _2025-10-11 – Refreshed REST/operator UI guides with per-algorithm generator preset tables, updated inline workflow descriptions, and synced the knowledge map to capture the multi-algorithm preset curation._

10. **I10 – Quality gate + follow-up capture**  
    - Run `./gradlew qualityGate`.  
    - Finalise feature documentation, resolve remaining TODOs, record lessons in plan/roadmap.  
    - Prepare conventional commit and ensure push after passing checks.  
    - _2025-10-10 – Quality gate executed via `./gradlew --no-daemon qualityGate`; reflection scan and aggregated coverage checks reported green, enabling wrap-up._

11. **I11 – Assertion generation UX**  
    - Update Evaluate tab request/response contract to accept relying party inputs, authenticator private key (auto-detect JWK or PEM/PKCS#8), and optional authenticator flags; generate signed assertions (authenticator data, client data JSON, signature) without invoking the verification pipeline.  
    - Add failing REST controller/application tests for assertion generation responses, including deterministic sample vectors and error cases for malformed JWK/PEM payloads.  
    - Update operator console JS/HTML to display generated payload blobs (with copy/download actions) and hide telemetry-style status text; ensure parsing errors surface inline.  
    - Adjust documentation/how-to guides to explain the new generation workflow, private-key formats, and updated Replay usage for verification.  
    - Initial implementation may emit ES256 assertions, but structure services/utilities to extend across RS256/PS256/ES384/ES512/EdDSA without UI rewrites.  
    - 2025-10-11 – Adopt the “Use current Unix seconds” default for the inline signature counter field, rendering the input read-only while the toggle remains checked and exposing a “Reset to now” helper to refresh the snapshot.
    - 2025-10-11 – Compact the inline and stored generator request forms by placing the relying party ID and origin inputs on a shared row with responsive wrapping for narrow viewports.
    - _2025-10-11 – Patched the REST generator flow so stored dropdowns enable correctly after seed refresh, mapped generator parse failures to `private_key_invalid`, and added `WebAuthnEvaluationServiceTest` to lock the behaviour. Selenium suite (`Fido2OperatorUiSeleniumTest`) passes alongside `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`.  `./gradlew --no-daemon spotlessApply check` now returns green._
    - 2025-10-10 – Confirmed: CLI `fido2 evaluate` commands will emit generated assertion payloads (no new `generate` verb), Evaluate result card presents a structured `PublicKeyCredential` JSON object with copy/download helpers, and operator presets provide ES256 JWK private keys only.  
    - 2025-10-11 – JSON vector bundle now carries `keyPairJwk` entries for every algorithm; expand `WebAuthnGeneratorSamples`/operator presets to surface curated RS256/PS256/ES384/ES512/Ed25519 keys alongside ES256 so multi-algorithm generators stay in sync.
    - 2025-10-11 – Added `.gitleaks.toml` allowlist so the higher-entropy JWK fixtures in `docs/webauthn_assertion_vectors.json` remain committed without tripping secret scans.

12. **I12 – Fixture hygiene & gitleaks alignment**  
    - Add `.gitleaks.toml` allowlisting the synthetic WebAuthn assertion bundles, documenting why the exemption is safe.  
    - Normalize JSON vector payloads to single-line base64 strings and update ingestion helpers to treat them as plain strings.  
    - Rename `publicKeyJwk` to `keyPairJwk` and enrich each entry with private-key parameters (`d`, RSA factors) so generator flows expose full JWK material without touching PEM files.  
    - Re-run `./gradlew spotlessApply check` to confirm formatting and tests remain green after the fixture change.  
    - _Status: Complete – 2025-10-11 – Allowlist added with documented rationale, fixtures flattened to single-line base64 strings, loader tightened to reject segmented arrays, full key-pair JWKs emitted, and `./gradlew --no-daemon spotlessApply check` verified the build stays green._
13. **I13 – Operator CTA copy alignment**  
    - Add failing Selenium assertions to verify Evaluate/Replay buttons present inline vs stored assertion wording when modes toggle.  
    - Update templates and console script to apply mode-specific labels, then rerun targeted `./gradlew :rest-api:test` cases and `./gradlew spotlessApply check`.  
    - _Status: Complete – 2025-10-12 – Added CTA copy/attribute Selenium checks, introduced dataset-driven label helpers in `console.js`, refreshed `Fido2OperatorUiSeleniumTest`, and validated via `./gradlew :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.evaluateButtonCopyMatchesMode"`, `--tests "...replayButtonCopyMatchesMode"`, plus `./gradlew spotlessApply check`._

14. **I14 – FIDO2 deep-link mode precedence**  
    - Add a failing Selenium test that opens `/ui/console?protocol=fido2&fido2Mode=replay`, verifies the Replay tab is active, refreshes the page, and asserts the mode persists.  
    - Update console state handling so query parameters override stored preferences on initial load/refresh while history/back navigation continues to restore prior selections.  
    - Re-run the targeted Selenium test alongside `./gradlew spotlessApply check`, then update the operator UI how-to guide if behaviour messaging changes.
    - _Status: Complete – 2025-10-12 – Added the deep-link Selenium regression, taught the host console to reapply FIDO2 state from query params (even when already active), introduced a lazy dataset helper, and revalidated via `./gradlew :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.deepLinkReplayModePersistsAcrossRefresh"` plus a full `./gradlew spotlessApply check` run._

15. **I15 – Replay deep-link inline mode visibility**  
    - Stage a failing UI test proving `/ui/console?protocol=fido2&fido2Mode=replay` renders the inline replay form (credential/public-key fields, telemetry inputs) instead of collapsing to the stored-only view.  
    - Adjust FIDO2 console bootstrap so deep-linked replay tabs honour the inline default before firing legacy compatibility hooks.  
    - Confirm the targeted test and `./gradlew spotlessApply check` both succeed.

16. **I16 – Additional W3C §16 fixtures**  
    - Convert the remaining WebAuthn Level 3 §16.1 authentication examples (ES256/384/512, RS256, Ed25519, platform variants) into the canonical `docs/webauthn_w3c_vectors.json`, capturing both the original hex literal and a Base64url rendering for every byte field.  
    - Document that Level 3 currently omits a packed PS256 authentication fixture so the simulator continues relying on the synthetic JSON bundle for that algorithm until the specification adds coverage.  
    - Extend loader utilities and unit tests to locate the new fixtures, adding at least one regression per algorithm that exercises the spec-authored data alongside the synthetic JSON bundle.  
    - Feed generator presets with any vector that now publishes a credential private key (derive JWKs from the attested public key + private material) so CLI/REST/UI generators can exercise W3C data directly.  
    - Update documentation (spec/plan/tasks) and rerun targeted test suites to confirm the additional fixtures compile, load, and pass verification.
    - _2025-10-12 – Normalized `webauthn_w3c_vectors.json`, unified the fixture loader, derived EC/RSA/EdDSA JWKs for generator presets, refreshed spec/plan/tasks/knowledge-map, and reran `:core:test`, `:application:test`, `:cli:test`, `:rest-api:test`, and `spotlessApply check`._

17. **I17 – Replay public-key format expansion**  
    - Extend the REST replay service (stored + inline), application layer, and supporting DTOs/validators to auto-detect COSE, JWK JSON, or PEM/PKCS#8 payloads carried in the existing `publicKey` field.  
    - Emit format-specific validation errors while keeping legacy Base64URL COSE behaviour intact, update OpenAPI contracts, and refresh CLI/REST/operator documentation.  
    - _2025-10-13 – Added `WebAuthnPublicKeyDecoder` + fixtures covering COSE/JWK/PEM flows, taught the REST replay service to surface `public_key_format_invalid`, refreshed the REST how-to guide, and re-ran `:application:test --tests "io.openauth.sim.application.fido2.WebAuthnPublicKeyDecoderTest"` plus `:rest-api:test --tests "io.openauth.sim.rest.Fido2ReplayEndpointTest"` (both green). 2025-10-13 – Reran `./gradlew --no-daemon spotlessApply check` after the `OcraOperatorUiSeleniumTest` regression cleared, closing the outstanding follow-up._  

18. **I18 – JWK field ordering in sample vectors**  
    - Update JSON vector renderers and preset serializers so `kty` is written first within every JWK object surfaced across CLI, REST responses, and operator UI presets.  
    - Preserve existing payload content, adjust formatting helpers/tests, and add regression coverage for the new field order.  
    - _2025-10-13 – Updated canonical JSON helpers to emit `kty` first for synthetic + W3C fixtures, added regression tests to enforce ordering, and validated via `./gradlew --no-daemon :core:test` plus a full `./gradlew --no-daemon spotlessApply check` run._
19. **I19 – Assertion result panel width clamp**  
    - Stage a failing Selenium regression that generates a WebAuthn assertion and asserts the result/status column remains narrower than the evaluation form column (and under the clamp threshold).  
    - Constrain the CSS for the status column/result panel to introduce a max-width with horizontal scrolling so Base64 payloads no longer expand the layout.  
    - Rerun the WebAuthn Selenium suite and `./gradlew spotlessApply check`, then record the outcome in spec/plan/tasks.  
    - _2025-10-13 – Selenium test `generatedAssertionPanelStaysClamped` failed first, the CSS clamp capped the status column at 600 px with horizontal scrolling, and both `:rest-api:test --tests io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest` and `./gradlew --no-daemon spotlessApply check` now pass._

20. **I20 – Protocol tab defaults & query parameter harmonisation**  
    - Add failing Selenium/UI tests that navigate between protocol tabs (HOTP/TOTP/OCRA/FIDO2), expecting the URL to update to `protocol=<key>&tab=evaluate&mode=inline` whenever a tab is clicked, regardless of prior state.  
    - Update console routing helpers so protocol changes force Evaluate/Inline mode, keeping existing evaluate/replay toggles functional when switching within a protocol.  
    - Extend the shared router to invoke each protocol façade’s `setMode(..., { broadcast: false, force: true })` hook when a deep link supplies `mode=stored` so HOTP/TOTP/OCRA/FIDO2 preserve stored selections across reloads without relying on synthetic radio clicks.  
    - Align query-parameter parsing and serialisation helpers to honour the shared `mode` key while preserving backwards compatibility redirects for any legacy `*Mode` parameters.  
    - Rerun `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.OperatorConsoleRoutingTest"` (or the Selenium suite covering protocol navigation) plus `./gradlew --no-daemon spotlessApply check`, then capture outcomes in spec/plan/tasks.
    - _2025-10-13 – Completed I20 by extending `OperatorConsoleUnificationSeleniumTest` with HOTP/FIDO2 replay deep-link regressions, wiring the router to forward stored/inline modes to each façade’s `setMode`/`setReplayMode` (with initial-mode fallbacks for pre-mounted consoles), ensuring HOTP replay toggles emit query updates so `mode` stays in sync, and validating the flow via `:rest-api:test --tests "…OperatorConsoleUnificationSeleniumTest"` plus `spotlessApply check`._  
    - _2025-10-13 – Follow-up: HOTP console now broadcasts `operator:hotp-tab-changed` when jumping from Evaluate → Replay so router state remains consistent before operators toggle replay mode._
    - _2025-10-13 – Follow-up: OCRA replay toggles now emit `operator:ocra-replay-mode-changed`, aligning the shared router with inline/stored selections after switching from Evaluate → Replay._

21. **I21 – Trim protocol-specific mode query parameters**  
    - Tighten the router so clicking HOTP/TOTP/OCRA/FIDO2 tabs writes only the shared `protocol`, `tab`, and `mode` parameters (e.g., drop `totpTab`, `totpMode`, `totpReplayMode`, `fido2Mode`).  
    - Backfill routing/unit tests that assert no protocol-specific mode parameters remain after tab activation, covering existing deep-link compatibility.  
    - Update documentation or clarifications if behaviour shifts, then rerun targeted Selenium specs and `./gradlew spotlessApply check`.
    - _2025-10-13 – Kicking off Increment I21 by preparing the routing regression tests; next step is to stage a failing Selenium case that asserts protocol tabs leave only the shared parameters in the URL before trimming the router serializers._
    - _2025-10-13 – Added `protocolTabNavigationOmitsLegacyQueryParameters`, refactored the router to emit only `protocol`, `tab`, and `mode`, taught the TOTP/FIDO2 consoles to parse the shared parameters while honouring legacy deep links, and reran `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.OperatorConsoleUnificationSeleniumTest"` plus `./gradlew --no-daemon spotlessApply check` (both green)._

22. **I22 – Stored dropdown styling parity**  
    - Add a failing Selenium regression that ensures the Evaluate stored credential dropdown inherits stacked field styling (label on its own row plus dark surface background) matching other protocol consoles.  
    - Update the FIDO2 evaluate template to apply the stacked field grouping or field-grid styling so the dropdown appears beneath the label with the same dark background as HOTP/TOTP/OCRA.  
    - Rerun `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"` alongside `./gradlew --no-daemon spotlessApply check`, then capture results here before marking the increment complete.
    - _2025-10-14 – Added the new Selenium regression, applied the `field-group--stacked` helper to the stored credential select, and re-ran the targeted test plus full `spotlessApply check` with green results._

23. **I23 – Stored preset label parity**  
    - Stage a failing Selenium regression that asserts stored credential dropdown entries omit the “Seed … generator preset” prefix and display algorithm-first text (mirroring HOTP/TOTP/OCRA).  
    - Update `WebAuthnGeneratorSamples` and `Fido2OperatorSampleData` so curated sample labels follow the new naming scheme (append W3C section suffix where applicable) without altering preset keys used by CLI/REST callers.  
    - Refresh docs/spec/tasks with the directive, rerun the targeted Selenium test plus `./gradlew --no-daemon spotlessApply check`, and record results once green.
    - _2025-10-14 – Added Selenium coverage, updated label generation + metadata, refreshed REST/operator docs, and re-ran the targeted test alongside `spotlessApply check`; dropdown entries now present algorithm-first text with W3C suffixes where applicable._

24. **I24 – Stored mode private key removal**  
    - Add failing Selenium coverage that asserts the stored Evaluate form never renders the authenticator private-key textarea while inline mode still does.  
    - Update the Thymeleaf template and client scripts so stored-mode submissions omit private-key inputs, ensure the relying-party ID displays as read-only text, and keep origin/challenge/counter/UV overrides editable.  
    - Adjust REST/application contracts if necessary to accept empty private-key payloads from stored mode, then rerun targeted Selenium tests plus `./gradlew --no-daemon spotlessApply check`; capture results here when green.
    - _2025-10-14 – Completed I24 by hiding the stored private-key textarea, backing the generator with a hidden field, marking the stored RP ID input `readonly`, updating `console.js` to continue seeding hidden key material, and rerunning the targeted Selenium suite plus `spotlessApply check` with green results._

25. **I25 – Seed message parity**  
    - Stage a failing Selenium regression that clicks the FIDO2 seed control with pre-seeded credentials and expects the warning copy “Seeded 0 sample credentials. All sample credentials are already present.” plus the amber status styling used by other protocols.  
    - Update REST/operator UI messaging (and any shared service/DTO responses) to emit the parity warning when zero records are created while keeping the success tone when inserts occur.  
    - Re-run `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"` along with `./gradlew --no-daemon spotlessApply check`, then document the green results before marking the increment complete.
    - _2025-10-14 – Landed the Selenium regression, switched the console seeding flow to parse added counts and emit parity copy + warning styling, updated seed template classnames, refreshed message predicates across existing UI tests, and finished with `:rest-api:test` + `spotlessApply check` green._

26. **I26 – Stored counter control parity**  
    - Add failing Selenium coverage asserting the stored Evaluate form mirrors the inline counter experience (stored counter pre-filled, read-only while “Use current Unix seconds” remains checked, reset helper present, overrides permitted once unchecked).  
    - Update the Thymeleaf template and `ui/fido2/console.js` to surface the stored counter value, reuse the inline toggle/reset controls, and keep override submission semantics unchanged.  
    - Re-run the targeted Selenium test plus `./gradlew --no-daemon spotlessApply check`, capture outcomes here, and note any service wiring adjustments required to transport the stored counter to the UI.  
    - _2025-10-14 – Completed I26 by adding the stored counter parity Selenium regression, wiring new toggle/reset controls in `panel.html` + `console.js`, verifying targeted test via `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.storedCounterControlsMirrorInlineBehaviour"`, and finishing with a green `./gradlew --no-daemon spotlessApply check`; no additional service wiring required._

27. **I27 – Result column overflow regression**  
    - Add failing Selenium coverage in the TOTP operator console suite that asserts the replay result column exposes visible overflow and preserves the status badge without clipping.  
    - Adjust the shared operator console stylesheet so `.status-column` restores visible overflow and introduces a small inline-end inset while keeping the FIDO2 horizontal scrollbars functional.  
    - Re-run the targeted Selenium test along with `./gradlew --no-daemon spotlessApply check`, recording the green results once the layout regression is fixed.
    - _2025-10-14 – Completed I27 by adding the `totpReplayResultColumnPreservesStatusBadge` Selenium regression (failed under the existing overflow clipping), updating `console.css` to restore visible overflow and add inline-end spacing, rerunning the targeted `:rest-api:test` plus `spotlessApply check`, and confirming the badge renders fully across HOTP/TOTP/OCRA panels._

28. **I28 – Replay result panel parity**  
    - Stage a failing Selenium regression that asserts the FIDO2 replay result card renders identically to the HOTP/TOTP/OCRA panels (status badge + “Reason Code” and “Outcome” rows only, no telemetry lines).  
    - Update the Thymeleaf template, shared panel partials, and any CSS helpers so the replay card layout matches the other protocols while still binding reason/outcome values from the existing DTO.  
    - Re-run the targeted Selenium test along with `./gradlew --no-daemon spotlessApply check`, documenting outcomes before closing the increment.  
    - _2025-10-14 – Added the layout parity Selenium regression, refactored the replay result sections and console script to emit status badge + reason/outcome rows (removing telemetry/messages), exercised the full `Fido2OperatorUiSeleniumTest` along with `spotlessApply check`, and captured green runs for both commands._

29. **I29 – Reason code parity**  
    - Stage failing service/UI tests asserting successful FIDO2 evaluation/replay returns reason code “match” (status badge + rows) to mirror HOTP/TOTP/OCRA.  
    - Update `WebAuthnEvaluationApplicationService` (and any wrappers) so success reason codes normalize to “match”, adjust replay DTO/JS bindings if needed, and refresh Selenium expectations.  
    - Re-run targeted application/service/REST/UI tests plus `./gradlew --no-daemon spotlessApply check`, recording results after the change.  
    - _2025-10-14 – Updated application/REST/UI test expectations to `match`, normalized the success reason in the evaluation service (and replay wrappers/JS), ran targeted FIDO2 unit + Selenium tests, and closed with a green `./gradlew --no-daemon spotlessApply check` (Ocra Selenium suite required a retry due to a transient HtmlUnit error)._

30. **I30 – Replay payload visibility**  
    - Stage a failing Selenium regression confirming the inline and stored replay forms visibly display credential ID, public key, challenge, client data, authenticator data, and signature fields populated by presets.  
    - Update the FIDO2 replay template and supporting JS so these assertion payload fields render as visible textareas (read-only only when the protocol mandates it), fulfilling the 2025-10-15 Option A directive.  
    - Re-run the focused Selenium suite plus `./gradlew --no-daemon spotlessApply check`, documenting green outcomes on completion.

31. **I31 – Replay presets default to JWK public keys**  
    - Stage failing Selenium coverage ensuring inline replay presets populate the public-key textarea with the JWK representation while still accepting manual COSE/Pem entries.  
    - Extend operator sample data and console wiring so loaded presets supply JWK strings by default (fall back to COSE when JWK is unavailable).  
    - Re-run the targeted Selenium test and `./gradlew --no-daemon spotlessApply check`; capture outcomes once green.  
    - _2025-10-15 – Added derived JWK strings to operator sample data, updated the console to populate JWK text by default, extended Selenium coverage, and verified via targeted `:rest-api:test` plus `spotlessApply check`._

32. **I32 – Stored credential placeholder & refresh parity**  
    - Stage failing Selenium coverage asserting the stored credential dropdown defaults to the placeholder while leaving Evaluate/Replay CTAs enabled, and that switching credentials refreshes both stored evaluate and stored replay forms/results.  
    - Update the FIDO2 console JS to honour the placeholder default, keep action buttons active, and synchronise both form sections (and their status panels) whenever the selection changes.  
    - Re-run the focused Selenium suite (`./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.*StoredCredential*"`), then finish with `./gradlew --no-daemon spotlessApply check`.  
    - _2025-10-15 – Added placeholder/refresh Selenium regression, centralised stored-selection state in `console.js` (syncing evaluate/replay forms and resetting result panels), verified replay dropdown change updates signatures, and completed the Gradle runs noted above (spotless needed a second attempt with extended timeout)._

## Analysis Gate
- 2025-10-13 – Checklist completed after adding Increment T20; spec clarifications synced (protocol/tab/mode + default Evaluate/Inline), plan/tasks updated, no open questions remain. All items pass; next step is staging failing Selenium routing tests before implementation.

## Risks & Mitigations
- **Large vector set increases test time** → run JSONL suite in targeted tests (I8) with caching helpers; consider tagging for selective execution.  
- **Signature algorithm variance** → rely on deterministic fixtures; add explicit assertions for DER decoding vs raw Ed25519.  
- **UI seed overload** → curated subset for seed button; document advanced usage for manual imports.

## Telemetry & Observability
- Add `TelemetryContracts` entries: `fido2.evaluate` and `fido2.replay`, capturing algorithm, RP ID, UV flag, outcome.  
- Ensure CLI/REST/UI call the shared adapters; include unit tests verifying redaction.

## Intent & Tooling Notes
- Session decisions (2025-10-09) captured in spec clarifications and open-questions log (resolved).  
- Gradle wrapper configured with `GRADLE_USER_HOME=$PWD/.gradle` to avoid home-directory locks; CLI/REST/Application suites re-executed locally via `./gradlew --no-daemon :cli:test --rerun-tasks`, `:rest-api:test --rerun-tasks`, `:application:test --tests "io.openauth.sim.application.fido2.*"`, and `spotlessApply check`.  
- JSONL + W3C fixtures will be version-controlled to avoid external fetches.
- 2025-10-12 – Delivered I13 by adding CTA copy Selenium coverage, wiring dataset-based label helpers in `ui/fido2/console.js`, rerunning targeted `:rest-api:test` cases, and completing `./gradlew spotlessApply check`.
