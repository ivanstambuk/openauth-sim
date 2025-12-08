# Use the EMV/CAP CLI

_Status: Draft_  
_Last updated: 2025-11-15_

The `emv cap` Picocli commands let you exercise the CAP engine without spinning up the REST facade. You can seed canonical fixtures, evaluate Identify/Respond/Sign inputs, replay stored or inline OTPs, and emit either human-readable output or full JSON payloads (including verbose traces) that mirror the REST contract.

The CLI uses the same `includeTrace` toggle as the REST API and operator UI. The `--include-trace` flag defaults to `true` for Evaluate and Replay commands, which means CLI JSON output can be copied directly into the shared `VerboseTraceConsole` when troubleshooting. Set `--include-trace false` any time you want to suppress the provenance payload.

## Prerequisites
- Java 17 with `JAVA_HOME` pointing at a JDK 17 install.
- The standalone fat JAR built or downloaded (`openauth-sim-standalone-<version>.jar`).
- Optional: run the REST API (`./gradlew --no-daemon --init-script [tools/run-rest-api.init.gradle.kts](tools/run-rest-api.init.gradle.kts) runRestApi`) when you want to inspect responses through Swagger UI or seed credentials over HTTP—the CLI shares the same MapDB database.
- `jq` (optional) for pretty-printing JSON output.

## Seed the canonical fixtures
Load the curated CAP credentials and transcripts so stored-mode evaluations succeed immediately:
```bash
java -jar openauth-sim-standalone-<version>.jar emv cap seed --preset all
```
The command prints sanitized telemetry (for example `{"event":"cli.emv.cap.seed","status":"success",...}`) listing which credential IDs were created. Subsequent runs are idempotent; the command reports `already_seeded` when presets exist.

## Evaluate a stored Identify preset
Preset identifiers align with the JSON fixtures under ``docs/test-vectors/emv-cap`/`. Use `identify-baseline` after seeding:
```bash
java -jar openauth-sim-standalone-<version>.jar emv cap evaluate --preset-id identify-baseline
```
Output (truncated):
```
OTP: 42511495
Mask length: 8
Masked digits: 8
Telemetry: {"event":"cli.emv.cap.identify","status":"success","fields":{"mode":"IDENTIFY","atc":"00B4","ipbMaskLength":8,"maskedDigitsCount":8}}
```
Pass `--output-json` to receive the REST-style payload, including the verbose trace.

## Evaluate inline Respond inputs
Inline mode accepts the same flags the REST endpoint expects. The example below exercises the `respond-challenge8` vector captured in T3908c:
```bash
java -jar openauth-sim-standalone-<version>.jar emv cap evaluate \
  --mode RESPOND \
  --master-key 0123456789ABCDEF0123456789ABCDEF \
  --atc 00C8 \
  --branch-factor 4 \
  --height 8 \
  --iv 00000000000000000000000000000000 \
  --cdol1 9F02069F03069F1A0295055F2A029A039C019F3704 \
  --issuer-proprietary-bitmap 00001F00000000000FFFFF00000000008000 \
  --challenge 12345678 \
  --icc-data-template 1000xxxxA50006040000 \
  --issuer-application-data 06770A03A48000 \
  --include-trace false
```
Setting `--include-trace false` suppresses derivation details; the CLI still prints sanitized telemetry so you can correlate requests across tooling.

## Inspect Sign mode with JSON output
Switch to Sign mode and request JSON output to mirror the REST facade:
```bash
java -jar openauth-sim-standalone-<version>.jar emv cap evaluate \
  --mode SIGN \
  --master-key 0123456789ABCDEF0123456789ABCDEF \
  --atc 0142 \
  --branch-factor 4 \
  --height 8 \
  --iv 00000000000000000000000000000000 \
  --cdol1 9F02069F03069F1A0295055F2A029A039C019F3704 \
  --issuer-proprietary-bitmap 00001F00000000000FFFFF00000000008000 \
  --challenge 1234 \
  --reference 5689 \
  --amount 50375 \
  --icc-data-template 1000xxxxA50006040000 \
  --issuer-application-data 06770A03A48000 \
  --output-json | jq
```
The JSON payload includes the OTP, mask metadata, verbose trace (session key, Generate AC buffers, masked overlay), and sanitized telemetry (`event = cli.emv.cap.sign`).

## Replay stored credentials
After running `emv cap seed`, use the replay command to validate calculator entries against the stored presets. Supply the credential ID, mode, OTP, and optional preview-window bounds:
```bash
java -jar openauth-sim-standalone-<version>.jar emv cap replay \
  --credential-id emv-cap:respond-baseline \
  --mode RESPOND \
  --otp 94644592 \
  --search-backward 1 \
  --search-forward 1
```
Text output shows a sanitized telemetry frame followed by the replay summary:
```
event=cli.emv.cap.replay status=success mode=RESPOND matchedDelta=0 driftBackward=1 driftForward=1
Replay status: match (reason=match)
Credential source: stored (ID=emv-cap:respond-baseline)
```
Add `--output-json` to mirror the REST payload, including the verbose trace when `--include-trace` remains `true` (the default).

## Replay inline OTPs
When you need to verify an OTP without seeding a preset, pass the same inputs you would use for evaluation plus the OTP you observed:
```bash
java -jar openauth-sim-standalone-<version>.jar emv cap replay \
  --mode SIGN \
  --master-key 0123456789ABCDEF0123456789ABCDEF \
  --atc 0142 \
  --branch-factor 4 \
  --height 8 \
  --iv 00000000000000000000000000000000 \
  --cdol1 9F02069F03069F1A0295055F2A029A039C019F3704 \
  --issuer-proprietary-bitmap 00001F00000000000FFFFF00000000008000 \
  --challenge 1234 \
  --reference 5689 \
  --amount 50375 \
  --icc-template 1000xxxxA50006040000 \
  --issuer-application-data 06770A03A48000 \
  --otp 00000000 \
  --search-forward 1 \
  --include-trace false
```
Mismatched OTPs print a `status=mismatch` summary while keeping all secrets redacted. Set `--include-trace true` whenever you want the masked-digit overlay and Generate AC buffers to troubleshoot derivation issues. Preview window bounds (`--search-backward/forward`) control how far the replay service searches around the supplied ATC.

## Troubleshooting & telemetry notes
- Input validation mirrors the REST layer. Invalid hex values, incorrect branch factors, or missing Sign fields return a non-zero exit code and print a `status=invalid_input` problem alongside sanitized telemetry.
- Telemetry IDs are prefixed with `cli-emv-cap-*`; use them to cross-reference REST/UI events when reproducing issues.
- Combine `--database` with CLI commands to point at a non-default MapDB location if you are testing isolated credential sets.
- JSON emitted by `--output-json` is byte-for-byte compatible with the REST responses and the operator console’s `VerboseTraceConsole`. Toggle `--include-trace` to control whether that payload includes the provenance sections.

See the operator UI guide to drive the same workflows through the EMV/CAP tab and to inspect verbose traces visually.
