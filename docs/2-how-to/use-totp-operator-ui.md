# Use the TOTP Operator UI

_Status: Draft_
_Last updated: 2025-10-13_

The operator console embedded in the REST API now exposes TOTP evaluation and replay tooling alongside the existing OCRA and HOTP flows. This guide shows how to validate stored credentials, drive inline checks with custom parameters, replay OTP submissions without mutating counters, and interpret the metadata surfaced by the UI.

## Prerequisites
- Run the REST API locally (`./gradlew :rest-api:bootRun`) so the console is reachable at `http://localhost:8080/ui/console`.
- Ensure the simulator can read the credential store MapDB file (defaults to `data/credentials.db`). TOTP issuance is still out of scope, but the operator console now exposes a **Seed sample credentials** action that loads canonical demo entries into the store. Use the TOTP CLI (`./gradlew :cli:run --args='totp list'`) if you want to inspect or verify the seeded credentials outside the UI.
- Inline evaluation does not require persisted credentials, but you will need the shared secret (hex), algorithm, digit length, time-step, drift window, and OTP you want to verify.
- RFC 6238 validation vectors now ship in `docs/totp_validation_vectors.json`; the simulator loads this catalogue for CLI, REST, and UI presets so the same fixtures remain available across interfaces.

## Evaluate a Stored TOTP Credential
1. Navigate to `http://localhost:8080/ui/console?protocol=totp`. The console activates the **TOTP** protocol and selects the **Evaluate** tab with the mode toggle set to **Stored credential**.
2. Select the stored credential from the **Credential** dropdown. If the registry is empty, choose **Seed sample credentials** to load the same SHA-1/SHA-256/SHA-512 presets available in the inline dropdown (covering both 6- and 8-digit variants). You can reseed at any time—the action is idempotent and reports when all canonical entries already exist.
3. Enter the OTP you want to verify plus the timestamp (epoch seconds) that the operator supplied. If you omit the timestamp, the simulator uses the server clock.
4. Adjust the **Drift backward** and **Drift forward** fields to reflect the permitted window (defaults to ±1 step). Populate **Timestamp override** if you need to emulate verifier clock skew; the service will evaluate using the override and keep the submission timestamp for telemetry.
5. Choose **Evaluate stored credential**. On success the result panel surfaces the submitted OTP and a status badge (`validated`, `otp_out_of_window`, etc.). Errors appear in the dedicated error panel with sanitized reason codes (for example `credential_not_found` or `otp_out_of_window`).

## Run Inline TOTP Evaluations
1. Switch the mode toggle to **Inline parameters**. The form hides the stored controls and surfaces inputs for the shared secret, algorithm, digits, step size, drift window, timestamp, optional timestamp override, and OTP. A **Load a sample vector** dropdown appears above the form when presets are available.
2. Pick a preset when you want to preload the fields with curated data. RFC 6238-derived samples now cover SHA-1, SHA-256, and SHA-512 secrets: the 8-digit presets mirror Appendix B and display the `(RFC 6238)` suffix in the dropdown, while the 6-digit options truncate those reference values and keep a plain label. Another preset mirrors the canonical credential delivered by the **Seed sample credentials** control. Selecting a preset fills in the secret, algorithm, digits, step seconds, drift window, timestamp, and OTP while clearing the timestamp override. Changing any field keeps the preset applied until you manually choose another sample or reset the dropdown.
3. To start from scratch, leave the preset dropdown on the placeholder option and paste the shared secret as a hex string. Select the hash algorithm (`SHA1`, `SHA256`, or `SHA512`), adjust the digit length (6 or 8) and step duration (seconds) to match the credential under test.
4. Define acceptable drift by setting the backward and forward step counts. Leave the fields at zero to enforce an exact match window. Provide the timestamp representing when the OTP was issued and, if required, set **Timestamp override** to simulate the verifier’s clock. Finally, enter the OTP you want to validate and press **Evaluate inline parameters**.
5. Successful responses render the submitted OTP and status badge; validation failures surface the sanitized reason code and message (for example `otp_out_of_window`) without echoing the shared secret or OTP in telemetry or UI.

## Replay Stored or Inline TOTP Submissions
1. Select the **Replay** tab. The console exposes a replay mode toggle that defaults to **Stored credential** and never mutates simulator state.
2. For stored replays, select the credential from the dropdown and either click **Load sample data** to populate the OTP/timestamp/drift fields with a guaranteed match or enter your own values manually. Adjust the drift window if you want to test looser or stricter tolerance and populate the optional timestamp override when simulating verifier skew. Click **Verify OTP** to receive a `match` or `mismatch` outcome alongside the corresponding reason code; additional metadata remains available through telemetry logs if you need deeper diagnostics.
3. For inline replays, choose **Inline parameters**. Use the **Load a sample vector** dropdown to populate all inline fields with any RFC 6238 sample (8-digit) or derived 6-digit truncation, plus the demo preset, or leave it blank to supply your own data. Provide the shared secret and configuration (algorithm, digits, step seconds, drift window), set the timestamp and optional override, then press **Verify OTP**. The UI reports whether the OTP would have matched under the supplied conditions.
4. Replay requests emit `rest.totp.replay` telemetry with the same sanitisation guarantees as evaluation—secrets and OTP values are never echoed in the payload.

## Persist Tabs and Modes with Query Parameters
- The console encodes the active tab (`totpTab=evaluate` or `totpTab=replay`) and the current mode (`totpMode=stored|inline` for evaluation, `totpReplayMode=stored|inline` for replay) into the URL. Bookmark or share the deep link to reopen the console in the same state.
- Changing evaluation or replay modes emits `operator:totp-mode-changed` and `operator:totp-replay-mode-changed` events respectively; the surrounding console updates browser history so the back button and refreshes preserve your selection.

## Interpreting Metadata and Telemetry
- Stored requests emit `rest.totp.evaluate` telemetry with context fields for algorithm, digit length, step seconds, drift window, matched skew steps, and whether a timestamp override was provided. Telemetry identifiers remain available in server logs; the UI focuses on OTP + status parity with HOTP/OCRA.
- Inline validations emit the same evaluation event but omit credential references and redact the shared secret, OTP, and override timestamp values. When you select an inline preset, the UI still sends the preset key/label so telemetry frames can trace which sample produced the submission. Replay submissions emit `rest.totp.replay` telemetry, adding the credential source and replay outcome (`match`/`mismatch`) without exposing secrets or OTP material.
- Invoking **Load sample data** on the stored replay form emits a `totp.sample` telemetry frame that records the credential identifier, algorithm, digit length, step seconds, drift window, and timestamp provided while keeping secrets and OTP material redacted. The UI surfaces the same sanitized status message shown in the telemetry payload so operators see which preset or note applied.
- Validation errors return `reasonCode` values such as `credential_not_found`, `otp_out_of_window`, or `shared_secret_required`. The UI mirrors these codes in the error panels so operators can triage without inspecting server logs.
- Remember that evaluation and replay requests never mutate persistence—TOTP remains verification-only until issuance lands in a future feature.

Keep this guide updated as TOTP issuance, credential seeding helpers, or replay tooling are introduced in subsequent increments.
