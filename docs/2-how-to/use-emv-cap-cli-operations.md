# Use the EMV/CAP CLI

_Status: Draft_  
_Last updated: 2025-11-02_

The `emv cap` Picocli commands let you exercise the CAP engine without spinning up the REST facade. You can seed canonical fixtures, evaluate Identify/Respond/Sign inputs, and emit either human-readable output or full JSON payloads (including verbose traces) that mirror the REST contract.

## Prerequisites
- Java 17 with `JAVA_HOME` pointing at a JDK 17 install.
- Gradle dependencies resolved (`./gradlew spotlessApply check` should already pass).
- Optional: run the REST API (`./gradlew --no-daemon --init-script tools/run-rest-api.init.gradle.kts runRestApi`) when you want to inspect responses through Swagger UI or seed credentials over HTTP—the CLI shares the same MapDB database.
- `jq` (optional) for pretty-printing JSON output.

Run Picocli commands from the repository root. Set `GRADLE_USER_HOME=$PWD/.gradle` to keep Gradle caches within the workspace.

## Seed the canonical fixtures
Load the curated CAP credentials and transcripts so stored-mode evaluations succeed immediately:
```bash
./gradlew --quiet :cli:run --args=$'emv cap seed --preset all'
```
The command prints sanitized telemetry (for example `{"event":"cli.emv.cap.seed","status":"success",...}`) listing which credential IDs were created. Subsequent runs are idempotent; the command reports `already_seeded` when presets exist.

## Evaluate a stored Identify preset
Preset identifiers align with the JSON fixtures under `docs/test-vectors/emv-cap/`. Use `identify-baseline` after seeding:
```bash
./gradlew --quiet :cli:run --args=$'emv cap evaluate --preset-id identify-baseline'
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
./gradlew --quiet :cli:run --args=$'emv cap evaluate \
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
  --include-trace false'
```
Setting `--include-trace false` suppresses derivation details; the CLI still prints sanitized telemetry so you can correlate requests across tooling.

## Inspect Sign mode with JSON output
Switch to Sign mode and request JSON output to mirror the REST facade:
```bash
./gradlew --quiet :cli:run --args=$'emv cap evaluate \
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
  --output-json' | jq
```
The JSON payload includes the OTP, mask metadata, verbose trace (session key, Generate AC buffers, masked overlay), and sanitized telemetry (`event = cli.emv.cap.sign`).

## Troubleshooting & telemetry notes
- Input validation mirrors the REST layer. Invalid hex values, incorrect branch factors, or missing Sign fields return a non-zero exit code and print a `status=invalid_input` problem alongside sanitized telemetry.
- Telemetry IDs are prefixed with `cli-emv-cap-*`; use them to cross-reference REST/UI events when reproducing issues.
- Combine `--database` with CLI commands to point at a non-default MapDB location if you are testing isolated credential sets.

See the operator UI guide to drive the same workflows through the EMV/CAP tab and to inspect verbose traces visually.
