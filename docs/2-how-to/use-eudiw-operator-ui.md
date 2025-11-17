# Use the EUDIW OpenID4VP Operator UI

_Status: Draft_  
_Last updated: 2025-11-08_

The operator console now includes an **EUDIW** tab for Feature 040. It mirrors the REST/CLI contracts so you can generate HAIP-compliant authorization requests, drive deterministic wallet simulations, and validate VP Tokens without leaving the browser. This guide covers the Evaluate (Generate) and Replay (Validate) flows, baseline vs HAIP profile toggles, Trusted Authority diagnostics, and trace handling.

## Prerequisites
- Start the REST application: `./gradlew --no-daemon --init-script [tools/run-rest-api.init.gradle.kts](tools/run-rest-api.init.gradle.kts) runRestApi`.
- Keep the shared fixtures in place (``docs/test-vectors/eudiw/openid4vp`/…`). The simulator ships with the synthetic `pid-haip-baseline` vector preloaded; conformance bundles will appear once the ingestion toggle is exposed.
- Open `http://localhost:8080/ui/console?protocol=eudiw&tab=evaluate` in Chrome, Firefox, or Edge.

## Evaluate (Generate) mode walk-through
1. Select **EUDIW** in the protocol switcher and stay on the **Evaluate** sub-tab (Generate mode).
2. Choose a **Profile**:
   - **HAIP** enforces signed requests, HAIP encryption, and displays no banner.
   - **Baseline** disables HAIP enforcement and displays the yellow “Baseline mode – HAIP enforcement disabled” banner above the result card.
3. Select **Wallet input**:
   - **Stored preset** – pick `pid-haip-baseline` to load the synthetic SD-JWT VC, KB-JWT, and Trusted Authority policies immediately.
   - **Inline parameters** – switch the radio button to enter a compact SD-JWT, disclosures, and optional KB-JWT manually. Use the **Load sample vector** dropdown to copy a fixture into the inline fields without editing JSON files.
4. Optional: expand the DCQL preview (read-only). It always mirrors the presets under ``docs/test-vectors/eudiw/openid4vp/fixtures/dcql`/` and helps you confirm credential IDs, claim paths, and Trusted Authority filters before generation.
5. Click **Generate presentation**. The result column renders:
   - Status badge (`SUCCESS`/`FAILED`).
   - Response mode and holder-binding summary.
   - Inline VP Token JSON (read-only, horizontally scrollable) so you can copy the payload without enabling verbose traces.
   - Trusted Authority labels (e.g., “EU PID Issuer (aki:s9tIpP7qrS9=)”).
6. Use the global **Verbose traces** toggle in the console header when you need hashes, nonce/state masks, QR payloads, or DCQL hashes. All trace entries flow through the shared Trace Dock—individual panels do not have their own checkbox.

### Tips for inline mode
- Credential metadata (`credentialId`, `format`, `trustedAuthorityPolicies`) is required when no preset is selected; the UI highlights any missing fields inline.
- Disclosures accept either plain JSON arrays or compact disclosures pasted directly from the fixture file.
- Holder binding is optional; pasting the KB-JWT automatically flips the “Holder binding” badge in the result card.

## Replay (Validate) mode walk-through
1. Switch to the **Replay** sub-tab—the UI labels this mode as **Validate** to align with the specification.
2. Choose **Source**:
   - **Stored presentation** – select `pid-haip-baseline`. The console loads the reference VP Token and DCQL preview; you only provide an optional Trusted Authority policy override or response mode override.
   - **Inline VP Token** – paste the VP Token JSON (including `vp_token` and `presentation_submission`) plus disclosures and optional KB-JWT. Inline mode also accepts a DCQL preview so the result ties back to the verifier query.
3. Provide an optional **Trusted Authority policy** (e.g., `aki:s9tIpP7qrS9=`). Leave it blank to reuse the policies embedded in the preset or inline payload.
4. Click **Validate presentation**. The result card shows:
   - `SUCCESS` or `FAILED` with the problem-detail summary when validation throws `invalid_scope`/`invalid_presentation`/etc.
   - Credential metadata (format, holder binding, Trusted Authority match) and the sanitized VP Token snippet.
5. Enable verbose traces to capture:
   - `vpTokenHash`, `kbJwtHash`, disclosure hashes.
   - Trusted Authority verdict metadata (type/value/label).
   - Claims path diagnostics for multi-presentation DCQL responses. Each presentation receives a dedicated trace entry that matches the row in the result card.

## Trusted Authority labels & provenance
- The UI surfaces both the hash (`aki:s9tIpP7qrS9=`) and the friendly label (“EU PID Issuer”). These values originate from ``docs/test-vectors/eudiw/openid4vp/trust/snapshots`/*.json` and stay synchronized through the loader.
- When the ingestion toggle exposes conformance bundles, the **Stored preset** dropdown and sample selector automatically reflect the currently selected dataset (synthetic vs conformance). The UI labels those presets with their provenance metadata so reviewers can tell whether a VP Token came from the in-repo fixtures or a third-party bundle.

## Trace dock & telemetry reminders
- Verbose output always routes through the global Trace Dock; copy/download buttons export the same JSON payload returned by REST/CLI.
- Telemetry frames (`event=ui.eudiw.request|wallet|validate`) stay sanitized even when verbose tracing is enabled. Sensitive fields (nonces, disclosures, DeviceResponse blobs) appear as hashes in telemetry and only show up in the trace payload.

## Troubleshooting
- Validation errors surface beside the field and in the result card. For example, supplying a preset plus inline payload triggers “Provide either stored or inline presentation.”
- If presets disappear, restart the REST app to rehydrate the fixture repositories; no database writes occur in the operator UI yet.
- Baseline mode intentionally disables HAIP encryption and hides QR signing metadata. Switch back to HAIP when demonstrating fully compliant flows.
- Selenium coverage (T4015a/b) enforces the two-column layout and trace docking. If you observe layout drift, rerun `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EudiwOperatorUiSeleniumTest"` to reproduce.

Refer to the REST and CLI how-to guides for equivalent payloads and automated workflows.
