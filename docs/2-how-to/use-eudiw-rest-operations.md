# Call the EUDIW OpenID4VP REST API

_Status: Draft_  
_Last updated: 2025-11-08_

The Feature 040 REST endpoints expose the same request, wallet, validation, and (future) ingestion flows as the CLI/UI. All routes live under `/api/v1/eudiw/openid4vp` and emit sanitized telemetry (`oid4vp.request.created`, `oid4vp.wallet.responded`, `oid4vp.response.*`, `oid4vp.fixtures.ingested`). This guide walks through each endpoint with fixture-backed examples.

## Prerequisites
- Start the REST application: `./gradlew --no-daemon --init-script [tools/run-rest-api.init.gradle.kts](tools/run-rest-api.init.gradle.kts) runRestApi`.
- Ensure ``docs/test-vectors/eudiw/openid4vp`/` is intact; it provides the `pid-haip-baseline` preset used below.
- Optional: run `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"` to view the current schema (`docs/3-reference/rest-openapi.(json|yaml)`).
- `jq` (optional) for formatting responses.

Verbose traces are disabled by default. Append `?verbose=true` to any POST route to include the `trace` object (hashed payloads, nonce/state, Trusted Authority verdict metadata).

## 1. Create a HAIP authorization request (`POST /requests`)
```bash
curl -s -H "Content-Type: application/json" \
  "http://localhost:8080/api/v1/eudiw/openid4vp/requests?verbose=true" \
  -d '{
        "profile": "HAIP",
        "responseMode": "DIRECT_POST_JWT",
        "dcqlPreset": "pid-haip-baseline",
        "signedRequest": true,
        "includeQrAscii": true
      }' | jq
```
Response highlights:
```json
{
  "requestId": "OID4VP-REQ-000042",
  "profile": "HAIP",
  "requestUri": "https://sim.local/oid4vp/request/OID4VP-REQ-000042",
  "authorizationRequest": {
    "clientId": "x509_hash:pid-haip-verifier",
    "nonce": "******3CF29",
    "state": "******1XRZ1",
    "responseMode": "direct_post.jwt",
    "presentationDefinition": { "input_descriptors": ["pid-haip-baseline"] }
  },
  "qr": {
    "ascii": "████ ███ …",
    "uri": "openid-vp://?request_uri=https://sim.local/oid4vp/request/OID4VP-REQ-000042"
  },
  "trace": {
    "requestId": "OID4VP-REQ-000042",
    "dcqlHash": "08c2…",
    "trustedAuthorities": ["aki:s9tIpP7qrS9="],
    "nonceFull": "e8618e14723cf29",
    "stateFull": "b5ac20f71d31xrZ1"
  }
}
```
Notes:
- Provide either `dcqlPreset` or `dcqlOverride`. Presets include Trusted Authority labels automatically.
- Set `signedRequest=false` when profiling the Baseline mode; the controller still masks nonce/state in the response and telemetry.

## 2. Simulate a wallet presentation (`POST /wallet/simulate`)
Use the `requestId` issued above or omit it to let the simulator generate one.
```bash
curl -s -H "Content-Type: application/json" \
  "http://localhost:8080/api/v1/eudiw/openid4vp/wallet/simulate?verbose=true" \
  -d '{
        "requestId": "OID4VP-REQ-000042",
        "profile": "HAIP",
        "walletPreset": "pid-haip-baseline",
        "trustedAuthorityPolicy": "aki:s9tIpP7qrS9=",
        "responseMode": "DIRECT_POST_JWT"
      }' | jq
```
Key fields:
- `presentations[0].vpToken.vp_token` – compact SD-JWT for the PID fixture.
- `trace.vpTokenHash` / `kbJwtHash` – SHA-256 digests for telemetry parity.
- `trace.trustedAuthorityMatch` – echoes the policy and friendly label recorded in telemetry.

Inline simulations replace `walletPreset` with `inlineSdJwt`:
```json
"inlineSdJwt": {
  "credentialId": "inline-pid",
  "format": "dc+sd-jwt",
  "compactSdJwt": "eyJ0eXAiOiJzZC1qd3QiLCJhbGciOiJFUzI1NiIs…",
  "disclosures": ["WyJleU8iLDAseyJqd…"],
  "keyBindingJwt": "eyJhbGciOiJFUzI1NiIs…",
  "trustedAuthorityPolicies": ["aki:s9tIpP7qrS9="]
}
```

## 3. Validate a VP Token (`POST /validate`)
### Stored preset
```bash
curl -s -H "Content-Type: application/json" \
  "http://localhost:8080/api/v1/eudiw/openid4vp/validate" \
  -d '{
        "presetId": "pid-haip-baseline",
        "profile": "HAIP",
        "responseMode": "DIRECT_POST_JWT",
        "trustedAuthorityPolicy": "aki:s9tIpP7qrS9="
      }' | jq '.presentations[0].trustedAuthorityMatch'
```
The service loads the stored VP Token/KB-JWT, recomputes disclosure hashes, verifies Trusted Authorities, and emits `oid4vp.response.validated` telemetry.

