# Use the EUDIW OpenID4VP CLI

_Status: Draft_  
_Last updated: 2025-11-08_

The `eudiw` Picocli entry point (Feature 040) lets you create HAIP/Baseline authorization requests, simulate wallet responses, and validate VP Tokens directly from the terminal. Output can be plain key/value pairs or JSON identical to the REST facade—add `--output-json` on any subcommand (`request create`, `wallet simulate`, `validate`, `seed`, `vectors`) to emit a single JSON object; combine with `--verbose` to embed traces. Flag defaults and required inputs are summarized in the [CLI flags matrix](../3-reference/cli-flags-matrix.md).

## Prerequisites
- Java 17 with `JAVA_HOME` pointed at a JDK 17 install.
- The standalone fat JAR built or downloaded (`openauth-sim-standalone-<version>.jar`).
- Optional: run the REST API in parallel so you can fetch QR links or inspect Swagger, but it is not required for CLI-only runs.
JSON schemas for `--output-json` live in the global registry [docs/3-reference/cli/cli.schema.json](../3-reference/cli/cli.schema.json):
- `request create` (event `cli.eudiw.request.create`): `definitions["cli.eudiw.request.create"]`
- `wallet simulate` (event `cli.eudiw.wallet.simulate`): `definitions["cli.eudiw.wallet.simulate"]`
- `validate` (event `cli.eudiw.validate`): `definitions["cli.eudiw.validate"]`

## Quick commands (stored, inline, failure)
- Stored request + JSON + QR:  
  ```bash
  java -jar openauth-sim-standalone-<version>.jar eudiw request create \
    --dcql-preset pid-haip-baseline \
    --include-qr \
    --output-json
  ```
- Inline wallet + JSON:  
  ```bash
  java -jar openauth-sim-standalone-<version>.jar eudiw wallet simulate \
    --request-id HAIP-0001 \
    --wallet-preset pid-haip-baseline \
    --verbose \
    --output-json
  ```
- Failure drill (JSON): pointing to a missing DCQL file returns a validation error.  
  ```bash
  java -jar openauth-sim-standalone-<version>.jar eudiw request create \
    --dcql-json /tmp/missing-dcql.json \
    --output-json
  ```
  Output (truncated): `{"event":"oid4vp.request.created","status":"invalid","reasonCode":"validation_error","sanitized":true,"data":{"reason":"Unable to read DCQL override /tmp/missing-dcql.json"}}` with exit code `64`.

