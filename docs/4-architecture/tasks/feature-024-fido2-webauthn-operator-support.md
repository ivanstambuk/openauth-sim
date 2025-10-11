# Feature 024 Tasks – FIDO2/WebAuthn Operator Support

_Linked plan:_ `docs/4-architecture/feature-plan-024-fido2-webauthn-operator-support.md`  
_Status:_ In Progress  
_Last updated:_ 2025-10-10

☑ **T1 – Stage W3C vector fixtures**  
 ☑ Convert selected W3C §16 authentication vectors to Base64url fixtures under `docs/webauthn_w3c_vectors/`.  
 ☑ Add failing core verification tests (success + RP ID mismatch + signature failure cases).  
 _2025-10-09 – Created `packed-es256.properties`, introduced `WebAuthnAssertionVerifierTest`, and verified red state via `./gradlew :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAssertionVerifierTest"` (fails with `UnsupportedOperationException`)._  

☑ **T2 – Implement verification engine**  
 ☑ Implement minimal parser/validator satisfying T1 tests (flags, RP ID hash, origin/type, signature base).  
 ☑ Add additional failure branch tests (UV required vs optional, counter regression).  
 _2025-10-09 – Delivered `WebAuthnAssertionVerifier` with COSE→EC key conversion, client data parsing, RP hash verification, UV/counter guards, and ECDSA signature checks. Verified via `./gradlew :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAssertionVerifierTest"` and full `./gradlew spotlessApply check`._  

☑ **T3 – Persistence support**  
 ☑ Add failing integration tests ensuring MapDB schema v1 stores/retrieves FIDO2 credentials with metadata.  
 ☑ Implement persistence descriptors and schema wiring, keeping HOTP/TOTP/OCRA compatibility.  
 _2025-10-10 – Added WebAuthn persistence adapter + descriptor, extended MapDB integration tests for FIDO2 metadata coverage; re-ran `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.store.MapDbCredentialStoreTest"` to confirm the new schema wiring now that sandbox socket restrictions are lifted._  

☑ **T4 – Application services & telemetry**  
 ☑ Add failing application service tests for stored/inline evaluation and replay diagnostics (telemetry assertions).  
  _2025-10-10 – Introduced `WebAuthnEvaluationApplicationServiceTest`/`WebAuthnReplayApplicationServiceTest` exercising success/failure branches; current production stubs throw `UnsupportedOperationException` so tests remain red until services are implemented._  
 ☑ Implement services emitting `fido2.evaluate` / `fido2.replay` events with redacted payloads.  
  _2025-10-10 – Implemented evaluation+replay coordinators with sanitized telemetry fields and persistence lookups. Executed `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.*"` and all scenarios now pass locally._

☑ **T5 – CLI façade**  
 ☑ Add failing Picocli tests covering evaluate (stored/inline) and replay commands.  
  _2025-10-10 – Authored `Fido2CliTest` staging stored success, inline origin mismatch, and replay flows; promoted shared WebAuthn fixtures into the core module so downstream facades can compile._  
 ☑ Implement CLI commands with HOTP/OCRA parity (validation, output, telemetry).  
  _2025-10-10 – Added `Fido2Cli` with stored/inline evaluate and replay commands emitting sanitized telemetry, enabled optional boolean parsing for `--user-verification-required`, and re-ran `./gradlew --no-daemon :cli:test --rerun-tasks` to validate the Picocli coverage._

☑ **T6 – REST endpoints**  
 ☑ Add failing MockMvc + OpenAPI snapshot tests for `/api/v1/webauthn/evaluate`, `/evaluate/inline`, `/replay`.  
  _2025-10-10 – Added `Fido2EvaluationEndpointTest` (stored/inline/replay flows) expecting sanitized payloads; executed `./gradlew --no-daemon :rest-api:test --rerun-tasks` to exercise the suite and followed up with a targeted rerun of `TotpOperatorUiSeleniumTest.storedTotpCredentialEvaluationSucceeds` to confirm stable results._  
 ☑ Implement controllers/DTOs, ensure telemetry + validation, update OpenAPI docs.  
  _2025-10-10 – Added `rest.webauthn` controllers/services/DTOs with sanitized metadata, regenerated `docs/3-reference/rest-openapi.json|yaml` via `OPENAPI_SNAPSHOT_WRITE=true GRADLE_USER_HOME=$PWD/.gradle ./gradlew --no-daemon :rest-api:test --tests io.openauth.sim.rest.OpenApiSnapshotTest`, and confirmed the refreshed contract alongside a clean `:rest-api:test` run._  

☐ **T7 – Operator UI enablement**  
☑ Extend Selenium/system + accessibility tests for WebAuthn panels (presets, seed button, keyboard navigation).  
 _2025-10-10 – Added `Fido2OperatorUiSeleniumTest` covering stored/inline evaluate, replay, and preset interactions; suite executed via `./gradlew --no-daemon :rest-api:test --rerun-tasks`, with HtmlUnit traces captured for regression auditing._  
☑ Implement Thymeleaf/JS updates enabling forms, preset loading, curated seed action, JWK display.  
 _2025-10-10 – Activated WebAuthn panel with sanitized telemetry, seed/fetch helpers, and sample vector presets; verified UI flows through the same `:rest-api:test` Selenium pass and a follow-up `spotlessApply check` run._  
