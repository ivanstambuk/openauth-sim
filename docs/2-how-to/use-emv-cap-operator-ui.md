# Use the EMV/CAP Operator UI

_Status: Draft_  
_Last updated: 2025-11-15_

The operator console exposes an EMV/CAP tab that mirrors the REST contract while surfacing a concise OTP preview and an optional verbose trace. This guide walks through loading presets, submitting Identify/Respond/Sign evaluations, capturing traces, and keeping sensitive data gated behind the trace toggle.

## Prerequisites
- Launch the REST application (`./gradlew --no-daemon --init-script [tools/run-rest-api.init.gradle.kts](tools/run-rest-api.init.gradle.kts) runRestApi`).
- Seed EMV/CAP credentials via the CLI (`./gradlew --quiet :cli:run --args=$'emv cap seed --preset all'`) or the REST endpoint (`POST /api/v1/emv/cap/credentials/seed`) if you want stored presets available.
- Use a modern browser (Chrome, Firefox, Edge) with JavaScript enabled. The console lives at `http://localhost:8080/ui/console`.

## Navigate to the EMV/CAP tab
1. Open the console and select **EMV/CAP** from the protocol picker.
2. Choose a preset from the “Stored credential (optional)” dropdown (for example `identify-baseline` or `sign-amount-50375`). Presets populate the ICC master key, ATC, branch factor, height, IV, CDOL1, issuer bitmap, ICC payload template, and issuer application data fields automatically—inline mode keeps all of these fields editable, while stored mode treats card configuration fields as read-only and continues to hide only EMV keys.
3. Confirm the customer input controls reflect the active mode:
   - Identify hides challenge/reference/amount.
   - Respond only enables the challenge input.
   - Sign enables all three inputs and displays amount formatting hints.

## Submit an Identify evaluation
1. Leave the mode set to Identify and press **Evaluate CAP OTP**.
2. The right column (Result card) renders the counter/Δ/OTP table along with a success/failure badge.
3. Toggle “Enable verbose tracing for the next request” if you need session keys, Generate AC buffers, or overlay information; the verbose trace panel appears below the result card after submission. This switch is global—whatever state you choose applies to the next Evaluate **and** Replay request, matching the `includeTrace` flag used by the REST/CLI facades.

## Capture Respond and Sign traces
- Respond evaluations (e.g., `respond-challenge4`) surface the numeric challenge directly in the verbose trace, along with the resolved ICC payload and masked-digit overlay. The result card remains concise.
- Sign evaluations (e.g., `sign-amount-0845`) include the reference and amount values in the trace metadata, while the result card continues to display only the masked OTP preview.

## Replay stored or inline OTPs
1. Switch to the **Replay** tab next to **Evaluate**.
2. Choose whether to replay a stored credential or an inline payload:
   - **Stored** – select a preset ID from the dropdown (`emv-cap:respond-baseline`, `emv-cap:sign-amount-50375`, etc.). Enter the OTP you captured from the calculator. Preview window bounds default to `0/0`; adjust the forward/backward fields to widen the search delta.
   - **Inline** – leave the preset dropdown empty and fill in the master key, ATC, branch factor, height, IV, CDOL1, issuer bitmap, ICC template, issuer application data, and mode-specific customer inputs. These fields mirror the Evaluate tab and honour the same validation rules. Provide the OTP plus optional preview-window bounds.
3. Leave “Include verbose trace” checked when you want the masked overlay, Generate AC buffers, matched delta, and resolved ICC payload. Uncheck it to suppress the trace and keep sensitive data off-screen. The UI simply forwards the toggle state to the backend (setting `includeTrace=true/false`), so Replay responses mirror CLI/REST payloads without any extra switches.
4. Press **Replay CAP OTP**. The result card summarizes the outcome:
   - Matches display `status = match`, the matched delta (0 unless the preview window is non-zero), and the credential source (`stored` or `inline`).
   - Mismatches show `status = mismatch` and the reason code (`otp_mismatch`). The supplied OTP length and preview-window bounds remain visible in the metadata column.
5. When the trace toggle is enabled, the verbose panel renders the same JSON payload exposed by the REST/CLI facades (`operation` set to `emv.cap.replay.stored` or `.inline`). Use the toolbar to copy/download the payload.
6. When a mismatch occurs and verbose tracing is disabled, the Replay result card displays a hashed OTP banner showing the `sha256:` digest of the expected OTP and a short reminder to **Enable verbose tracing for the next request** before replaying if you need full mismatch diagnostics (decimalization overlay, comparison steps, and provenance details).

Replay submissions emit sanitized telemetry events with `telemetryId` prefixes `ui-emv-cap-replay-*`, allowing you to align them with REST/CLI logs when necessary.

## Copy and sanitize traces
- The verbose trace panel routes through the shared `VerboseTraceConsole` toolbar. Use the copy button to collect JSON output that matches the REST/CLI responses; inline and stored replay flows now emit the same payload whenever the global toggle requests a trace.
- Sensitive buffers (master keys, session keys, Generate AC inputs/outputs) only appear when the trace toggle is enabled for the request. Disable the toggle when capturing screenshots or operating in untrusted environments.

## Keep provenance fixtures mirrored
- The verbose trace JSON shown in the UI (and the Node/Selenium harnesses that stub it) reads from [docs/test-vectors/emv-cap/trace-provenance-example.json](docs/test-vectors/emv-cap/trace-provenance-example.json). Because the UI tests execute from the `rest-api/` module, the same file must also live under [rest-api/docs/test-vectors/emv-cap/trace-provenance-example.json](rest-api/docs/test-vectors/emv-cap/trace-provenance-example.json) until a future fixture-sync automation task lands.
- When updating or replacing the provenance fixture, edit the canonical copy under ``docs/test-vectors/emv-cap`/`, then copy it into ``rest-api/docs/test-vectors/emv-cap`/` before rerunning `node --test [rest-api/src/test/javascript/emv/console.test.js](rest-api/src/test/javascript/emv/console.test.js)` and `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`.
- Keeping the two files identical prevents the console bundle, REST OpenAPI snapshots, and Selenium stub server from diverging during local development or CI.

## Troubleshooting
- Input validation messages appear inline beneath the offending field and are mirrored in the console’s log view.
- Stored presets rely on the shared MapDB database. If presets are missing, rerun the CLI seeding command or seed via REST, then refresh the page.
- Selenium coverage enforces the two-column layout (result card on the right, trace panel below). If you observe regressions, confirm that static assets (`static/ui/emv-cap/*.js`) match the latest repository state.

Refer to the REST and CLI how-to guides for the corresponding API payloads and command-line workflows.