### Inline VP Token
```bash
curl -s -H "Content-Type: application/json" \
  "http://localhost:8080/api/v1/eudiw/openid4vp/validate?verbose=true" \
  -d '{
        "inlineVpToken": {
          "credentialId": "inline-pid",
          "format": "dc+sd-jwt",
          "vpToken": {
            "vp_token": "eyJ0eXAiOiJzZC1qd3Qi…",
            "presentation_submission": { "descriptor_map": [] }
          },
          "disclosures": ["WyJleU8iLDAseyJqd…"],
          "trustedAuthorityPolicies": ["aki:s9tIpP7qrS9="]
        },
        "trustedAuthorityPolicy": "aki:s9tIpP7qrS9="
      }'
```
Validation failures raise RFC 7807 problem details (for example `invalid_scope` when the Trusted Authority filter is unmet). The response still includes sanitized telemetry in the `details` object.

## 4. Seed presentations (`POST /presentations/seed`)
`/presentations/seed` now drives `OpenId4VpFixtureIngestionService`, so every call returns the fixture summaries plus the provenance pulled from ``docs/trust/snapshots`/<timestamp>/`.
```bash
curl -s -H "Content-Type: application/json" \
  http://localhost:8080/api/v1/eudiw/openid4vp/presentations/seed \
  -d '{
        "source": "CONFORMANCE",
        "presentations": ["pid-haip-lotl"]
      }' | jq
```
Sample response (2025-11-15 ingestion):
```json
{
  "source": "conformance",
  "requestedCount": 1,
  "ingestedCount": 1,
  "provenance": {
    "source": "EU LOTL + Member-State Trusted Lists (DE, SI)",
    "version": "2025-11-15T13:11:59Z",
    "sha256": "sha256:cb0dfbf7a8df9d7ea1b36bce46dbfc48ee5c40e8e6c6f43bae48e165b5bc69e5",
    "ingestId": "2025-11-15T13:11:59Z-optionA",
    "lotlSequenceNumber": 373,
    "lotlIssueDateTime": "2025-10-15T10:09:50Z",
    "memberStates": [
      { "country": "DE", "tslSequenceNumber": 149, "listIssueDateTime": "2025-10-07T13:00:24Z" },
      { "country": "SI", "tslSequenceNumber": 78, "listIssueDateTime": "2025-07-02T08:20:21Z" }
    ]
  },
  "presentations": [
    {
      "presentationId": "pid-haip-lotl",
      "credentialId": "pid-haip-lotl",
      "format": "dc+sd-jwt",
      "trustedAuthorities": [
        "aki:s9tIpP7qrS9=",
        "etsi_tl:lotl-373",
        "etsi_tl:de-149",
        "etsi_tl:si-78",
        "openid_federation:https://haip.ec.europa.eu/trust-list"
      ]
    }
  ],
  "telemetry": {
    "event": "oid4vp.fixtures.ingested",
    "fields": {
      "source": "conformance",
      "ingestedCount": 1,
      "provenanceSource": "EU LOTL + Member-State Trusted Lists (DE, SI)",
      "provenanceVersion": "2025-11-15T13:11:59Z",
      "provenanceHash": "sha256:cb0dfbf7a8df9d7ea1b36bce46dbfc48ee5c40e8e6c6f43bae48e165b5bc69e5",
      "ingestId": "2025-11-15T13:11:59Z-optionA",
      "lotlSequenceNumber": 373,
      "memberStates": ["DE", "SI"],
      "telemetryId": "oid4vp-…" 
    }
  }
}
```
Notes:
- `source` still matches `FixtureDatasets.Source` (`synthetic` or `conformance`). The controller normalises case so either `"CONFORMANCE"` or `"conformance"` works.
- `presentations` mirrors the stored preset metadata so operators can confirm which Trusted Authority policies each presentation satisfies before seeding a backing store.
- `telemetry` is the raw `oid4vp.fixtures.ingested` frame (see the telemetry catalog below) so you can trace ingestion activity alongside other simulator events.

## Telemetry & troubleshooting
- Request creation emits `oid4vp.request.created` with `haipMode`, masked nonce/state, and Trusted Authority labels. Wallet simulations emit `oid4vp.wallet.responded` plus the Trusted Authority verdict. Validation emits either `oid4vp.response.validated` (success) or `oid4vp.response.failed` (problem details). Fixture ingestion now emits `oid4vp.fixtures.ingested` with the same provenance metadata shown above.
- All telemetry frames set `sanitized=true`. Hashes (`vpTokenHash`, `kbJwtHash`, disclosure hashes) appear in traces rather than telemetry, keeping sensitive payloads off the wire.
- Trusted Authority mismatches return `invalid_scope` with a `violations[]` array describing which policy failed. Inspect the response trace (via `?verbose=true`) to confirm which policies were evaluated.
- Baseline mode bypasses HAIP encryption. Use HAIP mode to exercise the `direct_post.jwt` encryption path (`DirectPostJwtEncryptionService`) and collect latency metrics via `telemetry.fields.durationMs`.

See [docs/2-how-to/use-eudiw-cli-operations.md](docs/2-how-to/use-eudiw-cli-operations.md) and [docs/2-how-to/use-eudiw-operator-ui.md](docs/2-how-to/use-eudiw-operator-ui.md) for companion workflows.
