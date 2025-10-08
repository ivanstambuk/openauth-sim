# Use the TOTP Operator UI

_Status: Draft_
_Last updated: 2025-10-08_

The operator console embedded in the REST API now exposes TOTP evaluation and replay tooling alongside the existing OCRA and HOTP flows. This guide shows how to validate stored credentials, drive inline checks with custom parameters, replay OTP submissions without mutating counters, and interpret the metadata surfaced by the UI.

## Prerequisites
- Run the REST API locally (`./gradlew :rest-api:bootRun`) so the console is reachable at `http://localhost:8080/ui/console`.
- Ensure the simulator can read the credential store MapDB file (defaults to `data/credentials.db`). TOTP issuance is still out of scope, so populate stored credentials through bespoke scripts or migrations before exercising the stored flow. Use the TOTP CLI (`./gradlew :cli:run --args='totp list'`) to confirm which credential IDs are available.
- Inline evaluation does not require persisted credentials, but you will need the shared secret (hex), algorithm, digit length, time-step, drift window, and OTP you want to verify.

## Evaluate a Stored TOTP Credential
1. Navigate to `http://localhost:8080/ui/console?protocol=totp`. The console activates the **TOTP** protocol and selects the **Evaluate** tab with the mode toggle set to **Stored credential**.
2. Provide the stored credential identifier in **Credential ID**. The value must match the entry saved in MapDB (case-sensitive).
3. Enter the OTP you want to verify plus the timestamp (epoch seconds) that the operator supplied. If you omit the timestamp, the simulator uses the server clock.
4. Adjust the **Drift backward** and **Drift forward** fields to reflect the permitted window (defaults to ±1 step). Populate **Timestamp override** if you need to emulate verifier clock skew; the service will evaluate using the override and keep the submission timestamp for telemetry.
5. Choose **Evaluate stored credential**. On success the result panel surfaces the status (`validated` or error code), matched skew steps, algorithm, digit count, step duration, drift window, and telemetry identifier. Errors appear in the dedicated error panel with sanitized reason codes (for example `credential_not_found` or `otp_out_of_window`).

## Run Inline TOTP Evaluations
1. Switch the mode toggle to **Inline parameters**. The form hides the stored controls and surfaces inputs for the shared secret, algorithm, digits, step size, drift window, timestamp, optional timestamp override, and OTP.
2. Paste the shared secret as a hex string. Select the hash algorithm (`SHA1`, `SHA256`, or `SHA512`), adjust the digit length (6 or 8) and step duration (seconds) to match the credential under test.
3. Define acceptable drift by setting the backward and forward step counts. Leave the fields at zero to enforce an exact match window.
4. Provide the timestamp representing when the OTP was issued. Set **Timestamp override** if you need to simulate the verifier’s clock. Finally, enter the OTP you want to validate and press **Evaluate inline parameters**.
5. Successful responses render the status, reason code, and telemetry identifier. Validation failures expose the sanitized reason code and message (for example `otp_out_of_window`) without echoing the shared secret or OTP.

## Replay Stored or Inline TOTP Submissions
1. Select the **Replay** tab. The console exposes a replay mode toggle that defaults to **Stored credential** and never mutates simulator state.
2. For stored replays, enter the credential ID, OTP, timestamp, and optional timestamp override. Adjust the drift window if you want to test looser or stricter tolerance. Click **Verify OTP** to receive a `match` or `mismatch` outcome along with matched skew metadata, the credential source, and a telemetry identifier.
3. For inline replays, choose **Inline parameters**, provide the shared secret and configuration (algorithm, digits, step seconds, drift window), set the timestamp and optional override, then press **Verify OTP**. The UI reports whether the OTP would have matched under the supplied conditions.
4. Replay requests emit `rest.totp.replay` telemetry with the same sanitisation guarantees as evaluation—secrets and OTP values are never echoed in the payload.

## Persist Tabs and Modes with Query Parameters
- The console encodes the active tab (`totpTab=evaluate` or `totpTab=replay`) and the current mode (`totpMode=stored|inline` for evaluation, `totpReplayMode=stored|inline` for replay) into the URL. Bookmark or share the deep link to reopen the console in the same state.
- Changing evaluation or replay modes emits `operator:totp-mode-changed` and `operator:totp-replay-mode-changed` events respectively; the surrounding console updates browser history so the back button and refreshes preserve your selection.

## Interpreting Metadata and Telemetry
- Stored requests emit `rest.totp.evaluate` telemetry with context fields for algorithm, digit length, step seconds, drift window, matched skew steps, and whether a timestamp override was provided. The UI exposes the generated telemetry identifier so you can correlate against downstream logs.
- Inline validations emit the same evaluation event but omit credential references and redact the shared secret, OTP, and override timestamp values. Replay submissions emit `rest.totp.replay` telemetry, adding the credential source and replay outcome (`match`/`mismatch`) without exposing secrets or OTP material.
- Validation errors return `reasonCode` values such as `credential_not_found`, `otp_out_of_window`, or `shared_secret_required`. The UI mirrors these codes in the error panels so operators can triage without inspecting server logs.
- Remember that evaluation and replay requests never mutate persistence—TOTP remains verification-only until issuance lands in a future feature.

Keep this guide updated as TOTP issuance, credential seeding helpers, or replay tooling are introduced in subsequent increments.
