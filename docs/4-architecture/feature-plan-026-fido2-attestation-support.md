# Feature Plan 026 – FIDO2/WebAuthn Attestation Support

_Linked specification:_ `docs/4-architecture/specs/feature-026-fido2-attestation-support.md`  
_Status:_ In Progress  
_Last updated:_ 2025-10-18

## Vision & Success Criteria
- Provide end-to-end attestation generation and verification across core, application services, CLI, REST API, and operator UI, mirroring the existing assertion workflow.
- Cover the packed, FIDO-U2F, TPM, and Android Key attestation formats with deterministic fixtures (W3C Level 3 + synthetic bundle).
- Maintain sanitized telemetry (`fido2.attest`, `fido2.attestReplay`) and redaction rules equivalent to assertion flows.
- Keep attestation generation inline-only in the operator UI while preserving stored assertion generation.
- Allow operators to supply optional trust anchor bundles (UI text area, CLI/REST inputs) while defaulting to self-attested acceptance and laying groundwork for WebAuthn MDS ingestion.
- Ensure `./gradlew spotlessApply check` plus targeted suites (`:core:test`, `:application:test`, `:cli:test`, `:rest-api:test`) remain green after each increment.

## Scope Alignment
- In scope: attestation generation & replay helpers, telemetry, CLI/REST endpoints, UI toggles/forms, fixture ingestion, trust-anchor configuration (inline uploads), documentation updates, and initial MDS ingestion scaffolding.
- Evaluate flows across CLI/REST/operator UI must generate attestation payloads (attestationObject, clientDataJSON, challenge) while Replay owns verification scenarios.
- Out of scope: MapDB persistence of attestation payloads, attestation formats beyond the four specified types, hardware-backed certificate chain validation beyond deterministic fixtures.

## Dependencies & Interfaces
- Builds on existing FIDO2 assertion utilities (COSE parsing, JWK conversion).
- Stores attestation fixtures under `docs/webauthn_attestation/` with per-format JSON files while reusing shared loader patterns.
- Operator UI adjustments hinge on the current Evaluate/Replay tab layout; ensure toggle logic integrates with existing state management.
- Telemetry contracts must extend `TelemetryContracts` without breaking existing assertion events.
- Trust anchor handling must accept inline PEM bundles and prepare for optional MDS-sourced metadata without introducing external network calls.

## Increment Breakdown (≤10 min each)
_2025-10-18 – Active increment: T2628 now reuses `WebAuthnPrivateKeyParser` across application/CLI/REST so attestation generation accepts JWK and PEM/PKCS#8 inputs; next steps convert presets/UI bindings to multi-line JWK output and remove legacy Base64URL branches._
1. **I1 – Fixture + test scaffolding**  
   - Convert targeted W3C and synthetic attestation vectors into per-format JSON fixtures under `docs/webauthn_attestation/` (`packed.json`, `fido-u2f.json`, `tpm.json`, `android-key.json`).  
   - Add failing core tests for attestation generation/verification covering the four formats (happy path + invalid cases) using `WebAuthnAttestationFixtures`.  
   - Update spec clarifications if additional vector gaps emerge.  
   - Commands: `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAttestationFixturesTest"` / `WebAuthnAttestationVerifierTest`.  
   - Notes: Loader coverage landed alongside the attestation verifier scaffold; verifier tests promoted to active coverage after I2 landed.

2. **I2 – Core attestation engine**  
   - Implement attestation generation/verification helpers in `core`, satisfying I1 tests.  
   - Ensure format-specific validation (certificate parsing, nonce/hash checks) and error enums.  
   - Add failure-branch tests for bad signatures, unsupported formats, and certificate mismatches.
   - _2025-10-16 – Delivered `WebAuthnAttestationVerifier` with packed/FIDO-U2F/TPM/Android Key flows, certificate parsing, TPM extraData validation, and negative tests for tampered signatures and format mismatches; verification result wrapper now surfaces attested credentials + certificate chains._