## Create a HAIP authorization request
```bash
java -jar openauth-sim-standalone-<version>.jar eudiw request create \
  --dcql-preset pid-haip-baseline \
  --include-qr \
  --output-json
```
Output formats:
- Default: telemetry line with masked nonce/state plus QR/URI fields.
- `--output-json`: full request object + QR + telemetry (sample below).
Captured output:
```json
{
  "requestId": "HAIP-0001",
  "profile": "HAIP",
  "requestUri": "https://sim.openauth.local/oid4vp/request/HAIP-0001",
  "authorizationRequest": {
    "clientId": "x509_hash:pid-haip-verifier",
    "nonce": "eudiw-openid4vp-nonce-seed-v1-0001",
    "state": "eudiw-openid4vp-state-seed-v1-0001",
    "responseMode": "direct_post.jwt",
    "presentationDefinition": "{\n  \"type\": \"pid-haip-baseline\",\n  \"credentials\": [\n    {\n      \"credential_id\": \"pid-haip-baseline\",\n      \"format\": \"dc+sd-jwt\",\n      \"required_claims\": [\n        { \"path\": \"vc.credentialSubject.given_name\" },\n        { \"path\": \"vc.credentialSubject.family_name\" },\n        { \"path\": \"vc.credentialSubject.nationality\" }\n      ]\n    }\n  ],\n  \"trusted_authorities\": [\n    {\n      \"type\": \"aki\",\n      \"values\": [ \"s9tIpP7qrS9=\" ]\n    },\n    {\n      \"type\": \"etsi_tl\",\n      \"values\": [ \"lotl-373\", \"de-149\", \"si-78\" ]\n    }\n  ]\n}\n"
  },
  "qr": {
    "uri": "openid-vp://?request_uri=https://sim.openauth.local/oid4vp/request/HAIP-0001",
    "ascii": "QR::openid-vp://?request_uri=https://sim.openauth.local/oid4vp/request/HAIP-0001"
  },
  "telemetry": {
    "fields": {
      "requestId": "HAIP-0001",
      "nonceMasked": "******1-0001",
      "responseMode": "DIRECT_POST_JWT",
      "requestUri": "https://sim.openauth.local/oid4vp/request/HAIP-0001",
      "telemetryId": "oid4vp-1afbc578-69ba-4a14-83fc-a6062a19a934",
      "trustedAuthorities": [
        "aki:s9tIpP7qrS9=",
        "etsi_tl:lotl-373",
        "etsi_tl:de-149",
        "etsi_tl:si-78"
      ],
      "haipMode": true,
      "trustedAuthorityMetadata": [
        {
          "value": "s9tIpP7qrS9=",
          "type": "aki",
          "policy": "aki:s9tIpP7qrS9=",
          "label": "EU PID Issuer"
        },
        {
          "value": "lotl-373",
          "type": "etsi_tl",
          "policy": "etsi_tl:lotl-373",
          "label": "EU LOTL seq 373 (2025-10-15)"
        },
        {
          "value": "de-149",
          "type": "etsi_tl",
          "policy": "etsi_tl:de-149",
          "label": "Germany TL seq 149 (2025-10-07)"
        },
        {
          "value": "si-78",
          "type": "etsi_tl",
          "policy": "etsi_tl:si-78",
          "label": "Slovenia TL seq 78 (2025-07-02)"
        }
      ],
      "stateMasked": "******1-0001",
      "qrUri": "openid-vp://?request_uri=https://sim.openauth.local/oid4vp/request/HAIP-0001",
      "qrAscii": "QR::openid-vp://?request_uri=https://sim.openauth.local/oid4vp/request/HAIP-0001",
      "profile": "HAIP"
    },
    "event": "oid4vp.request.created"
  }
}
```
Notes:
- Provide exactly one of `--dcql-preset` or `--dcql-json <path>`. Inline JSON is read from disk; errors bubble up as `error=Unable to read DCQL override …`.
- Pass `--signed-request=false` or `--profile BASELINE` to inspect relaxed behaviour (nonce/state still masked in telemetry).

## Generate a wallet response
### Stored preset
```bash
java -jar openauth-sim-standalone-<version>.jar eudiw wallet simulate \
  --request-id HAIP-0001 \
  --wallet-preset pid-haip-baseline \
  --output-json
```
Default output is a telemetry line with summary fields; `--output-json` returns the full VP Token bundle plus telemetry, matching the REST response shape.
Captured output:
```json
{
  "requestId": "HAIP-0001",
  "status": "SUCCESS",
  "profile": "HAIP",
  "responseMode": "DIRECT_POST_JWT",
  "presentations": [
    {
      "credentialId": "pid-haip-baseline",
      "format": "dc+sd-jwt",
      "holderBinding": true,
      "trustedAuthorityMatch": null,
      "vpToken": {
        "vp_token": "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCIsInNkX2FsZyI6InNoYS0yNTYifQ.eyJpc3MiOiJodHRwczovL3BpZC1pc3N1ZXIuZXhhbXBsZS5ldS9pc3N1ZXJzL2hhaXAiLCJzdWIiOiJkaWQ6ZXhhbXBsZTpob2xkZXItMDAwMSIsImF1ZCI6Imh0dHBzOi8vdmVyaWZpZXIuZXhhbXBsZS5ldS9vcGVuaWQ0dnAiLCJ2YyI6eyJ0eXBlIjpbIlZlcmlmaWFibGVDcmVkZW50aWFsIiwiZXUuZXVyb3BhLmVjLmV1ZGkucGlkLjEiXX19fQ.RW1UZXN0U2lnbmF0dXJlLXN5bmN0aGV0aWMtaHR0cC1wbGFjZWhvbGRlcg==",
        "presentation_submission": { "presentation_definition_id": "pid-haip-baseline" }
      },
      "disclosureHashes": [
        "sha-256:92eda87cc8cd487acb12471ab92d4c48fec0a568475bf8d16aeb326f066c8fc5",
        "sha-256:0f4f64c052f2322c030df3e1a3ad5d8f501e591a19374fe107fcaca6247a68b0",
        "sha-256:9651f9e53bbf121c26742ed5321ab360b0c07a7593040ac1d6d86af1a1eae044"
      ]
    }
  ],
  "telemetry": {
    "fields": {
      "requestId": "HAIP-0001",
      "telemetryId": "oid4vp-690d1d47-ef17-4b8e-b3ea-e6c984a78046",
      "presentations": 1
    },
    "event": "oid4vp.wallet.responded"
  }
}
```

