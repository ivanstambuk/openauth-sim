# Feature Plan 026 – FIDO2/WebAuthn Attestation Support

_Linked specification:_ `docs/4-architecture/specs/feature-026-fido2-attestation-support.md`  
_Status:_ Complete  
_Last updated:_ 2025-10-28

## Vision & Success Criteria
- Provide end-to-end attestation generation and verification across core, application services, CLI, REST API, and operator UI, mirroring the existing assertion workflow.
- Cover the packed, FIDO-U2F, TPM, and Android Key attestation formats with deterministic fixtures (W3C Level 3 + synthetic bundle).
- Maintain sanitized telemetry (`fido2.attest`, `fido2.attestReplay`) and redaction rules equivalent to assertion flows.
- Introduce Preset, Manual, and Stored attestation flows across all facades, backed by the shared MapDB credential store and a curated seeding control (Evaluate retains all three modes; Replay now offers only Manual and Stored selectors).
- Allow operators to supply optional trust anchor bundles (UI text area, CLI/REST inputs) while defaulting to self-attested acceptance and laying groundwork for WebAuthn MDS ingestion.
- Ensure `./gradlew spotlessApply check` plus targeted suites (`:core:test`, `:application:test`, `:cli:test`, `:rest-api:test`) remain green after each increment.
- Align operator UI labels with the accepted input formats (JWK or PEM/PKCS#8) now that Base64URL private keys are no longer supported.

## Scope Alignment
- In scope: attestation generation & replay helpers, telemetry, CLI/REST endpoints, UI toggles/forms, fixture ingestion, trust-anchor configuration (inline uploads), documentation updates, initial MDS ingestion scaffolding, and stored attestation credential workflows (seeding + reuse through MapDB).
- Evaluate flows across CLI/REST/operator UI must generate attestation payloads (attestationObject, clientDataJSON, challenge) while Replay owns verification scenarios; all facades must also resolve Stored attestation credentials via the shared `CredentialStore`.
- Out of scope: credential export/import tooling beyond curated seeds, attestation formats beyond the four specified types, hardware-backed certificate chain validation beyond deterministic fixtures.

## Dependencies & Interfaces
- Builds on existing FIDO2 assertion utilities (COSE parsing, JWK conversion).
- Stored workflows reuse the shared `CredentialStore` (MapDB) and `WebAuthn` persistence adapters; ensure schema extensions capture attestation metadata/private keys without breaking existing assertion records.
- Stores attestation fixtures under `docs/webauthn_attestation/` with per-format JSON files while reusing shared loader patterns.
- Operator UI adjustments hinge on the current Evaluate/Replay tab layout; ensure toggle logic integrates with existing state management.
- Telemetry contracts must extend `TelemetryContracts` without breaking existing assertion events.
- Trust anchor handling must accept inline PEM bundles and prepare for optional MDS-sourced metadata without introducing external network calls.

## Increment Breakdown (≤30 min each)
_2025-10-19 – T2628 closed: fixture key material now ships as structured JWK objects, the core loader derives canonical scalars from the JWK `d` field, CLI/REST/UI inputs reject legacy Base64-only keys, and `./gradlew spotlessApply check` verified the change. Manual-mode increments (T2618–T2622) remain next up._
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
18. **I18 – Remove attestationId from user-facing responses**  
    - Update REST/OpenAPI DTOs, CLI output, and operator UI JSON rendering to omit `attestationId` while leaving telemetry and internal trust-anchor plumbing unchanged.  
    - Ensure replay metadata no longer echoes `attestationId`; retain the identifier solely inside telemetry fields.  
    - Refresh MockMvc/Selenium coverage, documentation snapshots, and CLI expectations accordingly.  
    - Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationTest"`, `./gradlew --no-daemon spotlessApply check`.

19. **I25 – Assertion payload sanitization**  
    - Trim generated assertion JSON (CLI/REST/UI) to the spec-compliant PublicKeyCredential shape (`type`, `id`, `rawId`, `response`).  
    - Continue surfacing relying-party metadata, algorithm, UV requirements, and counters via telemetry/metadata sections, not inside the main credential payload.  
    - Update MockMvc and CLI tests plus OpenAPI snapshots to assert the lean payload, and document the contract change where applicable.  
    - Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2EvaluationEndpointTest"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliTest"`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon spotlessApply check`.

## Risks & Mitigations
- **Complex format coverage** – TPM/Android Key attestations involve certificate handling; rely on deterministic fixtures and synthetic certificate chains.  
- **UI complexity** – Toggle must not regress assertion flows; isolate attestation form logic to maintain readability.  
- **Fixture entropy** – Large attestation blobs may trigger gitleaks; reuse or extend the existing allowlist with documented rationale.

## Upcoming Increments – Manual Mode (Blocked pending clarifications)
1. I19 – Credential ID alignment
   - Extract credential IDs from fixture attestation objects and encode them as Base64URL for generated payloads (PRESET path).
   - Adjust CLI/REST/UI DTOs, tests, and documentation snapshots to assert `id`/`rawId` output in Base64URL format while preserving preset tracing via telemetry instead of user-facing fields.
   - Commands: `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAttestationGeneratorTest"`, `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationService*"`; `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationTest"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestation*"`; `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon spotlessApply check`.
   - _2025-10-18 – Completed: Base64URL credential IDs now surface in PRESET responses across core/application/REST/UI layers; tests, OpenAPI snapshots, and docs refreshed._ 
2. I20 – Core Manual input source (tests first)
   - Add `inputSource` to generator commands with a `MANUAL` path that does not require `attestationId`.
   - Stage failing tests for UNSIGNED, SELF_SIGNED, CUSTOM_ROOT across packed/fido-u2f/tpm/android-key.
     - _2025-10-19 – Parameterized coverage added in `WebAuthnAttestationGeneratorTest`, asserting unsigned attStmt sanitization and successful verification for self-signed/custom-root manual flows. Command: `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAttestationGeneratorTest"` (currently red pending implementation)._
   - Implement server-generated random Base64URL credential IDs for Manual runs with optional override (Base64URL validation, blank → random).
     - _2025-10-19 – Manual generation implemented: generator now derives EC key material from JWK/PEM scalars, rebuilds authenticator data, COSE public keys, and emits attestation statements (including TPM `certInfo` regeneration). Self-signed, custom-root, and unsigned paths exercised by `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAttestationGeneratorTest"` (green)._ 
   - Blocked by: Q1, Q2, Q5.
3. I21 – Application service wiring
   - Accept `inputSource` (default PRESET) and pass-through for MANUAL; add telemetry fields `inputSource`, optional `seedPresetId`, and `overrides` set.
     - _2025-10-19 – `GenerationCommand` now exposes `inputSource`; inline commands default to PRESET, manual commands force MANUAL, and telemetry enrichment covers relying-party, AAGUID, and certificate fingerprint fields._
   - Remaining: propagate `inputSource` through REST/CLI DTOs and manual override detection so callers can select MANUAL without hand-crafting commands.
4. I22 – REST DTO/service/controller
   - Extend request with `inputSource` (PRESET|MANUAL) and conditional validation (attestationId required only for PRESET).
   - Reshape generation response to align with WebAuthn assertions (`type`/`id`/`rawId` + nested `response` containing only `clientDataJSON` and `attestationObject`); update OpenAPI snapshots and endpoint tests.
   - Blocked by: Q1, Q2, Q5.
   - _2025-10-20 – Completed: enforced `input_source_invalid` validation, expanded attestation endpoint coverage, and reran targeted REST + spotless checks._
5. I23 – UI auto-switch to Manual
   - Detect overrides (challenge, RP ID, origin, credential/attestation keys, serial) and flip to Manual; send `inputSource=MANUAL` and omit `attestationId`.
   - Render attestation results using the nested response object shape (clientDataJSON + attestationObject only) while surfacing signature/certificate statistics from telemetry.
   - Owner declined the “Copy preset ID” link; no additional affordance required.
   - Blocked by: Q1.
   - _2025-10-20 – Completed: operator console detects preset overrides, surfaces a live Manual/Preset hint, keeps Manual calls free of `attestationId`, and Selenium captures the mode toggling behaviour (T2621)._
   - _2025-10-20 – Follow-up: increase default height of credential/attestation private-key text areas so EC JWK payloads show without scrollbars._
6. I24 – CLI parity (Manual source)
   - Stage failing Picocli tests for Manual generation (one green-path run + validation errors for missing manual required fields and unsupported input-source values).
   - Parse `--input-source` in `Fido2Cli.AttestCommand`, defaulting to PRESET; when Manual is requested, bypass preset lookups, enforce manual-required options, and propagate overrides metadata to telemetry.
   - Refresh `fido2` help output and operator documentation (`docs/2-how-to/use-fido2-cli-operations.md`) to describe Manual mode inputs.
   - Commands: `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationTest"` (expect red until implementation), `./gradlew --no-daemon :cli:test`, `./gradlew --no-daemon spotlessApply check`.
   - _2025-10-20 – Completed: Added Manual-mode CLI tests (success + missing-key validation), wired `--input-source`, `--seed-preset-id`, and `--override` handling into `Fido2Cli`, refreshed the how-to guide, and verified `./gradlew --no-daemon :cli:test`. Full `./gradlew --no-daemon spotlessApply check` now passes after extending Manual-mode coverage._
7. I25 – Attestation preset label harmonisation
   - Update attestation vector labels exposed to the operator console to follow the `<algorithm> (format, W3C <section>)` pattern, with an origin fallback when no W3C section is defined.
   - Extend controller/UI tests to assert the new label formatting so regressions surface quickly.
   - Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.OperatorConsoleControllerAttestationTest"` and targeted Selenium runs as needed, followed by `./gradlew --no-daemon spotlessApply check`.

8. **I26 – Credential store attestation schema** _(completed 2025-10-20)_  
   - Stage failing persistence/application tests asserting MapDB can persist and retrieve attestation credentials (leaf certificate chain, signing mode, attestation private key linkage) alongside assertions.  
   - Extend `WebAuthnCredentialPersistenceAdapter` (or equivalent) to serialise attestation descriptors, update schema enums, and document migration notes.  
   - _Outcome:_ Persistence adapter now emits `WebAuthnAttestationCredentialDescriptor` records with certificate chains/private keys; tests in core, infra, and application verify the round-trip.  
   - Commands: `./gradlew --no-daemon :infra-persistence:test --tests "io.openauth.sim.infra.persistence.webauthn.WebAuthnCredentialStoreAttestationTest"`, `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnCredentialPersistenceSmokeTest"`, `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAttestationPersistenceAdapterTest"`, `./gradlew --no-daemon spotlessApply check`.  

9. **I27 – Stored attestation application services** _(completed 2025-10-20)_  
   - Add failing application tests ensuring stored credentials resolve via generation commands (`inputSource=STORED`), telemetry records credential handles, and replay flows can fetch stored attestations when requested.  
   - Implement service wiring that looks up MapDB entries, reuses manual/preset validation, and surfaces meaningful errors when a stored credential is missing.  
   - _Outcome:_ Application services now load stored attestation descriptors from MapDB, regenerate attestation payloads, reuse stored certificate chains, and enrich telemetry with `storedCredentialId`; replay flows hydrate stored attestation objects/clientData and verify them through the existing support helper.  
   - Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationGenerationStoredTest" --tests "io.openauth.sim.application.fido2.WebAuthnAttestationReplayStoredTest"`, `./gradlew --no-daemon :application:test`.  

10. **I28 – CLI/REST stored flow support**  
    - Stage failing Picocli + MockMvc tests covering `inputSource=stored`, including credential selection validation, not-found errors, and telemetry propagation.  
      _2025-10-20 – Staged `Fido2CliAttestationStoredTest` and `Fido2AttestationStoredEndpointTest`; both suites currently red, exercising stored success paths, missing identifier validation, and not-found handling to keep telemetry expectations (`inputSource=stored`, `storedCredentialId`) front-loaded._  
    - Update CLI flags/help (`--input-source=stored`, `--credential-id`, stored credential selector) and REST request DTO validation (`credentialId` + Base64URL `challenge`, optional RP/origin overrides), ensuring stored mode skips manual-only requirements and loads MapDB entries with descriptive errors when metadata is absent.  
      _2025-10-20 – CLI stored attestation generation now opens the configured store, hydrates descriptors via the persistence adapter, and emits deterministic payloads/telemetry; REST controller/service accept `inputSource=STORED`, validate credential/challenge fields, and surface consistent reason codes. Targeted suites pass via `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationStoredTest"` and `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationStoredEndpointTest"`._  
    - Commands: `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationStoredTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationStoredEndpointTest"`, plus `./gradlew --no-daemon spotlessApply check`.  

11. **I29 – Operator UI stored selector**  
    - Add failing Selenium + controller tests asserting the attestation Evaluate tab renders Preset/Manual/Stored radio buttons, lists stored credentials, and hides manual inputs when Stored is selected.  
      _2025-10-20 – Staged `Fido2OperatorUiSeleniumTest.attestationStoredModeRendersStoredForm` alongside `Fido2AttestationStoredOperatorControllerTest`; both fail pending UI/controller implementation, covering stored credential lists, manual-panel hiding, and operator API metadata responses._  
    - Implement UI state management to fetch stored credential summaries, submit `inputSource=STORED`, and display retrieval errors inline without breaking Manual/Preset flows.  
      _2025-10-21 – Operator console now preloads stored attestation selectors, wiring REST metadata/seed endpoints and toggling inline/manual panels; Selenium suite covers stored mode, seeding feedback, and replay flows once more. Tests: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest" --tests "io.openauth.sim.rest.Fido2AttestationStoredOperatorControllerTest"`._  
    - Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationStored*"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationStoredOperatorControllerTest"`, and `./gradlew --no-daemon spotlessApply check`.  

12. **I30 – Seed stored attestation credentials**  
    - Stage failing tests for a seeding application service, CLI command, REST endpoint, and operator UI control that populate curated attestation credentials into MapDB; assert idempotent runs and telemetry output.  
      _2025-10-20 – Staged `WebAuthnAttestationSeedServiceTest`, `Fido2CliAttestationSeedTest`, `Fido2AttestationSeedEndpointTest`, and the Selenium scenario `attestationSeedPopulatesStoredCredentials`; all suites currently red, covering curated seed data, CLI/REST affordances, and operator UI feedback._  
    - Implement the seed helper reusing fixture data, expose it via CLI (`seed-attestations`), REST (`POST /api/v1/webauthn/attestations/seed`), and UI controls, then document operator guidance + knowledge map updates.  
      _2025-10-21 – Seed service persists canonical attestation payloads (challenge/clientData/attestationObject), CLI command surfaces idempotent output, REST endpoint returns deterministic metadata, and operator UI seeding integrates stored-mode selectors; full stack verified via `./gradlew --no-daemon :application:test :cli:test :rest-api:test` and `spotlessApply check`._  
    - Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationSeedServiceTest"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationSeedTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationSeedEndpointTest" --tests "io.openauth.sim.rest.Fido2AttestationStoredEndpointTest" --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`, Selenium stored seed spec, and `./gradlew --no-daemon spotlessApply check`.  

_2025-10-26 – Scope reopened to deliver stored attestation replay affordances across all facades and remove the attestation identifier text input per owner directive._

31. **I31 – Specification refresh for stored replay**  
    - Update the specification clarifications, stored workflow requirements, and UI adjustments to adopt stored attestation replay (Option B) and remove the attestation identifier input.  
      _2025-10-26 – Spec updated to capture stored replay across CLI/REST/UI, telemetry expectations, and UI field removal._  
    - Commands: Documentation-only change (no builds required).  

32. **I32 – Stored replay test scaffolding**  
    - Add failing tests for stored attestation replay across layers: application (`WebAuthnAttestationReplayStoredTest` extensions), CLI (`Fido2CliAttestationReplayStoredTest`), REST (`Fido2AttestationReplayStoredEndpointTest`), and Selenium coverage validating Manual/Stored toggles plus absence of the attestation identifier field.  
    - Capture new OpenAPI expectations for `inputSource=STORED` replay requests without serialising manual attestation IDs.  
    - Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationReplayStoredTest"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationReplayStoredTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationReplayStoredEndpointTest" --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplayStoredMode"`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"` (expected red).  

33. **I33 – Stored replay implementation**  
    - Implement stored replay plumbing end-to-end:  
      - CLI `attest-replay` command accepts `--input-source=stored` with `--credential-id`, resolves stored descriptors through `WebAuthnAttestationReplayApplicationService.ReplayCommand.Stored`, and reuses existing trust-anchor parsing for inline submissions.  
      - REST DTO adds `inputSource`, `credentialId`, and stored-mode validation; `WebAuthnAttestationService.replay` branches to build the stored replay command, letting the application layer hydrate attestation payloads/metadata while preserving telemetry (`inputSource`, `storedCredentialId`).  
      - OpenAPI snapshots document the expanded schema (enumerated input sources, stored credential fields, and absence of inline-only requirements when `inputSource=STORED`).  
    - Drive I32 tests to green (`Fido2CliAttestationReplayStoredTest`, `Fido2AttestationReplayStoredEndpointTest`, Selenium stored replay scenario, application stored replay coverage) and refresh OpenAPI snapshots if structure changes.  
    - Commands: Targeted module suites plus `./gradlew --no-daemon spotlessApply check`.  

34. **I34 – Operator UI stored replay & identifier removal**  
    - Update Thymeleaf templates and console JS to expose stored replay mode, hydrate fields from `/api/v1/webauthn/attestations/{id}`, and remove the attestation identifier text input.  
    - Extend Selenium specs to assert stored replay submissions, challenge validation, trust-anchor handling, and the absence of the identifier field across evaluate/replay views.  
    - _2025-10-27 - Updated Selenium stored replay coverage to interact with the stored replay submit control and wait for metadata hydration, confirming read-only RP/origin/challenge/format fields populate before submitting._  
    - Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplayStoredMode" --tests "io.openauth.sim.rest.Fido2AttestationStoredOperatorControllerTest"` and `./gradlew --no-daemon spotlessApply check`.  

35. **I35 – Documentation & regression sweep**  
    - Sync how-to guides, knowledge map, roadmap, and tasks with the stored replay feature; document telemetry updates and operator instructions.  
    - Run full verification (`./gradlew --no-daemon :application:test :cli:test :rest-api:test spotlessApply check`) and capture lessons learned in the feature plan/tasks.  
    - _2025-10-27 - Documentation refreshed across CLI/REST/operator UI guides, knowledge map, roadmap, and tasks; full regression command succeeded after OpenAPI snapshot regeneration and Checkstyle tidy-up._  

36. **I36 – Attestation replay inline sample loader parity**  
    - Add failing Selenium coverage asserting the attestation replay inline form renders the “Load a sample vector” selector, hydrates inline fields from curated fixtures, and leaves Stored mode untouched.  
    - Reintroduce the inline preset dropdown in `panel.html` and associated handlers in `console.js`, sourcing attestation vectors via the existing fixture index without reinstating the Preset radio mode.  
    - Once wiring is complete, refresh the new Selenium scenario plus `./gradlew --no-daemon :rest-api:test` and full spotless/check to keep regressions green.  
    - _2025-10-27 – Selenium parity test added, inline preset loader reinstated for attestation replay, attestation vector metadata extended, and full Gradle verification rerun._  

37. **I37 – Stored replay payload display (UI Option A)**  
    - Stage failing Selenium/UI expectations requiring read-only `attestationObject` and `clientDataJSON` fields to render in stored replay mode, matching the manual form layout.  
    - Update Thymeleaf templates to render the payload text areas in stored replay, keeping them read-only and visually aligned with the manual form labels.  
    - _2025-10-27 – Added `attestationReplayStoredModeDisplaysPersistedPayloads` Selenium coverage, rendered read-only payload text areas in `panel.html`, and hydrated them via console metadata helpers; targeted + full Gradle suites now green._  
    - Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplayStoredModeDisplaysPersistedPayloads"`, `./gradlew --no-daemon :application:test :cli:test :rest-api:test spotlessApply check`.  

38. **I38 – Stored replay challenge hydration fix**  
    - Add failing application + Selenium coverage asserting the stored replay challenge auto-populates from MapDB seed metadata, and adjust the console JS metadata wiring to prioritise stored attributes.  
    - Confirm CLI/REST stored replay flows continue to emit the expected telemetry fields after the hydration fix and refresh OpenAPI snapshots if schemas change.  
    - _2025-10-27 – Verified MapDB-backed challenge hydration through Selenium/application tests and updated console metadata flow to clear + repopulate challenge/client payloads on stored selection; regression suite remains green._  
    - Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationReplayStoredTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplayStoredModeDisplaysPersistedPayloads"`, `./gradlew --no-daemon :application:test :cli:test :rest-api:test spotlessApply check`.  

39. **I39 – Stored attestation label normalization**  
    - Add failing tests asserting operator dropdown labels follow the assertion-style convention (`ES256 (W3C 16.1.6)`) even when credential IDs retain `w3c-*` prefixes.  
    - Persist `fido2.metadata.label` during attestation seeding and normalize console rendering to fall back to curated labels when metadata is unavailable.  
    - Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationSeedServiceTest"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationSeedTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplayStoredLabels" --tests "io.openauth.sim.rest.VerboseTracePayloadTest"`, `./gradlew --no-daemon jacocoAggregatedReport`, `./gradlew --no-daemon :application:test :cli:test :rest-api:test spotlessApply check`.  
    - _2025-10-28 – Implemented label normalization across application/CLI/REST/UI seeding paths, updated console formatting to prefer curated metadata, and added REST trace payload coverage so Jacoco branch ratios remain above the 70% threshold._  

40. **I40 – Unified stored credential seeding**  
    - Update the assertion and attestation seeding flows so they share a single stored credential per fixture, enriching existing MapDB records with attestation attributes instead of persisting duplicates.  
    - Document the new behaviour (spec/plan/how-to) and stage failing tests that assert: (a) attestation seeding updates an existing assertion credential, (b) only one entry appears in credential listings/dropdowns, and (c) attestation replay still hydrates payloads from the unified record.  
    - Commands (expected once implementation proceeds): `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationSeedServiceTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplayStoredMode"`, plus full `spotlessApply check`.  
    - _2025-10-28 – Failing coverage staged: `WebAuthnAttestationSeedServiceTest.updatesExistingAssertionCredential` validates in-place enrichment, and `Fido2AttestationSeedEndpointTest.updatesCanonicalAssertionCredentials` guards against duplicate REST entries; both currently red pending seeder consolidation._  

41. **I41 – Stored replay read-only styling parity**  
    - Extend the operator console stylesheet so read-only textarea fields (stored challenge, attestationObject, clientDataJSON) share the muted, non-interactive treatment applied to other locked inputs, preventing them from appearing editable.  
    - Verification: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplayStoredModeDisplaysPersistedPayloads"` and full `./gradlew --no-daemon spotlessApply check`.  
    - _2025-10-28 – Updated `console.css` so stored replay textareas inherit the read-only palette, confirmed the UI diff locally, and reran the targeted Selenium scenario plus full `spotlessApply check`._  

41. **I41 – Synthetic PS256 packed attestation fixture**  
    - Clarify specification and docs so the synthetic PS256 packed fixture is part of the curated seed catalogue (see 2025-10-28 clarification).  
    - Stage failing tests across core (`WebAuthnAttestationFixturesTest`), application (`WebAuthnAttestationSeedServiceTest`), CLI (`Fido2CliAttestationSeedTest`), and REST (`Fido2AttestationSeedEndpointTest`, Selenium stored attestation checks) that expect PS256 stored metadata to hydrate automatically.  
    - Author the synthetic packed PS256 attestation entry under `docs/webauthn_attestation/packed.json`, ensuring deterministic challenge/clientData/attestationObject and RSA-PSS key material are provided.  
    - Update seeding metadata and UI label mappings so the PS256 credential surfaces with a populated challenge, then rerun targeted suites followed by `./gradlew --no-daemon :application:test :cli:test :rest-api:test spotlessApply check`.  
    - Refresh operator/REST/CLI how-to guidance once tests pass so users know PS256 stored attestations seed automatically.
    - _2025-10-28 – Added `synthetic-packed-ps256` with deterministic RSA-PSS fixtures, updated seeders to fall back to generator presets when W3C vectors are unavailable, expanded cross-module tests to cover the PS256 preset, and refreshed CLI/REST/UI guidance. Final verification: `./gradlew --no-daemon :application:test :cli:test :rest-api:test spotlessApply check`._

42. **I42 – Stored replay dropdown stabilisation**  
    - Add an explicit wait in Selenium coverage so stored attestation dropdowns confirm the target credential option is present before invoking `selectOptionByValue`, preventing `IllegalStateException` when MapDB hydration lags.  
    - Reuse the existing `waitForOption` helper to block on option availability, keeping the implementation change confined to the UI test layer.  
    - Verification: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplayStoredModeDisplaysPersistedPayloads"` followed by full `./gradlew --no-daemon spotlessApply check`.  
    - _2025-10-28 – Implemented the wait using the shared helper, reran the targeted Selenium scenario, and revalidated the full spotless/check pipeline._  

43. **I43 – Curated trust anchor selector**  
    - CLI now accepts repeatable `--metadata-anchor` flags, forwarding the identifier list to the resolver and surfacing it in telemetry (`metadataAnchorIds` + `anchorMetadataEntry`); the staged Picocli test covers success output.  
    - REST replay requests accept `metadataAnchorIds` arrays, wiring the values into resolver calls and response metadata; controller unit test verifies the payload is forwarded and the response serialises the identifier list.  
    - Operator console manual replay includes a format-filtered multi-select with selection summary, posting only metadata entry ids while keeping the PEM textarea for custom anchors; Selenium coverage confirms rendering and filtering.  
    - Sample vector loads now leave curated anchors unselected by default while flagging recommended entries in the summary so inline verification still exercises the self-attested baseline unless operators opt in.  
    - Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnTrustAnchorResolverMetadataTest"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliAttestationReplayMetadataAnchorsTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.webauthn.Fido2AttestationReplayMetadataAnchorTest" --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplayManualMetadataAnchors" --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationReplayInlineSampleLoadsPreset"`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon :application:test :cli:test :rest-api:test spotlessApply check`.  

## Analysis Gate (2025-10-20)
- **Specification completeness** – Updated 2025-10-27 to capture Preset/Manual/Stored evaluate inputs (with replay simplified to Manual/Stored), MapDB-backed persistence, and curated seeding controls; clarifications document the owner’s Option B selections and follow-on directives.
- **Open questions review** – No open entries remain for Feature 026 in `open-questions.md`.
- **Plan alignment** – Stored-mode increments (I26–I30) now implemented across application, CLI, REST, and operator UI; dependencies and success criteria mirror the revised specification.
- **Tasks coverage** – Tasks checklist reflects completed stored-mode work (T2633–T2635) with verification commands recorded.
- **Constitution compliance** – Spec-first, clarification gate, and test-first expectations remain in force; newly added increments continue the ≤30-minute cadence with failing tests staged ahead of code.
- **Tooling readiness** – Existing commands remain valid; add targeted `:infra-persistence:test`, stored-mode CLI/REST tests, and Selenium stored-mode specs to the baseline before coding.
- **Outcome** – Gate reopened for the stored-mode scope; proceed with I26–I30 only after tasks/spec/plan stay in sync and failing tests are staged.

## Analysis Gate (2025-10-21)
- **Specification completeness** – PASS; spec dated 2025-10-20 still current after stored-mode delivery, clarifications unchanged.
- **Open questions review** – PASS; `docs/4-architecture/open-questions.md` contains no Feature 026 entries.
- **Plan alignment** – PASS; plan references spec/tasks and now marks I26–I30 complete with MapDB stored flows captured.
- **Tasks coverage** – PASS; tasks checklist updated through T2635 with documented test commands ahead of implementation.
- **Constitution compliance** – PASS; increments maintained spec-first/test-first cadence and reused shared helpers to keep control flow flat.
- **Tooling readiness** – PASS; verification commands (`./gradlew --no-daemon :application:test :cli:test :rest-api:test spotlessApply check`) captured under Last Green Commands on 2025-10-21.
- **Outcome** – All checklist items satisfied and recorded; Feature 026 authorised for merge pending final diff review.

## Analysis Gate (2025-10-26)
- **Specification completeness** – PASS; spec updated 2025-10-26 to include stored attestation replay and UI field removal directives.
- **Open questions review** – PASS; no new entries required in `open-questions.md`.
- **Plan alignment** – PASS; I31 logged as complete and I32–I35 define the reopened increments for stored replay implementation.
- **Tasks coverage** – PENDING; tasks T2636–T2639 added to mirror the new increments. Failing coverage for T2636 (CLI, REST, Selenium, OpenAPI expectations) landed 2025-10-26 and remains red by design until stored replay wiring ships.
- **Constitution compliance** – PENDING; proceed only after T2636 establishes red tests per test-first guardrail.
- **Tooling readiness** – UPDATED; commands for new increments captured alongside OpenAPI snapshot regeneration and Selenium suites.
- **Outcome** – Hold implementation until T2636 failing tests are committed; resume once the new scaffolding is in place.

## Telemetry & Observability
- Add `fido2.attest` and `fido2.attestReplay` adapters capturing attestation format, RP ID, authenticator AAGUID, certificate SHA-256 fingerprint, anchor source (self-attested vs provided), and validity status.  
- Ensure CLI/REST/UI outputs surface telemetry IDs and anchor provenance without exposing raw attestation statements, certificates, or private keys.
- Record the attestation input source (Preset, Manual, Stored), stored credential handles, and seeded preset identifiers in telemetry frames so cross-facade analytics stay aligned.
