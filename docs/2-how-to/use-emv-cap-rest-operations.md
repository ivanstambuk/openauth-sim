# Operate the EMV/CAP REST API

_Status: Draft_  
_Last updated: 2025-11-15_

The EMV/CAP evaluate endpoint derives CAP one-time passwords (Identify, Respond, Sign) on demand. It wraps the shared core engine, emits sanitized telemetry, and optionally returns a full derivation trace for diagnostics. The companion replay endpoint validates stored or ad-hoc OTPs against the same derivation pipeline so you can confirm calculator outputs. This guide shows how to call both endpoints, interpret the response payloads, and disable verbose traces when you only need the masked digits.

REST, CLI, and operator UI clients all share the same verbose trace contract. The operator console’s “Enable verbose tracing for the next request” toggle simply flips the `includeTrace` flag for whichever EMV/CAP request (Evaluate or Replay) you submit; disable it—or pass `includeTrace: false` / `--include-trace false`—when you want responses without provenance data.

## Prerequisites
- Java 17 with `JAVA_HOME` configured.
- Spring Boot application running (`./gradlew --no-daemon --init-script tools/run-rest-api.init.gradle.kts runRestApi` from the repo root).
- Optional: `jq` for formatting JSON responses.

OpenAPI references:
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- JSON: `http://localhost:8080/v3/api-docs`
- YAML: `http://localhost:8080/v3/api-docs.yaml`

Snapshot copies live at `docs/3-reference/rest-openapi.(json|yaml)` and are kept in sync via:
```bash
OPENAPI_SNAPSHOT_WRITE=true ./gradlew :rest-api:test --tests io.openauth.sim.rest.OpenApiSnapshotTest
```

## Evaluate a CAP Identify request (`POST /api/v1/emv/cap/evaluate`)
The endpoint accepts a JSON body with the CAP mode, keys, issuer bitmaps, and optional customer inputs. All hexadecimal fields must be uppercase with an even length; decimal fields (challenge, reference, amount) stay as plain strings.

Example Identify request:
```bash
curl -s -H "Content-Type: application/json" \
  -d '{
        "mode": "IDENTIFY",
        "masterKey": "0123456789ABCDEF0123456789ABCDEF",
        "atc": "00B4",
        "branchFactor": 4,
        "height": 8,
        "iv": "00000000000000000000000000000000",
        "cdol1": "9F02069F03069F1A0295055F2A029A039C019F3704",
        "issuerProprietaryBitmap": "00001F00000000000FFFFF00000000008000",
        "customerInputs": {
          "challenge": "",
          "reference": "",
          "amount": ""
        },
        "iccDataTemplate": "1000xxxxA50006040000",
        "issuerApplicationData": "06770A03A48000"
      }' \
  http://localhost:8080/api/v1/emv/cap/evaluate | jq
```

Key request notes:
- `iccDataTemplate` may contain `x` placeholders; the service substitutes the provided ATC before generating the cryptogram. The resolved value appears in the trace payload.
- `customerInputs` fields are optional per mode:
  - Identify ignores all three fields.
  - Respond uses `challenge`.
  - Sign expects all three.
- `transactionData` allows overriding pre-computed terminal or ICC payloads:
  ```json
  {
    "transactionData": {
      "terminal": "0000...",
      "icc": "1000..."
    }
  }
  ```
- Omit `includeTrace` (or set `true`) to receive derivation details; set `false` to return OTP + telemetry only. Operator UI submissions mirror the same flag so Evaluate/Replay runs from the console always match the API you would call via curl.

