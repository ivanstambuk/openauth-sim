# Operate the FIDO2/WebAuthn REST API

_Status: Draft_  
_Last updated: 2025-10-14_

The REST API exposes stored and inline WebAuthn assertion verification plus replay diagnostics and sample data feeds for the operator console. This guide walks through seeding the canonical credentials, validating assertions, replaying submissions, and tapping into the JSON vector catalogue that powers every facade.

## Prerequisites
- Java 17 with `JAVA_HOME` configured.
- Gradle dependencies installed (`./gradlew spotlessApply check` should succeed).
- Optional: `jq` for inspecting JSON responses.

## Verbose Tracing
- All evaluation and replay endpoints accept an optional `verbose` boolean. When set to `true`, the response (or error payload) includes a `trace` object containing the operation identifier (`fido2.evaluate.inline`, `fido2.replay.stored`, etc.), request metadata, and an ordered list of steps (`resolve.credential`, `parse.cose.publicKey`, `verify.signature`, `verify.counter`, `resolve.trustAnchor`, …).
- Example request:
  ```bash
  curl -s -H "Content-Type: application/json" \
    -d '{"credentialId":"packed-es256","relyingPartyId":"example.org","origin":"https://example.org","expectedType":"webauthn.get","challenge":"tNvtqPm4a6c4ZC7a6r0N0Q","privateKey":"<JWK>","verbose":true}' \
    http://localhost:8080/api/v1/webauthn/evaluate | jq '.trace'
  ```
- Traces expose raw assertions and key material, so only enable verbose mode in controlled environments. Leave the field out (or set `false`) during regular runs to keep responses minimal and sanitized.