3. **I3 – Application services & telemetry**  
   - Stage failing application-level tests for attestation generation/replay telemetry, trust-anchor enforcement (anchors supplied vs absent), and self-attested warnings.  
   - Implement services emitting sanitized `fido2.attest` / `fido2.attestReplay` signals.  
   - _2025-10-16 – Added attestation verification/replay application services backed by a shared support helper; telemetry now records format/RP/AAGUID/anchor provenance, trust anchors drive success vs fallback, and new tests pass._  
   - Verify inline-only flows, trust-anchor fallback warnings, and error mapping.  
   - Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestation*"` (expected red → green).

4. **I4 – CLI façade**  
  - Add failing Picocli tests for `fido2 attest` (generate) and `fido2 attest replay` commands, including trust-anchor arguments.  
    _2025-10-16 – `Fido2CliAttestationTest` stages self-attested fallback, trust-anchor success, and challenge mismatch paths._  
  - Wire CLI commands to the new application services, ensuring format selection, trust-anchor usage, and telemetry output.  
    _2025-10-16 – Implemented attestation CLI façade with trust-anchor parsing, telemetry bridging, and help/guide updates; staged tests now pass via `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationTest"`._  
  - Commands: `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationTest"`.  

5. **I5 – REST endpoints**  
   - Add failing MockMvc + OpenAPI tests for `/api/v1/webauthn/attest` and `/api/v1/webauthn/attest/replay`, including optional trust-anchor payloads and self-attested warnings.  
     _2025-10-16 – `Fido2AttestationEndpointTest` now asserts trust-anchor success, self-attested fallback, and challenge mismatch paths._  
   - Implement controllers/DTOs with validation, format selection, trust-anchor handling, and sanitized responses.  
     _2025-10-16 – Implemented attestation controller/service with PEM trust-anchor parsing, telemetry surface, and sanitized responses._  
   - Regenerate OpenAPI snapshots.  
     _2025-10-16 – Refreshed `docs/3-reference/rest-openapi.{json,yaml}` via `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`._  
   - Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`.

6. **I6 – Operator Evaluate toggle**  
   - Add failing Selenium tests asserting the Evaluate tab exposes an Assertion/Attestation toggle, renders attestation-specific inputs/result card, and surfaces an optional trust-anchor text area with validation.  
     _2025-10-16 – Selenium coverage now expects a ceremony toggle (`data-mode` assertion/attestation), inline-only lock, attestation payload inputs, and trust-anchor helper block in `Fido2OperatorUiSeleniumTest`; the suite now passes alongside the implemented UI via `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestation*"`._  
   - Implement toggle, attestation form, trust-anchor upload field (with persistence to local storage), and attestation result display (copy/download).  
     _2025-10-16 – Ceremony toggle, inline-only locking, and inline attestation form (including trust-anchor helper and result panel scaffolding) now live in `panel.html`; `console.js` orchestrates assertion/attestation state, hides stored controls, and exposes attestation result plumbing. Selenium attestation specs now pass via `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestation*"`._  
   - Confirm assertion workflows remain unaffected.  
   - Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationEvaluate*"` (expected red → green).

7. **I7 – Operator Replay toggle**  
   - Add failing Selenium tests for attestation replay verification (inline-only) including payload validation feedback and trust-anchor enforcement messaging.  
     _2025-10-17 – Introduced Replay attestation Selenium coverage exercising the ceremony toggle, inline-only constraints, success metadata, and invalid-anchor handling; initial run red via `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplay*"`._  
   - Implement UI/JS wiring for attestation verification, telemetry display, trust-anchor reuse, and error handling.  
     _2025-10-17 – Extended `panel.html` + `console.js` with Replay attestation UI, trust-anchor parsing, telemetry propagation, and error banner wiring; suite now green with `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplay*"`. Full quality gate revalidated via `./gradlew --no-daemon spotlessApply check`._  
   - Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplay*"`, `./gradlew --no-daemon spotlessApply check`.