Successful response (Identify baseline):
```json
{
  "otp": "42511495",
  "maskLength": 8,
  "trace": {
    "masterKeySha256": "sha256:8A7F239A0C4B7342C0178F274D37B9F6B3C8D1E4F7A2C5D8E9F0011223344556",
    "sessionKey": "5EC8B98ABC8F9E7597647CBCB9A75402",
    "atc": "00B4",
    "branchFactor": 4,
    "height": 8,
    "maskLength": 8,
    "previewWindowBackward": 0,
    "previewWindowForward": 0,
    "generateAcInput": {
      "terminal": "0000000000000000000000000000800000000000000000000000000000",
      "icc": "100000B4A50006040000"
    },
    "generateAcResult": "8000B47F32A79FDA94564306770A03A48000",
    "bitmask": "....1F...........FFFFF..........8...",
    "maskedDigitsOverlay": "....14...........45643..........8...",
    "issuerApplicationData": "06770A03A48000",
    "iccPayloadTemplate": "1000xxxxA50006040000",
    "iccPayloadResolved": "100000B4A50006040000"
  },
  "telemetry": {
    "event": "emv.cap.identify",
    "status": "success",
    "reasonCode": "generated",
    "sanitized": true,
    "fields": {
      "telemetryId": "rest-emv-cap-d4b4d5d5",
      "mode": "IDENTIFY",
      "atc": "00B4",
      "ipbMaskLength": 8,
      "maskedDigitsCount": 8,
      "branchFactor": 4,
      "height": 8,
      "previewWindowBackward": 0,
      "previewWindowForward": 0
    }
  }
}
```

Telemetry highlights:
- `event` mirrors the CAP mode (`emv.cap.identify`, `.respond`, `.sign`).
- `sanitized` is always `true` for successful requests and validation errors; unexpected failures keep `sanitized = false` but still omit master/session keys.
- `fields.telemetryId` carries a facade-specific prefix (`rest-emv-cap-*`). Use it to correlate request/response pairs in logs.

### Disable verbose traces
Set `includeTrace` to `false` to keep responses minimal. The OTP and telemetry remain; `trace` is omitted entirely.
```bash
curl -s -H "Content-Type: application/json" \
  -d '{ "mode": "SIGN", "...": "...", "includeTrace": false }' \
  http://localhost:8080/api/v1/emv/cap/evaluate | jq
```

### Handle validation errors
Missing or malformed fields return an RFC 7807 problem-details payload with a precise `reasonCode` and field hint.

Example response (missing `masterKey`):
```json
{
  "status": "invalid_input",
  "reasonCode": "missing_field",
  "message": "masterKey is required",
  "details": {
    "status": "invalid",
    "field": "masterKey"
  }
}
```

All error payloads include sanitized telemetry in the `details` map when available. Fix the input and retry; no state is persisted server-side.

## Mode-specific guidance
- **Identify** – no customer inputs required. Use this mode to validate branch factor/height/IPB configuration against the reference calculators.
- **Respond** – populate `customerInputs.challenge` with the numeric challenge displayed by the token. The response OTP still derives from the masked digits overlay.
- **Sign** – supply `challenge`, `reference`, and `amount`. `reference` and `amount` remain string values; the backend enforces numeric-only rules.

## Replay a CAP OTP (`POST /api/v1/emv/cap/replay`)
Replay accepts either a stored credential ID (seeded through the CLI/REST seeding commands) or a full inline payload that mirrors evaluation inputs. Responses include a `status` (`match` or `mismatch`), a `reasonCode`, metadata describing how the OTP was validated, and an optional verbose trace.

### Stored replay workflow
Replay a stored preset by supplying the credential ID, CAP mode, OTP, preview-window bounds, and the optional `includeTrace` toggle:
```bash
curl -s -H "Content-Type: application/json" \
  -d '{
        "credentialId": "emv-cap:respond-baseline",
        "mode": "RESPOND",
        "otp": "94644592",
        "driftBackward": 1,
        "driftForward": 1,
        "includeTrace": true
      }' \
  http://localhost:8080/api/v1/emv/cap/replay | jq
```