☑ Align panel layout with HOTP/TOTP/OCRA Evaluate/Replay structure while keeping JSON “Load sample vector” controls within inline forms.  
 _2025-10-10 – Restructured the FIDO2 template/console script to expose Evaluate/Replay tabs with stored/inline sub-modes, retained inline sample dropdowns, enabled inline replay, refreshed Selenium coverage, and re-ran `./gradlew --no-daemon spotlessApply check`._  
☑ Adjust FIDO2 evaluate/replay mode ordering to list inline parameters before stored credentials for parity with HOTP/TOTP/OCRA.  
 _2025-10-10 – Updated panel fieldsets/sections so inline appears first while stored remains default-selected; reran `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"` to confirm the layout change._  
☑ Default FIDO2 Evaluate/Replay selections to inline parameters while keeping stored flows accessible.  
 _2025-10-10 – Switched initial radio state + console bootstrap to inline defaults and revalidated via `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`._  
☑ Add failing Selenium coverage that expects Evaluate/Replay submissions to surface success/invalid statuses and sanitized telemetry fields.  
 _2025-10-10 – Extended `Fido2OperatorUiSeleniumTest` to assert validated/invalid outcomes, reason codes, and sanitized telemetry for stored/inline evaluate and replay flows; captured red state prior to wiring the console JS._  
☑ Wire `ui/fido2/console.js` evaluate/replay buttons to POST the inline/stored payloads to the REST endpoints, update the result cards with sanitized responses, and surface validation errors.  
 _2025-10-10 – Added JSON POST orchestration for stored/inline evaluate + replay, sanitized success/error handlers, and sample ingestion alignment via stored replay presets. Verified green via `./gradlew --no-daemon :rest-api:test`._  
☑ Handle transport failures gracefully (telemetry stays sanitized, status reflects error) to mirror HOTP/TOTP UX.  
  _2025-10-10 – Normalised pending/error helpers, sanitized message handling, and ensured replay/evaluate status cards surface network validation errors without leaking payload data._

_2025-10-11 – Fixed the inline generator regression by switching stored presets to the `generator-es256` sample, reshaping telemetry strings to `key=value`, and refreshing the Selenium suite (`./gradlew --no-daemon :rest-api:test`)._

☐ **T8 – JSON bundle coverage**  
 ☑ Add failing parameterised tests iterating over `docs/webauthn_assertion_vectors.json` across core/application layers.  
  _2025-10-10 – Introduced `WebAuthnJsonVectorVerificationTest` (core) and `WebAuthnJsonVectorEvaluationApplicationServiceTest` (application); after extending the verifier to handle ES384/ES512/RS256/PS256/EdDSA, both suites now pass via `GRADLE_USER_HOME=$PWD/.gradle ./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.WebAuthnJsonVectorVerificationTest"` and `:application:test --tests "io.openauth.sim.application.fido2.WebAuthnJsonVectorEvaluationApplicationServiceTest"`, covering every JSON bundle vector._  
 ☑ Implement ingestion helpers and expand presets to include JSONL flows where appropriate.  
  _2025-10-10 – Wired `WebAuthnJsonVectorFixtures` into CLI (`--vector-id` + `vectors` listing) and REST (`/api/v1/webauthn/credentials/*` sample responses) to expose the full JSONL catalog, while the operator UI inline sample dropdown now presents a curated subset to keep the console concise._  

☑ **T9 – Documentation & telemetry updates**  
 ☑ Update how-to guides, roadmap, knowledge map, and telemetry docs with WebAuthn coverage + seed info.  
 ☑ Ensure documentation reflects JWK preference and vector handling.  
   _2025-10-10 – Authored FIDO2 CLI/REST/UI how-to guides (docs/2-how-to/use-fido2-*.md) and documented JWK-focused guidance for vector handling._  

☐ **T10 – Quality gate & wrap-up**  
 ☑ Run `./gradlew spotlessApply check` and `./gradlew qualityGate`; resolve issues.  
  _2025-10-10 – Executed `./gradlew --no-daemon spotlessApply check`, revalidated Selenium focus flows, and finished with `./gradlew --no-daemon qualityGate` (reflection scan + coverage verification all green)._  
 ☑ Finalise spec/plan/task updates, capture lessons, and prepare conventional commit & push.  
  _2025-10-10 – Synced feature spec/plan/tasks, roadmap, and knowledge map; prepared aggregated change notes ahead of the WebAuthn commit/push._  
  _Follow-up: document the deferred HOTP/TOTP preset parity feature before final review so the roadmap reflects the outstanding alignment item._

☐ **T11 – Evaluate tab assertion generation**  
 ☐ Stage failing application + REST tests for assertion generation endpoints returning authenticator data, client data JSON, and signature (with telemetry limited to Replay), including malformed-key scenarios.  
 ☐ Refactor Evaluate tab controller/service/JS/HTML to accept authenticator private key inputs (auto-detect JWK or PEM/PKCS#8), generate assertions, and display/download the resulting payloads.  
 ☐ Update Selenium coverage and operator docs to cover the new generation workflow and surface parsing errors clearly.
