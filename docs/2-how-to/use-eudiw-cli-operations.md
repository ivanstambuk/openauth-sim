# Use the EUDIW OpenID4VP CLI

_Status: Draft_  
_Last updated: 2025-11-08_

The `eudiw` Picocli entry point (Feature 040) lets you create HAIP/Baseline authorization requests, simulate wallet responses, and validate VP Tokens directly from the terminal. Output can be plain key/value pairs or JSON identical to the REST facade. This guide documents the most common flows.

## Prerequisites
- Java 17 with `JAVA_HOME` pointed at a JDK 17 install.
- Resolve dependencies once via `./gradlew --no-daemon spotlessApply check`.
- Run commands from the repo root; add `--quiet` to keep Gradle output minimal. Example wrapper: `./gradlew --quiet :cli:run --args=$'eudiw …'`.
- Optional: run the REST API in parallel so you can fetch QR links or inspect Swagger, but it is not required for CLI-only runs.

## Create a HAIP authorization request
```bash
./gradlew --quiet :cli:run --args=$'eudiw request create \
  --dcql-preset pid-haip-baseline \
  --profile HAIP \
  --response-mode DIRECT_POST_JWT \
  --include-qr \
  --verbose \
  --output-json' | jq
```
Output (trimmed):
```json
{
  "requestId": "OID4VP-REQ-000101",
  "authorizationRequest": {
    "clientId": "x509_hash:pid-haip-verifier",
    "nonce": "******C1F29",
    "state": "******7RZ1",
    "presentationDefinition": { "input_descriptors": ["pid-haip-baseline"] }
  },
  "qr": { "ascii": "████ ███ …" },
  "telemetry": {
    "event": "oid4vp.request.created",
    "fields": { "haipMode": true, "trustedAuthorities": ["aki:s9tIpP7qrS9="] }
  }
}
```
Notes:
- Provide exactly one of `--dcql-preset` or `--dcql-json <path>`. Inline JSON is read from disk; errors bubble up as `error=Unable to read DCQL override …`.
- Pass `--signed-request=false` or `--profile BASELINE` to inspect relaxed behaviour (nonce/state still masked in telemetry).

## Generate a wallet response
### Stored preset
```bash
./gradlew --quiet :cli:run --args=$'eudiw wallet simulate \
  --request-id OID4VP-REQ-000101 \
  --wallet-preset pid-haip-baseline \
  --trusted-authority aki:s9tIpP7qrS9= \
  --response-mode DIRECT_POST_JWT \
  --verbose \
  --output-json' | jq '.presentations[0]'
```
Key fields:
- `presentation.vpToken.vp_token` – the compact SD-JWT minted by the simulator.
- `presentation.disclosureHashes[]` – deterministic SHA-256 digests (prefixed with `sha-256:`).
- `trace.trustedAuthorityMatch.policy` – confirms which Trusted Authority satisfied the request.

### Inline credentials
Provide a compact SD-JWT plus optional disclosures/KB-JWT. If a path exists on disk, the CLI reads it; otherwise the literal string is used.
```bash
./gradlew --quiet :cli:run --args=$'eudiw wallet simulate \
  --request-id OID4VP-REQ-000202 \
  --profile BASELINE \
  --inline-sdjwt docs/test-vectors/eudiw/openid4vp/fixtures/synthetic/sdjwt-vc/pid-haip-baseline/sdjwt.txt \
  --disclosure docs/test-vectors/eudiw/openid4vp/fixtures/synthetic/sdjwt-vc/pid-haip-baseline/disclosures.json \
  --kb-jwt docs/test-vectors/eudiw/openid4vp/fixtures/synthetic/sdjwt-vc/pid-haip-baseline/kb-jwt.json \
  --verbose \
  --output-json'
```
If you omit `--wallet-preset`, the CLI synthesizes `credentialId = inline-credential` unless you override it inside the JSON payload.

## Validate VP Tokens
### Stored presentation (`--preset`)
```bash
./gradlew --quiet :cli:run --args=$'eudiw validate \
  --preset pid-haip-baseline \
  --trusted-authority aki:s9tIpP7qrS9= \
  --output-json' | jq '.presentations[0].trustedAuthorityMatch'
```
The CLI generates a request ID automatically when you omit `--request-id`.

### Inline VP Token (`--vp-token`)
```bash
./gradlew --quiet :cli:run --args=$'eudiw validate \
  --vp-token /tmp/inline-vp-token.json \
  --trusted-authority aki:s9tIpP7qrS9= \
  --verbose \
  --output-json' | jq '.trace'
```
The inline file must be a JSON object with `vp_token`, `presentation_submission`, optional `disclosures[]`, and optional `keyBindingJwt`. Validation errors return exit code `2` and print the RFC 7807 payload.

## Mixed tips
- Add `--verbose` to surface nonce/state, VP Token hashes, and Trusted Authority verdicts. The JSON output matches the REST response when you pair it with `--output-json`.
- Telemetry IDs are prefixed with `cli.eudiw.*`; capture them when correlating with REST/UI logs.
- `wallet simulate` accepts either `--wallet-preset` or `--inline-sdjwt` (plus disclosures). Passing neither raises `error=walletPresetId or inlineSdJwt required…`.
- `validate` enforces mutual exclusivity between `--preset` and `--vp-token`. Provide one or the other to avoid `Provide either --preset or --vp-token` errors.

## Looking ahead: fixture ingestion
The ingestion primitives (FixtureDatasets + ingestion service) live in the application layer and emit `oid4vp.fixtures.ingested`. Once the CLI wiring lands, expect a `eudiw fixtures ingest --source <SYNTHETIC|CONFORMANCE>` command mirroring the REST endpoint. For now, updating ``docs/test-vectors/eudiw/openid4vp/fixtures`/*/` and restarting the REST app refreshes presets automatically.