8. **I8 – Fixture ingestion & docs**  
   - Extend fixture loaders to ingest attestation datasets (W3C + synthetic) across core/app/CLI/REST tests.  
     _2025-10-17 – User confirmed Option A: stage failing fixture-ingestion tests now; loader/doc wiring will follow in the subsequent increment._  
     _2025-10-17 – Implemented ingestion helpers (`WebAuthnAttestationFixtures.findById`, `WebAuthnAttestationSamples`, CLI vector summaries, operator UI catalogue) so staged tests now pass across core/application/CLI/REST._  
   - Document the new workflow in how-to guides, update knowledge map/roadmap as needed.  
     _2025-10-17 – Refreshed the CLI how-to guide, roadmap entry, and knowledge map to reference the shared attestation catalogue._  
   - Run `./gradlew --no-daemon spotlessApply check` and targeted suites to close the feature.  
     _2025-10-17 – `./gradlew --no-daemon spotlessApply check` completed successfully (8m14s) after the documentation sync._

9. **I9 – Trust anchor ingestion helpers**  
   - Add failing service/CLI/REST/UI tests ensuring PEM trust-anchor bundles are parsed, cached per session, and applied during verification (including error reporting).  
   - Implement shared parsing utilities, persistence hooks (in-memory only), and warning messages when anchors are absent or invalid.  
  - Update telemetry to flag whether anchors were provided.  
     _2025-10-17 – Implemented `WebAuthnTrustAnchorResolver`, cached anchor telemetry (`anchorMode`), and propagated warnings through CLI/REST/UI layers; updated application/CLI/REST tests now validate the behaviour._  
   - Update telemetry to flag whether anchors were provided.  
   - Commands: targeted module tests (`:application:test`, `:cli:test`, `:rest-api:test`, Selenium suites).

10. **I10 – MDS scaffolding**  
    - Stage failing tests for a metadata ingestion helper that loads offline MDS JSON blobs from `docs/webauthn_attestation/mds/`.  
    - Implement loader utilities and integrate with trust-anchor storage, ensuring deterministic fixtures.  
    - Document manual MDS refresh procedures and record limitations (no live network fetch).  
     _2025-10-17 – Added offline MDS sample dataset and staged failing catalogue test (`WebAuthnMetadataCatalogueTest`), expected red via `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnMetadataCatalogueTest"` until loader implementation lands._  
     _2025-10-17 – Implemented `WebAuthnMetadataCatalogue` loader backed by `SimpleJson`, hydrating `docs/webauthn_attestation/mds/*.json`; `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnMetadataCatalogueTest"` now passes (16.7s)._  
     _2025-10-17 – Curated `docs/webauthn_attestation/mds/curated-mds-v3.json` (packed, U2F, TPM, Android Key) from FIDO MDS v3 production to cover real-world trust anchors while keeping the bundle lightweight._  
     _2025-10-17 – Documented the offline MDS refresh workflow and operator guidance in `docs/2-how-to/refresh-offline-webauthn-metadata.md`; CLI/REST/UI telemetry now exposes `anchorMetadataEntry` and updated `anchorSource` modes._  
     _2025-10-17 – `WebAuthnTrustAnchorResolver` now merges metadata + manual anchors (source telemetry `metadata`, `metadata_manual`, `manual`), with CLI/REST/UI suites refreshed via `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationTest"` and `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest" --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`; outstanding follow-up: document offline MDS refresh flow._  

11. **I11 – Generator test scaffolding**  
    - Add failing tests (core/app/CLI/REST/UI) describing generator outputs: self-signed default, unsigned attestation emission, and custom-root signing fed by inline PEM or file uploads.  
    - Expand OpenAPI snapshots, CLI usage expectations, and Selenium specs to assert new payload shapes and button text.  
    - Stage command failures via `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAttestationGeneratorTest"` (new), `:application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationServiceTest"`, `:rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`, and attestation Selenium suites.  