### Inline credentials
Provide a compact SD-JWT plus optional disclosures/KB-JWT. If a path exists on disk, the CLI reads it; otherwise the literal string is used.
```bash
java -jar openauth-sim-standalone-<version>.jar eudiw wallet simulate \
  --request-id OID4VP-REQ-000202 \
  --profile BASELINE \
  --inline-sdjwt docs/test-vectors/eudiw/openid4vp/fixtures/synthetic/sdjwt-vc/pid-haip-baseline/sdjwt.txt \
  --disclosure docs/test-vectors/eudiw/openid4vp/fixtures/synthetic/sdjwt-vc/pid-haip-baseline/disclosures.json \
  --kb-jwt docs/test-vectors/eudiw/openid4vp/fixtures/synthetic/sdjwt-vc/pid-haip-baseline/kb-jwt.json \
  --verbose \
  --output-json
```
If you omit `--wallet-preset`, the CLI synthesizes `credentialId = inline-credential` unless you override it inside the JSON payload.

## Validate VP Tokens
### Stored presentation (`--preset`)
```bash
java -jar openauth-sim-standalone-<version>.jar eudiw validate \
  --preset pid-haip-baseline \
  --request-id HAIP-0001 \
  --output-json
```
Captured output:
```json
{
  "requestId": "HAIP-0001",
  "status": "SUCCESS",
  "profile": "HAIP",
  "responseMode": "DIRECT_POST_JWT",
  "presentations": [
    {
      "credentialId": "pid-haip-baseline",
      "format": "dc+sd-jwt",
      "holderBinding": true,
      "trustedAuthorityMatch": null,
      "vpToken": {
        "vp_token": "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCIsInNkX2FsZyI6InNoYS0yNTYifQ.eyJpc3MiOiJodHRwczovL3BpZC1pc3N1ZXIuZXhhbXBsZS5ldS9pc3N1ZXJzL2hhaXAiLCJzdWIiOiJkaWQ6ZXhhbXBsZTpob2xkZXItMDAwMSIsImF1ZCI6Imh0dHBzOi8vdmVyaWZpZXIuZXhhbXBsZS5ldS9vcGVuaWQ0dnAiLCJ2YyI6eyJ0eXBlIjpbIlZlcmlmaWFibGVDcmVkZW50aWFsIiwiZXUuZXVyb3BhLmVjLmV1ZGkucGlkLjEiXX19fQ.RW1UZXN0U2lnbmF0dXJlLXN5bmN0aGV0aWMtaHR0cC1wbGFjZWhvbGRlcg==",
        "presentation_submission": {
          "descriptor_map": [
            {
              "id": "pid-haip-baseline",
              "format": "dc+sd-jwt",
              "path": "$.vp_token"
            }
          ]
        }
      },
      "disclosureHashes": [
        "sha-256:92eda87cc8cd487acb12471ab92d4c48fec0a568475bf8d16aeb326f066c8fc5",
        "sha-256:0f4f64c052f2322c030df3e1a3ad5d8f501e591a19374fe107fcaca6247a68b0",
        "sha-256:9651f9e53bbf121c26742ed5321ab360b0c07a7593040ac1d6d86af1a1eae044"
      ]
    }
  ],
  "telemetry": {
    "event": "oid4vp.response.validated",
    "fields": {
      "responseMode": "DIRECT_POST_JWT",
      "telemetryId": "oid4vp-74cca818-242d-44a9-9336-f1cd055b3dc6",
      "profile": "HAIP",
      "requestId": "HAIP-0001",
      "walletPreset": "pid-haip-baseline",
      "presentations": 1,
      "dcqlPreview": "{\n  \"type\": \"pid-haip-baseline\",\n  \"credentials\": [\n    {\n      \"credential_id\": \"pid-haip-baseline\",\n      \"format\": \"dc+sd-jwt\",\n      \"required_claims\": [\n        { \"path\": \"vc.credentialSubject.given_name\" },\n        { \"path\": \"vc.credentialSubject.family_name\" },\n        { \"path\": \"vc.credentialSubject.nationality\" }\n      ]\n    }\n  ],\n  \"trusted_authorities\": [\n    { \"type\": \"aki\", \"values\": [ \"s9tIpP7qrS9=\" ] },\n    { \"type\": \"etsi_tl\", \"values\": [ \"lotl-373\", \"de-149\", \"si-78\" ] }\n  ]\n}\n"
    }
  }
}
```
The CLI generates a request ID automatically when you omit `--request-id`.

