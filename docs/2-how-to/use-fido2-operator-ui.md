# Use the FIDO2/WebAuthn Operator UI

_Status: Draft_  
_Last updated: 2025-10-11_

The operator console bundled with the REST API now includes a FIDO2/WebAuthn panel alongside the existing HOTP, TOTP, and OCRA tooling. This guide shows you how to seed demo credentials, generate WebAuthn assertions from stored or inline inputs, replay submissions without mutating counters, and interpret the sanitized telemetry that surfaces after each action.

## Prerequisites
- Start the REST API locally (`./gradlew :rest-api:bootRun`). The console lives at `http://localhost:8080/ui/console`.
- Ensure the simulator can open its MapDB store (`data/fido2-credentials.db` by default). If you have not seeded any WebAuthn credentials, the UI provides a **Seed sample credential** button that pulls curated entries from `docs/webauthn_assertion_vectors.json`.
- Use a modern browser with JavaScript enabled. The panel relies on client-side scripts for preset loading, telemetry updates, and query-parameter deep links.

Directly open the WebAuthn tab via `http://localhost:8080/ui/console?protocol=fido2`. The console keeps protocol/mode preferences in the query string (see [Persisting mode state](#persisting-mode-state)).

## Generate Stored Assertions
1. Navigate to the WebAuthn panel. The Evaluate tab now defaults to **Inline** mode; switch the radio selector to **Stored credential** when you want to operate on registry entries.
2. If the credential dropdown is empty, click **Seed sample credential**. The action loads one representative credential per supported signature algorithm (ES256/ES384/ES512, RS256, PS256, Ed25519) plus the canonical W3C §16 `packed-es256` fixture. Seeding is idempotent; a banner reports whether new entries were added.
3. Select a credential from **Stored credential**. Click **Load preset challenge & key** to populate the awaiting fields with a sample Base64URL challenge and a compact authenticator private key (JWK). Both values come from the curated presets maintained in `Fido2OperatorSampleData`.
4. Adjust the relying-party ID, origin, client-data type, challenge, or private key if you need to simulate alternate inputs. Signature-counter and UV overrides are optional—leave them blank to reuse the stored values.
5. Press **Generate stored assertion**. The result panel renders the signed `PublicKeyCredential` JSON, enables **Copy JSON** / **Download JSON** controls, and displays a sanitized telemetry summary (`event=rest.fido2.evaluate`) with the credential source, telemetry id, algorithm, and UV requirement. If generation fails (for example an invalid private key), the JSON area shows `Generation failed.` and the telemetry line records the sanitized error reason.

## Generate Inline Assertions
1. Keep the Evaluate tab in **Inline** mode (the default). The stored form hides and the inline parameters remain compact until you pick a preset.
2. Select a sample from **Load a sample vector**. The dropdown lists exactly one generator-backed option per supported algorithm (labels follow the `<algorithm> - UV required|UV optional` pattern), letting you jump between ES256/384/512, RS256, PS256, and Ed25519 without scrolling through the full JSON catalogue. The form fields update immediately, so you can tweak the pre-filled relying-party data, challenge, and authenticator private key as needed without touching the credential store.
3. Provide or adjust your own relying-party data, challenge, signature counter, UV flag, and private key. Click **Generate inline assertion** to invoke the REST endpoint.
4. The result panel renders the generated `PublicKeyCredential` JSON. The telemetry line echoes a sanitized summary (`credentialSource=inline`, `credentialReference=false`, algorithm, origin, telemetry id). Errors (for example malformed JWK payloads) surface in an inline alert with the sanitized `reasonCode` returned by the API.

## Replay Stored Assertions
1. Toggle to **Replay**. The panel never mutates counters—it simply checks whether the supplied assertion matches the stored credential.
2. Choose a credential from the dropdown (reseed if necessary) and click **Load sample assertion** to retrieve the canonical payload. Each credential map includes a pointer back to the same generator preset used for inline and stored generation flows.
3. Modify the challenge or origin to observe `match=false` behavior, then click **Verify assertion**.
4. The result panel highlights whether the assertion matched and the telemetry frame records `event=rest.fido2.replay`, `credentialSource=stored`, `match=true|false`, and sanitized error details.

Inline replay follows the same shape: switch the mode selector to **Inline**, load a sample, tweak fields as needed, and submit. Telemetry reports `credentialSource=inline` while keeping Base64URL material redacted.

## Persisting Mode State
- Deep links: append `&fido2Mode=stored|inline|replay` to the console URL to open the panel in a specific mode (for example `http://localhost:8080/ui/console?protocol=fido2&fido2Mode=inline`).
- History integration: changing modes emits `operator:fido2-mode-changed` events, updates `fido2Mode` in the query string, and pushes new history entries so the browser back/forward buttons restore your selection.
- Preferences: the console remembers the last WebAuthn mode you used and replays it on the next visit.

## Telemetry & Troubleshooting
- Stored and inline generations emit sanitized telemetry frames (`event=rest.fido2.evaluate`). The result panel mirrors the telemetry id so you can cross-reference server logs without exposing challenges, signatures, or private keys.
- Sample loading is local to the console. Seeding continues to call `/api/v1/webauthn/credentials/seed`; network failures surface in the metadata line with a sanitized error message.
- Copy/download buttons are disabled until an assertion is successfully generated. If you change presets, the console clears the previous payload and telemetry so you do not accidentally reuse stale data.
- If you add additional fixtures, update `Fido2OperatorSampleData.inlineVectors()` so the curated subset stays concise—exactly one sample per algorithm. The CLI/REST facades always expose the complete catalogue via `fido2 vectors` and the WebAuthn REST endpoints.

## Related Resources
- [Use the FIDO2/WebAuthn CLI](use-fido2-cli-operations.md) for headless verification and replay workflows powered by the same JSON vectors.
- [Operate the FIDO2/WebAuthn REST API](use-fido2-rest-operations.md) to script evaluations or consume the endpoints from automated test suites.
- `docs/webauthn_assertion_vectors.json` for the canonical synthetic assertion data used across CLI, REST, and UI presets.
