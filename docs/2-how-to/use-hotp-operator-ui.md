# Use the HOTP Operator UI

_Status: Draft_
_Last updated: 2025-10-06_

The operator console embedded in the REST API now provides HOTP evaluation and replay tooling alongside the existing OCRA flows. This guide walks through evaluating stored credentials, running inline checks, replaying observed OTPs without mutating counters, and interpreting telemetry identifiers.

## Prerequisites
- Run the REST API (`./gradlew :rest-api:bootRun`) so the operator console is reachable at `http://localhost:8080/ui/console`.
- Ensure the simulator can access a MapDB credential store (defaults to `data/credentials.db`). Use the CLI to import HOTP credentials if none exist.
- The operator console now exposes a **Seed sample credentials** button in stored mode; use it to add the canonical SHA-1/6 and SHA-256/8 demo records before running drills. The CLI and REST endpoints remain available for bulk imports or cleanup.

## Turn on Verbose Tracing
- A global **Enable verbose tracing for the next request** checkbox now lives in the console header. Leave it off for regular runs; when enabled, the next HOTP evaluation or replay request sets `"verbose": true` so the REST API returns a detailed `trace` payload alongside the status and OTP data.
- Verbose traces surface every step (`resolve.credential`, `generate.otp`, `persist.counter`, etc.) with the raw inputs and outputs that produced the result. The lower **Verbose trace** dock automatically expands when a verbose response returns, rendering the terminal-style output and exposing a **Copy trace** button for quick sharing. Because the trace includes shared secrets and counters, disable the toggle as soon as you are done inspecting the flow.
- With verbose mode disabled, the dock collapses and subsequent requests omit the verbose flag, keeping sensitive material out of the UI and REST payload.

## Seed Canonical HOTP Credentials
1. Navigate to `http://localhost:8080/ui/console?protocol=hotp` and confirm the **Stored credential** mode is active.
2. Locate the **Seed sample credentials** button beneath the credential dropdown. The control appears only in stored mode.
3. Choose **Seed sample credentials** to call `/api/v1/hotp/credentials/seed`. The UI surfaces a status message indicating how many canonical records were added (or whether they already exist) and refreshes the credential dropdown automatically. Seeding now provisions six stored credentials matching every inline preset: the RFC 4226 SHA-1 vector (6 digits), SHA-1 (8 digits), SHA-256 (6 digits, 8 digits), and SHA-512 (6 digits, 8 digits).
4. Reseeding is idempotent; the console reports when no new credentials were added so repeated drills stay predictable.

## Reference Vectors
- HOTP demo data now ships with an RFC 4226 catalogue at `docs/hotp_validation_vectors.json`. The file enumerates counters 0–9 for both six- and eight-digit SHA-1 sequences and includes metadata fields (`vectorId`, `secretEncoding`, `algorithm`, `digits`, `counter`, `otp`, optional `label`/`notes`).
- Core, CLI, REST, and operator UI fixtures load from this catalogue so sample data stays consistent. Inline presets pull the `vectorId`s `rfc4226_sha1_digits6_counter5` and `rfc4226_sha1_digits8_counter5`; stored-mode seeding uses the counter `0` variant.
- When adding new presets, extend the JSON catalogue first and reference the new `vectorId` from CLI/REST/JS helpers instead of hard-coding secrets or OTP values.

## Evaluate a Stored HOTP Credential
1. Open `http://localhost:8080/ui/console?protocol=hotp` and select the **HOTP** tab if it is not already active.
2. The console fetches stored HOTP credentials from `/api/v1/hotp/credentials`. Choose an entry from the **Credential** dropdown; labels include hash algorithm and digit length for quick reference.
3. Select **Evaluate stored credential** to generate the next OTP without providing any additional input. The UI renders the generated OTP, counter progression, algorithm, and telemetry identifier in the result panel.
4. Validation errors (counter overflow, missing credential) appear in the error panel with sanitized messaging; counters are not mutated when evaluation fails.

## Run Inline HOTP Checks
1. Switch the evaluation mode toggle to **Inline parameters**; the inline form appears immediately. Use **Load a sample vector** to populate curated data—`SHA-1, 6 digits (RFC 4226)` mirrors the reference vector, while the remaining presets cover SHA-1/8 digits, SHA-256 (6 and 8 digits), and SHA-512 (6 and 8 digits). Each inline preset now has an identically configured stored credential seeded from the previous step so you can compare stored and inline flows side-by-side. You can still provide your own secret, algorithm, digit length, and counter manually.
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
