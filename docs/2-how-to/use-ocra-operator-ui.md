# How-To: Use the OCRA Operator UI

_Status: Draft_
_Last updated: 2025-09-28_

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
3. A CSRF-protected form renders; tokens are bound to your HTTP session.
4. If you seeded credentials via the CLI, ensure you reused the same database path as the REST app (for example `--database=build/operator-ui/credentials.db`).

## Choosing an Evaluation Mode
- **Inline parameters** – Provide the OCRA suite and shared secret as hex. Use this for ad-hoc
  checks when you do not want to rely on persisted credentials.
- **Stored credential** – Supply a credential identifier that already exists in the simulator.
  The UI forwards only the identifier; secrets stay in persistence. The REST app loads
  credentials from the MapDB file referenced by `openauth.sim.persistence.database-path`.

The mode toggle is keyboard-accessible and announces which section is visible. JavaScript enhances
it, but the page remains usable without scripts (both sections remain readable).

## Supplying Request Parameters
- Inline mode requires the suite and shared secret. An optional PIN hash field supports suite
  variants that expect it.
- Request parameters (challenge, client/server challenge, session, timestamp, counter) map one-to-one
  with `POST /api/v1/ocra/evaluate`. Leave fields blank to accept backend defaults.
- Secrets are scrubbed from the form after each submission and never rendered back to the page.

## Reading Results and Telemetry
- Successful evaluations show the OTP prominently and render a telemetry summary as a definition list
  (status, telemetry ID, reason code, sanitized flag, suite).
- Copy the telemetry block when escalating issues; it matches the REST payload and is already
  redacted for safety.
- Validation errors reuse sanitized reason codes/messages from the REST API. Unexpected server errors
  surface a generic banner without secret material.

## Logging & Observability Notes
- UI submissions emit the same sanitized telemetry events as REST calls. Operator logs include the
  telemetry ID and reason code only.
- CSRF protection relies on HTTP sessions; clear cookies or restart the server to invalidate tokens.
- The controller clears shared secret and PIN hash fields after delegating to the REST endpoint to
  prevent accidental redisplay.

## Next Steps
- Extend the UI with credential discovery or search once Feature 006 scope expands.
- Keep observer tooling pointed at the telemetry ID to correlate UI actions with backend traces.
