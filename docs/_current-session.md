# Current Session Snapshot

> Keep this file up to date across all active chats. Treat it as the single source of truth for in-progress workstreams so every hand-off is instant. Replace the bracketed text and prune sections you do not need.

## Meta
- Date: 2025-10-18
- Primary branch: `main`
- Other active branches: none
- Last green commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationGenerationProducesDeterministicPayload"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.OcraOperatorUiSeleniumTest.inlinePolicyPresetEvaluatesSuccessfully"`; `./gradlew --no-daemon spotlessApply check` (all completed 2025-10-18).
- Quality gate note: Full pipeline (`spotlessApply check`) plus targeted REST/UI Selenium suites rerun 2025-10-18; all tasks exited green after re-running a flaky Ocra Selenium scenario.
- Outstanding git state: Attestation UI updates touch `rest-api/src/main/resources/static/ui/fido2/console.js`, `rest-api/src/main/resources/templates/ui/fido2/panel.html`, Selenium coverage under `rest-api/src/test/java/io/openauth/sim/rest/ui/Fido2OperatorUiSeleniumTest.java`, and the Feature 026 tasks doc; prior Feature 026 edits remain unstaged (see `git status -sb`).

## Workstream Summary
| Workstream | Status | Last Increment | Next Increment | Notes |
|------------|--------|----------------|----------------|-------|
| Feature 026 – FIDO2/WebAuthn Attestation Support | In progress | T2628 (Shared private-key parser integration) | T2628 – Surface pretty-printed JWK presets & prune Base64URL code paths; ensure manual attestation labels reflect JWK/PEM-only support after removing `attestationId` | Parser shared across core/application/CLI/REST; Option B decisions locked to require JWK or PEM inputs and render presets as multi-line JWK JSON ahead of UI/docs/test updates. |
| Feature 027 – Unified Credential Store Naming | In progress | T2704 (Documentation refresh and migration guidance) | TBD – Coordinate fallback deprecation timeline after telemetry confirms unified default adoption | Factory/CLI/REST defaults updated to `credentials.db`, docs refreshed with legacy fallback notes; monitoring telemetry before retiring legacy probes. |

## Active TODOs / Blocking Items
- [x] Stage failing application-layer tests for attestation services (Feature 026, T2603). (_2025-10-16 – Added attestation verification/replay tests and skeletal services; superseded by the now-green implementation._)
- [x] Align unexpected documentation updates (`AGENTS.md`, `docs/5-operations/runbook-session-reset.md`, new quick-reference files) with owner direction. (_2025-10-16 – Owner confirmed they are the authoritative baseline._)
- [x] Implement attestation application services + telemetry to satisfy the new failing tests; refreshed plan/tasks/knowledge map and ran full quality gate (2025-10-16).
- [x] Stage CLI attestation command tests (Feature 026, T2604) before wiring Picocli façade. (_2025-10-16 – `Fido2CliAttestationTest` now captures self-attested fallback, trust-anchor success, and challenge mismatch paths; tests red pending CLI wiring._)
- [x] Implement CLI attestation commands and update help/telemetry output (Feature 026, T2604). (_2025-10-16 – Added `fido2 attest`/`attest-replay` Picocli commands, trust-anchor parsing, telemetry wiring, and refreshed CLI how-to doc; staged tests now pass._)
- [x] Stage REST attestation endpoint tests before wiring controllers (Feature 026, T2605). (_2025-10-16 – Added MockMvc coverage in `Fido2AttestationEndpointTest`.)_
- [x] Implement REST attestation controllers/DTOs and regenerate OpenAPI artifacts (Feature 026, T2605). (_2025-10-16 – Implemented attestation controller/service with PEM trust-anchor parsing, telemetry mapping, refreshed OpenAPI snapshots; targeted test command: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`._)
- [x] Stage operator UI attestation Evaluate tests before wiring frontend changes (Feature 026, T2606). (_2025-10-16 – Added attestation toggle/inline-only/trust-anchor Selenium coverage in `rest-api/src/test/java/io/openauth/sim/rest/ui/Fido2OperatorUiSeleniumTest.java`; suite now green alongside the toggle implementation via `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestation*"`._)
- [x] Stage operator UI attestation Replay tests before wiring frontend changes (Feature 026, T2607). (_2025-10-17 – `attestationReplay*` Selenium suite now covers success + invalid anchor paths; initial run red.)_
- [x] Implement Replay tab attestation wiring (Feature 026, T2607). (_2025-10-17 – Replay ceremony toggle, attestation form, trust-anchor parsing, and telemetry/error panels now live; green via `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplay*"` + `./gradlew --no-daemon spotlessApply check`._)
- [x] Stage attestation fixture ingestion tests before wiring loader changes (Feature 026, T2608). (_2025-10-17 – Added failing fixture-ingestion coverage across core/app/CLI/REST; superseded by the ingestion implementation below._)
- [x] Extend fixture loaders/docs/roadmap for attestation datasets and record follow-ups (Feature 026, T2608). (_2025-10-17 – Ingestion wiring plus doc/roadmap updates landed; spotless check verified the build._)
- [x] Stage MDS scaffolding tests and parsing plan (Feature 026, T2610). (_2025-10-17 – Added offline dataset plus `WebAuthnMetadataCatalogueTest`, now green alongside the loader via `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnMetadataCatalogueTest"` (16.7 s)._ )
- [x] Integrate offline MDS catalogue with trust-anchor resolver and document refresh workflow (Feature 026, T2610 follow-up). (_2025-10-17 – Resolver now merges metadata/manual anchors, curated production roots (`curated-mds-v3.json`), and the offline MDS refresh guide is published._)
- [x] Document offline MDS refresh workflow + operator guidance (Feature 026, T2610 follow-up). (_2025-10-17 – Added `docs/2-how-to/refresh-offline-webauthn-metadata.md` capturing catalogue edits, test commands, and operator steps._)
- [x] Stage generator regression tests across layers (Feature 026, T2611) before wiring new services. (_2025-10-17 – Added failing generator coverage across core/application/CLI/REST/UI; pending implementation._)
- [x] Implement core generator with selectable signing modes (Feature 026, T2612). (_2025-10-17 – Added `WebAuthnAttestationGenerator` plus application service wrapper; verified via `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAttestationGeneratorTest"` and `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationServiceTest"`._)
- [x] Rewire CLI/REST Evaluate flows to call the generator and update OpenAPI/help text (Feature 026, T2613). (_2025-10-17 – `fido2 attest` and `/api/v1/webauthn/attest` now emit generated payloads; OpenAPI snapshots/CLI docs refreshed; targeted tests: `:cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationTest"`, `:rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`.)
- [x] Migrate operator UI Evaluate tab to generation-only semantics, including button relabeling (Feature 026, T2614). (_2025-10-17 – Templates/`console.js` now drive generator requests; Selenium `attestationGenerationProducesDeterministicPayload` verifies rendered attestationObject/clientData/challenge outputs._)
- [x] Repository lint follow-up: per Option A decision, fix the offending core/application/cli sources/tests so `./gradlew --no-daemon spotlessApply check` returns green (see local build output for exact rule hits). (_2025-10-17 – Added record-body comments + CLI cleanup; pipeline now green._)
- [x] Remove redundant attestation metadata rows from the Evaluate result panel and refresh Selenium coverage (T2616). (_2025-10-17 – Template/JS trimmed; Selenium + full pipeline rerun._)
- [x] Implement Option B: trim generated attestation response payloads to `clientDataJSON` + `attestationObject`, dropping `expectedChallenge`, raw certificate chains, and `signatureIncluded`; update CLI/REST/UI and docs accordingly (Feature 026, T2617). (_2025-10-18 – Updated application/REST/CLI/JS, regenerated OpenAPI snapshots, refreshed docs, and reran CLI/REST/Selenium suites + `spotlessApply check`._)
- [x] Restore the attestation certificate-chain count hint in the operator UI (T2625). (_2025-10-18 – Updated `panel.html`/`console.js` to render `certificate chain (N)`, refreshed Selenium expectations, reran targeted REST/UI tests, and completed `spotlessApply check`._)
- [x] Remove the attestation result “response” subheading so the JSON renders directly beneath the card header (T2626). (_2025-10-18 – Updated `panel.html` to inline the JSON block, refreshed Selenium assertions to enforce a single subtitle, and reran targeted REST/UI suites plus `spotlessApply check`._)
- [x] Restyle the attestation certificate-chain heading to use title case and the standard result typography (`section-title`) (T2627). (_2025-10-18 – Promoted the heading to the `section-title` style, capitalised the label, added a `certificate-chain-block` spacer for breathing room, updated JS defaults, refreshed Selenium coverage, and reran targeted tests plus `spotlessApply check`._)
- [ ] T2628 – Attestation private key format parity (render presets/manual outputs as pretty-printed JWK JSON, require JWK or PEM inputs, drop Base64URL branches across core/app/CLI/REST/UI/docs/tests, regenerate OpenAPI + Selenium coverage). (_2025-10-18 – Parser + CLI/REST integration landed; next step is converting fixtures/UI/docs and pruning legacy Base64URL code paths._)
- [x] T2701 – Governance sync for unified credential store naming (roadmap/knowledge-map/current-session updates plus spec/tasks alignment). (_2025-10-18 – Completed via Feature 027 kickoff updates.)
- [x] T2702 – Persistence fallback implementation for legacy credential filenames (update `CredentialStoreFactory`, add tests/logging). (_2025-10-18 – Factory updated, legacy fallback logging in place, targeted infra-persistence/rest-api tests green.)

> Open questions live exclusively in `docs/4-architecture/open-questions.md`; consult that log for any pending clarifications.

## Upcoming Focus
- [ ] T2618 – Core Manual input source scaffolding (pending kickoff once Manual-mode clarifications are fully consumed in the spec).

## Reference Links
- Roadmap entry: `docs/4-architecture/roadmap.md` (Workstream 21)
- Specification: `docs/4-architecture/specs/feature-026-fido2-attestation-support.md`
- Feature plan: `docs/4-architecture/feature-plan-026-fido2-attestation-support.md`
- Tasks checklist: `docs/4-architecture/tasks/feature-026-fido2-attestation-support.md`
- Quick reference: `docs/5-operations/session-quick-reference.md`

> Update this snapshot before ending each session and after significant context changes.
