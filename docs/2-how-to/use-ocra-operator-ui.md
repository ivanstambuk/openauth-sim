# How-To: Use the OCRA Operator UI

_Status: Draft_
_Last updated: 2025-09-29_

The operator UI provides a browser-based workflow for running OCRA evaluations against the
existing REST endpoint without exposing shared secrets in logs or templates. It mirrors the REST
contract so operators can swap between the UI, CLI, and direct API calls with consistent
telemetry.

## Prerequisites
- The `rest-api` Spring Boot application is running (launch the `RestApiApplication` class from your IDE or use `./gradlew :rest-api:run` if you have added the `application` plugin locally).
- Point both the REST app and CLI at the same credential database (default `build/operator-ui/credentials.db`). You can override this path via the `openauth.sim.persistence.database-path` property.
- Your browser can reach the service host (default `http://localhost:8080`).
- OCRA credentials are already persisted if you plan to use the stored credential flow.

## Launching the Console
1. Navigate to `http://localhost:8080/ui/ocra`.
2. Review the landing page and follow the **Evaluate OCRA responses** link to load the console.
3. A CSRF-protected form renders; tokens are bound to your HTTP session and reused by the JavaScript fetch client.
4. If you seeded credentials via the CLI, ensure you reused the same database path as the REST app (for example `--database=build/operator-ui/credentials.db`).

## Choosing an Evaluation Mode
- **Inline parameters** – Provide the OCRA suite and shared secret as hex. Use this for ad-hoc
  checks when you do not want to rely on persisted credentials. A preset dropdown loads sample
  vectors (derived from the automated tests) to speed up manual verification. Presets include both
  the RFC 6287 session samples and the `OCRA-1:HOTP-SHA256-6:C-QH64` counter/hex scenario generated
  via the Appendix B workflow described in `generate-ocra-test-vectors.md`.
- **Stored credential** – Supply a credential identifier that already exists in the simulator.
  The UI forwards only the identifier; secrets stay in persistence. The REST app loads
  credentials from the MapDB file referenced by `openauth.sim.persistence.database-path`.

The mode toggle is keyboard-accessible and announces which section is visible. JavaScript is required
for evaluations because submissions now run through asynchronous JSON fetch calls. The Evaluate button
no longer performs a traditional form POST; without JavaScript the console stays inert.

## Supplying Request Parameters
- Inline mode requires the suite and shared secret. An optional PIN hash field supports suite
  variants that expect it.
- Request parameters (challenge, client/server challenge, session, timestamp, counter) map one-to-one
  with `POST /api/v1/ocra/evaluate`. Leave fields blank to accept backend defaults. The counter field
  is automatically populated when you select the C-QH64 preset so results match the documented test
  vectors.
- Shared secrets remain visible after each evaluation so you can iterate on the same test data
  during verification. Clear the field manually if you need to hide the value. The fetch handler
  still clears the optional PIN hash field once the REST call completes.

## Reading Results and Telemetry
- Successful evaluations show the OTP prominently and render a telemetry summary as a definition list
  (status, telemetry ID, reason code, sanitized flag, suite). The result panel is injected dynamically
  once the fetch request resolves.
- Copy the telemetry block when escalating issues; it matches the REST payload and is already
  redacted for safety.
- Validation errors reuse sanitized reason codes/messages from the REST API. Unexpected server errors
  surface a generic banner without secret material. The error panel is only revealed when the REST
  call returns a non-2xx status.

## Logging & Observability Notes
- UI submissions emit the same sanitized telemetry events as REST calls. Operator logs include the
  telemetry ID and reason code only.
- CSRF protection relies on HTTP sessions; clear cookies or restart the server to invalidate tokens.
- The controller keeps shared secrets in memory for the active session so operators can reuse
  inline data. Only the optional PIN hash field is cleared automatically after each request.

## Next Steps
- Extend the UI with credential discovery or search once Feature 006 scope expands.
- Keep observer tooling pointed at the telemetry ID to correlate UI actions with backend traces.
