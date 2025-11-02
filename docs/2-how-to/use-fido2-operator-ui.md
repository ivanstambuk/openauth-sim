# Use the FIDO2/WebAuthn Operator UI

_Status: Draft_  
_Last updated: 2025-10-27_

The operator console bundled with the REST API now includes a FIDO2/WebAuthn panel alongside the existing HOTP, TOTP, and OCRA tooling. This guide shows you how to seed demo credentials, generate WebAuthn assertions from stored or inline inputs, replay submissions without mutating counters, and interpret the sanitized telemetry that surfaces after each action.

## Prerequisites
- Start the REST API locally (`./gradlew --no-daemon --init-script tools/run-rest-api.init.gradle.kts runRestApi`). The console lives at `http://localhost:8080/ui/console`.
- Ensure the simulator can open its MapDB store (`data/credentials.db` by default). If you have not seeded any WebAuthn credentials, the UI provides a **Seed sample credential** button that pulls curated entries from `docs/webauthn_assertion_vectors.json`. Configure the REST property explicitly if you need to point at a legacy file such as `data/fido2-credentials.db`.
- Use a modern browser with JavaScript enabled. The panel relies on client-side scripts for preset loading, telemetry updates, and query-parameter deep links.

## Enable Verbose Tracing
- A shared **Enable verbose tracing for the next request** toggle now appears in the console header. When enabled, the upcoming WebAuthn evaluate or replay call sends `"verbose": true` so the REST API returns a complete trace of each verification step—credential hydration, COSE key parsing, signature verification, counter checks, and trust-anchor resolution.
- Verbose responses automatically expand the docked **Verbose trace** panel at the bottom of the console. The panel lists the operation (`fido2.evaluate.inline`, `fido2.replay.stored`, etc.), ordered steps, attributes (for example `challenge`, `relyingPartyId`, `counterBefore`, `counterAfter`, `verificationResult`), and any notes emitted by the verifier. Use **Copy trace** to grab the terminal-style output for debugging.
- WebAuthn traces now surface authenticator extension metadata directly in the panel. Look for the `parse.extensions` step to confirm `extensions.present`, examine the raw CBOR (`extensions.cbor.hex`), and review decoded fields such as `ext.credProps.rk`, `ext.credProtect.policy`, `ext.largeBlobKey.b64u`, and `ext.hmac-secret`; unexpected keys show up under `extensions.unknown.*` so you can capture vendor-specific fields during investigations.
- Because the trace surfaces raw assertions and credential material, disable the toggle once you have captured the data you need. The dock collapses and subsequent requests omit verbose payloads to reduce the risk of exposing sensitive buffers.

