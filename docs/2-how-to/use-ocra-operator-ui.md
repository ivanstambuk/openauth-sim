# How-To: Use the OCRA Operator UI

_Status: Draft_
_Last updated: 2025-10-03_

The operator UI provides a browser-based workflow for running OCRA evaluations and replaying
previous OTP submissions against the REST facade without exposing shared secrets in logs or
templates. It mirrors the REST contract so operators can swap between the UI, CLI, and direct API
calls with consistent telemetry.

## Prerequisites
- The `rest-api` Spring Boot application is running (launch the `RestApiApplication` class from your IDE or use `./gradlew :rest-api:run` if you have added the `application` plugin locally).
- Point both the REST app and CLI at the same credential database (default `data/ocra-credentials.db`). You can override this path via the `openauth.sim.persistence.database-path` property.
- Your browser can reach the service host (default `http://localhost:8080`).
- OCRA credentials already exist if you plan to use stored flows (evaluation presets or replay).

## Launching the Console
1. Navigate to `http://localhost:8080/ui/ocra`.
2. The navigation bar exposes **Evaluate** (default view) and **Replay**. Keyboard shortcuts (`Tab`/`Shift+Tab`) move between the tabs and their content sections.
3. Every view uses CSRF-protected fetch requests; tokens are bound to your HTTP session and reused by the JavaScript client.
4. If you seeded credentials via the CLI, ensure you reused the same database path as the REST app (for example `--database=data/ocra-credentials.db`).

## Using the Evaluation Console

### Choosing an Evaluation Mode
- **Inline parameters** – Provide the OCRA suite and shared secret as hex. Use this for ad-hoc checks when you do not want to rely on persisted credentials. A preset dropdown loads sample vectors (derived from the automated tests) to speed up manual verification. Presets include both the RFC 6287 session samples and the `OCRA-1:HOTP-SHA256-6:C-QH64` counter/hex scenario generated via the Appendix B workflow described in `generate-ocra-test-vectors.md`.
- **Stored credential** – Supply a credential identifier that already exists in the simulator. The UI forwards only the identifier; secrets stay in persistence. The REST app loads credentials from the MapDB file referenced by `openauth.sim.persistence.database-path`. After selecting a credential, use **Auto-fill parameters** to generate suite-compatible challenges, counters, sessions, and timestamps directly in the browser so the subsequent evaluation succeeds without manual data entry.

The mode toggle is keyboard-accessible and announces which section is visible. JavaScript is required for evaluations because submissions run through asynchronous JSON fetch calls. The Evaluate button no longer performs a traditional form POST; without JavaScript the console stays inert.

### Supplying Request Parameters
- Inline mode requires the suite and shared secret. An optional PIN hash field supports suite variants that expect it.
- Request parameters (challenge, client/server challenge, session, timestamp, counter) map one-to-one with `POST /api/v1/ocra/evaluate`. Leave fields blank to accept backend defaults. The counter field is automatically populated when you select the C-QH64 preset so results match the documented test vectors. Stored credential mode can auto-populate required fields; the UI clears values the selected suite forbids so you avoid validation conflicts.
- Shared secrets remain visible after each evaluation so you can iterate on the same test data during verification. Clear the field manually if you need to hide the value. The fetch handler still clears the optional PIN hash field once the REST call completes.

## Using the Replay Screen
1. Select the **Replay** tab in the navigation bar or browse directly to `http://localhost:8080/ui/ocra/replay`.
2. The screen presents two cards: **Stored credential replay** and **Inline replay**. Toggle buttons are keyboard-focusable and announce which mode is active.
3. Once a replay completes, the UI sends a follow-up POST to `/ui/ocra/replay/telemetry` so the shared telemetry adapter records the outcome with `origin=ui` and replay metadata.

### Stored Credential Replays
- Pick a credential from the dropdown. The UI fetches the credential inventory from `/api/v1/ocra/credentials` and caches it for the active session.
- Provide the OTP the operator received plus any context values (challenge, counter, sessions, timestamp). Defaults match those exposed on the evaluation console.
- Submit the form to send `{ credentialId, otp, context }` to `POST /api/v1/ocra/verify`. Secrets remain server-side; only the identifier travels over the wire.
- A successful replay displays `outcome=match`, highlights the telemetry ID returned by the REST endpoint, and shows the hashed `contextFingerprint` for auditing. Mismatch or validation errors render descriptive banners with the REST-provided reason code.

### Inline Secret Replays
- Supply the full suite descriptor, shared secret hex, and OTP along with context fields. The UI enforces the same required/optional constraints as the REST endpoint and redacts the secret from telemetry.
- Validation feedback appears inline; failing fields gain `aria-invalid="true"` and error messages for assistive technology. Successful submissions echo the telemetry ID plus the sanitized fingerprint hash so teams can cross-check backend logs.

## Reading Results and Telemetry
- Evaluation results render the OTP, status, telemetry ID, reason code, and suite. Replay responses focus on match/mismatch status, credential source (`stored` vs `inline`), sanitized flag, and the hashed context fingerprint.
- Copy the telemetry panel when escalating issues; it matches the REST metadata (`mode`, `credentialSource`, `outcome`, `contextFingerprint`) and is already redacted for safety.
- Validation errors reuse sanitized reason codes/messages from the REST API. Unexpected server errors surface a generic banner without secret material. Error panels appear only when the REST call returns a non-2xx status.

## Logging & Observability Notes
- Evaluation submissions emit `event=rest.ocra.evaluate`; replays emit `event=ui.ocra.replay` with `mode`, `outcome`, and hashed fingerprints. All events flow through `TelemetryContracts.ocraVerificationAdapter` and preserve the `sanitized=true` invariant.
- CSRF protection relies on HTTP sessions; clear cookies or restart the server to invalidate tokens.
- The controller keeps shared secrets in memory for the active session so operators can reuse inline data. Only the optional PIN hash field is cleared automatically after each request.
- Keep observer tooling pointed at the telemetry ID to correlate UI actions with backend traces. Stored replays use the credential identifier only; the shared secret never leaves persistence.