### Inline VP Token (`--vp-token`)
```bash
java -jar openauth-sim-standalone-<version>.jar eudiw validate \
  --vp-token /tmp/inline-vp-token.json \
  --trusted-authority aki:s9tIpP7qrS9= \
  --verbose \
  --output-json | jq '.trace'
```
The inline file must be a JSON object with `vp_token`, `presentation_submission`, optional `disclosures[]`, and optional `keyBindingJwt`. Validation errors return exit code `2` and print the RFC 7807 payload.

### Output quick reference
- `request create` – default telemetry line; `--output-json` returns request + QR + telemetry.
- `wallet simulate` – default telemetry line; `--output-json` returns presentations and telemetry.
- `validate` – default telemetry line; `--output-json` returns validation result, trust authority matches, and telemetry.
- `seed` – default text summary; `--output-json` returns counts and provenance.
- `vectors` – default tabular list; `--output-json` returns arrays of request/presentation presets with counts.

## Mixed tips
- Add `--verbose` to surface nonce/state, VP Token hashes, and Trusted Authority verdicts. The JSON output matches the REST response when you pair it with `--output-json`.
- Telemetry IDs are prefixed with `cli.eudiw.*`; capture them when correlating with REST/UI logs.
- `wallet simulate` accepts either `--wallet-preset` or `--inline-sdjwt` (plus disclosures). Passing neither raises `error=walletPresetId or inlineSdJwt required…`.
- `validate` enforces mutual exclusivity between `--preset` and `--vp-token`. Provide one or the other to avoid `Provide either --preset or --vp-token` errors.

## Looking ahead: fixture ingestion
The ingestion primitives (FixtureDatasets + ingestion service) live in the application layer and emit `oid4vp.fixtures.ingested`. Once the CLI wiring lands, expect a `eudiw fixtures ingest --source <SYNTHETIC|CONFORMANCE>` command mirroring the REST endpoint. For now, updating ``docs/test-vectors/eudiw/openid4vp/fixtures`/*/` and restarting the REST app refreshes presets automatically.