12. **I12 – Core attestation generator implementation**  
    - Implement generator utilities that accept authenticator key material (JWK/PEM) and emit attestationObject/clientDataJSON/challenge for packed, FIDO-U2F, TPM, and Android Key formats.  
    - Support selectable signing modes (self-signed, unsigned, custom-root) with deterministic outputs and certificate synthesis helpers.  
    - Extend fixture catalogues to expose generator presets and update the knowledge map if new helpers emerge.  

13. **I13 – Application + CLI/REST wiring**  
    - Introduce `WebAuthnAttestationGenerationApplicationService`, parse operator-provided certificates/keys, and emit sanitized telemetry.  
    - Switch CLI `fido2 attest` and REST `/api/v1/webauthn/attest` to call the generation service, returning generated payloads while relocating verification exclusively to Replay.  
    - Refresh OpenAPI docs, CLI help text, and REST/CLI tests to match generation semantics; ensure trust-anchor options behave correctly for custom-root signing.  

14. **I14 – Operator UI Evaluate migration**  
    - Replace Evaluate attestation handlers to call the generator endpoint, render generated payloads with copy/download affordances, and expose mode selection (self-signed / unsigned / custom-root with PEM upload/import).  
    - Update button copy (“Generate attestation”), retire verification-only messaging, and extend Selenium coverage to assert generator outputs (attestationObject/clientDataJSON/challenge, signing mode metadata, certificate chain visibility).  
    - Refresh `rest-api/src/main/resources/templates/ui/fido2/panel.html` and `rest-api/src/main/resources/static/ui/fido2/console.js` so the attestation ceremony reuses the new generator request shape (attestationId, signingMode, credential/attestation key material, optional custom roots) and surfaces the attestation catalogue presets.  
    - Command checklist (T2611):  
      - `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAttestationGeneratorTest"`  
      - `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationServiceTest"`  
      - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`  
      - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`  
    - `./gradlew --no-daemon spotlessApply check`  
  - Sync documentation, roadmap, and session snapshot once the full `./gradlew --no-daemon :rest-api:test` suite (including Selenium) and formatting pipeline complete without failures.  
  - _2025-10-17 – Attestation Evaluate UI now sources presets from `WebAuthnAttestationSamples`, posts to `/api/v1/webauthn/attest`, and renders generated payloads/telemetry; Selenium coverage exercises the new flow (`Fido2OperatorUiSeleniumTest`). `./gradlew --no-daemon :rest-api:test` passed; `spotlessApply check` was executed but the `check` phase still flags pre-existing Checkstyle/PMD issues in core/application/cli modules._  

15. **I15 – Checkstyle/PMD remediation**  
    - Run `./gradlew --no-daemon checkstyleMain pmdMain` (and the full `spotlessApply check`) to list current violations.  
    - Update offending source/tests so they comply with existing rule sets (Option A decision); avoid configuration changes unless separately approved.  
    - Re-run the full pipeline to confirm the build returns to green.  
    - Capture resulting adjustments in this plan, the tasks checklist, and `_current-session.md`.  
    - _2025-10-17 – Whitespace corrections + CLI cleanup applied; `./gradlew --no-daemon spotlessApply check` now passes (runtime ≈7m46s)._

16. **I16 – Attestation result panel trim**  
    - Update the operator UI attestation result card to remove redundant Attestation ID / Format / Signing mode rows and duplicate status text, relying on the header badge + input summary.  
    - Remove the expected challenge excerpt and telemetry hint from the result panel to keep the card payload-focused.  
    - Adjust JavaScript rendering helpers and Selenium coverage to focus on core payload fields.  
    - Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"` and `./gradlew --no-daemon spotlessApply check`.  
    - _2025-10-17 – Result panel now displays only status, certificate count, and payloads; expected challenge/telemetry hints removed, Selenium updated, full pipeline rerun._  