Successful responses report `status = "match"` with metadata describing how the calculator output aligned with the stored credential:
```json
{
  "status": "match",
  "reasonCode": "match",
  "metadata": {
    "credentialSource": "stored",
    "credentialId": "emv-cap:respond-baseline",
    "mode": "RESPOND",
    "matchedDelta": 0,
    "driftBackward": 1,
    "driftForward": 1,
    "branchFactor": 4,
    "height": 8,
    "ipbMaskLength": 8,
    "telemetryId": "rest-emv-cap-d4b4d5d5"
  },
  "trace": {
    "operation": "emv.cap.replay.stored",
    "...": "..."
  }
}
```
Trace payloads reuse the verbose structure from evaluation (`sessionKey`, Generate AC buffers, masked overlay, etc.). Set `includeTrace` to `false` when you only need the match metadata.

### Inline replay workflow
Inline replay validates an OTP by supplying the same master key, ATC, bitmap, and customer inputs you would use for evaluation. Include the OTP and preview-window bounds; the backend derives the expected OTP and compares it with the supplied value.
```bash
curl -s -H "Content-Type: application/json" \
  -d '{
        "mode": "SIGN",
        "masterKey": "0123456789ABCDEF0123456789ABCDEF",
        "atc": "0142",
        "branchFactor": 4,
        "height": 8,
        "iv": "00000000000000000000000000000000",
        "cdol1": "9F02069F03069F1A0295055F2A029A039C019F3704",
        "issuerProprietaryBitmap": "00001F00000000000FFFFF00000000008000",
        "customerInputs": {
          "challenge": "1234",
          "reference": "5689",
          "amount": "50375"
        },
        "iccDataTemplate": "1000xxxxA50006040000",
        "issuerApplicationData": "06770A03A48000",
        "otp": "00000000",
        "driftBackward": 1,
        "driftForward": 1,
        "includeTrace": false
      }' \
  http://localhost:8080/api/v1/emv/cap/replay | jq
```

Mismatch responses keep secrets redacted while explaining why validation failed:
```json
{
  "status": "mismatch",
  "reasonCode": "otp_mismatch",
  "metadata": {
    "credentialSource": "inline",
    "mode": "SIGN",
    "driftBackward": 1,
    "driftForward": 1,
    "branchFactor": 4,
    "height": 8,
    "ipbMaskLength": 8,
    "suppliedOtpLength": 8,
    "telemetryId": "rest-emv-cap-f3dab9e0"
  }
}
```
The trace is omitted because `includeTrace` defaults to `false`. Set it to `true` when you want the masked overlay that led to the expected OTP.

## Compare with published vectors
Baseline vectors live under `docs/test-vectors/emv-cap/`. Each record includes the request payload, resolved ICC data, and expected outputs. Load them directly from tests or tooling to confirm the simulator stays aligned with the transcripted calculator runs.

### Extended coverage set
Beyond the original Identify/Respond/Sign baselines, the simulator now ships six additional vectors captured from hardware calculator transcripts:
- `identify-b2-h6` and `identify-b6-h10` exercise alternate branch-factor/height pairs.
- `respond-challenge4` and `respond-challenge8` cover short and long numeric challenges.
- `sign-amount-0845` and `sign-amount-50375` model low and high purchase amounts.

All vectors power end-to-end regression tests in the core, application, and REST suites. Treat them as canonical references whenever you validate new CAP inputs or extend external tooling.

## Shared verbose trace console
- The JSON shown in REST responses is the exact payload rendered inside the operator console’s `VerboseTraceConsole` and printed by the CLI when `--output-json` is enabled. Expect identical sections (`Protocol Context`, `Key Derivation`, `CDOL Breakdown`, `IAD Decoding`, `MAC Transcript`, `Decimalization Overlay`) in every surface.
- The global “Enable verbose tracing for the next request” toggle in the UI simply forwards `includeTrace=true` for both Evaluate and Replay submissions. Unchecked requests omit the `trace` object, and the console hides the verbose panel automatically—matching the behaviour you see when you call the REST endpoints directly with `includeTrace=false`.