Directly open the WebAuthn tab via `http://localhost:8080/ui/console?protocol=fido2`. The console keeps protocol/mode preferences in the query string (see [Persisting mode state](#persisting-mode-state)).

## Generate Stored Assertions
1. Navigate to the WebAuthn panel. The Evaluate tab now defaults to **Inline** mode; switch the radio selector to **Stored credential** when you want to operate on registry entries.
2. If the credential dropdown is empty, click **Seed sample credential**. The action loads one representative credential per supported signature algorithm (ES256/ES384/ES512, RS256, PS256, Ed25519) plus the canonical W3C §16 `packed-es256` fixture. Seeding is idempotent; a banner reports whether new entries were added.
3. Select a credential from **Stored credential**. Labels now follow the `<algorithm> (source)` pattern—for example `ES256 (W3C 16.1.1)` or `PS256`. The console auto-populates the challenge and authenticator private key (JWK) with the curated preset linked to that entry; re-select the placeholder (`Select a stored credential`) whenever you want to clear the fields and start over. The PS256 entry resolves to the synthetic `synthetic-packed-ps256` fixture so the cross-origin challenge is always present without manual edits.
4. Adjust the relying-party ID, origin, challenge, or private key if you need to simulate alternate inputs. Signature-counter and UV overrides are optional—leave them blank to reuse the stored values.
5. Press **Generate stored assertion**. The result panel renders the signed `PublicKeyCredential` JSON. If generation fails (for example an invalid private key), the JSON area shows `Generation failed.` so you know the payload is unusable; sanitized telemetry remains available from server logs.

## Generate Inline Assertions
1. Keep the Evaluate tab in **Inline** mode (the default). The stored form hides and the inline parameters remain compact until you pick a preset.
2. Select a sample from **Load a sample vector**. The dropdown lists exactly one curated option per supported algorithm (labels follow the `<algorithm> - UV required|UV optional` pattern), letting you jump between ES256/384/512, RS256, PS256, and Ed25519 without scrolling through the full JSON catalogue. The form fields update immediately, so you can tweak the pre-filled relying-party data, challenge, and authenticator private key as needed without touching the credential store.
3. Provide or adjust your own relying-party data, challenge, signature counter, UV flag, and private key. Click **Generate inline assertion** to invoke the REST endpoint.
4. The result panel renders the generated `PublicKeyCredential` JSON. The telemetry line echoes a sanitized summary (`credentialSource=inline`, `credentialReference=false`, algorithm, origin, telemetry id). Errors (for example malformed JWK payloads) surface in an inline alert with the sanitized `reasonCode` returned by the API.

## Replay Stored Assertions
1. Toggle to **Replay**. The panel never mutates counters—it simply checks whether the supplied assertion matches the stored credential.
2. Choose a credential from the dropdown (reseed if necessary) and click **Load sample assertion** to retrieve the canonical payload. Each credential map includes a pointer back to the curated preset used for inline and stored generation flows.
3. Modify the challenge or origin to observe `match=false` behavior, then click **Verify assertion**.
4. The result panel highlights whether the assertion matched and the telemetry frame records `event=rest.fido2.replay`, `credentialSource=stored`, `match=true|false`, and sanitized error details.

Inline replay follows the same shape: switch the mode selector to **Inline**, load a sample, tweak fields as needed, and submit. Telemetry reports `credentialSource=inline` while keeping Base64URL material redacted.

## Replay Stored Attestations
1. With the **Replay** tab active, click **Attestation**. The console locks the ceremony to attestation-specific inputs and hides assertion-only controls.
2. Select the **Stored credential** radio. If you have not seeded attestation credentials yet, run **Seed stored attestation credentials** on the Evaluate tab first (the action is idempotent).
3. Choose a credential from the dropdown. The console fetches `/api/v1/webauthn/attestations/{id}` and `/api/v1/webauthn/credentials/{id}/sample`, then hydrates the relying-party ID, origin, challenge, and attestation format into read-only fields. The stored challenge mirrors what the CLI and REST facades expect, so you cannot accidentally modify it inline.
4. Click **Replay stored attestation**. The submit button stays enabled while metadata loads; the result panel surfaces `event=rest.fido2.attestReplay`, `inputSource=stored`, `storedCredentialId`, and the attestation reason code (for example `match`, `stored_credential_not_found`, or `stored_attestation_required`). Stored mode always relies on the persisted certificate chain, so the UI does not expose a trust-anchor textarea here.

> Rerunning **Seed attestation credentials** simply layers attestation payloads onto the existing assertion entry so the dropdown never duplicates identifiers; the canonical credential secret and metadata stay intact.


## Replay Inline Attestations
1. Switch the attestation mode selector to **Preset** or **Manual**. Preset mode pre-fills vectors from `WebAuthnAttestationSamples`; manual mode lets you paste your own Base64URL attestation object, client-data JSON, and optional PEM trust anchors.
2. Adjust the relying-party metadata as needed, provide trust anchors when you want to enforce a specific root, and submit. The result panel mirrors the telemetry emitted by the REST API and highlights whether anchors matched the stored certificates (`anchorTrusted`) or the verifier fell back to self-attestation.

## Persisting Mode State
- Deep links: use the shared query parameters to open specific states (for example `http://localhost:8080/ui/console?protocol=fido2&tab=evaluate&mode=inline` or `...&tab=replay&mode=stored`). Legacy links that specify `fido2Mode` still resolve for backward compatibility.
- History integration: changing modes emits `operator:fido2-mode-changed` events, updates the shared `mode` (and `tab` when switching between Evaluate/Replay) in the query string, and pushes new history entries so the browser back/forward buttons restore your selection; older history entries containing `fido2Mode` continue to hydrate correctly.
- Preferences: the console remembers the last WebAuthn mode you used and replays it on the next visit.

## Telemetry & Troubleshooting
- Stored and inline generations emit sanitized telemetry frames (`event=rest.fido2.evaluate`). Inline mode still mirrors the telemetry id in the UI; stored mode omits the summary to keep the panel focused on the generated assertion while leaving telemetry accessible in server logs.
- Attestation replays emit `event=rest.fido2.attestReplay` with `inputSource` (inline/preset/stored), `storedCredentialId` when applicable, and anchor metadata (`anchorProvided`, `anchorTrusted`, `anchorSource`). Stored replays reuse the persisted certificate chain, so supplying trust anchors triggers `stored_trust_anchor_unsupported`.
- Sample loading is local to the console. Seeding continues to call `/api/v1/webauthn/credentials/seed`; network failures surface in the inline status banner with a sanitized error message.
- If you change presets, the console clears the previous payload and telemetry so you do not accidentally reuse stale data.
- If you add additional fixtures, update `Fido2OperatorSampleData.inlineVectors()` so the curated subset stays concise—exactly one sample per algorithm. The CLI/REST facades always expose the complete catalogue via `fido2 vectors` and the WebAuthn REST endpoints.

## Related Resources
- [Use the FIDO2/WebAuthn CLI](use-fido2-cli-operations.md) for headless verification and replay workflows powered by the same JSON vectors.
- [Operate the FIDO2/WebAuthn REST API](use-fido2-rest-operations.md) to script evaluations or consume the endpoints from automated test suites.
- `docs/webauthn_assertion_vectors.json` for the canonical synthetic assertion data used across CLI, REST, and UI presets.