17. **I17 – Attestation response payload reduction**  
    - Update application DTOs and transport models so generated attestation responses expose only `clientDataJSON` and `attestationObject`.  
    - Remove `expectedChallenge`, raw certificate PEM arrays, and `signatureIncluded` flags from CLI/REST outputs while keeping certificate/signature statistics in telemetry metadata.  
    - Refresh OpenAPI snapshots, CLI expectations, Selenium assertions, and operator UI rendering to align with the slimmer payload.  
    - Commands: `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`, `./gradlew --no-daemon spotlessApply check`.  
    - _2025-10-18 – Completed Option B: trimmed generation payloads across application/CLI/REST/UI, added telemetry-based summaries, regenerated OpenAPI snapshots, and reran targeted suites + full quality gate._

## Risks & Mitigations
- **Complex format coverage** – TPM/Android Key attestations involve certificate handling; rely on deterministic fixtures and synthetic certificate chains.  
- **UI complexity** – Toggle must not regress assertion flows; isolate attestation form logic to maintain readability.  
- **Fixture entropy** – Large attestation blobs may trigger gitleaks; reuse or extend the existing allowlist with documented rationale.

## Upcoming Increments – Manual Mode (Blocked pending clarifications)
1. I18 – Credential ID alignment
   - Extract credential IDs from fixture attestation objects and encode them as Base64URL for generated payloads (PRESET path).
   - Adjust CLI/REST/UI DTOs, tests, and documentation snapshots to assert `id`/`rawId` output in Base64URL format while keeping `attestationId` for fixture references.
   - Commands: `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAttestationGeneratorTest"`, `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationService*"`; `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationTest"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestation*"`; `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon spotlessApply check`.
   - _2025-10-18 – Completed: Base64URL credential IDs now surface in PRESET responses across core/application/REST/UI layers; tests, OpenAPI snapshots, and docs refreshed._ 
2. I19 – Core Manual input source (tests first)
   - Add `inputSource` to generator commands with a `MANUAL` path that does not require `attestationId`.
   - Stage failing tests for UNSIGNED, SELF_SIGNED, CUSTOM_ROOT across packed/fido-u2f/tpm/android-key.
   - Implement server-generated random Base64URL credential IDs for Manual runs with optional override (Base64URL validation, blank → random).
   - Blocked by: Q1, Q2, Q5.
3. I20 – Application service wiring
   - Accept `inputSource` (default PRESET) and pass-through for MANUAL; add telemetry fields `inputSource`, optional `seedPresetId`, and `overrides` set.
   - Blocked by: Q1, Q5.
4. I21 – REST DTO/service/controller
   - Extend request with `inputSource` (PRESET|MANUAL) and conditional validation (attestationId required only for PRESET).
   - Reshape generation response to align with WebAuthn assertions (`type`/`id`/`rawId` + nested `response` containing only `clientDataJSON` and `attestationObject`); update OpenAPI snapshots and endpoint tests.
   - Blocked by: Q1, Q2, Q5.
5. I22 – UI auto-switch to Manual
   - Detect overrides (challenge, RP ID, origin, credential/attestation keys, serial) and flip to Manual; send `inputSource=MANUAL` and omit `attestationId`.
   - Render attestation results using the nested response object shape (clientDataJSON + attestationObject only) while surfacing signature/certificate statistics from telemetry.
   - Owner declined the “Copy preset ID” link; no additional affordance required.
   - Blocked by: Q1.
6. I23 – CLI parity (if approved)
   - Add `--input-source=manual` and required inputs mirroring REST; update help and tests.
   - Blocked by: Q4, Q5.

## Telemetry & Observability
- Add `fido2.attest` and `fido2.attestReplay` adapters capturing attestation format, RP ID, authenticator AAGUID, certificate SHA-256 fingerprint, anchor source (self-attested vs provided), and validity status.  
- Ensure CLI/REST/UI outputs surface telemetry IDs and anchor provenance without exposing raw attestation statements, certificates, or private keys.