### Start the REST Service
From the repository root:
```bash
./gradlew :rest-api:bootRun
```
Endpoints are now available at `http://localhost:8080`. The OpenAPI contract lives at:
- JSON: `http://localhost:8080/v3/api-docs`
- YAML: `http://localhost:8080/v3/api-docs.yaml`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`

Snapshot copies are stored under `docs/3-reference/rest-openapi.json` and `.yaml`. Regenerate them after API changes with:
```bash
OPENAPI_SNAPSHOT_WRITE=true ./gradlew :rest-api:test --tests io.openauth.sim.rest.OpenApiSnapshotTest
```

## Seed Canonical Credentials (`POST /api/v1/webauthn/credentials/seed`)
Seed the curated WebAuthn credentials (one per supported algorithm) so stored-mode flows work immediately. Each entry retains the preset key (for example `packed-es256`) used by CLI and automation flows:
```bash
curl -s -X POST http://localhost:8080/api/v1/webauthn/credentials/seed | jq
```
Example response:
```json
{
  "addedCount": 6,
  "canonicalCount": 6,
  "addedCredentialIds": [
    "packed-es256",
    "packed-es384",
    "packed-es512",
    "packed-rs256",
    "synthetic-ps256-uv0_up1",
    "packed-ed25519"
  ]
}
```
The action is idempotent—subsequent calls report `addedCount: 0` once every curated credential already exists. Each identifier matches the preset key surfaced by `WebAuthnGeneratorSamples`.

### Generator preset catalogue
| Preset key | Algorithm | Notes |
|------------|-----------|-------|
| `packed-es256` | ES256 | W3C §16 `packed` fixture with compact P-256 JWK private key |
| `packed-es384` | ES384 | W3C §16 `packed` fixture with compact P-384 JWK private key |
| `packed-es512` | ES512 | W3C §16 `packed` fixture with compact P-521 JWK private key |
| `packed-rs256` | RS256 | W3C §16 `packed` fixture with deterministic RSA-2048 key material |
| `synthetic-ps256-uv0_up1` | PS256 | Synthetic JSON vector seeded from `docs/webauthn_assertion_vectors.json` (spec omits private key) |
| `packed-ed25519` | Ed25519 | W3C §16 `packed` fixture with Ed25519 JWK private key |

The CLI, REST, and operator console all rely on this catalogue, so preset behaviour stays consistent across facades.

## Discover Stored Credentials (`GET /api/v1/webauthn/credentials`)
Fetch sanitized summaries for UI dropdowns or tooling:
```bash
curl -s http://localhost:8080/api/v1/webauthn/credentials | jq
```
Sample response:
```json
[
  {
    "id": "packed-ed25519",
    "label": "EdDSA (W3C 16.1.10)",
    "relyingPartyId": "example.org",
    "algorithm": "EdDSA",
    "userVerification": true
  },
  {
    "id": "packed-es256",
    "label": "ES256 (W3C 16.1.1)",
    "relyingPartyId": "example.org",
    "algorithm": "ES256",
    "userVerification": false
  }
]
```
If persistence is disabled (`openauth.sim.persistence.enable-store=false`) the endpoint returns an empty array.

## Retrieve Sample Assertion Inputs
Stored-mode helper (replace the preset key to target a different algorithm):
```bash
curl -s http://localhost:8080/api/v1/webauthn/credentials/packed-es256/sample | jq
```
Example response (trimmed):
```json
{
  "credentialId": "packed-es256",
  "relyingPartyId": "example.org",
  "origin": "https://example.org",
  "expectedType": "webauthn.get",
  "algorithm": "ES256",
  "userVerificationRequired": false,
  "challenge": "tNvtqPm4a6c4ZC7a6r0N0Q",
  "privateKeyJwk": "<JWK JSON shortened>"
}
```
The endpoint returns the relying-party metadata, a Base64URL challenge, and a compact authenticator private key (JWK) sourced from the generator presets. Use these inputs when calling the evaluation endpoints.

## Generate Stored Assertions (`POST /api/v1/webauthn/evaluate`)
Send the credential identifier, relying-party context, challenge, and authenticator private key. Signature-counter and UV overrides are optional.
```bash
curl -s -H "Content-Type: application/json"   -d '{"credentialId":"packed-es256","relyingPartyId":"example.org","origin":"https://example.org","expectedType":"webauthn.get","challenge":"tNvtqPm4a6c4ZC7a6r0N0Q","privateKey":"<JWK JSON shortened>"}'   http://localhost:8080/api/v1/webauthn/evaluate | jq
```
> Replace `<JWK JSON shortened>` with the `privateKeyJwk` value returned by the sample endpoint.
Success response:
```json
{
  "status": "generated",
  "assertion": { "id": "V2VhQXV0aFNpbUJhc2UuLi4=", "type": "public-key", "response": { "clientDataJSON": "…", "authenticatorData": "…", "signature": "…" }, "relyingPartyId": "example.org", "origin": "https://example.org", "algorithm": "ES256", "userVerificationRequired": false, "signatureCounter": 0 },
  "metadata": { "telemetryId": "rest-fido2-3b27c9c4-…", "credentialSource": "stored", "credentialReference": true, "relyingPartyId": "example.org", "origin": "https://example.org", "algorithm": "ES256", "userVerificationRequired": false }
}
```
Validation failures return HTTP 422 with sanitized error metadata (for example `credential_not_found`, `origin_mismatch`, or `private_key_invalid`). Only successful calls include the assertion payload.

## Generate Inline Assertions (`POST /api/v1/webauthn/evaluate/inline`)
Inline requests provide every credential attribute explicitly. The example below reuses the `packed-es256` private key but supplies a fresh challenge.
```bash
curl -s -H "Content-Type: application/json"   -d '{"credentialName":"inline-generator","credentialId":"V2VhQXV0aFNpbUJhc2UuLi4=","relyingPartyId":"example.org","origin":"https://example.org","expectedType":"webauthn.get","algorithm":"ES256","signatureCounter":0,"userVerificationRequired":false,"challenge":"d2ViYXV0aG4taW5saW5lLWNobGctMTIz","privateKey":"<JWK JSON shortened>"}'   http://localhost:8080/api/v1/webauthn/evaluate/inline | jq
```
> Replace `<JWK JSON shortened>` with the desired private key (for example the value returned by `/sample`).
The response mirrors stored mode aside from `credentialSource`/`credentialReference` reflecting the inline request. If you load preset data from the operator console, the UI now forwards a `credentialName` field that matches the sample vector label (for example `"ES256 sample vector"`).

### Quick inline payload helper
Convert JSON vectors to inline request bodies while sourcing the accompanying private key from the generator presets:
```bash
python - <<'PYV' | curl -s -H "Content-Type: application/json" -d @- http://localhost:8080/api/v1/webauthn/evaluate/inline | jq
import json, sys
from pathlib import Path
vector_id = "ES256:uv0_up1"
vectors = json.loads(Path("docs/webauthn_assertion_vectors.json").read_text())
vector = next(v for v in vectors if v["vector_id"] == vector_id)
payload = {
    "credentialName": f"{vector_id}-inline",
    "relyingPartyId": vector["computed"]["rpId"],
    "origin": "https://example.com",
    "expectedType": "webauthn.get",
    "credentialId": vector["response"]["rawId"],
    "algorithm": vector["key_material"]["algorithm"],
    "signatureCounter": vector["computed"]["signCount"],
    "userVerificationRequired": vector["request"]["userVerification"] == "required",
    "challenge": vector["request"]["challenge"],
    "privateKey": json.dumps(vector["key_material"].get("privateKeyJwk", {})),
}
json.dump(payload, sys.stdout)
PYV
```
> The JSON vector catalogue stores the private key in JWK form for every preset (Gitleaks-friendly segments). Merge it with your own relying-party metadata to craft inline-generation requests quickly.


## Replay Assertions (`POST /api/v1/webauthn/replay`)
Replay requests check whether an assertion would match without mutating counters. Supply the same payload you would use for stored evaluation:

```bash
curl -s \
  -H "Content-Type: application/json" \
  -d "$(curl -s http://localhost:8080/api/v1/webauthn/credentials/packed-es256/sample \
        | jq '{credentialId, relyingPartyId, origin, expectedType,
               expectedChallenge: .expectedChallengeBase64Url,
               clientData: .clientDataBase64Url,
               authenticatorData: .authenticatorDataBase64Url,
               signature: .signatureBase64Url}')" \
  http://localhost:8080/api/v1/webauthn/replay | jq
