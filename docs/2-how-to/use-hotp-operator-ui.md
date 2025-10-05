# Use the HOTP Operator UI

_Status: Draft_
_Last updated: 2025-10-05_

The operator console embedded in the REST API now provides HOTP evaluation and replay tooling alongside the existing OCRA flows. This guide walks through evaluating stored credentials, running inline checks, replaying observed OTPs without mutating counters, and interpreting telemetry identifiers.

## Prerequisites
- Run the REST API (`./gradlew :rest-api:bootRun`) so the operator console is reachable at `http://localhost:8080/ui/console`.
- Ensure the simulator can access a MapDB credential store (defaults to `data/credentials.db`). Use the CLI to import HOTP credentials if none exist.
- Keep the CLI or REST endpoints available for seeding new credentials or clearing counters when needed.

## Evaluate a Stored HOTP Credential
1. Open `http://localhost:8080/ui/console?protocol=hotp` and select the **HOTP** tab if it is not already active.
2. The console fetches stored HOTP credentials from `/api/v1/hotp/credentials`. Choose an entry from the **Credential** dropdown; labels include hash algorithm and digit length for quick reference.
3. Select **Evaluate stored credential** to generate the next OTP without providing any additional input. The UI renders the generated OTP, counter progression, algorithm, and telemetry identifier in the result panel.
4. Validation errors (counter overflow, missing credential) appear in the error panel with sanitized messaging; counters are not mutated when evaluation fails.

## Run Inline HOTP Checks
1. Switch the evaluation mode toggle to **Inline parameters**; the inline form appears immediately. Use **Load a sample vector** to populate known demo values—`Inline demo vector (SHA-1)` mirrors RFC 4226 while `Inline demo vector (SHA-256)` highlights the 8-digit SHA-256 flow. You can still provide your own secret, algorithm, digit length, and counter manually.
2. Choose **Evaluate inline parameters** to call `/api/v1/hotp/evaluate/inline` without mutating stored credentials. The response contains the generated OTP, the previous/next counter values, and telemetry metadata.
3. Inline evaluation metadata now includes the selected preset key/label when you launch the request from a sample vector, making it easier to trace automated drills in downstream logs.

## Replay Observed HOTP OTPs
1. Activate the **Replay** pill within the HOTP tab to load the replay form. The console defaults to **Stored** mode and surfaces a CSRF-aware form bound to `/api/v1/hotp/replay`.
2. For stored credentials, choose an entry from the dropdown, paste the observed OTP, and submit **Replay stored credential**. The response echoes counter metadata but leaves the MapDB counter untouched.
3. Switch mode to **Inline** to replay ad-hoc secrets. Provide the identifier, shared secret (hex), hash algorithm, digit length, counter, and observed OTP; optional advanced metadata (label/notes) is sent as contextual hints without persistence.
4. Use **Load a sample vector** to populate demo values that mirror the Selenium coverage. Inline presets derive from RFC 4226 test vectors, while stored presets reuse the seeded credential shipped with the UI smoke tests.
5. Results report a **match**/**mismatch** status plus telemetry metadata; validation issues render sanitized error details and never mutate counters or stored credentials.

## Telemetry and Counter Hygiene
- Stored and inline evaluations emit `rest.hotp.evaluate` telemetry via the shared `TelemetryContracts` adapters. Result panels surface the telemetry identifier so operators can correlate with downstream logs.
- Replay flows emit `rest.hotp.replay` telemetry. The UI normalises telemetry IDs to the `ui-hotp-*` prefix for operator readability while preserving the original identifier in the metadata list.
- Replay responses expose `credentialSource`, `previousCounter`, and `nextCounter` without mutating counters. Evaluation responses include the counter delta when the OTP matches and a sanitized payload when validation fails.
- UI requests reuse the console CSRF token; if a 403 occurs, refresh the page to mint a new session token before retrying.
- Seed or import additional HOTP credentials with the CLI (`./gradlew :cli:run --args='hotp import …'`) to keep the directory populated for operator drills.

Keep this document updated as HOTP issuance or additional operator affordances land.
