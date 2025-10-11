# Operate the FIDO2/WebAuthn REST API

_Status: Draft_  
_Last updated: 2025-10-10_

The REST API exposes stored and inline WebAuthn assertion verification plus replay diagnostics and sample data feeds for the operator console. This guide walks through seeding the canonical credentials, validating assertions, replaying submissions, and tapping into the JSON vector catalogue that powers every facade.

## Prerequisites
- Java 17 with `JAVA_HOME` configured.
- Gradle dependencies installed (`./gradlew spotlessApply check` should succeed).
- Optional: `jq` for inspecting JSON responses.

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
Seed a curated subset of JSON bundle vectors so stored-mode flows work immediately:
```bash
curl -s -X POST http://localhost:8080/api/v1/webauthn/credentials/seed | jq
```
Example response:
```json
{
  "addedCount": 3,
  "canonicalCount": 5,
  "addedCredentialIds": [
    "packed-es256",
    "resident-key-es256",
    "rs256-hsm"
  ]
}
```
The action is idempotent—subsequent calls report `addedCount: 0` once every curated credential already exists.

## Discover Stored Credentials (`GET /api/v1/webauthn/credentials`)
Fetch sanitized summaries for UI dropdowns or tooling:
```bash
curl -s http://localhost:8080/api/v1/webauthn/credentials | jq
```
Sample response:
```json
[
  {
    "id": "packed-es256",
    "label": "packed-es256 (ES256)",
    "relyingPartyId": "example.org",
    "algorithm": "ES256",
    "userVerification": false
  },
  {
    "id": "resident-key-es256",
    "label": "resident-key-es256 (ES256)",
    "relyingPartyId": "example.org",
    "algorithm": "ES256",
    "userVerification": true
  }
]
```
If persistence is disabled (`openauth.sim.persistence.enable-store=false`) the endpoint returns an empty array.

## Retrieve Sample Assertion Inputs
Stored-mode helper:
```bash
curl -s http://localhost:8080/api/v1/webauthn/credentials/generator-es256/sample | jq
```
Example response (trimmed):
```json
{
  "credentialId": "generator-es256",
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
curl -s -H "Content-Type: application/json"   -d '{"credentialId":"generator-es256","relyingPartyId":"example.org","origin":"https://example.org","expectedType":"webauthn.get","challenge":"tNvtqPm4a6c4ZC7a6r0N0Q","privateKey":"<JWK JSON shortened>"}'   http://localhost:8080/api/v1/webauthn/evaluate | jq
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
Inline requests provide every credential attribute explicitly. The example below reuses the `generator-es256` private key but supplies a fresh challenge.
```bash
curl -s -H "Content-Type: application/json"   -d '{"credentialName":"inline-generator","credentialId":"V2VhQXV0aFNpbUJhc2UuLi4=","relyingPartyId":"example.org","origin":"https://example.org","expectedType":"webauthn.get","algorithm":"ES256","signatureCounter":0,"userVerificationRequired":false,"challenge":"d2ViYXV0aG4taW5saW5lLWNobGctMTIz","privateKey":"<JWK JSON shortened>"}'   http://localhost:8080/api/v1/webauthn/evaluate/inline | jq
```
> Replace `<JWK JSON shortened>` with the desired private key (for example the value returned by `/sample`).
The response mirrors stored mode aside from `credentialSource`/`credentialReference` reflecting the inline request.

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

Replay responses include `match` (`true`/`false`), `credentialSource` (`stored` or `inline`), and sanitized error metadata. Mismatches remain HTTP 200 so you can diagnose incidents without triggering error handling.

## Telemetry & Troubleshooting
- Stored requests emit `event=rest.fido2.evaluate` with `status=generated`, the sanitized telemetry id, `credentialReference`, `relyingPartyId`, `algorithm`, and `userVerificationRequired`. Inline requests emit the same event but omit credential references.
- Replay requests emit `event=rest.fido2.replay` and surface `match`, `credentialSource`, and `reason` fields (`mismatch`, `signature_invalid`, etc.).
- HTTP 422 responses always include a `telemetryId` for correlation plus a `reasonCode` such as `credential_not_found`, `origin_mismatch`, `signature_counter_regressed`, or `invalid_payload`. HTTP 500 responses strip secret material and default to `reasonCode=webauthn_evaluation_failed` / `webauthn_replay_failed`.
- Need fresh payloads? Call `/api/v1/webauthn/credentials/{credentialId}/sample` after seeding, or read from `docs/webauthn_assertion_vectors.json` if you want inline-only vectors that are not part of the curated seed set.

Tie this REST workflow with the CLI (`use-fido2-cli-operations.md`) and operator UI (`use-fido2-operator-ui.md`) guides to reproduce the same vectors across every facade.