```

### Public-key formats
- **COSE (Base64URL)**: Continue sending the raw credential `publicKey` returned by stored presets or inline generator outputs. The simulator accepts Base64URL strings exactly as before.
- **JWK JSON**: Provide the public portion of a JWK object (fields such as `kty`, `crv`, `x`, `y` for EC or `n`, `e` for RSA). The API auto-detects JSON and converts it to the COSE representation used by the verifier. Validation errors surface `public_key_format_invalid`.
- **PEM / PKCS#8**: Paste a `-----BEGIN PUBLIC KEY-----` block (EC, RSA, or Ed25519). The service parses the PEM, converts it into COSE, and reuses the same verification pipeline. Formatting issues also return `public_key_format_invalid`.

All formats are interchangeable; choose whichever is easiest to export from your tooling. Replay responses include `match` (`true`/`false`), `credentialSource` (`stored` or `inline`), and sanitized error metadata. Mismatches remain HTTP 200 so you can diagnose incidents without triggering error handling.

## Telemetry & Troubleshooting
- Stored requests emit `event=rest.fido2.evaluate` with `status=generated`, the sanitized telemetry id, `credentialReference`, `relyingPartyId`, `algorithm`, and `userVerificationRequired`. Inline requests emit the same event but omit credential references.
- Replay requests emit `event=rest.fido2.replay` and surface `match`, `credentialSource`, and `reason` fields (`mismatch`, `signature_invalid`, etc.).
- HTTP 422 responses always include a `telemetryId` for correlation plus a `reasonCode` such as `credential_not_found`, `origin_mismatch`, `signature_counter_regressed`, or `invalid_payload`. HTTP 500 responses strip secret material and default to `reasonCode=webauthn_evaluation_failed` / `webauthn_replay_failed`.
- Need fresh payloads? Call `/api/v1/webauthn/credentials/{credentialId}/sample` after seeding (swap `{credentialId}` with any generator preset key), or read from `docs/webauthn_assertion_vectors.json` if you want inline-only vectors that are not part of the curated seed set.

Tie this REST workflow with the CLI (`use-fido2-cli-operations.md`) and operator UI (`use-fido2-operator-ui.md`) guides to reproduce the same vectors across every facade.
