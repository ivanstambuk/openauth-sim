# Refresh Offline WebAuthn Metadata (MDS)

_Status: Draft_  
_Last updated: 2025-10-17_

The simulator ships a deterministic subset of the FIDO Metadata Service (MDS) under
``docs/webauthn_attestation/mds`/`. Each JSON file represents a catalogue bundle that can be
refreshed or extended without contacting the live MDS service. This guide explains how to add new
entries, validate them, and propagate the changes across the CLI, REST API, and operator console so
trust anchors stay aligned.

## Directory Layout

- ``docs/webauthn_attestation/mds`/` – Offline bundles split per use-case. `offline-sample.json`
  covers the W3C packed attestation vectors and their issuing certificates, while
  `curated-mds-v3.json` carries the hand-picked FIDO MDS v3 production entries (Ledger packed,
  YubiKey U2F, WinMagic TPM, IDmelon Android Key).
- ``docs/webauthn_attestation`/*.json` – Attestation payload fixtures referenced by CLI/REST/UI
  tooling (`vector_id` values referenced by the metadata catalogue).
- application/src/main/java/.../WebAuthnMetadataCatalogue.java – Loader that hydrates the offline
  bundles.
- application/src/main/java/.../WebAuthnTrustAnchorResolver.java – Resolver that merges metadata
  anchors with operator-provided PEM inputs and emits telemetry suitable for downstream facades.

## Workflow Overview

1. **Prepare the metadata JSON** – Start from the existing JSON schema. Each entry requires:
   - `entry_id` – unique identifier (`mds-<slug>` recommended).
   - `aaguid` – lowercase hex (no separators) or canonical UUID string.
   - `attestation_format` – one of `packed`, `fido-u2f`, `tpm`, `android-key`.
   - `description` – human-readable summary for operators.
   - `sources` – at least one `{ "type": "fixture", "vector_id": "<attestation vector id>" }`.
   - `attestation_root_certificates` – list of rooted anchors (`label`, `fingerprint_sha256`,
     `certificate_pem`).

2. **Edit the bundle** – Add or update entries in ``docs/webauthn_attestation/mds`/<file>.json`.
   Keep certificate PEM blocks compact (64-character wrapping) and ensure fingerprints are lowercase
   SHA-256 digests with no separators.

3. **Run targeted checks** – Confirm the catalogue loads and the resolver surfaces metadata:
   ```bash
   ./gradlew --no-daemon :application:test \
     --tests "io.openauth.sim.application.fido2.WebAuthnMetadataCatalogueTest" \
     --tests "io.openauth.sim.application.fido2.WebAuthnTrustAnchorResolverTest"

   ./gradlew --no-daemon :cli:test \
     --tests "io.openauth.sim.cli.Fido2CliAttestationTest"

   ./gradlew --no-daemon :rest-api:test \
     --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest" \
     --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"
   ```
   The CLI and REST suites confirm that telemetry fields (`anchorSource`, `anchorMode`,
   `anchorMetadataEntry`) remain synchronized across facades.

4. **Review OpenAPI docs** – When new telemetry properties or metadata fields surface, regenerate
   API documentation if needed. For simple catalogue refreshes (new entries, updated certificates)
   no regeneration is required, but ensure `docs/3-reference/rest-openapi.(yaml|json)` stays current
   if you touched metadata fields.

5. **Update the roadmap & tasks** – Note the refresh in the relevant feature plan/task checklist so
   future sessions understand which anchors ship offline.

## Operator Guidance

- **CLI** – Run `./gradlew --quiet :cli:run --args='fido2 vectors'` to list the attestation catalogue
  and confirm the new `vector_id` identifiers. Attestation commands (`fido2 attest`,
  `fido2 attest-replay`) now surface metadata-backed anchors via telemetry
  (`anchorSource=metadata` or `anchorSource=metadata_manual`).

- **REST API** – The `POST /api/v1/webauthn/attest` and
  `POST /api/v1/webauthn/attest/replay` endpoints embed the new metadata in the response payload.
  Check the `metadata.anchorMetadataEntry` and `metadata.anchorSource` fields for the expected
  catalogue entry ID.

- **Operator Console** – The Attestation panel automatically references the refreshed catalogue.
  The result card displays the anchor source (“Metadata” or “Metadata + Manual”) and highlights any
  resolver warnings. To refresh the UI without restarting the simulator, redeploy the REST API
  application (or rerun `./gradlew --no-daemon --init-script [tools/run-rest-api.init.gradle.kts](tools/run-rest-api.init.gradle.kts) runRestApi`
  in development).

## Troubleshooting

- **Anchor parsing failures** – The resolver emits warnings when PEM material is malformed. CLI and
  REST telemetry include `anchorWarnings`; the operator UI surfaces the same text beneath the result
  card. Fix the PEM content in the metadata bundle and rerun the targeted tests.
- **Duplicate entry IDs** – The catalogue loader rejects duplicates and fails fast during
  `:application:test`. Ensure each `entry_id` (and `vector_id` Source mapping) is unique.
- **Out-of-sync attestation fixtures** – If a metadata entry references a `vector_id` that does not
  exist in ``docs/webauthn_attestation`/`, the loader throws during tests. Add or update the fixture
  first, rerun tests, and then refresh the metadata bundle.

## Follow-up Checklist

- [ ] Add/adjust metadata JSON under ``docs/webauthn_attestation/mds`/`.
- [ ] Run targeted Gradle tests (`:application:test`, `:cli:test`, `:rest-api:test`).
- [ ] Update feature plan/task documents with the refresh details.
- [ ] Verify CLI/REST/UI telemetry shows the new metadata entry identifiers.
