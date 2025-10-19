# How-To: Use the OCRA Operator UI

_Status: Draft_
_Last updated: 2025-10-04_

The operator UI provides a browser-based workflow for running OCRA evaluations and replaying
previous OTP submissions against the REST facade without exposing shared secrets in logs or
templates. It mirrors the REST contract so operators can swap between the UI, CLI, and direct API
calls with consistent telemetry.

## Prerequisites
- The `rest-api` Spring Boot application is running (launch the `RestApiApplication` class from your IDE or use `./gradlew :rest-api:run` if you have added the `application` plugin locally).
- Point both the REST app and CLI at the same credential database (default `data/credentials.db`). You can override this path via the `openauth.sim.persistence.database-path` property; rename legacy files like `data/ocra-credentials.db` or point the property directly at them before launching.
- Your browser can reach the service host (default `http://localhost:8080`).
- OCRA credentials already exist if you plan to use stored flows (evaluation presets or replay).

## Launching the Console
1. Navigate to `http://localhost:8080/ui/console`. The unified operator console now hosts all OCRA interactions; additional protocol tabs are disabled until their implementations land.
2. The console encodes the active protocol and OCRA tab in query parameters (for example `?protocol=ocra&tab=replay`), so bookmarks and shared URLs reload the correct state. Deep-linking to disabled protocols (FIDO2/WebAuthn, EMV/CAP) still surfaces their placeholder copy while keeping OCRA-specific controls hidden.
3. The OCRA panel loads by default and presents a dark-themed mode toggle labelled **Evaluate** and **Replay**. Keyboard navigation lands on the toggle first so operators can switch modes with `Enter`/`Space`.
4. Both forms execute CSRF-protected JSON fetches; session-bound tokens are embedded in the page and reused by the JavaScript client.
5. If you seeded credentials via the CLI, make sure the REST app points at the same MapDB database (for example `--openauth.sim.persistence.database-path=data/credentials.db`). The resolver no longer falls back to legacy filenames automatically, so update the property if you rely on an older file.

## Running Evaluations (Evaluate Mode)

### Choosing an Evaluation Mode
- **Inline parameters** – Provide the OCRA suite and shared secret as hex. Use this for ad-hoc checks when you do not want to rely on persisted credentials. A preset dropdown loads sample vectors (derived from the automated tests) to speed up manual verification. Presets now surface one representative for every RFC 6287 Appendix C suite (numeric, counter+PIN, mutual, signature, time-based, and session variants) in addition to the draft-driven `OCRA-1:HOTP-SHA256-6:C-QH64` counter/hex scenario described in `generate-ocra-test-vectors.md`.
- **Stored credential** – Supply a credential identifier that already exists in the simulator. The UI forwards only the identifier; secrets stay in persistence. The REST app loads credentials from the MapDB file referenced by `openauth.sim.persistence.database-path`. After selecting a credential, use **Auto-fill parameters** to generate suite-compatible challenges, counters, sessions, and timestamps directly in the browser so the subsequent evaluation succeeds without manual data entry.

The mode toggle is keyboard-accessible and announces which section is visible. JavaScript is required for evaluations because submissions run through asynchronous JSON fetch calls. The Evaluate button no longer performs a traditional form POST; without JavaScript the console stays inert.

### Supplying Request Parameters
- Inline mode requires the suite and shared secret. An optional PIN hash field supports suite variants that expect it.
- Request parameters (challenge, client/server challenge, session, timestamp, counter) map one-to-one with `POST /api/v1/ocra/evaluate`. Leave fields blank to accept backend defaults. The counter field is automatically populated when you select the C-QH64 preset so results match the documented test vectors. Stored credential mode can auto-populate required fields; the UI clears values the selected suite forbids so you avoid validation conflicts.
- Shared secrets remain visible after each evaluation so you can iterate on the same test data during verification. Clear the field manually if you need to hide the value. The fetch handler still clears the optional PIN hash field once the REST call completes.

## Running Replays (Replay Mode)
1. Click the **Replay** toggle inside the OCRA panel. The evaluation form hides while the replay fields become visible in-place.
2. Replay mode exposes two subsections: **Stored credential replay** (default) and **Inline replay**. The radio buttons drive the same async wiring HtmlUnit exercises in the Selenium suite, so keyboard users receive focus and `aria-live` announcements when sections change.
3. Successful replays trigger a telemetry POST to `/ui/ocra/replay/telemetry` with `origin=ui`, `mode`, outcome, sanitized fingerprint, and credential source so downstream observability matches the REST facade.

### Stored Credential Replays
- Pick a credential from the dropdown. The UI fetches the credential inventory from `/api/v1/ocra/credentials` and caches it for the active session.
- Provide the OTP the operator received plus any context values (challenge, counter, sessions, timestamp). Defaults match those exposed on the evaluation console.
- Submit the form to send `{ credentialId, otp, context }` to `POST /api/v1/ocra/verify`. Secrets remain server-side; only the identifier travels over the wire.
- A successful replay displays `outcome=match`, highlights the telemetry ID returned by the REST endpoint, and shows the hashed `contextFingerprint` for auditing. Mismatch or validation errors render descriptive banners with the REST-provided reason code.

### Inline Secret Replays
- Supply the full suite descriptor, shared secret hex, and OTP along with context fields. The UI enforces the same required/optional constraints as the REST endpoint and redacts the secret from telemetry.
- Validation feedback appears inline; failing fields gain `aria-invalid="true"` and error messages for assistive technology. Successful submissions echo the telemetry ID plus the sanitized fingerprint hash so teams can cross-check backend logs.
- Selecting a preset instantly repopulates the suite, shared secret, OTP, and advanced context with curated sample values so operators can reset the form without extra clicks.

## Reading Results and Telemetry
- Evaluation results render the generated OTP alongside the status row. Sanitisation remains enforced by the REST API, so the UI no longer surfaces a separate flag. Suite details moved to the preset dropdowns, keeping the card compact while still surfacing success/failure at a glance.
- Replay responses now highlight match/mismatch outcome and any attached reason code without echoing telemetry identifiers or credential metadata. Operators can still cross-check telemetry via backend logs using the request timestamp.
- Validation errors reuse sanitized reason codes/messages from the REST API. Unexpected server errors surface a generic banner without secret material. Error panels appear only when the REST call returns a non-2xx status.

## Logging & Observability Notes
- Evaluation submissions emit `event=rest.ocra.evaluate`; replays emit `event=ui.ocra.replay` with `mode`, `outcome`, and hashed fingerprints. All events flow through `TelemetryContracts.ocraVerificationAdapter` and preserve the `sanitized=true` invariant.
- CSRF protection relies on HTTP sessions; clear cookies or restart the server to invalidate tokens.
- The controller keeps shared secrets in memory for the active session so operators can reuse inline data. Only the optional PIN hash field is cleared automatically after each request.
- Keep observer tooling pointed at the telemetry ID to correlate UI actions with backend traces. Stored replays use the credential identifier only; the shared secret never leaves persistence.
